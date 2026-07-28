package com.wsy.ci.core.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultShopItemsTest {

    @Test
    fun `四档数量分别是10_8_5_3`() {
        val byRarity = DefaultShopItems.ALL.groupingBy { it.rarity }.eachCount()

        assertEquals(10, byRarity[Rarity.COMMON])
        assertEquals(8, byRarity[Rarity.RARE])
        assertEquals(5, byRarity[Rarity.EPIC])
        assertEquals(3, byRarity[Rarity.LEGENDARY])
    }

    @Test
    fun `商品名不重复否则去重铺货会漏件`() {
        val names = DefaultShopItems.ALL.map { it.name }

        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `品质越高价格档位越高不重叠`() {
        val maxOf = { r: Rarity -> DefaultShopItems.ALL.filter { it.rarity == r }.maxOf { it.priceCi } }
        val minOf = { r: Rarity -> DefaultShopItems.ALL.filter { it.rarity == r }.minOf { it.priceCi } }

        assertTrue(maxOf(Rarity.COMMON) < minOf(Rarity.RARE))
        assertTrue(maxOf(Rarity.RARE) < minOf(Rarity.EPIC))
        assertTrue(maxOf(Rarity.EPIC) < minOf(Rarity.LEGENDARY))
    }

    @Test
    fun `传说档压在6万以内保证半年内够得着`() {
        val maxPrice = DefaultShopItems.ALL.maxOf { it.priceCi }

        assertTrue("最贵商品 $maxPrice 超过 60000，攒起来会超过一年", maxPrice <= 60_000)
    }

    @Test
    fun `种子数据不带id交给Room自增`() {
        assertTrue(DefaultShopItems.ALL.all { it.id == 0L })
    }
}
