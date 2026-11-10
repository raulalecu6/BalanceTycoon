package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.accounting.Ledger
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.tax.CorporateTaxRegime

/** Estado variable de un departamento (una planta del edificio). */
class DeptState(val id: String) {
    /** Nivel de cartera: cuántos clientes atiende. 0 = departamento cerrado. */
    var level: Int = 0
    var workers: Int = 0
    var training: Int = 0
    var equipmentTier: Int = 0
    /** Expediente manual en marcha (solo cuando no hay trabajadores). */
    var running: Boolean = false
    /** Segundos transcurridos del expediente actual. */
    var progress: Double = 0.0
    /** Coste y amortización acumulada de los ordenadores del departamento (217 y 2817). */
    var equipmentGross: Long = 0
    var equipmentDepreciation: Long = 0
    /** Honorarios del día aún no contabilizados (se anotan en un asiento resumen al cerrar el día). */
    var todayRevenue: Long = 0
    var todayCycles: Long = 0
    /** Suma de trabajadores por día del mes, para prorratear las nóminas de quien entra a mitad de mes. */
    var workerDaysThisMonth: Long = 0
    var lifetimeRevenue: Long = 0
    /** Expedientes terminados desde el inicio (para la animación de la torre). */
    var completedCycles: Long = 0

    /** Puestos con ordenador: al menos el de la persona que abre el departamento. */
    val desks: Int get() = maxOf(1, workers)
}

/** Cobro (430) o pago (400/410) aplazado que vence en una fecha. */
class PendingFlow(val dueDay: Long, val account: String, val amount: Long)

enum class ObligationKind { VAT, WITHHOLDINGS, SOCIAL_SECURITY, CORPORATE_TAX, DIVIDEND }

enum class PaidBy { PLAYER, GESTORIA, DIRECT_DEBIT }

/** Deuda con Hacienda, la Seguridad Social o los socios, con su fecha de vencimiento. */
class Obligation(
    val id: Int,
    val kind: ObligationKind,
    val label: L,
    val account: String,
    val amount: Long,
    val createdDay: Long,
    val dueDay: Long,
) {
    var paidDay: Long? = null
    var paidBy: PaidBy? = null
    var surcharge: Long = 0
    var fee: Long = 0
    val isPaid: Boolean get() = paidDay != null
}

/** Consulta de un cliente pendiente de resolver. [order] es el orden barajado de las opciones. */
class PendingCase(
    val id: Int,
    val templateId: String,
    val arrivalDay: Long,
    /** Honorarios netos que se facturan si se resuelve bien. */
    val fee: Long,
    /** Tipo de cliente con el que llegó (fija la reputación que da). */
    val tier: Int,
    val order: List<Int>,
) {
    val template: CaseTemplate get() = Cases.get(templateId)
}

/** Beneficio de un ejercicio cerrado pendiente de que la junta acuerde su aplicación. */
class PendingDistribution(val year: Int, val result: Long)

/** Resumen de un ejercicio cerrado, para comparar con el actual. */
class YearSummary(
    val year: Int,
    val revenue: Long,
    val netResult: Long,
    val incomeTax: Long,
    val totalAssets: Long,
    val equity: Long,
    val regime: CorporateTaxRegime?,
    val taxableBase: Long,
    val lossesOffset: Long,
)

enum class EventKind { INFO, GOOD, WARNING, TAX }

class GameEvent(val seq: Long, val day: Long, val text: L, val kind: EventKind)

/** Estado completo de una partida. Todo lo que hay aquí se guarda en el archivo de partida. */
class GameState(val startYear: Int) {
    var companyName: String = "Mi Gestoría, S.L."

    /** Fecha actual (día de época) y segundos transcurridos dentro del día. */
    var day: Long = GameDate.of(startYear, 1, 1).epochDay
    var secondsIntoDay: Double = 0.0
    /** Segundos simulados desde el inicio de la partida (también fuera de la aplicación). */
    var playSeconds: Double = 0.0

    val ledger: Ledger = Ledger(startYear)
    val depts: LinkedHashMap<String, DeptState> = LinkedHashMap()
    val flows: MutableList<PendingFlow> = mutableListOf()
    val obligations: MutableList<Obligation> = mutableListOf()
    var nextObligationId: Int = 1

    // Local, programa de gestión y reputación
    var officeIndex: Int = 0
    var fitOutGross: Long = 0
    var fitOutDepreciation: Long = 0
    var softwareTier: Int = 0
    var softwareGross: Long = 0
    var softwareDepreciation: Long = 0
    var reputation: Long = 0
    var bestTier: Int = 0

    // Consultas de clientes
    val cases: MutableList<PendingCase> = mutableListOf()
    var nextCaseId: Int = 1
    var nextCaseDay: Long = day
    val recentCases: ArrayDeque<String> = ArrayDeque()
    var streak: Int = 0
    var bestStreak: Int = 0
    var casesCorrect: Int = 0
    var casesWrong: Int = 0
    var casesAuto: Int = 0

    var boostUntil: Double = 0.0
    var rngState: Long = 88172645463325252L

    // Fiscalidad
    var prevYearIncn: Long = 0
    var pendingLosses: Long = 0
    /** Primer ejercicio con base imponible positiva (−1 si aún no lo ha habido). */
    var firstPositiveBaseYear: Int = -1
    var nonDeductible: Long = 0
    var wh111: Long = 0
    var wh115: Long = 0
    var wh123: Long = 0
    var pendingDistribution: PendingDistribution? = null
    var lastYear: YearSummary? = null

    // Tesorería y estadísticas
    var creditDaySum: Long = 0
    val salesLast30: LongArray = LongArray(30)
    var todaySales: Long = 0
    var lifetimeSales: Long = 0
    var ownerWealth: Long = 0

    val events: ArrayDeque<GameEvent> = ArrayDeque()
    var eventSeq: Long = 0

    /** Momento real (milisegundos) del último guardado, para calcular el progreso fuera de la aplicación. */
    var lastSavedMillis: Long = 0
}
