package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.targetIdOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CompleteQuest / ArchiveQuest / RestoreQuest / DeleteQuest 的规则匹配：任务线目标消歧。 */
class QuestSkillsTest {

    // ---------- CompleteQuestSkill ----------

    @Test
    fun `任务线完成了命中并解析出任务线id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = CompleteQuestSkill.matchRule("机器学习任务线完成了", ctx)

        // Assert
        assertEquals(10L, args?.targetIdOrNull())
    }

    @Test
    fun `光说完成了不命中CompleteQuest`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = CompleteQuestSkill.matchRule("完成了", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- ArchiveQuestSkill ----------

    @Test
    fun `归档任务线命中并解析出任务线id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = ArchiveQuestSkill.matchRule("把机器学习归档", ctx)

        // Assert
        assertEquals(10L, args?.targetIdOrNull())
    }

    @Test
    fun `归档任务不命中ArchiveQuest`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = ArchiveQuestSkill.matchRule("把 Rust 所有权归档", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- RestoreQuestSkill ----------

    @Test
    fun `恢复任务线命中并解析出任务线id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = RestoreQuestSkill.matchRule("恢复机器学习", ctx)

        // Assert
        assertEquals(10L, args?.targetIdOrNull())
    }

    @Test
    fun `开始说法不命中RestoreQuest`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = RestoreQuestSkill.matchRule("开始机器学习", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- DeleteQuestSkill ----------

    @Test
    fun `删掉任务线命中并解析出任务线id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = DeleteQuestSkill.matchRule("删掉机器学习", ctx)

        // Assert
        assertEquals(10L, args?.targetIdOrNull())
    }

    @Test
    fun `删掉任务不命中DeleteQuest`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = DeleteQuestSkill.matchRule("删掉 Rust 所有权", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `删除任务线危险操作预览带警示标记`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = DeleteQuestSkill.matchRule("删掉机器学习", ctx)!!

        // Act
        val preview = DeleteQuestSkill.preview(args, ctx)

        // Assert
        assertEquals(true, preview.dangerous)
    }
}
