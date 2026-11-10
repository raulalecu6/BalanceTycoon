package com.alecu.balancetycoon.core.format

import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.i18n.Lang
import kotlin.math.abs

/**
 * Formato de cifras y fechas sin depender de la configuración regional del dispositivo,
 * para que el juego se vea igual en todos los teléfonos y los tests sean deterministas.
 *
 * - Español: 1.234,56 € · 1,23 M€ · 4,50 mil M€ · 2,10 B€ (billón = 10^12)
 * - Inglés: €1,234.56 · €1.23M · €4.50B · €2.10T
 */
object Fmt {
    private val MONTHS_ES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val MONTHS_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_LONG_ES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    private val MONTHS_LONG_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** Importe en céntimos. Con [compact], las cifras de un millón o más se abrevian. */
    fun money(cents: Long, lang: Lang, compact: Boolean = true): String {
        val negative = cents < 0
        val absCents = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else abs(cents)
        val body: String = if (compact && absCents >= 100_000_000L) compactBody(absCents, lang) else fullBody(absCents, lang)
        val sign = if (negative) "-" else ""
        return when (lang) {
            Lang.ES -> "$sign$body"
            Lang.EN -> "$sign€$body"
        }
    }

    /** Importe sin decimales (para cifras grandes de la interfaz que no necesitan céntimos). */
    fun moneyNoCents(cents: Long, lang: Lang): String {
        val euros = Math.round(cents / 100.0)
        if (abs(euros) >= 1_000_000L) return money(cents, lang, compact = true)
        val sign = if (euros < 0) "-" else ""
        val grouped = group(abs(euros), if (lang == Lang.ES) '.' else ',')
        return if (lang == Lang.ES) "$sign$grouped €" else "$sign€$grouped"
    }

    private fun fullBody(absCents: Long, lang: Lang): String {
        val euros = absCents / 100
        val cents = absCents % 100
        return when (lang) {
            Lang.ES -> group(euros, '.') + "," + cents.toString().padStart(2, '0') + " €"
            Lang.EN -> group(euros, ',') + "." + cents.toString().padStart(2, '0')
        }
    }

    private fun compactBody(absCents: Long, lang: Lang): String {
        val euros = absCents / 100.0
        val (value, suffix) = when (lang) {
            Lang.ES -> when {
                euros >= 1e12 -> euros / 1e12 to " B€"
                euros >= 1e9 -> euros / 1e9 to " mil M€"
                else -> euros / 1e6 to " M€"
            }
            Lang.EN -> when {
                euros >= 1e12 -> euros / 1e12 to "T"
                euros >= 1e9 -> euros / 1e9 to "B"
                else -> euros / 1e6 to "M"
            }
        }
        return decimal2(value, lang) + suffix
    }

    /** Número con dos decimales y separadores del idioma. */
    fun decimal2(value: Double, lang: Lang): String {
        val hundredths = Math.round(value * 100.0)
        val intPart = hundredths / 100
        val dec = (abs(hundredths) % 100).toString().padStart(2, '0')
        return when (lang) {
            Lang.ES -> group(intPart, '.') + "," + dec
            Lang.EN -> group(intPart, ',') + "." + dec
        }
    }

    fun integer(value: Long, lang: Lang): String {
        val sign = if (value < 0) "-" else ""
        return sign + group(abs(value), if (lang == Lang.ES) '.' else ',')
    }

    /** Tipo en puntos básicos: 2100 → "21%", 1050 → "10,5%". */
    fun rate(basisPoints: Int, lang: Lang): String {
        val whole = basisPoints / 100
        val frac = basisPoints % 100
        if (frac == 0) return "$whole%"
        val decimals = if (frac % 10 == 0) (frac / 10).toString() else frac.toString().padStart(2, '0')
        val sep = if (lang == Lang.ES) "," else "."
        return "$whole$sep$decimals%"
    }

    /** 12/03/2026 (ES) · 2026-03-12 (EN). */
    fun dateShort(date: GameDate, lang: Lang): String {
        val d = date.day.toString().padStart(2, '0')
        val m = date.month.toString().padStart(2, '0')
        return if (lang == Lang.ES) "$d/$m/${date.year}" else "${date.year}-$m-$d"
    }

    /** 12 mar 2026 (ES) · Mar 12, 2026 (EN). */
    fun dateLong(date: GameDate, lang: Lang): String =
        if (lang == Lang.ES) "${date.day} ${MONTHS_ES[date.month - 1]} ${date.year}"
        else "${MONTHS_EN[date.month - 1]} ${date.day}, ${date.year}"

    /** marzo de 2026 · March 2026. */
    fun monthYear(date: GameDate, lang: Lang): String =
        if (lang == Lang.ES) "${MONTHS_LONG_ES[date.month - 1]} de ${date.year}"
        else "${MONTHS_LONG_EN[date.month - 1]} ${date.year}"

    private fun group(value: Long, sep: Char): String {
        val s = abs(value).toString()
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(sep)
            sb.append(c)
        }
        return (if (value < 0) "-" else "") + sb.toString()
    }
}
