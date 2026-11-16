package app.crome1394.almanac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.GolfCourse
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Festival
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.crome1394.almanac.data.EventTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Month banners: a small landscape for each month, drawn in layers so the list can move them at
// different speeds as it scrolls (parallax). Original artwork, drawn with shapes.
// ---------------------------------------------------------------------------------------------

/**
 * [parallax] runs from -1 (banner at the bottom of the screen) to 1 (scrolled off the top);
 * nearer layers move more.
 */
@Composable
fun MonthScene(month: Int, dark: Boolean, parallax: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ink = Ink(dark)
        val p = parallax().coerceIn(-1.5f, 1.5f)
        // Sky doesn't move; each layer after it moves a little more.
        fun layer(depth: Float, block: DrawScope.() -> Unit) = translate(top = p * depth * size.height * 0.18f) { block() }
        when (month) {
            1 -> january(ink, ::layer)
            2 -> february(ink, ::layer)
            3 -> march(ink, ::layer)
            4 -> april(ink, ::layer)
            5 -> may(ink, ::layer)
            6 -> june(ink, ::layer)
            7 -> july(ink, ::layer)
            8 -> august(ink, ::layer)
            9 -> september(ink, ::layer)
            10 -> october(ink, ::layer)
            11 -> november(ink, ::layer)
            else -> december(ink, ::layer)
        }
    }
}

/** Colors for a scene; in dark mode everything is pulled toward night. */
private class Ink(val dark: Boolean) {
    operator fun invoke(c: Long): Color {
        val col = Color(c)
        return if (dark) lerpColor(col, Color(0xFF0E1424), 0.36f) else col
    }
}

private typealias Layer = (Float, DrawScope.() -> Unit) -> Unit

private fun DrawScope.sky(top: Color, bottom: Color) =
    drawRect(Brush.verticalGradient(listOf(top, bottom)), size = size)

/** A band of rolling hills whose top edge sits around [y] (fraction of height). */
private fun DrawScope.hills(y: Float, amp: Float, waves: Float, phase: Float, color: Color) {
    val w = size.width; val h = size.height
    val path = Path().apply {
        moveTo(0f, h * 2)
        val steps = 48
        for (i in 0..steps) {
            val x = w * i / steps
            val yy = h * (y + amp * sin((i.toFloat() / steps * waves * 2 * PI + phase).toFloat()) +
                amp * 0.4f * sin((i.toFloat() / steps * waves * 5.3 * PI + phase * 2).toFloat()))
            lineTo(x, yy)
        }
        lineTo(w, h * 2); close()
    }
    drawPath(path, color)
}

