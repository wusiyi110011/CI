package com.wsy.ci.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wsy.ci.R
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.Rarity

/** 通用 pill 标签：容器底 + 内容色，圆角 full。 */
@Composable
fun CiChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    borderColor: Color? = null,
    horizontalPadding: Dp = CiSpacing.sm,
    verticalPadding: Dp = CiSpacing.xxs,
    @DrawableRes leadingIcon: Int? = null,
    iconContentDescription: String? = null,
) {
    Row(
        modifier = modifier
            .clip(CiShapes.pill)
            .background(container)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, CiShapes.pill)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            CiFunctionIcon(
                resourceId = it,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(CiSizes.compactIcon),
            )
        }
        Text(
            text = text,
            style = style,
            color = content,
            modifier = if (leadingIcon == null) {
                Modifier
            } else {
                Modifier.padding(start = CiSpacing.xxs)
            },
        )
    }
}

/** CI 币余额 chip：星尘图标 + 千分位金额。 */
@Composable
fun CiBalanceChip(balance: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CiShapes.pill)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = CiSpacing.sm, vertical = CiSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
    ) {
        CiFunctionIcon(
            resourceId = R.drawable.ic_ci_coin,
            contentDescription = "CI 币",
            modifier = Modifier.size(CiSizes.balanceIcon),
        )
        Text(
            text = formatCi(balance),
            style = MaterialTheme.typography.labelLarge.tabularNums(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 难度 chip：越难墨色越重，见 token 表四档难度。 */
@Composable
fun CiDifficultyChip(
    difficulty: Difficulty,
    modifier: Modifier = Modifier,
    showFactor: Boolean = true,
) {
    val colors = CiTheme.colors.difficulty(difficulty)
    val label = if (showFactor) "${difficulty.label} ×${difficulty.factor}" else difficulty.label
    CiChip(text = label, container = colors.container, content = colors.content, modifier = modifier)
}

/** 商品品质 chip。[label] 允许传「稀有 · 铜币」这类带雅称的展示文案。 */
@Composable
fun CiQualityChip(rarity: Rarity, label: String, modifier: Modifier = Modifier) {
    val colors = CiTheme.colors.quality(rarity)
    CiChip(
        text = label,
        container = colors.container,
        content = colors.accent,
        modifier = modifier,
        horizontalPadding = CiSpacing.xs + 2.dp,
        verticalPadding = 3.dp,
    )
}

/** 图例用的状态圆点。 */
@Composable
fun CiLegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(9.dp).clip(CiShapes.pill).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
