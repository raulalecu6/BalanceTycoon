package com.alecu.balancetycoon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.CaseResult
import com.alecu.balancetycoon.core.game.ClientTiers
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.PendingCase
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Palette

private val VipPurple = Color(0xFF6B3FA0)
private val VipCard = Color(0xFF2E2547)

/** Clientes: reputación, tipo de cliente y consultas pendientes (de opción múltiple y de asiento). */
@Composable
fun ClientsScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val s = engine.state
    val haptic = rememberHaptic()
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
                Hint(stringResource(R.string.fee_multiplier, Fmt.decimal2(tier.feeMultiplier, lang)))
                if (s.experience > 0) {
                    Hint(stringResource(R.string.experience_line, s.experience, s.experience * 10), Palette.Mint)
                }
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
        // Los VIP, primero: se van si no se les atiende.
        val ordered = s.cases.sortedWith(compareByDescending<PendingCase> { it.vip }.thenBy { it.id })
        items(ordered, key = { it.id }) { pc ->
            if (pc.isEntry) {
                EntryCaseCard(pc, engine, lang) { accounts ->
                    vm.answerEntry(pc.id, accounts)
                    haptic(if (vm.lastCase?.correct == true) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                }
            } else {
                QuizCaseCard(pc, engine, lang) { option ->
                    vm.answer(pc.id, option)
                    haptic(if (vm.lastCase?.correct == true) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                }
            }
        }
        item(key = "tiers") {
            SectionCard(stringResource(R.string.tiers_title)) {
                for (t in ClientTiers.all) {
                    val reached = s.reputation >= t.reputation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reached) t.emoji else "🔒", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name.get(lang), color = if (reached) Palette.Cream else Palette.Muted, fontWeight = FontWeight.SemiBold)
                            Hint(stringResource(R.string.tier_line, t.reputation.toInt(), Fmt.decimal2(t.feeMultiplier, lang)))
                        }
                    }
                }
            }
        }
    }
}

/** Cabecera común: cliente, departamento, honorarios y distintivos (VIP, asiento). */
@Composable
private fun CaseHeader(pc: PendingCase, engine: GameEngine, lang: Lang) {
    val info = pc.info
    val dept = Departments.get(info.dept)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(info.emoji, fontSize = 26.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(info.client.get(lang), style = MaterialTheme.typography.titleSmall, color = Palette.Gold)
            Hint("${dept.emoji} ${dept.name.get(lang)} · " + stringResource(R.string.case_fee, moneyShort(pc.fee, lang)))
        }
    }
    if (pc.vip || pc.isEntry) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pc.vip) {
                val left = (GameEngine.VIP_DAYS - (engine.state.day - pc.arrivalDay)).coerceAtLeast(1).toInt()
                Badge(stringResource(R.string.vip_badge, left), color = VipPurple)
            }
            if (pc.isEntry) Badge(stringResource(R.string.entry_badge), color = Palette.PanelLight)
        }
    }
}

@Composable
private fun AutoHint(pc: PendingCase, engine: GameEngine) {
    if (pc.vip) {
        Hint(stringResource(R.string.vip_hint), Palette.Muted)
    } else {
        val daysLeft = (GameEngine.CASE_AUTO_DAYS - (engine.state.day - pc.arrivalDay)).coerceAtLeast(0)
        Hint(stringResource(R.string.case_auto, daysLeft.toInt()))
    }
}

