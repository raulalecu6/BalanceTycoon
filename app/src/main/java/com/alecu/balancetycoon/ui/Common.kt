package com.alecu.balancetycoon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Amounts

/** Idioma del contenido del juego, según el idioma con el que Android muestra la app. */
@Composable
fun rememberLang(): Lang = Lang.fromCode(LocalConfiguration.current.locales[0].language)

fun money(cents: Long, lang: Lang): String = Fmt.money(cents, lang)

fun accountLabel(code: String, lang: Lang): String = "$code · ${Pgc.get(code).name.get(lang)}"

@Composable
fun durationLabel(seconds: Double): String {
    val total = (seconds / 60).toInt()
    val h = total / 60
    val m = total % 60
    return if (h > 0) stringResource(R.string.hours_minutes, h, m) else stringResource(R.string.minutes, m)
}

/** Tarjeta con título opcional, usada en todas las pantallas. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** Fila «concepto ............ importe». */
@Composable
fun AmountRow(
    label: String,
    amount: Long,
    lang: Lang,
    bold: Boolean = false,
    indent: Dp = 0.dp,
    colored: Boolean = false,
) {
    val color = when {
        !colored -> MaterialTheme.colorScheme.onSurface
        amount < 0 -> Amounts.negative
        else -> Amounts.positive
    }
    Row(
        Modifier.fillMaxWidth().padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            money(amount, lang),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
