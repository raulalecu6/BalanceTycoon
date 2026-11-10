package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.accounting.EntryBuilder
import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.tax.CorporateTax
import com.alecu.balancetycoon.core.tax.SocialSecurity
import com.alecu.balancetycoon.core.tax.Surcharge
import com.alecu.balancetycoon.core.tax.Vat
import com.alecu.balancetycoon.core.tax.Withholding
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Resultado de clasificar un documento de la bandeja. */
data class DocResult(val correct: Boolean, val correctAccount: String, val explanation: L, val streak: Int)

/** Propuesta de aplicación del resultado según la LSC. */
data class DistributionPreview(
    val year: Int,
    val profit: Long,
    val legalReserve: Long,
    val lossOffset: Long,
    val distributable: Long,
    val maxDividend: Long,
)

/** Resumen de lo ocurrido mientras la aplicación estaba cerrada. */
data class OfflineReport(
    val seconds: Double,
    val capped: Boolean,
    val days: Long,
    val sales: Long,
    val cashChange: Long,
    val entries: Long,
    val events: List<GameEvent>,
)

enum class PayResult { OK, NOT_FOUND, ALREADY_PAID }

/**
 * Motor del juego. Hace avanzar el tiempo, produce las ventas y genera todos los asientos.
 * No conoce Android: la app solo llama a [advance] y a las acciones del jugador.
 */
class GameEngine(val state: GameState) {
    private val ledger get() = state.ledger

    companion object {
        /** Un día contable dura 30 segundos reales: un mes, 15 minutos; un ejercicio, unas 3 horas. */
        const val DAY_SECONDS = 30.0
        const val OFFLINE_CAP_SECONDS = 8 * 3600.0
        const val DOC_INTERVAL_DAYS = 2L
        const val DOC_AUTO_DAYS = 10L
        const val MAX_DOCS = 12
        const val STREAK_STEP = 0.02
        const val STREAK_CAP = 10
        const val BOOST_SECONDS = 2 * 3600.0
        const val CREDIT_RATE_BP = 700
        const val LATE_AUTO_DAYS = 30L
        const val DIVIDEND_PAYMENT_DAYS = 30L
        const val MAX_EVENTS = 80
        val GESTORIA_FILING_FEE: Long = Money.euros(45L)
        val GESTORIA_DOC_FEE: Long = Money.euros(15L)
        val INITIAL_CAPITAL: Long = Money.euros(3_000L)

        /** Crea una partida nueva: constitución de la sociedad, primer horno y factura de la notaría. */
        fun newGame(startYear: Int, seed: Long = 88172645463325252L, companyName: String = "Mi Empresa, S.L."): GameEngine {
            val s = GameState(startYear)
            s.companyName = companyName
            s.rngState = if (seed == 0L) 88172645463325252L else seed
            val engine = GameEngine(s)
            engine.setupNewCompany()
            return engine
        }
    }

    private fun setupNewCompany() {
        for (def in Lines.all) state.lines[def.id] = LineState(def.id)
        post(
            L("Constitución de la sociedad: aportación dineraria de los socios", "Incorporation: cash contribution from the partners"),
            EntryBuilder().debit("572", INITIAL_CAPITAL).credit("100", INITIAL_CAPITAL),
        )
        buyLevels("bakery", 1)
        addDocument(Documents.notary)
        state.nextDocDay = state.day + DOC_INTERVAL_DAYS
        event(
            L("Se ha constituido ${state.companyName} con un capital de 3.000 €.", "${state.companyName} has been incorporated with €3,000 of share capital."),
            EventKind.GOOD,
        )
    }

    // ───────────────────────────── Consultas ─────────────────────────────

    fun date(): GameDate = GameDate(state.day)
    fun cash(): Long = ledger.natural("572")
    fun creditDrawn(): Long = ledger.natural("5201")
    fun line(id: String): LineState = state.lines.getValue(id)
    fun dayProgress(): Double = state.secondsIntoDay / DAY_SECONDS

    fun boostActive(): Boolean = state.boostUntil > state.playSeconds
    fun boostRemainingSeconds(): Double = max(0.0, state.boostUntil - state.playSeconds)

    /** Multiplicador de ventas: racha de rigor contable (+2 % por acierto, máx. +20 %) y turbo ×2. */
    fun salesMultiplier(): Double {
        val streakBonus = 1.0 + min(state.streak, STREAK_CAP) * STREAK_STEP
        val boost = if (boostActive()) 2.0 else 1.0
        return streakBonus * boost
    }

    fun cycleSeconds(id: String): Double {
        val def = Lines.get(id)
        val ms = Lines.milestonesReached(line(id).level)
        return def.cycleSeconds / (1 shl ms)
    }

    /** Venta neta por ciclo con los multiplicadores actuales. */
    fun revenuePerCycle(id: String): Long {
        val def = Lines.get(id)
        val ls = line(id)
        return Money.times(def.revenuePerCycle * ls.level, salesMultiplier())
    }

