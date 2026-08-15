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

import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.economy.Rarity

/** 「我的」的实现状态筛选。 */
enum class FulfillFilter(val label: String) {
    ALL("全部"),
    PENDING("未实现"),
    DONE("已实现"),
}

/** 「我的」的时间范围筛选。[days] 为 null 表示不限。 */
enum class TimeFilter(val label: String, val days: Long?) {
    ALL("不限", null),
    WEEK("近 7 天", 7),
    MONTH("近 30 天", 30),
    QUARTER("近 90 天", 90),
}

/** 一次筛选条件。[rarities] 为空表示不限品质（默认全部显示）。 */
data class PurchaseFilter(
    val rarities: Set<Rarity> = emptySet(),
    val fulfill: FulfillFilter = FulfillFilter.ALL,
    val time: TimeFilter = TimeFilter.ALL,
) {
    val isDefault: Boolean
        get() = rarities.isEmpty() && fulfill == FulfillFilter.ALL && time == TimeFilter.ALL
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * 「我的」列表的筛选与排序。
 *
 * 排序固定为：未实现在前、已实现在后；两组内部都按品质从高到低，
 * 同品质再按兑换时间由近及远。未实现的是待兑现的心愿，越贵重越该先安排，
 * 所以它排在最上面；已实现的沉到下面当战利品陈列。
 */
object PurchaseBoard {

    fun apply(
        purchases: List<PurchaseEntity>,
        filter: PurchaseFilter,
        nowMillis: Long,
    ): List<PurchaseEntity> {
        val since = filter.time.days?.let { nowMillis - it * MILLIS_PER_DAY }
        return purchases
            .filter { filter.rarities.isEmpty() || it.rarity in filter.rarities }
            .filter {
                when (filter.fulfill) {
                    FulfillFilter.ALL -> true
                    FulfillFilter.PENDING -> !it.fulfilled
                    FulfillFilter.DONE -> it.fulfilled
                }
            }
            .filter { since == null || it.at >= since }
            .sortedWith(
                compareBy<PurchaseEntity> { it.fulfilled }
                    .thenByDescending { it.rarity.ordinal }
                    .thenByDescending { it.at }
            )
    }
}
