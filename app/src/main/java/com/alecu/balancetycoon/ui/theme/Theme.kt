package com.alecu.balancetycoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta provisional: tinta de libro mayor (gris pizarra) y oro de moneda, sobre papel.
val Ink = Color(0xFF1F2937)
val Gold = Color(0xFFD9A441)
val Paper = Color(0xFFF6F3EC)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Ink,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFF7E7C1),
    onSecondaryContainer = Color(0xFF4A3510),
    tertiary = Color(0xFF8A5A0B),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFCBC3B3),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9C46A),
    onPrimary = Ink,
    primaryContainer = Color(0xFF374151),
    onPrimaryContainer = Color(0xFFF3F4F6),
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3510),
    onSecondaryContainer = Color(0xFFF7E7C1),
    tertiary = Color(0xFFE9C46A),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1B1F24),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFB8BEC7),
    outline = Color(0xFF4B5563),
    error = Color(0xFFEF9A9A),
)

/** Colores para cifras positivas y negativas, adaptados al tema claro u oscuro. */
object Amounts {
    val positive: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
    val negative: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFC62828)
    val headerBackground: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0B0E12) else Ink
}

@Composable
fun BalanceTycoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
