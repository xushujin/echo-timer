package com.echotimer.ui.share

import androidx.compose.ui.graphics.toArgb
import com.echotimer.core.share.PosterPalette
import com.echotimer.ui.theme.EchoColors

/**
 * 把当前主题色转成绘制用的 ARGB。
 * 分享图与界面同色系，切换配色后导出的图也跟着变。
 */
fun EchoColors.toPosterPalette(): PosterPalette = PosterPalette(
    background = background.toArgb(),
    surface = surface.toArgb(),
    surfaceAlt = surfaceAlt.toArgb(),
    textPrimary = textPrimary.toArgb(),
    textSecondary = textSecondary.toArgb(),
    accent = accent.toArgb(),
    onAccent = onAccent.toArgb(),
    shadowLight = shadowLight.toArgb(),
    shadowDark = shadowDark.toArgb(),
)
