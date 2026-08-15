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
