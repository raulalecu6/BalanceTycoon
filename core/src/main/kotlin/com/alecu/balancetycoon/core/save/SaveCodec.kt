package com.alecu.balancetycoon.core.save

import com.alecu.balancetycoon.core.accounting.EntryLine
import com.alecu.balancetycoon.core.accounting.JournalEntry
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.game.Cases
import com.alecu.balancetycoon.core.game.DeptState
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.EventKind
import com.alecu.balancetycoon.core.game.GameEvent
import com.alecu.balancetycoon.core.game.GameState
import com.alecu.balancetycoon.core.game.Obligation
import com.alecu.balancetycoon.core.game.ObligationKind
import com.alecu.balancetycoon.core.game.PaidBy
import com.alecu.balancetycoon.core.game.PendingCase
import com.alecu.balancetycoon.core.game.PendingDistribution
import com.alecu.balancetycoon.core.game.PendingFlow
import com.alecu.balancetycoon.core.game.YearSummary
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.tax.CorporateTaxRegime

/** La partida guardada es de una versión anterior incompatible (el prototipo 0.1 de las tiendas). */
class IncompatibleSaveException(message: String) : Exception(message)

/**
 * Convierte la partida en JSON y viceversa. El campo "v" es la versión del formato:
 * si en el futuro cambia, aquí se migrarán las partidas antiguas.
 * La versión 1 era el prototipo de tiendas y no se puede convertir en una gestoría.
 */
object SaveCodec {
    const val FORMAT_VERSION = 2L

