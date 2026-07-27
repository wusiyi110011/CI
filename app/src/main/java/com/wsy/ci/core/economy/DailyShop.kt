package com.wsy.ci.core.economy

import kotlin.random.Random

/** 每日精选抽取结果：商品 id + 当日折扣（10~20，表示打 9 折~8 折）。 */
data class DailyPick(val itemId: Long, val discountPercent: Int)

/**
 * 每日商店精选抽取，纯函数：同一天（同 seed）结果稳定可复现。
 *
 * 流程：按 45/35/15/5 权重独立抽 4 次品质 → 每个品质从对应商品池不放回随机取 1 件
 * （池空则品质降一档，普通空则升档）→ 每件随机 10~20% 折扣。
 */
object DailyShop {

    const val PICK_COUNT = 4
    private const val DISCOUNT_MIN = 10
    private const val DISCOUNT_MAX = 20

    /** epochDay 作种子，保证同一天重复刷新结果一致。 */
    fun seedForDay(epochDay: Long): Long = epochDay

    fun rollRarity(random: Random): Rarity {
        val total = Rarity.entries.sumOf { it.weight }
        var roll = random.nextInt(total)
        for (rarity in Rarity.entries) {
            roll -= rarity.weight
            if (roll < 0) return rarity
        }
        return Rarity.COMMON
    }

    /**
     * @param itemsByRarity 各品质在售商品 id 池
     * @return 至多 4 件精选；全部池为空时返回空列表
     */
    fun rollDailyPicks(
        itemsByRarity: Map<Rarity, List<Long>>,
        seed: Long,
    ): List<DailyPick> {
        val random = Random(seed)
        val pools = Rarity.entries.associateWith {
            (itemsByRarity[it] ?: emptyList()).toMutableList()
        }
        val picks = mutableListOf<DailyPick>()
        repeat(PICK_COUNT) {
            val rarity = rollRarity(random)
            val itemId = drawFromPools(pools, rarity, random) ?: return@repeat
            val discount = random.nextInt(DISCOUNT_MIN, DISCOUNT_MAX + 1)
            picks.add(DailyPick(itemId, discount))
        }
        return picks
    }

    /** 目标品质池空则先降档再升档找非空池，不放回抽取。 */
    private fun drawFromPools(
        pools: Map<Rarity, MutableList<Long>>,
        target: Rarity,
        random: Random,
    ): Long? {
        val order = Rarity.entries
        val start = order.indexOf(target)
        val searchOrder = (start downTo 0) + (start + 1 until order.size)
        for (i in searchOrder) {
            val pool = pools.getValue(order[i])
            if (pool.isNotEmpty()) {
                return pool.removeAt(random.nextInt(pool.size))
            }
        }
        return null
    }

    /** 折扣后价格：向上取整避免出现 0 价。 */
    fun discountedPrice(price: Long, discountPercent: Int): Long {
        val discounted = price * (100 - discountPercent) / 100.0
        return kotlin.math.ceil(discounted).toLong().coerceAtLeast(1)
    }
}
