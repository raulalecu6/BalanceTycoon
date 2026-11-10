package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.ObligationKind
import com.alecu.balancetycoon.core.game.PaidBy
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun assertBooksBalance(engine: GameEngine, where: String) {
        val bs = Reports.balanceSheet(engine.state.ledger)
        assertTrue("El balance no cuadra ($where): activo ${bs.totalAssets}, PN ${bs.equity}, pasivo ${bs.liabilities}", bs.balanced)
        val trial = Reports.trialBalance(engine.state.ledger)
        assertEquals("Sumas y saldos ($where)", trial.sumOf { it.debit }, trial.sumOf { it.credit })
        assertTrue("Banco negativo ($where): ${engine.cash()}", engine.cash() >= 0)
    }

    /** Juega con el bot durante [seconds] segundos reales, actuando cada medio segundo. */
    private fun play(engine: GameEngine, bot: Bot, seconds: Double, checkEvery: Double = 300.0, act: Boolean = true) {
        var t = 0.0
        var nextCheck = checkEvery
        while (t < seconds) {
            if (act) bot.act()
            engine.advance(0.5)
            t += 0.5
            if (t >= nextCheck) {
                assertBooksBalance(engine, "t=$t s, día ${GameDate(engine.state.day)}")
                nextCheck += checkEvery
            }
        }
    }

    @Test
    fun partidaNuevaArrancaCuadrada() {
        val e = GameEngine.newGame(2026)
        assertEquals(1, e.line("bakery").level)
        assertEquals(Money.euros(3_000L), e.state.ledger.natural("100"))
        // 3.000 € de capital − 600 € del horno − 126 € de IVA
        assertEquals(Money.euros(2_274L), e.cash())
        assertEquals(1, e.state.documents.size)
        assertEquals("notary", e.state.documents.first().templateId)
        assertBooksBalance(e, "inicio")
    }

    @Test
    fun ventaManualGeneraAsientoResumenAlCerrarElDia() {
        val e = GameEngine.newGame(2026)
        assertTrue(e.tapLine("bakery"))
        assertFalse("No se puede pulsar dos veces el mismo ciclo", e.tapLine("bakery"))
        e.advance(2.0)
        val sale = com.alecu.balancetycoon.core.game.Lines.get("bakery").revenuePerCycle
        assertEquals(sale, e.line("bakery").todayRevenue)
        e.advance(GameEngine.DAY_SECONDS) // cierra el día
        assertEquals(sale, e.state.ledger.natural("701"))
        assertEquals(Money.pct(sale, 400), e.state.ledger.natural("477")) // IVA del 4 % del pan
        assertBooksBalance(e, "tras la primera venta")
    }

    @Test
    fun documentoSeContabilizaEnLaCuentaCorrectaAunqueSeFalle() {
        val e = GameEngine.newGame(2026)
        val doc = e.state.documents.first()
        val r = e.answerDocument(doc.id, "100")!!
        assertFalse(r.correct)
        assertEquals("623", r.correctAccount)
        assertEquals(0, e.state.streak)
        assertEquals(doc.base, e.state.ledger.natural("623"))
        assertEquals(Money.pct(doc.base, 1500), e.state.ledger.natural("4751")) // retención del notario
        assertEquals(0L, e.state.ledger.natural("100") - Money.euros(3_000L))
        assertBooksBalance(e, "documento")
    }

    @Test
    fun liquidacionTrimestralDelIva() {
        val e = GameEngine.newGame(2026)
        e.hireManager("bakery")
        e.advance(GameEngine.DAY_SECONDS * 90) // 1T de 2026: 31 + 28 + 31 días
        assertEquals(GameDate.of(2026, 4, 1).epochDay, e.state.day)
        val led = e.state.ledger
        assertEquals("El IVA repercutido queda saldado", 0L, led.natural("477"))
        assertEquals("El IVA soportado queda saldado", 0L, led.natural("472"))
        val vat = e.state.obligations.firstOrNull { it.kind == ObligationKind.VAT }
        // Con la inversión inicial, el 1T puede salir a ingresar o a compensar; los dos casos deben cuadrar.
        if (vat != null) {
            assertEquals(GameDate.of(2026, 4, 20).epochDay, vat.dueDay)
            assertEquals(vat.amount, led.natural("4750"))
        } else {
            assertTrue(led.natural("4700") > 0)
        }
        assertBooksBalance(e, "tras el 1T")
    }

    @Test
    fun cierreDelEjercicioYAperturaDelSiguiente() {
        val e = GameEngine.newGame(2026)
        val bot = Bot(e)
        play(e, bot, GameEngine.DAY_SECONDS * 366)
        val s = e.state
        assertEquals(2027, s.ledger.year)
        assertNotNull("Debe haber resumen del ejercicio 2026", s.lastYear)
        val summary = s.lastYear!!
        assertEquals(2026, summary.year)
        // Tras la apertura, las cuentas de ingresos y gastos del ejercicio anterior no arrastran saldo.
        val opening = s.ledger.recent.first { it.year == 2027 && it.number == 1 }
        assertTrue(opening.concept.es.startsWith("Asiento de apertura"))
        assertTrue(opening.lines.none { Pgc.get(it.account).isPyg })
        // Si hubo beneficio, queda pendiente de reparto (o ya repartido por el bot) y la 129 lo refleja.
        if (summary.netResult > 0) {
            assertTrue(s.pendingDistribution != null || s.ledger.natural("112") > 0)
        }
        // El Impuesto sobre Sociedades del primer ejercicio con beneficio aplica el 15 % de nueva creación.
        if (summary.taxableBase > 0) {
            assertEquals(Money.pct(summary.taxableBase, 1500), summary.incomeTax)
        }
        assertBooksBalance(e, "apertura 2027")
    }

    @Test
    fun repartoRespetaReservaLegalYLimiteDeDividendos() {
        val e = GameEngine.newGame(2026)
        val bot = Bot(e)
        var t = 0.0
        while (e.state.pendingDistribution == null && t < GameEngine.DAY_SECONDS * 400) {
            bot.act(distribute = false)
            e.advance(0.5)
            t += 0.5
        }
        val pv = e.distributionPreview()!! // la partida del bot cierra 2026 con beneficio
        assertEquals(minOf(Money.pct(pv.profit, 1000), Money.euros(600L)), pv.legalReserve) // 20 % de 3.000 € = 600 €
        assertTrue(pv.maxDividend <= pv.distributable)
        assertTrue(e.decideDistribution(1.0))
        assertNull(e.state.pendingDistribution)
        assertEquals(pv.legalReserve, e.state.ledger.natural("112"))
        assertEquals(pv.maxDividend, e.state.ledger.natural("526"))
        assertTrue(Reports.balanceSheet(e.state.ledger).equity >= e.state.ledger.natural("100"))
        // A los 30 días se paga el dividendo con retención del 19 % y el neto llega al socio.
        e.advance(GameEngine.DAY_SECONDS * 31)
        assertEquals(0L, e.state.ledger.natural("526"))
        assertEquals(pv.maxDividend - Money.pct(pv.maxDividend, 1900), e.state.ownerWealth)
        assertBooksBalance(e, "dividendos")
    }

    @Test
    fun gestoriaPagaEnPlazoYSinGestoriaHayRecargo() {
        val withG = GameEngine.newGame(2026)
        withG.hireManager("bakery")
        withG.advance(GameEngine.DAY_SECONDS * 140) // hasta mayo
        val paidByGestoria = withG.state.obligations.filter { it.paidBy == PaidBy.GESTORIA }
        assertTrue("La gestoría presenta al menos un modelo", paidByGestoria.isNotEmpty())
        assertTrue(paidByGestoria.all { it.paidDay == it.dueDay && it.surcharge == 0L && it.fee > 0 })

        val noG = GameEngine.newGame(2026)
        noG.setGestoria(false)
        noG.hireManager("bakery")
        noG.advance(GameEngine.DAY_SECONDS * 160)
        val late = noG.state.obligations.filter { it.isPaid && it.kind != ObligationKind.SOCIAL_SECURITY && it.paidDay!! > it.dueDay }
        assertTrue("Sin gestoría, los modelos se pagan tarde con recargo", late.isNotEmpty())
        assertTrue(late.all { it.surcharge == Money.pct(it.amount, 200) })
        assertTrue(noG.state.ledger.natural("678") > 0)
        assertBooksBalance(noG, "recargos")
    }

    @Test
    fun guardadoYCargaReproducenLaPartida() {
        val e = GameEngine.newGame(2026, seed = 12345L)
        val bot = Bot(e)
        play(e, bot, 1800.0)
        val json = SaveCodec.encode(e.state)
        val restored = GameEngine(SaveCodec.decode(json))
        assertEquals(json, SaveCodec.encode(restored.state))

        // Las dos partidas, avanzadas igual, deben seguir siendo idénticas.
        e.advance(GameEngine.DAY_SECONDS * 120)
        restored.advance(GameEngine.DAY_SECONDS * 120)
        assertEquals(SaveCodec.encode(e.state), SaveCodec.encode(restored.state))
        assertBooksBalance(restored, "tras cargar")
    }

    @Test
    fun progresoFueraDeLaAplicacionConTope() {
        val e = GameEngine.newGame(2026)
        e.hireManager("bakery")
        val report = e.catchUp(12 * 3600.0)
        assertTrue(report.capped)
        assertEquals(GameEngine.OFFLINE_CAP_SECONDS, report.seconds, 0.001)
        assertEquals(960L, report.days)
        assertTrue(report.sales > 0)
        assertBooksBalance(e, "tras 8 h fuera")
    }

    @Test
    fun partidaLargaSiempreCuadra() {
        val e = GameEngine.newGame(2026, seed = 99L)
        val bot = Bot(e, accuracy = 0.7)
        play(e, bot, 3 * 3600.0, checkEvery = 600.0) // tres horas de juego activo
        e.catchUp(8 * 3600.0) // y una noche fuera
        assertBooksBalance(e, "final")
    }
}
