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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 根布局使用的窗口尺寸类别。600dp 以下视为手机布局。 */
enum class CiWindowSize {
    COMPACT,
    EXPANDED,
}

/**
 * 当前窗口尺寸：默认使用平板布局，便于预览、独立渲染和未包裹在根 Provider 中的组件保持稳定。
 */
val LocalCiWindowSize = staticCompositionLocalOf { CiWindowSize.EXPANDED }

/** 响应式根布局的宽高断点；短屏也使用紧凑导航，避免手机横屏时导航栏被裁切。 */
val CiCompactWindowBreakpoint = 600.dp

/** 对话框宽度：填满当前可用宽度，同时保留平板上的设计上限。 */
fun Modifier.ciDialogWidth(maxWidth: Dp): Modifier =
    widthIn(max = maxWidth).fillMaxWidth()

/** 完整的响应式弹窗宽度规则：手机额外保留屏幕边缘留白，平板只限制最大宽度。 */
@Composable
fun Modifier.ciResponsiveDialogWidth(maxWidth: Dp): Modifier =
    ciDialogWidth(maxWidth).then(
        if (LocalCiWindowSize.current == CiWindowSize.COMPACT) {
            Modifier.padding(horizontal = CiSpacing.xs)
        } else {
            Modifier
        },
    )
