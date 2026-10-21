package com.echotimer.core.theme

/**
 * 配色规格（纯数据，不含 Compose 类型），便于在 JVM 单元测试里做对比度校验。
 * 颜色为 0xAARRGGBB。
 */
data class PaletteColors(
    val background: Long,
    val surface: Long,
    val surfaceAlt: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val accent: Long,
    val onAccent: Long,
    val shadowLight: Long,
    val shadowDark: Long,
)

/** 一套主题配色，含亮/暗两个变体。 */
data class PaletteSpec(
    val id: String,
    val displayName: String,
    val light: PaletteColors,
    val dark: PaletteColors,
)

/**
 * 内置配色。拟态风格依赖"同色系浅阴影 + 深阴影"，因此背景与控件面色差很小，
 * 可读性完全靠 textPrimary / textSecondary 的对比度保证（见 PaletteContrastTest）。
 */
object Palettes {

    const val DEFAULT_ID = "graphite"

    val ALL: List<PaletteSpec> = listOf(
        PaletteSpec(
            id = "graphite",
            displayName = "石墨",
            light = PaletteColors(
                background = 0xFFE9ECF1,
                surface = 0xFFE9ECF1,
                surfaceAlt = 0xFFDDE2E9,
                textPrimary = 0xFF23282F,
                textSecondary = 0xFF5C6673,
                accent = 0xFF2B5FD9,
                onAccent = 0xFFFFFFFF,
                shadowLight = 0xFFFFFFFF,
                shadowDark = 0xFFBFC6D1,
            ),
            dark = PaletteColors(
                background = 0xFF22262C,
                surface = 0xFF22262C,
                surfaceAlt = 0xFF1B1F24,
                textPrimary = 0xFFEEF2F7,
                textSecondary = 0xFFA3AEBC,
                accent = 0xFF7AA2FF,
                onAccent = 0xFF0A1224,
                shadowLight = 0xFF343B44,
                shadowDark = 0xFF121518,
            ),
        ),
        PaletteSpec(
            id = "sand",
            displayName = "暖沙",
            light = PaletteColors(
                background = 0xFFF0E8DC,
                surface = 0xFFF0E8DC,
                surfaceAlt = 0xFFE3D8C8,
                textPrimary = 0xFF33291F,
                textSecondary = 0xFF6B5B49,
                accent = 0xFF9C4A22,
                onAccent = 0xFFFFF8F3,
                shadowLight = 0xFFFFFCF6,
                shadowDark = 0xFFCBBBA4,
            ),
            dark = PaletteColors(
                background = 0xFF2A241E,
                surface = 0xFF2A241E,
                surfaceAlt = 0xFF211C17,
                textPrimary = 0xFFF4EBDF,
                textSecondary = 0xFFBEAD99,
                accent = 0xFFE08A5E,
                onAccent = 0xFF241109,
                shadowLight = 0xFF3C342B,
                shadowDark = 0xFF191411,
            ),
        ),
        PaletteSpec(
            id = "mist",
            displayName = "雾青",
            light = PaletteColors(
                background = 0xFFE6EDEC,
                surface = 0xFFE6EDEC,
                surfaceAlt = 0xFFD7E0DF,
                textPrimary = 0xFF1D2C2A,
                textSecondary = 0xFF566B68,
                accent = 0xFF1F6F63,
                onAccent = 0xFFF4FFFC,
                shadowLight = 0xFFFCFFFF,
                shadowDark = 0xFFBDC9C7,
            ),
            dark = PaletteColors(
                background = 0xFF1E2827,
                surface = 0xFF1E2827,
                surfaceAlt = 0xFF172020,
                textPrimary = 0xFFE8F1EF,
                textSecondary = 0xFF9BAEAB,
                accent = 0xFF5FC9B7,
                onAccent = 0xFF06201B,
                shadowLight = 0xFF2E3C3A,
                shadowDark = 0xFF0F1716,
            ),
        ),
    )

    /** 未知 id 回落到默认配色，保证主题 id 存坏时界面仍可用。 */
    fun byId(id: String?): PaletteSpec = ALL.firstOrNull { it.id == id } ?: ALL.first()

    fun ids(): List<String> = ALL.map { it.id }
}
