package com.alecu.balancetycoon.core.game

/**
 * Jugador automático razonable. Se usa en los tests de equilibrio y para preparar partidas de
 * demostración (capturas de pantalla). Tramita a mano, responde consultas con un porcentaje de
 * acierto, contrata cuando el puesto se paga con holgura y compra la mejora más rentable por euro.
 */
class AutoPlayer(private val engine: GameEngine, private val accuracy: Double = 0.85, seed: Long = 7L) {
    private var rnd = seed
    private fun next(): Double {
        rnd = rnd * 6364136223846793005L + 1442695040888963407L
        return ((rnd ushr 11) * (1.0 / (1L shl 53)))
    }

    /** Una «decisión» del jugador. Conviene llamarla varias veces por segundo de juego. */
    fun act(answerCases: Boolean = true, buy: Boolean = true, distribute: Boolean = true) {
        val s = engine.state
        for (def in Departments.all) engine.tapDept(def.id)

        if (answerCases) {
            for (pc in s.cases.toList()) engine.answerCase(pc.id, if (next() < accuracy) 0 else 1)
        }
        if (distribute && s.pendingDistribution != null) engine.decideDistribution(0.5)
        if (!buy) return

        if (engine.canMove() && engine.cash() - engine.moveCashNeeded() >= reserve()) engine.moveOffice()
        // Como haría una persona, abre cada departamento nuevo en cuanto se lo puede permitir.
        for (def in Departments.all) {
            if (engine.deptAvailable(def.id) && engine.dept(def.id).level == 0 &&
                engine.cash() - engine.upgradeCashNeeded(def.id, 1) >= reserve()
            ) engine.buyLevels(def.id, 1)
        }
        hireWhenWorthIt()
        buyBest()
    }

    private fun reserve(): Long = engine.publicDebtEstimate() + engine.monthlyFixedCosts()

    private fun monthly(rps: Double): Double = rps * GameEngine.DAY_SECONDS * 30

    private fun hireWhenWorthIt() {
        for (def in Departments.all) {
            val d = engine.dept(def.id)
            if (d.level == 0 || d.workers >= engine.maxWorkers()) continue
            // El primer trabajador automatiza; los siguientes suman lo mismo que uno de los actuales.
            val gain = if (d.workers == 0) monthly(engine.revenuePerSecond(def.id)) else monthly(engine.revenuePerSecond(def.id)) / d.workers
            val cost = engine.workerMonthlyCost(def.id)
            if (gain > 2.0 * cost && engine.cash() - engine.hireCashNeeded(def.id) >= reserve()) engine.hire(def.id)
        }
    }

    private enum class Kind { LEVEL, EQUIPMENT, TRAINING, SOFTWARE }

    private fun buyBest() {
        var guard = 0
        while (guard++ < 300) {
            var bestKind: Kind? = null
            var bestId = ""
            var bestRatio = 0.0
            var bestCost = 0L
            fun consider(kind: Kind, id: String, gain: Double, cost: Long) {
                if (cost <= 0 || gain <= 0) return
                val ratio = gain / cost
                if (ratio > bestRatio) {
                    bestRatio = ratio
                    bestKind = kind
                    bestId = id
                    bestCost = cost
                }
            }
            val total = Departments.all.sumOf { engine.revenuePerSecond(it.id) }
            for (def in Departments.all) {
                if (!engine.deptAvailable(def.id)) continue
                val d = engine.dept(def.id)
                val rps = engine.revenuePerSecond(def.id)
                // Ganancia aproximada del siguiente nivel de cartera (incluye los hitos).
                val nextLevel = d.level + 1
                val ms = Departments.milestonesReached(nextLevel)
                val nextRps = def.feePerCycle * nextLevel * engine.deptMultiplier(def.id) * engine.globalMultiplier() / (def.cycleSeconds / (1 shl ms))
                consider(Kind.LEVEL, def.id, nextRps - rps, engine.upgradeCashNeeded(def.id, 1))
                if (d.level > 0 && d.equipmentTier < engine.maxEquipmentTier()) {
                    val cur = EquipmentTiers.get(d.equipmentTier).multiplier
                    val nxt = EquipmentTiers.get(d.equipmentTier + 1).multiplier
                    consider(Kind.EQUIPMENT, def.id, rps * (nxt / cur - 1), engine.equipmentUpgradeCost(def.id) * 121 / 100)
                }
                if (d.workers > 0 && d.training < engine.maxTraining()) {
                    val cur = Departments.trainingMultiplier(d.training)
                    val nxt = Departments.trainingMultiplier(d.training + 1)
                    consider(Kind.TRAINING, def.id, rps * (nxt / cur - 1), engine.trainingCost(def.id))
                }
            }
            engine.nextSoftware()?.let { next ->
                if (engine.tier().index >= next.minTier) {
                    val cur = SoftwareTiers.get(engine.state.softwareTier).multiplier
                    consider(Kind.SOFTWARE, "", total * (next.multiplier / cur - 1), next.price * 121 / 100)
                }
            }
            val kind = bestKind ?: return
            if (engine.cash() - bestCost < reserve()) return
            val ok = when (kind) {
                Kind.LEVEL -> engine.buyLevels(bestId, 1)
                Kind.EQUIPMENT -> engine.upgradeEquipment(bestId)
                Kind.TRAINING -> engine.train(bestId)
                Kind.SOFTWARE -> engine.buySoftware()
            }
            if (!ok) return
        }
    }
}
