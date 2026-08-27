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

package com.wsy.ci.core.shop

import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.Rarity
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopSearchTest {

    private fun item(id: Long, name: String, description: String = "") = ShopItemEntity(
        id = id, name = name, description = description, priceCi = 100, rarity = Rarity.COMMON,
    )

    @Test
    fun `空白关键词原样返回`() {
        val items = listOf(item(1, "电影票"), item(2, "Switch 游戏卡带"))
        assertEquals(items, ShopSearch.shelf(items, ""))
        assertEquals(items, ShopSearch.shelf(items, "   "))
    }

    @Test
    fun `货架按名称或描述匹配且大小写不敏感`() {
        val items = listOf(
            item(1, "看一场电影"),
            item(2, "iPhone 17", description = "攒三个月专注换一部"),
            item(3, "咖啡券"),
        )
        assertEquals(listOf(items[0]), ShopSearch.shelf(items, "电影"))
        assertEquals(listOf(items[1]), ShopSearch.shelf(items, "iphone"))
        assertEquals(listOf(items[1]), ShopSearch.shelf(items, "专注"))
    }

    @Test
    fun `没有匹配时返回空列表`() {
        val items = listOf(item(1, "电影票"))
        assertEquals(emptyList<ShopItemEntity>(), ShopSearch.shelf(items, "不存在的奖励"))
    }

    @Test
    fun `兑换记录按商品名匹配`() {
        val purchases = listOf(
            PurchaseEntity(id = 1, itemId = 1, itemName = "看一场电影", pricePaid = 120),
            PurchaseEntity(id = 2, itemId = 2, itemName = "Switch 游戏卡带", pricePaid = 2000),
        )
        assertEquals(listOf(purchases[1]), ShopSearch.purchases(purchases, "switch"))
    }

    @Test
    fun `流水按备注匹配空备注回退到类型文案`() {
        val entries = listOf(
            LedgerEntity(id = 1, amount = 50, type = LedgerType.SPEND_SHOP, note = "看一场电影"),
            LedgerEntity(id = 2, amount = 30, type = LedgerType.SPEND_SHOP, note = ""),
            LedgerEntity(id = 3, amount = 100, type = LedgerType.EARN_TASK, note = "数学第三章"),
        )
        val label: (LedgerEntity) -> String = {
            if (it.type == LedgerType.SPEND_SHOP) "商城兑换" else "专注入账"
        }

        // 命中备注
        assertEquals(listOf(entries[0]), ShopSearch.ledger(entries, "电影", label))
        // 命中类型文案：备注为空的靠它兜底，带备注的同类型也能一起搜出来
        assertEquals(listOf(entries[0], entries[1]), ShopSearch.ledger(entries, "兑换", label))
        // 不同类型互不串
        assertEquals(listOf(entries[2]), ShopSearch.ledger(entries, "专注", label))
    }
}
