package com.alecu.balancetycoon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alecu.balancetycoon.core.game.Departments
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Palette
import com.alecu.balancetycoon.ui.theme.PixelFont
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.lerp
import com.alecu.balancetycoon.core.game.IncidentKind
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Unidades de alto de cada planta: todo el dibujo se hace en «píxeles» de juego de tamaño [u]. */
private const val FLOOR_UNITS = 24f
private const val ROOF_UNITS = 15f
private const val GROUND_UNITS = 11f
private const val SHAFT_UNITS = 10f
private const val SLOT_UNITS = 16f
private const val LEFT_UNITS = 14f

/** Geometría de la torre, calculada a partir del ancho disponible y del local actual. */
private class TowerGeometry(widthPx: Float, minHeightPx: Float, density: Density, val floors: Int, desks: Int) {
    val mainUnits = max(SLOT_UNITS * desks + LEFT_UNITS + 2f, 48f)
    val u: Float = min(with(density) { 4.2.dp.toPx() }, widthPx * 0.9f / (mainUnits + SHAFT_UNITS + 2f))
    val buildingW = (mainUnits + SHAFT_UNITS + 2f) * u
    val left = (widthPx - buildingW) / 2f
    val mainLeft = left + u
    val mainW = mainUnits * u
    val shaftLeft = mainLeft + mainW
    val floorH = FLOOR_UNITS * u
    /** Si el edificio es más bajo que la pantalla, se deja cielo arriba para que la calle quede abajo. */
    private val naturalH = (6f + ROOF_UNITS + FLOOR_UNITS * (floors + 1) + GROUND_UNITS) * u
    val roofTop = 6f * u + max(0f, minHeightPx - naturalH)
    val firstFloorTop = roofTop + ROOF_UNITS * u
    /** Planta i (0 = la de abajo, el departamento fiscal). */
    fun floorTop(i: Int): Float = firstFloorTop + (floors - 1 - i) * floorH
    val lobbyTop = firstFloorTop + floors * floorH
    val groundTop = lobbyTop + floorH
    val totalH = groundTop + GROUND_UNITS * u
}

/** Moneda flotante «+12 €» que sale de una planta al terminar expedientes. */
private class Floater(val floor: Int, val text: String, val start: Long, val x: Float)

/**
 * Edificio en corte de la gestoría: tejado con el rótulo, una planta por departamento
 * y la recepción en la planta baja. Al tocar una planta sin trabajadores se tramita a mano;
 * al tocar una planta cerrada se abre; al tocar la recepción se van a ver las consultas.
 */
@Composable
fun Tower(
    engine: GameEngine,
    lang: Lang,
    tick: Long,
    modifier: Modifier = Modifier,
    nightOverride: Float? = null,
    onManualCoin: () -> Unit = {},
    onFloorTap: (String) -> Unit,
    onLobbyTap: () -> Unit,
) {
    val frame by rememberFrameMillis()
    val night = rememberNightFactor(nightOverride)
    // Momento en que cada cliente apareció, para animar su entrada por la puerta.
    val arrivals = remember { HashMap<Int, Long>() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val office = engine.office()
    val floaters = remember { mutableStateListOf<Floater>() }
    val lastCycles = remember { HashMap<String, Long>() }
    val lastFloatAt = remember { HashMap<String, Long>() }

    // Monedas flotantes: como mucho una cada 450 ms por planta para no saturar.
    LaunchedEffect(tick) {
        val now = frame
        for ((i, def) in Departments.all.withIndex()) {
            val d = engine.dept(def.id)
            val prev = lastCycles[def.id]
            lastCycles[def.id] = d.completedCycles
            if (prev == null || d.completedCycles <= prev || i >= office.floors) continue
            if (now - (lastFloatAt[def.id] ?: 0L) < 450L) continue
            lastFloatAt[def.id] = now
            val amount = engine.revenuePerCycle(def.id) * (d.completedCycles - prev)
            floaters.add(Floater(i, "+" + moneyShort(amount, lang), now, ((now / 7) % 100) / 100f))
            if (d.workers == 0) onManualCoin()
        }
        floaters.removeAll { now - it.start > 1300L }
        val ids = engine.state.cases.map { it.id }.toSet()
        val firstTime = arrivals.isEmpty()
        for (id in ids) if (id !in arrivals) arrivals[id] = if (firstTime) now - 60_000L else now
        arrivals.keys.retainAll(ids)
    }

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val minHeightPx = with(density) { maxHeight.toPx() }
        val geo = TowerGeometry(widthPx, minHeightPx, density, office.floors, office.desksPerFloor)
        val heightDp = with(density) { geo.totalH.toDp() }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), reverseScrolling = true)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(office.index, geo.totalH) {
                    detectTapGestures { p ->
                        if (p.x < geo.left || p.x > geo.left + geo.buildingW) return@detectTapGestures
                        if (p.y >= geo.lobbyTop && p.y < geo.groundTop) {
                            onLobbyTap()
                            return@detectTapGestures
                        }
                        for (i in 0 until geo.floors) {
                            val top = geo.floorTop(i)
                            if (p.y >= top && p.y < top + geo.floorH) {
                                onFloorTap(Departments.all[i].id)
                                return@detectTapGestures
                            }
                        }
                    }
                },
        ) {
            drawBackground(geo, office.index, frame, night)
            drawBuilding(geo, night)
            drawRoof(geo, measurer, density, engine.state.companyName, office.name.get(lang) + " · " + office.city, office.index, frame)
            for (i in 0 until geo.floors) {
                val def = Departments.all[i]
                drawFloor(geo, i, def.id, engine, lang, measurer, density, frame)
            }
            drawShaft(geo, frame, night)
            drawLobby(geo, engine, arrivals, lang, measurer, density, frame, office.index)
            for (f in floaters) {
                val t = ((frame - f.start) / 1300f).coerceIn(0f, 1f)
                val top = geo.floorTop(f.floor)
                val style = TextStyle(fontFamily = PixelFont, fontSize = with(density) { (geo.u * 3.6f).toSp() }, color = Palette.Gold)
                val layout = measurer.measure(pixelString(f.text), style)
                val x = geo.mainLeft + LEFT_UNITS * geo.u + f.x * (geo.mainW - LEFT_UNITS * geo.u - layout.size.width)
                val y = top + geo.floorH * (0.5f - 0.35f * t)
                val o = geo.u * 0.35f
                for ((dx, dy) in listOf(-o to 0f, o to 0f, 0f to -o, 0f to o, o to o)) {
                    drawText(layout, topLeft = Offset(x + dx, y + dy), color = Palette.Stroke, alpha = 1f - t)
                }
                drawText(layout, topLeft = Offset(x, y), alpha = 1f - t)
            }
        }
        }
    }
}

