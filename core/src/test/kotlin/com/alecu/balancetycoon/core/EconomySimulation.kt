package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.AutoPlayer
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.i18n.Lang
import org.junit.Test

/**
 * Mediciones de equilibrio: no comprueba nada, imprime los tiempos de desbloqueo y las cifras
 * de una partida jugada por el jugador automático. Los resultados se copian en el documento de estado.
 */
class EconomySimulation {
    private fun m(c: Long) = Fmt.money(c, Lang.ES)

    @Test
    fun mediciones() {
        val hours = (System.getProperty("sim.hours") ?: "6").toDouble()
        val e = GameEngine.newGame(2026, seed = 2026L)
        val bot = AutoPlayer(e, accuracy = 0.85)
        val opened = LinkedHashMap<String, Double>()
        val firstWorker = LinkedHashMap<String, Double>()
        val tiers = LinkedHashMap<Int, Double>()
        val offices = LinkedHashMap<Int, Double>()
        var t = 0.0
        var lastPrinted = 0
        val activeSeconds = hours * 3600.0
        val checkpoints = listOf(5, 15, 30, 60, 90, 120, 180, 240, 300, 360, 480, 600).map { it * 60.0 }.toMutableList()
        println("=== Mediciones de equilibrio (jugador automático activo ${hours.toInt()} h, acierto 85 %) ===")
        while (t < activeSeconds) {
            bot.act()
            e.advance(0.5)
            t += 0.5
            for (def in Departments.all) {
                if (e.dept(def.id).level > 0 && def.id !in opened) opened[def.id] = t
                if (e.dept(def.id).workers > 0 && def.id !in firstWorker) firstWorker[def.id] = t
            }
            if (e.tier().index !in tiers) tiers[e.tier().index] = t
            e.state.lastYear?.let {
                if (it.year != lastPrinted) {
                    lastPrinted = it.year
                    println("  >> Ejercicio ${it.year} (min %.0f): honorarios ${m(it.revenue)}, resultado ${m(it.netResult)}, IS ${m(it.incomeTax)}, activo ${m(it.totalAssets)}".format(t / 60))
                }
            }
            if (e.state.officeIndex !in offices) offices[e.state.officeIndex] = t
            if (checkpoints.isNotEmpty() && t >= checkpoints.first()) {
                val cp = checkpoints.removeAt(0)
                println(
                    "min %3.0f | %s | banco %s | póliza %s | €/min %s | rep %d (%s) | local %d | soft %d | %s".format(
                        cp / 60, Fmt.dateShort(GameDate(e.state.day), Lang.ES), m(e.cash()), m(e.creditDrawn()),
                        m((e.incomePerSecond() * 60).toLong()), e.state.reputation, e.tier().name.es, e.state.officeIndex, e.state.softwareTier,
                        Departments.all.filter { e.dept(it.id).level > 0 }.joinToString(" ") {
                            val d = e.dept(it.id)
                            "${it.id.take(4)} n${d.level} t${d.workers} f${d.training} e${d.equipmentTier}"
                        },
                    ),
                )
            }
        }
        println("Departamentos abiertos (minuto real):")
        for ((id, sec) in opened) println("  ${Departments.get(id).name.es}: %.1f min".format(sec / 60))
        println("Primer trabajador (minuto real):")
        for ((id, sec) in firstWorker) println("  ${Departments.get(id).name.es}: %.1f min".format(sec / 60))
        println("Tipos de cliente alcanzados (minuto real):")
        for ((i, sec) in tiers) println("  $i: %.1f min".format(sec / 60))
        println("Locales (minuto real):")
        for ((i, sec) in offices) println("  $i: %.1f min".format(sec / 60))
        e.state.lastYear?.let {
            println("Último ejercicio cerrado ${it.year}: honorarios ${m(it.revenue)}, resultado ${m(it.netResult)}, IS ${m(it.incomeTax)} (${it.regime?.title?.es}), activo ${m(it.totalAssets)}")
        }
        val pyg = Reports.pyg(e.state.ledger)
        println("Ejercicio en curso ${e.state.ledger.year}: honorarios ${m(pyg.revenue)}, personal ${m(pyg.personnel)}, otros gastos ${m(pyg.otherOpex)}, amortización ${m(pyg.depreciation)}, bajas ${m(pyg.disposals)}, resultado ${m(pyg.netResult)}")
        println("Patrimonio gestionado: ${m(e.managedWealth())} · clientes ${e.clientCount()} · plantilla ${e.workersTotal()}")
        println("Consultas: ${e.state.casesCorrect} bien, ${e.state.casesWrong} mal, ${e.state.casesAuto} automáticas · racha máx. ${e.state.bestStreak}")
        println("Asientos registrados: ${e.state.ledger.totalPosted}")

        val report = e.catchUp(8 * 3600.0)
        println("Noche fuera (8 h): ${report.days} días, honorarios ${m(report.sales)}, variación del banco ${m(report.cashChange)}, ${report.entries} asientos, +${report.reputation} de reputación")
        println("Tamaño de la partida guardada: %.1f KB".format(com.alecu.balancetycoon.core.save.SaveCodec.encode(e.state).length / 1024.0))
    }
}
