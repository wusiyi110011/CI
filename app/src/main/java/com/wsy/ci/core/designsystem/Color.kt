package com.wsy.ci.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 「复利」调色板——完整设计规范中的“鼠尾草与日光”及“月光标本室”。
 *
 * 这里是全工程唯一允许出现字面量颜色的地方。其余任何位置一律走
 * [androidx.compose.material3.MaterialTheme.colorScheme] 或 [LocalCiColors]。
 */

/** 持久化领域没有指定颜色时使用的默认 ARGB。 */
const val DEFAULT_DOMAIN_COLOR_ARGB: Long = 0xFF53634B

/** 统计中“未分类”领域使用的 ARGB。 */
const val UNCLASSIFIED_DOMAIN_COLOR_ARGB: Long = 0xFF8B8B7D

// ---- 亮色：鼠尾草与日光 ----
private val SageLight = Color(0xFF6A7B61)
private val SageOnLight = Color(0xFFFFFFFF)
private val SageContainerLight = Color(0xFFE8ECE2)
private val SageOnContainerLight = Color(0xFF31332C)

private val DriedGoldLight = Color(0xFFB18449)
private val DriedGoldOnLight = Color(0xFFFFFFFF)
private val DriedGoldContainerLight = Color(0xFFF2E3C4)
private val DriedGoldOnContainerLight = Color(0xFF31332C)

private val InfoLight = Color(0xFF6C8584)
private val InfoOnLight = Color(0xFFFFFFFF)
private val InfoContainerLight = Color(0xFFDCE7E4)
private val InfoOnContainerLight = Color(0xFF2B3B3A)

private val ErrorLight = Color(0xFFAD6150)
private val ErrorOnLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFF4DDD7)
private val ErrorOnContainerLight = Color(0xFF512219)

private val OatPaperLight = Color(0xFFF8F4EA)
private val SurfaceLight = Color(0xFFEEE8DA)
private val InkLight = Color(0xFF31332C)
private val SurfaceVariantLight = Color(0xFFF3EEE2)
private val OnSurfaceVariantLight = Color(0xFF606257)
private val OutlineLight = Color(0xFF8B8B7D)
private val OutlineVariantLight = Color(0xFFD4CAB7)
private val InverseSurfaceLight = Color(0xFF31332C)

private val ContainerLowestLight = Color(0xFFF8F4EA)
private val ContainerLowLight = Color(0xFFF3EEE2)
private val ContainerLight = Color(0xFFEEE8DA)
private val ContainerHighLight = Color(0xFFE5DDCB)
private val ContainerHighestLight = Color(0xFFDDD3C0)

// ---- 暗色：月光标本室 ----
private val SageDark = Color(0xFFA8B89F)
private val SageOnDark = Color(0xFF182120)
private val SageContainerDark = Color(0xFF2D3B39)
private val SageOnContainerDark = Color(0xFFE8E7DC)

private val DriedGoldDark = Color(0xFFC1A36B)
private val DriedGoldOnDark = Color(0xFF182120)
private val DriedGoldContainerDark = Color(0xFF3B3324)
private val DriedGoldOnContainerDark = Color(0xFFE8E7DC)

private val InfoDark = Color(0xFF88A9AE)
private val InfoOnDark = Color(0xFF182120)
private val InfoContainerDark = Color(0xFF2B3B3C)
private val InfoOnContainerDark = Color(0xFFE8E7DC)

private val ErrorDark = Color(0xFFD18A79)
private val ErrorOnDark = Color(0xFF182120)
private val ErrorContainerDark = Color(0xFF4C302B)
private val ErrorOnContainerDark = Color(0xFFF3D8D1)

private val MoonPaperDark = Color(0xFF182120)
private val SurfaceDark = Color(0xFF222D2B)
private val InkDark = Color(0xFFE8E7DC)
private val SurfaceVariantDark = Color(0xFF273330)
private val OnSurfaceVariantDark = Color(0xFFBFC5BC)
private val OutlineDark = Color(0xFF8F9A94)
private val OutlineVariantDark = Color(0xFF3B4946)

private val ContainerLowestDark = Color(0xFF182120)
private val ContainerLowDark = Color(0xFF222D2B)
private val ContainerDark = Color(0xFF273330)
private val ContainerHighDark = Color(0xFF32413E)
private val ContainerHighestDark = Color(0xFF3B4946)

// ---- 自定义语义色的原始值（不占用 M3 角色）----
private val QualityCommonLight = Color(0xFF8B8B7D)
private val QualityCommonContainerLight = Color(0xFFF3EEE2)
private val QualityLegendaryLight = Color(0xFFAD6150)
private val QualityLegendaryContainerLight = Color(0xFFF4DDD7)

private val QualityCommonDark = Color(0xFF8F9A94)
private val QualityCommonContainerDark = Color(0xFF273330)
private val QualityLegendaryDark = Color(0xFFD18A79)
private val QualityLegendaryContainerDark = Color(0xFF4C302B)

internal val CiLightColorScheme = lightColorScheme(
    primary = SageLight,
    onPrimary = SageOnLight,
    primaryContainer = SageContainerLight,
    onPrimaryContainer = SageOnContainerLight,
    secondary = DriedGoldLight,
    onSecondary = DriedGoldOnLight,
    secondaryContainer = DriedGoldContainerLight,
    onSecondaryContainer = DriedGoldOnContainerLight,
    tertiary = InfoLight,
    onTertiary = InfoOnLight,
    tertiaryContainer = InfoContainerLight,
    onTertiaryContainer = InfoOnContainerLight,
    error = ErrorLight,
    onError = ErrorOnLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorOnContainerLight,
    background = OatPaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = OatPaperLight,
    surfaceContainerLowest = ContainerLowestLight,
    surfaceContainerLow = ContainerLowLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerHighLight,
    surfaceContainerHighest = ContainerHighestLight,
)

internal val CiDarkColorScheme = darkColorScheme(
    primary = SageDark,
    onPrimary = SageOnDark,
    primaryContainer = SageContainerDark,
    onPrimaryContainer = SageOnContainerDark,
    secondary = DriedGoldDark,
    onSecondary = DriedGoldOnDark,
    secondaryContainer = DriedGoldContainerDark,
    onSecondaryContainer = DriedGoldOnContainerDark,
    tertiary = InfoDark,
    onTertiary = InfoOnDark,
    tertiaryContainer = InfoContainerDark,
    onTertiaryContainer = InfoOnContainerDark,
    error = ErrorDark,
    onError = ErrorOnDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,
    background = MoonPaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InkDark,
    inverseOnSurface = MoonPaperDark,
    surfaceContainerLowest = ContainerLowestDark,
    surfaceContainerLow = ContainerLowDark,
    surfaceContainer = ContainerDark,
    surfaceContainerHigh = ContainerHighDark,
    surfaceContainerHighest = ContainerHighestDark,
)

/** 亮色下的自定义语义色原始值，供 [CiColors.light] 组装。 */
internal object CiRawLight {
    val qualityCommon = QualityCommonLight
    val qualityCommonContainer = QualityCommonContainerLight
    val qualityLegendary = QualityLegendaryLight
    val qualityLegendaryContainer = QualityLegendaryContainerLight
}

/** 暗色下的自定义语义色原始值，供 [CiColors.dark] 组装。 */
internal object CiRawDark {
    val qualityCommon = QualityCommonDark
    val qualityCommonContainer = QualityCommonContainerDark
    val qualityLegendary = QualityLegendaryDark
    val qualityLegendaryContainer = QualityLegendaryContainerDark
}