    fun revenuePerSecond(id: String): Double {
        val ls = line(id)
        if (ls.level == 0) return 0.0
        return revenuePerCycle(id) / cycleSeconds(id)
    }

    /** Coste sin IVA de comprar [count] niveles. */
    fun upgradeCost(id: String, count: Int): Long = Lines.costOf(Lines.get(id), line(id).level, count)

    /** Dinero necesario en el banco (coste más IVA soportado del 21 %). */
    fun upgradeCashNeeded(id: String, count: Int): Long {
        val cost = upgradeCost(id, count)
        return cost + Money.pct(cost, Vat.GENERAL)
    }

    /** Número máximo de niveles que se pueden pagar ahora con el saldo del banco (tope 1.000). */
    fun maxAffordable(id: String): Int {
        val def = Lines.get(id)
        var level = line(id).level
        var spent = 0L
        var count = 0
        val available = cash()
        while (count < 1000) {
            val c = Lines.nextLevelCost(def, level)
            val withVat = c + Money.pct(c, Vat.GENERAL)
            if (spent + withVat > available) break
            spent += withVat
            level++
            count++
        }
        return count
    }

    /** Coste mensual estimado de la plantilla (salario bruto más cuota patronal). */
    fun monthlyPayrollCost(): Long {
        var gross = 0L
        for (def in Lines.all) if (line(def.id).hasManager) gross += def.managerSalary
        if (state.hasAssistant) gross += ASSISTANT_SALARY
        return gross + Money.pct(gross, SocialSecurity.EMPLOYER)
    }

    /** IVA que saldría a ingresar si el trimestre se cerrara hoy. */
    fun vatEstimate(): Long = ledger.natural("477") - ledger.natural("472") - ledger.natural("4700")

    /** Todo lo que la empresa debe a Hacienda y a la Seguridad Social, incluido el IVA estimado. */
    fun publicDebtEstimate(): Long =
        max(0L, vatEstimate()) + ledger.natural("4750") + ledger.natural("4751") + ledger.natural("4752") + ledger.natural("476")

    fun pendingObligations(): List<Obligation> = state.obligations.filter { !it.isPaid }.sortedBy { it.dueDay }

    fun receivables(): Long = ledger.natural("430")
    fun payables(): Long = ledger.natural("400") + ledger.natural("410")

    // ───────────────────────────── Acciones del jugador ─────────────────────────────

    /** Pone en marcha un ciclo manual («Vender»). Solo si la línea no tiene encargado. */
    fun tapLine(id: String): Boolean {
        val ls = line(id)
        if (ls.level == 0 || ls.hasManager || ls.running) return false
        ls.running = true
        ls.progress = 0.0
        return true
    }

    /** Compra niveles: inversión en inmovilizado material pagada al contado con IVA soportado. */
    fun buyLevels(id: String, count: Int): Boolean {
        if (count <= 0) return false
        val def = Lines.get(id)
        val ls = line(id)
        val cost = Lines.costOf(def, ls.level, count)
        val vat = Money.pct(cost, Vat.GENERAL)
        if (cash() < cost + vat) return false
        val firstLevel = ls.level == 0
        post(
            L(
                "${if (firstLevel) "Apertura" else "Ampliación"} · ${def.name.es} (${def.assetName.es}, +$count ${if (count == 1) "nivel" else "niveles"})",
                "${if (firstLevel) "Opening" else "Expansion"} · ${def.name.en} (${def.assetName.en}, +$count ${if (count == 1) "level" else "levels"})",
            ),
            EntryBuilder().debit(def.assetAccount, cost).debit("472", vat).credit("572", cost + vat),
        )
        val before = ls.level
        ls.level += count
        ls.grossCost += cost
        if (firstLevel && id != "bakery") {
            event(L("Has abierto: ${def.name.es}.", "You opened: ${def.name.en}."), EventKind.GOOD)
        }
        val ms = MILESTONES.filter { it in (before + 1)..ls.level }
        if (ms.isNotEmpty()) {
            event(
                L("${def.name.es} llega al nivel ${ms.last()}: el ciclo es el doble de rápido.", "${def.name.en} reached level ${ms.last()}: cycles are twice as fast."),
                EventKind.GOOD,
            )
        }
        return true
    }

    fun hireManager(id: String): Boolean {
        val ls = line(id)
        if (ls.level == 0 || ls.hasManager) return false
        ls.hasManager = true
        val def = Lines.get(id)
        event(
            L(
                "Contratado: ${def.managerTitle.es} (${def.name.es}). Salario de ${fmt(def.managerSalary, Lang.ES)} al mes.",
                "Hired: ${def.managerTitle.en} (${def.name.en}). Salary of ${fmt(def.managerSalary, Lang.EN)} per month.",
            ),
            EventKind.INFO,
        )
        return true
    }

