package com.wsy.ci

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.feature.settings.LocalModelUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `六个一级页面均可从左栏到达且选中态明确`() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(Destination.TODAY) }
            CiTheme(darkTheme = false) {
                CiNavigationRail(
                    selected = selected,
                    localModelState = LocalModelUiState(),
                    onSelect = { selected = it },
                    onAiClick = {},
                    onVoiceStart = {},
                    onVoiceDragCancelChanged = {},
                    onVoiceEnd = {},
                    onVoiceCancel = {},
                    onRequestRecordAudioPermission = {},
                )
            }
        }
        listOf("今日", "日程", "任务", "商城", "复盘", "设置").forEach { label ->
            val entry = composeRule.onNode(hasText(label) and hasClickAction())
            entry.assertIsDisplayed().performClick().assertIsSelected()
        }
    }
}
