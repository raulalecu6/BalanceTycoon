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
import kotlin.math.pow

/** Resultado de responder una consulta de un cliente. */
data class CaseResult(
    val correct: Boolean,
    val template: CaseTemplate,
    val fee: Long,
    val reputationChange: Long,
    val streak: Int,
)

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
    val reputation: Long,
    val casesSolved: Int,
    val events: List<GameEvent>,
)

enum class PayResult { OK, NOT_FOUND, ALREADY_PAID }

/**
 * Motor del juego: una gestoría que crece de un bajo comercial en Tomelloso a una torre en Madrid.
 * Hace avanzar el tiempo, factura los honorarios y genera todos los asientos.
 * No conoce Android: la app solo llama a [advance] y a las acciones del jugador.
 */
class GameEngine(val state: GameState) {
    private val ledger get() = state.ledger

    companion object {
        /** Un día contable dura 30 segundos reales: un mes, 15 minutos; un ejercicio, unas 3 horas. */
        const val DAY_SECONDS = 30.0
        const val OFFLINE_CAP_SECONDS = 8 * 3600.0
        const val CASE_INTERVAL_DAYS = 2L
        const val CASE_AUTO_DAYS = 10L
        const val MAX_CASES = 6
        const val STREAK_STEP = 0.02
        const val STREAK_CAP = 10
        const val BOOST_SECONDS = 2 * 3600.0
        const val CREDIT_RATE_BP = 700
        const val DIVIDEND_PAYMENT_DAYS = 30L
        const val MAX_EVENTS = 80
        /** Segundos de facturación que vale, como referencia, una consulta bien resuelta. */
        const val CASE_FEE_SECONDS = 90.0
        const val REP_WRONG = 3L
        const val REP_AUTO = 1L
        const val REP_MILESTONE = 10L
        val INITIAL_CAPITAL: Long = Money.euros(3_000L)

        /** Crea una partida nueva: constitución de la sociedad, fianza del local y apertura del departamento fiscal. */
        fun newGame(startYear: Int, seed: Long = 88172645463325252L, companyName: String = "Mi Gestoría, S.L."): GameEngine {
            val s = GameState(startYear)
            s.companyName = companyName
            s.rngState = if (seed == 0L) 88172645463325252L else seed
            val engine = GameEngine(s)
            engine.setupNewCompany()
            return engine
        }
    }

    private fun setupNewCompany() {
        for (def in Departments.all) state.depts[def.id] = DeptState(def.id)
        post(
            L("Constitución de la sociedad: aportación dineraria de los socios", "Incorporation: cash contribution from the partners"),
            EntryBuilder().debit("572", INITIAL_CAPITAL).credit("100", INITIAL_CAPITAL),
        )
        val office = office()
        post(
            L("Fianza del local · ${office.name.es} (${office.city})", "Rent deposit · ${office.name.en} (${office.city})"),
            EntryBuilder().debit("260", office.deposit).credit("572", office.deposit),
        )
        buyLevels("fiscal", 1)
        state.nextCaseDay = state.day + 1
        event(
            L("Se ha constituido ${state.companyName}. Abres la gestoría en un bajo de Tomelloso.", "${state.companyName} has been incorporated. You open the practice in a small office in Tomelloso."),
            EventKind.GOOD,
        )
    }

    // ───────────────────────────── Consultas ─────────────────────────────

    fun date(): GameDate = GameDate(state.day)
    fun cash(): Long = ledger.natural("572")
    fun creditDrawn(): Long = ledger.natural("5201")
    fun dept(id: String): DeptState = state.depts.getValue(id)
    fun dayProgress(): Double = state.secondsIntoDay / DAY_SECONDS
    fun office(): OfficeDef = Offices.get(state.officeIndex)
    fun tier(): ClientTier = ClientTiers.forReputation(state.reputation)
    fun nextTier(): ClientTier? = ClientTiers.all.getOrNull(tier().index + 1)

    fun boostActive(): Boolean = state.boostUntil > state.playSeconds
    fun boostRemainingSeconds(): Double = max(0.0, state.boostUntil - state.playSeconds)

    /** El local tiene planta para este departamento. */
    fun deptAvailable(id: String): Boolean = Departments.indexOf(id) < office().floors
    fun deptOpen(id: String): Boolean = dept(id).level > 0

