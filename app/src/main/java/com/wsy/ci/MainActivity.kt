package com.wsy.ci

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.feature.calendar.CalendarScreen
import com.wsy.ci.feature.quest.QuestScreen
import com.wsy.ci.feature.settings.SettingsScreen
import com.wsy.ci.feature.shop.ShopScreen
import com.wsy.ci.feature.stats.StatsScreen
import com.wsy.ci.feature.today.TodayScreen

private enum class Destination(
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    TODAY("今日", R.drawable.ic_nav_today),
    CALENDAR("日程", R.drawable.ic_nav_schedule),
    QUEST("任务", R.drawable.ic_nav_quest),
    SHOP("商城", R.drawable.ic_nav_shop),
    STATS("复盘", R.drawable.ic_nav_stats),
    SETTINGS("设置", R.drawable.ic_nav_settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appSettings = (application as CiApp).container.appSettings
        setContent {
            val themeMode by appSettings.themeMode.collectAsState()
            CiTheme(darkTheme = themeMode.isDark(isSystemInDarkTheme())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    CiRoot()
                }
            }
        }
    }
}

@Composable
private fun CiRoot() {
    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    Row(modifier = Modifier.fillMaxSize()) {
        CiNavigationRail(
            selected = destination,
            onSelect = { destination = it },
        )
        Box(modifier = Modifier.weight(1f)) {
            when (destination) {
                Destination.TODAY -> TodayScreen()
                Destination.CALENDAR -> CalendarScreen()
                // 从任务线里开始专注后直接切到今日屏，那里才有计时卡
                Destination.QUEST -> QuestScreen(
                    onNavigateToToday = { destination = Destination.TODAY },
                )
                Destination.SHOP -> ShopScreen()
                Destination.STATS -> StatsScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
}

/**
 * 左侧导航：88dp 宽、扁平（elevation 0）、右缘 1dp 分割线，选中项金铜容器 + 圆角 16。
 * 6 项整组垂直居中：横屏平板栏高远大于内容高（6×64 + 5×10 = 434dp），
 * 顶部对齐会在下方留一大片空白。
 */
@Composable
private fun CiNavigationRail(selected: Destination, onSelect: (Destination) -> Unit) {
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(CiSizes.navRailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .rightEdge(edgeColor)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Destination.entries.forEach { destination ->
            NavRailItem(
                destination = destination,
                isSelected = destination == selected,
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun NavRailItem(destination: Destination, isSelected: Boolean, onClick: () -> Unit) {
    val container = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val content = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .size(CiSizes.navRailItem)
            .clip(CiShapes.fab)
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(destination.icon),
            contentDescription = null,
            modifier = Modifier.size(CiSizes.navRailIcon),
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(top = CiSpacing.xxs),
        )
    }
}

/** 导航栏右缘的 1dp 分割线。 */
private fun Modifier.rightEdge(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset(size.width, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}