// ───────────────────────────── Ayudas de dibujo ─────────────────────────────

/** Rectángulo en unidades de juego desde un origen. */
private fun DrawScope.px(ox: Float, oy: Float, u: Float, x: Float, y: Float, w: Float, h: Float, c: Color, alpha: Float = 1f) {
    drawRect(c, Offset(ox + x * u, oy + y * u), Size(w * u, h * u), alpha = alpha)
}

private fun shade(c: Color, f: Float): Color = Color((c.red * f).coerceIn(0f, 1f), (c.green * f).coerceIn(0f, 1f), (c.blue * f).coerceIn(0f, 1f), c.alpha)

private fun DrawScope.label(
    measurer: TextMeasurer, density: Density, text: String, x: Float, y: Float, sizeUnits: Float, u: Float,
    color: Color = Palette.Cream, centered: Boolean = false, shadow: Boolean = true, maxWidth: Float? = null, pill: Boolean = false,
    pillColor: Color = Palette.Stroke.copy(alpha = 0.72f),
): Float {
    val style = TextStyle(fontFamily = PixelFont, fontSize = with(density) { (u * sizeUnits).toSp() }, color = color)
    val layout = if (maxWidth != null) {
        measurer.measure(pixelString(text), style, maxLines = 1, constraints = androidx.compose.ui.unit.Constraints(maxWidth = max(1, maxWidth.toInt())), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    } else {
        measurer.measure(pixelString(text), style)
    }
    val left = if (centered) x - layout.size.width / 2f else x
    if (pill) {
        drawRoundRect(
            pillColor,
            topLeft = Offset(left - u * 0.8f, y - u * 0.2f),
            size = Size(layout.size.width + u * 1.6f, layout.size.height + u * 0.4f),
            cornerRadius = CornerRadius(u, u),
        )
    }
    if (shadow) drawText(layout, topLeft = Offset(left + u * 0.4f, y + u * 0.4f), color = Palette.Stroke)
    drawText(layout, topLeft = Offset(left, y))
    return left + layout.size.width
}

/** 0 de día, 1 de noche, con transición al amanecer (6:30-8:00) y al anochecer (19:30-21:30). */
fun nightFactor(hour: Float): Float = when {
    hour < 6.5f -> 1f
    hour < 8f -> 1f - (hour - 6.5f) / 1.5f
    hour < 19.5f -> 0f
    hour < 21.5f -> (hour - 19.5f) / 2f
    else -> 1f
}

@Composable
private fun rememberNightFactor(override: Float?): Float {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }
    return override ?: nightFactor(now.hour + now.minute / 60f)
}

/** Número pseudoaleatorio estable en [0, 1) a partir de una semilla, para decorar sin parpadeos. */
private fun hash01(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
}

// ───────────────────────────── Fondo y edificio ─────────────────────────────

private fun DrawScope.drawBackground(g: TowerGeometry, officeIndex: Int, frame: Long, night: Float) {
    val skyTop = lerp(Palette.Sky, Color(0xFF101A40), night)
    val skyBottom = lerp(Palette.SkyLight, Color(0xFF35407A), night)
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom), startY = 0f, endY = g.groundTop))
    val u = g.u
    val w = size.width
    // Estrellas y luna por la noche.
    if (night > 0.2f) {
        for (k in 0 until 45) {
            val sx = hash01(k) * w
            val sy = hash01(k + 999) * g.groundTop * 0.85f
            val tw = (sin(frame / 600.0 + k) * 0.5 + 0.5).toFloat()
            drawRect(Color.White, Offset(sx, sy), Size(0.6f * u, 0.6f * u), alpha = night * (0.35f + 0.65f * tw))
        }
        drawCircle(Color(0xFFF6F1D5), radius = 5f * u, center = Offset(w - 12f * u, 14f * u), alpha = night)
        drawCircle(skyTop, radius = 4.6f * u, center = Offset(w - 9.5f * u, 12.5f * u), alpha = night)
    }
    // Nubes que se desplazan despacio.
    val cloud = lerp(Color.White, Color(0xFF8C93B8), night)
    for (k in 0 until 4) {
        val speed = 0.004f + k * 0.0015f
        val cx = ((frame * speed * u / 4f + k * w / 3.3f) % (w + 40 * u)) - 20 * u
        val cy = (8 + k * 37) * u % (g.groundTop * 0.7f) + 4 * u
        px(cx, cy, u, 0f, 2f, 16f, 4f, cloud, 0.85f - 0.45f * night)
        px(cx, cy, u, 3f, 0f, 9f, 3f, cloud, 0.85f - 0.45f * night)
    }
    // Silueta de la ciudad detrás del edificio, según dónde esté el local.
    val baseY = g.groundTop
    val far = lerp(Color(0xFF9CC7DA), Color(0xFF242C55), night)
    val near = lerp(Color(0xFF7FB0C6), Color(0xFF1C2347), night)
    val lit = Color(0xFFFFD86B)
    when {
        officeIndex <= 1 -> {
            // Tomelloso: casas bajas y molinos de La Mancha.
            var x = 0f
            var k = 0
            while (x < w) {
                val h = (6 + (k * 5) % 7) * u
                drawRect(near, Offset(x, baseY - h), Size(9 * u, h))
                drawRect(shade(near, 0.85f), Offset(x, baseY - h - 1.5f * u), Size(9 * u, 1.5f * u))
                if (night > 0f && hash01(k * 7) < 0.6f) drawRect(lit, Offset(x + 3.5f * u, baseY - h + 2f * u), Size(2 * u, 2 * u), alpha = night)
                x += 10 * u
                k++
            }
            for (mx in listOf(w * 0.06f, w * 0.88f)) drawWindmill(mx, baseY - 6 * u, u, frame)
        }
        officeIndex == 2 -> {
            var x = 0f
            var k = 0
            while (x < w) {
                val h = (14 + (k * 7) % 12) * u
                drawRect(far, Offset(x, baseY - h), Size(11 * u, h))
                for (wy in 0 until (h / (4 * u)).toInt()) {
                    val on = hash01(k * 31 + wy) < 0.55f
                    val c = if (on) lerp(Color.White.copy(alpha = 0.35f), lit, night) else Color.White.copy(alpha = 0.35f * (1f - night))
                    drawRect(c, Offset(x + 2 * u, baseY - h + (2 + wy * 4) * u), Size(7 * u, 1.5f * u))
                }
                x += 12 * u
                k++
            }
        }
        else -> {
            var x = 0f
            var k = 0
            while (x < w) {
                val h = (30 + (k * 13) % 35) * u
                drawRect(far, Offset(x, baseY - h), Size(10 * u, h))
                drawRect(shade(far, 1.08f), Offset(x + 7 * u, baseY - h), Size(3 * u, h))
                if (night > 0f) {
                    for (wy in 0 until (h / (3 * u)).toInt()) for (wx in 0 until 2) {
                        if (hash01(k * 97 + wy * 5 + wx) < 0.45f) drawRect(lit, Offset(x + (1.2f + wx * 3f) * u, baseY - h + (1.5f + wy * 3f) * u), Size(1.6f * u, 1.2f * u), alpha = night)
                    }
                }
                if (k % 3 == 0) {
                    val blink = (frame / 700 + k) % 2 == 0L
                    drawRect(if (blink) Palette.Red else shade(Palette.Red, 0.5f), Offset(x + 4.5f * u, baseY - h - 3 * u), Size(u, 3 * u))
                }
                x += 11 * u
                k++
            }
        }
    }
    // Acera y calzada.
    drawRect(lerp(Color(0xFFB9B4AA), Color(0xFF5B5A63), night), Offset(0f, g.groundTop), Size(w, 3 * u))
    drawRect(Color(0xFF8E8A82), Offset(0f, g.groundTop + 3 * u), Size(w, 0.6f * u))
    drawRect(lerp(Color(0xFF4A4E55), Color(0xFF26282F), night), Offset(0f, g.groundTop + 3.6f * u), Size(w, size.height - g.groundTop))
    var dx = 0f
    while (dx < w) {
        drawRect(Color(0xFFE8E2C8), Offset(dx, g.groundTop + 7.2f * u), Size(6 * u, 0.7f * u), alpha = 1f - 0.4f * night)
        dx += 12 * u
    }
    // Farolas a los lados del edificio, encendidas por la noche.
    for (lx in listOf(g.left - 8f * u, g.left + g.buildingW + 7f * u)) {
        if (lx < 0 || lx > w) continue
        if (night > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66FFE9A0), Color(0x00FFE9A0)), center = Offset(lx, g.groundTop - 14f * u), radius = 9f * u),
                radius = 9f * u, center = Offset(lx, g.groundTop - 14f * u), alpha = night,
            )
        }
        drawRect(Palette.Outline, Offset(lx - 0.4f * u, g.groundTop - 14f * u), Size(0.8f * u, 14f * u))
        drawRect(Palette.Outline, Offset(lx - 1.6f * u, g.groundTop - 15.2f * u), Size(3.2f * u, 1.4f * u))
        drawRect(lerp(Color(0xFFBFC4C9), Color(0xFFFFE9A0), night), Offset(lx - 1.2f * u, g.groundTop - 13.9f * u), Size(2.4f * u, 0.8f * u))
    }
    // Tráfico: más coches (y taxis) cuanto más grande es la ciudad.
    val cars = 2 + officeIndex / 2
    val colors = listOf(Color(0xFFD9534F), Color(0xFF3A6EA5), Color(0xFFF4C542), Color(0xFF3E8E5E), Color(0xFFEDEDED), Color(0xFF6B4C9A))
    for (k in 0 until cars) {
        val right = k % 2 == 0
        val speed = 9f + hash01(k + 5) * 8f // unidades por segundo
        val span = w + 30f * u
        val p = ((frame / 1000f * speed * u + hash01(k + 50) * span) % span) - 15f * u
        val x = if (right) p else w - p
        val y = g.groundTop + if (right) 3.9f * u else 6.6f * u
        val taxi = officeIndex >= 3 && k % 3 == 1
        drawCar(x, y, u, if (taxi) Color(0xFFF2F2F2) else colors[k % colors.size], right, night, taxi)
    }
}

