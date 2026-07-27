package com.wsy.ci.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
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

        fun get(context: Context): CiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CiDatabase::class.java,
                    "ci.db",
                ).build().also { instance = it }
            }
    }
}
