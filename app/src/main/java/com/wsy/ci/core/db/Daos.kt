package com.wsy.ci.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Query("SELECT * FROM domains WHERE archived = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE id = :id")
    suspend fun byId(id: Long): DomainEntity?

    @Insert
    suspend fun insert(domain: DomainEntity): Long

    @Update
    suspend fun update(domain: DomainEntity)

    @Query("UPDATE domains SET totalExp = totalExp + :delta WHERE id = :id")
    suspend fun addExp(id: Long, delta: Long)
}

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests WHERE status != 'ARCHIVED' ORDER BY type, createdAt")
    fun observeAll(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE status = 'ACTIVE' AND type = :type")
    suspend fun activeByType(type: QuestType): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun byId(id: Long): QuestEntity?

    @Insert
    suspend fun insert(quest: QuestEntity): Long

    @Update
    suspend fun update(quest: QuestEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE epochDay = :epochDay ORDER BY startMinute")
    fun observeByDay(epochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, startMinute")
    fun observeByRange(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE epochDay BETWEEN :from AND :to")
    suspend fun byRange(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

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

    @Query("SELECT * FROM sessions WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeOpenSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

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
