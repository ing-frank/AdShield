package com.adshield.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Logo dibujado con Canvas: escudo + símbolo de bloqueo + nodos de red.
 */
@Composable
fun AdShieldLogo(
    color: Color,
    innerColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            lineTo(w * 0.88f, h * 0.20f)
            lineTo(w * 0.88f, h * 0.52f)
            cubicTo(w * 0.88f, h * 0.76f, w * 0.70f, h * 0.90f, w * 0.50f, h * 0.96f)
            cubicTo(w * 0.30f, h * 0.90f, w * 0.12f, h * 0.76f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.20f)
            close()
        }
        drawPath(path = shield, color = color)

        val center = Offset(w * 0.50f, h * 0.50f)
        val radius = w * 0.19f
        val stroke = w * 0.06f

        // Nodos de red conectados al centro
        val nodes = listOf(
            Offset(w * 0.27f, h * 0.30f),
            Offset(w * 0.73f, h * 0.30f),
            Offset(w * 0.50f, h * 0.82f)
        )
        nodes.forEach { node ->
            drawLine(color = innerColor, start = center, end = node, strokeWidth = stroke * 0.4f)
            drawCircle(color = innerColor, radius = stroke * 0.6f, center = node)
        }

        // Símbolo de bloqueo
        drawCircle(color = color, radius = radius, center = center)
        drawCircle(color = innerColor, radius = radius, center = center, style = Stroke(width = stroke))
        val d = radius * 0.70f
        drawLine(
            color = innerColor,
            start = Offset(center.x - d, center.y - d),
            end = Offset(center.x + d, center.y + d),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
