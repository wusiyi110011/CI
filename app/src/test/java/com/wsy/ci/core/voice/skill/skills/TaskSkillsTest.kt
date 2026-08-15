package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.targetIdOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CompleteTask / SkipTask / DeleteTask 的规则匹配：任务目标消歧与计时状态联动。 */
class TaskSkillsTest {

    // ---------- CompleteTaskSkill ----------

    @Test
    fun `未计时时完成类说法命中任务并解析出任务id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = CompleteTaskSkill.matchRule("把 Rust 所有权做完了", ctx)

        // Assert
        assertEquals(1L, args?.targetIdOrNull())
    }

    @Test
    fun `计时中完成类说法不命中CompleteTask留给StopTimer结算`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES, hasRunningSession = true)

        // Act
        val result = CompleteTaskSkill.matchRule("把 Rust 所有权做完了", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `干完了命中CompleteTask而不是StartTimer`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES, hasRunningSession = false)

        // Act
        val args = CompleteTaskSkill.matchRule("把 Rust 所有权干完了", ctx)

        // Assert
        assertEquals(1L, args?.targetIdOrNull())
    }

    @Test
    fun `自由专注进行中也不命中CompleteTask`() {
        // Arrange
        // 对着任务线直接开工时 session 不挂任务，此时「做完了」仍应走结算
        val ctx = ctx(STANDARD_CANDIDATES, hasRunningSession = true)

        // Act
        val result = CompleteTaskSkill.matchRule("机器学习做完了", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `无法定位任务名时返回null`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = CompleteTaskSkill.matchRule("这个任务做完了", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `完成任务线不命中CompleteTask`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = CompleteTaskSkill.matchRule("机器学习任务线完成了", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- SkipTaskSkill ----------

    @Test
    fun `跳过任务命中并解析出任务id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = SkipTaskSkill.matchRule("跳过 Rust 所有权", ctx)

        // Assert
        assertEquals(1L, args?.targetIdOrNull())
    }

    @Test
    fun `跳过任务线不命中SkipTask`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = SkipTaskSkill.matchRule("跳过机器学习", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- DeleteTaskSkill ----------

    @Test
    fun `删掉任务命中并解析出任务id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = DeleteTaskSkill.matchRule("删掉 Rust 所有权", ctx)

        // Assert
        assertEquals(1L, args?.targetIdOrNull())
    }

    @Test
    fun `删掉任务线不命中DeleteTask留给DeleteQuest`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = DeleteTaskSkill.matchRule("删掉机器学习", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `删除类危险操作预览带警示标记`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = DeleteTaskSkill.matchRule("删掉 Rust 所有权", ctx)!!

        // Act
        val preview = DeleteTaskSkill.preview(args, ctx)

        // Assert
        assertEquals(true, preview.dangerous)
    }
}
