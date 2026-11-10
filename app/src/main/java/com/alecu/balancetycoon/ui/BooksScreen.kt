package com.alecu.balancetycoon.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.accounting.BsLine
import com.alecu.balancetycoon.core.accounting.BsSection
import com.alecu.balancetycoon.core.accounting.JournalEntry
import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.accounting.PygLine
import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Amounts
import kotlin.math.abs

private val AmountWidth = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(engine: GameEngine, lang: Lang, tick: Long) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(
        stringResource(R.string.books_journal),
        stringResource(R.string.books_ledger),
        stringResource(R.string.books_pyg),
        stringResource(R.string.books_balance),
    )
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                FilterChip(selected = sub == i, onClick = { sub = i }, label = { Text(label) })
            }
        }
        when (sub) {
            0 -> Journal(engine, lang)
            1 -> GeneralLedger(engine, lang)
            2 -> IncomeStatement(engine, lang)
            else -> BalanceSheet(engine, lang)
        }
    }
}

@Composable
private fun AmountsHeader() {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.debit), Modifier.width(AmountWidth), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        Text(stringResource(R.string.credit), Modifier.width(AmountWidth), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun Journal(engine: GameEngine, lang: Lang) {
    val entries = engine.state.ledger.recent.asReversed()
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "note") { Hint(stringResource(R.string.journal_note, entries.size)) }
        items(entries, key = { "${it.year}-${it.number}" }) { e -> EntryCard(e, lang) }
    }
}

@Composable
private fun EntryCard(e: JournalEntry, lang: Lang) {
    SectionCard {
        Text(stringResource(R.string.journal_entry, e.number, Fmt.dateShort(e.date, lang)), style = MaterialTheme.typography.labelMedium)
        Text(e.concept.get(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        AmountsHeader()
        // Convención española: primero el debe y, debajo y sangrado, el haber («a»).
        for (l in e.lines.sortedBy { if (it.debit > 0) 0 else 1 }) {
            val isCredit = l.credit > 0
            Row(Modifier.fillMaxWidth()) {
                Text(
                    (if (isCredit) "   a " else "") + l.account,
                    Modifier.width(if (isCredit) 78.dp else 50.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    Pgc.get(l.account).name.get(lang),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(if (l.debit > 0) money(l.debit, lang) else "", Modifier.width(AmountWidth), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text(if (isCredit) money(l.credit, lang) else "", Modifier.width(AmountWidth), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun GeneralLedger(engine: GameEngine, lang: Lang) {
    val led = engine.state.ledger
    val codes = led.accountsWithMovement()
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(codes, key = { it }) { code ->
            val bal = led.balance(code)
            SectionCard {
                Text(accountLabel(code, lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Hint(stringResource(R.string.debit))
                        Text(money(led.debitTotal(code), lang), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Hint(stringResource(R.string.credit))
                        Text(money(led.creditTotal(code), lang), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Hint(stringResource(R.string.balance_col))
                        val side = when {
                            bal > 0 -> if (lang == Lang.ES) " D" else " Dr"
                            bal < 0 -> if (lang == Lang.ES) " H" else " Cr"
                            else -> ""
                        }
                        Text(money(abs(bal), lang) + side, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeStatement(engine: GameEngine, lang: Lang) {
    val p = Reports.pyg(engine.state.ledger)
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "pyg") {
            SectionCard(stringResource(R.string.pyg_title, engine.state.ledger.year)) {
                AmountRow(PygLine.REVENUE.title.get(lang), p.revenue, lang, indent = 8.dp)
                AmountRow(PygLine.SUPPLIES.title.get(lang), p.supplies, lang, indent = 8.dp)
                AmountRow(PygLine.PERSONNEL.title.get(lang), p.personnel, lang, indent = 8.dp)
                AmountRow(PygLine.OTHER_OPEX.title.get(lang), p.otherOpex, lang, indent = 8.dp)
                AmountRow(PygLine.DEPRECIATION.title.get(lang), p.depreciation, lang, indent = 8.dp)
                if (p.otherResults != 0L) AmountRow(PygLine.OTHER_RESULTS.title.get(lang), p.otherResults, lang, indent = 8.dp)
                AmountRow(stringResource(R.string.pyg_operating), p.operatingResult, lang, bold = true, colored = true)
                HorizontalDivider()
                AmountRow(PygLine.FINANCIAL_INCOME.title.get(lang), p.financialIncome, lang, indent = 8.dp)
                AmountRow(PygLine.FINANCIAL_EXPENSE.title.get(lang), p.financialExpense, lang, indent = 8.dp)
                AmountRow(stringResource(R.string.pyg_financial), p.financialResult, lang, bold = true, colored = true)
                HorizontalDivider()
                AmountRow(stringResource(R.string.pyg_before_tax), p.resultBeforeTax, lang, bold = true, colored = true)
                AmountRow(PygLine.INCOME_TAX.title.get(lang), p.incomeTax, lang, indent = 8.dp)
                AmountRow(stringResource(R.string.pyg_net), p.netResult, lang, bold = true, colored = true)
            }
        }
        engine.state.lastYear?.let { y ->
            item(key = "last") {
                SectionCard(stringResource(R.string.last_year, y.year)) {
                    Hint(stringResource(R.string.last_year_detail, money(y.revenue, lang), money(y.netResult, lang), money(y.incomeTax, lang)))
                    y.regime?.let { Hint(stringResource(R.string.tax_is_regime, it.title.get(lang))) }
                }
            }
        }
    }
}

@Composable
private fun BalanceSheet(engine: GameEngine, lang: Lang) {
    val bs = Reports.balanceSheet(engine.state.ledger)
    @Composable
    fun section(sec: BsSection) {
        AmountRow(sec.title.get(lang), bs.section(sec), lang, bold = true)
        for (line in BsLine.values().filter { it.section == sec }) {
            val v = bs.lines[line] ?: 0L
            if (v != 0L || line == BsLine.CAPITAL) AmountRow(line.title.get(lang), v, lang, indent = 12.dp)
        }
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "title") {
            Text(
                stringResource(R.string.bs_title, Fmt.dateLong(engine.date(), lang)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item(key = "assets") {
            SectionCard(stringResource(R.string.bs_assets)) {
                section(BsSection.NON_CURRENT_ASSETS)
                section(BsSection.CURRENT_ASSETS)
                HorizontalDivider()
                AmountRow(stringResource(R.string.bs_total_assets), bs.totalAssets, lang, bold = true)
            }
        }
        item(key = "equity") {
            SectionCard(stringResource(R.string.bs_equity_liab)) {
                section(BsSection.EQUITY)
                section(BsSection.NON_CURRENT_LIABILITIES)
                section(BsSection.CURRENT_LIABILITIES)
                HorizontalDivider()
                AmountRow(stringResource(R.string.bs_total_eq_liab), bs.equity + bs.liabilities, lang, bold = true)
            }
        }
        item(key = "check") {
            Text(
                stringResource(if (bs.balanced) R.string.bs_balanced else R.string.bs_unbalanced),
                color = if (bs.balanced) Amounts.positive else Amounts.negative,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
