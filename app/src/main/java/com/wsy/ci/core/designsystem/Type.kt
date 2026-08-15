/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wsy.ci.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号层级。正文与标签走系统内置无衬线字体，标题另用系统通用衬线字体；不引入外部字体文件。
 */
private val CiFontFamily = FontFamily.Default
private val CiTitleFontFamily = FontFamily.Serif
private val CiNumericFontFamily = FontFamily.Monospace

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

private fun ciNumericStyle(size: Int, weight: FontWeight, lineHeight: Int) =
    ciStyle(size, weight, lineHeight).copy(fontFamily = CiNumericFontFamily).tabularNums()

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
    /** 页面标题；使用系统通用衬线字体，保留观察札记般的标题气质。 */
    val pageTitle: TextStyle = ciStyle(28, FontWeight.SemiBold, 36)
        .copy(fontFamily = CiTitleFontFamily)

    /** 章节题签；只用于少量标题，不能进入密集列表与表格。 */
    val sectionTitle: TextStyle = ciStyle(22, FontWeight.SemiBold, 28)
        .copy(fontFamily = CiTitleFontFamily)

    /** 超大展示号，token 表中的 displayJumbo。 */
    val displayJumbo: TextStyle = ciNumericStyle(96, FontWeight.Light, 104)

    /** 进行中计时卡的主计时器：按设计画布实测值 64sp + 2sp 字距。 */
    val timer: TextStyle = ciNumericStyle(64, FontWeight.Light, 72)
        .copy(letterSpacing = 2.sp)

    /** 时间线内任务块的标题（比 labelMedium 更紧凑）。 */
    val blockTitle: TextStyle = ciStyle(12, FontWeight.SemiBold, 15)

    /** 时间线内任务块的副标题（时刻区间）。 */
    val blockCaption: TextStyle = ciNumericStyle(10, FontWeight.Normal, 13)
}
