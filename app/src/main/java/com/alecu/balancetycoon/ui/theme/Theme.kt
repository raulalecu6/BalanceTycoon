package com.alecu.balancetycoon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alecu.balancetycoon.R

/** Paleta de juego: paneles verde petróleo, botones menta y monedas doradas. */
object Palette {
    val PanelDark = Color(0xFF12292E)
    val Panel = Color(0xFF1B3D44)
    val PanelLight = Color(0xFF265059)
    val Stroke = Color(0xFF0A1A1D)
    val Mint = Color(0xFF8EE3B5)
    val MintDark = Color(0xFF3F9C74)
    val Gold = Color(0xFFF4C542)
    val GoldDark = Color(0xFFB8860B)
    val Cream = Color(0xFFF3F1E7)
    val Muted = Color(0xFFA9C4C0)
    val Red = Color(0xFFE5735F)
    val Disabled = Color(0xFF51666A)

    val Sky = Color(0xFF8FD3F4)
    val SkyLight = Color(0xFFD4F1FD)
    val Facade = Color(0xFFDAD5CB)
    val FacadeDark = Color(0xFF9C968B)
    val Slab = Color(0xFF5B5F66)
    val Outline = Color(0xFF2B2B33)
    val WoodFloor = Color(0xFF7A4E2D)
    val Desk = Color(0xFF9B6B43)
    val DeskDark = Color(0xFF6E4A2C)

    /** Color de pared de cada departamento (de abajo arriba). */
    fun deptWall(id: String): Color = when (id) {
        "fiscal" -> Color(0xFFF2C9A0)
        "laboral" -> Color(0xFFBFE3B4)
        "contable" -> Color(0xFFB9D7F0)
        "mercantil" -> Color(0xFFD8C4EC)
        "consultoria" -> Color(0xFFF5E3A3)
        else -> Color(0xFF9FD8D1)
    }

    /** Color de la camisa de los trabajadores de cada departamento. */
    fun deptShirt(id: String): Color = when (id) {
        "fiscal" -> Color(0xFFD9534F)
        "laboral" -> Color(0xFF3E8E5E)
        "contable" -> Color(0xFF3A6EA5)
        "mercantil" -> Color(0xFF6B4C9A)
        "consultoria" -> Color(0xFFCC8A1E)
        else -> Color(0xFF1F7A72)
    }
}

/** Colores para cifras positivas y negativas. */
object Amounts {
    val positive: Color @Composable get() = Palette.Mint
    val negative: Color @Composable get() = Palette.Red
    val headerBackground: Color @Composable get() = Palette.PanelDark
}

/** Fuente pixelada (Pixelify Sans, licencia SIL OFL 1.1) para títulos y cifras. */
val PixelFont = FontFamily(Font(R.font.pixelify_sans))

// Compatibilidad con las pantallas de libros.
val Gold = Palette.Gold

private val GameColors = darkColorScheme(
    primary = Palette.Mint,
    onPrimary = Palette.Stroke,
    primaryContainer = Palette.PanelLight,
    onPrimaryContainer = Palette.Cream,
    secondary = Palette.Gold,
    onSecondary = Palette.Stroke,
    secondaryContainer = Color(0xFF4A3D12),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Palette.Gold,
    background = Palette.PanelDark,
    onBackground = Palette.Cream,
    surface = Palette.Panel,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.PanelLight,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelLight,
    surfaceContainerHighest = Palette.PanelLight,
    surfaceContainerLow = Palette.Panel,
    outline = Color(0xFF3D6A72),
    outlineVariant = Color(0xFF2E555C),
    error = Palette.Red,
)

private val GameTypography: Typography = Typography().let { t ->
    fun pixel(style: TextStyle) = style.copy(fontFamily = PixelFont, fontWeight = FontWeight.Normal)
    t.copy(
        headlineSmall = pixel(t.headlineSmall),
        titleLarge = pixel(t.titleLarge),
        titleMedium = pixel(t.titleMedium).copy(fontSize = 17.sp),
        titleSmall = pixel(t.titleSmall),
        labelLarge = pixel(t.labelLarge).copy(fontSize = 15.sp),
    )
}

@Composable
fun BalanceTycoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColors, typography = GameTypography, content = content)
}