private fun DrawScope.drawCar(x: Float, y: Float, u: Float, color: Color, right: Boolean, night: Float, taxi: Boolean) {
    val glass = lerp(Color(0xFFA9DCF0), Color(0xFF39466B), night)
    val front = if (right) 10f else 0f
    if (night > 0f) {
        val beamX = if (right) x + 10f * u else x - 7f * u
        drawRect(Color(0xFFFFF2B0), Offset(beamX, y + 1.4f * u), Size(7f * u, 1.4f * u), alpha = 0.22f * night)
    }
    px(x, y, u, 0f, 1.2f, 10f, 1.8f, color)
    px(x, y, u, 2f, 0f, 6f, 1.4f, shade(color, 0.9f))
    px(x, y, u, 2.5f, 0.25f, 2.2f, 1f, glass)
    px(x, y, u, 5.2f, 0.25f, 2.2f, 1f, glass)
    if (taxi) {
        px(x, y, u, 2f, 1.6f, 6f, 0.5f, Color(0xFFD9534F))
        px(x, y, u, 4.3f, -0.7f, 1.4f, 0.7f, lerp(Color(0xFF5DBB63), Color(0xFF9CFF9F), night))
    }
    drawCircle(Palette.Outline, radius = 0.9f * u, center = Offset(x + 2.2f * u, y + 3f * u))
    drawCircle(Palette.Outline, radius = 0.9f * u, center = Offset(x + 7.8f * u, y + 3f * u))
    px(x, y, u, front - (if (right) 0.6f else 0f), 1.5f, 0.6f, 0.6f, Color(0xFFFFF2B0))
    px(x, y, u, (if (right) 0f else 9.4f), 1.5f, 0.6f, 0.6f, Color(0xFFE5735F))
}

private fun DrawScope.drawWindmill(x: Float, groundY: Float, u: Float, frame: Long) {
    val body = Color(0xFFEDEBE4)
    drawRect(body, Offset(x - 3 * u, groundY - 12 * u), Size(6 * u, 12 * u))
    drawRect(Color(0xFF6D4C3D), Offset(x - 3.5f * u, groundY - 14 * u), Size(7 * u, 2.5f * u))
    drawRect(Color(0xFF3A3A3A), Offset(x - 1 * u, groundY - 4 * u), Size(2 * u, 4 * u))
    val hub = Offset(x, groundY - 13 * u)
    val a = (frame % 6000L) / 6000f * 2f * PI.toFloat()
    for (k in 0 until 4) {
        val ang = a + k * PI.toFloat() / 2f
        val end = Offset(hub.x + kotlin.math.cos(ang) * 9 * u, hub.y + sin(ang) * 9 * u)
        drawLine(Color(0xFF6D4C3D), hub, end, strokeWidth = 1.4f * u)
    }
    drawCircle(Color(0xFF3A3A3A), radius = 1.2f * u, center = hub)
}

private fun DrawScope.drawBuilding(g: TowerGeometry, night: Float) {
    val u = g.u
    val top = g.firstFloorTop
    val bottom = g.groundTop
    // Muros exteriores con contorno.
    drawRect(Palette.Outline, Offset(g.left - 0.6f * u, top - 0.6f * u), Size(g.buildingW + 1.2f * u, bottom - top + 0.6f * u))
    drawRect(lerp(Palette.Facade, Color(0xFF6E6F86), night * 0.7f), Offset(g.left, top), Size(g.buildingW, bottom - top))
}

