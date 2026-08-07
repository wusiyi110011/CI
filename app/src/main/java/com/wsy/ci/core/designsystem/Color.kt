package com.wsy.ci.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 「复利」调色板——方向：账本 A（纸白 + 石墨 + 金铜）+ 夜间账本 + 电青 tertiary。
 *
 * 这里是全工程唯一允许出现字面量颜色的地方。其余任何位置一律走
 * [androidx.compose.material3.MaterialTheme.colorScheme] 或 [LocalCiColors]。
 */

/** 持久化领域没有指定颜色时使用的默认 ARGB。 */
const val DEFAULT_DOMAIN_COLOR_ARGB: Long = 0xFF3F6B35

/** 统计中“未分类”领域使用的 ARGB。 */
const val UNCLASSIFIED_DOMAIN_COLOR_ARGB: Long = 0xFF9E9E9E

// ---- 亮色：纸白账本 ----
private val Graphite = Color(0xFF39362F)
private val GraphiteOn = Color(0xFFFFFFFF)
private val GraphiteContainer = Color(0xFFE4DFD1)
private val GraphiteOnContainer = Color(0xFF221F1A)

private val BrassLight = Color(0xFFB8863B)
private val BrassOnLight = Color(0xFFFFFFFF)
private val BrassContainerLight = Color(0xFFF6E4C1)
private val BrassOnContainerLight = Color(0xFF4A3512)

private val TealLight = Color(0xFF3FA9A0)
private val TealOnLight = Color(0xFFFFFFFF)
private val TealContainerLight = Color(0xFFCFEEEA)
private val TealOnContainerLight = Color(0xFF0B3A36)

private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorOnLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val ErrorOnContainerLight = Color(0xFF410002)

private val PaperLight = Color(0xFFF7F3EA)
private val InkLight = Color(0xFF211F1A)
private val SurfaceVariantLight = Color(0xFFE9E2D0)
private val OnSurfaceVariantLight = Color(0xFF4B473C)
private val OutlineLight = Color(0xFF7C7667)
private val OutlineVariantLight = Color(0xFFD8D2C0)
private val InverseSurfaceLight = Color(0xFF34322B)

private val ContainerLowestLight = Color(0xFFFFFFFF)
private val ContainerLowLight = Color(0xFFF1ECDD)
private val ContainerLight = Color(0xFFECE5D5)
private val ContainerHighLight = Color(0xFFE6DFCE)
private val ContainerHighestLight = Color(0xFFE0D9C8)

// ---- 暗色：夜间账本（深暖石墨，非冷科技灰）----
private val GraphiteDark = Color(0xFFC9C3B3)
private val GraphiteOnDark = Color(0xFF201E19)
private val GraphiteContainerDark = Color(0xFF4A4739)
private val GraphiteOnContainerDark = Color(0xFFE4DFD1)

private val BrassDark = Color(0xFFD4AF37)
private val BrassOnDark = Color(0xFF3A2E05)
private val BrassContainerDark = Color(0xFF52410C)
private val BrassOnContainerDark = Color(0xFFF6E4C1)

private val TealDark = Color(0xFF5FC2B9)
private val TealOnDark = Color(0xFF00372F)
private val TealContainerDark = Color(0xFF1E4E48)
private val TealOnContainerDark = Color(0xFFCFEEEA)

private val ErrorDark = Color(0xFFFFB4AB)
private val ErrorOnDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val ErrorOnContainerDark = Color(0xFFFFDAD6)

private val PaperDark = Color(0xFF1C1B18)
private val InkDark = Color(0xFFE8E4DB)
private val SurfaceVariantDark = Color(0xFF46443A)
private val OnSurfaceVariantDark = Color(0xFFC9C3B4)
private val OutlineDark = Color(0xFF948E7D)
private val OutlineVariantDark = Color(0xFF46443A)

private val ContainerLowestDark = Color(0xFF131210)
private val ContainerLowDark = Color(0xFF201F1B)
private val ContainerDark = Color(0xFF24231F)
private val ContainerHighDark = Color(0xFF2E2D28)
private val ContainerHighestDark = Color(0xFF393832)

// ---- 自定义语义色的原始值（不占用 M3 角色）----
private val QualityCommonLight = Color(0xFF8C877B)
private val QualityCommonContainerLight = Color(0xFFE7E3D6)
private val QualityLegendaryLight = Color(0xFFC45B3A)
private val QualityLegendaryContainerLight = Color(0xFFF5DAD0)

private val QualityCommonDark = Color(0xFFABA595)
private val QualityCommonContainerDark = Color(0xFF3A382F)
private val QualityLegendaryDark = Color(0xFFE2835F)
private val QualityLegendaryContainerDark = Color(0xFF4A2A1D)

internal val CiLightColorScheme = lightColorScheme(
    primary = Graphite,
    onPrimary = GraphiteOn,
    primaryContainer = GraphiteContainer,
    onPrimaryContainer = GraphiteOnContainer,
    secondary = BrassLight,
    onSecondary = BrassOnLight,
    secondaryContainer = BrassContainerLight,
    onSecondaryContainer = BrassOnContainerLight,
    tertiary = TealLight,
    onTertiary = TealOnLight,
    tertiaryContainer = TealContainerLight,
    onTertiaryContainer = TealOnContainerLight,
    error = ErrorLight,
    onError = ErrorOnLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorOnContainerLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PaperLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = PaperLight,
    surfaceContainerLowest = ContainerLowestLight,
    surfaceContainerLow = ContainerLowLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerHighLight,
    surfaceContainerHighest = ContainerHighestLight,
)

internal val CiDarkColorScheme = darkColorScheme(
    primary = GraphiteDark,
    onPrimary = GraphiteOnDark,
    primaryContainer = GraphiteContainerDark,
    onPrimaryContainer = GraphiteOnContainerDark,
    secondary = BrassDark,
    onSecondary = BrassOnDark,
    secondaryContainer = BrassContainerDark,
    onSecondaryContainer = BrassOnContainerDark,
    tertiary = TealDark,
    onTertiary = TealOnDark,
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = TealOnContainerDark,
    error = ErrorDark,
    onError = ErrorOnDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InkDark,
    inverseOnSurface = PaperDark,
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
