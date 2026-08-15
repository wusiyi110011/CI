package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.SkillTestFixtures.item
import com.wsy.ci.core.voice.skill.SkillTestFixtures.quest
import com.wsy.ci.core.voice.skill.SkillTestFixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** StartTimer / StopTimer / AbandonTimer 的规则匹配，含与完成类/恢复类说法的消歧。 */
class TimerSkillsTest {

    // ---------- StartTimerSkill ----------

    @Test
    fun `开始类动词命中候选返回目标对象`() {
        // Arrange
        val ctx = ctx(listOf(task(1, "Rust 所有权"), quest(10, "机器学习")))

        // Act
        val args = StartTimerSkill.matchRule("开始 Rust 所有权那个任务", ctx)

        // Assert
        assertEquals(1L, args?.get("target")?.let { (it as VoiceTarget).id })
    }

    @Test
    fun `开始类动词也能命中任务线候选`() {
        // Arrange
        val ctx = ctx(listOf(task(1, "Rust 所有权"), quest(10, "机器学习")))

        // Act
        val args = StartTimerSkill.matchRule("我要学机器学习", ctx)

        // Assert
        assertEquals(10L, args?.get("target")?.let { (it as VoiceTarget).id })
    }

    @Test
    fun `重新开始不命中StartTimer留给恢复任务线`() {
        // Arrange
        val ctx = ctx(listOf(quest(10, "机器学习")))

        // Act
        val result = StartTimerSkill.matchRule("重新开始机器学习", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `开始动词命中商品候选不命中StartTimer`() {
        // Arrange
        val ctx = ctx(listOf(item(30, "奶茶")))

        // Act
        val result = StartTimerSkill.matchRule("开始奶茶", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `无开始动词返回null`() {
        // Arrange
        val ctx = ctx(listOf(task(1, "Rust 所有权")))

        // Act
        val result = StartTimerSkill.matchRule("看一下今天的安排", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- StopTimerSkill ----------

    @Test
    fun `完成了命中结束专注`() {
        // Arrange
        val ctx = ctx(emptyList(), hasRunningSession = true)

        // Act
        val args = StopTimerSkill.matchRule("完成了", ctx)

        // Assert
        assertEquals(emptyMap<String, Any?>(), args)
    }

    @Test
    fun `结束专注命中StopTimer`() {
        // Arrange
        val ctx = ctx(emptyList(), hasRunningSession = true)

        // Act
        val args = StopTimerSkill.matchRule("结束专注", ctx)

        // Assert
        assertEquals(emptyMap<String, Any?>(), args)
    }

    @Test
    fun `任务线完成了不命中StopTimer留给CompleteQuest`() {
        // Arrange
        val ctx = ctx(listOf(quest(10, "机器学习")))

        // Act
        val result = StopTimerSkill.matchRule("机器学习任务线完成了", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- AbandonTimerSkill ----------

    @Test
    fun `放弃类说法命中放弃专注`() {
        // Arrange
        val ctx = ctx(emptyList())

        // Act
        val args = AbandonTimerSkill.matchRule("算了不学了", ctx)

        // Assert
        assertEquals(emptyMap<String, Any?>(), args)
    }

    @Test
    fun `完成类说法不命中放弃专注`() {
        // Arrange
        val ctx = ctx(emptyList())

        // Act
        val result = AbandonTimerSkill.matchRule("完成了", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `算了一下的无关语句不命中放弃专注`() {
        // Arrange
        val ctx = ctx(emptyList())

        // Act
        // 「算了」曾经在放弃词表里，会误吞这种高频日常说法
        val result = AbandonTimerSkill.matchRule("帮我算了一下这个月花了多少钱", ctx)

        // Assert
        assertNull(result)
    }
}
