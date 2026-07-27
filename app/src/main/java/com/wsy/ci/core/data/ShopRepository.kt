package com.wsy.ci.core.data

import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.DailyPickEntity
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.DailyShop
import java.time.LocalDate

sealed interface PurchaseResult {
    data class Success(val item: ShopItemEntity, val paid: Long) : PurchaseResult
    data class NotEnough(val balance: Long, val price: Long) : PurchaseResult
    data object NotFound : PurchaseResult
}

/** 商城：货架、每日精选刷新（幂等）、购买扣款。 */
class ShopRepository(private val db: CiDatabase) {

    fun observeItems() = db.shopDao().observeItems()
    fun observePurchases() = db.shopDao().observePurchases()
    fun observeTodayPicks() = db.shopDao().observePicks(LocalDate.now().toEpochDay())
    fun observeBalance() = db.ledgerDao().observeBalance()
    fun observeLedger(limit: Int = 200) = db.ledgerDao().observeRecent(limit)

    /** 确保今日精选已生成；App 打开与每日 WorkManager 都会调用，幂等。 */
    suspend fun ensureTodayPicks() {
        val today = LocalDate.now().toEpochDay()
        if (db.shopDao().pickCount(today) > 0) return
        val pools = db.shopDao().activeItems()
            .groupBy({ it.rarity }, { it.id })
        val picks = DailyShop.rollDailyPicks(pools, DailyShop.seedForDay(today))
        if (picks.isEmpty()) return
        db.shopDao().insertPicks(
            picks.map {
                DailyPickEntity(
                    epochDay = today,
                    itemId = it.itemId,
                    discountPercent = it.discountPercent,
                )
            }
        )
    }

    suspend fun addItem(item: ShopItemEntity): Long = db.shopDao().insertItem(item)

    suspend fun updateItem(item: ShopItemEntity) = db.shopDao().updateItem(item)

    suspend fun removeItem(item: ShopItemEntity) =
        db.shopDao().updateItem(item.copy(active = false))

    /** 购买：pickId 非空按精选折扣价结算，否则原价。余额不足直接拒绝。 */
    suspend fun purchase(itemId: Long, pickId: Long? = null): PurchaseResult {
        val item = db.shopDao().itemById(itemId) ?: return PurchaseResult.NotFound
        val pick = pickId?.let { db.shopDao().pickById(it) }
        val price = if (pick != null && !pick.purchased) {
            DailyShop.discountedPrice(item.priceCi, pick.discountPercent)
        } else {
            item.priceCi
        }
        val balance = db.ledgerDao().balance()
        if (balance < price) return PurchaseResult.NotEnough(balance, price)

        db.ledgerDao().insert(
            LedgerEntity(
                amount = -price,
                type = LedgerType.SPEND_SHOP,
                refId = item.id,
                note = item.name,
            )
        )
        db.shopDao().insertPurchase(
            PurchaseEntity(itemId = item.id, itemName = item.name, pricePaid = price)
        )
        pick?.let { db.shopDao().updatePick(it.copy(purchased = true)) }
        return PurchaseResult.Success(item, price)
    }
}
