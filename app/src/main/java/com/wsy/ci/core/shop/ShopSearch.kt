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
import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.db.ShopItemEntity

/**
 * 商城的本地关键词过滤：大小写不敏感的包含匹配，空白关键词原样返回。
 * 个人自用的商品量级（几百件封顶）不值得为搜索建 FTS 索引，内存过滤足够。
 */
object ShopSearch {

    private fun String.matches(query: String): Boolean = contains(query, ignoreCase = true)

    private fun normalized(query: String): String = query.trim()

    /** 货架：按商品名与描述匹配。 */
    fun shelf(items: List<ShopItemEntity>, query: String): List<ShopItemEntity> {
        val q = normalized(query)
        if (q.isEmpty()) return items
        return items.filter { it.name.matches(q) || it.description.matches(q) }
    }

    /** 我的：按兑换时的商品名匹配。 */
    fun purchases(purchases: List<PurchaseEntity>, query: String): List<PurchaseEntity> {
        val q = normalized(query)
        if (q.isEmpty()) return purchases
        return purchases.filter { it.itemName.matches(q) }
    }

    /**
     * 流水：按备注匹配；备注为空时按类型的默认文案兜底（与列表展示同一口径），
     * 这样搜「兑换」也能把带备注的商城支出全找出来。
     */
    fun ledger(
        entries: List<LedgerEntity>,
        query: String,
        typeLabel: (LedgerEntity) -> String,
    ): List<LedgerEntity> {
        val q = normalized(query)
        if (q.isEmpty()) return entries
        return entries.filter { it.note.matches(q) || typeLabel(it).matches(q) }
    }
}