    /** Racha de consultas bien resueltas (+2 % por acierto, máx. +20 %) y turbo ×2. */
    fun globalMultiplier(): Double {
        val streakBonus = 1.0 + min(state.streak, STREAK_CAP) * STREAK_STEP
        val boost = if (boostActive()) 2.0 else 1.0
        return streakBonus * boost * SoftwareTiers.get(state.softwareTier).multiplier * tier().feeMultiplier
    }

    /** Multiplicador propio del departamento: trabajadores, equipos y formación. */
    fun deptMultiplier(id: String): Double {
        val d = dept(id)
        return max(1, d.workers) * EquipmentTiers.get(d.equipmentTier).multiplier * Departments.trainingMultiplier(d.training)
    }

    fun cycleSeconds(id: String): Double {
        val def = Departments.get(id)
        val ms = Departments.milestonesReached(dept(id).level)
        return def.cycleSeconds / (1 shl ms)
    }

    /** Honorarios netos por expediente con los multiplicadores actuales. */
    fun revenuePerCycle(id: String): Long {
        val def = Departments.get(id)
        val d = dept(id)
        return Money.times(def.feePerCycle * d.level, deptMultiplier(id) * globalMultiplier())
    }

    /** Facturación por segundo del departamento si estuviera trabajando sin parar (céntimos). */
    fun revenuePerSecond(id: String): Double {
        if (dept(id).level == 0) return 0.0
        return revenuePerCycle(id) / cycleSeconds(id)
    }

    /** Facturación automática por segundo: solo los departamentos con trabajadores. */
    fun incomePerSecond(): Double = Departments.all.filter { dept(it.id).workers > 0 }.sumOf { revenuePerSecond(it.id) }

    /** Coste sin IVA de comprar [count] niveles de cartera. */
    fun upgradeCost(id: String, count: Int): Long = Departments.costOf(Departments.get(id), dept(id).level, count)

    /** Dinero necesario en el banco (coste más IVA soportado del 21 %, y el primer ordenador si se abre). */
    fun upgradeCashNeeded(id: String, count: Int): Long {
        val cost = upgradeCost(id, count)
        val pc = if (dept(id).level == 0) EquipmentTiers.get(0).price else 0L
        return withVat(cost + pc)
    }

    /** Número máximo de niveles que se pueden pagar ahora con el saldo del banco (tope 1.000). */
    fun maxAffordable(id: String): Int {
        val def = Departments.get(id)
        var level = dept(id).level
        var spent = if (level == 0) withVat(EquipmentTiers.get(0).price) else 0L
        var count = 0
        val available = cash()
        while (count < 1000) {
            val withVat = withVat(Departments.nextLevelCost(def, level))
            if (spent + withVat > available) break
            spent += withVat
            level++
            count++
        }
        return count
    }

    fun maxWorkers(): Int = office().desksPerFloor

    /** Dinero necesario para contratar: el ordenador del nuevo puesto (el primero aprovecha el que ya hay). */
    fun hireCashNeeded(id: String): Long {
        val d = dept(id)
        return if (d.workers == 0) 0L else withVat(EquipmentTiers.get(d.equipmentTier).price)
    }

    fun canHire(id: String): Boolean {
        val d = dept(id)
        return d.level > 0 && d.workers < maxWorkers() && cash() >= hireCashNeeded(id)
    }

    /** Coste mensual de un trabajador más (salario bruto más cuota patronal). */
    fun workerMonthlyCost(id: String): Long {
        val salary = Departments.get(id).salary
        return salary + Money.pct(salary, SocialSecurity.EMPLOYER)
    }

    fun maxTraining(): Int = min(Departments.MAX_TRAINING, tier().index + 1)

    /** Coste del siguiente nivel de formación para todo el departamento (exento de IVA). */
    fun trainingCost(id: String): Long {
        val d = dept(id)
        val def = Departments.get(id)
        return Money.times(def.trainingBase * d.desks, 3.0.pow(d.training))
    }

    fun canTrain(id: String): Boolean {
        val d = dept(id)
        return d.workers > 0 && d.training < maxTraining() && cash() >= trainingCost(id)
    }

