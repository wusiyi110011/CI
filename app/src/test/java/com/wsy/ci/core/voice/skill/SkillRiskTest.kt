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

package com.wsy.ci.core.voice.skill

import com.wsy.ci.core.voice.skill.skills.DeleteQuestSkill
import com.wsy.ci.core.voice.skill.skills.DeleteTaskSkill
import com.wsy.ci.core.voice.skill.skills.PurchaseItemSkill
import com.wsy.ci.core.voice.skill.skills.QueryBalanceSkill
import com.wsy.ci.core.voice.skill.skills.QueryCurrentFocusSkill
import com.wsy.ci.core.voice.skill.skills.QueryDomainSkill
import com.wsy.ci.core.voice.skill.skills.StartTimerSkill
import org.junit.Assert.assertEquals
import org.junit.Test

/** 删除、购买与查询/变更技能的风险元数据测试。 */
class SkillRiskTest {
    @Test
    fun `删除和购买永远是危险操作`() {
        assertEquals(SkillRisk.DANGEROUS, DeleteTaskSkill.risk)
        assertEquals(SkillRisk.DANGEROUS, DeleteQuestSkill.risk)
        assertEquals(SkillRisk.DANGEROUS, PurchaseItemSkill.risk)
    }

    @Test
    fun `查询是安全操作而开始专注是中风险操作`() {
        assertEquals(SkillRisk.SAFE, QueryBalanceSkill.risk)
        assertEquals(SkillRisk.SAFE, QueryCurrentFocusSkill.risk)
        assertEquals(SkillRisk.SAFE, QueryDomainSkill.risk)
        assertEquals(SkillRisk.MODERATE, StartTimerSkill.risk)
    }
}
