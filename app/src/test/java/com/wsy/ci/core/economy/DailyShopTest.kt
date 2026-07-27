package com.wsy.ci.core.economy

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyShopTest {

    private val fullPools: Map<Rarity, List<Long>> = mapOf(
        Rarity.COMMON to (1L..10L).toList(),
        Rarity.RARE to (11L..20L).toList(),
        Rarity.EPIC to (21L..30L).toList(),
        Rarity.LEGENDARY to (31L..40L).toList(),
    )

    @Test
    fun `品质抽取分布万次采样在45-35-15-5正负2个百分点内`() {
        val random = Random(42)
        val counts = mutableMapOf<Rarity, Int>()
        val n = 100_000
        repeat(n) {
            val r = DailyShop.rollRarity(random)
            counts[r] = (counts[r] ?: 0) + 1
        }
        Rarity.entries.forEach { rarity ->
            val actualPct = counts.getValue(rarity) * 100.0 / n
            assertTrue(
                "$rarity 期望 ${rarity.weight}% 实际 $actualPct%",
                kotlin.math.abs(actualPct - rarity.weight) < 2.0,
            )
        }
    }

    @Test
    fun `同一seed结果可复现`() {
        val a = DailyShop.rollDailyPicks(fullPools, 20026)
        val b = DailyShop.rollDailyPicks(fullPools, 20026)
        assertEquals(a, b)
    }

    @Test
    fun `不同seed结果大概率不同`() {
        val days = (1L..50L)
        val distinct = days.map { DailyShop.rollDailyPicks(fullPools, it) }.distinct()
        assertTrue(distinct.size > 40)
    }

    @Test
    fun `抽满4件且不重复`() {
        val picks = DailyShop.rollDailyPicks(fullPools, 1)
        assertEquals(4, picks.size)
        assertEquals(4, picks.map { it.itemId }.distinct().size)
    }

    @Test
    fun `折扣在10到20之间`() {
        (1L..200L).forEach { seed ->
            DailyShop.rollDailyPicks(fullPools, seed).forEach {
                assertTrue(it.discountPercent in 10..20)
            }
        }
    }

    @Test
    fun `池空降档不崩溃`() {
        val onlyCommon = mapOf(Rarity.COMMON to listOf(1L, 2L))
        val picks = DailyShop.rollDailyPicks(onlyCommon, 7)
        assertEquals(2, picks.size)
    }

    @Test
    fun `全空返回空列表`() {
        assertTrue(DailyShop.rollDailyPicks(emptyMap(), 7).isEmpty())
    }

    @Test
    fun `商品不足4件全部上架`() {
        val pools = mapOf(
            Rarity.COMMON to listOf(1L),
            Rarity.LEGENDARY to listOf(2L),
        )
        val picks = DailyShop.rollDailyPicks(pools, 3)
        assertEquals(2, picks.size)
    }

    @Test
    fun `折扣价向上取整且不为0`() {
        assertEquals(80L, DailyShop.discountedPrice(100, 20))
        assertEquals(90L, DailyShop.discountedPrice(100, 10))
        assertEquals(1L, DailyShop.discountedPrice(1, 20))
        assertEquals(9L, DailyShop.discountedPrice(10, 15))
    }
}
