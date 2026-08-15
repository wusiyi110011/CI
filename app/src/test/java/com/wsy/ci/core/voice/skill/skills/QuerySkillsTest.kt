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

import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.TODAY
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.dateRangeOrNull
import com.wsy.ci.core.voice.skill.targetIdOrNull
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** QueryDomain / QuerySchedule 的规则匹配与 LLM 参数校验。 */
class QuerySkillsTest {

    // ---------- QueryDomainSkill ----------

    @Test
    fun `领域进度类说法命中并解析出领域id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = QueryDomainSkill.matchRule("物理领域什么进度了", ctx)

        // Assert
        assertEquals(20L, args?.targetIdOrNull())
    }

    @Test
    fun `领域怎么样也能命中`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = QueryDomainSkill.matchRule("物理怎么样", ctx)

        // Assert
        assertEquals(20L, args?.targetIdOrNull())
    }

    @Test
    fun `领域的日程问题不命中QueryDomain留给QuerySchedule`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = QueryDomainSkill.matchRule("明天物理有什么安排", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数领域id不在候选清单返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(mapOf("targetId" to JsonPrimitive(999)))

        // Act
        val result = QueryDomainSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数targetId是嵌套对象返回null而不崩溃`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(
            mapOf("targetId" to JsonObject(mapOf("x" to JsonPrimitive(1))))
        )

        // Act
        val result = QueryDomainSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    // ---------- QueryScheduleSkill ----------

    @Test
    fun `查询类词解析出日期区间命中`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val expected = LocalDate.of(2026, 8, 12).toEpochDay()

        // Act
        val args = QueryScheduleSkill.matchRule("看一下这周三的安排", ctx)

        // Assert
        assertEquals(expected, args?.get("from"))
        assertEquals(expected, args?.get("to"))
    }

    @Test
    fun `今天干了什么也能识别成日程查询`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = QueryScheduleSkill.matchRule("今天干了什么", ctx)

        // Assert
        assertEquals(TODAY.toEpochDay(), args?.get("from"))
        assertEquals(TODAY.toEpochDay(), args?.get("to"))
    }

    @Test
    fun `领域进度问题不命中QuerySchedule`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = QueryScheduleSkill.matchRule("物理领域什么进度了", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数from缺失返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(mapOf("from" to JsonPrimitive("2026-08-17")))

        // Act
        val result = QueryScheduleSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数区间倒挂返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(
            mapOf(
                "from" to JsonPrimitive("2026-08-20"),
                "to" to JsonPrimitive("2026-08-17"),
            )
        )

        // Act
        val result = QueryScheduleSkill.parseLlmArgs(args, ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `LLM参数合法日期区间通过校验`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = JsonObject(
            mapOf(
                "from" to JsonPrimitive("2026-08-17"),
                "to" to JsonPrimitive("2026-08-23"),
            )
        )

        // Act
        val parsed = QueryScheduleSkill.parseLlmArgs(args, ctx)

        // Assert
        assertEquals(LocalDate.of(2026, 8, 17).toEpochDay(), parsed?.get("from"))
        assertEquals(LocalDate.of(2026, 8, 23).toEpochDay(), parsed?.get("to"))
    }
}
