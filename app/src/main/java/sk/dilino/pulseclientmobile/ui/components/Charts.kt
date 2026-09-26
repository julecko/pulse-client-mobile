package sk.dilino.pulseclientmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.dilino.pulseclientmobile.ui.theme.PulseColors

/** Horizontal usage bar: track + fill, square ends. */
@Composable
fun Meter(percent: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier = modifier.height(height).background(PulseColors.Track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                .background(color)
        )
    }
}

class Series(val values: List<Float>, val color: Color, val dashed: Boolean = false, val width: Dp = 1.6.dp)

private fun linePath(values: List<Float>, w: Float, h: Float, pad: Float, max: Float): Path {
    val path = Path()
    if (values.isEmpty()) return path
    val step = if (values.size > 1) w / (values.size - 1) else 0f
    values.forEachIndexed { i, v ->
        val x = i * step
        val y = h - pad - (v / max).coerceIn(0f, 1f) * (h - pad * 2)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/** Polyline chart on a 0..[max] scale with an optional baseline; several series can share the canvas. */
@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    max: Float = 100f,
    baseline: Boolean = false
) {
    Canvas(modifier) {
        if (baseline) {
            drawLine(PulseColors.BorderSubtle, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
        }
        series.forEach { s ->
            drawPath(
                path = linePath(s.values, size.width, size.height, 2f, max),
                color = s.color,
                style = Stroke(
                    width = s.width.toPx(),
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(9f, 9f)) else null
                )
            )
        }
    }
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    LineChart(listOf(Series(values, color, width = 1.5.dp)), modifier, baseline = true)
}
