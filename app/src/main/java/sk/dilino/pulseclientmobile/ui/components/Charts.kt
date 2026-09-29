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

/** One line of a [TimeChart]: (unix seconds, value) oldest first; a null value is a gap. */
class TimeSeries(val points: List<Pair<Long, Float?>>, val color: Color, val dashed: Boolean = false, val width: Dp = 1.6.dp)

/**
 * Lines over a time axis from [since] to [until] (unix seconds) on a 0..[max] scale, with faint quarter grid lines.
 * A line breaks where two points are more than [gapSecs] apart, so time the agent wasn't reporting shows as a gap
 * instead of a straight line across it.
 */
@Composable
fun TimeChart(
    series: List<TimeSeries>,
    since: Long,
    until: Long,
    gapSecs: Long,
    max: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 2f
        for (q in 1..3) {
            val y = h * q / 4f
            drawLine(PulseColors.BorderSubtle.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), 1f)
        }
        drawLine(PulseColors.BorderSubtle, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
        val span = (until - since).coerceAtLeast(1).toFloat()
        val scale = if (max > 0f) max else 1f
        series.forEach { s ->
            val path = Path()
            var prevAt: Long? = null
            s.points.forEach { (at, v) ->
                if (v == null) {
                    prevAt = null
                    return@forEach
                }
                val x = ((at - since) / span).coerceIn(0f, 1f) * w
                val y = h - pad - (v / scale).coerceIn(0f, 1f) * (h - pad * 2)
                val prev = prevAt
                if (prev == null || at - prev > gapSecs) path.moveTo(x, y) else path.lineTo(x, y)
                prevAt = at
            }
            drawPath(
                path = path,
                color = s.color,
                style = Stroke(
                    width = s.width.toPx(),
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(9f, 9f)) else null
                )
            )
        }
    }
}
