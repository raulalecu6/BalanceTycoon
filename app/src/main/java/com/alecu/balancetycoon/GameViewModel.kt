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

    val ads: AdsProvider = SimulatedAdsProvider()

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
        // Deja un par de consultas sin responder para que se vean en la pantalla de clientes.
        e.advance(GameEngine.DAY_SECONDS * 4)
        engine = e
        offlineReport = null
        lastCase = null
        catchingUp = false
        save()
        bump()
    }

    // ───────────── Acciones ─────────────

    fun tap(id: String) {
        engine?.tapDept(id)
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

    fun buy(id: String) {
        val e = engine ?: return
        e.buyLevels(id, buyCount(id))
        bump()
    }

    fun hire(id: String) {
        engine?.hire(id)
        bump()
    }

    fun train(id: String) {
        engine?.train(id)
        bump()
    }

    fun upgradeEquipment(id: String) {
        engine?.upgradeEquipment(id)
        bump()
    }

    fun buySoftware() {
        engine?.buySoftware()
        bump()
    }

    fun moveOffice() {
        engine?.moveOffice()
        bump()
    }

    fun answer(caseId: Int, option: Int) {
        val e = engine ?: return
        lastCase = e.answerCase(caseId, option)
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
        engine = newEngine()
        offlineReport = null
        lastCase = null
        save()
        bump()
    }

    fun save() {
        val e = engine ?: return
        e.state.lastSavedMillis = System.currentTimeMillis()
        val json = SaveCodec.encode(e.state)
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { store.write(json) }
    }
}
