package com.echotimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echotimer.ui.theme.LocalEchoColors

/** 图标集合。全部用 Canvas 手绘，避免为几个图标引入图标库。 */
enum class Glyph {
    PLAY,
    PAUSE,
    STOP,
    FLAG,
    RESET,
    TIMER,
    HISTORY,
    SETTINGS,
    INFO,
    TROPHY,
    CROWN,
    CLEAVER,
    MIC,
    TRASH,
    EDIT,
    CHECK,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalEchoColors.current.textPrimary,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.085f, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.PLAY -> drawPlay(tint)
            Glyph.PAUSE -> drawPause(tint)
            Glyph.STOP -> drawStop(tint)
            Glyph.FLAG -> drawFlag(tint, stroke)
            Glyph.RESET -> drawReset(tint, stroke)
            Glyph.TIMER -> drawClock(tint, stroke, withArrow = false)
            Glyph.HISTORY -> drawClock(tint, stroke, withArrow = true)
            Glyph.SETTINGS -> drawSliders(tint, stroke)
            Glyph.INFO -> drawInfo(tint, stroke)
            Glyph.TROPHY -> drawTrophy(tint, stroke)
            Glyph.CROWN -> drawCrown(tint)
            Glyph.CLEAVER -> drawCleaver(tint, stroke)
            Glyph.MIC -> drawMic(tint, stroke)
            Glyph.TRASH -> drawTrash(tint, stroke)
            Glyph.EDIT -> drawPencil(tint, stroke)
            Glyph.CHECK -> drawCheck(tint, stroke)
        }
    }
}

private fun DrawScope.px(x: Float, y: Float): Offset = Offset(size.width * x, size.height * y)

private fun DrawScope.drawPlay(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.34f, size.height * 0.22f)
        lineTo(size.width * 0.80f, size.height * 0.50f)
        lineTo(size.width * 0.34f, size.height * 0.78f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPause(color: Color) {
    val barWidth = size.width * 0.16f
    val top = size.height * 0.24f
    val height = size.height * 0.52f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.30f, top),
        size = Size(barWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.35f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.54f, top),
        size = Size(barWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.35f),
    )
}

private fun DrawScope.drawStop(color: Color) {
    val inset = size.width * 0.27f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.10f),
    )
}

