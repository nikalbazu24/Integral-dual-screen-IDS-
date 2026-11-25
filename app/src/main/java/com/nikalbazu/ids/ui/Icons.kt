package com.nikalbazu.ids.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Íconos de línea (cuadro de 24×24), los mismos del boceto. */
object Ico {
    const val INICIO = "M3 11 12 3l9 8M5 9.5V21h14V9.5M10 21v-6h4v6"
    const val CONTROL = "M6 8h12a4 4 0 0 1 4 4v1a3.5 3.5 0 0 1-6.3 2.1L14.5 14h-5l-1.2 1.1A3.5 3.5 0 0 1 2 13v-1a4 4 0 0 1 4-4zM7 10v4M5 12h4M16 11h.01M18 13h.01"
    const val CT = "M7 7h10v10H7zM10 10h4v4h-4zM10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4"
    const val HW = "M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0M12 10c0-4 1-7 4-7 2 0 2 3 0 5l-2 2M14 12c4 0 7 1 7 4 0 2-3 2-5 0l-2-2M12 14c0 4-1 7-4 7-2 0-2-3 0-5l2-2M10 12c-4 0-7-1-7-4 0-2 3-2 5 0l2 2"
    const val JUEGOS = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
    const val HERR = "M3 8h18v12H3zM8 8V5h8v3M3 13h18M11 12h2v2h-2z"
    const val PERS = "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-2 0-3 2-1 3-1 4-1 4-4a9 9 0 0 0-9-8zM7.5 11h.01M9.5 7.5h.01M14.5 7.5h.01M17 11h.01"
    const val POWER = "M9 2v5M15 2v5M6 7h12v4a6 6 0 0 1-12 0zM12 17v5"
    const val LAND = "M4.5 7h15A2.5 2.5 0 0 1 22 9.5v6a2.5 2.5 0 0 1-2.5 2.5h-15A2.5 2.5 0 0 1 2 15.5v-6A2.5 2.5 0 0 1 4.5 7zM6 4.5c2-1.5 4.5-2 7-1.3M13 1.5l.5 1.8-1.8.5"
    const val AIM = "M5 12a7 7 0 1 0 14 0a7 7 0 1 0-14 0M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0M12 2v4M12 18v4M2 12h4M18 12h4"
    const val ROTATE = "M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5"
    const val RESIZE = "M20 10v10H10M20 20 9 9"
    const val RAM = "M3 7h18v10H3zM7 7v10M11 7v10M15 7v10M5 17v3M19 17v3"
}

@Composable
fun Icon(d: String, color: Color, size: Dp = 26.dp, stroke: Float = 2.2f) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.minDimension / 24f, Offset.Zero) {
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
