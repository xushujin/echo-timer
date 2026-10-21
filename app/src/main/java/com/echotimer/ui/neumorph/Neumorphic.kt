package com.echotimer.ui.neumorph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echotimer.ui.theme.EchoColors
import com.echotimer.ui.theme.LocalEchoColors

/**
 * 拟态（Neumorphism）外观：同色系的双向柔和阴影 + 微渐变表面。
 *
 * 自研原因：主流拟态库都依赖 `Modifier.shadow` / RenderEffect（API 31+ 才有真实模糊），
 * 这里用"多层递减 alpha 的圆角矩形"叠加模拟软阴影，API 26 起表现一致，也不引入任何第三方依赖。
 *
 * 实现约定：
 * - 阴影只画在节点边界内（表面矩形内缩 depth/2），不会溢出影响相邻布局；
 * - [pressed] 为 true 时阴影内翻、渐变反向，形成"按下去"的凹陷感。
 */
fun Modifier.neumorphicFace(
    colors: EchoColors,
    cornerRadius: Dp,
    circle: Boolean,
    pressed: Boolean,
    depth: Dp,
    faceColor: Color = colors.surface,
): Modifier = drawBehind {
    val depthPx = depth.toPx()
    val inset = depthPx * 0.5f
    if (size.width <= depthPx * 2f || size.height <= depthPx * 2f) return@drawBehind

    val face = Rect(inset, inset, size.width - inset, size.height - inset)
    val radius = if (circle) minOf(face.width, face.height) / 2f else cornerRadius.toPx()
    val spread = if (pressed) inset * 0.5f else inset * 1.35f
    val shift = if (pressed) -inset * 0.30f else inset * 0.70f

    // 暗影在右下、亮影在左上：两者必须在同一圈层里，才像"从背景里挤出来"。
    drawSoftShadow(colors.shadowDark, face, radius, Offset(shift, shift), spread, strength = 0.95f)
    drawSoftShadow(colors.shadowLight, face, radius, Offset(-shift, -shift), spread, strength = 1.0f)

    val top = if (pressed) {
        lerp(faceColor, colors.shadowDark, 0.18f)
    } else {
        lerp(faceColor, colors.shadowLight, 0.22f)
    }
    val bottom = if (pressed) {
        lerp(faceColor, colors.shadowLight, 0.25f)
    } else {
        lerp(faceColor, colors.shadowDark, 0.06f)
    }
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(top, bottom),
            start = face.topLeft,
            end = face.bottomRight,
        ),
        topLeft = face.topLeft,
        size = face.size,
        cornerRadius = CornerRadius(radius),
    )

    // 内边缘一圈 1dp 的明暗过渡：拟态的"立体感"主要靠这条边，没有它整体会发平。
    val rim = 1.dp.toPx()
    if (face.width > rim * 2 && face.height > rim * 2) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    if (pressed) colors.shadowDark.copy(alpha = 0.30f) else colors.shadowLight.copy(alpha = 0.85f),
                    Color.Transparent,
                    if (pressed) colors.shadowLight.copy(alpha = 0.40f) else colors.shadowDark.copy(alpha = 0.30f),
                ),
                start = face.topLeft,
                end = face.bottomRight,
            ),
            topLeft = Offset(face.left + rim / 2f, face.top + rim / 2f),
            size = Size(face.width - rim, face.height - rim),
            cornerRadius = CornerRadius(radius - rim / 2f),
            style = Stroke(width = rim),
        )
    }
}

/**
 * 分层叠加模拟高斯软阴影。层数固定，性能稳定；单层 alpha 很低，叠加后边缘自然衰减。
 */
private fun DrawScope.drawSoftShadow(
    color: Color,
    face: Rect,
    radius: Float,
    offset: Offset,
    spread: Float,
    strength: Float,
    layers: Int = 8,
) {
    if (spread <= 0f) return
    for (i in 0 until layers) {
        val t = (i + 1) / layers.toFloat()
        // 平方衰减近似高斯：靠内浓、靠外迅速变淡，比线性衰减更像真实阴影
        val falloff = (1f - t) * (1f - t)
        val grow = spread * (1f - t)
        val alpha = strength * falloff / layers * 3.2f
        if (alpha <= 0.002f) continue

        val dx = offset.x * t
        val dy = offset.y * t
        val rect = Rect(
            left = face.left - grow + dx,
            top = face.top - grow + dy,
            right = face.right + grow + dx,
            bottom = face.bottom + grow + dy,
        )
        drawRoundRect(
            color = color.copy(alpha = color.alpha * alpha),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(radius + grow),
        )
    }
}

/** 拟态容器：卡片、按钮底座、表盘都用它。 */
@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    circle: Boolean = false,
    pressed: Boolean = false,
    depth: Dp = 14.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    faceColor: Color? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalEchoColors.current
    val shape = if (circle) CircleShape else RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .neumorphicFace(colors, cornerRadius, circle, pressed, depth, faceColor ?: colors.surface)
            .padding(depth / 2)
            .clip(shape)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}
