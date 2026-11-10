package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.accounting.EntryBuilder
import com.alecu.balancetycoon.core.accounting.Ledger
import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.accounting.UnbalancedEntryException
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingTest {
    private val day = GameDate.of(2026, 1, 1)
    private val c = L("Prueba", "Test")

    @Test(expected = UnbalancedEntryException::class)
    fun rechazaAsientoDescuadrado() {
        Ledger(2026).post(day, c, EntryBuilder().debit("572", 1000).credit("100", 999))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaCuentaInexistente() {
        Ledger(2026).post(day, c, EntryBuilder().debit("999", 1000).credit("100", 1000))
    }

    @Test(expected = UnbalancedEntryException::class)
    fun rechazaAsientoDeUnaSolaLinea() {
        Ledger(2026).post(day, c, EntryBuilder().debit("572", 1000))
    }

    @Test
    fun saldosYBalanceCuadran() {
        val led = Ledger(2026)
        led.post(day, c, EntryBuilder().debit("572", 300_000).credit("100", 300_000))
        // Venta de 1.000 € + 21 % de IVA, la mitad a crédito.
        led.post(day, c, EntryBuilder().debit("572", 60_500).debit("430", 60_500).credit("700", 100_000).credit("477", 21_000))
        // Compra de 400 € + IVA a proveedores.
        led.post(day, c, EntryBuilder().debit("600", 40_000).debit("472", 8_400).credit("400", 48_400))

        assertEquals(360_500L, led.natural("572"))
        assertEquals(21_000L, led.natural("477"))
        val pyg = Reports.pyg(led)
        assertEquals(100_000L, pyg.revenue)
        assertEquals(-40_000L, pyg.supplies)
        assertEquals(60_000L, pyg.netResult)

        val bs = Reports.balanceSheet(led)
        assertTrue(bs.balanced)
        assertEquals(360_500L + 60_500L + 8_400L, bs.totalAssets)
        assertEquals(300_000L + 60_000L, bs.equity)

        val trial = Reports.trialBalance(led)
        assertEquals(trial.sumOf { it.debit }, trial.sumOf { it.credit })
    }

    @Test
    fun redondeoComercialAlCentimo() {
        assertEquals(21L, Money.pct(100L, 2100)) // 1,00 € al 21 % = 0,21 €
        assertEquals(1L, Money.pct(5L, 2100)) // 0,05 € × 21 % = 0,0105 → 0,01 €
        assertEquals(13L, Money.pct(125L, 1000)) // 1,25 € × 10 % = 0,125 → 0,13 € (mitad hacia arriba)
        assertEquals(0L, Money.pct(12_345L, 0))
    }

    @Test
    fun formatoDeCifrasEnEspanolEIngles() {
        assertEquals("1.234,56 €", Fmt.money(123_456, Lang.ES))
        assertEquals("€1,234.56", Fmt.money(123_456, Lang.EN))
        assertEquals("-0,05 €", Fmt.money(-5, Lang.ES))
        assertEquals("1,23 M€", Fmt.money(123_456_789, Lang.ES))
        assertEquals("€1.23M", Fmt.money(123_456_789, Lang.EN))
        assertEquals("4,50 mil M€", Fmt.money(450_000_000_000, Lang.ES))
        assertEquals("21%", Fmt.rate(2100, Lang.ES))
        assertEquals("32,15%", Fmt.rate(3215, Lang.ES))
        assertEquals("12/03/2026", Fmt.dateShort(GameDate.of(2026, 3, 12), Lang.ES))
        assertEquals("12 mar 2026", Fmt.dateLong(GameDate.of(2026, 3, 12), Lang.ES))
    }

    @Test
    fun calendarioYPlazos() {
        val q1 = GameDate.of(2026, 3, 31)
        assertTrue(q1.isQuarterEnd)
        assertEquals(GameDate.of(2026, 4, 20), q1.quarterlyFilingDeadline())
        assertEquals(GameDate.of(2027, 1, 30), GameDate.of(2026, 12, 31).quarterlyFilingDeadline())
        assertEquals(GameDate.of(2026, 3, 31), GameDate.of(2026, 2, 28).endOfNextMonth())
        assertFalse(GameDate.of(2026, 2, 27).isMonthEnd)
        assertTrue(GameDate.of(2028, 2, 29).isMonthEnd) // año bisiesto
    }
}