    fun fireManager(id: String): Boolean {
        val ls = line(id)
        if (!ls.hasManager) return false
        ls.hasManager = false
        ls.running = false
        ls.progress = 0.0
        return true
    }

    fun hireAssistant(): Boolean {
        if (state.hasAssistant) return false
        state.hasAssistant = true
        event(L("Contratado un auxiliar contable: la bandeja se clasifica sola.", "Accounting assistant hired: the inbox is processed automatically."), EventKind.INFO)
        return true
    }

    fun fireAssistant(): Boolean {
        if (!state.hasAssistant) return false
        state.hasAssistant = false
        return true
    }

    fun setGestoria(active: Boolean) {
        state.gestoriaActive = active
    }

    /** Recompensa de un anuncio (o de la compra Premium): ventas ×2 durante 2 horas reales. */
    fun activateBoost() {
        state.boostUntil = max(state.boostUntil, state.playSeconds) + BOOST_SECONDS
    }

    /** El jugador elige una cuenta para un documento. El asiento siempre se registra en la correcta. */
    fun answerDocument(docId: Int, account: String): DocResult? {
        val doc = state.documents.firstOrNull { it.id == docId } ?: return null
        val t = doc.template
        val correct = account == t.correctAccount
        resolveDocument(doc, byGestoria = false)
        if (correct) {
            state.streak++
            state.bestStreak = max(state.bestStreak, state.streak)
            state.docsCorrect++
        } else {
            state.streak = 0
            state.docsWrong++
        }
        return DocResult(correct, t.correctAccount, t.explanation, state.streak)
    }

    /** Pago anticipado por el jugador desde la pantalla de Hacienda: sin comisión de la gestoría. */
    fun payObligation(id: Int): PayResult {
        val o = state.obligations.firstOrNull { it.id == id } ?: return PayResult.NOT_FOUND
        if (o.isPaid) return PayResult.ALREADY_PAID
        settle(o, PaidBy.PLAYER)
        return PayResult.OK
    }

    fun distributionPreview(): DistributionPreview? {
        val p = state.pendingDistribution ?: return null
        val profit = p.result
        val capital = ledger.natural("100")
        val legal = ledger.natural("112")
        val legalNeeded = max(0L, Money.pct(capital, 2000) - legal) // art. 274 LSC: hasta el 20 % del capital
        val legalReserve = min(Money.pct(profit, 1000), legalNeeded) // 10 % del beneficio
        val priorLosses = max(0L, ledger.natural("121"))
        val lossOffset = min(priorLosses, profit - legalReserve)
        val distributable = profit - legalReserve - lossOffset
        val equity = Reports.balanceSheet(ledger).equity
        val maxDividend = max(0L, min(distributable, equity - capital)) // art. 273 LSC
        return DistributionPreview(p.year, profit, legalReserve, lossOffset, distributable, maxDividend)
    }

    /**
     * Acuerdo de aplicación del resultado. [dividendShare] es la parte del dividendo máximo
     * que se reparte (0 = todo a reservas, 1 = máximo legal).
     */
    fun decideDistribution(dividendShare: Double): Boolean {
        val pv = distributionPreview() ?: return false
        val dividend = Money.times(pv.maxDividend, dividendShare.coerceIn(0.0, 1.0))
        val reserves = pv.distributable - dividend
        post(
            L("Aplicación del resultado de ${pv.year} (junta general ordinaria)", "Appropriation of ${pv.year} profit (annual general meeting)"),
            EntryBuilder().debit("129", pv.profit).credit("112", pv.legalReserve).credit("121", pv.lossOffset)
                .credit("113", reserves).credit("526", dividend),
        )
        if (dividend > 0) {
            addObligation(
                ObligationKind.DIVIDEND,
                L("Dividendo del ejercicio ${pv.year}", "Dividend for ${pv.year}"),
                "526", dividend, state.day + DIVIDEND_PAYMENT_DAYS,
            )
        }
        state.pendingDistribution = null
        event(
            L(
                "La junta aprueba el reparto de ${pv.year}: ${fmt(reserves + pv.legalReserve, Lang.ES)} a reservas y ${fmt(dividend, Lang.ES)} en dividendos.",
                "The AGM approves the ${pv.year} appropriation: ${fmt(reserves + pv.legalReserve, Lang.EN)} to reserves and ${fmt(dividend, Lang.EN)} in dividends.",
            ),
            EventKind.GOOD,
        )
        return true
    }

    // ───────────────────────────── Simulación ─────────────────────────────

    /** Avanza el juego [seconds] segundos reales. Se usa en cada fotograma y para ponerse al día. */
    fun advance(seconds: Double) {
        var remaining = if (seconds.isNaN()) 0.0 else max(0.0, seconds)
        while (remaining > 1e-9) {
            val step = min(remaining, DAY_SECONDS - state.secondsIntoDay)
            advanceLines(step)
            state.secondsIntoDay += step
            state.playSeconds += step
            remaining -= step
            if (state.secondsIntoDay >= DAY_SECONDS - 1e-9) {
                state.secondsIntoDay = 0.0
                endOfDay()
            }
        }
    }

