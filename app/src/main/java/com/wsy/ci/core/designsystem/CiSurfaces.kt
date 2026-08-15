package com.wsy.ci.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** 重点轻贴纸片：只用于当前焦点、主线概览、商品与结算等高层级内容。 */
@Composable
fun CiPanelCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = CiSpacing.md,
    verticalSpacing: Dp = CiSpacing.xs,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = CiShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = CiElevation.card),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

/**
 * 账页面板：用于时间线、统计、设置和明细，不以阴影制造层层卡片。
 */
@Composable
fun CiLedgerSection(
    modifier: Modifier = Modifier,
    contentPadding: Dp = CiSpacing.md,
    verticalSpacing: Dp = CiSpacing.xs,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .border(CiSizes.border, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

/** 带标题的统计面板，复盘屏五大面板共用。 */
@Composable
fun CiStatPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CiLedgerSection(modifier = modifier, verticalSpacing = CiSpacing.xs) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = CiSpacing.xxs),
        )
        content()
    }
}

/** 屏幕头部行：标题 headlineSmall（+ 可选副标题），右侧放余额 chip 或分段控件。 */
@Composable
fun CiScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
        ) {
            Text(text = title, style = CiTextStyles.pageTitle)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = CiSpacing.xxs),
                )
            }
        }
        trailing?.invoke()
    }
}
