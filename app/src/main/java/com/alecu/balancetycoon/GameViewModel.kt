package com.alecu.balancetycoon

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alecu.balancetycoon.core.game.DocResult
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.OfflineReport
import com.alecu.balancetycoon.core.i18n.L
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

/** Resultado del último documento clasificado, para mostrar la explicación. */
data class DocFeedback(val title: L, val result: DocResult)

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
    var lastDocFeedback: DocFeedback? by mutableStateOf(null)
    var buyMode: BuyMode by mutableStateOf(BuyMode.ONE)
    var message: String? by mutableStateOf(null)

    val ads: AdsProvider = SimulatedAdsProvider()

    private var loopJob: Job? = null
    private var backgroundAtMillis = 0L
    private var loaded = false

    init {
        viewModelScope.launch { initialLoad() }
    }

    private suspend fun initialLoad() {
        val saved = withContext(Dispatchers.IO) { store.load() }
        val e = if (saved != null) GameEngine(saved) else newEngine()
        engine = e
        if (saved != null && saved.lastSavedMillis > 0) {
            catchUp(e, (System.currentTimeMillis() - saved.lastSavedMillis) / 1000.0)
        }
        loaded = true
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

    // ───────────── Acciones ─────────────

    fun tap(id: String) {
        engine?.tapLine(id)
        bump()
    }

    /** Niveles que compraría ahora el botón «Ampliar» según el modo elegido. */
    fun buyCount(id: String): Int {
        val e = engine ?: return 1
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

    fun open(id: String) {
        engine?.buyLevels(id, 1)
        bump()
    }

    fun hire(id: String) {
        engine?.hireManager(id)
        bump()
    }

    fun fire(id: String) {
        engine?.fireManager(id)
        bump()
    }

    fun hireAssistant() {
        engine?.hireAssistant()
        bump()
    }

    fun fireAssistant() {
        engine?.fireAssistant()
        bump()
    }

    fun answer(docId: Int, account: String) {
        val e = engine ?: return
        val doc = e.state.documents.firstOrNull { it.id == docId } ?: return
        val title = doc.template.title
        val result = e.answerDocument(docId, account) ?: return
        lastDocFeedback = DocFeedback(title, result)
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

    fun setGestoria(active: Boolean) {
        engine?.setGestoria(active)
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
        lastDocFeedback = null
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
