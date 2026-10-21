package com.echotimer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors

/** 圆形拟态按钮，用于主操作与次要操作。 */
@Composable
fun NeumorphicIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 66.dp,
    depth: Dp = 14.dp,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val colors = LocalEchoColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val face = when {
        !enabled -> colors.surfaceAlt
        accent -> colors.accent
        else -> colors.surface
    }
    val tint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.45f)
        accent -> colors.onAccent
        else -> colors.textPrimary
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        NeumorphicSurface(
            modifier = Modifier
                .matchParentSize()
                .semantics { this.contentDescription = contentDescription }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            circle = true,
            pressed = pressed && enabled,
            depth = depth,
            faceColor = face,
        ) {}
        GlyphIcon(
            glyph = glyph,
            size = size * 0.34f,
            tint = tint,
        )
    }
}

/**
 * 胶囊形选择块。
 *
 * [horizontalPadding] / [fontSize] 可调是为了让「跟随系统」这类四字标签
 * 在三分之一宽度的分段选择器里也能完整显示（默认值用于普通按钮场景）。
 */
@Composable
fun NeumorphicChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    fontSize: TextUnit = 14.sp,
) {
    val colors = LocalEchoColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    NeumorphicSurface(
        modifier = modifier
            .height(40.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        cornerRadius = 20.dp,
        pressed = pressed && enabled,
        depth = 8.dp,
        faceColor = if (selected) colors.accent else colors.surface,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            color = when {
                !enabled -> colors.textSecondary.copy(alpha = 0.4f)
                selected -> colors.onAccent
                else -> colors.textSecondary
            },
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 拟态卡片。 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    depth: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    NeumorphicSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        depth = depth,
        contentPadding = contentPadding,
    ) {
        Column(content = content)
    }
}

/** 小节标题。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalEchoColors.current.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
    )
}

/** 自绘开关，保持拟态风格一致（不使用 Material Switch）。 */
@Composable
fun NeumorphicToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.surfaceAlt,
        label = "toggleTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        label = "toggleKnob",
    )
    // 用 lambda 版本的 offset：位移在布局阶段读取，开关切换不会引起额外重组。
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 28.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .clip(CircleShape),
    ) {
        NeumorphicSurface(
            modifier = Modifier.matchParentSize(),
            circle = true,
            depth = 6.dp,
            faceColor = trackColor,
        ) {}
        Box(
            modifier = Modifier
                .padding(3.dp)
                .offset { IntOffset(knobOffset.roundToPx(), 0) }
                .size(22.dp)
                .clip(CircleShape),
        ) {
            NeumorphicSurface(
                modifier = Modifier.matchParentSize(),
                circle = true,
                depth = 5.dp,
                faceColor = if (checked) colors.onAccent else colors.surface,
            ) {}
        }
    }
}

/** 设置行：左标题/说明 + 右控件。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: @Composable (() -> Unit)? = null,
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        if (control != null) {
            Spacer(Modifier.width(12.dp))
            control()
        }
    }
}

/** 两/三段分段选择器。 */
@Composable
fun NeumorphicSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            NeumorphicChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                horizontalPadding = 8.dp,
                fontSize = 13.sp,
            )
        }
    }
}

/** 强调色小圆点，用于状态提示。 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
