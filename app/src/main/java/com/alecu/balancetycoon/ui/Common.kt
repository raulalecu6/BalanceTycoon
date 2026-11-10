package com.alecu.balancetycoon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.accounting.Pgc
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Amounts
import com.alecu.balancetycoon.ui.theme.Palette
import com.alecu.balancetycoon.ui.theme.PixelFont

/** Idioma del contenido del juego, según el idioma con el que Android muestra la app. */
@Composable
fun rememberLang(): Lang = Lang.fromCode(LocalConfiguration.current.locales[0].language)

fun money(cents: Long, lang: Lang): String = Fmt.money(cents, lang)

/** Importe sin céntimos para botones y cifras grandes. */
fun moneyShort(cents: Long, lang: Lang): String = Fmt.moneyNoCents(cents, lang)

/** Importe por segundo a partir de céntimos por segundo. */
fun perSecond(centsPerSecond: Double, lang: Lang): String {
    val cents = centsPerSecond.toLong()
    return (if (cents >= 100_000L) moneyShort(cents, lang) else money(cents, lang)) + "/s"
}

fun accountLabel(code: String, lang: Lang): String = "$code · ${Pgc.get(code).name.get(lang)}"

@Composable
fun durationLabel(seconds: Double): String {
    val total = (seconds / 60).toInt()
    val h = total / 60
    val m = total % 60
    return if (h > 0) stringResource(R.string.hours_minutes, h, m) else stringResource(R.string.minutes, m)
}

/** Tiempo de fotograma en milisegundos: redibuja cada fotograma mientras se lee. */
@Composable
fun rememberFrameMillis(): State<Long> = produceState(0L) {
    while (true) {
        withFrameMillis { value = it }
    }
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
        border = BorderStroke(2.dp, Palette.Stroke),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
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
fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/**
 * Texto para la fuente pixelada: el símbolo del euro de Pixelify Sans se confunde con una «B»,
 * así que se dibuja con la fuente del sistema.
 */
fun pixelString(text: String): AnnotatedString = buildAnnotatedString {
    var start = 0
    while (true) {
        val i = text.indexOf('€', start)
        if (i < 0) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, i))
        withStyle(SpanStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)) { append("€") }
        start = i + 1
    }
}

/** Texto con la fuente pixelada. */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Palette.Cream,
    align: TextAlign? = null,
    maxLines: Int = 1,
) {
    Text(
        pixelString(text),
        modifier = modifier,
        fontFamily = PixelFont,
        fontSize = size,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = size * 1.15f,
    )
}

/**
 * Botón de juego: bloque menta con borde oscuro y una sombra inferior, al estilo de los
 * tycoons en pixel art. Gris cuando no se puede pulsar.
 */
@Composable
fun GameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Mint,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = if (enabled) color else Palette.Disabled
    val shadow = if (enabled) darken(color) else Color(0xFF39484B)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(shadow)
            .border(2.dp, Palette.Stroke, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        propagateMinConstraints = true,
    ) {
        Column(
            Modifier
                .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 5.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(fill)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** Botón con una línea de título y otra de precio. */
@Composable
fun PriceButton(
    title: String,
    price: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Mint,
) {
    GameButton(onClick = onClick, modifier = modifier, enabled = enabled, color = color) {
        PixelText(title, size = 14.sp, color = Palette.Stroke, align = TextAlign.Center)
        if (price != null) PixelText(price, size = 12.sp, color = Palette.Stroke.copy(alpha = 0.8f), align = TextAlign.Center)
    }
}

fun darken(c: Color, f: Float = 0.62f): Color = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

/** Barra de progreso con borde y texto encima, como en los tycoons. */
@Composable
fun GameProgressBar(progress: Float, text: String?, modifier: Modifier = Modifier, color: Color = Palette.Mint, height: Dp = 20.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(5.dp))
            .background(Palette.Stroke)
            .border(2.dp, Palette.Stroke, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
        if (text != null) {
            PixelText(text, size = 12.sp, color = if (progress >= 0.5f) Palette.Stroke else Palette.Cream, align = TextAlign.Center)
        }
    }
}

/** Insignia pequeña (contador de pestañas, nivel...). */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Palette.Red) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(1.5.dp, Palette.Stroke, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text, size = 11.sp, color = Color.White)
    }
}

/** Estrellas de reputación: llenas hasta el tipo de cliente actual. */
fun starsText(filled: Int, total: Int = 6): String = "★".repeat(filled) + "☆".repeat((total - filled).coerceAtLeast(0))
