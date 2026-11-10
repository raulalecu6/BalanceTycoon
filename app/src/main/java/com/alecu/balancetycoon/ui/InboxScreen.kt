package com.alecu.balancetycoon.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.DocFeedback
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.ASSISTANT_SALARY
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.PendingDocument
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Amounts

@Composable
fun InboxScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val s = engine.state
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "streak") {
            SectionCard {
                val bonus = minOf(s.streak, GameEngine.STREAK_CAP) * 2
                Text(
                    "🎯 " + stringResource(R.string.inbox_streak, s.streak, "$bonus%"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Hint(stringResource(R.string.inbox_best, s.bestStreak, s.docsCorrect, s.docsCorrect + s.docsWrong))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.assistant_title), fontWeight = FontWeight.SemiBold)
                        Hint(stringResource(R.string.assistant_desc, money(ASSISTANT_SALARY, lang)))
                    }
                    if (s.hasAssistant) {
                        TextButton(onClick = { vm.fireAssistant() }) { Text(stringResource(R.string.fire)) }
                    } else {
                        OutlinedButton(onClick = { vm.hireAssistant() }) { Text(stringResource(R.string.hire_short)) }
                    }
                }
            }
        }
        vm.lastDocFeedback?.let { fb ->
            item(key = "feedback") { FeedbackCard(fb, lang) { vm.lastDocFeedback = null } }
        }
        if (s.documents.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.inbox_empty),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(s.documents.toList(), key = { it.id }) { doc ->
            DocumentCard(doc, engine, lang) { account -> vm.answer(doc.id, account) }
        }
    }
}

@Composable
private fun DocumentCard(doc: PendingDocument, engine: GameEngine, lang: Lang, onAnswer: (String) -> Unit) {
    val t = doc.template
    val daysLeft = (GameEngine.DOC_AUTO_DAYS - (engine.state.day - doc.arrivalDay)).coerceAtLeast(0)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title.get(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(t.issuer.get(lang), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Hint(t.description.get(lang))
        Hint(stringResource(R.string.doc_received, Fmt.dateShort(GameDate(doc.arrivalDay), lang), daysLeft.toInt()))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        AmountRow(stringResource(R.string.doc_base), doc.base, lang)
        if (t.vat > 0) {
            AmountRow(stringResource(R.string.doc_vat, Fmt.rate(t.vat, lang)), doc.vat, lang)
        } else {
            Hint(stringResource(R.string.doc_vat_exempt))
        }
        if (t.withholding > 0) {
            AmountRow(stringResource(R.string.doc_withholding, Fmt.rate(t.withholding, lang)), -doc.withholding, lang)
        }
        AmountRow(stringResource(R.string.doc_total), doc.total, lang, bold = true)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.doc_question), fontWeight = FontWeight.SemiBold)
        for (option in doc.options) {
            OutlinedButton(onClick = { onAnswer(option) }, modifier = Modifier.fillMaxWidth()) {
                Text(accountLabel(option, lang), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
    }
}

@Composable
private fun FeedbackCard(fb: DocFeedback, lang: Lang, onDismiss: () -> Unit) {
    val ok = fb.result.correct
    SectionCard(
        containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            (if (ok) "✅ " else "❌ ") + stringResource(if (ok) R.string.feedback_correct else R.string.feedback_wrong) + " · " + fb.title.get(lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) Amounts.positive else Amounts.negative,
        )
        Text(stringResource(R.string.feedback_account, accountLabel(fb.result.correctAccount, lang)), fontWeight = FontWeight.SemiBold)
        Text(fb.result.explanation.get(lang), style = MaterialTheme.typography.bodyMedium)
        Hint(stringResource(R.string.feedback_booked))
        Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.dismiss)) }
    }
}
