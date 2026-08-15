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

package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.TimeSpan
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.timeSpansOrNull
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BlockTime / Navigate 的规则匹配与 LLM 参数校验（迁移原 VoiceCommandParser 断言）。 */
class ScheduleSkillsTest {

    // ---------- BlockTimeSkill ----------

    @Test
    fun `占用类词解析出时间段返回占位事件`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = BlockTimeSkill.matchRule("明天下午三点到五点没空要去开会", ctx)

        // Assert
        val spans = args?.get("spans") as List<*>
        assertEquals(1, spans.size)
        val span = spans[0] as TimeSpan
        assertEquals(LocalDate.of(2026, 8, 16).toEpochDay(), span.epochDay)
        assertEquals(900, span.startMinute)
        assertEquals(1020, span.endMinute)
        assertEquals("要去开会", args["reason"])
    }

    @Test
    fun `占用类词解析不出原因兜底为临时安排`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = BlockTimeSkill.matchRule("明天下午三点到五点没空", ctx)

        // Assert
        assertEquals("临时安排", args?.get("reason"))
    }

    @Test
    fun `无关文本不命中BlockTime`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = BlockTimeSkill.matchRule("今天天气怎么样", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数spans空数组整体拒绝`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(mapOf("spans" to JsonArray(emptyList())))

        // Act
        val result = BlockTimeSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数spans是字符串等畸形形状返回null而不崩溃`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(mapOf("spans" to JsonPrimitive("下午")))

        // Act
        val result = BlockTimeSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `多个时间段各自转成一条ParsedBlocker`() {
        // Arrange
        val day1 = LocalDate.of(2026, 8, 16).toEpochDay()
        val day2 = LocalDate.of(2026, 8, 17).toEpochDay()
        val spans = listOf(
            TimeSpan(epochDay = day1, startMinute = 8 * 60, endMinute = 9 * 60),
            TimeSpan(epochDay = day2, startMinute = 14 * 60, endMinute = 18 * 60),
        )

        // Act
        val parsed = spans.toParsedBlockers("临时安排")

        // Assert
        assertEquals(2, parsed.size)
        assertEquals("2026-08-16", parsed[0].date)
        assertEquals("2026-08-17", parsed[1].date)
        assertEquals("14:00", parsed[1].start)
        assertEquals("18:00", parsed[1].end)
        assertEquals("临时安排", parsed[0].title)
    }

    @Test
    fun `LLM参数spans缺日期返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(
            mapOf(
                "spans" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "start" to JsonPrimitive("15:00"),
                                "end" to JsonPrimitive("17:00"),
                            )
                        )
                    )
                )
            )
        )

        // Act
        val result = BlockTimeSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数spans结束不晚于开始返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(
            mapOf(
                "spans" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "date" to JsonPrimitive("2026-08-16"),
                                "start" to JsonPrimitive("17:00"),
                                "end" to JsonPrimitive("15:00"),
                            )
                        )
                    )
                )
            )
        )

        // Act
        val result = BlockTimeSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数合法spans通过校验`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(
            mapOf(
                "spans" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "date" to JsonPrimitive("2026-08-16"),
                                "start" to JsonPrimitive("15:00"),
                                "end" to JsonPrimitive("17:00"),
                            )
                        )
                    )
                ),
                "reason" to JsonPrimitive("要去开会"),
            )
        )

        // Act
        val parsed = BlockTimeSkill.parseLlmArgs(args, ctx)

        // Assert
        assertEquals(listOf(TimeSpan(LocalDate.of(2026, 8, 16).toEpochDay(), 900, 1020)), parsed?.get("spans"))
        assertEquals("要去开会", parsed?.get("reason"))
    }

    // ---------- NavigateSkill ----------

    @Test
    fun `打开商城命中商城跳转`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = NavigateSkill.matchRule("打开商城", ctx)

        // Assert
        assertEquals(SkillDestination.SHOP, args?.get("destination"))
    }

    @Test
    fun `切到复盘命中复盘跳转`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = NavigateSkill.matchRule("切到复盘", ctx)

        // Assert
        assertEquals(SkillDestination.STATS, args?.get("destination"))
    }

    @Test
    fun `去开会不命中Navigate`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = NavigateSkill.matchRule("去开会", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数目的地不在枚举返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(mapOf("destination" to JsonPrimitive("MOON")))

        // Act
        val result = NavigateSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }
}
