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

import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import kotlinx.serialization.json.JsonObject

/** 商城商品查询（只读）：概要在结果弹窗里展示，用户点「去商城」细看再买。 */
object QueryShopSkill : AppSkill {

    override val id = "query_shop"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看商城在售商品；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (SHOP_WORDS.none { text.contains(it) }) return null
        if (BROWSE_WORDS.none { text.contains(it) }) return null
        return emptyMap()
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview =
        SkillPreview("查看商城", lines = listOf("查看在售商品与今日精选"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val items = ctx.db.shopDao().activeItems()
        if (items.isEmpty()) {
            return SkillOutcome.Done("商城还没有商品", navigateTo = SkillDestination.SHOP, title = "商城商品")
        }
        val summary = buildString {
            append("商城在售 ${items.size} 件商品：")
            items.take(3).forEach { append("\n· ${it.emoji} ${it.name}（${it.priceCi} CI）") }
            if (items.size > 3) append("\n· …还有 ${items.size - 3} 件")
        }
        return SkillOutcome.Done(summary, navigateTo = SkillDestination.SHOP, title = "商城商品")
    }

    private val SHOP_WORDS = listOf("商城", "商店", "货架")
    private val BROWSE_WORDS = listOf("有什么", "有啥", "卖什么", "逛逛", "看看", "看")
}
