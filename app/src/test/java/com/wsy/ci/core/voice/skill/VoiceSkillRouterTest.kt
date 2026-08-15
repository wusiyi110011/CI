package com.wsy.ci.core.voice.skill

import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.skills.AbandonTimerSkill
import com.wsy.ci.core.voice.skill.skills.ArchiveQuestSkill
import com.wsy.ci.core.voice.skill.skills.BlockTimeSkill
import com.wsy.ci.core.voice.skill.skills.CompleteQuestSkill
import com.wsy.ci.core.voice.skill.skills.CompleteTaskSkill
import com.wsy.ci.core.voice.skill.skills.DeleteQuestSkill
import com.wsy.ci.core.voice.skill.skills.DeleteTaskSkill
import com.wsy.ci.core.voice.skill.skills.NavigateSkill
import com.wsy.ci.core.voice.skill.skills.PurchaseItemSkill
import com.wsy.ci.core.voice.skill.skills.QueryDomainSkill
import com.wsy.ci.core.voice.skill.skills.QueryScheduleSkill
import com.wsy.ci.core.voice.skill.skills.QueryShopSkill
import com.wsy.ci.core.voice.skill.skills.RestoreQuestSkill
import com.wsy.ci.core.voice.skill.skills.SkipTaskSkill
import com.wsy.ci.core.voice.skill.skills.StartTimerSkill
import com.wsy.ci.core.voice.skill.skills.StopTimerSkill
import com.wsy.ci.llm.ParsedSkillCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 路由三类分支：规则命中 / 规则不命中转 LLM 查表 / LLM 结果候选校验不过退化为未识别。 */
class VoiceSkillRouterTest {

    private val router = VoiceSkillRouter(
        SkillRegistry(listOf(PurchaseItemSkill, QueryScheduleSkill, NavigateSkill))
    )
    private val ctx = ctx(STANDARD_CANDIDATES)

    /** 与生产 `CiApp` 一致的完整注册表，验证登记顺序下的穿透语义（A 未命中继续尝试 B）。 */
    private val productionRouter = VoiceSkillRouter(
        SkillRegistry(
            listOf(
                StartTimerSkill,
                CompleteTaskSkill,
                CompleteQuestSkill,
                StopTimerSkill,
                AbandonTimerSkill,
                SkipTaskSkill,
                DeleteTaskSkill,
                ArchiveQuestSkill,
                RestoreQuestSkill,
                DeleteQuestSkill,
                QueryShopSkill,
                PurchaseItemSkill,
                QueryDomainSkill,
                QueryScheduleSkill,
                BlockTimeSkill,
                NavigateSkill,
            )
        )
    )

    @Test
    fun `删掉任务线穿透DeleteTask落到DeleteQuest`() {
        // Arrange
        val ruleCtx = ctx(STANDARD_CANDIDATES)

        // Act
        val invocation = productionRouter.matchByRule("删掉机器学习", ruleCtx)

        // Assert
        assertEquals("delete_quest", invocation?.skill?.id)
        assertEquals(10L, invocation?.args?.targetIdOrNull())
    }

    @Test
    fun `完成任务线穿透完成类技能落到CompleteQuest`() {
        // Arrange
        val ruleCtx = ctx(STANDARD_CANDIDATES)

        // Act
        val invocation = productionRouter.matchByRule("机器学习任务线完成了", ruleCtx)

        // Assert
        assertEquals("complete_quest", invocation?.skill?.id)
    }

    @Test
    fun `做完了穿透到CompleteTask并保住计时结算语义`() {
        // Arrange
        val idleCtx = ctx(STANDARD_CANDIDATES)
        val runningCtx = ctx(STANDARD_CANDIDATES, hasRunningSession = true)

        // Act
        val idleInvocation = productionRouter.matchByRule("把 Rust 所有权做完了", idleCtx)
        val runningInvocation = productionRouter.matchByRule("把 Rust 所有权做完了", runningCtx)

        // Assert
        assertEquals("complete_task", idleInvocation?.skill?.id)
        assertEquals("stop_timer", runningInvocation?.skill?.id)
    }

    @Test
    fun `不搞了这类说法不再被StartTimer截胡`() {
        // Arrange
        val ruleCtx = ctx(STANDARD_CANDIDATES)

        // Act
        val invocation = productionRouter.matchByRule("不搞机器学习了", ruleCtx)

        // Assert
        // 「不搞机器学习了」规则层没有明确的放弃词命中（「不搞了」不连续），留给 LLM 兜底
        assertNull(invocation)
    }

    @Test
    fun `规则命中返回对应技能`() {
        // Arrange
        val text = "买奶茶"

        // Act
        val invocation = router.matchByRule(text, ctx)

        // Assert
        assertEquals("purchase_item", invocation?.skill?.id)
        assertEquals(30L, invocation?.args?.targetIdOrNull())
    }

    @Test
    fun `规则不命中时LLM结果按skill字段查表`() {
        // Arrange
        val parsed = ParsedSkillCall("purchase_item", JsonObject(mapOf("targetId" to JsonPrimitive(30))))

        // Act
        val invocation = router.matchFromLlm(parsed, ctx)

        // Assert
        assertEquals("purchase_item", invocation?.skill?.id)
        assertEquals(30L, invocation?.args?.targetIdOrNull())
    }

    @Test
    fun `LLM结果targetId不在候选清单退化为未识别`() {
        // Arrange
        val parsed = ParsedSkillCall("purchase_item", JsonObject(mapOf("targetId" to JsonPrimitive(999))))

        // Act
        val invocation = router.matchFromLlm(parsed, ctx)

        // Assert
        assertNull(invocation)
    }

    @Test
    fun `LLM结果targetId kind不符退化为未识别`() {
        // Arrange
        // 1 是任务 id，purchase_item 只认商品
        val parsed = ParsedSkillCall("purchase_item", JsonObject(mapOf("targetId" to JsonPrimitive(1))))

        // Act
        val invocation = router.matchFromLlm(parsed, ctx)

        // Assert
        assertNull(invocation)
    }

    @Test
    fun `LLM结果skill不在注册表退化为未识别`() {
        // Arrange
        val parsed = ParsedSkillCall("delete_quest", JsonObject(mapOf("targetId" to JsonPrimitive(10))))

        // Act
        val invocation = router.matchFromLlm(parsed, ctx)

        // Assert
        assertNull(invocation)
    }

    @Test
    fun `注册表skill id重复直接抛错`() {
        // Arrange + Act
        val error = runCatching {
            SkillRegistry(listOf(NavigateSkill, NavigateSkill))
        }.exceptionOrNull()

        // Assert
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