    /** Progreso fuera de la aplicación, con el tope de 8 horas, en una sola llamada. */
    fun catchUp(elapsedSeconds: Double): OfflineReport {
        val snap = snapshot()
        advance(elapsedSeconds.coerceIn(0.0, OFFLINE_CAP_SECONDS))
        return reportSince(snap, elapsedSeconds)
    }

    /** Foto del estado antes de ponerse al día; la app la usa para avanzar por tramos sin bloquear la pantalla. */
    class Snapshot internal constructor(val cash: Long, val sales: Long, val day: Long, val entries: Long, val eventSeq: Long)

    fun snapshot(): Snapshot = Snapshot(cash(), state.lifetimeSales, state.day, ledger.totalPosted, state.eventSeq)

    fun reportSince(snap: Snapshot, elapsedSeconds: Double): OfflineReport = OfflineReport(
        seconds = elapsedSeconds.coerceIn(0.0, OFFLINE_CAP_SECONDS),
        capped = elapsedSeconds > OFFLINE_CAP_SECONDS,
        days = state.day - snap.day,
        sales = state.lifetimeSales - snap.sales,
        cashChange = cash() - snap.cash,
        entries = ledger.totalPosted - snap.entries,
        events = state.events.filter { it.seq > snap.eventSeq },
    )

    private fun advanceLines(dt: Double) {
        val mult = salesMultiplier()
        for (def in Lines.all) {
            val ls = line(def.id)
            if (ls.level <= 0) continue
            val t = cycleSeconds(def.id)
            if (ls.hasManager) {
                ls.progress += dt
                if (ls.progress >= t) {
                    val n = floor(ls.progress / t).toLong()
                    ls.progress -= n * t
                    produce(def, ls, n, mult)
                }
            } else if (ls.running) {
                ls.progress += dt
                if (ls.progress >= t) {
                    produce(def, ls, 1, mult)
                    ls.progress = 0.0
                    ls.running = false
                }
            }
        }
    }

    private fun produce(def: LineDef, ls: LineState, cycles: Long, mult: Double) {
        val perCycle = Money.times(def.revenuePerCycle * ls.level, mult)
        ls.todayRevenue = Money.add(ls.todayRevenue, Money.times(perCycle, cycles.toDouble()))
        ls.todayCycles += cycles
    }

    private fun endOfDay() {
        val d = date()
        for (def in Lines.all) if (line(def.id).hasManager) line(def.id).managerDaysThisMonth++
        if (state.hasAssistant) state.assistantDaysThisMonth++

        postDailyOperations()
        processFlows()
        processObligations()
        processDocuments()

        state.salesLast30[Math.floorMod(state.day, 30L).toInt()] = state.todaySales
        state.todaySales = 0
        state.creditDaySum += creditDrawn()

        if (d.isMonthEnd) {
            postDepreciation()
            postPayroll()
            postCreditInterest()
        }
        if (d.isQuarterEnd) {
            postVatSettlement()
            createWithholdingsObligation()
        }
        repayCredit()
        if (d.month == 6 && d.day == 30 && state.pendingDistribution != null) {
            event(
                L("Vence el plazo de la junta general: el beneficio pasa íntegro a reservas.", "The AGM deadline has passed: the whole profit goes to reserves."),
                EventKind.WARNING,
            )
            decideDistribution(0.0)
        }
        if (d.isYearEnd) {
            closeYear()
            pruneHistory()
        }
        state.day++
    }

    /** Asientos resumen del día por línea (art. 28.2 del Código de Comercio). */
    private fun postDailyOperations() {
        for (def in Lines.all) {
            val ls = line(def.id)
            val base = ls.todayRevenue
            if (base <= 0) continue
            val vat = Money.pct(base, def.salesVat)
            val total = base + vat
            val creditPart = Money.times(total, def.creditShare)
            val cashPart = total - creditPart
            post(
                L("Ventas del día · ${def.name.es}", "Daily sales · ${def.name.en}"),
                EntryBuilder().debit("572", cashPart).debit("430", creditPart).credit(def.salesAccount, base).credit("477", vat),
            )
            if (creditPart > 0) state.flows.add(PendingFlow(state.day + def.collectionDays, "430", creditPart))

            val pBase = Money.times(base, def.costRatio)
            val pVat = Money.pct(pBase, def.purchaseVat)
            val pTotal = pBase + pVat
            if (pBase > 0) {
                val concept = L("Compras del día · ${def.name.es}", "Daily purchases · ${def.name.en}")
                if (def.supplierDays == 0) {
                    ensureCash(pTotal)
                    post(concept, EntryBuilder().debit(def.purchaseAccount, pBase).debit("472", pVat).credit("572", pTotal))
                } else {
                    post(concept, EntryBuilder().debit(def.purchaseAccount, pBase).debit("472", pVat).credit("400", pTotal))
                    state.flows.add(PendingFlow(state.day + def.supplierDays, "400", pTotal))
                }
            }
            state.todaySales += base
            state.lifetimeSales = Money.add(state.lifetimeSales, base)
            ls.lifetimeRevenue = Money.add(ls.lifetimeRevenue, base)
            ls.todayRevenue = 0
            ls.todayCycles = 0
        }
    }

