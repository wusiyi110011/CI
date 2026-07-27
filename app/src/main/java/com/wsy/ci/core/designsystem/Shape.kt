package com.wsy.ci.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** M3 Shape Scale：xs=4 / sm=8 / md=12 / lg=16 / xl=28。 */
internal val CiShapeScale = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 圆角的语义映射，见设计规格「圆角刻度」一节。 */
object CiShapes {
    /** 时间线任务块 = sm(8)。 */
    val taskBlock = RoundedCornerShape(8.dp)

    /** 输入框 / 下拉选择器 = md(12)。 */
    val field = RoundedCornerShape(12.dp)

    /** 卡片 = lg(16)。 */
    val card = RoundedCornerShape(16.dp)

    /** FAB = lg(16)。 */
    val fab = RoundedCornerShape(16.dp)

    /** 按钮 / chip / 分段控件 = full。 */
    val pill = RoundedCornerShape(percent = 50)

    /** 对话框 = xl(28)。 */
    val dialog = RoundedCornerShape(28.dp)

    /** 热力图格子。 */
    val heatCell = RoundedCornerShape(2.dp)

    /** 月视图日历格。 */
    val monthCell = RoundedCornerShape(10.dp)
}
