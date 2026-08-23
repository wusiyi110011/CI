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

package com.wsy.ci.core.economy

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckinTest {

    // ---------- 阶梯奖励 ----------

    @Test
    fun `奖励按1到2天_3到6天_7到13天_14天以上分四档`() {
        assertEquals(20L, Economy.checkinReward(1))
        assertEquals(20L, Economy.checkinReward(2))
        assertEquals(40L, Economy.checkinReward(3))
        assertEquals(40L, Economy.checkinReward(6))
        assertEquals(80L, Economy.checkinReward(7))
        assertEquals(80L, Economy.checkinReward(13))
        assertEquals(150L, Economy.checkinReward(14))
    }

    @Test
    fun `14天以上不再涨_150封顶`() {
        assertEquals(150L, Economy.checkinReward(100))
        assertEquals(150L, Economy.checkinReward(365))
    }

    @Test
    fun `连续0天不发奖`() {
        assertEquals(0L, Economy.checkinReward(0))
    }

    // ---------- 连续天数 ----------

    @Test
    fun `连续三天从今天往前数得3`() {
        val days = setOf(20_660L, 20_661L, 20_662L)

        assertEquals(3, Economy.checkinStreak(days, 20_662L))
    }

    @Test
    fun `今天没记录返回0_哪怕昨天连了很久`() {
        val days = setOf(20_658L, 20_659L, 20_660L, 20_661L)

        assertEquals(0, Economy.checkinStreak(days, 20_662L))
    }

    @Test
    fun `漏一天即断_只数断点之后的连续段`() {
        // 20_659 缺失，断点前的 20_657/20_658 不该被算进来
        val days = setOf(20_657L, 20_658L, 20_660L, 20_661L, 20_662L)

        assertEquals(3, Economy.checkinStreak(days, 20_662L))
    }

    @Test
    fun `只有今天有记录得1`() {
        assertEquals(1, Economy.checkinStreak(setOf(20_662L), 20_662L))
    }

    @Test
    fun `空记录得0`() {
        assertEquals(0, Economy.checkinStreak(emptySet(), 20_662L))
    }

    @Test
    fun `未来日期的记录不影响今天的连续天数`() {
        val days = setOf(20_661L, 20_662L, 20_663L, 20_664L)

        assertEquals(2, Economy.checkinStreak(days, 20_662L))
    }

    @Test
    fun `昨日专注记录已删除但打卡流水保留时今天续为2天`() {
        val previousCheckinDays = setOf(20_661L)

        assertEquals(2, Economy.checkinStreakAfterToday(previousCheckinDays, 20_662L))
    }

    @Test
    fun `历史打卡与今天不连续时今天重置为1天`() {
        val previousCheckinDays = setOf(20_659L, 20_660L)

        assertEquals(1, Economy.checkinStreakAfterToday(previousCheckinDays, 20_662L))
    }
}
