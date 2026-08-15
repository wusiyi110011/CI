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

package com.wsy.ci.core.porting

import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.Rarity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 单件待上架商品。价格锚定汇率 1 元 ≈ 20 CI。 */
@Serializable
data class ImportShopItem(
    val name: String,
    val description: String = "",
    val emoji: String = "🎁",
    val priceCi: Long,
    /** COMMON 普通 / RARE 稀有 / EPIC 史诗 / LEGENDARY 传说。 */
    val rarity: String = "COMMON",
)

@Serializable
data class ShopImportFile(
    val version: Int = 1,
    val items: List<ImportShopItem> = emptyList(),
)

sealed interface ShopImportResult {
    data class Ok(val items: List<ImportShopItem>) : ShopImportResult
    data class Err(val errors: List<String>) : ShopImportResult
}

/**
 * 商城货架导入：一次粘贴上架一批奖励，省得一件件敲。
 * 和学习计划导入（[CiImport]）是两套独立格式，共用同一种宽容的文本抠取。
 */
object ShopImport {

    const val SUPPORTED_VERSION = 1

    /** 单件价格上限：再贵也不该是一次性输入错误（如手滑多打几个 0）。 */
    const val MAX_PRICE_CI = 1_000_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): ShopImportResult {
        val file = try {
            json.decodeFromString<ShopImportFile>(extractJsonObject(text))
        } catch (e: Exception) {
            return ShopImportResult.Err(listOf("JSON 解析失败：${e.message?.take(120)}"))
        }
        val errors = validate(file)
        return if (errors.isEmpty()) ShopImportResult.Ok(file.items) else ShopImportResult.Err(errors)
    }

    fun validate(file: ShopImportFile): List<String> {
        val errors = mutableListOf<String>()
        if (file.version != SUPPORTED_VERSION) errors.add("version 必须为 $SUPPORTED_VERSION")
        if (file.items.isEmpty()) errors.add("items 为空：至少填一件商品")
        file.items.forEachIndexed { i, item ->
            val where = "items[$i]"
            if (item.name.isBlank()) errors.add("$where.name 不能为空")
            if (item.priceCi <= 0) errors.add("$where.priceCi 必须大于 0")
            if (item.priceCi > MAX_PRICE_CI) {
                errors.add("$where.priceCi 超过上限 $MAX_PRICE_CI（是不是多打了几个 0）")
            }
            if (parseRarity(item.rarity) == null) {
                errors.add("$where.rarity 必须是 COMMON/RARE/EPIC/LEGENDARY")
            }
        }
        // 同一批里重名会在货架上并排出现两件一样的，先拦下来
        file.items.map { it.name.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
            .forEach { errors.add("items 里「$it」重复出现") }
        return errors
    }

    fun parseRarity(text: String): Rarity? =
        Rarity.entries.firstOrNull { it.name == text.trim().uppercase() }

    /** 转成货架实体。校验通过后才调用，所以这里的 rarity 一定解析得出。 */
    fun toEntity(item: ImportShopItem): ShopItemEntity = ShopItemEntity(
        name = item.name.trim(),
        description = item.description.trim(),
        emoji = item.emoji.trim().ifBlank { "🎁" },
        priceCi = item.priceCi,
        rarity = parseRarity(item.rarity) ?: Rarity.COMMON,
    )

    /** 给外部 AI 的模板：改完内容直接粘回来即可。 */
    val TEMPLATE = """
请按下面的 JSON 格式帮我设计一批「奖励商品」，只输出 JSON：
{
  "version": 1,
  "items": [
    {
      "name": "看一场电影",
      "description": "一句话说明，可不填",
      "emoji": "🎬",
      "priceCi": 800,
      "rarity": "RARE"
    },
    {
      "name": "一杯喜欢的咖啡",
      "emoji": "☕",
      "priceCi": 400,
      "rarity": "COMMON"
    }
  ]
}
字段说明：priceCi 是价格，锚定汇率 1 元 ≈ 20 CI（40 元的电影票 ≈ 800 CI）；
rarity 取 COMMON(普通)/RARE(稀有)/EPIC(史诗)/LEGENDARY(传说)，越贵越稀有；
emoji 一个字符；description 可省略。名字不要重复。
""".trimIndent()
}
