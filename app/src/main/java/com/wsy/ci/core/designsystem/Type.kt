package com.wsy.ci.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号层级。字体走系统内置（中文即 Noto Sans SC / PingFang SC），不引入外部字体文件，
 * 因此统一用 [FontFamily.Default]。
 */
private val CiFontFamily = FontFamily.Default

/** OpenType 等宽数字特性。计时器、金额、时刻一律套用，避免数字跳动。 */
private const val TABULAR_NUMS = "tnum"

/** 给任意排版样式开启等宽数字。 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = TABULAR_NUMS)

private fun ciStyle(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = CiFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
)

internal val CiTypography = Typography(
    displayLarge = ciStyle(57, FontWeight.Normal, 64),
    displayMedium = ciStyle(45, FontWeight.Normal, 52),
    displaySmall = ciStyle(36, FontWeight.Normal, 44),
    headlineLarge = ciStyle(32, FontWeight.Medium, 40),
    headlineMedium = ciStyle(28, FontWeight.Medium, 36),
    headlineSmall = ciStyle(24, FontWeight.Medium, 32),
    titleLarge = ciStyle(22, FontWeight.Medium, 28),
    titleMedium = ciStyle(16, FontWeight.SemiBold, 24),
    titleSmall = ciStyle(14, FontWeight.SemiBold, 20),
    bodyLarge = ciStyle(16, FontWeight.Normal, 24),
    bodyMedium = ciStyle(14, FontWeight.Normal, 20),
    bodySmall = ciStyle(12, FontWeight.Normal, 16),
    labelLarge = ciStyle(14, FontWeight.SemiBold, 20),
    labelMedium = ciStyle(12, FontWeight.SemiBold, 16),
    labelSmall = ciStyle(11, FontWeight.SemiBold, 16),
)

/** M3 Type Scale 之外的自定义排版 token。 */
object CiTextStyles {
    /** 超大展示号，token 表中的 displayJumbo。 */
    val displayJumbo: TextStyle = ciStyle(96, FontWeight.Light, 104)

    /** 进行中计时卡的主计时器：按设计画布实测值 64sp + 2sp 字距。 */
    val timer: TextStyle = ciStyle(64, FontWeight.Light, 72)
        .copy(letterSpacing = 2.sp)
        .tabularNums()

    /** 时间线内任务块的标题（比 labelMedium 更紧凑）。 */
    val blockTitle: TextStyle = ciStyle(12, FontWeight.SemiBold, 15)

    /** 时间线内任务块的副标题（时刻区间）。 */
    val blockCaption: TextStyle = ciStyle(10, FontWeight.Normal, 13).tabularNums()
}
