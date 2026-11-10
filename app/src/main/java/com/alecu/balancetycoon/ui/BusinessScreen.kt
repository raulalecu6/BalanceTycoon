package com.alecu.balancetycoon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.BuyMode
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.LineDef
import com.alecu.balancetycoon.core.game.Lines
import com.alecu.balancetycoon.core.i18n.Lang
import kotlin.math.roundToInt

@Composable
fun BusinessScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") { BusinessSummary(vm, engine, lang, tick) }
        items(Lines.all, key = { it.id }) { def -> LineCard(vm, engine, def, lang, tick) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusinessSummary(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val perMinute = (Lines.all.sumOf { engine.revenuePerSecond(it.id) } * 60).toLong()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Hint(stringResource(R.string.sales_per_min))
                Text(money(perMinute, lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.multiplier, Fmt.decimal2(engine.salesMultiplier(), lang)),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (engine.boostActive()) {
            Text("🚀 " + stringResource(R.string.boost_title) + " · " + stringResource(R.string.boost_active, durationLabel(engine.boostRemainingSeconds())))
        } else {
            OutlinedButton(onClick = { vm.watchAdForBoost() }, modifier = Modifier.fillMaxWidth()) {
                Text("📺 " + stringResource(R.string.boost_title) + " · " + stringResource(R.string.boost_watch))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((mode, label) in listOf(
                BuyMode.ONE to stringResource(R.string.buy_x1),
                BuyMode.TEN to stringResource(R.string.buy_x10),
                BuyMode.MAX to stringResource(R.string.buy_max),
            )) {
                FilterChip(selected = vm.buyMode == mode, onClick = { vm.buyMode = mode }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun LineCard(vm: GameViewModel, engine: GameEngine, def: LineDef, lang: Lang, tick: Long) {
    val ls = engine.line(def.id)
    val collection = if (def.creditShare <= 0.0) {
        stringResource(R.string.collect_cash)
    } else {
        stringResource(R.string.collect_credit, "${(def.creditShare * 100).roundToInt()}%", def.collectionDays)
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(def.name.get(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Hint(stringResource(R.string.vat_rate, Fmt.rate(def.salesVat, lang)) + " · " + collection)
            }
            if (ls.level > 0) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(R.string.level, ls.level),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (ls.level == 0) {
            val canOpen = engine.cash() >= engine.upgradeCashNeeded(def.id, 1)
            Button(onClick = { vm.open(def.id) }, enabled = canOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_for, money(def.unlockCost, lang)))
            }
        } else {
            ActiveLine(vm, engine, def, lang)
        }
    }
}

/** Parte de la tarjeta de una línea ya abierta: progreso, ventas, ampliación y encargado. */
@Composable
private fun ActiveLine(vm: GameViewModel, engine: GameEngine, def: LineDef, lang: Lang) {
    val ls = engine.line(def.id)
    val cycle = engine.cycleSeconds(def.id)
    val progress = when {
        ls.hasManager && cycle < 0.25 -> 1f
        ls.hasManager || ls.running -> (ls.progress / cycle).toFloat().coerceIn(0f, 1f)
        else -> 0f
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
    Text(
        stringResource(R.string.sale_per_cycle, money(engine.revenuePerCycle(def.id), lang), Fmt.rate(def.salesVat, lang)),
        style = MaterialTheme.typography.bodyMedium,
    )
    val milestone = Lines.nextMilestone(ls.level)
    Hint(
        stringResource(R.string.cycle_time, Fmt.decimal2(cycle, lang)) +
            (if (milestone != null) " · " + stringResource(R.string.next_milestone, milestone) else ""),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!ls.hasManager) {
            Button(onClick = { vm.tap(def.id) }, enabled = !ls.running) {
                Text(stringResource(if (ls.running) R.string.selling else R.string.sell))
            }
        }
        val n = vm.buyCount(def.id)
        val canBuy = engine.cash() >= engine.upgradeCashNeeded(def.id, n)
        FilledTonalButton(onClick = { vm.buy(def.id) }, enabled = canBuy, modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.expand, n, money(engine.upgradeCost(def.id, n), lang)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (!ls.hasManager) {
        OutlinedButton(onClick = { vm.hire(def.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.hire, def.managerTitle.get(lang), money(def.managerSalary, lang)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "👤 " + stringResource(R.string.manager_working, def.managerTitle.get(lang)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { vm.fire(def.id) }) { Text(stringResource(R.string.fire)) }
        }
    }
    Hint(
        stringResource(
            R.string.asset_line,
            def.assetName.get(lang),
            def.assetAccount,
            money(ls.grossCost - ls.accumulatedDepreciation, lang),
        ),
    )
}
