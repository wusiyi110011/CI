package com.wsy.ci.widget

import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import com.wsy.ci.core.designsystem.CiDarkColorScheme
import com.wsy.ci.core.designsystem.CiLightColorScheme

/**
 * 小组件的配色来源。Glance 不在 Compose UI 的 CompositionLocal 作用域内，拿不到
 * `MaterialTheme.colorScheme`，所以这里把同一份亮/暗 ColorScheme 交给 GlanceTheme，
 * 之后小组件内一律用 `GlanceTheme.colors.*`，不出现字面量颜色。
 */
internal val CiGlanceColors = ColorProviders(
    light = CiLightColorScheme,
    dark = CiDarkColorScheme,
)

/**
 * Glance 的 `ColorProviders` 只覆盖 M3 的基础角色，缺 `surfaceContainer*` 与
 * `outlineVariant`——而小组件的层次恰恰靠它们表达（Glance 没有 elevation，
 * 只能用底色深浅代替阴影）。这里从同一份 ColorScheme 里补出这两档，
 * 仍然不出现字面量颜色。
 */
internal object CiWidgetPalette {
    /**
     * 小组件底板，比 `surface` 深一档。未落定的行用 `surface` 浮在它上面，
     * 已完成/已跳过的行改用这一档，视觉上「凹」回底板里。
     */
    val surfaceContainerLow = ColorProvider(
        day = CiLightColorScheme.surfaceContainerLow,
        night = CiDarkColorScheme.surfaceContainerLow,
    )

    /** 分割线，比 outline 更弱。 */
    val outlineVariant = ColorProvider(
        day = CiLightColorScheme.outlineVariant,
        night = CiDarkColorScheme.outlineVariant,
    )
}