    fun maxEquipmentTier(): Int = min(EquipmentTiers.all.size - 1, state.officeIndex + 1)

    /** Coste sin IVA de renovar todos los ordenadores del departamento al nivel siguiente. */
    fun equipmentUpgradeCost(id: String): Long {
        val d = dept(id)
        val next = EquipmentTiers.all.getOrNull(d.equipmentTier + 1) ?: return 0L
        return next.price * d.desks
    }

    fun canUpgradeEquipment(id: String): Boolean {
        val d = dept(id)
        return d.level > 0 && d.equipmentTier < maxEquipmentTier() && cash() >= withVat(equipmentUpgradeCost(id))
    }

    /** Valor neto contable de los ordenadores que se darían de baja al renovarlos. */
    fun equipmentBookValue(id: String): Long {
        val d = dept(id)
        return max(0L, d.equipmentGross - d.equipmentDepreciation)
    }

    fun nextSoftware(): SoftwareTier? = SoftwareTiers.all.getOrNull(state.softwareTier + 1)

    fun canBuySoftware(): Boolean {
        val next = nextSoftware() ?: return false
        return tier().index >= next.minTier && cash() >= withVat(next.price)
    }

    fun nextOffice(): OfficeDef? = Offices.all.getOrNull(state.officeIndex + 1)

    /** Dinero necesario para mudarse: obras con IVA y la nueva fianza (la anterior se recupera). */
    fun moveCashNeeded(): Long {
        val next = nextOffice() ?: return 0L
        return withVat(next.fitOut) + max(0L, next.deposit - office().deposit)
    }

    fun canMove(): Boolean {
        val next = nextOffice() ?: return false
        return tier().index >= next.minTier && cash() >= moveCashNeeded()
    }

    fun workersTotal(): Int = state.depts.values.sumOf { it.workers }

    /** Coste mensual estimado de la plantilla (salario bruto más cuota patronal). */
    fun monthlyPayrollCost(): Long {
        var gross = 0L
        for (def in Departments.all) gross += def.salary * dept(def.id).workers
        return gross + Money.pct(gross, SocialSecurity.EMPLOYER)
    }

    /** Gastos fijos del mes: plantilla, alquiler y suministros (con IVA). */
    fun monthlyFixedCosts(): Long = monthlyPayrollCost() + withVat(office().rent) + withVat(monthlyUtilities())

    fun monthlyUtilities(): Long = office().utilities + Offices.UTILITIES_PER_WORKER * workersTotal()

    /** Patrimonio de los clientes que lleva la gestoría: cada nivel de cartera es un cliente. */
    fun managedWealth(): Long {
        val clients = state.depts.values.sumOf { it.level.toLong() }
        return Money.times(tier().averageWealth, clients.toDouble())
    }

    fun clientCount(): Long = state.depts.values.sumOf { it.level.toLong() }

    /** IVA que saldría a ingresar si el trimestre se cerrara hoy. */
    fun vatEstimate(): Long = ledger.natural("477") - ledger.natural("472") - ledger.natural("4700")

    /** Todo lo que la empresa debe a Hacienda y a la Seguridad Social, incluido el IVA estimado. */
    fun publicDebtEstimate(): Long =
        max(0L, vatEstimate()) + ledger.natural("4750") + ledger.natural("4751") + ledger.natural("4752") + ledger.natural("476")

    fun pendingObligations(): List<Obligation> = state.obligations.filter { !it.isPaid }.sortedBy { it.dueDay }

    fun receivables(): Long = ledger.natural("430")

    // ───────────────────────────── Acciones del jugador ─────────────────────────────

    /** El jefe tramita un expediente a mano. Solo si el departamento no tiene trabajadores. */
    fun tapDept(id: String): Boolean {
        val d = dept(id)
        if (d.level == 0 || d.workers > 0 || d.running) return false
        d.running = true
        d.progress = 0.0
        return true
    }

