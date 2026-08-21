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

package com.wsy.ci.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Query("SELECT * FROM domains WHERE archived = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<DomainEntity>>

    /** 含已归档领域，供复盘还原历史专注当时的领域名称。 */
    @Query("SELECT * FROM domains ORDER BY createdAt")
    fun observeEvery(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE id = :id")
    suspend fun byId(id: Long): DomainEntity?

    @Insert
    suspend fun insert(domain: DomainEntity): Long

    @Update
    suspend fun update(domain: DomainEntity)

    @Query("UPDATE domains SET totalExp = totalExp + :delta WHERE id = :id")
    suspend fun addExp(id: Long, delta: Long)

    /** 软删除领域前先解除任务线与具体任务的归属，保留所有历史记录。 */
    @Transaction
    suspend fun archiveWithDetach(id: Long) {
        detachQuests(id)
        detachTasks(id)
        archive(id)
    }

    @Query("UPDATE quests SET domainId = NULL WHERE domainId = :domainId")
    suspend fun detachQuests(domainId: Long)

    @Query("UPDATE tasks SET domainId = NULL WHERE domainId = :domainId")
    suspend fun detachTasks(domainId: Long)

    @Query("UPDATE domains SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests WHERE status != 'ARCHIVED' ORDER BY type, createdAt")
    fun observeAll(): Flow<List<QuestEntity>>

    /**
     * 含已归档的全部任务线。任务屏要分「进行中」「已完成 · 已归档」两块展示，
     * 归档的也得能点开回看，所以那边不能用只给非归档的 [observeAll]。
     */
    @Query("SELECT * FROM quests ORDER BY type, createdAt")
    fun observeEvery(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE status = 'ACTIVE' AND type = :type")
    suspend fun activeByType(type: QuestType): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun byId(id: Long): QuestEntity?

    @Insert
    suspend fun insert(quest: QuestEntity): Long

    @Update
    suspend fun update(quest: QuestEntity)

    @Delete
    suspend fun delete(quest: QuestEntity)

    /** 删主线前先松开挂在它下面的支线，免得留下指向空气的 parentQuestId。 */
    @Query("UPDATE quests SET parentQuestId = NULL WHERE parentQuestId = :questId")
    suspend fun detachChildren(questId: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY epochDay, startMinute")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE epochDay = :epochDay ORDER BY startMinute")
    fun observeByDay(epochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, startMinute")
    fun observeByRange(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE epochDay BETWEEN :from AND :to")
    suspend fun byRange(from: Long, to: Long): List<TaskEntity>

    /** 某条任务线下的全部任务，按日期 + 起始时间排，供任务线详情列出完成情况。 */
    @Query("SELECT * FROM tasks WHERE questId = :questId ORDER BY epochDay, startMinute")
    fun observeByQuest(questId: Long): Flow<List<TaskEntity>>

    /** 尚未归属任何任务线的任务，供主线卡批量关联。 */
    @Query("SELECT * FROM tasks WHERE questId IS NULL ORDER BY epochDay, startMinute")
    suspend fun unassigned(): List<TaskEntity>

    /**
     * 把选中的未关联任务挂到任务线；再次限制 questId 为空，避免覆盖弹窗打开后新增的关联。
     * 返回实际关联数量。
     */
    @Query("UPDATE tasks SET questId = :questId WHERE id IN (:taskIds) AND questId IS NULL")
    suspend fun attachUnassignedToQuest(taskIds: List<Long>, questId: Long): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    /**
     * 解除某条任务线下所有任务的关联。删任务线时用：
     * 时间块和它背后的 session、流水都是真实发生过的历史，不该跟着任务线一起消失。
     */
    @Query("UPDATE tasks SET questId = NULL WHERE questId = :questId")
    suspend fun detachFromQuest(questId: Long)

    /** 单个任务的实时快照：计时中的任务可能不属于今天，不能只在当日列表里找。 */
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE status = 'RUNNING' LIMIT 1")
    suspend fun running(): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE startAt BETWEEN :from AND :to ORDER BY startAt")
    fun observeByTimeRange(from: Long, to: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startAt BETWEEN :from AND :to ORDER BY startAt")
    suspend fun byTimeRange(from: Long, to: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun openSession(): SessionEntity?

    /** 已结束 session 的起始时刻，算连续打卡天数用（毫秒转 epochDay 在 Kotlin 侧做，避开时区）。 */
    @Query("SELECT startAt FROM sessions WHERE endAt IS NOT NULL AND startAt >= :from")
    suspend fun completedStartsSince(from: Long): List<Long>

    @Query("SELECT * FROM sessions WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeOpenSession(): Flow<SessionEntity?>

    /**
     * 某个任务名下所有已结束专注的毫秒合计，任务卡的「学习了多久」用。
     * 只 SUM 毫秒、分钟换算留给 Kotlin，避免 SQLite 整数除法在每条记录上各截断一次。
     */
    @Query(
        "SELECT COALESCE(SUM(endAt - startAt), 0) FROM sessions " +
            "WHERE taskId = :taskId AND endAt IS NOT NULL"
    )
    fun observeFocusMillis(taskId: Long): Flow<Long>

    /**
     * 某条任务线名下全部已结束专注的 CI 入账合计，主线完结发「复利结算」用。
     * session 上的 questId 是开工那一刻记下的快照，之后换绑任务线的历史专注仍算给旧线。
     */
    @Query(
        "SELECT COALESCE(SUM(rewardCi), 0) FROM sessions " +
            "WHERE questId = :questId AND endAt IS NOT NULL"
    )
    suspend fun earnedCiByQuest(questId: Long): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<LedgerEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM ledger")
    fun observeBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM ledger")
    suspend fun balance(): Long

    @Query("SELECT * FROM ledger WHERE at BETWEEN :from AND :to ORDER BY at")
    suspend fun byTimeRange(from: Long, to: Long): List<LedgerEntity>

    /**
     * 当天已发出的打卡奖笔数。一天只发一次就靠这条去重，
     * 因此不需要再加表或字段去记「今天发过没」。
     */
    @Query("SELECT COUNT(*) FROM ledger WHERE type = 'EARN_STREAK' AND refId = :epochDay")
    suspend fun checkinCount(epochDay: Long): Int

    /** 某主线的复利结算是否已发过，DONE↔ACTIVE 反复切换时靠它去重。 */
    @Query("SELECT COUNT(*) FROM ledger WHERE type = 'EARN_QUEST_DONE' AND refId = :questId")
    suspend fun questDoneCount(questId: Long): Int

    /** 撤回某次专注发出的任务奖励，删记录时用。 */
    @Query("DELETE FROM ledger WHERE type = 'EARN_TASK' AND refId = :sessionId")
    suspend fun deleteTaskEarning(sessionId: Long)

    @Insert
    suspend fun insert(entry: LedgerEntity): Long
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop_items WHERE active = 1 ORDER BY rarity DESC, priceCi")
    fun observeItems(): Flow<List<ShopItemEntity>>

    @Query("SELECT * FROM shop_items WHERE active = 1")
    suspend fun activeItems(): List<ShopItemEntity>

    /** 含已下架的全部商品名，铺默认货架时按名去重用。 */
    @Query("SELECT name FROM shop_items")
    suspend fun allItemNames(): List<String>

    @Query("SELECT * FROM shop_items WHERE id = :id")
    suspend fun itemById(id: Long): ShopItemEntity?

    @Insert
    suspend fun insertItem(item: ShopItemEntity): Long

    @Update
    suspend fun updateItem(item: ShopItemEntity)

    @Query("SELECT * FROM daily_picks WHERE epochDay = :epochDay")
    fun observePicks(epochDay: Long): Flow<List<DailyPickEntity>>

    @Query("SELECT COUNT(*) FROM daily_picks WHERE epochDay = :epochDay")
    suspend fun pickCount(epochDay: Long): Int

    @Insert
    suspend fun insertPicks(picks: List<DailyPickEntity>)

    @Update
    suspend fun updatePick(pick: DailyPickEntity)

    @Query("SELECT * FROM daily_picks WHERE id = :id")
    suspend fun pickById(id: Long): DailyPickEntity?

    @Insert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert
    suspend fun insertPurchases(purchases: List<PurchaseEntity>)

    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity)

    /** 铺示例兑换记录前判空用。 */
    @Query("SELECT COUNT(*) FROM purchases")
    suspend fun purchaseCount(): Int

    @Query("SELECT * FROM purchases ORDER BY at DESC LIMIT :limit")
    fun observePurchases(limit: Int = 100): Flow<List<PurchaseEntity>>
}

@Dao
interface BlockerDao {
    @Query("SELECT * FROM blockers WHERE epochDay = :epochDay ORDER BY startMinute")
    suspend fun byDay(epochDay: Long): List<BlockerEntity>

    @Query("SELECT * FROM blockers WHERE epochDay = :epochDay ORDER BY startMinute")
    fun observeByDay(epochDay: Long): Flow<List<BlockerEntity>>

    @Insert
    suspend fun insert(blocker: BlockerEntity): Long

    @Delete
    suspend fun delete(blocker: BlockerEntity)
}
