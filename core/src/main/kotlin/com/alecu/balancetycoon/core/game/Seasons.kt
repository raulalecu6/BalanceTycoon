package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.i18n.L

/**
 * Temporada del calendario fiscal y contable: durante esas fechas un departamento tiene más
 * trabajo y factura más. Se repite cada año y no hace falta guardarla.
 */
data class Season(
    val id: String,
    val dept: String,
    val emoji: String,
    val name: L,
    val description: L,
    val multiplier: Double,
    val fromMonth: Int,
    val fromDay: Int,
    val toMonth: Int,
    val toDay: Int,
) {
    private fun key(month: Int, day: Int) = month * 100 + day
    fun isActive(date: GameDate): Boolean = key(date.month, date.day) in key(fromMonth, fromDay)..key(toMonth, toDay)
    fun startsOn(date: GameDate): Boolean = date.month == fromMonth && date.day == fromDay
    fun endsOn(date: GameDate): Boolean = date.month == toMonth && date.day == toDay
}

object Seasons {
    val all: List<Season> = listOf(
        Season(
            "cierres", "contable", "📚", L("Cierres contables", "Year-end closing"),
            L(
                "Las empresas cierran el ejercicio: los administradores formulan las cuentas anuales en tres meses (art. 253 LSC) y legalizan los libros en cuatro. Contabilidad factura un 50 % más.",
                "Companies close their books: directors prepare the annual accounts within three months (art. 253 of the Companies Act) and legalise the books within four. Bookkeeping bills 50% more.",
            ),
            1.5, 1, 1, 3, 31,
        ),
        Season(
            "renta", "fiscal", "🧾", L("Campaña de la renta", "Income tax season"),
            L(
                "De abril a junio se presentan las declaraciones del IRPF del año anterior. El departamento fiscal factura el doble.",
                "From April to June, last year's personal income tax returns are filed. The tax department bills twice as much.",
            ),
            2.0, 4, 1, 6, 30,
        ),
        Season(
            "patrimonio", "patrimonios", "💎", L("Renta y Patrimonio", "Income and wealth tax"),
            L(
                "Junto con la renta se declara el Impuesto sobre el Patrimonio de los clientes con más bienes. Patrimonios factura un 50 % más.",
                "Along with income tax, wealthier clients file their wealth tax return. The wealth department bills 50% more.",
            ),
            1.5, 4, 1, 6, 30,
        ),
        Season(
            "juntas", "mercantil", "🗳️", L("Juntas y depósito de cuentas", "AGMs and filing of accounts"),
            L(
                "Las juntas aprueban las cuentas antes del 30 de junio (art. 164 LSC) y se depositan en el Registro Mercantil en el mes siguiente (art. 279). Mercantil factura un 50 % más.",
                "General meetings approve the accounts before 30 June (art. 164 of the Companies Act) and they are filed with the Companies Registry within a month (art. 279). Corporate law bills 50% more.",
            ),
            1.5, 6, 1, 7, 31,
        ),
        Season(
            "vendimia", "laboral", "🍇", L("Vendimia", "Grape harvest"),
            L(
                "Las bodegas y cooperativas de La Mancha contratan temporeros para la vendimia: altas, contratos y nóminas sin parar. Laboral factura un 50 % más.",
                "La Mancha's wineries and cooperatives hire seasonal workers for the harvest: registrations, contracts and payslips non-stop. Payroll bills 50% more.",
            ),
            1.5, 9, 1, 10, 31,
        ),
        Season(
            "planificacion", "consultoria", "📈", L("Planificación de fin de año", "Year-end planning"),
            L(
                "Antes del 31 de diciembre las empresas revisan su resultado para planificar el Impuesto sobre Sociedades y sus inversiones. Consultoría factura un 50 % más.",
                "Before 31 December companies review their results to plan corporate tax and investments. Advisory bills 50% more.",
            ),
            1.5, 11, 1, 12, 31,
        ),
    )

    fun active(date: GameDate): List<Season> = all.filter { it.isActive(date) }
}

/** Imprevistos que aparecen de vez en cuando y duran unos días. */
enum class IncidentKind {
    /** Los ordenadores de un departamento fallan: factura la mitad hasta que se reparan. */
    BREAKDOWN,

    /** Gripe en la plantilla de un departamento: factura un 25 % menos unos días. */
    FLU,

    /** Invitación a una jornada de networking: se paga la inscripción y se gana reputación. */
    NETWORKING,
}

class Incident(
    val id: Int,
    val kind: IncidentKind,
    val dept: String?,
    val startDay: Long,
    val endDay: Long,
    /** Coste sin IVA de la reparación o de la inscripción. */
    val cost: Long,
    /** Reputación que da aceptar la invitación. */
    val reward: Long,
) {
    val multiplier: Double
        get() = when (kind) {
            IncidentKind.BREAKDOWN -> 0.5
            IncidentKind.FLU -> 0.75
            IncidentKind.NETWORKING -> 1.0
        }
}
