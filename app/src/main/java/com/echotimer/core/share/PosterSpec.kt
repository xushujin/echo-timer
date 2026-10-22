package com.echotimer.core.share

/** 榜单图片里姓名后面跟的名次标记。 */
enum class PosterMark { CROWN, CLEAVER, NONE }

/** 榜单图片里的一行成绩。 */
data class PosterRow(
    val rank: Int,
    val name: String,
    val time: String,
    val date: String,
    val mark: PosterMark,
)

/** 绘制用的配色（0xAARRGGBB），由当前主题转换而来，保证图片与界面同色系。 */
data class PosterPalette(
    val background: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val onAccent: Int,
    val shadowLight: Int,
    val shadowDark: Int,
)

/** 一张榜单分享图的全部内容。 */
data class PosterSpec(
    val title: String,
    val subtitle: String,
    val rows: List<PosterRow>,
    val footer: String,
    /** 行数被截断时的说明，例如「仅展示前 40 名（共 128 人）」。 */
    val note: String?,
    val palette: PosterPalette,
)

/**
 * 榜单图片的版面常量与尺寸计算（纯算术，便于单元测试）。
 *
 * 尺寸固定按 1080 宽设计，行高与页眉页脚都是常量，因此图片高度只跟行数有关。
 */
object PosterLayout {

    const val WIDTH = 1080

    const val PADDING = 72f
    const val HEADER = 300f
    const val ROW = 128f
    const val FOOTER = 150f

    /**
     * 单张图最多画多少行。
     * 40 行时高度约 5700px（1080 × 5700 × 4B ≈ 24MB），再大就有 OOM 风险。
     */
    const val MAX_ROWS = 40

    fun heightFor(rowCount: Int): Int = (HEADER + ROW * rowCount.coerceAtLeast(0) + FOOTER).toInt()

    /** 实际绘制的行数（超过上限时截断）。 */
    fun visibleRows(total: Int): Int = total.coerceIn(0, MAX_ROWS)
}
