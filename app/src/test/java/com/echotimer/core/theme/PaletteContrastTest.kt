package com.echotimer.core.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色可读性校验：拟态风格背景与控件面色差很小，文字对比度必须靠配色本身保证。
 * 阈值采用 WCAG 2.1：正文 4.5:1，强调色文字 4.5:1。
 */
class PaletteContrastTest {

    private fun luminance(color: Long): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel(((color shr 16) and 0xFF).toInt())
        val g = channel(((color shr 8) and 0xFF).toInt())
        val b = channel((color and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun paletteIdsAreUniqueAndComplete() {
        val ids = Palettes.ids()
        assertTrue("配色 id 必须唯一", ids.size == ids.distinct().size)
        assertTrue("至少提供三套配色", ids.size >= 3)
        assertTrue(ids.contains(Palettes.DEFAULT_ID))
    }

    @Test
    fun everyPaletteMeetsContrastRequirements() {
        for (palette in Palettes.ALL) {
            for ((variantName, colors) in listOf("light" to palette.light, "dark" to palette.dark)) {
                val label = "${palette.id}.$variantName"
                assertTrue(
                    "$label 正文与背景对比度不足: ${contrast(colors.textPrimary, colors.background)}",
                    contrast(colors.textPrimary, colors.background) >= 4.5,
                )
                assertTrue(
                    "$label 正文与卡片对比度不足: ${contrast(colors.textPrimary, colors.surface)}",
                    contrast(colors.textPrimary, colors.surface) >= 4.5,
                )
                assertTrue(
                    "$label 次要文字对比度不足: ${contrast(colors.textSecondary, colors.background)}",
                    contrast(colors.textSecondary, colors.background) >= 4.5,
                )
                assertTrue(
                    "$label 次要文字与卡片对比度不足: ${contrast(colors.textSecondary, colors.surface)}",
                    contrast(colors.textSecondary, colors.surface) >= 4.5,
                )
                assertTrue(
                    "$label 强调色上的文字对比度不足: ${contrast(colors.onAccent, colors.accent)}",
                    contrast(colors.onAccent, colors.accent) >= 4.5,
                )
                assertTrue(
                    "$label 强调色文字对比度不足: ${contrast(colors.accent, colors.background)}",
                    contrast(colors.accent, colors.background) >= 4.5,
                )
            }
        }
    }

    @Test
    fun shadowsSitOnOppositeSidesOfTheSurface() {
        for (palette in Palettes.ALL) {
            listOf("light" to palette.light, "dark" to palette.dark).forEach { (variantName, colors) ->
                val lightIsLighter = luminance(colors.shadowLight) > luminance(colors.surface)
                val darkIsDarker = luminance(colors.shadowDark) < luminance(colors.surface)
                assertTrue("${palette.id}.$variantName 高光应比面色更亮", lightIsLighter)
                assertTrue("${palette.id}.$variantName 暗影应比面色更暗", darkIsDarker)
            }
        }
    }
}
