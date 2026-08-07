package com.wsy.ci.core.data

import androidx.room.withTransaction
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.DailyPickEntity
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.DailyShop
import com.wsy.ci.core.economy.DefaultPurchases
import com.wsy.ci.core.economy.DefaultShopItems
import java.time.LocalDate

sealed interface PurchaseResult {
    data class Success(val item: ShopItemEntity, val paid: Long) : PurchaseResult
    data class NotEnough(val balance: Long, val price: Long) : PurchaseResult
    data object NotFound : PurchaseResult
    data object Unavailable : PurchaseResult
}

/** 商城：货架、每日精选刷新（幂等）、购买扣款。 */
class ShopRepository(private val db: CiDatabase) {

    fun observeItems() = db.shopDao().observeItems()
    fun observePurchases() = db.shopDao().observePurchases()

    /** 标记某笔兑换是否已经真正兑现（「我的」里手动切换）。 */
    suspend fun setPurchaseFulfilled(purchase: PurchaseEntity, fulfilled: Boolean) {
        db.shopDao().updatePurchase(purchase.copy(fulfilled = fulfilled))
    }

    /**
     * 首次进商城时铺 12 条示例兑换记录（每品质 3 条），让「我的」不是一片空白。
     * 只在一条记录都没有时铺，之后再也不动；不写流水，所以不影响余额。
     */
    suspend fun ensureSeedPurchases() {
        if (db.shopDao().purchaseCount() > 0) return
        db.shopDao().insertPurchases(DefaultPurchases.seeds(System.currentTimeMillis()))
    }

    fun observePicks(epochDay: Long) = db.shopDao().observePicks(epochDay)
    fun observeBalance() = db.ledgerDao().observeBalance()
    fun observeLedger(limit: Int = 200) = db.ledgerDao().observeRecent(limit)

    /**
     * 铺默认货架。按商品名去重，且比对的是**含已下架的**全部商品——
     * 否则用户主动删掉的商品每次启动又被塞回来。幂等，可反复调用。
     */
    suspend fun ensureSeedItems() {
        val existing = db.shopDao().allItemNames().toSet()
        DefaultShopItems.ALL
            .filterNot { it.name in existing }
            .forEach { db.shopDao().insertItem(it) }
    }

    /** 确保今日精选已生成；App 打开与每日 WorkManager 都会调用，幂等。 */
    suspend fun ensureTodayPicks() {
        ensurePicks(LocalDate.now().toEpochDay())
    }

    /** 为指定日期生成精选；事务内再次检查，避免多个入口并发插入重复精选。 */
    suspend fun ensurePicks(epochDay: Long) {
        db.withTransaction {
            if (db.shopDao().pickCount(epochDay) > 0) return@withTransaction
            val pools = db.shopDao().activeItems()
                .groupBy({ it.rarity }, { it.id })
            val picks = DailyShop.rollDailyPicks(pools, DailyShop.seedForDay(epochDay))
            if (picks.isEmpty()) return@withTransaction
            db.shopDao().insertPicks(
                picks.map {
                    DailyPickEntity(
                        epochDay = epochDay,
                        itemId = it.itemId,
                        discountPercent = it.discountPercent,
                    )
                }
            )
        }
    }

    /** 含已下架的全部商品名，批量导入时按名去重用。 */
    suspend fun allItemNames(): List<String> = db.shopDao().allItemNames()

    suspend fun addItem(item: ShopItemEntity): Long = db.shopDao().insertItem(item)

    suspend fun updateItem(item: ShopItemEntity) = db.shopDao().updateItem(item)

    suspend fun removeItem(item: ShopItemEntity) =
        db.shopDao().updateItem(item.copy(active = false))

    /** 购买：pickId 非空按精选折扣价结算，否则原价。余额不足直接拒绝。 */
    suspend fun purchase(itemId: Long, pickId: Long? = null): PurchaseResult {
        return db.withTransaction {
            val item = db.shopDao().itemById(itemId)
                ?.takeIf { it.active }
                ?: return@withTransaction PurchaseResult.NotFound
            val pick = pickId?.let { id ->
                db.shopDao().pickById(id)?.takeIf {
                    it.epochDay == LocalDate.now().toEpochDay() && it.itemId == itemId && !it.purchased
                } ?: return@withTransaction PurchaseResult.Unavailable
            }
            val price = pick?.let {
                DailyShop.discountedPrice(item.priceCi, it.discountPercent)
            } ?: item.priceCi
            val balance = db.ledgerDao().balance()
            if (balance < price) return@withTransaction PurchaseResult.NotEnough(balance, price)

            db.ledgerDao().insert(
                LedgerEntity(
                    amount = -price,
                    type = LedgerType.SPEND_SHOP,
                    refId = item.id,
                    note = item.name,
                )
            )
            db.shopDao().insertPurchase(
                PurchaseEntity(
                    itemId = item.id,
                    itemName = item.name,
                    pricePaid = price,
                    rarity = item.rarity,
                )
            )
            pick?.let { db.shopDao().updatePick(it.copy(purchased = true)) }
            PurchaseResult.Success(item, price)
        }
    }
}
