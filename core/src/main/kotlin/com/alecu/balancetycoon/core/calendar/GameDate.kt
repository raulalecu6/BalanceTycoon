package com.alecu.balancetycoon.core.calendar

import java.time.LocalDate

/**
 * Fecha del calendario contable del juego, guardada como día de época (días desde el 1/1/1970).
 * Se apoya en java.time, disponible en Android desde la API 26 (el minSdk del proyecto).
 */
data class GameDate(val epochDay: Long) : Comparable<GameDate> {
    private val local: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    val year: Int get() = local.year
    val month: Int get() = local.monthValue
    val day: Int get() = local.dayOfMonth
    val quarter: Int get() = (month - 1) / 3 + 1
    val lengthOfMonth: Int get() = local.lengthOfMonth()

    val isMonthEnd: Boolean get() = day == lengthOfMonth
    val isQuarterEnd: Boolean get() = isMonthEnd && month % 3 == 0
    val isYearEnd: Boolean get() = month == 12 && day == 31

    fun plusDays(days: Long): GameDate = GameDate(epochDay + days)

    /** Último día del mes siguiente (vencimiento de los seguros sociales). */
    fun endOfNextMonth(): GameDate {
        val next = local.withDayOfMonth(1).plusMonths(1)
        return GameDate(next.withDayOfMonth(next.lengthOfMonth()).toEpochDay())
    }

    /**
     * Plazo de las autoliquidaciones trimestrales (303, 111, 115, 123): del 1 al 20 del mes
     * siguiente al trimestre, salvo el cuarto trimestre, que se presenta hasta el 30 de enero.
     */
    fun quarterlyFilingDeadline(): GameDate {
        val next = local.withDayOfMonth(1).plusMonths(1)
        val limit = if (next.monthValue == 1) 30 else 20
        return GameDate(next.withDayOfMonth(limit).toEpochDay())
    }

    override fun compareTo(other: GameDate): Int = epochDay.compareTo(other.epochDay)

    companion object {
        fun of(year: Int, month: Int, day: Int): GameDate = GameDate(LocalDate.of(year, month, day).toEpochDay())
    }
}
