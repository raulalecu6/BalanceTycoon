package com.alecu.balancetycoon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.game.CaseResult
import com.alecu.balancetycoon.core.game.ClientTiers
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.PendingCase
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Palette

/** Clientes: reputación, tipo de cliente y consultas pendientes de resolver. */
@Composable
fun ClientsScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val s = engine.state
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "reputation") {
            val tier = engine.tier()
            val next = engine.nextTier()
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tier.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        PixelText(stringResource(R.string.clients_now, tier.name.get(lang)), size = 17.sp, color = Palette.Gold, maxLines = 2)
                        Hint(tier.examples.get(lang))
                    }
                }
                PixelText(starsText(tier.index + 1), size = 20.sp, color = Palette.Gold)
                if (next != null) {
                    val span = (next.reputation - tier.reputation).toFloat()
                    val p = (s.reputation - tier.reputation) / span
                    GameProgressBar(p, "${s.reputation} / ${next.reputation}", Modifier.fillMaxWidth(), color = Palette.Gold)
                    Hint(stringResource(R.string.next_tier, next.name.get(lang)))
                } else {
                    Hint(stringResource(R.string.max_tier))
                }
                Hint(stringResource(R.string.fee_multiplier, com.alecu.balancetycoon.core.format.Fmt.decimal2(tier.feeMultiplier, lang)))
                AmountRow(stringResource(R.string.managed_wealth), engine.managedWealth(), lang, bold = true)
                Hint(stringResource(R.string.client_count, engine.clientCount().toInt()))
            }
        }
        item(key = "streak") {
            SectionCard {
                val bonus = minOf(s.streak, GameEngine.STREAK_CAP) * 2
                PixelText("🎯 " + stringResource(R.string.streak, s.streak, bonus), size = 16.sp)
                Hint(stringResource(R.string.cases_stats, s.casesCorrect, s.casesCorrect + s.casesWrong, s.casesAuto, s.bestStreak))
            }
        }
        vm.lastCase?.let { r ->
            item(key = "feedback") { CaseFeedbackCard(r, lang) { vm.lastCase = null } }
        }
        if (s.cases.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.cases_empty),
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    textAlign = TextAlign.Center,
                    color = Palette.Muted,
                )
            }
        }
        items(s.cases.toList(), key = { it.id }) { pc -> CaseCard(pc, engine, lang) { option -> vm.answer(pc.id, option) } }
        item(key = "tiers") {
            SectionCard(stringResource(R.string.tiers_title)) {
                for (t in ClientTiers.all) {
                    val reached = s.reputation >= t.reputation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reached) t.emoji else "🔒", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name.get(lang), color = if (reached) Palette.Cream else Palette.Muted, fontWeight = FontWeight.SemiBold)
                            Hint(stringResource(R.string.tier_line, t.reputation.toInt(), com.alecu.balancetycoon.core.format.Fmt.decimal2(t.feeMultiplier, lang)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseCard(pc: PendingCase, engine: GameEngine, lang: Lang, onAnswer: (Int) -> Unit) {
    val t = pc.template
    val dept = Departments.get(t.dept)
    val daysLeft = (GameEngine.CASE_AUTO_DAYS - (engine.state.day - pc.arrivalDay)).coerceAtLeast(0)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.client.get(lang), style = MaterialTheme.typography.titleSmall, color = Palette.Gold)
                Hint("${dept.emoji} ${dept.name.get(lang)} · " + stringResource(R.string.case_fee, moneyShort(pc.fee, lang)))
            }
        }
        Text("«" + t.question.get(lang) + "»", style = MaterialTheme.typography.bodyLarge, color = Palette.Cream)
        for (option in pc.order) {
            OutlinedButton(
                onClick = { onAnswer(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, Palette.Mint.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Cream),
            ) {
                Text(t.options[option].get(lang), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
        Hint(stringResource(R.string.case_auto, daysLeft.toInt()))
    }
}

@Composable
private fun CaseFeedbackCard(r: CaseResult, lang: Lang, onDismiss: () -> Unit) {
    val ok = r.correct
    SectionCard(containerColor = if (ok) androidx.compose.ui.graphics.Color(0xFF1F4A36) else androidx.compose.ui.graphics.Color(0xFF4A2A26)) {
        PixelText(
            (if (ok) "✅ " else "❌ ") + stringResource(if (ok) R.string.case_ok else R.string.case_wrong),
            size = 17.sp,
            color = if (ok) Palette.Mint else Palette.Red,
        )
        Text(r.template.client.get(lang), color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.case_answer, r.template.options[0].get(lang)), fontWeight = FontWeight.SemiBold, color = Palette.Cream)
        Text(r.template.explanation.get(lang), style = MaterialTheme.typography.bodyMedium, color = Palette.Cream)
        Hint(
            if (ok) stringResource(R.string.case_ok_detail, moneyShort(r.fee, lang), r.reputationChange.toInt())
            else stringResource(R.string.case_wrong_detail, (-r.reputationChange).toInt()),
        )
        PriceButton(stringResource(R.string.dismiss), null, onClick = onDismiss, modifier = Modifier.align(Alignment.End))
    }
}
