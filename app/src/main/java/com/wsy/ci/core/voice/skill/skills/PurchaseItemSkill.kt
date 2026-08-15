package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.data.PurchaseResult
import com.wsy.ci.core.voice.VoiceTargetKind
import com.wsy.ci.core.voice.VoiceTargetMatcher
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.targetIdOrNull
import com.wsy.ci.core.voice.skill.targetOrNull
import kotlinx.serialization.json.JsonObject

/** 购买商品：花 CI 币，确认卡片走危险态；失败原因要具体（余额不足带上差额）。 */
object PurchaseItemSkill : AppSkill {

    override val id = "purchase_item"
    override val llmSpec = "用 CI 币购买商城商品；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (BUY_WORDS.none { text.contains(it) }) return null
        val item = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.SHOP_ITEM } ?: return null
        return mapOf("targetId" to item.id)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val item = args.targetOrNull(ctx, VoiceTargetKind.SHOP_ITEM) ?: return null
        return mapOf("targetId" to item.id)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val itemId = args.targetIdOrNull()
        val name = ctx.candidates.firstOrNull { it.id == itemId }?.name
        return SkillPreview(
            title = "购买商品",
            lines = listOf("商品 · ${name ?: "未知"}", "将从余额扣除商品价格，请确认"),
            dangerous = true,
        )
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val itemId = args.targetIdOrNull() ?: return SkillOutcome.Failed("商品已失效，请重新说一次")
        return when (val result = ctx.shop.purchase(itemId)) {
            is PurchaseResult.Success ->
                SkillOutcome.Done("已购买「${result.item.name}」，花 ${result.paid} CI")
            is PurchaseResult.NotEnough ->
                SkillOutcome.Failed(
                    "余额不足：需要 ${result.price} CI，当前 ${result.balance} CI，还差 ${result.price - result.balance} CI"
                )
            is PurchaseResult.NotFound -> SkillOutcome.Failed("找不到这个商品，可能已下架")
            is PurchaseResult.Unavailable -> SkillOutcome.Failed("精选折扣已失效，去商城按原价购买吧")
        }
    }

    private val BUY_WORDS = listOf("买", "购买", "兑换", "换")
}