private fun DrawScope.drawRoof(g: TowerGeometry, m: TextMeasurer, d: Density, company: String, officeLabel: String, officeIndex: Int, frame: Long) {
    val u = g.u
    val ox = g.left
    val top = g.roofTop
    val w = g.buildingW / u
    // Losa del tejado.
    px(ox, top, u, -1.5f, ROOF_UNITS - 3.5f, w + 3f, 3.5f, Palette.Outline)
    px(ox, top, u, -1f, ROOF_UNITS - 3f, w + 2f, 2.4f, Palette.Slab)
    // Rótulo con el nombre de la gestoría.
    val signW = min(w - 6f, 64f)
    val sx = (w - signW) / 2f
    px(ox, top, u, sx + 3f, ROOF_UNITS - 6f, 1f, 3f, Palette.Outline)
    px(ox, top, u, sx + signW - 4f, ROOF_UNITS - 6f, 1f, 3f, Palette.Outline)
    px(ox, top, u, sx - 0.6f, -0.6f, signW + 1.2f, 10.2f, Palette.Outline)
    px(ox, top, u, sx, 0f, signW, 9f, Color(0xFF1E2A33))
    // Bombillas del rótulo que se encienden en ola.
    for (k in 0 until (signW / 3).toInt()) {
        val on = ((frame / 160 + k) % 6) < 3
        px(ox, top, u, sx + 1.5f + k * 3f, 0.6f, 1f, 1f, if (on) Palette.Gold else Color(0xFF5A4A1A))
    }
    label(m, d, company.uppercase(), ox + (sx + signW / 2f) * u, top + 2.2f * u, 3.6f, u, Palette.Gold, centered = true, maxWidth = (signW - 3f) * u)
    label(m, d, officeLabel, ox + (sx + signW / 2f) * u, top + 6.2f * u, 2.3f, u, Palette.Muted, centered = true, shadow = false, maxWidth = (signW - 3f) * u)
    // Antena y, en las sedes grandes, una antena parabólica.
    px(ox, top, u, w - 5f, ROOF_UNITS - 12f, 0.8f, 8.5f, Palette.Outline)
    val blink = (frame / 500) % 2 == 0L
    px(ox, top, u, w - 5.4f, ROOF_UNITS - 13f, 1.6f, 1.6f, if (blink) Palette.Red else shade(Palette.Red, 0.5f))
    if (officeIndex >= 3) {
        px(ox, top, u, 3f, ROOF_UNITS - 7f, 5f, 3.5f, Color(0xFFCFD6DB))
        px(ox, top, u, 5f, ROOF_UNITS - 4f, 1f, 1f, Palette.Outline)
    }
}

private fun DrawScope.drawShaft(g: TowerGeometry, frame: Long, night: Float) {
    val u = g.u
    val x = g.shaftLeft
    val top = g.firstFloorTop
    // Columna de la escalera y el ascensor, con ventanas.
    drawRect(lerp(shade(Palette.Facade, 0.92f), Color(0xFF5E5F78), night * 0.7f), Offset(x, top), Size(SHAFT_UNITS * u, g.lobbyTop - top))
    drawRect(Palette.Outline, Offset(x - 0.4f * u, top), Size(0.8f * u, g.groundTop - top))
    for (i in 0 until g.floors) {
        val fy = g.floorTop(i)
        px(x, fy, u, 2f, 4f, 6f, 12f, Palette.Outline)
        px(x, fy, u, 2.5f, 4.5f, 5f, 11f, lerp(Color(0xFF9ED4EE), Color(0xFFFFD86B), night))
        px(x, fy, u, 3f, 5f, 1.2f, 6f, Color.White, 0.6f)
        px(x, fy, u, 0f, FLOOR_UNITS - 0.8f, SHAFT_UNITS, 0.8f, Palette.Slab)
    }
    // Ascensor que sube y baja.
    if (g.floors > 0) {
        val range = g.lobbyTop - top
        val t = (sin(frame / 2600.0) * 0.5 + 0.5).toFloat()
        val ey = top + t * (range - 10 * u) + 4.5f * u
        px(x, ey, u, 2.6f, 0f, 4.8f, 6f, Palette.Gold)
        px(x, ey, u, 3.2f, 0.6f, 3.6f, 2f, Color(0xFF6B5A21))
    }
}

// ───────────────────────────── Plantas ─────────────────────────────

private val SKINS = listOf(Color(0xFFF1C27D), Color(0xFFE0AC69), Color(0xFFC68642), Color(0xFF8D5524), Color(0xFFFFDBAC))
private val HAIRS = listOf(Color(0xFF2C1B10), Color(0xFF6B3E26), Color(0xFFB5651D), Color(0xFF1C1C1C), Color(0xFFD9B26F), Color(0xFF8A8A8A))

