package com.alecu.balancetycoon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.BuyMode
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.game.DeptDef
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.EquipmentTiers
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Palette

/** Pantalla principal: el edificio de la gestoría y, debajo, el panel de departamentos. */
@Composable
fun OfficeScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    Column(Modifier.fillMaxSize()) {
        Tower(
            engine = engine,
            lang = lang,
            tick = tick,
            modifier = Modifier.fillMaxWidth().weight(0.56f),
            onFloorTap = { id ->
                if (engine.dept(id).level == 0) vm.buy(id) else vm.tap(id)
            },
            onLobbyTap = { vm.tab = 3 },
        )
        DeptPanel(vm, engine, lang, tick, Modifier.fillMaxWidth().weight(0.44f))
    }
}

@Composable
private fun DeptPanel(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long, modifier: Modifier) {
    val office = engine.office()
    val visible = Departments.all.take(office.floors)
    Column(
        modifier
            .background(Palette.PanelDark)
            .border(2.dp, Palette.Stroke)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (mode in BuyMode.values()) {
                val label = when (mode) {
                    BuyMode.ONE -> stringResource(R.string.buy_x1)
                    BuyMode.TEN -> stringResource(R.string.buy_x10)
                    BuyMode.MAX -> stringResource(R.string.buy_max)
                }
                GameButton(
                    onClick = { vm.buyMode = mode },
                    color = if (vm.buyMode == mode) Palette.Gold else Palette.PanelLight,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    PixelText(label, size = 13.sp, color = if (vm.buyMode == mode) Palette.Stroke else Palette.Cream)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                PixelText(stringResource(R.string.income_rate), size = 11.sp, color = Palette.Muted)
                PixelText(perSecond(engine.incomePerSecond(), lang), size = 15.sp, color = Palette.Mint)
            }
            GameButton(
                onClick = { vm.watchAdForBoost() },
                color = if (engine.boostActive()) Palette.Gold else Palette.PanelLight,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                PixelText(if (engine.boostActive()) "×2 " + durationLabel(engine.boostRemainingSeconds()) else "📺 ×2", size = 12.sp,
                    color = if (engine.boostActive()) Palette.Stroke else Palette.Cream)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            items(visible, key = { it.id }) { def -> DeptRow(vm, engine, def, lang) }
            val next = Departments.all.getOrNull(office.floors)
            val nextOffice = engine.nextOffice()
            if (next != null && nextOffice != null) {
                item(key = "next") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Palette.Panel)
                            .border(2.dp, Palette.Stroke, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔒", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            PixelText("${next.emoji} ${next.name.get(lang)}", size = 15.sp)
                            Hint(stringResource(R.string.next_floor_hint, nextOffice.name.get(lang), nextOffice.city))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeptRow(vm: GameViewModel, engine: GameEngine, def: DeptDef, lang: Lang) {
    val d = engine.dept(def.id)
    val open = d.level > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Panel)
            .border(2.dp, Palette.Stroke, RoundedCornerShape(8.dp))
            .clickable { vm.tap(def.id) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icono del departamento con su nivel.
        Box(Modifier.size(54.dp)) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (open) Palette.deptWall(def.id) else Palette.Disabled)
                    .border(2.dp, Palette.Stroke, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(def.emoji, fontSize = 24.sp)
            }
            if (open) Badge(d.level.toString(), Modifier.align(Alignment.BottomEnd), color = Palette.PanelLight)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PixelText(def.name.get(lang).uppercase(), size = 15.sp)
            if (open) {
                val cycle = engine.cycleSeconds(def.id)
                val working = d.workers > 0 || d.running
                val progress = if (!working) 0f else if (cycle < 0.6) 1f else (d.progress / cycle).toFloat()
                GameProgressBar(progress, perSecond(engine.revenuePerSecond(def.id), lang), Modifier.fillMaxWidth())
                val staff = if (d.workers == 0) stringResource(R.string.no_staff_tap)
                else stringResource(R.string.staff_line, d.workers, engine.maxWorkers(), EquipmentTiers.get(d.equipmentTier).name.get(lang))
                Hint(staff)
            } else {
                Hint(def.description.get(lang))
            }
        }
        Spacer(Modifier.width(8.dp))
        val count = vm.buyCount(def.id)
        val needed = engine.upgradeCashNeeded(def.id, count)
        PriceButton(
            title = if (open) "+$count" else stringResource(R.string.open_short),
            price = moneyShort(needed, lang),
            onClick = { vm.buy(def.id) },
            enabled = engine.cash() >= needed,
            modifier = Modifier.width(96.dp),
        )
    }
}
