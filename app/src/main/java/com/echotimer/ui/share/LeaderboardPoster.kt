package com.echotimer.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.echotimer.core.share.PosterLayout
import com.echotimer.core.share.PosterMark
import com.echotimer.core.share.PosterRow
import com.echotimer.core.share.PosterSpec

/**
 * 把榜单画成一张可以直接发出去的图片。
 *
 * 为什么不用「截屏」：界面里的榜单是可滚动的，屏幕截图只能拿到可见的几行。
 * 这里按数据直接排版，行数完整（超过 [PosterLayout.MAX_ROWS] 才截断并注明），
 * 也不受当前滚动位置影响。
 *
 * 绘制全部走 android.graphics（不用 Compose 截图 API），好处是：
 * - 不依赖 View 是否已布局、是否可见；
 * - 尺寸完全由数据决定，长榜单也能一张图装下；
 * - 阴影、圆角、配色与 App 内的拟态卡片保持一致。
 */
object LeaderboardPoster {

    fun render(spec: PosterSpec): Bitmap {
        val rows = spec.rows.take(PosterLayout.MAX_ROWS)
        val width = PosterLayout.WIDTH
        val height = PosterLayout.heightFor(rows.size)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = spec.palette
        canvas.drawColor(palette.background)

        // 顶部一层很淡的强调色，避免整张图死平；alpha 极低，不影响文字对比度。
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height * 0.42f,
                intArrayOf(withAlpha(palette.accent, 0.12f), withAlpha(palette.accent, 0f)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)

        val pad = PosterLayout.PADDING
        val contentWidth = width - pad * 2

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 64f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(ellipsize(spec.title, titlePaint, contentWidth), pad, 148f, titlePaint)

        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = 34f
        }
        canvas.drawText(ellipsize(spec.subtitle, subtitlePaint, contentWidth), pad, 204f, subtitlePaint)

