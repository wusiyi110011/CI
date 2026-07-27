package com.wsy.ci

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.feature.calendar.CalendarScreen
import com.wsy.ci.feature.quest.QuestScreen
import com.wsy.ci.feature.settings.SettingsScreen
import com.wsy.ci.feature.shop.ShopScreen
import com.wsy.ci.feature.stats.StatsScreen
import com.wsy.ci.feature.today.TodayScreen

private enum class Destination(val label: String, val emoji: String) {
    TODAY("今日", "⏱"),
    CALENDAR("日程", "📅"),
    QUEST("任务", "🗡"),
    SHOP("商城", "🛍"),
    STATS("复盘", "📊"),
    SETTINGS("设置", "⚙️"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
        NavigationRail {
            Destination.entries.forEach { dest ->
                NavigationRailItem(
                    selected = destination == dest,
                    onClick = { destination = dest },
                    icon = { Text(dest.emoji) },
                    label = { Text(dest.label) },
                )
            }
        }
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            when (destination) {
                Destination.TODAY -> TodayScreen()
                Destination.CALENDAR -> CalendarScreen()
                Destination.QUEST -> QuestScreen()
                Destination.SHOP -> ShopScreen()
                Destination.STATS -> StatsScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
}
