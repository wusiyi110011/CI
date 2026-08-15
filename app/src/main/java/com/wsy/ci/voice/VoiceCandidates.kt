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

package com.wsy.ci.voice

import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetKind
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 语音可命中对象的候选清单（原 `VoiceCommandExecutor.candidates` 的逻辑搬到这里）：
 * 计划中的任务 + 全部任务线（含归档，供恢复/删除）+ 领域 + 在售商品。
 */
suspend fun loadVoiceTargets(db: CiDatabase): List<VoiceTarget> {
    val today = LocalDate.now().toEpochDay()
    val tasks = db.taskDao().byRange(today - CANDIDATE_LOOKBACK_DAYS, today + CANDIDATE_LOOKAHEAD_DAYS)
        .filter { it.status == TaskStatus.PLANNED }
        .map { VoiceTarget(it.id, it.title, VoiceTargetKind.TASK) }
    val quests = db.questDao().observeEvery().first()
        .map { VoiceTarget(it.id, it.title, VoiceTargetKind.QUEST) }
    val domains = db.domainDao().observeAll().first()
        .map { VoiceTarget(it.id, it.name, VoiceTargetKind.DOMAIN) }
    val items = db.shopDao().activeItems()
        .map { VoiceTarget(it.id, it.name, VoiceTargetKind.SHOP_ITEM) }
    return tasks + quests + domains + items
}

private const val CANDIDATE_LOOKBACK_DAYS = 7L
private const val CANDIDATE_LOOKAHEAD_DAYS = 14L