    fun encode(s: GameState): String {
        val led = s.ledger
        val m = LinkedHashMap<String, Any?>()
        m["v"] = FORMAT_VERSION
        m["startYear"] = s.startYear.toLong()
        m["company"] = s.companyName
        m["day"] = s.day
        m["secondsIntoDay"] = s.secondsIntoDay
        m["playSeconds"] = s.playSeconds
        m["ledger"] = mapOf(
            "year" to led.year.toLong(),
            "next" to led.nextNumber.toLong(),
            "posted" to led.totalPosted,
            "totals" to led.allTotals().entries.associate { it.key to listOf(it.value[0], it.value[1]) },
            "recent" to led.recent.map { e ->
                mapOf(
                    "n" to e.number.toLong(), "y" to e.year.toLong(), "d" to e.date.epochDay,
                    "es" to e.concept.es, "en" to e.concept.en,
                    "l" to e.lines.map { listOf(it.account, it.debit, it.credit) },
                )
            },
        )
        m["depts"] = s.depts.values.map { d ->
            mapOf(
                "id" to d.id, "level" to d.level.toLong(), "workers" to d.workers.toLong(), "training" to d.training.toLong(),
                "equip" to d.equipmentTier.toLong(), "running" to d.running, "progress" to d.progress,
                "eqGross" to d.equipmentGross, "eqDep" to d.equipmentDepreciation,
                "todayRev" to d.todayRevenue, "todayCycles" to d.todayCycles,
                "workerDays" to d.workerDaysThisMonth, "lifetime" to d.lifetimeRevenue, "cycles" to d.completedCycles,
            )
        }
        m["flows"] = s.flows.map { listOf(it.dueDay, it.account, it.amount) }
        m["obligations"] = s.obligations.map { o ->
            mapOf(
                "id" to o.id.toLong(), "kind" to o.kind.name, "es" to o.label.es, "en" to o.label.en,
                "acc" to o.account, "amount" to o.amount, "created" to o.createdDay, "due" to o.dueDay,
                "paidDay" to o.paidDay, "paidBy" to o.paidBy?.name, "surcharge" to o.surcharge, "fee" to o.fee,
            )
        }
        m["nextObligationId"] = s.nextObligationId.toLong()
        m["office"] = s.officeIndex.toLong()
        m["fitOut"] = listOf(s.fitOutGross, s.fitOutDepreciation)
        m["software"] = listOf(s.softwareTier.toLong(), s.softwareGross, s.softwareDepreciation)
        m["reputation"] = s.reputation
        m["bestTier"] = s.bestTier.toLong()
        m["cases"] = s.cases.map { c ->
            mapOf("id" to c.id.toLong(), "t" to c.templateId, "day" to c.arrivalDay, "fee" to c.fee, "tier" to c.tier.toLong(), "order" to c.order.map { it.toLong() })
        }
        m["nextCaseId"] = s.nextCaseId.toLong()
        m["nextCaseDay"] = s.nextCaseDay
        m["recentCases"] = s.recentCases.toList()
        m["streak"] = s.streak.toLong()
        m["bestStreak"] = s.bestStreak.toLong()
        m["casesStats"] = listOf(s.casesCorrect.toLong(), s.casesWrong.toLong(), s.casesAuto.toLong())
        m["boostUntil"] = s.boostUntil
        m["rng"] = s.rngState
        m["prevIncn"] = s.prevYearIncn
        m["pendingLosses"] = s.pendingLosses
        m["firstPositiveBaseYear"] = s.firstPositiveBaseYear.toLong()
        m["nonDeductible"] = s.nonDeductible
        m["wh"] = listOf(s.wh111, s.wh115, s.wh123)
        m["pendingDistribution"] = s.pendingDistribution?.let { mapOf("year" to it.year.toLong(), "result" to it.result) }
        m["lastYear"] = s.lastYear?.let {
            mapOf(
                "year" to it.year.toLong(), "revenue" to it.revenue, "net" to it.netResult, "tax" to it.incomeTax,
                "assets" to it.totalAssets, "equity" to it.equity, "regime" to it.regime?.name,
                "base" to it.taxableBase, "offset" to it.lossesOffset,
            )
        }
        m["creditDaySum"] = s.creditDaySum
        m["sales30"] = s.salesLast30.toList()
        m["todaySales"] = s.todaySales
        m["lifetimeSales"] = s.lifetimeSales
        m["ownerWealth"] = s.ownerWealth
        m["events"] = s.events.map { mapOf("seq" to it.seq, "day" to it.day, "es" to it.text.es, "en" to it.text.en, "k" to it.kind.name) }
        m["eventSeq"] = s.eventSeq
        m["savedAt"] = s.lastSavedMillis
        return Json.write(m)
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): GameState {
        val m = Json.parse(text) as Map<String, Any?>
        val version = m.long("v")
        require(version <= FORMAT_VERSION) { "La partida es de una versión más nueva del juego ($version)" }
        if (version < 2) throw IncompatibleSaveException("Partida del prototipo de tiendas (versión $version)")

        val s = GameState(m.int("startYear"))
        s.companyName = m["company"] as String
        s.day = m.long("day")
        s.secondsIntoDay = m.double("secondsIntoDay")
        s.playSeconds = m.double("playSeconds")

        val led = m["ledger"] as Map<String, Any?>
        s.ledger.year = led.int("year")
        s.ledger.restoreCounters(led.int("next"), led.long("posted"))
        for ((code, v) in led["totals"] as Map<String, Any?>) {
            val pair = v as List<Any?>
            s.ledger.restoreTotals(code, (pair[0] as Number).toLong(), (pair[1] as Number).toLong())
        }
        for (e in led["recent"] as List<Any?>) {
            val em = e as Map<String, Any?>
            val lines = (em["l"] as List<Any?>).map {
                val l = it as List<Any?>
                EntryLine(l[0] as String, (l[1] as Number).toLong(), (l[2] as Number).toLong())
            }
            s.ledger.restoreEntry(JournalEntry(em.int("n"), em.int("y"), GameDate(em.long("d")), L(em["es"] as String, em["en"] as String), lines))
        }

        // Se crean todos los departamentos del catálogo (por si una versión nueva añade alguno) y se rellenan los guardados.
        for (def in Departments.all) s.depts[def.id] = DeptState(def.id)
        for (x in m["depts"] as List<Any?>) {
            val dm = x as Map<String, Any?>
            val d = s.depts[dm["id"] as String] ?: continue
            d.level = dm.int("level")
            d.workers = dm.int("workers")
            d.training = dm.int("training")
            d.equipmentTier = dm.int("equip")
            d.running = dm["running"] as Boolean
            d.progress = dm.double("progress")
            d.equipmentGross = dm.long("eqGross")
            d.equipmentDepreciation = dm.long("eqDep")
            d.todayRevenue = dm.long("todayRev")
            d.todayCycles = dm.long("todayCycles")
            d.workerDaysThisMonth = dm.long("workerDays")
            d.lifetimeRevenue = dm.long("lifetime")
            d.completedCycles = dm.long("cycles")
        }
        for (x in m["flows"] as List<Any?>) {
            val f = x as List<Any?>
            s.flows.add(PendingFlow((f[0] as Number).toLong(), f[1] as String, (f[2] as Number).toLong()))
        }
        for (x in m["obligations"] as List<Any?>) {
            val om = x as Map<String, Any?>
            val o = Obligation(
                om.int("id"), ObligationKind.valueOf(om["kind"] as String), L(om["es"] as String, om["en"] as String),
                om["acc"] as String, om.long("amount"), om.long("created"), om.long("due"),
            )
            o.paidDay = (om["paidDay"] as Number?)?.toLong()
            o.paidBy = (om["paidBy"] as String?)?.let { PaidBy.valueOf(it) }
            o.surcharge = om.long("surcharge")
            o.fee = om.long("fee")
            s.obligations.add(o)
        }
        s.nextObligationId = m.int("nextObligationId")
        s.officeIndex = m.int("office")
        val fit = m["fitOut"] as List<Any?>
        s.fitOutGross = (fit[0] as Number).toLong()
        s.fitOutDepreciation = (fit[1] as Number).toLong()
        val sw = m["software"] as List<Any?>
        s.softwareTier = (sw[0] as Number).toInt()
        s.softwareGross = (sw[1] as Number).toLong()
        s.softwareDepreciation = (sw[2] as Number).toLong()
        s.reputation = m.long("reputation")
        s.bestTier = m.int("bestTier")
        for (x in m["cases"] as List<Any?>) {
            val cm = x as Map<String, Any?>
            val id = cm["t"] as String
            if (Cases.find(id) == null) continue // consulta retirada en una versión nueva
            s.cases.add(PendingCase(cm.int("id"), id, cm.long("day"), cm.long("fee"), cm.int("tier"), (cm["order"] as List<Any?>).map { (it as Number).toInt() }))
        }
        s.nextCaseId = m.int("nextCaseId")
        s.nextCaseDay = m.long("nextCaseDay")
        for (x in m["recentCases"] as List<Any?>) s.recentCases.addLast(x as String)
        s.streak = m.int("streak")
        s.bestStreak = m.int("bestStreak")
        val cs = m["casesStats"] as List<Any?>
        s.casesCorrect = (cs[0] as Number).toInt()
        s.casesWrong = (cs[1] as Number).toInt()
        s.casesAuto = (cs[2] as Number).toInt()
        s.boostUntil = m.double("boostUntil")
        s.rngState = m.long("rng")
        s.prevYearIncn = m.long("prevIncn")
        s.pendingLosses = m.long("pendingLosses")
        s.firstPositiveBaseYear = m.int("firstPositiveBaseYear")
        s.nonDeductible = m.long("nonDeductible")
        val wh = m["wh"] as List<Any?>
        s.wh111 = (wh[0] as Number).toLong()
        s.wh115 = (wh[1] as Number).toLong()
        s.wh123 = (wh[2] as Number).toLong()
        s.pendingDistribution = (m["pendingDistribution"] as Map<String, Any?>?)?.let { PendingDistribution(it.int("year"), it.long("result")) }
        s.lastYear = (m["lastYear"] as Map<String, Any?>?)?.let {
            YearSummary(
                it.int("year"), it.long("revenue"), it.long("net"), it.long("tax"), it.long("assets"), it.long("equity"),
                (it["regime"] as String?)?.let { r -> CorporateTaxRegime.valueOf(r) }, it.long("base"), it.long("offset"),
            )
        }
        s.creditDaySum = m.long("creditDaySum")
        val sales30 = m["sales30"] as List<Any?>
        for (i in 0 until minOf(30, sales30.size)) s.salesLast30[i] = (sales30[i] as Number).toLong()
        s.todaySales = m.long("todaySales")
        s.lifetimeSales = m.long("lifetimeSales")
        s.ownerWealth = m.long("ownerWealth")
        for (x in m["events"] as List<Any?>) {
            val em = x as Map<String, Any?>
            s.events.addLast(GameEvent(em.long("seq"), em.long("day"), L(em["es"] as String, em["en"] as String), EventKind.valueOf(em["k"] as String)))
        }
        s.eventSeq = m.long("eventSeq")
        s.lastSavedMillis = m.long("savedAt")
        return s
    }

    private fun Map<String, Any?>.long(key: String): Long = (this[key] as Number).toLong()
    private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number).toInt()
    private fun Map<String, Any?>.double(key: String): Double = (this[key] as Number).toDouble()
}
