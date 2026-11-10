package com.alecu.balancetycoon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.accounting.Reports
import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.Obligation
import com.alecu.balancetycoon.core.game.ObligationKind
import com.alecu.balancetycoon.core.game.PaidBy
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.core.tax.CorporateTax
import com.alecu.balancetycoon.core.tax.Surcharge
import com.alecu.balancetycoon.ui.theme.Amounts
import kotlin.math.abs

@Composable
fun TaxScreen(vm: GameViewModel, engine: GameEngine, lang: Lang, tick: Long) {
    val s = engine.state
    val led = s.ledger
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        engine.distributionPreview()?.let { pv ->
            item(key = "distribution") {
                SectionCard(
                    stringResource(R.string.distribution_title, pv.year),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Hint(stringResource(R.string.distribution_desc))
                    AmountRow(stringResource(R.string.distribution_profit), pv.profit, lang, bold = true)
                    AmountRow(stringResource(R.string.distribution_legal), pv.legalReserve, lang, indent = 8.dp)
                    if (pv.lossOffset > 0) AmountRow(stringResource(R.string.distribution_losses), pv.lossOffset, lang, indent = 8.dp)
                    AmountRow(stringResource(R.string.distribution_max_div), pv.maxDividend, lang, indent = 8.dp)
                    OutlinedButton(onClick = { vm.distribute(0.0) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.distribution_all_reserves))
                    }
                    FilledTonalButton(onClick = { vm.distribute(0.5) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.distribution_half))
                    }
                    Button(onClick = { vm.distribute(1.0) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.distribution_max))
                    }
                }
            }
        }

        item(key = "due") {
            SectionCard(stringResource(R.string.tax_due_title)) {
                val pending = engine.pendingObligations()
                if (pending.isEmpty()) Hint(stringResource(R.string.tax_none_due))
                pending.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ObligationRow(o, engine, lang) { vm.pay(o.id) }
                }
            }
        }

        item(key = "vat") {
            SectionCard(stringResource(R.string.tax_vat_title)) {
                val output = led.natural("477")
                val input = led.natural("472")
                val prev = led.natural("4700")
                val result = output - input - prev
                AmountRow(stringResource(R.string.tax_vat_output), output, lang)
                AmountRow(stringResource(R.string.tax_vat_input), -input, lang)
                if (prev > 0) AmountRow(stringResource(R.string.tax_vat_prev), -prev, lang)
                AmountRow(
                    stringResource(if (result >= 0) R.string.tax_vat_pay else R.string.tax_vat_credit),
                    abs(result), lang, bold = true,
                )
                Hint(stringResource(R.string.tax_vat_note))
            }
        }

        item(key = "is") {
            val year = led.year
            val pyg = Reports.pyg(led)
            val sizeIncn = if (year == s.startYear) pyg.revenue else s.prevYearIncn
            val eligible = s.firstPositiveBaseYear < 0 || year <= s.firstPositiveBaseYear + 1
            val (tax, _) = CorporateTax.compute(pyg.resultBeforeTax, s.nonDeductible, s.pendingLosses, sizeIncn, year, eligible)
            SectionCard(stringResource(R.string.tax_is_title, year)) {
                Text(stringResource(R.string.tax_is_regime, tax.regime.title.get(lang)))
                Text(stringResource(R.string.tax_is_quota, money(tax.quota, lang)), fontWeight = FontWeight.SemiBold)
            }
        }

        item(key = "gestoria") {
            SectionCard {
                Text(stringResource(R.string.gestoria_title), fontWeight = FontWeight.SemiBold)
                Hint(stringResource(R.string.gestoria_on))
            }
        }

        item(key = "owner") {
            SectionCard {
                Text("💼 " + stringResource(R.string.owner_wealth, money(s.ownerWealth, lang)), fontWeight = FontWeight.SemiBold)
                s.lastYear?.let { y ->
                    Hint(
                        stringResource(R.string.last_year, y.year) + " · " +
                            stringResource(R.string.last_year_detail, money(y.revenue, lang), money(y.netResult, lang), money(y.incomeTax, lang)),
                    )
                }
            }
        }

        item(key = "history") {
            val paid = s.obligations.filter { it.isPaid }.sortedByDescending { it.paidDay }.take(8)
            if (paid.isNotEmpty()) {
                SectionCard(stringResource(R.string.history_title)) {
                    for (o in paid) {
                        val by = when (o.paidBy) {
                            PaidBy.PLAYER -> stringResource(R.string.paid_by_player)
                            PaidBy.GESTORIA -> stringResource(R.string.paid_by_gestoria)
                            else -> stringResource(R.string.paid_by_bank)
                        }
                        Column {
                            Text(o.label.get(lang), style = MaterialTheme.typography.bodyMedium)
                            Hint("${money(o.amount + o.surcharge, lang)} · ${Fmt.dateShort(GameDate(o.paidDay ?: o.dueDay), lang)} · $by")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObligationRow(o: Obligation, engine: GameEngine, lang: Lang, onPay: () -> Unit) {
    val days = o.dueDay - engine.state.day
    val relative = when {
        days > 0 -> stringResource(R.string.days_left, days.toInt())
        days == 0L -> stringResource(R.string.due_today)
        else -> stringResource(R.string.overdue, (-days).toInt())
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.label.get(lang), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(money(o.amount, lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.tax_due_date, Fmt.dateLong(GameDate(o.dueDay), lang), relative),
            style = MaterialTheme.typography.bodySmall,
            color = if (days < 0) Amounts.negative else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (o.kind != ObligationKind.DIVIDEND) {
            val late = days < 0
            val canPay = engine.cash() >= o.amount
            FilledTonalButton(onClick = onPay, enabled = canPay, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (late) stringResource(R.string.pay_late, Fmt.rate(Surcharge.extemporaneousBp(-days), lang))
                    else stringResource(R.string.pay_now),
                )
            }
        }
    }
}
