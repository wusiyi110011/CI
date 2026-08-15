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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** M3 Shape Scale：完整设计规范的 10 / 18 / 26dp 语义圆角。 */
internal val CiShapeScale = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** 圆角的语义映射，见设计规格「圆角刻度」一节。 */
object CiShapes {
    /** 直角 = none(0)。 */
    val none = RoundedCornerShape(0.dp)

    /** 时间线任务块 = sm(10)。 */
    val taskBlock = RoundedCornerShape(10.dp)

    /** 周视图任务块，比日视图更紧凑。 */
    val weekBlock = RoundedCornerShape(10.dp)

    /** 输入框 / 下拉选择器 = md(18)。 */
    val field = RoundedCornerShape(18.dp)

    /** 重点轻贴纸片 = lg(26)。 */
    val card = RoundedCornerShape(26.dp)

    /** FAB = md(18)。 */
    val fab = RoundedCornerShape(18.dp)

    /** 按钮 / chip / 分段控件 = full。 */
    val pill = RoundedCornerShape(percent = 50)

    /** 对话框 = lg(26)。 */
    val dialog = RoundedCornerShape(26.dp)

    /** 黄金时段热力图格子。 */
    val heatCell = RoundedCornerShape(10.dp)

    /** 每日打卡格子。 */
    val checkinCell = RoundedCornerShape(10.dp)

    /** 月视图日历格。 */
    val monthCell = RoundedCornerShape(10.dp)
}