private fun DrawScope.drawFloor(
    g: TowerGeometry, index: Int, id: String, engine: GameEngine, lang: Lang, m: TextMeasurer, density: Density, frame: Long,
) {
    val u = g.u
    val ox = g.mainLeft
    val oy = g.floorTop(index)
    val w = g.mainW / u
    val def = Departments.get(id)
    val d = engine.dept(id)
    val open = d.level > 0
    val wall = if (open) Palette.deptWall(id) else Color(0xFF8C8F93)

    // Pared, zócalo, techo y suelo de madera.
    px(ox, oy, u, 0f, 0f, w, FLOOR_UNITS, wall)
    px(ox, oy, u, 0f, 0f, w, 1f, shade(wall, 0.7f))
    px(ox, oy, u, 0f, 15f, w, 6f, shade(wall, 0.88f))
    px(ox, oy, u, 0f, 15f, w, 0.5f, shade(wall, 0.75f))
    px(ox, oy, u, 0f, 21f, w, 3f, if (open) Palette.WoodFloor else Color(0xFF5E6166))
    px(ox, oy, u, 0f, 21f, w, 0.5f, shade(Palette.WoodFloor, 1.3f))
    px(ox, oy, u, 0f, FLOOR_UNITS - 0.8f, w, 0.8f, Palette.Slab)

    if (!open) {
        // Planta vacía: cajas de la mudanza y el precio de apertura.
        px(ox, oy, u, w * 0.2f, 15f, 6f, 6f, Color(0xFFB08A5A))
        px(ox, oy, u, w * 0.2f + 6.5f, 17f, 4f, 4f, Color(0xFFC29C6A))
        px(ox, oy, u, w * 0.7f, 16f, 5f, 5f, Color(0xFFB08A5A))
        val cost = moneyShort(engine.upgradeCashNeeded(id, 1), lang)
        label(m, density, "🔒 ${def.name.get(lang)}", ox + w * u / 2f, oy + 4f * u, 3.6f, u, centered = true)
        val affordable = engine.cash() >= engine.upgradeCashNeeded(id, 1)
        label(m, density, (if (lang == Lang.ES) "Abrir · " else "Open · ") + cost, ox + w * u / 2f, oy + 9.5f * u, 3.2f, u,
            if (affordable) Palette.Mint else Palette.Cream, centered = true)
        return
    }

    val working = d.workers > 0 || d.running
    // Zona izquierda: archivador, planta y cuadro.
    px(ox, oy, u, 2f, 11f, 6f, 10f, Color(0xFF7D8A95))
    for (k in 0 until 3) {
        px(ox, oy, u, 2.5f, 11.8f + k * 3f, 5f, 2.4f, Color(0xFF93A1AC))
        px(ox, oy, u, 4.5f, 12.8f + k * 3f, 1f, 0.5f, Palette.Outline)
    }
    px(ox, oy, u, 9.5f, 17f, 3f, 4f, Color(0xFFA0522D))
    px(ox, oy, u, 9f, 13f, 4f, 4f, Color(0xFF3E8E41))
    px(ox, oy, u, 10f, 11.5f, 2f, 2f, Color(0xFF5DBB63))
    // Reloj de pared (con las agujas al ritmo del día contable) y, en las oficinas grandes, un cuadro.
    val clock = Offset(ox + 11f * u, oy + 8.2f * u)
    drawCircle(Palette.Outline, radius = 1.9f * u, center = clock)
    drawCircle(Color(0xFFF3F1E7), radius = 1.5f * u, center = clock)
    val dayAngle = (engine.dayProgress() * 2 * PI).toFloat()
    drawLine(Palette.Outline, clock, Offset(clock.x + kotlin.math.sin(dayAngle) * 1.2f * u, clock.y - kotlin.math.cos(dayAngle) * 1.2f * u), strokeWidth = 0.35f * u)
    drawLine(Palette.Outline, clock, Offset(clock.x + kotlin.math.sin(dayAngle / 12f) * 0.8f * u, clock.y - kotlin.math.cos(dayAngle / 12f) * 0.8f * u), strokeWidth = 0.45f * u)
    if (engine.state.officeIndex >= 2) {
        px(ox, oy, u, 2.4f, 6.6f, 5.2f, 3.6f, Palette.GoldDark)
        px(ox, oy, u, 2.9f, 7.1f, 4.2f, 2.6f, lerp(Palette.deptWall(id), Color(0xFF3E6E8E), 0.6f))
        px(ox, oy, u, 3.2f, 8.4f, 3.6f, 1.1f, Color(0xFF5DBB63))
    }
    val incident = engine.incidentFor(id)
    val broken = incident?.kind == IncidentKind.BREAKDOWN
    val absent = if (incident?.kind == IncidentKind.FLU) 1 else 0


    // Puestos de trabajo.
    val desks = engine.office().desksPerFloor
    val slotsLeft = LEFT_UNITS
    val slotW = (w - slotsLeft - 1f) / desks
    val tier = d.equipmentTier
    for (k in 0 until desks) {
        val cx = slotsLeft + slotW * k + slotW / 2f - 1f
        val occupied = k < d.workers
        val hasPc = k < d.desks
        drawDesk(ox, oy, u, cx, tier, hasPc, occupied && working, frame + k * 137L + index * 59L, broken)
        if (broken && k == 0) drawSmoke(ox, oy, u, cx + 4f, frame)
        if (occupied && k >= d.workers - absent) {
            drawChair(ox, oy, u, cx)
        } else if (occupied) {
            val seed = index * 7 + k
            drawWorker(ox, oy, u, cx, Palette.deptShirt(id), SKINS[seed % SKINS.size], HAIRS[(seed * 3 + 1) % HAIRS.size], working, frame + seed * 91L)
        } else {
            drawChair(ox, oy, u, cx)
        }
        // Lámpara del techo.
        px(ox, oy, u, cx + 1.5f, 1f, 0.4f, 1.5f, Palette.Outline)
        px(ox, oy, u, cx, 2.5f, 3.4f, 1.2f, Color(0xFF3B4650))
        px(ox, oy, u, cx + 0.4f, 3.7f, 2.6f, 0.5f, Color(0xFFFFF4C2))
    }

    // Etiqueta del departamento, encima de las lámparas para que se lea siempre, y avisos de temporada o imprevisto.
    val levelText = (if (lang == Lang.ES) " · Nv. " else " · Lv. ") + d.level
    var right = label(m, density, "${def.emoji} ${def.name.get(lang)}$levelText", ox + 1.8f * u, oy + 1.7f * u, 3.1f, u,
        maxWidth = (w * 0.6f) * u, pill = true, shadow = false)
    val season = engine.activeSeasons().firstOrNull { it.dept == id }
    if (season != null) {
        right = label(m, density, "${season.emoji} ×${com.alecu.balancetycoon.core.format.Fmt.decimal2(season.multiplier, lang).trimEnd('0').trimEnd(',', '.')}",
            right + 2.2f * u, oy + 1.7f * u, 3.1f, u, Palette.Stroke, pill = true, shadow = false, pillColor = Palette.Gold)
    }
    when (incident?.kind) {
        IncidentKind.BREAKDOWN -> label(m, density, if (lang == Lang.ES) "💥 Avería" else "💥 Failure", right + 2.2f * u, oy + 1.7f * u, 3.1f, u,
            Color.White, pill = true, shadow = false, pillColor = Palette.Red)
        IncidentKind.FLU -> label(m, density, if (lang == Lang.ES) "🤧 Gripe" else "🤧 Flu", right + 2.2f * u, oy + 1.7f * u, 3.1f, u,
            Color.White, pill = true, shadow = false, pillColor = Color(0xFF8A6BB8))
        else -> Unit
    }

    // Barra de progreso del expediente sobre el suelo.
    val barX = slotsLeft
    val barW = w - slotsLeft - 1.5f
    px(ox, oy, u, barX, 21.8f, barW, 1.3f, Palette.Outline)
    val cycle = engine.cycleSeconds(id)
    if (working) {
        if (cycle < 0.6) {
            px(ox, oy, u, barX + 0.2f, 22f, barW - 0.4f, 0.9f, Palette.Mint)
            val stripe = ((frame / 30) % 8).toFloat()
            var sx = stripe
            while (sx < barW - 1f) {
                px(ox, oy, u, barX + sx, 22f, 1.2f, 0.9f, Color.White, 0.45f)
                sx += 8f
            }
        } else {
            val p = (d.progress / cycle).toFloat().coerceIn(0f, 1f)
            px(ox, oy, u, barX + 0.2f, 22f, (barW - 0.4f) * p, 0.9f, Palette.Mint)
        }
    } else {
        // Sin trabajadores: hay que tocar para que el jefe tramite el expediente.
        val pulse = (sin(frame / 220.0) * 0.5 + 0.5).toFloat()
        val text = if (lang == Lang.ES) "👆 ¡Toca aquí!" else "👆 Tap here!"
        drawRoundRect(
            Palette.Stroke.copy(alpha = 0.75f),
            topLeft = Offset(ox + (slotsLeft + 1f) * u, oy + 6.5f * u),
            size = Size((w - slotsLeft - 3f) * u, 5f * u),
            cornerRadius = CornerRadius(u, u),
        )
        label(m, density, text, ox + (slotsLeft + (w - slotsLeft - 1f) / 2f) * u, oy + 7.3f * u, 2.8f, u,
            Palette.Gold.copy(alpha = 0.6f + 0.4f * pulse), centered = true, shadow = false, maxWidth = (w - slotsLeft - 4f) * u)
    }
}

