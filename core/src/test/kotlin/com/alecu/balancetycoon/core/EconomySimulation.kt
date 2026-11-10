package com.alecu.balancetycoon.core

import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.Lines
import com.alecu.balancetycoon.core.i18n.Lang
import org.junit.Test

/**
 * Mediciones de equilibrio: no comprueba nada, imprime los tiempos de desbloqueo y las cifras
 * de una partida jugada por el bot. Los resultados se copian en el documento de estado.
 */
class EconomySimulation {
    private fun m(c: Long) = Fmt.money(c, Lang.ES)

    @Test
    fun mediciones() {
        val e = GameEngine.newGame(2026, seed = 2026L)
        val bot = Bot(e, accuracy = 0.85)
        val unlocked = LinkedHashMap<String, Double>()
        val managers = LinkedHashMap<String, Double>()
        var t = 0.0
        val activeSeconds = 4 * 3600.0
        val checkpoints = listOf(5, 15, 30, 60, 120, 180, 240).map { it * 60.0 }.toMutableList()
        println("=== Mediciones de equilibrio (bot activo 4 h, acierto 85 %) ===")
        while (t < activeSeconds) {
            bot.act()
            e.advance(0.5)
            t += 0.5
            for (def in Lines.all) {
                if (e.line(def.id).level > 0 && def.id !in unlocked) unlocked[def.id] = t
                if (e.line(def.id).hasManager && def.id !in managers) managers[def.id] = t
            }
            if (checkpoints.isNotEmpty() && t >= checkpoints.first()) {
                val cp = checkpoints.removeAt(0)
                val rps = Lines.all.sumOf { e.revenuePerSecond(it.id) }
                println(
                    "min %3.0f | %s | banco %s | póliza %s | ventas/min %s | niveles %s".format(
                        cp / 60, Fmt.dateShort(GameDate(e.state.day), Lang.ES), m(e.cash()), m(e.creditDrawn()),
                        m((rps * 60).toLong()), Lines.all.joinToString(" ") { "${it.emoji}${e.line(it.id).level}" },
                    ),
                )
            }
        }
        println("Desbloqueos (minuto real):")
        for ((id, sec) in unlocked) println("  ${Lines.get(id).name.es}: %.1f min".format(sec / 60))
        println("Encargados contratados (minuto real):")
        for ((id, sec) in managers) println("  ${Lines.get(id).name.es}: %.1f min".format(sec / 60))
        e.state.lastYear?.let {
            println("Último ejercicio cerrado ${it.year}: ventas ${m(it.revenue)}, resultado ${m(it.netResult)}, IS ${m(it.incomeTax)} (${it.regime?.title?.es}), activo ${m(it.totalAssets)}")
        }
        val pyg = Reports.pyg(e.state.ledger)
        println("Ejercicio en curso ${e.state.ledger.year}: ventas ${m(pyg.revenue)}, aprovisionamientos ${m(pyg.supplies)}, personal ${m(pyg.personnel)}, otros gastos ${m(pyg.otherOpex)}, amortización ${m(pyg.depreciation)}, resultado ${m(pyg.netResult)}")
        if (pyg.revenue > 0) println("Gastos generales (otros gastos de explotación) sobre ventas: %.1f %%".format(-pyg.otherOpex * 100.0 / pyg.revenue))
        println("Patrimonio del socio: ${m(e.state.ownerWealth)} · racha máx. ${e.state.bestStreak} · aciertos ${e.state.docsCorrect}/${e.state.docsCorrect + e.state.docsWrong}")
        println("Asientos registrados: ${e.state.ledger.totalPosted}")

        val before = e.cash()
        val report = e.catchUp(8 * 3600.0)
        println("Noche fuera (8 h): ${report.days} días, ventas ${m(report.sales)}, variación del banco ${m(report.cashChange)}, ${report.entries} asientos")
        println("Tamaño de la partida guardada: %.1f KB".format(com.alecu.balancetycoon.core.save.SaveCodec.encode(e.state).length / 1024.0))
        if (before < 0) println("¡Banco negativo!")
    }
}