        // 页眉与列表之间一条极淡的分隔线，代替卡片边框。
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(palette.shadowDark, 0.35f)
            strokeWidth = 2f
        }
        canvas.drawLine(pad, 244f, width - pad, 244f, divider)

        var top = PosterLayout.HEADER
        rows.forEachIndexed { index, row ->
            drawRow(canvas, row, index == rows.lastIndex, top, palette)
            top += PosterLayout.ROW
        }

        drawFooter(canvas, spec, height, palette)
        return bitmap
    }

    private fun drawRow(
        canvas: Canvas,
        row: PosterRow,
        isLast: Boolean,
        top: Float,
        palette: com.echotimer.core.share.PosterPalette,
    ) {
        val pad = PosterLayout.PADDING
        val card = RectF(
            pad,
            top + 10f,
            PosterLayout.WIDTH - pad,
            top + PosterLayout.ROW - 10f,
        )
        val radius = 34f

        // 与界面同款的双向软阴影：暗影右下、亮影左上。
        drawSoftShadow(canvas, card, radius, withAlpha(palette.shadowDark, 0.55f), 6f, 6f, 14f)
        drawSoftShadow(canvas, card, radius, withAlpha(palette.shadowLight, 0.85f), -6f, -6f, 12f)

        // 最后一名整行标红（只有一人上榜时按第一名处理，不标红）。
        val lastPlace = isLast && row.rank > 1
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lastPlace) blend(palette.surface, palette.danger, 0.16f) else palette.surface
        }
        canvas.drawRoundRect(card, radius, radius, face)

        val centerY = card.centerY()
        val badgeRadius = 32f
        val badgeCx = pad + 44f
        val topThree = row.rank <= 3
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                lastPlace -> blend(palette.surface, palette.danger, 0.30f)
                topThree -> palette.accent
                else -> palette.surfaceAlt
            }
        }
        canvas.drawCircle(badgeCx, centerY, badgeRadius, badgePaint)

        val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                lastPlace -> palette.danger
                topThree -> palette.onAccent
                else -> palette.textSecondary
            }
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(row.rank.toString(), badgeCx, centerY - (rankPaint.ascent() + rankPaint.descent()) / 2f, rankPaint)

        val nameLeft = badgeCx + badgeRadius + 26f
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lastPlace) palette.danger else palette.textPrimary
            textSize = 40f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.MONOSPACE
        }
        val timeRight = PosterLayout.WIDTH - pad - 34f
        canvas.drawText(row.time, timeRight, centerY - (timePaint.ascent() + timePaint.descent()) / 2f, timePaint)

        val timeLeft = timeRight - timePaint.measureText(row.time) - 30f
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lastPlace) palette.danger else palette.textPrimary
            textSize = 36f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val markWidth = if (row.mark == PosterMark.NONE) 0f else 46f
        val nameMaxWidth = (timeLeft - nameLeft - markWidth).coerceAtLeast(60f)
        val name = ellipsize(row.name, namePaint, nameMaxWidth)
        canvas.drawText(name, nameLeft, centerY - 4f, namePaint)

        when (row.mark) {
            PosterMark.CROWN -> drawCrown(
                canvas,
                nameLeft + namePaint.measureText(name) + 12f,
                centerY - 30f,
                34f,
                palette.accent,
            )

            PosterMark.NONE -> Unit
        }

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = 24f
        }
        canvas.drawText(row.date, nameLeft, centerY + 40f, datePaint)
    }

    private fun drawFooter(
        canvas: Canvas,
        spec: PosterSpec,
        height: Int,
        palette: com.echotimer.core.share.PosterPalette,
    ) {
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            spec.footer,
            PosterLayout.WIDTH / 2f,
            height - 74f,
            footerPaint,
        )
        val note = spec.note
        if (note != null) {
            val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.textSecondary, 0.75f)
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(note, PosterLayout.WIDTH / 2f, height - 34f, notePaint)
        }
    }

    private fun drawSoftShadow(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
        dx: Float,
        dy: Float,
        spread: Float,
        layers: Int = 6,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val baseAlpha = Color.alpha(color) / 255f
        for (i in 0 until layers) {
            val t = (i + 1) / layers.toFloat()
            val falloff = (1f - t) * (1f - t)
            val grow = spread * (1f - t)
            val alpha = baseAlpha * falloff / layers * 3.2f
            if (alpha <= 0.004f) continue
            paint.color = withAlpha(color, alpha)
            val r = RectF(
                rect.left - grow + dx * t,
                rect.top - grow + dy * t,
                rect.right + grow + dx * t,
                rect.bottom + grow + dy * t,
            )
            canvas.drawRoundRect(r, radius + grow, radius + grow, paint)
        }
    }

    private fun drawCrown(canvas: Canvas, left: Float, top: Float, size: Float, color: Int) {
        val path = Path().apply {
            moveTo(left + size * 0.16f, top + size * 0.72f)
            lineTo(left + size * 0.13f, top + size * 0.32f)
            lineTo(left + size * 0.34f, top + size * 0.48f)
            lineTo(left + size * 0.50f, top + size * 0.22f)
            lineTo(left + size * 0.66f, top + size * 0.48f)
            lineTo(left + size * 0.87f, top + size * 0.32f)
            lineTo(left + size * 0.84f, top + size * 0.72f)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.drawRoundRect(
            RectF(
                left + size * 0.18f,
                top + size * 0.76f,
                left + size * 0.82f,
                top + size * 0.86f,
            ),
            size * 0.05f,
            size * 0.05f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    /** 按比例把 [over] 混进 [base]，用于"同色系里带一点红"的行底色。 */
    private fun blend(base: Int, over: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(base),
            (Color.red(base) + (Color.red(over) - Color.red(base)) * t).toInt(),
            (Color.green(base) + (Color.green(over) - Color.green(base)) * t).toInt(),
            (Color.blue(base) + (Color.blue(over) - Color.blue(base)) * t).toInt(),
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val clamped = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(clamped, Color.red(color), Color.green(color), Color.blue(color))
    }
}