private fun DrawScope.drawChair(ox: Float, oy: Float, u: Float, cx: Float) {
    px(ox, oy, u, cx - 6.5f, 11f, 1.2f, 6f, Color(0xFF2F3A44))
    px(ox, oy, u, cx - 6.5f, 15.5f, 4f, 1.2f, Color(0xFF2F3A44))
    px(ox, oy, u, cx - 5f, 16.7f, 0.8f, 3.5f, Palette.Outline)
    px(ox, oy, u, cx - 6.5f, 20.2f, 4f, 0.8f, Palette.Outline)
}

private fun DrawScope.drawWorker(
    ox: Float, oy: Float, u: Float, cx: Float, shirt: Color, skin: Color, hair: Color, typing: Boolean, t: Long,
) {
    drawChair(ox, oy, u, cx)
    val bob = if (typing && (t / 260) % 2 == 0L) 0.3f else 0f
    // Piernas hacia la mesa.
    px(ox, oy, u, cx - 5f, 15f, 4.5f, 1.6f, Color(0xFF2E3B55))
    px(ox, oy, u, cx - 1.2f, 15f, 1.4f, 5.5f, Color(0xFF2E3B55))
    px(ox, oy, u, cx - 1.4f, 20.2f, 2.2f, 0.8f, Palette.Outline)
    // Cuerpo y cabeza.
    px(ox, oy, u, cx - 5.5f, 9.5f + bob, 4f, 6f, shirt)
    px(ox, oy, u, cx - 5.5f, 9.5f + bob, 4f, 0.8f, shade(shirt, 1.2f))
    px(ox, oy, u, cx - 5.2f, 5.8f + bob, 3.6f, 3.8f, skin)
    px(ox, oy, u, cx - 5.4f, 5.2f + bob, 3.9f, 1.4f, hair)
    px(ox, oy, u, cx - 5.4f, 5.2f + bob, 1.2f, 3f, hair)
    px(ox, oy, u, cx - 2.6f, 7.2f + bob, 0.7f, 0.7f, Palette.Outline)
    // Brazo hacia el teclado: sube y baja al teclear.
    val arm = if (typing && (t / 140) % 2 == 0L) -0.5f else 0f
    px(ox, oy, u, cx - 2f, 11.5f + arm + bob, 3.5f, 1.2f, shirt)
    px(ox, oy, u, cx + 1.4f, 11.5f + arm + bob, 1f, 1.1f, skin)
}

private fun DrawScope.drawSmoke(ox: Float, oy: Float, u: Float, x: Float, frame: Long) {
    for (k in 0 until 3) {
        val p = ((frame / 900f + k / 3f) % 1f)
        drawCircle(Color(0xFF8A8A8A), radius = (0.8f + p * 1.6f) * u, center = Offset(ox + (x + sin(p * 6f + k) * 0.8f) * u, oy + (8f - p * 6f) * u), alpha = 0.7f * (1f - p))
    }
}