    /** Amplía la cartera del departamento: campaña de captación de clientes (627) con IVA soportado. */
    fun buyLevels(id: String, count: Int): Boolean {
        if (count <= 0 || !deptAvailable(id)) return false
        val def = Departments.get(id)
        val d = dept(id)
        val cost = Departments.costOf(def, d.level, count)
        if (cash() < upgradeCashNeeded(id, count)) return false
        val opening = d.level == 0
        post(
            if (opening) L("Apertura del departamento ${def.name.es}: campaña de captación de clientes", "Opening of the ${def.name.en} department: client acquisition campaign")
            else L("Campaña de captación de clientes · ${def.name.es} (+$count ${if (count == 1) "cliente" else "clientes"})", "Client acquisition campaign · ${def.name.en} (+$count ${if (count == 1) "client" else "clients"})"),
            EntryBuilder().debit("627", cost).debit("472", Money.pct(cost, Vat.GENERAL)).credit("572", withVat(cost)),
        )
        if (opening) {
            buyComputers(d, def, 1, EquipmentTiers.get(0))
            if (id != "fiscal") event(L("Has abierto el departamento ${def.name.es}.", "You opened the ${def.name.en} department."), EventKind.GOOD)
        }
        val before = d.level
        d.level += count
        val ms = MILESTONES.filter { it in (before + 1)..d.level }
        if (ms.isNotEmpty()) {
            gainReputation(REP_MILESTONE * ms.size)
            event(
                L("${def.name.es} llega a ${ms.last()} clientes: los expedientes van el doble de rápido (+${REP_MILESTONE * ms.size} de reputación).", "${def.name.en} reached ${ms.last()} clients: files are processed twice as fast (+${REP_MILESTONE * ms.size} reputation)."),
                EventKind.GOOD,
            )
        }
        return true
    }

    /** Contrata a un trabajador. Si ya hay alguien, compra el ordenador del nuevo puesto. */
    fun hire(id: String): Boolean {
        if (!canHire(id)) return false
        val def = Departments.get(id)
        val d = dept(id)
        if (d.workers > 0) buyComputers(d, def, 1, EquipmentTiers.get(d.equipmentTier))
        d.workers++
        event(
            L(
                "Contratado: ${def.workerTitle.es} en ${def.name.es} (${fmt(def.salary, Lang.ES)} brutos al mes).",
                "Hired: ${def.workerTitle.en} in ${def.name.en} (${fmt(def.salary, Lang.EN)} gross per month).",
            ),
            EventKind.INFO,
        )
        return true
    }

    /** Curso de formación para todo el departamento: otros gastos sociales (649), exento de IVA. */
    fun train(id: String): Boolean {
        if (!canTrain(id)) return false
        val def = Departments.get(id)
        val d = dept(id)
        val cost = trainingCost(id)
        post(
            L("Curso de formación · ${def.name.es} (nivel ${d.training + 1}, centro autorizado exento de IVA)", "Training course · ${def.name.en} (level ${d.training + 1}, VAT-exempt approved centre)"),
            EntryBuilder().debit("649", cost).credit("572", cost),
        )
        d.training++
        event(L("La plantilla de ${def.name.es} ha completado la formación de nivel ${d.training}.", "${def.name.en} staff completed level ${d.training} training."), EventKind.GOOD)
        return true
    }

    /**
     * Renueva los ordenadores del departamento. Los antiguos se dan de baja: se cancela su
     * amortización acumulada y el valor pendiente es una pérdida (671).
     */
    fun upgradeEquipment(id: String): Boolean {
        if (!canUpgradeEquipment(id)) return false
        val def = Departments.get(id)
        val d = dept(id)
        if (d.equipmentGross > 0) {
            val loss = equipmentBookValue(id)
            post(
                L("Baja de los ordenadores sustituidos · ${def.name.es}", "Derecognition of replaced computers · ${def.name.en}"),
                EntryBuilder().debit("2817", d.equipmentDepreciation).debit("671", loss).credit("217", d.equipmentGross),
            )
            d.equipmentGross = 0
            d.equipmentDepreciation = 0
        }
        val next = EquipmentTiers.get(d.equipmentTier + 1)
        buyComputers(d, def, d.desks, next)
        d.equipmentTier = next.index
        event(L("${def.name.es} estrena equipos: ${next.name.es}.", "${def.name.en} gets new equipment: ${next.name.en}."), EventKind.GOOD)
        return true
    }

