package com.wsy.ci.core.economy

import com.wsy.ci.core.db.PurchaseEntity

/** 一天的毫秒数，用来把种子记录按天摊开。 */
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * 「我的」的种子兑换记录：每个品质 3 条，共 12 条。
 *
 * 名字与价格取自 [DefaultShopItems]，所以看起来和货架是同一套东西。
 * 每档留 1 条已实现、2 条未实现，这样一进来就能看出
 * 「未实现在上、已实现在下、组内按品质从高到低」的排法。
 *
 * 只在购买记录为空时铺一次（见 `ShopRepository.ensureSeedPurchases`），
 * 且**不写流水**——这些是示例，不该影响 CI 余额。
 */
object DefaultPurchases {

    /** 每个品质取前几件作为种子。 */
    private const val PER_RARITY = 3

    /** 第 n 条种子记录距今多少天，越靠后的越旧。 */
    private const val DAYS_STEP = 3L

    /** 每档第几条标记为已实现。 */
    private const val FULFILLED_INDEX = 1

    fun seeds(nowMillis: Long): List<PurchaseEntity> =
        Rarity.entries.flatMapIndexed { rarityIndex, rarity ->
            DefaultShopItems.ALL
                .filter { it.rarity == rarity }
                .take(PER_RARITY)
                .mapIndexed { index, item ->
                    val order = rarityIndex * PER_RARITY + index
                    PurchaseEntity(
                        itemId = 0,
                        itemName = item.name,
                        pricePaid = item.priceCi,
                        at = nowMillis - order * DAYS_STEP * MILLIS_PER_DAY,
                        fulfilled = index == FULFILLED_INDEX,
                        rarity = rarity,
                    )
                }
        }
}