private fun DrawScope.drawDesk(ox: Float, oy: Float, u: Float, cx: Float, tier: Int, hasPc: Boolean, working: Boolean, t: Long, broken: Boolean = false) {
    // Mesa.
    px(ox, oy, u, cx - 2f, 14.6f, 10f, 1.4f, Palette.Desk)
    px(ox, oy, u, cx - 2f, 14.6f, 10f, 0.4f, shade(Palette.Desk, 1.2f))
    px(ox, oy, u, cx - 1.5f, 16f, 0.9f, 5f, Palette.DeskDark)
    px(ox, oy, u, cx + 6.6f, 16f, 0.9f, 5f, Palette.DeskDark)
    px(ox, oy, u, cx - 0.6f, 16f, 7.2f, 2.6f, shade(Palette.Desk, 0.8f))
    // Teclado.
    px(ox, oy, u, cx + 0.5f, 14f, 3f, 0.6f, Color(0xFF3B3F45))
    if (!hasPc) return
    val screenOn = working
    val flicker = ((t / 180) % 3).toInt()
    fun screen(x: Float, y: Float, w: Float, h: Float, base: Color) {
        if (broken) {
            // Pantalla averiada: ruido rojo y gris que parpadea.
            px(ox, oy, u, x, y, w, h, if (flicker == 0) Color(0xFF7A1F1F) else Color(0xFF3A2A2A))
            for (line in 0 until (h / 0.8f).toInt()) {
                px(ox, oy, u, x + ((line * 7 + flicker * 3) % 5) * 0.3f, y + line * 0.8f, w * 0.5f, 0.3f, Color.White, 0.35f)
            }
            return
        }
        px(ox, oy, u, x, y, w, h, if (screenOn) base else Color(0xFF26313A))
        if (screenOn) {
            for (line in 0 until (h / 1.2f).toInt()) {
                val lw = (w - 1f) * (0.4f + ((line + flicker) % 3) * 0.2f)
                px(ox, oy, u, x + 0.5f, y + 0.5f + line * 1.2f, lw, 0.5f, Color.White, 0.55f)
            }
        }
    }
    when (tier) {
        0 -> {
            // Monitor de tubo beis.
            px(ox, oy, u, cx + 2f, 8.6f, 5.6f, 5.4f, Color(0xFFD8CFB4))
            px(ox, oy, u, cx + 2.6f, 14f, 4.4f, 0.6f, Color(0xFFB9AF92))
            screen(cx + 2.6f, 9.2f, 4.4f, 3.6f, Color(0xFF3E7D4A))
        }
        1 -> {
            px(ox, oy, u, cx + 2f, 8.6f, 5.6f, 4.6f, Palette.Outline)
            screen(cx + 2.4f, 9f, 4.8f, 3.8f, Color(0xFF3B82C4))
            px(ox, oy, u, cx + 4.4f, 13.2f, 0.8f, 1.4f, Palette.Outline)
        }
        2 -> {
            px(ox, oy, u, cx + 0.5f, 9.2f, 4.2f, 4f, Palette.Outline)
            screen(cx + 0.8f, 9.5f, 3.6f, 3.3f, Color(0xFF3B82C4))
            px(ox, oy, u, cx + 4.4f, 8.6f, 4.6f, 4.6f, Palette.Outline)
            screen(cx + 4.7f, 8.9f, 4f, 3.9f, Color(0xFF4CA3A0))
            px(ox, oy, u, cx + 6.4f, 13.2f, 0.8f, 1.4f, Palette.Outline)
            // Escáner.
            px(ox, oy, u, cx - 1.8f, 13.4f, 2.4f, 1.2f, Color(0xFFBFC5CC))
        }
        3 -> {
            px(ox, oy, u, cx + 0.8f, 7.8f, 7.6f, 5.4f, Palette.Outline)
            screen(cx + 1.2f, 8.2f, 6.8f, 4.6f, Color(0xFF6A5ACD))
            px(ox, oy, u, cx + 4.2f, 13.2f, 0.8f, 1.4f, Palette.Outline)
            // Torre bajo la mesa con luz.
            px(ox, oy, u, cx + 3.5f, 16.5f, 2.6f, 4.5f, Color(0xFF2F3A44))
            px(ox, oy, u, cx + 4.4f, 17.2f, 0.8f, 0.8f, if (working) Palette.Mint else Color(0xFF406050))
        }
        4 -> {
            px(ox, oy, u, cx + 1.4f, 8.2f, 6f, 5f, Palette.Outline)
            screen(cx + 1.8f, 8.6f, 5.2f, 4.2f, Color(0xFF2B7A9E))
            px(ox, oy, u, cx + 4f, 13.2f, 0.8f, 1.4f, Palette.Outline)
            // Servidor con LEDs que parpadean.
            px(ox, oy, u, cx + 8.2f, 11f, 2.4f, 10f, Color(0xFF1F2830))
            for (k in 0 until 5) {
                val on = ((t / 120 + k * 3) % 4) != 0L
                px(ox, oy, u, cx + 8.7f, 12f + k * 1.8f, 0.6f, 0.6f, if (on && working) Palette.Mint else Color(0xFF2F4F3F))
                px(ox, oy, u, cx + 9.5f, 12f + k * 1.8f, 0.6f, 0.6f, if (on && working && k % 2 == 0) Palette.Gold else Color(0xFF4F4A2F))
            }
        }
        else -> {
            // Holograma de IA sobre la mesa.
            val glow = (sin(t / 300.0) * 0.5 + 0.5).toFloat()
            px(ox, oy, u, cx + 1f, 13.6f, 6f, 1f, Color(0xFF2F3A44))
            px(ox, oy, u, cx + 1.4f, 7f, 5.2f, 6.4f, Color(0xFF7FF5FF), alpha = if (working) 0.35f + 0.25f * glow else 0.15f)
            px(ox, oy, u, cx + 1.4f, 7f, 5.2f, 0.4f, Color(0xFF7FF5FF), alpha = 0.9f)
            if (working) {
                for (k in 0 until 3) px(ox, oy, u, cx + 2f + k * 1.6f, 9f + ((t / 150 + k) % 3), 0.8f, 0.8f, Color.White, 0.9f)
            }
        }
    }
}

// ───────────────────────────── Recepción ─────────────────────────────

private const val COUNTER_X = 3f
private const val COUNTER_W = 16f
private const val BOSS_X = 8.8f
private const val QUEUE_GAP = 6f