    /** Compra el siguiente módulo del programa de gestión (aplicaciones informáticas, 206). */
    fun buySoftware(): Boolean {
        if (!canBuySoftware()) return false
        val next = nextSoftware() ?: return false
        post(
            L("Licencia de software · ${next.name.es}", "Software licence · ${next.name.en}"),
            EntryBuilder().debit("206", next.price).debit("472", Money.pct(next.price, Vat.GENERAL)).credit("572", withVat(next.price)),
        )
        state.softwareGross += next.price
        state.softwareTier = next.index
        event(L("Nuevo programa de gestión: ${next.name.es}.", "New practice software: ${next.name.en}."), EventKind.GOOD)
        return true
    }

    /**
     * Traslado al local siguiente: se recupera la fianza del anterior, se dan de baja sus obras
     * (que se quedan en el local), se deposita la nueva fianza y se pagan las obras del nuevo.
     */
    fun moveOffice(): Boolean {
        if (!canMove()) return false
        val old = office()
        val next = nextOffice() ?: return false
        post(
            L("Devolución de la fianza · ${old.name.es} (${old.city})", "Deposit refund · ${old.name.en} (${old.city})"),
            EntryBuilder().debit("572", old.deposit).credit("260", old.deposit),
        )
        if (state.fitOutGross > 0) {
            val loss = max(0L, state.fitOutGross - state.fitOutDepreciation)
            post(
                L("Baja de las obras del local anterior (quedan en el inmueble)", "Derecognition of the previous office fit-out (left in the building)"),
                EntryBuilder().debit("2815", state.fitOutDepreciation).debit("671", loss).credit("215", state.fitOutGross),
            )
        }
        post(
            L("Fianza del local · ${next.name.es} (${next.city})", "Rent deposit · ${next.name.en} (${next.city})"),
            EntryBuilder().debit("260", next.deposit).credit("572", next.deposit),
        )
        post(
            L("Obras de acondicionamiento · ${next.name.es} (${next.city})", "Fit-out works · ${next.name.en} (${next.city})"),
            EntryBuilder().debit("215", next.fitOut).debit("472", Money.pct(next.fitOut, Vat.GENERAL)).credit("572", withVat(next.fitOut)),
        )
        state.fitOutGross = next.fitOut
        state.fitOutDepreciation = 0
        state.officeIndex = next.index
        event(
            L("¡Mudanza! La gestoría se traslada a: ${next.name.es} (${next.city}). Ahora tienes ${next.floors} plantas y ${next.desksPerFloor} puestos por planta.",
                "Moving day! The practice moves to: ${next.name.en} (${next.city}). You now have ${next.floors} floors and ${next.desksPerFloor} desks per floor."),
            EventKind.GOOD,
        )
        return true
    }

    /** Recompensa de un anuncio (o de la compra Premium): honorarios ×2 durante 2 horas reales. */
    fun activateBoost() {
        state.boostUntil = max(state.boostUntil, state.playSeconds) + BOOST_SECONDS
    }

    /**
     * El jugador responde una consulta. [option] es el índice de la opción en la plantilla
     * (0 = correcta). Si acierta, factura los honorarios y gana reputación; si falla, la
     * rectificación es gratuita y pierde reputación.
     */
    fun answerCase(caseId: Int, option: Int): CaseResult? {
        val pc = state.cases.firstOrNull { it.id == caseId } ?: return null
        val t = pc.template
        val correct = option == 0
        state.cases.remove(pc)
        val repChange: Long
        val fee: Long
        if (correct) {
            fee = pc.fee
            invoiceCase(t, fee)
            state.streak++
            state.bestStreak = max(state.bestStreak, state.streak)
            state.casesCorrect++
            repChange = 3L + pc.tier
            gainReputation(repChange)
        } else {
            fee = 0
            state.streak = 0
            state.casesWrong++
            repChange = -min(REP_WRONG, state.reputation)
            state.reputation += repChange
        }
        return CaseResult(correct, t, fee, repChange, state.streak)
    }

    /** Pago anticipado por el jugador desde la pantalla de Hacienda. */
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
            advanceDepts(step)
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
    class Snapshot internal constructor(
        val cash: Long, val sales: Long, val day: Long, val entries: Long, val eventSeq: Long, val reputation: Long, val autoCases: Int,
    )

    fun snapshot(): Snapshot = Snapshot(cash(), state.lifetimeSales, state.day, ledger.totalPosted, state.eventSeq, state.reputation, state.casesAuto)

