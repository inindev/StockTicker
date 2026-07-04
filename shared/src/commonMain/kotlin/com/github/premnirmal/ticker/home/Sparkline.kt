package com.github.premnirmal.ticker.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.github.premnirmal.ticker.model.ChartData
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import kotlin.math.max
import kotlin.math.min

/** Cap on drawn points; 1-day/5-minute data (~78 candles) is downsampled to roughly this. */
private const val MaxPoints = 48

/**
 * The tiny 1-day price chart in a watchlist row (see reference/stocks.png): the close-price line
 * coloured by day direction with a soft gradient fill under it, over a dashed horizontal reference
 * line at the previous close. The y-range always includes the previous close so the line's position
 * relative to the dashes reads correctly.
 */
@Composable
fun Sparkline(
    chartData: ChartData,
    modifier: Modifier = Modifier,
) {
    val lineColour = SharedColours.pillColour(chartData.isUp, chartData.isDown)
    val baselineColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val points = chartData.dataPoints
        if (points.size < 2) return@Canvas

        val step = points.size / MaxPoints
        val sampled = if (step > 1) {
            points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
        } else {
            points
        }

        val xMin = sampled.first().xVal
        val xSpan = (sampled.last().xVal - xMin).takeIf { it > 0f } ?: 1f
        var yMin = chartData.chartPreviousClose
        var yMax = chartData.chartPreviousClose
        for (point in sampled) {
            yMin = min(yMin, point.closeVal)
            yMax = max(yMax, point.closeVal)
        }
        val yPadding = ((yMax - yMin) * 0.08f).takeIf { it > 0f } ?: 1f
        yMin -= yPadding
        yMax += yPadding
        val ySpan = yMax - yMin

        fun xFor(value: Float): Float = (value - xMin) / xSpan * size.width
        fun yFor(value: Float): Float = size.height - (value - yMin) / ySpan * size.height

        val baselineY = yFor(chartData.chartPreviousClose)
        drawLine(
            color = baselineColour,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx() * 0.75f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.5.dp.toPx())),
        )

        val line = Path()
        sampled.forEachIndexed { i, point ->
            val x = xFor(point.xVal)
            val y = yFor(point.closeVal)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        val fill = Path().apply {
            addPath(line)
            lineTo(xFor(sampled.last().xVal), size.height)
            lineTo(xFor(sampled.first().xVal), size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                0f to lineColour.copy(alpha = 0.28f),
                1f to Color.Transparent,
            ),
        )
        drawPath(
            path = line,
            color = lineColour,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}