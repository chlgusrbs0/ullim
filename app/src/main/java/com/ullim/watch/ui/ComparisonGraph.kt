package com.ullim.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ullim.watch.R
import com.ullim.watch.runtime.VisualizationPoint
import com.ullim.watch.runtime.VisualizationSnapshot

@Composable
internal fun ComparisonGraph(snapshot: VisualizationSnapshot, active: Boolean) {
    val latest = if (active) snapshot.points.lastOrNull() else null
    Text(stringResource(R.string.graph_input), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    GraphRow(snapshot, active, output = false)
    Text(stringResource(R.string.graph_output), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    GraphRow(snapshot, active, output = true)
    Text(stringResource(R.string.graph_time_axis), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    Text(stringResource(when {
        !active -> R.string.graph_stopped
        latest == null -> R.string.graph_waiting
        latest.rms == 0f && latest.commanded == 0f -> R.string.graph_silent
        else -> R.string.graph_source_time_notice
    }), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}

@Composable
private fun GraphRow(snapshot: VisualizationSnapshot, active: Boolean, output: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val baseline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
    val latest = if (active) snapshot.points.lastOrNull() else null
    val description = if (output) stringResource(R.string.graph_command_description, ((latest?.commanded ?: 0f) * 100).toInt())
        else stringResource(R.string.graph_input_description, ((latest?.rms ?: 0f) * 100).toInt(), ((latest?.envelope ?: 0f) * 100).toInt())
    Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = description }) {
        val bottom = size.height - 1.dp.toPx()
        val top = 2.dp.toPx()
        val span = (snapshot.endNs - snapshot.startNs).coerceAtLeast(1).toDouble()
        fun x(time: Long) = (((time - snapshot.startNs) / span).toFloat() * size.width).coerceIn(0f, size.width)
        fun y(value: Float) = bottom - value.coerceIn(0f, 1f) * (bottom - top)
        drawLine(baseline, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        if (!active) return@Canvas
        val rmsPath = Path()
        val envelopePath = Path()
        var previous: VisualizationPoint? = null
        for (point in snapshot.points) {
            if (output) {
                val left = x(point.timestampNs)
                val right = x(point.timestampNs + point.durationMs * 1_000_000L)
                val height = bottom - y(point.commanded)
                if (height > 0f && right > left) drawRect(primary.copy(alpha = .8f), Offset(left, bottom - height), Size(right - left, height))
            } else {
                val before = previous
                if (before != null && point.timestampNs - before.timestampNs == 20_000_000L) {
                    envelopePath.lineTo(x(point.timestampNs), y(point.envelope))
                    rmsPath.lineTo(x(point.timestampNs), y(point.rms))
                } else {
                    envelopePath.moveTo(x(point.timestampNs), y(point.envelope))
                    rmsPath.moveTo(x(point.timestampNs), y(point.rms))
                    drawCircle(primary, 1.dp.toPx(), Offset(x(point.timestampNs), y(point.rms)))
                }
            }
            previous = point
        }
        if (!output) {
            drawPath(envelopePath, secondary, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            drawPath(rmsPath, primary, style = Stroke(1.dp.toPx()))
        }
    }
}