    fun reportSince(snap: Snapshot, elapsedSeconds: Double): OfflineReport = OfflineReport(
        seconds = elapsedSeconds.coerceIn(0.0, OFFLINE_CAP_SECONDS),
        capped = elapsedSeconds > OFFLINE_CAP_SECONDS,
        days = state.day - snap.day,
        sales = state.lifetimeSales - snap.sales,
        cashChange = cash() - snap.cash,
        entries = ledger.totalPosted - snap.entries,
        reputation = state.reputation - snap.reputation,
        casesSolved = state.casesAuto - snap.autoCases,
        events = state.events.filter { it.seq > snap.eventSeq },
    )

    private fun advanceDepts(dt: Double) {
        for (def in Departments.all) {
            val d = dept(def.id)
            if (d.level <= 0) continue
            val t = cycleSeconds(def.id)
            if (d.workers > 0) {
                d.progress += dt
                if (d.progress >= t) {
                    val n = floor(d.progress / t).toLong()
                    d.progress -= n * t
                    produce(def, d, n)
                }
            } else if (d.running) {
                d.progress += dt
                if (d.progress >= t) {
                    produce(def, d, 1)
                    d.progress = 0.0
                    d.running = false
                }
            }
        }
    }

    private fun produce(def: DeptDef, d: DeptState, cycles: Long) {
        val perCycle = revenuePerCycle(def.id)
        d.todayRevenue = Money.add(d.todayRevenue, Money.times(perCycle, cycles.toDouble()))
        d.todayCycles += cycles
        d.completedCycles += cycles
    }

    private fun endOfDay() {
        val d = date()
        for (ds in state.depts.values) ds.workerDaysThisMonth += ds.workers

        postDailyFees()
        processFlows()
        processObligations()
        processCases()

        state.salesLast30[Math.floorMod(state.day, 30L).toInt()] = state.todaySales
        state.todaySales = 0
        state.creditDaySum += creditDrawn()

        if (d.isMonthEnd) {
            postDepreciation()
            postPayroll()
            postRent()
            postUtilities()
            postCreditInterest()
            val active = state.depts.values.count { it.workers > 0 }.toLong()
            if (active > 0) gainReputation(active)
        }
        if (d.isQuarterEnd) {
            postInsurance()
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

    /** Asiento resumen diario de honorarios por departamento (art. 28.2 del Código de Comercio). */
    private fun postDailyFees() {
        val t = tier()
        for (def in Departments.all) {
            val ds = dept(def.id)
            val base = ds.todayRevenue
            if (base <= 0) continue
            val vat = Money.pct(base, Vat.GENERAL)
            val total = base + vat
            val creditPart = Money.times(total, t.creditShare)
            val cashPart = total - creditPart
            post(
                L("Honorarios del día · ${def.name.es}", "Daily fees · ${def.name.en}"),
                EntryBuilder().debit("572", cashPart).debit("430", creditPart).credit("705", base).credit("477", vat),
            )
            if (creditPart > 0) state.flows.add(PendingFlow(state.day + t.collectionDays, "430", creditPart))
            state.todaySales += base
            state.lifetimeSales = Money.add(state.lifetimeSales, base)
            ds.lifetimeRevenue = Money.add(ds.lifetimeRevenue, base)
            ds.todayRevenue = 0
            ds.todayCycles = 0
        }
    }

    private fun invoiceCase(t: CaseTemplate, fee: Long) {
        if (fee <= 0) return
        val vat = Money.pct(fee, Vat.GENERAL)
        post(
            L("Honorarios por consulta · ${t.client.es}", "Consultation fee · ${t.client.en}"),
            EntryBuilder().debit("572", fee + vat).credit("705", fee).credit("477", vat),
        )
        state.todaySales += fee
        state.lifetimeSales = Money.add(state.lifetimeSales, fee)
        dept(t.dept).lifetimeRevenue = Money.add(dept(t.dept).lifetimeRevenue, fee)
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
            post(L("Cobros de clientes del día", "Client collections"), EntryBuilder().debit("572", collect).credit("430", collect))
        }
        if (pay > 0) {
            ensureCash(pay)
            post(L("Pagos a proveedores del día", "Supplier payments"), EntryBuilder().debit("400", pay).credit("572", pay))
        }
    }

    /** Tu propio departamento fiscal presenta los modelos el último día; la SS y los dividendos se domicilian. */
    private fun processObligations() {
        for (o in state.obligations.filter { !it.isPaid && it.dueDay <= state.day }) {
            when (o.kind) {
                ObligationKind.SOCIAL_SECURITY, ObligationKind.DIVIDEND -> settle(o, PaidBy.DIRECT_DEBIT)
                else -> settle(o, PaidBy.GESTORIA)
            }
        }
    }

    private fun settle(o: Obligation, by: PaidBy) {
        val daysLate = state.day - o.dueDay
        val applies = by == PaidBy.PLAYER && o.kind != ObligationKind.SOCIAL_SECURITY && o.kind != ObligationKind.DIVIDEND
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
            if (by == PaidBy.GESTORIA) {
                event(L("Tu departamento fiscal ha presentado ${o.label.es}.", "Your tax department filed ${o.label.en}."), EventKind.TAX)
            }
        }
        o.paidDay = state.day
        o.paidBy = by
        o.surcharge = surcharge
    }

