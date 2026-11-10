package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.accounting.EntryBuilder
import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.game.AutoPlayer
import com.alecu.balancetycoon.core.game.Cases
import com.alecu.balancetycoon.core.game.ClientTiers
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.EquipmentTiers
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.ObligationKind
import com.alecu.balancetycoon.core.game.Offices
import com.alecu.balancetycoon.core.game.PaidBy
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.save.IncompatibleSaveException
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

    /** Ampliación de capital para dar margen a un test. */
    private fun injectCash(e: GameEngine, euros: Long) {
        val c = Money.euros(euros)
        e.state.ledger.post(e.date(), L("Ampliación de capital (test)", "Capital increase (test)"), EntryBuilder().debit("572", c).credit("100", c))
    }

    /** Juega con el jugador automático durante [seconds] segundos reales, actuando cada medio segundo. */
    private fun play(engine: GameEngine, bot: AutoPlayer, seconds: Double, checkEvery: Double = 300.0) {
        var t = 0.0
        var nextCheck = checkEvery
        while (t < seconds) {
            bot.act()
            engine.advance(0.5)
            t += 0.5
            if (t >= nextCheck) {
                assertBooksBalance(engine, "t=$t s, día ${GameDate(engine.state.day)}")
                nextCheck += checkEvery
            }
        }
    }

    @Test
    fun catalogoDeConsultasCoherente() {
        val ids = Cases.all.map { it.id }
        assertEquals("Identificadores de consulta repetidos", ids.size, ids.toSet().size)
        for (c in Cases.all) {
            Departments.get(c.dept)
            assertTrue(c.minTier in ClientTiers.all.indices)
            assertEquals("Opciones repetidas en ${c.id}", 4, c.options.map { it.es }.toSet().size)
        }
        // Al empezar hay consultas suficientes para no repetirse enseguida.
        assertTrue(Cases.all.count { it.minTier == 0 && (it.dept == "fiscal" || it.dept == "laboral") } >= 6)
    }

    @Test
    fun partidaNuevaArrancaCuadrada() {
        val e = GameEngine.newGame(2026)
        assertEquals(1, e.dept("fiscal").level)
        assertEquals(0, e.dept("fiscal").workers)
        val led = e.state.ledger
        assertEquals(Money.euros(3_000L), led.natural("100"))
        assertEquals(Money.euros(600L), led.natural("260")) // fianza de dos meses
        assertEquals(Money.euros(600L), led.natural("627")) // campaña de apertura del departamento fiscal
        assertEquals(Money.euros(500L), led.natural("217")) // primer ordenador
        // 3.000 − 600 de fianza − (600 + 500) × 1,21
        assertEquals(Money.euros(1_069L), e.cash())
        assertTrue(e.deptAvailable("laboral"))
        assertFalse("El bajo solo tiene dos plantas", e.deptAvailable("contable"))
        assertBooksBalance(e, "inicio")
    }

    @Test
    fun expedienteManualGeneraAsientoResumenAlCerrarElDia() {
        val e = GameEngine.newGame(2026)
        assertTrue(e.tapDept("fiscal"))
        assertFalse("No se puede pulsar dos veces el mismo expediente", e.tapDept("fiscal"))
        val fee = e.revenuePerCycle("fiscal")
        assertEquals(Departments.get("fiscal").feePerCycle, fee)
        e.advance(2.0)
        assertEquals(fee, e.dept("fiscal").todayRevenue)
        e.advance(GameEngine.DAY_SECONDS) // cierra el día
        assertEquals(fee, e.state.ledger.natural("705"))
        assertEquals(Money.pct(fee, 2100), e.state.ledger.natural("477"))
        assertBooksBalance(e, "tras el primer expediente")
    }

    @Test
    fun consultaBienResueltaFacturaYDaReputacion() {
        val e = GameEngine.newGame(2026)
        e.advance(GameEngine.DAY_SECONDS * 2)
        val pc = e.state.cases.firstOrNull()
        assertNotNull("Debe haber llegado una consulta", pc)
        val before = e.state.ledger.natural("705")
        val r = e.answerCase(pc!!.id, 0)!!
        assertTrue(r.correct)
        assertEquals(pc.fee, e.state.ledger.natural("705") - before)
        assertEquals(3L, e.state.reputation)
        assertEquals(1, e.state.streak)
        assertTrue(e.state.cases.none { it.id == pc.id })

        e.advance(GameEngine.DAY_SECONDS * 2)
        val wrong = e.state.cases.first()
        val r2 = e.answerCase(wrong.id, 2)!!
        assertFalse(r2.correct)
        assertEquals(0L, r2.fee)
        assertEquals(0L, e.state.reputation)
        assertEquals(0, e.state.streak)
        assertBooksBalance(e, "consultas")
    }

    @Test
    fun consultasSinAtenderLasResuelveElEquipoAMitadDePrecio() {
        val e = GameEngine.newGame(2026)
        e.advance(GameEngine.DAY_SECONDS * 30)
        assertTrue(e.state.casesAuto > 0)
        assertEquals(e.state.casesAuto.toLong(), e.state.reputation)
        assertTrue(e.state.cases.size <= GameEngine.MAX_CASES)
        assertBooksBalance(e, "consultas automáticas")
    }

    @Test
    fun contratarFormarYRenovarEquipos() {
        val e = GameEngine.newGame(2026)
        injectCash(e, 50_000L)
        val led = e.state.ledger
        assertTrue(e.hire("fiscal"))
        assertEquals("El primer trabajador usa el ordenador que ya había", Money.euros(500L), led.natural("217"))
        assertTrue(e.hire("fiscal"))
        assertEquals(Money.euros(1_000L), led.natural("217"))
        assertFalse("En el bajo solo caben dos puestos por planta", e.hire("fiscal"))
        assertEquals(2.0 * Departments.trainingMultiplier(0), e.deptMultiplier("fiscal"), 1e-9)

        val training = e.trainingCost("fiscal")
        assertEquals(Money.euros(500L), training) // 250 € por puesto
        assertTrue(e.train("fiscal"))
        assertEquals(training, led.natural("649"))
        assertFalse("Con autónomos solo se llega al nivel 1 de formación", e.train("fiscal"))

        // Un mes de amortización y después se renuevan los equipos.
        e.advance(GameEngine.DAY_SECONDS * 31)
        val dep = e.dept("fiscal").equipmentDepreciation
        assertTrue(dep > 0)
        val bookValue = e.equipmentBookValue("fiscal")
        assertTrue(e.upgradeEquipment("fiscal"))
        assertEquals(1, e.dept("fiscal").equipmentTier)
        assertEquals(EquipmentTiers.get(1).price * 2, led.natural("217"))
        assertEquals(bookValue, led.natural("671"))
        assertEquals(0L, led.natural("2817"))
        assertFalse("En el bajo no hay equipos de nivel 2", e.upgradeEquipment("fiscal"))
        assertBooksBalance(e, "equipos")
    }

    @Test
    fun mudanzaDevuelveLaFianzaYDaDeBajaLasObras() {
        val e = GameEngine.newGame(2026)
        injectCash(e, 1_000_000L)
        assertFalse("Sin reputación no se puede cambiar de local", e.canMove())
        e.state.reputation = ClientTiers.get(1).reputation
        assertTrue(e.moveOffice())
        val led = e.state.ledger
        assertEquals(1, e.state.officeIndex)
        assertEquals(Offices.get(1).deposit, led.natural("260"))
        assertEquals(Offices.get(1).fitOut, led.natural("215"))
        assertTrue(e.deptAvailable("contable"))

        e.advance(GameEngine.DAY_SECONDS * 31) // un mes de amortización de las obras
        val pending = Offices.get(1).fitOut - e.state.fitOutDepreciation
        e.state.reputation = ClientTiers.get(2).reputation
        assertTrue(e.moveOffice())
        assertEquals(Offices.get(2).deposit, led.natural("260"))
        assertEquals(Offices.get(2).fitOut, led.natural("215"))
        assertEquals(pending, led.natural("671"))
        assertEquals(0L, led.natural("2815"))
        assertBooksBalance(e, "mudanzas")
    }

    @Test
    fun nominasAlquilerYRetencionesDelMes() {
        val e = GameEngine.newGame(2026)
        injectCash(e, 10_000L)
        assertTrue(e.hire("fiscal"))
        e.advance(GameEngine.DAY_SECONDS * 31) // enero completo
        val led = e.state.ledger
        assertEquals(Departments.get("fiscal").salary, led.natural("640"))
        assertEquals(Money.pct(Departments.get("fiscal").salary, 3215), led.natural("642"))
        assertEquals(Offices.get(0).rent, led.natural("621"))
        // Retenciones: IRPF de la nómina (12 %) y 19 % del alquiler (arrendador persona física).
        assertEquals(Money.pct(Departments.get("fiscal").salary, 1200), e.state.wh111)
        assertEquals(Money.pct(Offices.get(0).rent, 1900), e.state.wh115)
        assertTrue(led.natural("628") > 0)
        assertBooksBalance(e, "enero")
    }

    @Test
    fun liquidacionTrimestralDelIvaYModelosPresentadosEnPlazo() {
        val e = GameEngine.newGame(2026)
        injectCash(e, 10_000L)
        e.hire("fiscal")
        e.advance(GameEngine.DAY_SECONDS * 90) // 1T de 2026: 31 + 28 + 31 días
        assertEquals(GameDate.of(2026, 4, 1).epochDay, e.state.day)
        val led = e.state.ledger
        assertEquals("El IVA repercutido queda saldado", 0L, led.natural("477"))
        assertEquals("El IVA soportado queda saldado", 0L, led.natural("472"))
        val withholdings = e.state.obligations.first { it.kind == ObligationKind.WITHHOLDINGS }
        assertEquals(GameDate.of(2026, 4, 20).epochDay, withholdings.dueDay)

        e.advance(GameEngine.DAY_SECONDS * 50)
        val filed = e.state.obligations.filter { it.paidBy == PaidBy.GESTORIA }
        assertTrue("El departamento fiscal presenta los modelos", filed.isNotEmpty())
        assertTrue(filed.all { it.paidDay == it.dueDay && it.surcharge == 0L })
        assertBooksBalance(e, "tras el 1T")
    }

    @Test
    fun cierreDelEjercicioYReparto() {
        val e = GameEngine.newGame(2026)
        // Un gran encargo cobrado en enero asegura beneficio en el ejercicio.
        val fee = Money.euros(80_000L)
        e.state.ledger.post(e.date(), L("Honorarios extraordinarios (test)", "Extra fees (test)"), EntryBuilder().debit("572", fee).credit("705", fee))
        e.advance(GameEngine.DAY_SECONDS * 365)
        val s = e.state
        assertEquals(2027, s.ledger.year)
        val summary = s.lastYear!!
        assertEquals(2026, summary.year)
        assertTrue(summary.netResult > 0)
        val opening = s.ledger.recent.first { it.year == 2027 && it.number == 1 }
        assertTrue(opening.concept.es.startsWith("Asiento de apertura"))
        assertTrue(opening.lines.none { Pgc.get(it.account).isPyg })
        // Primer ejercicio con beneficio: 15 % de entidad de nueva creación.
        assertEquals(Money.pct(summary.taxableBase, 1500), summary.incomeTax)

        val pv = e.distributionPreview()!!
        assertEquals(minOf(Money.pct(pv.profit, 1000), Money.euros(600L)), pv.legalReserve) // 20 % de 3.000 € = 600 €
        assertTrue(pv.maxDividend <= pv.distributable)
        assertTrue(e.decideDistribution(1.0))
        assertNull(s.pendingDistribution)
        assertEquals(pv.legalReserve, s.ledger.natural("112"))
        assertTrue(Reports.balanceSheet(s.ledger).equity >= s.ledger.natural("100"))
        e.advance(GameEngine.DAY_SECONDS * 31)
        assertEquals(0L, s.ledger.natural("526"))
        assertEquals(pv.maxDividend - Money.pct(pv.maxDividend, 1900), s.ownerWealth)
        assertBooksBalance(e, "dividendos")
    }

    @Test
    fun guardadoYCargaReproducenLaPartida() {
        val e = GameEngine.newGame(2026, seed = 12345L)
        val bot = AutoPlayer(e)
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

    @Test(expected = IncompatibleSaveException::class)
    fun partidaDelPrototipoAnteriorSeRechaza() {
        SaveCodec.decode("{\"v\":1,\"startYear\":2026}")
    }

    @Test
    fun progresoFueraDeLaAplicacionConTope() {
        val e = GameEngine.newGame(2026)
        injectCash(e, 5_000L)
        e.hire("fiscal")
        val report = e.catchUp(12 * 3600.0)
        assertTrue(report.capped)
        assertEquals(GameEngine.OFFLINE_CAP_SECONDS, report.seconds, 0.001)
        assertEquals(960L, report.days)
        assertTrue(report.sales > 0)
        assertTrue(report.casesSolved > 0)
        assertBooksBalance(e, "tras 8 h fuera")
    }

    @Test
    fun partidaLargaSiempreCuadra() {
        val e = GameEngine.newGame(2026, seed = 99L)
        val bot = AutoPlayer(e, accuracy = 0.7)
        play(e, bot, 3 * 3600.0, checkEvery = 600.0) // tres horas de juego activo
        e.catchUp(8 * 3600.0) // y una noche fuera
        assertBooksBalance(e, "final")
    }
}
