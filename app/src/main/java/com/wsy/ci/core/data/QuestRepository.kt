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

package com.wsy.ci.core.data

import androidx.room.withTransaction
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.economy.Economy

/** 一次完结的产出，供语音文案与 UI 提示展示。 */
data class QuestCompletion(
    val questTitle: String,
    /** 本次发出的复利结算利息；0 表示支线、无收益或已发过（去重）。 */
    val interestCi: Long,
)

/**
 * 任务线完结：状态流转与主线「复利结算」奖励的唯一入口，语音技能与任务线屏共用。
 * 归档（搁置/放弃）不走这里，也不发任何奖励。
 */
class QuestRepository(private val db: CiDatabase) {

    /**
     * 把一条进行中的任务线完结。主线额外按其名下累计入账 CI 发一笔利息
     * （[Economy.questInterest]）；发没发过靠 ledger 的 refId 去重，所以
     * DONE↔ACTIVE 来回切换刷不出第二笔。找不到或状态不对返回 null。
     */
    suspend fun complete(questId: Long): QuestCompletion? = db.withTransaction {
        val quest = db.questDao().byId(questId) ?: return@withTransaction null
        if (quest.status != QuestStatus.ACTIVE) return@withTransaction null
        db.questDao().update(quest.copy(status = QuestStatus.DONE))

        var interest = 0L
        if (quest.type == QuestType.MAIN && db.ledgerDao().questDoneCount(questId) == 0) {
            interest = Economy.questInterest(db.sessionDao().earnedCiByQuest(questId))
            if (interest > 0) {
                db.ledgerDao().insert(
                    LedgerEntity(
                        amount = interest,
                        type = LedgerType.EARN_QUEST_DONE,
                        refId = questId,
                        note = "「${quest.title}」复利结算",
                    )
                )
            }
        }
        QuestCompletion(questTitle = quest.title, interestCi = interest)
    }
}
