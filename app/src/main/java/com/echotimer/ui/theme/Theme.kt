package com.echotimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.echotimer.core.settings.ThemeMode
import com.echotimer.core.theme.PaletteSpec

/** 主题色在 Compose 侧的表现形式。 */
@Immutable
data class EchoColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    /** 错误/警告提示色，与配色无关，保证在任意主题下都醒目。 */
    val danger: Color,
    val isDark: Boolean,
)

val LocalEchoColors = staticCompositionLocalOf<EchoColors> {
    error("EchoColors 未提供：请在 EchoTimerTheme 内使用")
}

/** 主题模式解析：SYSTEM 跟随系统，其余强制。 */
@Composable
fun resolveDarkMode(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 全局主题入口。整个应用只在根节点包一次，切换配色/明暗后所有界面同时生效。
 * MaterialTheme 仅用于对话框、文本样式等基础组件，视觉主体由拟态组件自绘。
 */
@Composable
fun EchoTimerTheme(
    palette: PaletteSpec,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = remember(palette, dark) { palette.colorsFor(dark) }
    val scheme = remember(colors) {
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.background,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceAlt,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.textSecondary,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.background,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceAlt,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.textSecondary,
            )
        }
    }
    CompositionLocalProvider(LocalEchoColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

private fun PaletteSpec.colorsFor(dark: Boolean): EchoColors {
    val source = if (dark) this.dark else this.light
    return EchoColors(
        background = Color(source.background),
        surface = Color(source.surface),
        surfaceAlt = Color(source.surfaceAlt),
        textPrimary = Color(source.textPrimary),
        textSecondary = Color(source.textSecondary),
        accent = Color(source.accent),
        onAccent = Color(source.onAccent),
        shadowLight = Color(source.shadowLight),
        shadowDark = Color(source.shadowDark),
        danger = if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E),
        isDark = dark,
    )
}