private fun DrawScope.drawFlag(color: Color, stroke: Stroke) {
    drawLine(color, px(0.34f, 0.18f), px(0.34f, 0.84f), stroke.width, StrokeCap.Round)
    val path = Path().apply {
        moveTo(size.width * 0.34f, size.height * 0.22f)
        lineTo(size.width * 0.76f, size.height * 0.34f)
        lineTo(size.width * 0.34f, size.height * 0.48f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawReset(color: Color, stroke: Stroke) {
    drawArc(
        color = color,
        startAngle = 40f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
        size = Size(size.width * 0.6f, size.height * 0.6f),
        style = stroke,
    )
    val tip = Path().apply {
        moveTo(size.width * 0.62f, size.height * 0.10f)
        lineTo(size.width * 0.78f, size.height * 0.26f)
        lineTo(size.width * 0.58f, size.height * 0.30f)
        close()
    }
    drawPath(tip, color)
}

private fun DrawScope.drawClock(color: Color, stroke: Stroke, withArrow: Boolean) {
    drawCircle(
        color = color,
        radius = size.minDimension * 0.32f,
        center = center,
        style = stroke,
    )
    drawLine(
        color,
        center,
        px(0.5f, 0.28f),
        stroke.width,
        StrokeCap.Round,
    )
    if (withArrow) {
        drawLine(color, center, px(0.68f, 0.52f), stroke.width, StrokeCap.Round)
    } else {
        drawLine(color, center, px(0.66f, 0.60f), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawSliders(color: Color, stroke: Stroke) {
    val rows = listOf(0.28f to 0.34f, 0.5f to 0.66f, 0.72f to 0.44f)
    for ((y, knob) in rows) {
        drawLine(color, px(0.2f, y), px(0.8f, y), stroke.width, StrokeCap.Round)
        drawCircle(color = color, radius = size.minDimension * 0.075f, center = px(knob, y))
    }
}

private fun DrawScope.drawInfo(color: Color, stroke: Stroke) {
    drawCircle(color = color, radius = size.minDimension * 0.32f, center = center, style = stroke)
    drawCircle(color = color, radius = size.minDimension * 0.045f, center = px(0.5f, 0.35f))
    drawLine(color, px(0.5f, 0.47f), px(0.5f, 0.68f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawTrophy(color: Color, stroke: Stroke) {
    val cup = Path().apply {
        moveTo(size.width * 0.32f, size.height * 0.22f)
        lineTo(size.width * 0.68f, size.height * 0.22f)
        lineTo(size.width * 0.63f, size.height * 0.50f)
        quadraticTo(
            size.width * 0.50f,
            size.height * 0.66f,
            size.width * 0.37f,
            size.height * 0.50f,
        )
        close()
    }
    drawPath(cup, color, style = stroke)
    drawLine(color, px(0.50f, 0.62f), px(0.50f, 0.78f), stroke.width, StrokeCap.Round)
    drawLine(color, px(0.36f, 0.82f), px(0.64f, 0.82f), stroke.width, StrokeCap.Round)
    drawArc(
        color = color,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.18f, size.height * 0.24f),
        size = Size(size.width * 0.14f, size.height * 0.20f),
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = 270f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.68f, size.height * 0.24f),
        size = Size(size.width * 0.14f, size.height * 0.20f),
        style = stroke,
    )
}

/** 皇冠：第一名标记。 */
private fun DrawScope.drawCrown(color: Color) {
    val crown = Path().apply {
        moveTo(size.width * 0.16f, size.height * 0.72f)
        lineTo(size.width * 0.13f, size.height * 0.32f)
        lineTo(size.width * 0.34f, size.height * 0.48f)
        lineTo(size.width * 0.50f, size.height * 0.22f)
        lineTo(size.width * 0.66f, size.height * 0.48f)
        lineTo(size.width * 0.87f, size.height * 0.32f)
        lineTo(size.width * 0.84f, size.height * 0.72f)
        close()
    }
    drawPath(crown, color)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.18f, size.height * 0.76f),
        size = Size(size.width * 0.64f, size.height * 0.10f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.05f),
    )
}

/** 菜刀：最后一名标记。 */
private fun DrawScope.drawCleaver(color: Color, stroke: Stroke) {
    val blade = Path().apply {
        moveTo(size.width * 0.14f, size.height * 0.34f)
        lineTo(size.width * 0.60f, size.height * 0.28f)
        lineTo(size.width * 0.60f, size.height * 0.62f)
        lineTo(size.width * 0.14f, size.height * 0.56f)
        close()
    }
    drawPath(blade, color)
    drawLine(
        color = color,
        start = Offset(size.width * 0.60f, size.height * 0.45f),
        end = Offset(size.width * 0.86f, size.height * 0.45f),
        strokeWidth = stroke.width * 1.4f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawMic(color: Color, stroke: Stroke) {
    val capsuleWidth = size.width * 0.26f
    drawRoundRect(
        color = color,
        topLeft = Offset((size.width - capsuleWidth) / 2f, size.height * 0.14f),
        size = Size(capsuleWidth, size.height * 0.44f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleWidth / 2f),
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.24f, size.height * 0.30f),
        size = Size(size.width * 0.52f, size.height * 0.42f),
        style = stroke,
    )
    drawLine(color, px(0.5f, 0.72f), px(0.5f, 0.86f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawTrash(color: Color, stroke: Stroke) {
    drawLine(color, px(0.24f, 0.30f), px(0.76f, 0.30f), stroke.width, StrokeCap.Round)
    drawLine(color, px(0.42f, 0.20f), px(0.58f, 0.20f), stroke.width, StrokeCap.Round)
    val body = Path().apply {
        moveTo(size.width * 0.30f, size.height * 0.32f)
        lineTo(size.width * 0.35f, size.height * 0.82f)
        lineTo(size.width * 0.65f, size.height * 0.82f)
        lineTo(size.width * 0.70f, size.height * 0.32f)
    }
    drawPath(body, color, style = stroke)
}

private fun DrawScope.drawPencil(color: Color, stroke: Stroke) {
    drawLine(color, px(0.26f, 0.74f), px(0.70f, 0.30f), stroke.width, StrokeCap.Round)
    drawLine(color, px(0.64f, 0.24f), px(0.76f, 0.36f), stroke.width, StrokeCap.Round)
    drawLine(color, px(0.26f, 0.74f), px(0.22f, 0.82f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawCheck(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(size.width * 0.24f, size.height * 0.52f)
        lineTo(size.width * 0.44f, size.height * 0.72f)
        lineTo(size.width * 0.78f, size.height * 0.30f)
    }
    drawPath(path, color, style = stroke)
}
