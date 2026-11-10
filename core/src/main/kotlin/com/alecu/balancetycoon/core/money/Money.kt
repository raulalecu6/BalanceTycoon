package com.alecu.balancetycoon.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Utilidades de importes.
 *
 * - Todos los importes se guardan en céntimos con Long, nunca en coma flotante, porque los
 *   decimales binarios producen descuadres de céntimos al sumar miles de asientos.
 * - Los tipos (IVA, retenciones, cotizaciones) se expresan en puntos básicos: 2100 = 21,00 %.
 * - El redondeo es el comercial (mitad hacia arriba), el habitual en facturación.
 */
object Money {
    /** Límite práctico para evitar desbordamientos en sumas: unos 23.000 billones de euros. */
    const val MAX: Long = Long.MAX_VALUE / 4

    fun euros(value: Long): Long = Math.multiplyExact(value, 100L)

    fun euros(value: Double): Long =
        BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

    /** Aplica un tipo en puntos básicos a una base en céntimos, con redondeo al céntimo. */
    fun pct(baseCents: Long, basisPoints: Int): Long {
        if (baseCents == 0L || basisPoints == 0) return 0L
        return BigDecimal.valueOf(baseCents)
            .multiply(BigDecimal.valueOf(basisPoints.toLong()))
            .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /** Multiplica por un factor real (multiplicadores de juego) con redondeo y saturación. */
    fun times(cents: Long, factor: Double): Long {
        val r = cents.toDouble() * factor
        if (r.isNaN()) return 0L
        if (r >= MAX.toDouble()) return MAX
        if (r <= -MAX.toDouble()) return -MAX
        return Math.round(r)
    }

    /** Suma con saturación en ±MAX. */
    fun add(a: Long, b: Long): Long {
        val r = a + b
        return when {
            r > MAX -> MAX
            r < -MAX -> -MAX
            else -> r
        }
    }
}
