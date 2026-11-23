package com.alecu.balancetycoon

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alecu.balancetycoon.core.game.AutoPlayer
import com.alecu.balancetycoon.core.game.CaseResult
import com.alecu.balancetycoon.core.game.EventKind
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.OfflineReport
import com.alecu.balancetycoon.core.save.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

enum class BuyMode { ONE, TEN, MAX }

/** Número romano para nombrar las gestorías siguientes. */
fun roman(n: Int): String {
    val values = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var rest = n
    val sb = StringBuilder()
    for ((v, r) in values) while (rest >= v) {
        sb.append(r)
        rest -= v
    }
    return sb.toString()
}

/**
 * Hace avanzar el motor unas diez veces por segundo mientras la app está visible,
 * guarda la partida cada 30 segundos y al salir, y calcula el progreso fuera de la app.
 *
 * Todo acceso al motor se hace en el hilo principal: el motor no es seguro entre hilos
 * y así se evitan condiciones de carrera con la interfaz.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SaveStore(app)

    var engine: GameEngine? by mutableStateOf(null)
        private set

    /** Contador que cambia en cada fotograma; la interfaz lo observa para redibujarse. */
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    var offlineReport: OfflineReport? by mutableStateOf(null)
    var catchingUp: Boolean by mutableStateOf(false)
        private set
    var lastCase: CaseResult? by mutableStateOf(null)
    var buyMode: BuyMode by mutableStateOf(BuyMode.ONE)
    var message: String? by mutableStateOf(null)
    var tab: Int by mutableIntStateOf(0)
    /** La partida guardada era del prototipo anterior y se ha empezado una nueva. */
    var startedFresh: Boolean by mutableStateOf(false)
    /** Cambia en cada hito para lanzar el confeti. */
    var celebration: Int by mutableIntStateOf(0)
        private set
    /** Solo en la versión de pruebas: fuerza el día (0) o la noche (1) en la torre. */
    var nightOverride: Float? by mutableStateOf(null)

    val ads: AdsProvider = SimulatedAdsProvider()
    val audio = GameAudio(app)

    /** Último suceso del motor cuyo sonido ya se ha tratado. */
    private var soundSeq = 0L

    private var loopJob: Job? = null
    private var backgroundAtMillis = 0L
    private var loaded = false
    private var pendingDemoMinutes = 0

    init {
        viewModelScope.launch { initialLoad() }
    }

    private suspend fun initialLoad() {
        val result = withContext(Dispatchers.IO) { store.load() }
        val saved = result.state
        startedFresh = result.discardedOldVersion
        val e = if (saved != null) GameEngine(saved) else newEngine()
        engine = e
        soundSeq = e.state.eventSeq
        if (saved != null && saved.lastSavedMillis > 0) {
            catchUp(e, (System.currentTimeMillis() - saved.lastSavedMillis) / 1000.0)
        }
        loaded = true
        if (pendingDemoMinutes > 0) runDemo(pendingDemoMinutes)
        save()
        startLoop()
    }

    private fun newEngine(): GameEngine = GameEngine.newGame(
        startYear = LocalDate.now().year,
        seed = System.nanoTime(),
        companyName = getApplication<Application>().getString(R.string.default_company),
    )

    // ───────────── Ciclo de vida ─────────────

    fun onForeground() {
        audio.onForeground()
        val e = engine ?: return
        if (!loaded || backgroundAtMillis == 0L) return
        val elapsed = (System.currentTimeMillis() - backgroundAtMillis) / 1000.0
        backgroundAtMillis = 0L
        viewModelScope.launch {
            catchUp(e, elapsed)
            startLoop()
        }
    }

    fun onBackground() {
        audio.onBackground()
        if (!loaded) return
        loopJob?.cancel()
        loopJob = null
        backgroundAtMillis = System.currentTimeMillis()
        save()
    }

    private fun startLoop() {
        if (loopJob != null) return
        loopJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var sinceSave = 0.0
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last) / 1000.0
                last = now
                val e = engine ?: continue
                if (catchingUp) continue
                e.advance(min(dt, 2.0))
                reactToEvents(null)
                bump()
                sinceSave += dt
                if (sinceSave >= 30.0) {
                    save()
                    sinceSave = 0.0
                }
            }
        }
    }

    /** Pone el juego al día por tramos de 10 días contables, cediendo el hilo entre tramos. */
    private suspend fun catchUp(e: GameEngine, elapsedSeconds: Double) {
        if (elapsedSeconds < 5.0) return
        catchingUp = true
        val snap = e.snapshot()
        var remaining = min(elapsedSeconds, GameEngine.OFFLINE_CAP_SECONDS)
        while (remaining > 0) {
            val step = min(remaining, GameEngine.DAY_SECONDS * 10)
            e.advance(step)
            remaining -= step
            yield()
        }
        val report = e.reportSince(snap, elapsedSeconds)
        if (report.days >= 1) offlineReport = report
        soundSeq = e.state.eventSeq // lo ocurrido fuera no suena: se resume en el diálogo
        catchingUp = false
        bump()
    }

    private fun bump() {
        _tick.value = _tick.value + 1
    }

    // ───────────── Modo demostración (solo en la versión de pruebas) ─────────────

    /** Prepara una partida avanzada con el jugador automático, para revisar la interfaz y hacer capturas. */
    fun requestDemo(minutes: Int) {
        if (minutes <= 0) return
        if (loaded) viewModelScope.launch { runDemo(minutes) } else pendingDemoMinutes = minutes
    }

    private suspend fun runDemo(minutes: Int) {
        pendingDemoMinutes = 0
        catchingUp = true
        val e = GameEngine.newGame(LocalDate.now().year, seed = 2026L, companyName = getApplication<Application>().getString(R.string.default_company))
        val bot = AutoPlayer(e, accuracy = 0.85)
        var t = 0.0
        var steps = 0
        while (t < minutes * 60.0) {
            bot.act()
            e.advance(0.5)
            t += 0.5
            if (++steps % 400 == 0) yield()
        }
        // Deja consultas sin responder (al menos una de asiento, si llega) para que se vean en la
        // pantalla de clientes.
        var waited = 0
        while (waited < 4 || (waited < 9 && e.state.cases.none { it.isEntry })) {
            e.advance(GameEngine.DAY_SECONDS)
            waited++
        }
        engine = e
        soundSeq = e.state.eventSeq
        offlineReport = null
        lastCase = null
        catchingUp = false
        save()
        bump()
    }

    // ───────────── Sonido y celebraciones ─────────────

    /**
     * Suena según lo que acaba de pasar: un hito (o una mudanza) tiene prioridad sobre el
     * sonido de la acción; sin acción, los avisos del motor suenan como alerta o campanilla.
     */
    private fun reactToEvents(actionSfx: Sfx?) {
        val e = engine ?: return
        val fresh = e.state.events.filter { it.seq > soundSeq }
        soundSeq = e.state.eventSeq
        val milestone = fresh.any { it.kind == EventKind.MILESTONE }
        if (milestone) celebration++
        val sfx = when {
            milestone && actionSfx == Sfx.MOVE -> Sfx.MOVE
            milestone -> Sfx.LEVEL_UP
            actionSfx != null -> actionSfx
            fresh.any { it.kind == EventKind.WARNING } -> Sfx.ALERT
            fresh.any { it.kind == EventKind.GOOD || it.kind == EventKind.INFO } -> Sfx.CHIME
            else -> null
        }
        sfx?.let { audio.play(it) }
    }

    private inline fun act(sfx: Sfx, block: (GameEngine) -> Boolean) {
        val e = engine ?: return
        if (block(e)) reactToEvents(sfx)
        bump()
    }

    fun playTap() = audio.play(Sfx.TAP)

    fun playCoin() = audio.play(Sfx.COIN)

    fun selectTab(i: Int) {
        if (tab != i) audio.play(Sfx.TAP)
        tab = i
    }

    // ───────────── Acciones ─────────────

    fun tap(id: String) {
        if (engine?.tapDept(id) == true) audio.play(Sfx.TAP)
        bump()
    }

    /** Niveles que compraría ahora el botón de ampliar según el modo elegido. */
    fun buyCount(id: String): Int {
        val e = engine ?: return 1
        if (e.dept(id).level == 0) return 1
        return when (buyMode) {
            BuyMode.ONE -> 1
            BuyMode.TEN -> 10
            BuyMode.MAX -> max(1, e.maxAffordable(id))
        }
    }

    fun buy(id: String) = act(Sfx.BUY) { it.buyLevels(id, buyCount(id)) }

    fun hire(id: String) = act(Sfx.HIRE) { it.hire(id) }

    fun train(id: String) = act(Sfx.BUY) { it.train(id) }

    fun upgradeEquipment(id: String) = act(Sfx.BUY) { it.upgradeEquipment(id) }

    fun buySoftware() = act(Sfx.BUY) { it.buySoftware() }

    fun moveOffice() = act(Sfx.MOVE) { it.moveOffice() }

    fun repair(incidentId: Int) = act(Sfx.BUY) { it.repair(incidentId) }

    fun acceptInvitation(incidentId: Int) = act(Sfx.CORRECT) { it.acceptInvitation(incidentId) }

    fun declineInvitation(incidentId: Int) = act(Sfx.TAP) { it.declineInvitation(incidentId) }

    fun answer(caseId: Int, option: Int) {
        val e = engine ?: return
        val r = e.answerCase(caseId, option) ?: return
        lastCase = r
        reactToEvents(if (r.correct) Sfx.CORRECT else Sfx.WRONG)
        bump()
    }

    fun answerEntry(caseId: Int, accounts: List<String>) {
        val e = engine ?: return
        val r = e.answerEntry(caseId, accounts) ?: return
        lastCase = r
        reactToEvents(if (r.correct) Sfx.CORRECT else Sfx.WRONG)
        bump()
    }

    /** Nombre propuesto para la siguiente gestoría: «Mi Gestoría II, S.L.», «III»... */
    fun suggestedNextName(): String {
        val n = (engine?.state?.prestigeCount ?: 0) + 2
        return getApplication<Application>().getString(R.string.default_company_numbered, roman(n))
    }

    /** Traspasa la gestoría y empieza otra con los puntos de experiencia. */
    fun prestige(newName: String) {
        val e = engine ?: return
        val name = newName.trim().ifEmpty { suggestedNextName() }
        val ng = e.prestige(name) ?: return
        engine = ng
        soundSeq = ng.state.eventSeq
        lastCase = null
        tab = 0
        celebration++
        audio.play(Sfx.MOVE)
        save()
        bump()
    }

    fun pay(obligationId: Int) {
        engine?.payObligation(obligationId)
        bump()
    }

    fun distribute(dividendShare: Double) {
        engine?.decideDistribution(dividendShare)
        bump()
    }

    fun watchAdForBoost() {
        ads.showRewarded(
            onReward = {
                engine?.activateBoost()
                message = getApplication<Application>().getString(R.string.ad_simulated)
                bump()
            },
            onUnavailable = {},
        )
    }

    fun resetGame() {
        val e = newEngine()
        soundSeq = e.state.eventSeq
        engine = e
        offlineReport = null
        lastCase = null
        save()
        bump()
    }

    override fun onCleared() {
        audio.release()
        super.onCleared()
    }

    fun save() {
        val e = engine ?: return
        e.state.lastSavedMillis = System.currentTimeMillis()
        val json = SaveCodec.encode(e.state)
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { store.write(json) }
    }
}