@Composable
private fun QuizCaseCard(pc: PendingCase, engine: GameEngine, lang: Lang, onAnswer: (Int) -> Unit) {
    val t = pc.quiz ?: return
    SectionCard(containerColor = if (pc.vip) VipCard else MaterialTheme.colorScheme.surface) {
        CaseHeader(pc, engine, lang)
        Text("«" + t.question.get(lang) + "»", style = MaterialTheme.typography.bodyLarge, color = Palette.Cream)
        for (option in pc.order) {
            OutlinedButton(
                onClick = { onAnswer(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, Palette.Mint.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Cream),
            ) {
                Text(t.options[option].get(lang), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, style = MaterialTheme.typography.bodyLarge)
            }
        }
        AutoHint(pc, engine)
    }
}

/**
 * Consulta de asiento: el asiento aparece con sus importes en el debe y el haber, y el jugador
 * toca cada línea y elige su cuenta entre las candidatas.
 */
@Composable
private fun EntryCaseCard(pc: PendingCase, engine: GameEngine, lang: Lang, onSubmit: (List<String>) -> Unit) {
    val t = pc.entry ?: return
    val lines = remember(pc.id) { engine.entryLines(pc) }
    val candidates = remember(pc.id) { engine.entryCandidates(pc) }
    var chosen by remember(pc.id) { mutableStateOf(List<String?>(lines.size) { null }) }
    var selected by remember(pc.id) { mutableIntStateOf(0) }
    val creditPrefix = stringResource(R.string.entry_credit_prefix)
    SectionCard(containerColor = if (pc.vip) VipCard else MaterialTheme.colorScheme.surface) {
        CaseHeader(pc, engine, lang)
        Text("«" + t.statementText(pc.base, lang) + "»", style = MaterialTheme.typography.bodyLarge, color = Palette.Cream)
        Hint(stringResource(R.string.entry_hint))
        // Cabecera del asiento al estilo del libro diario.
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Text(stringResource(R.string.debit), Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(stringResource(R.string.entry_account), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, textAlign = TextAlign.Center)
            Text(stringResource(R.string.credit), Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, textAlign = TextAlign.End)
        }
        lines.forEachIndexed { i, line ->
            val isSelected = i == selected
            val code = chosen.getOrNull(i)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Palette.PanelLight else Palette.PanelDark)
                    .border(2.dp, if (isSelected) Palette.Gold else Palette.Stroke, RoundedCornerShape(6.dp))
                    .clickable { selected = i }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (line.debit) money(line.amount, lang) else "",
                    Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Cream,
                )
                Text(
                    (if (line.debit) "" else creditPrefix) +
                        (code?.let { "$it · ${Pgc.get(it).name.get(lang)}" } ?: stringResource(R.string.entry_pick)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (code == null) Palette.Gold else Palette.Cream,
                    fontWeight = if (code == null) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (line.debit) TextAlign.Start else TextAlign.End,
                )
                Text(
                    if (!line.debit) money(line.amount, lang) else "",
                    Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Cream,
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // Cuentas candidatas en dos columnas.
        for (row in candidates.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (code in row) {
                    val used = code in chosen
                    GameButton(
                        onClick = {
                            val next = chosen.toMutableList()
                            next[selected] = code
                            chosen = next
                            // Salta a la siguiente línea vacía para agilizar.
                            val empty = next.indexOfFirst { it == null }
                            if (empty >= 0) selected = empty
                        },
                        modifier = Modifier.weight(1f),
                        color = if (used) Palette.Gold else Palette.Mint,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        PixelText(code, size = 15.sp, color = Palette.Stroke)
                        Text(
                            Pgc.get(code).name.get(lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Stroke,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PriceButton(
                stringResource(R.string.entry_clear), null,
                onClick = {
                    chosen = List(lines.size) { null }
                    selected = 0
                },
                color = Palette.Muted,
            )
            PriceButton(
                stringResource(R.string.entry_submit), null,
                onClick = { onSubmit(chosen.map { it ?: "" }) },
                enabled = chosen.all { it != null },
                modifier = Modifier.weight(1f),
                color = Palette.Gold,
            )
        }
        AutoHint(pc, engine)
    }
}

@Composable
private fun CaseFeedbackCard(r: CaseResult, lang: Lang, onDismiss: () -> Unit) {
    val ok = r.correct
    val creditPrefix = stringResource(R.string.entry_credit_prefix)
    SectionCard(containerColor = if (ok) Color(0xFF1F4A36) else Color(0xFF4A2A26)) {
        PixelText(
            (if (ok) "✅ " else "❌ ") + stringResource(if (ok) R.string.case_ok else R.string.case_wrong),
            size = 17.sp,
            color = if (ok) Palette.Mint else Palette.Red,
        )
        Text(
            (if (r.vip) "⭐ VIP · " else "") + r.info.client.get(lang),
            color = Palette.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        r.quiz?.let { q ->
            Text(stringResource(R.string.case_answer, q.options[0].get(lang)), fontWeight = FontWeight.SemiBold, color = Palette.Cream)
            Text(q.explanation.get(lang), style = MaterialTheme.typography.bodyMedium, color = Palette.Cream)
        }
        r.entry?.let { e ->
            Text(stringResource(R.string.entry_correct), fontWeight = FontWeight.SemiBold, color = Palette.Cream)
            r.lines.forEachIndexed { i, line ->
                val given = r.given.getOrNull(i).orEmpty()
                val right = given == line.account
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (right) "✓" else "✗",
                        Modifier.width(18.dp),
                        color = if (right) Palette.Mint else Palette.Red,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        (if (line.debit) "" else creditPrefix) + "${line.account} · ${Pgc.get(line.account).name.get(lang)}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Cream,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (if (line.debit) "D " else "H ") + money(line.amount, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (line.debit) Palette.Cream else Palette.Muted,
                    )
                }
                if (!right && given.isNotEmpty()) {
                    Hint("    " + stringResource(R.string.entry_you_put, "$given · ${Pgc.get(given).name.get(lang)}"), Palette.Red)
                }
            }
            Text(e.explanation.get(lang), style = MaterialTheme.typography.bodyMedium, color = Palette.Cream)
        }
        Hint(
            if (ok) stringResource(R.string.case_ok_detail, moneyShort(r.fee, lang), r.reputationChange.toInt())
            else stringResource(R.string.case_wrong_detail, (-r.reputationChange).toInt()),
        )
        PriceButton(stringResource(R.string.dismiss), null, onClick = onDismiss, modifier = Modifier.align(Alignment.End))
    }
}
