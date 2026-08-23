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

import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.TODAY
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 新增高频技能的规则解析与二次参数校验测试。 */
class ExtendedVoiceSkillsTest {
    @Test
    fun `结束专注支持三种结算状态和完成备注`() {
        val context = ctx(emptyList(), hasRunningSession = true)
        assertEquals(FocusOutcome.OVERTIME, StopTimerSkill.matchRule("超时完成", context)?.get("outcome"))
        assertEquals(emptyMap<String, Any?>(), AbandonTimerSkill.matchRule("放弃", context))
        assertNull(StopTimerSkill.matchRule("放弃", context))
        val args = StopTimerSkill.matchRule("完成了，备注：复习了积分", context)
        assertEquals(FocusOutcome.COMPLETED, args?.get("outcome"))
        assertEquals("复习了积分", args?.get("note"))
    }

    @Test
    fun `结束专注没有session时由执行层返回失败而不是伪造结算`() {
        // 规则层只负责意图；真正的无 session 检查在 StopTimerSkill.execute 的仓库调用处完成。
        assertEquals(emptyMap<String, Any?>(), StopTimerSkill.matchRule("完成了", ctx(emptyList())))
    }

    @Test
    fun `创建任务的时间和名称通过基础口语解析`() {
        val args = CreateTaskSkill.matchRule("创建任务：背单词，明天9点到10点", ctx(STANDARD_CANDIDATES))!!
        assertEquals("背单词", args["title"])
        assertEquals(TODAY.plusDays(1).toEpochDay(), args["epochDay"])
        assertEquals(9 * 60, args["startMinute"])
        assertEquals(10 * 60, args["endMinute"])
    }

    @Test
    fun `创建和移动任务拒绝倒挂或过去时间`() {
        val context = ctx(STANDARD_CANDIDATES)
        val invalid = JsonObject(
            mapOf(
                "title" to JsonPrimitive("背单词"),
                "date" to JsonPrimitive(TODAY.toString()),
                "start" to JsonPrimitive("10:00"),
                "end" to JsonPrimitive("09:00"),
            )
        )
        assertNull(CreateTaskSkill.parseLlmArgs(invalid, context))
        val past = JsonObject(
            mapOf(
                "targetId" to JsonPrimitive(1),
                "date" to JsonPrimitive(TODAY.minusDays(1).toString()),
                "start" to JsonPrimitive("09:00"),
                "end" to JsonPrimitive("10:00"),
            )
        )
        assertNull(MoveTaskSkill.parseLlmArgs(past, context))
        val tooFar = JsonObject(
            mapOf(
                "title" to JsonPrimitive("背单词"),
                "epochDay" to JsonPrimitive(Long.MAX_VALUE),
                "start" to JsonPrimitive("09:00"),
                "end" to JsonPrimitive("10:00"),
            )
        )
        assertNull(CreateTaskSkill.parseLlmArgs(tooFar, context))
    }

    @Test
    fun `锁定难度备注和截止日期规则命中`() {
        val context = ctx(STANDARD_CANDIDATES)
        assertEquals(1L, LockTaskSkill.matchRule("锁定 Rust 所有权", context)?.get("target")?.let { (it as com.wsy.ci.core.voice.VoiceTarget).id })
        assertEquals(Difficulty.HARD, SetTaskDifficultySkill.matchRule("把 Rust 所有权调成困难", context)?.get("difficulty"))
        assertEquals("复习错题", SetTaskNoteSkill.matchRule("给 Rust 所有权备注：复习错题", context)?.get("note"))
        assertEquals(TODAY.plusDays(1).toEpochDay(), SetQuestDeadlineSkill.matchRule("机器学习截止明天", context)?.get("deadlineEpochDay"))
    }
}
