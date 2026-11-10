package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.tax.CorporateTax
import com.alecu.balancetycoon.core.tax.CorporateTaxRegime
import com.alecu.balancetycoon.core.tax.Surcharge
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxTest {
    private fun e(v: Long) = Money.euros(v)

    @Test
    fun microempresa2026() {
        // 50.000 € al 19 % + 50.000 € al 21 % = 9.500 + 10.500 = 20.000 €
        assertEquals(e(20_000), CorporateTax.quota(e(100_000), 2026, CorporateTaxRegime.MICRO))
        // Por debajo del primer tramo: todo al 19 %
        assertEquals(e(5_700), CorporateTax.quota(e(30_000), 2026, CorporateTaxRegime.MICRO))
    }

    @Test
    fun microempresa2027EnAdelante() {
        // 50.000 € al 17 % + 50.000 € al 20 % = 8.500 + 10.000
        assertEquals(e(18_500), CorporateTax.quota(e(100_000), 2027, CorporateTaxRegime.MICRO))
        assertEquals(e(18_500), CorporateTax.quota(e(100_000), 2030, CorporateTaxRegime.MICRO))
    }

    @Test
    fun reducidaDimensionYGeneral() {
        assertEquals(e(23_000), CorporateTax.quota(e(100_000), 2026, CorporateTaxRegime.SMALL))
        assertEquals(e(22_000), CorporateTax.quota(e(100_000), 2027, CorporateTaxRegime.SMALL))
        assertEquals(e(20_000), CorporateTax.quota(e(100_000), 2029, CorporateTaxRegime.SMALL))
        assertEquals(e(25_000), CorporateTax.quota(e(100_000), 2026, CorporateTaxRegime.GENERAL))
    }

    @Test
    fun regimenSegunCifraDeNegocios() {
        assertEquals(CorporateTaxRegime.MICRO, CorporateTax.regimeFor(e(999_999), false))
        assertEquals(CorporateTaxRegime.SMALL, CorporateTax.regimeFor(e(1_000_000), false))
        assertEquals(CorporateTaxRegime.GENERAL, CorporateTax.regimeFor(e(10_000_000), false))
        assertEquals(CorporateTaxRegime.NEW_ENTITY, CorporateTax.regimeFor(e(50_000_000), true))
    }

    @Test
    fun nuevaCreacionAl15() {
        val (r, _) = CorporateTax.compute(e(40_000), 0, 0, e(200_000), 2026, newEntityEligible = true)
        assertEquals(CorporateTaxRegime.NEW_ENTITY, r.regime)
        assertEquals(e(6_000), r.quota)
    }

    @Test
    fun compensacionDeBasesNegativasYGastosNoDeducibles() {
        // Pérdida de 10.000 €: no hay cuota y queda una base negativa pendiente.
        val (loss, pending) = CorporateTax.compute(-e(10_000), 0, 0, e(50_000), 2026, false)
        assertEquals(0L, loss.quota)
        assertEquals(e(10_000), pending)
        // Al año siguiente, beneficio de 30.000 € con 500 € de recargos no deducibles.
        val (r, left) = CorporateTax.compute(e(30_000), e(500), pending, e(50_000), 2027, false)
        assertEquals(e(10_000), r.lossesOffset)
        assertEquals(e(20_500), r.taxableBase)
        assertEquals(Money.pct(e(20_500), 1700), r.quota)
        assertEquals(0L, left)
    }

    @Test
    fun recargosArticulo27LGT() {
        assertEquals(0, Surcharge.extemporaneousBp(0))
        assertEquals(100, Surcharge.extemporaneousBp(10)) // menos de un mes: 1 %
        assertEquals(200, Surcharge.extemporaneousBp(30)) // un mes completo: 2 %
        assertEquals(1200, Surcharge.extemporaneousBp(359)) // 11 meses: 12 %
        assertEquals(1500, Surcharge.extemporaneousBp(400)) // más de 12 meses: 15 %
    }
}
