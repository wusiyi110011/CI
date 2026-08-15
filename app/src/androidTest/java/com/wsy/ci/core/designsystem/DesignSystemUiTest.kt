package com.wsy.ci.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `弱动效状态可由主题下发且字段触控高度不少于四十八dp`() {
        composeRule.setContent {
            CiTheme(darkTheme = false, reducedMotion = true) {
                CiTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = if (CiTheme.reducedMotion) "弱动效" else "标准动效",
                    height = 44.dp,
                    modifier = Modifier.testTag("field"),
                )
            }
        }

        composeRule.onNodeWithTag("field")
            .assertHeightIsAtLeast(48.dp)
            .assertTextContains("弱动效")
    }
}
