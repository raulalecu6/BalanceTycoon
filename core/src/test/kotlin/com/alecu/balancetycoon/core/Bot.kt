package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.Lines
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.tax.SocialSecurity

/**
 * Jugador automático para tests y mediciones de equilibrio. Juega de forma razonable:
 * pulsa «Vender», compra la ampliación más rentable por euro, contrata encargados cuando
 * la línea lo paga con holgura, clasifica la bandeja y paga los impuestos antes de plazo.
 *
 * @param accuracy probabilidad de acertar la cuenta de cada documento.
 */
class Bot(private val engine: GameEngine, private val accuracy: Double = 0.85, seed: Long = 7L) {
    private var rnd = seed
    private fun next(): Double {
        rnd = rnd * 6364136223846793005L + 1442695040888963407L
        return ((rnd ushr 11) * (1.0 / (1L shl 53)))
    }

    /** Una «decisión» del jugador. Se llama varias veces por segundo de juego. */
    fun act(payTaxesEarly: Boolean = true, buy: Boolean = true, distribute: Boolean = true) {
        val s = engine.state
        for (def in Lines.all) engine.tapLine(def.id)

        for (doc in s.documents.toList()) {
            val choice = if (next() < accuracy) doc.template.correctAccount else doc.options.first { it != doc.template.correctAccount }
            engine.answerDocument(doc.id, choice)
        }
        if (payTaxesEarly) {
            for (o in engine.pendingObligations()) {
                if (engine.cash() - o.amount > reserve() / 2) engine.payObligation(o.id)
            }
        }
        if (distribute && s.pendingDistribution != null) engine.decideDistribution(0.5)

        for (def in Lines.all) {
            val ls = engine.line(def.id)
            if (ls.level > 0 && !ls.hasManager) {
                val monthlyMargin = engine.revenuePerSecond(def.id) * (1 - def.costRatio) * GameEngine.DAY_SECONDS * 30
                val salaryCost = def.managerSalary + Money.pct(def.managerSalary, SocialSecurity.EMPLOYER)
                if (monthlyMargin > 2.0 * salaryCost) engine.hireManager(def.id)
            }
        }
        if (buy) buyBest()
    }

    private fun reserve(): Long = engine.publicDebtEstimate() + engine.monthlyPayrollCost()

    private fun buyBest() {
        var guard = 0
        while (guard++ < 200) {
            var bestId: String? = null
            var bestRatio = 0.0
            for (def in Lines.all) {
                val ls = engine.line(def.id)
                val cost = engine.upgradeCashNeeded(def.id, 1)
                if (cost <= 0) continue
                val currentRps = if (ls.level == 0) 0.0 else engine.revenuePerSecond(def.id)
                // Ganancia aproximada: venta por segundo del nivel siguiente (incluye hitos).
                val nextLevel = ls.level + 1
                val ms = Lines.milestonesReached(nextLevel)
                val nextRps = def.revenuePerCycle * nextLevel * engine.salesMultiplier() / (def.cycleSeconds / (1 shl ms))
                val gain = (nextRps - currentRps) * (1 - def.costRatio)
                val ratio = gain / cost
                if (ratio > bestRatio) {
                    bestRatio = ratio
                    bestId = def.id
                }
            }
            val id = bestId ?: return
            if (engine.cash() - engine.upgradeCashNeeded(id, 1) < reserve()) return
            if (!engine.buyLevels(id, 1)) return
        }
    }
}