private fun DrawScope.drawLobby(
    g: TowerGeometry, engine: GameEngine, arrivals: Map<Int, Long>, lang: Lang, m: TextMeasurer, density: Density, frame: Long, officeIndex: Int,
) {
    val cases = engine.state.cases
    val pendingCases = cases.size
    val u = g.u
    val ox = g.mainLeft
    val oy = g.lobbyTop
    val w = (g.mainW + SHAFT_UNITS * u) / u
    // Suelo de baldosas y pared.
    px(ox, oy, u, 0f, 0f, w, FLOOR_UNITS, Color(0xFFE9E4D8))
    px(ox, oy, u, 0f, 0f, w, 1f, Color(0xFFBDB6A6))
    // Suelo: baldosas en los primeros locales y mármol en las sedes grandes.
    val (tileA, tileB) = if (officeIndex >= 3) Color(0xFFF1EFEA) to Color(0xFFD9D6CF) else Color(0xFFCFC8B8) to Color(0xFFB8B09E)
    var tx = 0f
    var k = 0
    while (tx < w) {
        px(ox, oy, u, tx, 21f, 3f, 3f, if (k % 2 == 0) tileA else tileB)
        tx += 3f
        k++
    }
    if (officeIndex >= 4) px(ox, oy, u, 0f, 1f, w, 0.6f, Palette.Gold)
    // Cuadro en la pared de las sedes grandes.
    if (officeIndex >= 2) {
        px(ox, oy, u, w - 19.5f, 2.5f, 6.4f, 5f, Palette.GoldDark)
        px(ox, oy, u, w - 19f, 3f, 5.4f, 4f, Color(0xFF8FC1D9))
        px(ox, oy, u, w - 19f, 5.5f, 5.4f, 1.5f, Color(0xFF6E9A4E))
        drawCircle(Color(0xFFF4C542), radius = 0.7f * u, center = Offset(ox + (w - 15f) * u, oy + 4.2f * u))
    }
    // Puerta de cristal.
    px(ox, oy, u, w - 13f, 5f, 10f, 16f, Palette.Outline)
    px(ox, oy, u, w - 12.4f, 5.6f, 4.2f, 15.4f, Color(0xFFA9DCF0))
    px(ox, oy, u, w - 7.8f, 5.6f, 4.2f, 15.4f, Color(0xFFA9DCF0))
    px(ox, oy, u, w - 11.5f, 6.5f, 1f, 8f, Color.White, 0.6f)
    // Planta grande.
    px(ox, oy, u, w - 18f, 17f, 3.4f, 4f, Color(0xFFA0522D))
    px(ox, oy, u, w - 19f, 11f, 5.4f, 6.4f, Color(0xFF3E8E41))
    // El jefe o la jefa de la gestoría (tú), detrás del mostrador con corbata dorada.
    val bx = BOSS_X
    val bob = ((frame / 400) % 2).toFloat() * 0.3f
    px(ox, oy, u, bx - 0.5f, 9.5f - bob, 4.5f, 6.8f, Color(0xFF2F3E56))
    px(ox, oy, u, bx + 1.3f, 9.5f - bob, 1f, 4f, Palette.Gold)
    px(ox, oy, u, bx - 0.1f, 5.6f - bob, 3.8f, 3.9f, SKINS[0])
    px(ox, oy, u, bx - 0.3f, 5f - bob, 4.2f, 1.4f, HAIRS[1])
    px(ox, oy, u, bx + 0.7f, 7.2f - bob, 0.6f, 0.6f, Palette.Outline)
    px(ox, oy, u, bx + 2.5f, 7.2f - bob, 0.6f, 0.6f, Palette.Outline)
    // Mostrador de recepción, con el rótulo en el frontal.
    px(ox, oy, u, COUNTER_X, 13.5f, COUNTER_W, 7.5f, Color(0xFF3F5D6B))
    px(ox, oy, u, COUNTER_X, 13.5f, COUNTER_W, 1.2f, Palette.Gold)
    px(ox, oy, u, COUNTER_X + 1f, 15.4f, COUNTER_W - 2f, 4.8f, Color(0xFF34505C))
    label(m, density, if (lang == Lang.ES) "RECEPCIÓN" else "RECEPTION", ox + (COUNTER_X + COUNTER_W / 2f) * u, oy + 15.9f * u, 2.6f, u, Palette.Cream, centered = true, shadow = false)
    // Clientes que esperan su consulta en fila a la derecha del mostrador (los VIP, primero);
    // los nuevos entran andando desde la puerta.
    val queue = cases.sortedWith(compareByDescending<com.alecu.balancetycoon.core.game.PendingCase> { it.vip }.thenBy { it.id })
    val lastSlot = w - 26f
    for ((i, pc) in queue.withIndex()) {
        val slot = COUNTER_X + COUNTER_W + 1.5f + i * QUEUE_GAP
        if (slot > lastSlot) break
        val since = frame - (arrivals[pc.id] ?: (frame - 60_000L))
        val walk = (since / 1600f).coerceIn(0f, 1f)
        val doorX = w - 9f
        val x = doorX + (slot - doorX) * walk
        drawClient(ox, oy, u, x, pc.id, pc.tier, pc.vip, walk < 1f, frame + pc.id * 37L)
    }
    // Bocadillo con las consultas pendientes.
    if (pendingCases > 0) {
        val pulse = (sin(frame / 250.0) * 0.5 + 0.5).toFloat()
        val text = if (lang == Lang.ES) {
            if (pendingCases == 1) "¡1 consulta!" else "¡$pendingCases consultas!"
        } else {
            if (pendingCases == 1) "1 question!" else "$pendingCases questions!"
        }
        val style = TextStyle(fontFamily = PixelFont, fontSize = with(density) { (u * 2.6f).toSp() }, color = Palette.Stroke)
        val layout = m.measure(pixelString(text), style)
        val bw = layout.size.width + 3f * u
        val bh = layout.size.height + 1.1f * u
        val bxPx = ox + (bx + 4.6f) * u
        val byPx = oy + 1.0f * u - pulse * u * 0.4f
        drawRoundRect(Palette.Stroke, Offset(bxPx - 0.4f * u, byPx - 0.4f * u), Size(bw + 0.8f * u, bh + 0.8f * u), CornerRadius(1.6f * u, 1.6f * u))
        drawRoundRect(Color.White, Offset(bxPx, byPx), Size(bw, bh), CornerRadius(1.4f * u, 1.4f * u))
        px(bxPx, byPx, u, 0.4f, bh / u - 0.1f, 1.6f, 1.6f, Color.White)
        drawText(layout, topLeft = Offset(bxPx + 1.5f * u, byPx + 0.55f * u))
    }
    // Línea del suelo de la calle.
    px(ox, oy, u, -1f, FLOOR_UNITS - 0.3f, w + 2f, 0.6f, Palette.Outline)
}

/** Cliente de pie, mirando al mostrador. La ropa depende del tipo de cliente; el VIP va de traje morado y gafas de sol. */
private fun DrawScope.drawClient(ox: Float, oy: Float, u: Float, x: Float, seed: Int, tier: Int, vip: Boolean, walking: Boolean, t: Long) {
    val skin = SKINS[(seed * 5 + 2) % SKINS.size]
    val hair = HAIRS[(seed * 3) % HAIRS.size]
    val casual = listOf(Color(0xFF4FA3D1), Color(0xFFE08A3C), Color(0xFF6AB04C), Color(0xFFD45D79), Color(0xFF8E7CC3))
    val body = when {
        vip -> Color(0xFF6B3FA0)
        tier >= 2 -> listOf(Color(0xFF2F3E56), Color(0xFF3B3B3B), Color(0xFF4A3B2F))[seed % 3]
        else -> casual[seed % casual.size]
    }
    val step = if (walking && (t / 180) % 2 == 0L) 0.8f else 0f
    // Piernas (al andar se alternan).
    px(ox, oy, u, x + 0.2f + step, 16f, 1.4f, 5f, Color(0xFF2E3B55))
    px(ox, oy, u, x + 2f - step, 16f, 1.4f, 5f, Color(0xFF2E3B55))
    px(ox, oy, u, x, 9.6f, 3.8f, 6.6f, body)
    if (tier >= 2 || vip) px(ox, oy, u, x + 1.4f, 9.6f, 0.9f, 3.6f, if (vip) Palette.Gold else Color(0xFFB03A2E))
    px(ox, oy, u, x + 0.1f, 5.8f, 3.4f, 3.8f, skin)
    px(ox, oy, u, x - 0.1f, 5.2f, 3.8f, 1.3f, hair)
    px(ox, oy, u, x + 2.4f, 5.2f, 1.2f, 2.6f, hair)
    if (vip) {
        px(ox, oy, u, x - 0.1f, 7f, 2.6f, 0.8f, Color(0xFF111111))
        val spark = (sin(t / 200.0) * 0.5 + 0.5).toFloat()
        px(ox, oy, u, x - 1.6f, 6.4f - spark, 1f, 1f, Palette.Gold, 0.5f + 0.5f * spark)
    } else {
        px(ox, oy, u, x + 0.6f, 7.2f, 0.6f, 0.6f, Palette.Outline)
    }
    // Maletín para las empresas medianas y grandes.
    if (tier >= 3 || vip) {
        px(ox, oy, u, x + 3.3f, 14.5f, 2.2f, 2.2f, Color(0xFF6B4226))
        px(ox, oy, u, x + 3.9f, 14f, 1f, 0.5f, Palette.Outline)
    }
}
