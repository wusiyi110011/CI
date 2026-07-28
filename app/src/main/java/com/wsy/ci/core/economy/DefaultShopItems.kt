package com.wsy.ci.core.economy

import com.wsy.ci.core.db.ShopItemEntity

/**
 * 默认货架种子数据：普通 10 / 稀有 8 / 史诗 5 / 传说 3，共 26 件。
 *
 * 定价一律按 [Economy.CI_PER_YUAN]（1 元 ≈ 20 CI）从现实价格折算，
 * 非实物的（回笼觉、追剧）按「愿意为这段时间付多少钱」估。
 *
 * 档位跨度是照「日均专注 4 小时普通难度 ≈ 240 CI/天」定的：
 * 普通半天到三天够一件，稀有一两周，史诗一两个月，传说半年到八个月。
 * 传说档特意压在 6 万以内——再贵就要攒一年多，激励会失效。
 *
 * 不带 id 与 createdAt，走 Entity 默认值由 Room 自增/取当前时间。
 */
object DefaultShopItems {

    private fun item(
        emoji: String,
        name: String,
        description: String,
        priceCi: Long,
        rarity: Rarity,
    ) = ShopItemEntity(
        name = name,
        description = description,
        emoji = emoji,
        priceCi = priceCi,
        rarity = rarity,
    )

    /** 普通：随手可兑的小确幸，攒半天到三天。 */
    private val COMMON = listOf(
        item("🥤", "一瓶快乐水", "冰镇的那种", 100, Rarity.COMMON),
        item("🎵", "单曲循环一小时", "什么都不干，只听歌", 200, Rarity.COMMON),
        item("🍦", "一支冰淇淋", "便利店随手一支", 300, Rarity.COMMON),
        item("📺", "刷半小时短视频", "心安理得地刷", 300, Rarity.COMMON),
        item("🚿", "泡个长澡", "水声盖过所有待办", 300, Rarity.COMMON),
        item("🧋", "一杯奶茶", "全糖去冰，不必内疚", 400, Rarity.COMMON),
        item("🎮", "打一局游戏", "只打一局，说好的", 400, Rarity.COMMON),
        item("🍰", "一块蛋糕", "下午茶时段的甜点", 500, Rarity.COMMON),
        item("😴", "睡个回笼觉", "闹钟按掉，重睡一轮", 600, Rarity.COMMON),
        item("☕", "一杯精品咖啡", "不是速溶的那种", 700, Rarity.COMMON),
    )

    /** 稀有：半天级的享受，攒一到两周。 */
    private val RARE = listOf(
        item("🎬", "看一场电影", "一张电影票的参考价格", 1000, Rarity.RARE),
        item("🏊", "游泳或健身房单次", "花钱买一次出汗", 1000, Rarity.RARE),
        item("📚", "买一本想读的书", "加购物车很久的那本", 1200, Rarity.RARE),
        item("🎧", "一张专辑或游戏 DLC", "数字内容，即买即用", 1200, Rarity.RARE),
        item("🛋️", "一个下午追剧", "整段时间不排任何任务", 1500, Rarity.RARE),
        item("🍽️", "吃100元内自助", "一顿价格实惠的自助餐", 1600, Rarity.RARE),
        item("🍖", "一顿烧烤", "配冰啤酒的那种", 2000, Rarity.RARE),
        item("🍣", "一顿日料", "人均一百五上下", 3000, Rarity.RARE),
    )

    /** 史诗：大件或整天，攒一到两个月。 */
    private val EPIC = listOf(
        item("🎡", "周末一日游", "当天往返的近郊", 6000, Rarity.EPIC),
        item("🎸", "一件想要的装备配件", "键盘、耳机、镜头之类", 8000, Rarity.EPIC),
        item("👟", "一双新鞋", "不是替换旧的，是想要", 10000, Rarity.EPIC),
        item("🎫", "一场演出或球赛门票", "现场的气氛值这个价", 12000, Rarity.EPIC),
        item("🍾", "一顿人均 400 的大餐", "需要提前订位的那种", 16000, Rarity.EPIC),
    )

    /** 传说：重大奖励，攒半年到八个月。 */
    private val LEGENDARY = listOf(
        item("🎓", "报一门心仪的课程", "系统学一件一直想学的事", 40000, Rarity.LEGENDARY),
        item("💻", "换新数码大件", "电脑、平板、相机", 50000, Rarity.LEGENDARY),
        item("✈️", "旅游", "一次国内短途旅行", 60000, Rarity.LEGENDARY),
    )

    val ALL: List<ShopItemEntity> = COMMON + RARE + EPIC + LEGENDARY
}
