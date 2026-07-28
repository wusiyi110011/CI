package com.wsy.ci.core.shop

import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.economy.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseBoardTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun purchase(
        id: Long,
        rarity: Rarity,
        fulfilled: Boolean = false,
        daysAgo: Long = 0,
    ) = PurchaseEntity(
        id = id,
        itemId = id,
        itemName = "奖励$id",
        pricePaid = 100,
        at = now - daysAgo * day,
        fulfilled = fulfilled,
        rarity = rarity,
    )

    @Test
    fun `默认条件下全部显示`() {
        val all = listOf(
            purchase(1, Rarity.COMMON),
            purchase(2, Rarity.LEGENDARY, fulfilled = true),
        )

        val result = PurchaseBoard.apply(all, PurchaseFilter(), now)

        assertEquals(2, result.size)
        assertTrue(PurchaseFilter().isDefault)
    }

    @Test
    fun `未实现整体排在已实现之前`() {
        val all = listOf(
            purchase(1, Rarity.LEGENDARY, fulfilled = true),
            purchase(2, Rarity.COMMON, fulfilled = false),
        )

        val result = PurchaseBoard.apply(all, PurchaseFilter(), now)

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `两组内部都按品质从高到低`() {
        val all = listOf(
            purchase(1, Rarity.COMMON),
            purchase(2, Rarity.LEGENDARY),
            purchase(3, Rarity.RARE),
            purchase(4, Rarity.EPIC),
            purchase(5, Rarity.COMMON, fulfilled = true),
            purchase(6, Rarity.EPIC, fulfilled = true),
        )

        val result = PurchaseBoard.apply(all, PurchaseFilter(), now)

        assertEquals(listOf(2L, 4L, 3L, 1L, 6L, 5L), result.map { it.id })
    }

    @Test
    fun `同品质内按兑换时间由近及远`() {
        val all = listOf(
            purchase(1, Rarity.RARE, daysAgo = 5),
            purchase(2, Rarity.RARE, daysAgo = 1),
            purchase(3, Rarity.RARE, daysAgo = 3),
        )

        val result = PurchaseBoard.apply(all, PurchaseFilter(), now)

        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun `品质多选是并集`() {
        val all = listOf(
            purchase(1, Rarity.COMMON),
            purchase(2, Rarity.RARE),
            purchase(3, Rarity.EPIC),
            purchase(4, Rarity.LEGENDARY),
        )

        val result = PurchaseBoard.apply(
            all,
            PurchaseFilter(rarities = setOf(Rarity.RARE, Rarity.LEGENDARY)),
            now,
        )

        assertEquals(listOf(4L, 2L), result.map { it.id })
    }

    @Test
    fun `只看未实现时滤掉已实现`() {
        val all = listOf(
            purchase(1, Rarity.COMMON),
            purchase(2, Rarity.COMMON, fulfilled = true),
        )

        val result = PurchaseBoard.apply(
            all,
            PurchaseFilter(fulfill = FulfillFilter.PENDING),
            now,
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `时间范围按天数截断且边界当天算在内`() {
        val all = listOf(
            purchase(1, Rarity.COMMON, daysAgo = 3),
            purchase(2, Rarity.COMMON, daysAgo = 7),
            purchase(3, Rarity.COMMON, daysAgo = 20),
        )

        val result = PurchaseBoard.apply(all, PurchaseFilter(time = TimeFilter.WEEK), now)

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `多个条件同时生效时取交集`() {
        val all = listOf(
            purchase(1, Rarity.EPIC, fulfilled = false, daysAgo = 2),
            purchase(2, Rarity.EPIC, fulfilled = true, daysAgo = 2),
            purchase(3, Rarity.COMMON, fulfilled = false, daysAgo = 2),
            purchase(4, Rarity.EPIC, fulfilled = false, daysAgo = 40),
        )

        val result = PurchaseBoard.apply(
            all,
            PurchaseFilter(
                rarities = setOf(Rarity.EPIC),
                fulfill = FulfillFilter.PENDING,
                time = TimeFilter.MONTH,
            ),
            now,
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `空列表进空列表出`() {
        assertEquals(emptyList<PurchaseEntity>(), PurchaseBoard.apply(emptyList(), PurchaseFilter(), now))
    }
}
