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
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Palette

/** Plantilla: contratar trabajadores y formarlos, departamento a departamento. */
@Composable
fun StaffScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val open = Departments.all.take(engine.office().floors).filter { engine.dept(it.id).level > 0 }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "summary") {
            SectionCard(stringResource(R.string.staff_title)) {
                AmountRow(stringResource(R.string.staff_payroll), engine.monthlyPayrollCost(), lang, bold = true)
                Hint(stringResource(R.string.staff_summary, engine.workersTotal(), engine.maxWorkers()))
                Hint(stringResource(R.string.staff_note))
            }
        }
        items(open, key = { it.id }) { def ->
            val d = engine.dept(def.id)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        PixelText(def.name.get(lang), size = 17.sp, color = Palette.Gold)
                        Hint(def.workerTitle.get(lang) + " · " + stringResource(R.string.salary_month, moneyShort(def.salary, lang)))
                    }
                }
                // Puestos ocupados y libres.
                Text(
                    "🧑‍💼".repeat(d.workers) + "🪑".repeat((engine.maxWorkers() - d.workers).coerceAtLeast(0)),
                    fontSize = 22.sp,
                )
                Hint(stringResource(R.string.workers_count, d.workers, engine.maxWorkers()))
                if (d.workers < engine.maxWorkers()) {
                    val pc = engine.hireCashNeeded(def.id)
                    PriceButton(
                        title = stringResource(R.string.hire_btn),
                        price = stringResource(R.string.hire_price, moneyShort(engine.workerMonthlyCost(def.id), lang)) +
                            (if (pc > 0) " + " + stringResource(R.string.hire_pc, moneyShort(pc, lang)) else ""),
                        onClick = { vm.hire(def.id) },
                        enabled = engine.canHire(def.id),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Hint(stringResource(R.string.staff_full))
                }
                Spacer(Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PixelText(stringResource(R.string.training_title) + " " + starsText(d.training, Departments.MAX_TRAINING), size = 15.sp)
                        Hint(stringResource(R.string.training_effect, d.training * 15))
                    }
                }
                when {
                    d.workers == 0 -> Hint(stringResource(R.string.training_need_staff))
                    d.training >= Departments.MAX_TRAINING -> Hint(stringResource(R.string.training_max))
                    d.training >= engine.maxTraining() -> Hint(stringResource(R.string.training_locked))
                    else -> PriceButton(
                        title = stringResource(R.string.training_btn, d.training + 1),
                        price = moneyShort(engine.trainingCost(def.id), lang) + " · " + stringResource(R.string.vat_exempt),
                        onClick = { vm.train(def.id) },
                        enabled = engine.canTrain(def.id),
                        modifier = Modifier.fillMaxWidth(),
                        color = Palette.Gold,
                    )
                }
            }
        }
    }
}
