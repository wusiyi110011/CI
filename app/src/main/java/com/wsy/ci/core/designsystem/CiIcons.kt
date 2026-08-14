package com.wsy.ci.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import com.wsy.ci.R

/**
 * 复利手绘功能图标的统一入口。
 *
 * 图标本身包含石墨、黄铜和电青语义色，因此不做 tint；亮暗主题由同名
 * `drawable` / `drawable-night` 资源自动切换。
 */
@Composable
fun CiFunctionIcon(
    @DrawableRes resourceId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(CiSizes.actionIcon),
) {
    Image(
        painter = painterResource(themedIconResource(resourceId, CiTheme.isDark)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/**
 * 应用主题可以独立于系统主题切换，不能依赖 `drawable-night` 自动选图。
 * Glance 仍使用无后缀别名跟随系统；Compose 在这里显式选择应用主题版本。
 */
@DrawableRes
private fun themedIconResource(@DrawableRes resourceId: Int, isDark: Boolean): Int = when (resourceId) {
    R.drawable.ic_ci_today -> if (isDark) R.drawable.ic_ci_today_dark else R.drawable.ic_ci_today_light
    R.drawable.ic_ci_schedule -> if (isDark) R.drawable.ic_ci_schedule_dark else R.drawable.ic_ci_schedule_light
    R.drawable.ic_ci_quest -> if (isDark) R.drawable.ic_ci_quest_dark else R.drawable.ic_ci_quest_light
    R.drawable.ic_ci_shop -> if (isDark) R.drawable.ic_ci_shop_dark else R.drawable.ic_ci_shop_light
    R.drawable.ic_ci_stats -> if (isDark) R.drawable.ic_ci_stats_dark else R.drawable.ic_ci_stats_light
    R.drawable.ic_ci_settings -> if (isDark) R.drawable.ic_ci_settings_dark else R.drawable.ic_ci_settings_light
    R.drawable.ic_ci_main_quest -> if (isDark) R.drawable.ic_ci_main_quest_dark else R.drawable.ic_ci_main_quest_light
    R.drawable.ic_ci_side_quest -> if (isDark) R.drawable.ic_ci_side_quest_dark else R.drawable.ic_ci_side_quest_light
    R.drawable.ic_ci_focus_timer -> if (isDark) R.drawable.ic_ci_focus_timer_dark else R.drawable.ic_ci_focus_timer_light
    R.drawable.ic_ci_ai_schedule -> if (isDark) R.drawable.ic_ci_ai_schedule_dark else R.drawable.ic_ci_ai_schedule_light
    R.drawable.ic_ci_coin -> if (isDark) R.drawable.ic_ci_coin_dark else R.drawable.ic_ci_coin_light
    R.drawable.ic_ci_level_up -> if (isDark) R.drawable.ic_ci_level_up_dark else R.drawable.ic_ci_level_up_light
    R.drawable.ic_ci_streak -> if (isDark) R.drawable.ic_ci_streak_dark else R.drawable.ic_ci_streak_light
    R.drawable.ic_ci_learning_domain -> if (isDark) R.drawable.ic_ci_learning_domain_dark else R.drawable.ic_ci_learning_domain_light
    R.drawable.ic_ci_blocker -> if (isDark) R.drawable.ic_ci_blocker_dark else R.drawable.ic_ci_blocker_light
    R.drawable.ic_ci_backup -> if (isDark) R.drawable.ic_ci_backup_dark else R.drawable.ic_ci_backup_light
    R.drawable.ic_ci_add -> if (isDark) R.drawable.ic_ci_add_dark else R.drawable.ic_ci_add_light
    R.drawable.ic_ci_previous -> if (isDark) R.drawable.ic_ci_previous_dark else R.drawable.ic_ci_previous_light
    R.drawable.ic_ci_next -> if (isDark) R.drawable.ic_ci_next_dark else R.drawable.ic_ci_next_light
    R.drawable.ic_ci_edit -> if (isDark) R.drawable.ic_ci_edit_dark else R.drawable.ic_ci_edit_light
    R.drawable.ic_ci_complete -> if (isDark) R.drawable.ic_ci_complete_dark else R.drawable.ic_ci_complete_light
    R.drawable.ic_ci_archive -> if (isDark) R.drawable.ic_ci_archive_dark else R.drawable.ic_ci_archive_light
    R.drawable.ic_ci_restore -> if (isDark) R.drawable.ic_ci_restore_dark else R.drawable.ic_ci_restore_light
    R.drawable.ic_ci_lock -> if (isDark) R.drawable.ic_ci_lock_dark else R.drawable.ic_ci_lock_light
    R.drawable.ic_ci_import -> if (isDark) R.drawable.ic_ci_import_dark else R.drawable.ic_ci_import_light
    R.drawable.ic_ci_visibility_on -> if (isDark) R.drawable.ic_ci_visibility_on_dark else R.drawable.ic_ci_visibility_on_light
    R.drawable.ic_ci_visibility_off -> if (isDark) R.drawable.ic_ci_visibility_off_dark else R.drawable.ic_ci_visibility_off_light
    R.drawable.ic_ci_copy -> if (isDark) R.drawable.ic_ci_copy_dark else R.drawable.ic_ci_copy_light
    R.drawable.ic_ci_paste -> if (isDark) R.drawable.ic_ci_paste_dark else R.drawable.ic_ci_paste_light
    R.drawable.ic_ci_warning -> if (isDark) R.drawable.ic_ci_warning_dark else R.drawable.ic_ci_warning_light
    R.drawable.ic_ci_close -> if (isDark) R.drawable.ic_ci_close_dark else R.drawable.ic_ci_close_light
    R.drawable.ic_ci_filter -> if (isDark) R.drawable.ic_ci_filter_dark else R.drawable.ic_ci_filter_light
    R.drawable.ic_ci_stop -> if (isDark) R.drawable.ic_ci_stop_dark else R.drawable.ic_ci_stop_light
    R.drawable.ic_ci_open -> if (isDark) R.drawable.ic_ci_open_dark else R.drawable.ic_ci_open_light
    R.drawable.ic_ci_dropdown -> if (isDark) R.drawable.ic_ci_dropdown_dark else R.drawable.ic_ci_dropdown_light
    else -> resourceId
}