    private fun processCases() {
        for (pc in state.cases.filter { state.day - it.arrivalDay >= CASE_AUTO_DAYS }) {
            state.cases.remove(pc)
            val fee = pc.fee / 2
            invoiceCase(pc.template, fee)
            state.casesAuto++
            gainReputation(REP_AUTO)
        }
        if (state.day >= state.nextCaseDay) {
            if (state.cases.size < MAX_CASES) pickCaseTemplate()?.let { addCase(it) }
            state.nextCaseDay = state.day + CASE_INTERVAL_DAYS
        }
    }

    private fun pickCaseTemplate(): CaseTemplate? {
        val tierIndex = tier().index
        val open = Cases.all.filter { deptOpen(it.dept) && it.minTier <= tierIndex }
        if (open.isEmpty()) return null
        val pending = state.cases.map { it.templateId }.toSet()
        val fresh = open.filter { it.id !in state.recentCases && it.id !in pending }
        val candidates = fresh.ifEmpty { open.filter { it.id !in pending }.ifEmpty { open } }
        val weights = candidates.map { (it.minTier + 1) * (it.minTier + 1) }
        var r = nextInt(weights.sum())
        for ((i, c) in candidates.withIndex()) {
            if (r < weights[i]) return c
            r -= weights[i]
        }
        return candidates.last()
    }

    private fun addCase(t: CaseTemplate) {
        val tr = tier()
        val variation = 0.8 + nextDouble() * 0.4
        val scaled = (incomePerSecond() * CASE_FEE_SECONDS * variation).toLong()
        val fee = max(Money.times(tr.caseMinFee, variation), scaled)
        state.cases.add(PendingCase(state.nextCaseId++, t.id, state.day, fee, tr.index, shuffled(listOf(0, 1, 2, 3))))
        state.recentCases.addLast(t.id)
        while (state.recentCases.size > 8) state.recentCases.removeFirst()
    }

    private fun gainReputation(points: Long) {
        if (points <= 0) return
        state.reputation += points
        val t = tier()
        if (t.index > state.bestTier) {
            state.bestTier = t.index
            event(
                L(
                    "¡Tu reputación crece! Ahora llegan clientes del tipo «${t.name.es}»: los honorarios se multiplican por ${t.feeMultiplier.toInt()}.",
                    "Your reputation grows! Clients of type \"${t.name.en}\" now come to you: fees are multiplied by ${t.feeMultiplier.toInt()}.",
                ),
                EventKind.GOOD,
            )
        }
    }

