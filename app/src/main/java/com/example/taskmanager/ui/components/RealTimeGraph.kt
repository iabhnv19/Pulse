package com.example.taskmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RealTimeGraph(
    data: List<Float>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    maxPoints: Int = 60,
    isDashed: Boolean = false,
    fixedMaxData: Float? = null
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        val maxData = fixedMaxData ?: (data.maxOrNull()?.takeIf { it > 0 } ?: 1f)
        
        // Draw Grid
        val gridColor = color.copy(alpha = 0.1f)
        val horizontalLines = 10
        val verticalLines = 20
        
        for (i in 0..horizontalLines) {
            val y = i * (height / horizontalLines)
            drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
        }
        
        for (i in 0..verticalLines) {
            val x = i * (width / verticalLines)
            drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1.dp.toPx())
        }

        if (data.isEmpty()) return@Canvas
        
        val points = data.takeLast(maxPoints)
        val stepX = width / (maxPoints - 1)
        
        val path = Path().apply {
            points.forEachIndexed { index, value ->
                val x = width - (points.size - 1 - index) * stepX
                val y = height - (value / maxData * height)
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
        }
        
        // Draw Fill (Only if not dashed)
        if (points.size >= 2 && !isDashed) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(width - (points.size - 1) * stepX, height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
                )
            )
        }
        
        // Draw Line
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
            )
        )
    }
}
