package com.alecu.balancetycoon.core.tax

import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.money.Money

/** Tipos de IVA en puntos básicos (arts. 90 y 91 de la Ley 37/1992). */
object Vat {
    const val SUPER_REDUCED = 400 // pan común, harinas panificables, leche, quesos...
    const val REDUCED = 1000 // hostelería y alimentos en general
    const val GENERAL = 2100
    const val EXEMPT = 0 // seguros y servicios financieros (art. 20)
}

/** Retenciones de IRPF en puntos básicos. */
object Withholding {
    const val PROFESSIONALS = 1500 // art. 101.5 LIRPF (modelo 111)
    const val RENT = 1900 // arrendamiento de inmuebles urbanos (modelo 115)
    const val DIVIDENDS = 1900 // rendimientos del capital mobiliario (modelo 123)
    const val PAYROLL_AVERAGE = 1200 // simplificación del juego: en la realidad depende de cada trabajador
}

/**
 * Cotizaciones a la Seguridad Social en puntos básicos. Simplificación de los tipos de 2026:
 * contingencias comunes 23,60 + 4,70; desempleo 5,50 + 1,55; FOGASA 0,20; FP 0,60 + 0,10;
 * MEI 0,75 + 0,15; AT y EP estimado en 1,50 (varía según la actividad).
 */
object SocialSecurity {
    const val EMPLOYER = 3215
    const val EMPLOYEE = 650
}

/** Recargos por presentación extemporánea sin requerimiento previo (art. 27.2 LGT). */
object Surcharge {
    /** 1 % más un 1 % por cada mes completo de retraso; a partir de 12 meses, 15 %. */
    fun extemporaneousBp(daysLate: Long): Int {
        if (daysLate <= 0) return 0
        val fullMonths = (daysLate / 30).toInt()
        return if (fullMonths >= 12) 1500 else 100 + 100 * fullMonths
    }
}

/** Desglose del cálculo del Impuesto sobre Sociedades de un ejercicio. */
data class CorporateTaxResult(
    val accountingResult: Long,
    val adjustments: Long,
    val lossesOffset: Long,
    val taxableBase: Long,
    val quota: Long,
    val regime: CorporateTaxRegime,
)

enum class CorporateTaxRegime(val title: L) {
    NEW_ENTITY(L("Entidad de nueva creación", "Newly created entity")),
    MICRO(L("Microempresa (INCN < 1 M€)", "Micro-enterprise (revenue < €1M)")),
    SMALL(L("Empresa de reducida dimensión (INCN < 10 M€)", "Small company (revenue < €10M)")),
    GENERAL(L("Tipo general", "General rate")),
}

/**
 * Impuesto sobre Sociedades según el art. 29 y la DT 44.ª de la Ley 27/2014, en la redacción
 * dada por la Ley 7/2024. El juego no aplica deducciones, bonificaciones ni pagos fraccionados.
 */
object CorporateTax {
    const val GENERAL_RATE = 2500
    const val NEW_ENTITY_RATE = 1500
    val MICRO_LIMIT: Long = Money.euros(1_000_000L)
    val SMALL_LIMIT: Long = Money.euros(10_000_000L)
    val MICRO_FIRST_BRACKET: Long = Money.euros(50_000L)

    /** Límite de compensación de bases negativas: 70 % de la base, con un mínimo de 1 M€ (art. 26 LIS). */
    val LOSS_OFFSET_MINIMUM: Long = Money.euros(1_000_000L)
    const val LOSS_OFFSET_PCT = 7000

    /** Tipos de la microempresa (primeros 50.000 € / resto) según el año. */
    fun microRates(year: Int): Pair<Int, Int> = when {
        year <= 2025 -> 2100 to 2200
        year == 2026 -> 1900 to 2100
        else -> 1700 to 2000
    }

    /** Tipo de las empresas de reducida dimensión según el año. */
    fun smallRate(year: Int): Int = when {
        year <= 2025 -> 2400
        year == 2026 -> 2300
        year == 2027 -> 2200
        year == 2028 -> 2100
        else -> 2000
    }

    fun regimeFor(incn: Long, newEntity: Boolean): CorporateTaxRegime = when {
        newEntity -> CorporateTaxRegime.NEW_ENTITY
        incn < MICRO_LIMIT -> CorporateTaxRegime.MICRO
        incn < SMALL_LIMIT -> CorporateTaxRegime.SMALL
        else -> CorporateTaxRegime.GENERAL
    }

    /** Cuota íntegra para una base imponible ya compensada. */
    fun quota(base: Long, year: Int, regime: CorporateTaxRegime): Long {
        if (base <= 0) return 0
        return when (regime) {
            CorporateTaxRegime.NEW_ENTITY -> Money.pct(base, NEW_ENTITY_RATE)
            CorporateTaxRegime.MICRO -> {
                val (first, rest) = microRates(year)
                val firstPart = minOf(base, MICRO_FIRST_BRACKET)
                Money.pct(firstPart, first) + Money.pct(base - firstPart, rest)
            }
            CorporateTaxRegime.SMALL -> Money.pct(base, smallRate(year))
            CorporateTaxRegime.GENERAL -> Money.pct(base, GENERAL_RATE)
        }
    }

    /**
     * Calcula el impuesto de un ejercicio.
     * @param accountingResult resultado contable antes de impuestos.
     * @param nonDeductible gastos no deducibles del ejercicio (recargos y sanciones, art. 15 LIS).
     * @param pendingLosses bases imponibles negativas pendientes de ejercicios anteriores.
     * @param newEntityEligible true si el ejercicio puede aplicar el 15 % de nueva creación.
     * @return el desglose y las bases negativas que quedan pendientes.
     */
    fun compute(
        accountingResult: Long,
        nonDeductible: Long,
        pendingLosses: Long,
        incn: Long,
        year: Int,
        newEntityEligible: Boolean,
    ): Pair<CorporateTaxResult, Long> {
        val preliminary = accountingResult + nonDeductible
        var remainingLosses = pendingLosses
        var offset = 0L
        var base = preliminary
        if (base > 0 && remainingLosses > 0) {
            val limit = maxOf(LOSS_OFFSET_MINIMUM, Money.pct(base, LOSS_OFFSET_PCT))
            offset = minOf(remainingLosses, base, limit)
            base -= offset
            remainingLosses -= offset
        }
        if (base < 0) {
            remainingLosses += -base
            base = 0
        }
        val regime = regimeFor(incn, newEntityEligible && base > 0)
        val result = CorporateTaxResult(
            accountingResult = accountingResult,
            adjustments = nonDeductible,
            lossesOffset = offset,
            taxableBase = base,
            quota = quota(base, year, regime),
            regime = regime,
        )
        return result to remainingLosses
    }
}
