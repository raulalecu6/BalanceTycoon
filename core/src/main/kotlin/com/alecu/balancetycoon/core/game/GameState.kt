package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.accounting.Ledger
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.tax.CorporateTaxRegime

/** Estado variable de una línea de negocio. */
class LineState(val id: String) {
    var level: Int = 0
    var hasManager: Boolean = false
    /** Ciclo manual en marcha (solo cuando no hay encargado). */
    var running: Boolean = false
    /** Segundos transcurridos del ciclo actual. */
    var progress: Double = 0.0
    /** Coste del inmovilizado de la línea y su amortización acumulada (grupo homogéneo). */
    var grossCost: Long = 0
    var accumulatedDepreciation: Long = 0
    /** Ventas netas del día aún no contabilizadas (se anotan en un asiento resumen al cerrar el día). */
    var todayRevenue: Long = 0
    var todayCycles: Long = 0
    var managerDaysThisMonth: Int = 0
    var lifetimeRevenue: Long = 0
}

/** Cobro (430) o pago (400) aplazado que vence en una fecha. */
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

/** Documento pendiente de clasificar en la bandeja. */
class PendingDocument(
    val id: Int,
    val templateId: String,
    val arrivalDay: Long,
    val base: Long,
    val options: List<String>,
) {
    val template: DocTemplate get() = Documents.get(templateId)
    val vat: Long get() = Money.pct(base, template.vat)
    val withholding: Long get() = Money.pct(base, template.withholding)
    val total: Long get() = base + vat - withholding
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
    var companyName: String = "Mi Empresa, S.L."

    /** Fecha actual (día de época) y segundos transcurridos dentro del día. */
    var day: Long = GameDate.of(startYear, 1, 1).epochDay
    var secondsIntoDay: Double = 0.0
    /** Segundos simulados desde el inicio de la partida (también fuera de la aplicación). */
    var playSeconds: Double = 0.0

    val ledger: Ledger = Ledger(startYear)
    val lines: LinkedHashMap<String, LineState> = LinkedHashMap()
    val flows: MutableList<PendingFlow> = mutableListOf()
    val obligations: MutableList<Obligation> = mutableListOf()
    var nextObligationId: Int = 1

    val documents: MutableList<PendingDocument> = mutableListOf()
    var nextDocId: Int = 1
    var nextDocDay: Long = day
    var lastDocTemplate: String = ""

    var streak: Int = 0
    var bestStreak: Int = 0
    var docsCorrect: Int = 0
    var docsWrong: Int = 0

    var hasAssistant: Boolean = false
    var assistantDaysThisMonth: Int = 0
    var gestoriaActive: Boolean = true
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