    private fun processFlows() {
        var collect = 0L
        var pay = 0L
        val it = state.flows.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (f.dueDay > state.day) continue
            if (f.account == "430") collect += f.amount else pay += f.amount
            it.remove()
        }
        if (collect > 0) {
            post(L("Cobros de clientes del día", "Customer collections"), EntryBuilder().debit("572", collect).credit("430", collect))
        }
        if (pay > 0) {
            ensureCash(pay)
            post(L("Pagos a proveedores del día", "Supplier payments"), EntryBuilder().debit("400", pay).credit("572", pay))
        }
    }

    private fun processObligations() {
        for (o in state.obligations.filter { !it.isPaid && it.dueDay <= state.day }) {
            when (o.kind) {
                ObligationKind.SOCIAL_SECURITY, ObligationKind.DIVIDEND -> settle(o, PaidBy.DIRECT_DEBIT)
                else -> when {
                    state.gestoriaActive -> settle(o, PaidBy.GESTORIA)
                    state.day - o.dueDay >= LATE_AUTO_DAYS -> settle(o, PaidBy.DIRECT_DEBIT)
                }
            }
        }
    }

    private fun settle(o: Obligation, by: PaidBy) {
        val daysLate = state.day - o.dueDay
        val applies = by != PaidBy.GESTORIA && o.kind != ObligationKind.SOCIAL_SECURITY && o.kind != ObligationKind.DIVIDEND
        val surcharge = if (applies) Money.pct(o.amount, Surcharge.extemporaneousBp(daysLate)) else 0L

        if (o.kind == ObligationKind.DIVIDEND) {
            val ret = Money.pct(o.amount, Withholding.DIVIDENDS)
            val net = o.amount - ret
            ensureCash(net)
            post(
                L("Pago de dividendos (retención del 19%)", "Dividend payment (19% withholding)"),
                EntryBuilder().debit("526", o.amount).credit("4751", ret).credit("572", net),
            )
            state.wh123 += ret
            state.ownerWealth = Money.add(state.ownerWealth, net)
            event(L("Has cobrado ${fmt(net, Lang.ES)} netos de dividendos.", "You received ${fmt(net, Lang.EN)} in net dividends."), EventKind.GOOD)
        } else {
            ensureCash(o.amount + surcharge)
            post(
                L("Pago · ${o.label.es}", "Payment · ${o.label.en}"),
                EntryBuilder().debit(o.account, o.amount).debit("678", surcharge).credit("572", o.amount + surcharge),
            )
            if (surcharge > 0) {
                state.nonDeductible += surcharge
                event(
                    L("Pago fuera de plazo de ${o.label.es}: recargo de ${fmt(surcharge, Lang.ES)} (art. 27 LGT).", "Late payment of ${o.label.en}: surcharge of ${fmt(surcharge, Lang.EN)} (art. 27 LGT)."),
                    EventKind.WARNING,
                )
            }
        }
        if (by == PaidBy.GESTORIA) {
            o.fee = chargeGestoria(GESTORIA_FILING_FEE, o.label)
            event(
                L("La gestoría ha presentado ${o.label.es} el último día (comisión de 45 € + IVA).", "Your accountants filed ${o.label.en} on the last day (fee: €45 + VAT)."),
                EventKind.TAX,
            )
        }
        o.paidDay = state.day
        o.paidBy = by
        o.surcharge = surcharge
    }

    private fun chargeGestoria(fee: Long, reason: L): Long {
        val vat = Money.pct(fee, Vat.GENERAL)
        val total = fee + vat
        post(
            L("Factura de la gestoría · ${reason.es}", "Accountancy firm invoice · ${reason.en}"),
            EntryBuilder().debit("623", fee).debit("472", vat).credit("410", total),
        )
        ensureCash(total)
        post(L("Pago a la gestoría", "Payment to the accountancy firm"), EntryBuilder().debit("410", total).credit("572", total))
        return total
    }

    private fun processDocuments() {
        if (state.hasAssistant) {
            for (doc in state.documents.toList()) resolveDocument(doc, byGestoria = false)
        }
        for (doc in state.documents.filter { state.day - it.arrivalDay >= DOC_AUTO_DAYS }) {
            resolveDocument(doc, byGestoria = true)
        }
        if (date().day == 1) addDocument(Documents.rent)
        if (state.day >= state.nextDocDay) {
            addDocument(pickRandomTemplate())
            state.nextDocDay = state.day + DOC_INTERVAL_DAYS
        }
        while (state.documents.size > MAX_DOCS) resolveDocument(state.documents.first(), byGestoria = true)
    }

    private fun addDocument(t: DocTemplate) {
        val sales30 = state.salesLast30.fold(0L) { acc, v -> Money.add(acc, v) }
        val variation = 0.8 + nextDouble() * 0.4
        val scaled = Money.times(sales30, t.weight * 0.005 * variation)
        val minimum = Money.times(t.minAmount, variation)
        val base = max(minimum, scaled)
        val options = shuffled(listOf(t.correctAccount) + t.distractors)
        state.documents.add(PendingDocument(state.nextDocId++, t.id, state.day, base, options))
    }

    private fun pickRandomTemplate(): DocTemplate {
        val candidates = Documents.random.filter { it.frequency > 0 && it.id != state.lastDocTemplate }
        val totalWeight = candidates.sumOf { it.frequency }
        var r = nextInt(totalWeight)
        for (c in candidates) {
            if (r < c.frequency) {
                state.lastDocTemplate = c.id
                return c
            }
            r -= c.frequency
        }
        return candidates.last()
    }

    /** Contabiliza un documento siempre en la cuenta correcta, con su IVA, retención y pago. */
    private fun resolveDocument(doc: PendingDocument, byGestoria: Boolean) {
        val t = doc.template
        val vat = doc.vat
        val wh = doc.withholding
        val total = doc.total
        val concept = L("${t.title.es} · ${t.issuer.es}", "${t.title.en} · ${t.issuer.en}")
        if (t.directDebit) {
            ensureCash(total)
            post(concept, EntryBuilder().debit(t.correctAccount, doc.base).debit("472", vat).credit("4751", wh).credit("572", total))
        } else {
            post(concept, EntryBuilder().debit(t.correctAccount, doc.base).debit("472", vat).credit("4751", wh).credit("410", total))
            ensureCash(total)
            post(L("Pago · ${t.issuer.es}", "Payment · ${t.issuer.en}"), EntryBuilder().debit("410", total).credit("572", total))
        }
        when (t.withholdingModel) {
            WithholdingModel.M111 -> state.wh111 += wh
            WithholdingModel.M115 -> state.wh115 += wh
            WithholdingModel.M123 -> state.wh123 += wh
            null -> {}
        }
        state.documents.remove(doc)
        if (byGestoria) chargeGestoria(GESTORIA_DOC_FEE, t.title)
    }

    /** Amortización lineal mensual de cada línea como grupo homogéneo de bienes. */
    private fun postDepreciation() {
        val b = EntryBuilder()
        var total = 0L
        for (def in Lines.all) {
            val ls = line(def.id)
            val remaining = ls.grossCost - ls.accumulatedDepreciation
            if (remaining <= 0) continue
            val monthly = max(1L, ls.grossCost / (def.usefulLifeYears * 12L))
            val amount = min(monthly, remaining)
            b.credit(def.depreciationAccount, amount)
            ls.accumulatedDepreciation += amount
            total += amount
        }
        if (total <= 0) return
        b.debit("681", total)
        post(L("Amortización del inmovilizado · ${Fmt.monthYear(date(), Lang.ES)}", "Depreciation · ${Fmt.monthYear(date(), Lang.EN)}"), b)
    }

    private fun postPayroll() {
        val d = date()
        val daysInMonth = d.lengthOfMonth
        var gross = 0L
        for (def in Lines.all) {
            val ls = line(def.id)
            if (ls.managerDaysThisMonth > 0) gross += def.managerSalary * min(ls.managerDaysThisMonth, daysInMonth) / daysInMonth
            ls.managerDaysThisMonth = 0
        }
        if (state.assistantDaysThisMonth > 0) gross += ASSISTANT_SALARY * min(state.assistantDaysThisMonth, daysInMonth) / daysInMonth
        state.assistantDaysThisMonth = 0
        if (gross <= 0) return

        val ssEmployer = Money.pct(gross, SocialSecurity.EMPLOYER)
        val ssEmployee = Money.pct(gross, SocialSecurity.EMPLOYEE)
        val irpf = Money.pct(gross, Withholding.PAYROLL_AVERAGE)
        val net = gross - ssEmployee - irpf
        val month = L(Fmt.monthYear(d, Lang.ES), Fmt.monthYear(d, Lang.EN))
        post(
            L("Nóminas de ${month.es}", "Payroll for ${month.en}"),
            EntryBuilder().debit("640", gross).debit("642", ssEmployer)
                .credit("476", ssEmployer + ssEmployee).credit("4751", irpf).credit("465", net),
        )
        ensureCash(net)
        post(L("Pago de las nóminas de ${month.es}", "Payroll payment for ${month.en}"), EntryBuilder().debit("465", net).credit("572", net))
        state.wh111 += irpf
        addObligation(
            ObligationKind.SOCIAL_SECURITY,
            L("Seguros sociales de ${month.es}", "Social Security for ${month.en}"),
            "476", ssEmployer + ssEmployee, d.endOfNextMonth().epochDay,
        )
    }

    private fun postCreditInterest() {
        val daySum = state.creditDaySum
        state.creditDaySum = 0
        val interest = Money.pct(daySum, CREDIT_RATE_BP) / 365
        if (interest <= 0) return
        ensureCash(interest)
        post(L("Intereses de la póliza de crédito (7% anual)", "Credit line interest (7% per year)"), EntryBuilder().debit("662", interest).credit("572", interest))
    }

    /** Liquidación trimestral del IVA (modelo 303), con compensación de cuotas de periodos anteriores. */
    private fun postVatSettlement() {
        val d = date()
        val output = ledger.natural("477")
        val input = ledger.natural("472")
        val previousCredit = max(0L, ledger.natural("4700"))
        if (output == 0L && input == 0L) return
        val b = EntryBuilder().debit("477", output).credit("472", input)
        val diff = output - input
        var payable = 0L
        if (diff >= 0) {
            val used = min(previousCredit, diff)
            payable = diff - used
            b.credit("4700", used).credit("4750", payable)
        } else {
            b.debit("4700", -diff)
        }
        val period = "${d.quarter}T ${d.year}"
        val periodEn = "Q${d.quarter} ${d.year}"
        post(L("Liquidación del IVA del $period (modelo 303)", "VAT return for $periodEn (form 303)"), b)
        if (payable > 0) {
            addObligation(ObligationKind.VAT, L("Modelo 303 · IVA del $period", "Form 303 · VAT $periodEn"), "4750", payable, d.quarterlyFilingDeadline().epochDay)
            event(L("IVA del $period: ${fmt(payable, Lang.ES)} a ingresar.", "VAT for $periodEn: ${fmt(payable, Lang.EN)} payable."), EventKind.TAX)
        } else {
            val credit = ledger.natural("4700")
            event(L("IVA del $period: sale a compensar (${fmt(credit, Lang.ES)} pendientes).", "VAT for $periodEn: a credit of ${fmt(credit, Lang.EN)} is carried forward."), EventKind.TAX)
        }
    }

    private fun createWithholdingsObligation() {
        val total = state.wh111 + state.wh115 + state.wh123
        if (total <= 0) return
        val d = date()
        val models = buildList {
            if (state.wh111 > 0) add("111")
            if (state.wh115 > 0) add("115")
            if (state.wh123 > 0) add("123")
        }
        val modelsEs = joinEs(models)
        val modelsEn = joinEn(models)
        val label = if (models.size == 1)
            L("Modelo $modelsEs · retenciones del ${d.quarter}T ${d.year}", "Form $modelsEn · withholdings Q${d.quarter} ${d.year}")
        else
            L("Modelos $modelsEs · retenciones del ${d.quarter}T ${d.year}", "Forms $modelsEn · withholdings Q${d.quarter} ${d.year}")
        addObligation(ObligationKind.WITHHOLDINGS, label, "4751", total, d.quarterlyFilingDeadline().epochDay)
        state.wh111 = 0
        state.wh115 = 0
        state.wh123 = 0
    }

    /**
     * Cierre del ejercicio: Impuesto sobre Sociedades, regularización, asiento de cierre
     * y asiento de apertura del ejercicio siguiente.
     */
    private fun closeYear() {
        val d = date()
        val year = d.year
        val before = Reports.pyg(ledger)
        val incn = before.revenue
        // Para el tamaño se usa la cifra de negocios del ejercicio anterior; en el primero, la del propio ejercicio.
        val sizeIncn = if (year == state.startYear) incn else state.prevYearIncn
        val newEntityEligible = state.firstPositiveBaseYear < 0 || year <= state.firstPositiveBaseYear + 1
        val (tax, remainingLosses) = CorporateTax.compute(
            before.resultBeforeTax, state.nonDeductible, state.pendingLosses, sizeIncn, year, newEntityEligible,
        )
        if (tax.taxableBase > 0 && state.firstPositiveBaseYear < 0) state.firstPositiveBaseYear = year
        state.pendingLosses = remainingLosses
        if (tax.quota > 0) {
            post(L("Impuesto sobre Sociedades del ejercicio $year", "Corporate income tax for $year"), EntryBuilder().debit("6300", tax.quota).credit("4752", tax.quota))
            addObligation(
                ObligationKind.CORPORATE_TAX,
                L("Modelo 200 · Impuesto sobre Sociedades $year", "Form 200 · Corporate tax $year"),
                "4752", tax.quota, GameDate.of(year + 1, 7, 25).epochDay,
            )
        }

        val pyg = Reports.pyg(ledger)
        val bs = Reports.balanceSheet(ledger)
        state.lastYear = YearSummary(year, pyg.revenue, pyg.netResult, -pyg.incomeTax, bs.totalAssets, bs.equity, tax.regime, tax.taxableBase, tax.lossesOffset)

        // Regularización: los grupos 6 y 7 se saldan contra la 129.
        val reg = EntryBuilder()
        for (code in ledger.accountsWithMovement()) {
            if (!Pgc.get(code).isPyg) continue
            val bal = ledger.balance(code)
            if (bal > 0) reg.credit(code, bal) else if (bal < 0) reg.debit(code, -bal)
        }
        val net = pyg.netResult
        if (net > 0) reg.credit("129", net) else if (net < 0) reg.debit("129", -net)
        if (!reg.isEmpty) post(L("Regularización del ejercicio $year", "Closing of income and expense accounts $year"), reg)

        // Cierre y apertura.
        val closing = EntryBuilder()
        val opening = EntryBuilder()
        for (code in ledger.accountsWithMovement()) {
            val bal = ledger.balance(code)
            if (bal > 0) {
                closing.credit(code, bal)
                opening.debit(code, bal)
            } else if (bal < 0) {
                closing.debit(code, -bal)
                opening.credit(code, -bal)
            }
        }
        if (!closing.isEmpty) post(L("Asiento de cierre del ejercicio $year", "Closing entry $year"), closing)

        state.prevYearIncn = incn
        state.nonDeductible = 0
        ledger.startNewYear(year + 1)
        val jan1 = d.plusDays(1)
        if (!opening.isEmpty) post(L("Asiento de apertura del ejercicio ${year + 1}", "Opening entry ${year + 1}"), opening, jan1)

        if (net > 0) {
            state.pendingDistribution = PendingDistribution(year, net)
        } else if (net < 0) {
            post(
                L("Aplicación del resultado de $year: pérdidas a resultados negativos", "Appropriation of $year result: losses carried forward"),
                EntryBuilder().debit("121", -net).credit("129", -net), jan1,
            )
        }
        event(
            L(
                "Cierre del ejercicio $year: ${if (net >= 0) "beneficio" else "pérdida"} de ${fmt(kotlin.math.abs(net), Lang.ES)}" +
                    (if (tax.quota > 0) ", Impuesto sobre Sociedades de ${fmt(tax.quota, Lang.ES)} (${tax.regime.title.es})." else "."),
                "Year-end close $year: ${if (net >= 0) "profit" else "loss"} of ${fmt(kotlin.math.abs(net), Lang.EN)}" +
                    (if (tax.quota > 0) ", corporate tax of ${fmt(tax.quota, Lang.EN)} (${tax.regime.title.en})." else "."),
            ),
            EventKind.TAX,
        )
    }

    private fun repayCredit() {
        val drawn = creditDrawn()
        val available = cash()
        if (drawn <= 0 || available <= 0) return
        val amount = min(drawn, available)
        post(L("Devolución de la póliza de crédito", "Credit line repayment"), EntryBuilder().debit("5201", amount).credit("572", amount))
    }

    /** Si el banco no llega para un pago obligatorio, se dispone de la póliza de crédito. */
    private fun ensureCash(amount: Long) {
        val available = cash()
        if (available >= amount) return
        val draw = amount - available
        post(L("Disposición de la póliza de crédito", "Credit line drawdown"), EntryBuilder().debit("572", draw).credit("5201", draw))
    }

    private fun addObligation(kind: ObligationKind, label: L, account: String, amount: Long, dueDay: Long) {
        state.obligations.add(Obligation(state.nextObligationId++, kind, label, account, amount, state.day, dueDay))
    }

    private fun pruneHistory() {
        state.obligations.removeAll { it.isPaid && state.day - (it.paidDay ?: state.day) > 400 }
    }

    private fun post(concept: L, b: EntryBuilder, date: GameDate = date()) {
        if (b.isEmpty) return
        ledger.post(date, concept, b)
    }

    private fun event(text: L, kind: EventKind) {
        state.events.addLast(GameEvent(++state.eventSeq, state.day, text, kind))
        while (state.events.size > MAX_EVENTS) state.events.removeFirst()
    }

    private fun fmt(cents: Long, lang: Lang) = Fmt.money(cents, lang)

    private fun joinEs(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
    }

    private fun joinEn(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    // ───────────────────────────── Azar determinista ─────────────────────────────
    // xorshift64: el estado se guarda en la partida, así que el azar es reproducible y testeable.

    private fun nextLong(): Long {
        var x = state.rngState
        if (x == 0L) x = 88172645463325252L
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state.rngState = x
        return x
    }

    private fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    private fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

    private fun <T> shuffled(items: List<T>): List<T> {
        val list = items.toMutableList()
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }
}
