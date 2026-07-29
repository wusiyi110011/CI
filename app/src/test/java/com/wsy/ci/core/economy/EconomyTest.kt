package com.wsy.ci.core.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EconomyTest {

    @Test
    fun `半小时以内不加成`() {
        assertEquals(30.0, Economy.weightedMinutes(30), 1e-9)
        assertEquals(0.0, Economy.weightedMinutes(0), 1e-9)
        assertEquals(0.0, Economy.weightedMinutes(-5), 1e-9)
    }

    @Test
    fun `超过半小时的那段按一点五倍`() {
        // 40 分钟 = 30×1 + 10×1.5
        assertEquals(45.0, Economy.weightedMinutes(40), 1e-9)
        assertEquals(75.0, Economy.weightedMinutes(60), 1e-9)
    }

    @Test
    fun `超过一小时的那段按两倍`() {
        // 90 分钟 = 30×1 + 30×1.5 + 30×2
        assertEquals(135.0, Economy.weightedMinutes(90), 1e-9)
        assertEquals(77.0, Economy.weightedMinutes(61), 1e-9)
    }

    @Test
    fun `整体倍率是加权分钟与实际分钟之比`() {
        assertEquals(1.0, Economy.durationMultiplier(20), 1e-9)
        assertEquals(1.125, Economy.durationMultiplier(40), 1e-9)
        assertEquals(1.5, Economy.durationMultiplier(90), 1e-9)
        // 0 分钟没有倍率可言，兜底为 1 免得 UI 除零
        assertEquals(1.0, Economy.durationMultiplier(0), 1e-9)
    }

    @Test
    fun `一般难度按时完成 1小时 得75CI`() {
        // 加权 75 分钟 × 1.0 × 1.0
        assertEquals(75L, Economy.taskReward(60, Difficulty.NORMAL, FocusOutcome.COMPLETED))
    }

    @Test
    fun `半小时内的收益不受阶梯影响`() {
        assertEquals(30L, Economy.taskReward(30, Difficulty.NORMAL, FocusOutcome.COMPLETED))
    }

    @Test
    fun `攻坚难度系数1_6生效`() {
        assertEquals(120L, Economy.taskReward(60, Difficulty.EPIC, FocusOutcome.COMPLETED))
    }

    @Test
    fun `中途放弃打五折`() {
        assertEquals(37L, Economy.taskReward(60, Difficulty.NORMAL, FocusOutcome.ABANDONED))
    }

    @Test
    fun `超时完成打九折`() {
        assertEquals(67L, Economy.taskReward(60, Difficulty.NORMAL, FocusOutcome.OVERTIME))
    }

    @Test
    fun `连击加成分档`() {
        assertEquals(0.0, Economy.streakBonus(0), 1e-9)
        assertEquals(0.0, Economy.streakBonus(6), 1e-9)
        assertEquals(0.10, Economy.streakBonus(7), 1e-9)
        assertEquals(0.10, Economy.streakBonus(29), 1e-9)
        assertEquals(0.25, Economy.streakBonus(30), 1e-9)
        assertEquals(0.25, Economy.streakBonus(99), 1e-9)
        assertEquals(0.50, Economy.streakBonus(100), 1e-9)
        assertEquals(0.50, Economy.streakBonus(365), 1e-9)
    }

    @Test
    fun `连击7天加一成`() {
        assertEquals(82L, Economy.taskReward(60, Difficulty.NORMAL, FocusOutcome.COMPLETED, 7))
    }

    @Test
    fun `零分钟不给币`() {
        assertEquals(0L, Economy.taskReward(0, Difficulty.EPIC, FocusOutcome.COMPLETED))
        assertEquals(0L, Economy.expGain(0, Difficulty.EPIC))
    }

    @Test
    fun `经验等于分钟乘难度`() {
        assertEquals(78L, Economy.expGain(60, Difficulty.HARD))
    }

    @Test
    fun `经验等级门槛`() {
        assertEquals(1, Economy.levelForExp(0))
        assertEquals(1, Economy.levelForExp(599))
        assertEquals(2, Economy.levelForExp(600))
        assertEquals(3, Economy.levelForExp(2100))
        assertEquals(4, Economy.levelForExp(5400))
        assertEquals(5, Economy.levelForExp(12000))
        assertEquals(6, Economy.levelForExp(24000))
        assertEquals(6, Economy.levelForExp(999999))
    }

    @Test
    fun `距下一级经验`() {
        assertEquals(600L, Economy.expToNextLevel(0)!!)
        assertEquals(1L, Economy.expToNextLevel(2099)!!)
        assertNull(Economy.expToNextLevel(24000))
    }

    @Test
    fun `升级奖励等于新等级乘100`() {
        assertEquals(200L, Economy.levelUpReward(2))
        assertEquals(600L, Economy.levelUpReward(6))
    }

    @Test
    fun `等级进度条边界`() {
        assertEquals(0f, Economy.levelProgress(0), 1e-6f)
        assertEquals(1f, Economy.levelProgress(24000), 1e-6f)
        assertEquals(0.5f, Economy.levelProgress(300), 1e-6f)
    }

    @Test
    fun `价格档位对应品质`() {
        assertEquals(Rarity.COMMON, Economy.suggestRarity(499))
        assertEquals(Rarity.RARE, Economy.suggestRarity(500))
        assertEquals(Rarity.RARE, Economy.suggestRarity(1999))
        assertEquals(Rarity.EPIC, Economy.suggestRarity(2000))
        assertEquals(Rarity.EPIC, Economy.suggestRarity(10000))
        assertEquals(Rarity.LEGENDARY, Economy.suggestRarity(10001))
    }
}