    /** Amortización lineal mensual: ordenadores (4 años), obras del local (10 años) y software (3 años). */
    private fun postDepreciation() {
        val b = EntryBuilder()
        var total = 0L
        for (ds in state.depts.values) {
            val amount = monthlyDepreciation(ds.equipmentGross, ds.equipmentDepreciation, EquipmentTiers.USEFUL_LIFE_YEARS)
            if (amount <= 0) continue
            ds.equipmentDepreciation += amount
            total += amount
        }
        if (total > 0) b.credit("2817", total)
        val fitOut = monthlyDepreciation(state.fitOutGross, state.fitOutDepreciation, FIT_OUT_USEFUL_LIFE_YEARS)
        if (fitOut > 0) {
            state.fitOutDepreciation += fitOut
            b.credit("2815", fitOut)
            total += fitOut
        }
        if (total > 0) {
            b.debit("681", total)
            post(L("Amortización del inmovilizado material · ${Fmt.monthYear(date(), Lang.ES)}", "Depreciation · ${Fmt.monthYear(date(), Lang.EN)}"), b)
        }
        val software = monthlyDepreciation(state.softwareGross, state.softwareDepreciation, SoftwareTiers.USEFUL_LIFE_YEARS)
        if (software > 0) {
            state.softwareDepreciation += software
            post(
                L("Amortización de las aplicaciones informáticas · ${Fmt.monthYear(date(), Lang.ES)}", "Software amortisation · ${Fmt.monthYear(date(), Lang.EN)}"),
                EntryBuilder().debit("680", software).credit("2806", software),
            )
        }
    }

    private fun monthlyDepreciation(gross: Long, accumulated: Long, years: Int): Long {
        val remaining = gross - accumulated
        if (remaining <= 0) return 0
        return min(max(1L, gross / (years * 12L)), remaining)
    }

    private fun postPayroll() {
        val d = date()
        val daysInMonth = d.lengthOfMonth
        var gross = 0L
        for (def in Departments.all) {
            val ds = dept(def.id)
            if (ds.workerDaysThisMonth > 0) gross += def.salary * ds.workerDaysThisMonth / daysInMonth
            ds.workerDaysThisMonth = 0
        }
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

    /** Alquiler del local: IVA del 21 % y, si el arrendador es persona física, retención del 19 % (modelo 115). */
    private fun postRent() {
        val o = office()
        val vat = Money.pct(o.rent, Vat.GENERAL)
        val wh = if (o.landlordIsIndividual) Money.pct(o.rent, Withholding.RENT) else 0L
        val total = o.rent + vat - wh
        val month = Fmt.monthYear(date(), Lang.ES)
        post(
            L("Alquiler de ${month} · ${o.landlord.es}", "Rent for ${Fmt.monthYear(date(), Lang.EN)} · ${o.landlord.en}"),
            EntryBuilder().debit("621", o.rent).debit("472", vat).credit("4751", wh).credit("410", total),
        )
        ensureCash(total)
        post(L("Pago del alquiler · ${o.landlord.es}", "Rent payment · ${o.landlord.en}"), EntryBuilder().debit("410", total).credit("572", total))
        state.wh115 += wh
    }

    private fun postUtilities() {
        val base = monthlyUtilities()
        val vat = Money.pct(base, Vat.GENERAL)
        post(
            L("Suministros del mes (luz, agua e internet)", "Monthly utilities (power, water and internet)"),
            EntryBuilder().debit("628", base).debit("472", vat).credit("410", base + vat),
        )
        ensureCash(base + vat)
        post(L("Pago de los suministros", "Utilities payment"), EntryBuilder().debit("410", base + vat).credit("572", base + vat))
    }

    /** Prima trimestral del seguro de la oficina: exenta de IVA (art. 20.Uno.16.º LIVA). */
    private fun postInsurance() {
        val premium = office().insuranceQuarterly
        ensureCash(premium)
        post(
            L("Seguro de responsabilidad civil y multirriesgo de la oficina", "Professional liability and office insurance"),
            EntryBuilder().debit("625", premium).credit("572", premium),
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

    private fun buyComputers(d: DeptState, def: DeptDef, count: Int, tier: EquipmentTier) {
        val cost = tier.price * count
        post(
            L("Compra de ${if (count == 1) "un ordenador" else "$count ordenadores"} · ${def.name.es} (${tier.name.es})", "Purchase of ${if (count == 1) "a computer" else "$count computers"} · ${def.name.en} (${tier.name.en})"),
            EntryBuilder().debit("217", cost).debit("472", Money.pct(cost, Vat.GENERAL)).credit("572", withVat(cost)),
        )
        d.equipmentGross += cost
    }

    private fun withVat(base: Long): Long = base + Money.pct(base, Vat.GENERAL)

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
