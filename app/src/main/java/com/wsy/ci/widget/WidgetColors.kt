package com.wsy.ci.widget

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