/** Jagged mountains with optional snow caps. */
private fun DrawScope.mountains(y: Float, peaks: List<Pair<Float, Float>>, color: Color, snow: Color? = null) {
    val w = size.width; val h = size.height
    peaks.forEach { (cx, ph) ->
        val base = h * y; val top = h * (y - ph); val half = w * ph * 0.9f
        val path = Path().apply { moveTo(w * cx - half, base + h); lineTo(w * cx, top); lineTo(w * cx + half, base + h); close() }
        drawPath(path, color)
        if (snow != null) {
            // Points a fraction of the way down each side, so the cap sits on the peak.
            val dy = base + h - top
            fun side(f: Float, dir: Int) = Offset(w * cx + dir * half * f, top + dy * f)
            val cap = Path().apply {
                moveTo(w * cx, top)
                side(0.16f, 1).let { lineTo(it.x, it.y) }
                lineTo(w * cx + half * 0.06f, top + dy * 0.12f)
                lineTo(w * cx - half * 0.05f, top + dy * 0.17f)
                side(0.14f, -1).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(cap, snow)
        }
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, color: Color, glow: Boolean = true) {
    val c = Offset(size.width * cx, size.height * cy)
    val rr = size.height * r
    if (glow) drawCircle(color.copy(alpha = 0.25f), rr * 1.6f, c)
    drawCircle(color, rr, c)
}

private fun DrawScope.cloud(cx: Float, cy: Float, s: Float, color: Color) {
    val x = size.width * cx; val y = size.height * cy; val r = size.height * s
    drawCircle(color, r, Offset(x, y))
    drawCircle(color, r * 0.75f, Offset(x - r * 1.0f, y + r * 0.25f))
    drawCircle(color, r * 0.8f, Offset(x + r * 1.05f, y + r * 0.2f))
    drawRect(color, Offset(x - r * 1.0f, y + r * 0.2f), Size(r * 2.1f, r * 0.8f))
}

private fun DrawScope.pine(x: Float, baseY: Float, hgt: Float, color: Color, snow: Color? = null) {
    val bx = size.width * x; val by = size.height * baseY; val th = size.height * hgt
    drawRect(lerpColor(color, Color(0xFF4E342E), 0.6f), Offset(bx - th * 0.04f, by - th * 0.12f), Size(th * 0.08f, th * 0.14f))
    for (k in 0 until 3) {
        val top = by - th + th * 0.28f * k
        val bottom = top + th * 0.45f
        val half = th * (0.18f + 0.07f * k)
        drawPath(Path().apply { moveTo(bx, top); lineTo(bx + half, bottom); lineTo(bx - half, bottom); close() }, color)
        if (snow != null) drawPath(Path().apply {
            moveTo(bx, top); lineTo(bx + half * 0.45f, top + th * 0.2f); lineTo(bx - half * 0.45f, top + th * 0.2f); close()
        }, snow)
    }
}

private fun DrawScope.roundTree(x: Float, baseY: Float, hgt: Float, crown: Color, trunk: Color = Color(0xFF6D4C41)) {
    val bx = size.width * x; val by = size.height * baseY; val th = size.height * hgt
    drawRect(trunk, Offset(bx - th * 0.05f, by - th * 0.5f), Size(th * 0.1f, th * 0.5f))
    drawCircle(crown, th * 0.32f, Offset(bx, by - th * 0.68f))
    drawCircle(crown, th * 0.24f, Offset(bx - th * 0.22f, by - th * 0.5f))
    drawCircle(crown, th * 0.24f, Offset(bx + th * 0.22f, by - th * 0.52f))
}

private fun DrawScope.speckles(seed: Int, n: Int, color: Color, rMin: Float, rMax: Float, yFrom: Float = 0f, yTo: Float = 1f) {
    val rnd = Random(seed)
    repeat(n) {
        val r = size.height * (rMin + rnd.nextFloat() * (rMax - rMin))
        drawCircle(color, r, Offset(rnd.nextFloat() * size.width, size.height * (yFrom + rnd.nextFloat() * (yTo - yFrom))))
    }
}

private fun DrawScope.birds(cx: Float, cy: Float, n: Int, color: Color) {
    val s = size.height * 0.035f
    for (k in 0 until n) {
        val x = size.width * cx + k * s * 3.2f
        val y = size.height * cy + (if (k % 2 == 0) 0f else s * 1.4f) + k * s * 0.3f
        val path = Path().apply {
            moveTo(x - s, y); quadraticTo(x - s * 0.5f, y - s * 0.7f, x, y); quadraticTo(x + s * 0.5f, y - s * 0.7f, x + s, y)
        }
        drawPath(path, color, style = Stroke(width = s * 0.32f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.january(c: Ink, layer: Layer) {
    sky(c(0xFFBFD9F2), c(0xFFE9F2FB))
    sun(0.8f, 0.28f, 0.11f, c(0xFFFFF4D6))
    layer(0.2f) { mountains(0.62f, listOf(0.18f to 0.42f, 0.42f to 0.52f, 0.7f to 0.4f), c(0xFF9DB8D6), c(0xFFF7FBFF)) }
    layer(0.45f) { hills(0.7f, 0.04f, 1.3f, 0.5f, c(0xFFE3EEF8)); listOf(0.08f, 0.15f, 0.62f, 0.9f).forEach { pine(it, 0.75f, 0.32f, c(0xFF2E6B4F), c(0xFFF7FBFF)) } }
    layer(0.8f) { hills(0.86f, 0.03f, 0.9f, 2.1f, c(0xFFFAFDFF)); pine(0.3f, 0.92f, 0.42f, c(0xFF245A42), c(0xFFFFFFFF)); pine(0.78f, 0.95f, 0.5f, c(0xFF245A42), c(0xFFFFFFFF)) }
    speckles(1, 40, Color.White.copy(alpha = 0.9f), 0.008f, 0.02f)
}

private fun DrawScope.february(c: Ink, layer: Layer) {
    sky(c(0xFFD9C8EC), c(0xFFF8D9E3))
    sun(0.25f, 0.32f, 0.1f, c(0xFFFFFFFF))
    layer(0.15f) { mountains(0.6f, listOf(0.12f to 0.36f, 0.35f to 0.48f, 0.6f to 0.38f, 0.88f to 0.5f), c(0xFFA99BC9), c(0xFFF4EEFB)) }
    layer(0.35f) {
        drawRect(c(0xFFCFE3F3), Offset(0f, size.height * 0.62f), Size(size.width, size.height))
        speckles(2, 10, c(0xFFFFFFFF).copy(alpha = 0.6f), 0.004f, 0.01f, 0.65f, 0.8f)
    }
    layer(0.7f) { hills(0.84f, 0.04f, 1.1f, 1.2f, c(0xFFF3EDF8)); pine(0.85f, 0.9f, 0.4f, c(0xFF5B4E7A), c(0xFFFFFFFF)); pine(0.92f, 0.92f, 0.32f, c(0xFF5B4E7A), c(0xFFFFFFFF)) }
}

private fun DrawScope.march(c: Ink, layer: Layer) {
    sky(c(0xFFCDEBDD), c(0xFFF5F9DF))
    cloud(0.2f, 0.25f, 0.08f, c(0xFFFFFFFF)); cloud(0.72f, 0.18f, 0.06f, c(0xFFFFFFFF))
    layer(0.2f) { hills(0.55f, 0.06f, 1.0f, 0.2f, c(0xFFA8D5A2)) }
    layer(0.45f) { hills(0.7f, 0.05f, 1.6f, 1.4f, c(0xFF7CC07A)); roundTree(0.84f, 0.72f, 0.3f, c(0xFF9CCC65)) }
    layer(0.8f) {
        hills(0.86f, 0.03f, 1.2f, 2.5f, c(0xFF5DA65B))
        val rnd = Random(3)
        repeat(18) {
            val x = rnd.nextFloat(); val y = 0.9f + rnd.nextFloat() * 0.08f
            drawCircle(listOf(c(0xFFFFF176), c(0xFFFFFFFF), c(0xFFF8BBD0))[it % 3], size.height * 0.018f, Offset(size.width * x, size.height * y))
        }
    }
    // A kite
    val kx = size.width * 0.52f; val ky = size.height * 0.28f; val k = size.height * 0.08f
    drawPath(Path().apply { moveTo(kx, ky - k); lineTo(kx + k * 0.7f, ky); lineTo(kx, ky + k * 1.2f); lineTo(kx - k * 0.7f, ky); close() }, c(0xFFEF5350))
    drawLine(c(0xFF795548), Offset(kx, ky + k * 1.2f), Offset(kx - k * 2f, size.height * 0.8f), strokeWidth = 2f)
}

private fun DrawScope.april(c: Ink, layer: Layer) {
    sky(c(0xFFB9CCE0), c(0xFFE4EEF5))
    // Rainbow
    val colors = listOf(0xFFEF9A9A, 0xFFFFCC80, 0xFFFFF59D, 0xFFA5D6A7, 0xFF90CAF9, 0xFFCE93D8)
    colors.forEachIndexed { i, col ->
        val r = size.height * (0.75f - i * 0.06f)
        drawArc(c(col).copy(alpha = 0.8f), 180f, 180f, false, Offset(size.width * 0.62f - r, size.height * 0.85f - r), Size(r * 2, r * 2),
            style = Stroke(width = size.height * 0.06f))
    }
    cloud(0.25f, 0.22f, 0.1f, c(0xFF9FB3C8)); cloud(0.45f, 0.15f, 0.07f, c(0xFFAFC0D2))
    val rnd = Random(4)
    repeat(26) {
        val x = size.width * (0.1f + rnd.nextFloat() * 0.45f); val y = size.height * (0.32f + rnd.nextFloat() * 0.4f)
        drawLine(c(0xFF7E9AB8), Offset(x, y), Offset(x - size.height * 0.02f, y + size.height * 0.07f), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    layer(0.4f) { hills(0.74f, 0.05f, 1.2f, 0.7f, c(0xFF8BC48A)) }
    layer(0.8f) { hills(0.88f, 0.03f, 1.0f, 2.0f, c(0xFF62A964)); roundTree(0.12f, 0.95f, 0.4f, c(0xFF4C8C4A)) }
}

private fun DrawScope.may(c: Ink, layer: Layer) {
    sky(c(0xFFAEDCF7), c(0xFFF2FAFF))
    sun(0.82f, 0.25f, 0.1f, c(0xFFFFE082))
    cloud(0.3f, 0.2f, 0.06f, c(0xFFFFFFFF))
    layer(0.25f) { hills(0.6f, 0.05f, 1.1f, 0.1f, c(0xFFB5DDA4)) }
    layer(0.5f) { hills(0.74f, 0.04f, 1.5f, 1.8f, c(0xFF8FCB7A)); speckles(5, 30, c(0xFFFFFFFF), 0.008f, 0.012f, 0.76f, 0.86f) }
    layer(0.85f) {
        hills(0.88f, 0.03f, 1.0f, 2.8f, c(0xFF6DB85E))
        val rnd = Random(55)
        val petals = listOf(0xFFF06292, 0xFFFFD54F, 0xFFBA68C8, 0xFFFF8A65, 0xFFFFFFFF)
        repeat(26) {
            val x = size.width * rnd.nextFloat(); val y = size.height * (0.9f + rnd.nextFloat() * 0.09f)
            drawCircle(c(petals[it % petals.size]), size.height * 0.024f, Offset(x, y))
            drawCircle(c(0xFFFFF59D), size.height * 0.009f, Offset(x, y))
        }
    }
}

private fun DrawScope.june(c: Ink, layer: Layer) {
    sky(c(0xFF8FD3F4), c(0xFFE6F7FD))
    sun(0.2f, 0.27f, 0.11f, c(0xFFFFE57F))
    layer(0.2f) {
        drawRect(c(0xFF4FB3E0), Offset(0f, size.height * 0.56f), Size(size.width, size.height))
        for (k in 0 until 4) {
            val y = size.height * (0.62f + k * 0.07f)
            hillsLine(y, size.height * 0.012f, 6f + k, c(0xFFB3E5FC))
        }
    }
    layer(0.7f) {
        hills(0.86f, 0.02f, 0.8f, 1.2f, c(0xFFF6DFA8))
        // Umbrella
        val ux = size.width * 0.72f; val uy = size.height * 0.62f; val r = size.height * 0.16f
        drawLine(c(0xFF8D6E63), Offset(ux, uy), Offset(ux + r * 0.15f, size.height * 0.92f), strokeWidth = 4f)
        drawArc(c(0xFFFF7043), 180f, 180f, true, Offset(ux - r, uy - r * 0.6f), Size(r * 2, r * 1.2f))
        drawArc(c(0xFFFFFFFF), 240f, 60f, true, Offset(ux - r, uy - r * 0.6f), Size(r * 2, r * 1.2f))
    }
}

private fun DrawScope.hillsLine(y: Float, amp: Float, waves: Float, color: Color) {
    val path = Path().apply {
        moveTo(0f, y)
        val steps = 60
        for (i in 0..steps) lineTo(size.width * i / steps, y + amp * sin((i.toFloat() / steps * waves * 2 * PI).toFloat()))
    }
    drawPath(path, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}

private fun DrawScope.july(c: Ink, layer: Layer) {
    sky(c(0xFFFFD7A8), c(0xFFFFF1DC))
    sun(0.65f, 0.52f, 0.16f, c(0xFFFFAB66))
    birds(0.18f, 0.25f, 3, c(0xFF8D6E63))
    layer(0.25f) { drawRect(c(0xFF3D8FC6), Offset(0f, size.height * 0.62f), Size(size.width, size.height)) }
    layer(0.45f) {
        // Sailboat
        val bx = size.width * 0.32f; val by = size.height * 0.7f; val s = size.height * 0.28f
        drawPath(Path().apply { moveTo(bx - s * 0.6f, by); lineTo(bx + s * 0.6f, by); lineTo(bx + s * 0.45f, by + s * 0.18f); lineTo(bx - s * 0.45f, by + s * 0.18f); close() }, c(0xFF6D4C41))
        drawLine(c(0xFF5D4037), Offset(bx, by), Offset(bx, by - s), strokeWidth = 3f)
        drawPath(Path().apply { moveTo(bx + 2f, by - s); lineTo(bx + s * 0.5f, by - s * 0.08f); lineTo(bx + 2f, by - s * 0.08f); close() }, c(0xFFFFFFFF))
        drawPath(Path().apply { moveTo(bx - 2f, by - s * 0.85f); lineTo(bx - s * 0.36f, by - s * 0.08f); lineTo(bx - 2f, by - s * 0.08f); close() }, c(0xFFFFE0B2))
    }
    layer(0.8f) {
        drawRect(c(0xFF2F7DB3), Offset(0f, size.height * 0.84f), Size(size.width, size.height))
        hillsLine(size.height * 0.86f, size.height * 0.012f, 9f, c(0xFF90CAF9))
        hillsLine(size.height * 0.93f, size.height * 0.012f, 7f, c(0xFF90CAF9))
    }
}

private fun DrawScope.august(c: Ink, layer: Layer) {
    sky(c(0xFF9AD0EC), c(0xFFFFF3D6))
    cloud(0.15f, 0.3f, 0.06f, c(0xFFFFFFFF)); cloud(0.88f, 0.18f, 0.05f, c(0xFFFFFFFF))
    fun balloon(x: Float, y: Float, r: Float, a: Color, b: Color) {
        val cx = size.width * x; val cy = size.height * y; val rr = size.height * r
        drawOval(a, Offset(cx - rr, cy - rr * 1.15f), Size(rr * 2, rr * 2.3f))
        drawOval(b, Offset(cx - rr * 0.4f, cy - rr * 1.15f), Size(rr * 0.8f, rr * 2.3f))
        drawLine(c(0xFF6D4C41), Offset(cx - rr * 0.5f, cy + rr * 0.95f), Offset(cx - rr * 0.2f, cy + rr * 1.5f), strokeWidth = 2f)
        drawLine(c(0xFF6D4C41), Offset(cx + rr * 0.5f, cy + rr * 0.95f), Offset(cx + rr * 0.2f, cy + rr * 1.5f), strokeWidth = 2f)
        drawRect(c(0xFF8D6E63), Offset(cx - rr * 0.22f, cy + rr * 1.5f), Size(rr * 0.44f, rr * 0.35f))
    }
    layer(0.1f) { balloon(0.62f, 0.25f, 0.07f, c(0xFF64B5F6), c(0xFFFFFFFF)) }
    layer(0.3f) { hills(0.66f, 0.05f, 1.2f, 0.4f, c(0xFFE8C77E)) }
    layer(0.5f) { balloon(0.3f, 0.32f, 0.11f, c(0xFFEF5350), c(0xFFFFCA28)) }
    layer(0.8f) { hills(0.84f, 0.04f, 1.0f, 2.2f, c(0xFFD9A84E)); roundTree(0.86f, 0.92f, 0.36f, c(0xFF7CB342)) }
}

private fun DrawScope.september(c: Ink, layer: Layer) {
    sky(c(0xFFCFE3EE), c(0xFFFFF4E0))
    sun(0.15f, 0.3f, 0.08f, c(0xFFFFE0A3))
    layer(0.25f) { hills(0.62f, 0.05f, 1.0f, 1.0f, c(0xFFC8D7A0)) }
    layer(0.5f) {
        hills(0.76f, 0.04f, 1.3f, 0.3f, c(0xFFB5C47A))
        roundTree(0.25f, 0.8f, 0.42f, c(0xFFFFB74D)); roundTree(0.45f, 0.78f, 0.36f, c(0xFF9CCC65)); roundTree(0.7f, 0.8f, 0.46f, c(0xFFFF8A65))
    }
    layer(0.85f) { hills(0.9f, 0.025f, 1.0f, 2.4f, c(0xFFA1A55A)) }
    val rnd = Random(9)
    repeat(14) {
        val x = size.width * rnd.nextFloat(); val y = size.height * (0.15f + rnd.nextFloat() * 0.6f)
        drawOval(c(listOf(0xFFFF8A65, 0xFFFFB74D, 0xFFD84315)[it % 3]), Offset(x, y), Size(size.height * 0.035f, size.height * 0.018f))
    }
}

private fun DrawScope.october(c: Ink, layer: Layer) {
    sky(c(0xFFFFC48C), c(0xFFFFE9CF))
    sun(0.78f, 0.4f, 0.13f, c(0xFFFFF0C2))
    birds(0.2f, 0.2f, 4, c(0xFF6D4C41))
    layer(0.25f) { hills(0.62f, 0.06f, 1.0f, 2.0f, c(0xFFD9885A)) }
    layer(0.5f) {
        hills(0.76f, 0.04f, 1.4f, 0.6f, c(0xFFB86B44))
        // Bare tree
        val x = size.width * 0.18f; val base = size.height * 0.8f; val t = size.height * 0.45f; val col = c(0xFF5D4037)
        drawLine(col, Offset(x, base), Offset(x, base - t), strokeWidth = 6f, cap = StrokeCap.Round)
        drawLine(col, Offset(x, base - t * 0.55f), Offset(x + t * 0.35f, base - t * 0.95f), strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(col, Offset(x, base - t * 0.4f), Offset(x - t * 0.3f, base - t * 0.8f), strokeWidth = 4f, cap = StrokeCap.Round)
    }
    layer(0.85f) {
        hills(0.9f, 0.025f, 1.0f, 1.0f, c(0xFF8D5A3B))
        fun pumpkin(x: Float, r: Float) {
            val cx = size.width * x; val cy = size.height * 0.9f; val rr = size.height * r
            drawOval(c(0xFFF57C00), Offset(cx - rr * 1.2f, cy - rr), Size(rr * 2.4f, rr * 2f))
            drawOval(c(0xFFFF9800), Offset(cx - rr * 0.5f, cy - rr), Size(rr, rr * 2f))
            drawRect(c(0xFF558B2F), Offset(cx - rr * 0.1f, cy - rr * 1.35f), Size(rr * 0.2f, rr * 0.4f))
        }
        pumpkin(0.6f, 0.09f); pumpkin(0.72f, 0.065f); pumpkin(0.4f, 0.055f)
    }
}

private fun DrawScope.november(c: Ink, layer: Layer) {
    sky(c(0xFFC5CFDB), c(0xFFE8ECF1))
    birds(0.62f, 0.22f, 5, c(0xFF607D8B))
    layer(0.15f) { mountains(0.55f, listOf(0.15f to 0.38f, 0.4f to 0.3f, 0.72f to 0.42f), c(0xFFAAB8C8)) }
    layer(0.35f) { mountains(0.68f, listOf(0.05f to 0.3f, 0.3f to 0.35f, 0.58f to 0.28f, 0.9f to 0.36f), c(0xFF8FA1B5)) }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, c(0xFFE8ECF1).copy(alpha = 0.7f), Color.Transparent),
        startY = size.height * 0.5f, endY = size.height * 0.85f), size = size)
    layer(0.75f) {
        hills(0.86f, 0.03f, 1.1f, 0.9f, c(0xFF6E8296))
        listOf(0.1f, 0.18f, 0.84f).forEach { pine(it, 0.92f, 0.36f, c(0xFF4A5F72)) }
    }
}

private fun DrawScope.december(c: Ink, layer: Layer) {
    sky(Color(if (c.dark) 0xFF0B1330 else 0xFF2B3F7A), Color(if (c.dark) 0xFF1C2A55 else 0xFF6B83C2))
    speckles(12, 26, Color.White.copy(alpha = 0.85f), 0.004f, 0.009f, 0f, 0.5f)
    drawCircle(Color(0xFFFFF8E1), size.height * 0.08f, Offset(size.width * 0.82f, size.height * 0.22f))
    layer(0.25f) { hills(0.66f, 0.04f, 1.0f, 0.5f, c(0xFFD7E3F4)) }
    layer(0.5f) {
        fun house(x: Float, w: Float, body: Long, roof: Long) {
            val bx = size.width * x; val base = size.height * 0.8f; val ww = size.height * w
            drawRect(c(body), Offset(bx, base - ww * 0.8f), Size(ww, ww * 0.8f))
            drawPath(Path().apply { moveTo(bx - ww * 0.12f, base - ww * 0.8f); lineTo(bx + ww / 2, base - ww * 1.35f); lineTo(bx + ww * 1.12f, base - ww * 0.8f); close() }, Color(0xFFF5F9FF))
            drawRect(Color(0xFFFFD54F), Offset(bx + ww * 0.18f, base - ww * 0.6f), Size(ww * 0.22f, ww * 0.22f))
            drawRect(Color(0xFFFFD54F), Offset(bx + ww * 0.6f, base - ww * 0.6f), Size(ww * 0.22f, ww * 0.22f))
            drawRect(c(roof), Offset(bx + ww * 0.4f, base - ww * 0.32f), Size(ww * 0.2f, ww * 0.32f))
        }
        hills(0.78f, 0.02f, 1.2f, 1.7f, c(0xFFE6EEF9))
        house(0.2f, 0.2f, 0xFFC62828, 0xFF5D4037); house(0.42f, 0.16f, 0xFF2E7D32, 0xFF5D4037)
    }
    layer(0.8f) {
        hills(0.9f, 0.02f, 0.8f, 0.3f, c(0xFFF7FAFF))
        pine(0.78f, 0.96f, 0.56f, c(0xFF1B5E20), Color(0xFFFFFFFF))
        val rnd = Random(21)
        repeat(9) {
            drawCircle(listOf(Color(0xFFFFD54F), Color(0xFFEF5350), Color(0xFF64B5F6))[it % 3], size.height * 0.012f,
                Offset(size.width * 0.78f + (rnd.nextFloat() - 0.5f) * size.height * 0.3f, size.height * (0.55f + rnd.nextFloat() * 0.35f)))
        }
    }
    speckles(13, 30, Color.White.copy(alpha = 0.9f), 0.006f, 0.014f)
}

// ---------------------------------------------------------------------------------------------
// Event pictures: a soft colored backdrop with the theme's symbol, behind the event's text.
// ---------------------------------------------------------------------------------------------

private class ThemeArt(val icon: ImageVector, val a: Long, val b: Long, val confetti: Boolean = false)

private fun artFor(t: EventTheme): ThemeArt = when (t) {
    EventTheme.BIRTHDAY -> ThemeArt(Icons.Outlined.Cake, 0xFFFFE0E9, 0xFFFFF3D1, confetti = true)
    EventTheme.ANNIVERSARY -> ThemeArt(Icons.Outlined.Favorite, 0xFFFCE4EC, 0xFFF8D7E8)
    EventTheme.CHRISTMAS -> ThemeArt(Icons.Outlined.Forest, 0xFFDDF0E0, 0xFFFDE3E1, confetti = true)
    EventTheme.NEW_YEAR -> ThemeArt(Icons.Outlined.Celebration, 0xFFE3E4FB, 0xFFFFF1CC, confetti = true)
    EventTheme.HALLOWEEN -> ThemeArt(Icons.Outlined.NightsStay, 0xFFFFE0C2, 0xFFE9DDF6)
    EventTheme.THANKSGIVING -> ThemeArt(Icons.Outlined.Restaurant, 0xFFFFE6CC, 0xFFF7EBD3)
    EventTheme.EASTER -> ThemeArt(Icons.Outlined.EggAlt, 0xFFE8F5D8, 0xFFFDF0D5, confetti = true)
    EventTheme.FLIGHT -> ThemeArt(Icons.Outlined.Flight, 0xFFD6ECFB, 0xFFEFF7FD)
    EventTheme.TRAIN -> ThemeArt(Icons.Outlined.Train, 0xFFE0E7F1, 0xFFF1F5FA)
    EventTheme.TRIP -> ThemeArt(Icons.Outlined.Luggage, 0xFFD9F0EC, 0xFFFFF1D6)
    EventTheme.BEACH -> ThemeArt(Icons.Outlined.BeachAccess, 0xFFCFEFFA, 0xFFFFF0CC)
    EventTheme.CAMPING -> ThemeArt(Icons.Outlined.Festival, 0xFFDDEFD9, 0xFFF1F0D8)
    EventTheme.PET -> ThemeArt(Icons.Outlined.Pets, 0xFFF3E7DA, 0xFFFDF4E8)
    EventTheme.HIKE -> ThemeArt(Icons.Outlined.Hiking, 0xFFDCEFD8, 0xFFEFF5E1)
    EventTheme.BREAKFAST -> ThemeArt(Icons.Outlined.BreakfastDining, 0xFFFFF0D2, 0xFFFDE7D7)
    EventTheme.COFFEE -> ThemeArt(Icons.Outlined.LocalCafe, 0xFFF1E3D6, 0xFFFAF2EA)
    EventTheme.LUNCH -> ThemeArt(Icons.Outlined.LunchDining, 0xFFFFF0D6, 0xFFE8F4DC)
    EventTheme.DINNER -> ThemeArt(Icons.Outlined.Restaurant, 0xFFFDE2D5, 0xFFFFF0DC)
    EventTheme.DRINKS -> ThemeArt(Icons.Outlined.LocalBar, 0xFFEADFF6, 0xFFFDE5EF)
    EventTheme.PARTY -> ThemeArt(Icons.Outlined.Celebration, 0xFFFDE3F0, 0xFFE5E8FD, confetti = true)
    EventTheme.GYM -> ThemeArt(Icons.Outlined.FitnessCenter, 0xFFE0E9F5, 0xFFF4E9F5)
    EventTheme.YOGA -> ThemeArt(Icons.Outlined.SelfImprovement, 0xFFE8E4F6, 0xFFE2F3EE)
    EventTheme.RUN -> ThemeArt(Icons.AutoMirrored.Outlined.DirectionsRun, 0xFFE2F2E2, 0xFFFFF2D9)
    EventTheme.SWIM -> ThemeArt(Icons.Outlined.Pool, 0xFFD3EEFA, 0xFFE3F6F3)
    EventTheme.BIKE -> ThemeArt(Icons.AutoMirrored.Outlined.DirectionsBike, 0xFFE3F1DD, 0xFFFFF0D9)
    EventTheme.SOCCER -> ThemeArt(Icons.Outlined.SportsSoccer, 0xFFDCF0D8, 0xFFF2F7EC)
    EventTheme.BASKETBALL -> ThemeArt(Icons.Outlined.SportsBasketball, 0xFFFFE5D1, 0xFFFFF2E4)
    EventTheme.TENNIS -> ThemeArt(Icons.Outlined.SportsTennis, 0xFFEFF6CF, 0xFFE2F2E4)
    EventTheme.GOLF -> ThemeArt(Icons.Outlined.GolfCourse, 0xFFDDF1DA, 0xFFEAF6F8)
    EventTheme.SKI -> ThemeArt(Icons.Outlined.DownhillSkiing, 0xFFE3EFFA, 0xFFF6FAFE)
    EventTheme.DOCTOR -> ThemeArt(Icons.Outlined.LocalHospital, 0xFFDDF0F3, 0xFFF0F7F8)
    EventTheme.DENTIST -> ThemeArt(Icons.Outlined.MedicalServices, 0xFFDDF0F6, 0xFFF3F8FB)
    EventTheme.HAIRCUT -> ThemeArt(Icons.Outlined.ContentCut, 0xFFF4E3F1, 0xFFFBF1F6)
    EventTheme.MOVIE -> ThemeArt(Icons.Outlined.Movie, 0xFFE4E2F2, 0xFFF8E6E6)
    EventTheme.CONCERT -> ThemeArt(Icons.Outlined.MusicNote, 0xFFE9E0F8, 0xFFFFE6EE)
    EventTheme.THEATRE -> ThemeArt(Icons.Outlined.TheaterComedy, 0xFFF8E1E1, 0xFFF4EBD9)
    EventTheme.SHOPPING -> ThemeArt(Icons.Outlined.ShoppingCart, 0xFFE6F2DD, 0xFFFFF0E0)
    EventTheme.STUDY -> ThemeArt(Icons.Outlined.School, 0xFFE2E9F7, 0xFFFFF4D9)
    EventTheme.READING -> ThemeArt(Icons.Outlined.AutoStories, 0xFFF3EAD9, 0xFFEAF0F6)
    EventTheme.CLEANING -> ThemeArt(Icons.Outlined.CleaningServices, 0xFFE0F2F1, 0xFFF1F7FD)
    EventTheme.GARDEN -> ThemeArt(Icons.Outlined.Yard, 0xFFDDF1D6, 0xFFF5F6DA)
    EventTheme.CAR -> ThemeArt(Icons.Outlined.DirectionsCar, 0xFFE3E8EE, 0xFFF3F5F8)
    EventTheme.BILLS -> ThemeArt(Icons.Outlined.Payments, 0xFFE2F1E3, 0xFFF4F7EA)
    EventTheme.CODE -> ThemeArt(Icons.Outlined.Code, 0xFFE3E7F3, 0xFFEDF3EE)
}

/**
 * The backdrop for an event card with a picture theme. [accent] is the event's color, used for
 * the symbol so the card still says which calendar it belongs to.
 */
@Composable
fun EventArt(theme: EventTheme, accent: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val art = remember(theme) { artFor(theme) }
    val night = Color(0xFF1B1F27)
    val a = if (dark) lerpColor(Color(art.a), night, 0.78f) else Color(art.a)
    val b = if (dark) lerpColor(Color(art.b), night, 0.82f) else Color(art.b)
    val ink = if (dark) lerpColor(accent, Color.White, 0.25f) else accent
    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset(size.width, size.height)))
            // Soft rounded shapes, like paper cut-outs.
            drawCircle(ink.copy(alpha = 0.10f), size.height * 0.9f, Offset(size.width * 0.92f, size.height * 1.05f))
            drawCircle(ink.copy(alpha = 0.07f), size.height * 0.55f, Offset(size.width * 0.7f, -size.height * 0.15f))
            if (art.confetti) {
                val rnd = Random(theme.ordinal)
                val bits = listOf(Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFAB47BC))
                repeat(16) {
                    val x = size.width * (0.45f + rnd.nextFloat() * 0.55f); val y = size.height * rnd.nextFloat()
                    drawRect(bits[it % bits.size].copy(alpha = if (dark) 0.55f else 0.75f), Offset(x, y), Size(size.height * 0.05f, size.height * 0.025f))
                }
            }
        }
        val big = maxHeight * 0.78f
        Icon(art.icon, null, tint = ink.copy(alpha = 0.85f),
            modifier = Modifier.size(big).offset(x = maxWidth - big - 18.dp, y = (maxHeight - big) / 2).rotate(-12f))
        Icon(art.icon, null, tint = ink.copy(alpha = 0.28f),
            modifier = Modifier.size(big * 0.42f).offset(x = maxWidth - big * 1.55f - 18.dp, y = maxHeight * 0.08f).rotate(14f))
    }
}
