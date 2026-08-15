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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.economy.Rarity

class EnumConverters {
    @TypeConverter fun questTypeToString(v: QuestType): String = v.name
    @TypeConverter fun stringToQuestType(v: String): QuestType = QuestType.valueOf(v)

    @TypeConverter fun questStatusToString(v: QuestStatus): String = v.name
    @TypeConverter fun stringToQuestStatus(v: String): QuestStatus = QuestStatus.valueOf(v)

    @TypeConverter fun taskStatusToString(v: TaskStatus): String = v.name
    @TypeConverter fun stringToTaskStatus(v: String): TaskStatus = TaskStatus.valueOf(v)

    @TypeConverter fun difficultyToString(v: Difficulty): String = v.name
    @TypeConverter fun stringToDifficulty(v: String): Difficulty = Difficulty.valueOf(v)

    @TypeConverter fun focusToString(v: FocusOutcome): String = v.name
    @TypeConverter fun stringToFocus(v: String): FocusOutcome = FocusOutcome.valueOf(v)

    @TypeConverter fun rarityToString(v: Rarity): String = v.name
    @TypeConverter fun stringToRarity(v: String): Rarity = Rarity.valueOf(v)

    @TypeConverter fun ledgerTypeToString(v: LedgerType): String = v.name
    @TypeConverter fun stringToLedgerType(v: String): LedgerType = LedgerType.valueOf(v)
}

@Database(
    entities = [
        DomainEntity::class,
        QuestEntity::class,
        TaskEntity::class,
        SessionEntity::class,
        LedgerEntity::class,
        ShopItemEntity::class,
        DailyPickEntity::class,
        PurchaseEntity::class,
        BlockerEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class CiDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun questDao(): QuestDao
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun shopDao(): ShopDao
    abstract fun blockerDao(): BlockerDao

    companion object {
        @Volatile private var instance: CiDatabase? = null

        /**
         * v1 → v2：购买记录加「是否已兑现」。
         *
         * 这是本工程第一条 migration——设备上已经有真实数据了，
         * 不能再靠卸载重装糊弄过去。以后每改一次 Entity 都要照此补一条。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE purchases ADD COLUMN fulfilled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v2 → v3：购买记录冗余存下单时的品质，供「我的」按品质筛选与排序。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE purchases ADD COLUMN rarity TEXT NOT NULL DEFAULT 'COMMON'"
                )
            }
        }

        /** v3 → v4：专注记录记下任务线，支持不挂具体任务、直接对着支线打卡。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN questId INTEGER")
            }
        }

        /** v4 → v5：支线可以挂到某条主线下面。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quests ADD COLUMN parentQuestId INTEGER")
            }
        }

        fun get(context: Context): CiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CiDatabase::class.java,
                    "ci.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { instance = it }
            }

        /**
         * 数据备份恢复需要替换整个数据库文件。先关闭并清空单例，避免 Room 持有旧文件句柄。
         * 调用方完成替换后再通过 [get] 懒加载新数据库。
         */
        fun closeForRestore() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
