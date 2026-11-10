package com.alecu.balancetycoon.core.save

import com.alecu.balancetycoon.core.accounting.EntryLine
import com.alecu.balancetycoon.core.accounting.JournalEntry
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.game.EventKind
import com.alecu.balancetycoon.core.game.GameEvent
import com.alecu.balancetycoon.core.game.GameState
import com.alecu.balancetycoon.core.game.LineState
import com.alecu.balancetycoon.core.game.Lines
import com.alecu.balancetycoon.core.game.Obligation
import com.alecu.balancetycoon.core.game.ObligationKind
import com.alecu.balancetycoon.core.game.PaidBy
import com.alecu.balancetycoon.core.game.PendingDistribution
import com.alecu.balancetycoon.core.game.PendingDocument
import com.alecu.balancetycoon.core.game.PendingFlow
import com.alecu.balancetycoon.core.game.YearSummary
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.tax.CorporateTaxRegime

/**
 * Convierte la partida en JSON y viceversa. El campo "v" es la versión del formato:
 * si en el futuro cambia, aquí se migrarán las partidas antiguas.
 */
object SaveCodec {
    const val FORMAT_VERSION = 1L

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
        m["lines"] = s.lines.values.map { l ->
            mapOf(
                "id" to l.id, "level" to l.level.toLong(), "manager" to l.hasManager, "running" to l.running,
                "progress" to l.progress, "gross" to l.grossCost, "dep" to l.accumulatedDepreciation,
                "todayRev" to l.todayRevenue, "todayCycles" to l.todayCycles,
                "mgrDays" to l.managerDaysThisMonth.toLong(), "lifetime" to l.lifetimeRevenue,
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
        m["documents"] = s.documents.map { d ->
            mapOf("id" to d.id.toLong(), "t" to d.templateId, "day" to d.arrivalDay, "base" to d.base, "opts" to d.options)
        }
        m["nextDocId"] = s.nextDocId.toLong()
        m["nextDocDay"] = s.nextDocDay
        m["lastDocTemplate"] = s.lastDocTemplate
        m["streak"] = s.streak.toLong()
        m["bestStreak"] = s.bestStreak.toLong()
        m["docsCorrect"] = s.docsCorrect.toLong()
        m["docsWrong"] = s.docsWrong.toLong()
        m["assistant"] = s.hasAssistant
        m["assistantDays"] = s.assistantDaysThisMonth.toLong()
        m["gestoria"] = s.gestoriaActive
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

        // Se crean todas las líneas del catálogo (por si una versión nueva añade alguna) y se rellenan las guardadas.
        for (def in Lines.all) s.lines[def.id] = LineState(def.id)
        for (x in m["lines"] as List<Any?>) {
            val lm = x as Map<String, Any?>
            val ls = s.lines[lm["id"] as String] ?: continue
            ls.level = lm.int("level")
            ls.hasManager = lm["manager"] as Boolean
            ls.running = lm["running"] as Boolean
            ls.progress = lm.double("progress")
            ls.grossCost = lm.long("gross")
            ls.accumulatedDepreciation = lm.long("dep")
            ls.todayRevenue = lm.long("todayRev")
            ls.todayCycles = lm.long("todayCycles")
            ls.managerDaysThisMonth = lm.int("mgrDays")
            ls.lifetimeRevenue = lm.long("lifetime")
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
        for (x in m["documents"] as List<Any?>) {
            val dm = x as Map<String, Any?>
            s.documents.add(PendingDocument(dm.int("id"), dm["t"] as String, dm.long("day"), dm.long("base"), (dm["opts"] as List<Any?>).map { it as String }))
        }
        s.nextDocId = m.int("nextDocId")
        s.nextDocDay = m.long("nextDocDay")
        s.lastDocTemplate = m["lastDocTemplate"] as String
        s.streak = m.int("streak")
        s.bestStreak = m.int("bestStreak")
        s.docsCorrect = m.int("docsCorrect")
        s.docsWrong = m.int("docsWrong")
        s.hasAssistant = m["assistant"] as Boolean
        s.assistantDaysThisMonth = m.int("assistantDays")
        s.gestoriaActive = m["gestoria"] as Boolean
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
