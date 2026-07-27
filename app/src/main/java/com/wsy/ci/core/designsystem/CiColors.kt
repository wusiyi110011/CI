package com.wsy.ci.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.Rarity

/** 品质配色：强调色（价格/标签文字/顶部色条）+ 容器色（标签底）。 */
@Immutable
data class QualityColors(val accent: Color, val container: Color)

/** chip 配色：容器底 + 内容色。 */
@Immutable
data class ChipColors(val container: Color, val content: Color)

/** 任务块四态配色。[alpha] 与 [strikethrough] 专为「已跳过」态服务。 */
@Immutable
data class TaskBlockColors(
    val container: Color,
    val accent: Color,
    val content: Color,
    val alpha: Float = 1f,
    val strikethrough: Boolean = false,
)

/** 热力色阶：容器色 + 对应的内容色，索引 0（无）到 4（最强）。 */
@Immutable
data class HeatScale(val containers: List<Color>, val contents: List<Color>) {
    val levels: Int get() = containers.size

    /** 把任意强度值收敛到合法档位。 */
    fun container(level: Int): Color = containers[level.coerceIn(0, containers.lastIndex)]

    fun content(level: Int): Color = contents[level.coerceIn(0, contents.lastIndex)]
}

/**
 * 不占用 M3 角色的自定义语义 token。通过 [LocalCiColors] 下发，
 * 全部由 [CiColors.from] 从当前 [ColorScheme] 派生，保证亮/暗自动跟随。
 */
@Immutable
data class CiColors(
    /** 流水收入、专注入账。 */
    val income: Color,
    /** 流水支出：中性灰 + 「−」，不新增色相。 */
    val expense: Color,
    /** 时间线当前时刻指示线。 */
    val currentTimeLine: Color,
    val qualityCommon: QualityColors,
    val qualityRare: QualityColors,
    val qualityEpic: QualityColors,
    val qualityLegendary: QualityColors,
    val difficultyEasy: ChipColors,
    val difficultyNormal: ChipColors,
    val difficultyHard: ChipColors,
    val difficultyEpic: ChipColors,
    val taskPlanned: TaskBlockColors,
    val taskRunning: TaskBlockColors,
    val taskDone: TaskBlockColors,
    val taskSkipped: TaskBlockColors,
    /** 专注强度色阶（电青系）：月视图热力、黄金时段热力图。 */
    val focusHeat: HeatScale,
    /** 打卡强度色阶（金铜系）：每日打卡格。 */
    val checkinHeat: HeatScale,
) {
    fun quality(rarity: Rarity): QualityColors = when (rarity) {
        Rarity.COMMON -> qualityCommon
        Rarity.RARE -> qualityRare
        Rarity.EPIC -> qualityEpic
        Rarity.LEGENDARY -> qualityLegendary
    }

    fun difficulty(difficulty: Difficulty): ChipColors = when (difficulty) {
        Difficulty.EASY -> difficultyEasy
        Difficulty.NORMAL -> difficultyNormal
        Difficulty.HARD -> difficultyHard
        Difficulty.EPIC -> difficultyEpic
    }

    fun taskBlock(status: TaskStatus): TaskBlockColors = when (status) {
        TaskStatus.PLANNED -> taskPlanned
        TaskStatus.RUNNING -> taskRunning
        TaskStatus.DONE -> taskDone
        TaskStatus.SKIPPED -> taskSkipped
    }

    companion object {
        /** 「已跳过」态的不透明度，见 token 表任务块四态。 */
        private const val SKIPPED_ALPHA = 0.55f

        fun from(scheme: ColorScheme, isDark: Boolean): CiColors {
            val qualityCommonAccent = if (isDark) CiRawDark.qualityCommon else CiRawLight.qualityCommon
            val qualityCommonContainer =
                if (isDark) CiRawDark.qualityCommonContainer else CiRawLight.qualityCommonContainer
            val qualityLegendaryAccent =
                if (isDark) CiRawDark.qualityLegendary else CiRawLight.qualityLegendary
            val qualityLegendaryContainer =
                if (isDark) CiRawDark.qualityLegendaryContainer else CiRawLight.qualityLegendaryContainer

            return CiColors(
                income = scheme.secondary,
                expense = scheme.onSurfaceVariant,
                currentTimeLine = scheme.tertiary,
                qualityCommon = QualityColors(qualityCommonAccent, qualityCommonContainer),
                qualityRare = QualityColors(scheme.secondary, scheme.secondaryContainer),
                qualityEpic = QualityColors(scheme.tertiary, scheme.tertiaryContainer),
                qualityLegendary = QualityColors(qualityLegendaryAccent, qualityLegendaryContainer),
                difficultyEasy = ChipColors(scheme.outlineVariant, scheme.onSurfaceVariant),
                difficultyNormal = ChipColors(scheme.surfaceContainerHigh, scheme.onSurface),
                difficultyHard = ChipColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                difficultyEpic = ChipColors(scheme.primary, scheme.onPrimary),
                taskPlanned = TaskBlockColors(
                    container = scheme.surfaceContainerLow,
                    accent = scheme.outline,
                    content = scheme.onSurfaceVariant,
                ),
                taskRunning = TaskBlockColors(
                    container = scheme.tertiaryContainer,
                    accent = scheme.tertiary,
                    content = scheme.onTertiaryContainer,
                ),
                taskDone = TaskBlockColors(
                    container = scheme.secondaryContainer,
                    accent = scheme.secondary,
                    content = scheme.onSecondaryContainer,
                ),
                taskSkipped = TaskBlockColors(
                    container = scheme.surfaceVariant,
                    accent = scheme.outlineVariant,
                    content = scheme.onSurfaceVariant,
                    alpha = SKIPPED_ALPHA,
                    strikethrough = true,
                ),
                focusHeat = HeatScale(
                    containers = listOf(
                        scheme.surfaceContainerLow,
                        scheme.tertiaryContainer,
                        scheme.tertiaryContainer,
                        scheme.tertiary,
                        scheme.tertiary,
                    ),
                    contents = listOf(
                        scheme.onSurfaceVariant,
                        scheme.onTertiaryContainer,
                        scheme.onTertiaryContainer,
                        scheme.onTertiary,
                        scheme.onTertiary,
                    ),
                ),
                checkinHeat = HeatScale(
                    containers = listOf(
                        scheme.surfaceContainerLow,
                        scheme.secondaryContainer,
                        scheme.secondaryContainer,
                        scheme.secondary,
                        scheme.secondary,
                    ),
                    contents = listOf(
                        scheme.onSurfaceVariant,
                        scheme.onSecondaryContainer,
                        scheme.onSecondaryContainer,
                        scheme.onSecondary,
                        scheme.onSecondary,
                    ),
                ),
            )
        }
    }
}

val LocalCiColors = staticCompositionLocalOf<CiColors> {
    error("LocalCiColors 未提供：请把内容包在 CiTheme { } 内")
}
