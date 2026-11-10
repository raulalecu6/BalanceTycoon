package com.alecu.balancetycoon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.EquipmentTiers
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.SoftwareTiers
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.ui.theme.Palette

/** Equipos informáticos de cada departamento y programa de gestión de la gestoría. */
@Composable
fun EquipmentScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val open = Departments.all.take(engine.office().floors).filter { engine.dept(it.id).level > 0 }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "software") {
            val current = SoftwareTiers.get(engine.state.softwareTier)
            val next = engine.nextSoftware()
            SectionCard(stringResource(R.string.software_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💽", fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        PixelText(current.name.get(lang), size = 15.sp)
                        Hint(stringResource(R.string.multiplier_all, Fmt.decimal2(current.multiplier, lang)))
                    }
                }
                if (next == null) {
                    Hint(stringResource(R.string.equipment_max))
                } else if (engine.tier().index < next.minTier) {
                    Hint(stringResource(R.string.software_locked, next.name.get(lang)))
                } else {
                    PriceButton(
                        title = next.name.get(lang) + " · ×" + Fmt.decimal2(next.multiplier, lang),
                        price = moneyShort(next.price + Money.pct(next.price, 2100), lang),
                        onClick = { vm.buySoftware() },
                        enabled = engine.canBuySoftware(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Hint(stringResource(R.string.software_note))
            }
        }
        items(open, key = { it.id }) { def ->
            val d = engine.dept(def.id)
            val current = EquipmentTiers.get(d.equipmentTier)
            val next = EquipmentTiers.all.getOrNull(d.equipmentTier + 1)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        PixelText(def.name.get(lang), size = 17.sp, color = Palette.Gold)
                        Hint(stringResource(R.string.equipment_current, current.name.get(lang), Fmt.decimal2(current.multiplier, lang), d.desks))
                    }
                }
                when {
                    next == null -> Hint(stringResource(R.string.equipment_max))
                    d.equipmentTier >= engine.maxEquipmentTier() -> Hint(stringResource(R.string.equipment_locked, next.name.get(lang)))
                    else -> {
                        val cost = engine.equipmentUpgradeCost(def.id)
                        PriceButton(
                            title = next.name.get(lang) + " · ×" + Fmt.decimal2(next.multiplier, lang),
                            price = moneyShort(cost + Money.pct(cost, 2100), lang),
                            onClick = { vm.upgradeEquipment(def.id) },
                            enabled = engine.canUpgradeEquipment(def.id),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val book = engine.equipmentBookValue(def.id)
                        if (book > 0) Hint(stringResource(R.string.equipment_disposal, money(book, lang)))
                    }
                }
            }
        }
        item(key = "note") {
            Hint(stringResource(R.string.equipment_note), Palette.Muted)
        }
    }
}
