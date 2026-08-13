package com.wsy.ci.core.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wsy.ci.core.db.CiDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DataBackupItem(
    val id: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val label: String,
)

data class DataBackupState(
    val backups: List<DataBackupItem> = emptyList(),
    val operation: DataBackupOperation = DataBackupOperation.IDLE,
    val targetId: String? = null,
    val message: String? = null,
)

enum class DataBackupOperation { IDLE, CREATING, RESTORING, DELETING }

/**
 * 应用内数据备份管理器。
 *
 * 快照通过 SQLite `VACUUM INTO` 创建，能得到不依赖 WAL 的一致数据库。导入时使用
 * `ATTACH` 和单事务逐表替换，因此失败会完整回滚，不替换正在使用的数据库文件。
 * 备份包含普通应用设置和任务路由；API Key 与 1.39GB 本地模型明确排除。
 */
class DataBackupManager(
    context: Context,
    private val database: CiDatabase,
    private val onSettingsRestored: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        Thread(it, "ci-data-backup").apply { isDaemon = true }
    }.asCoroutineDispatcher(),
) {
    private val appContext = context.applicationContext
    private val io = dispatcher
    private val root = File(appContext.filesDir, BACKUP_DIRECTORY)
    private val _state = MutableStateFlow(DataBackupState())
    val state: StateFlow<DataBackupState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(backups = scanBackups())
    }

    suspend fun createBackup(): Result<DataBackupItem> = runOperation(
        operation = DataBackupOperation.CREATING,
        errorPrefix = "备份失败",
    ) {
        createSnapshot()
    }

    private fun createSnapshot(): DataBackupItem {
        root.mkdirs()
        val now = System.currentTimeMillis()
        val id = "${FILE_TIME.format(Date(now))}-${UUID.randomUUID().toString().take(8)}"
        val staging = File(root, ".$id.tmp")
        val destination = File(root, id)
        check(staging.mkdirs()) { "无法创建备份暂存目录" }
        try {
            val snapshot = File(staging, DATABASE_FILE)
            database.openHelper.writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf(snapshot.absolutePath),
            )
            validateDatabase(snapshot)
            writeStringPreferences("app_settings", File(staging, APP_SETTINGS_FILE))
            writeStringPreferences("llm_routes", File(staging, LLM_ROUTES_FILE))
            writeMetadata(
                file = File(staging, METADATA_FILE),
                createdAtMillis = now,
                databaseSize = snapshot.length(),
                databaseSha256 = sha256(snapshot),
                appSettingsFile = File(staging, APP_SETTINGS_FILE),
                llmRoutesFile = File(staging, LLM_ROUTES_FILE),
            )
            check(staging.renameTo(destination)) { "无法完成备份原子切换" }
            return scanBackup(destination) ?: error("备份校验失败")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    suspend fun restoreBackup(id: String): Result<Unit> = runOperation(
        operation = DataBackupOperation.RESTORING,
        targetId = id,
        errorPrefix = "导入失败",
    ) {
        val source = safeBackupDirectory(id)
        val item = scanBackup(source) ?: error("备份不存在或已损坏")
        val sourceDatabase = File(source, DATABASE_FILE)
        validateDatabase(sourceDatabase)
        val desiredAppSettings = readStringPreferences(File(source, APP_SETTINGS_FILE))
        val desiredRoutes = readStringPreferences(File(source, LLM_ROUTES_FILE))
        val currentAppSettings = readCurrentStringPreferences("app_settings")
        val currentRoutes = readCurrentStringPreferences("llm_routes")
        // 导入前强制留一份当前数据快照；即使用户选错，仍能从列表一键恢复。
        val rollback = createSnapshot()
        try {
            restoreTables(database.openHelper.writableDatabase, sourceDatabase)
            check(writeStringPreferences("app_settings", desiredAppSettings)) { "应用设置写入失败" }
            check(writeStringPreferences("llm_routes", desiredRoutes)) { "模型路由写入失败" }
        } catch (error: Throwable) {
            // 数据库与设置任一环节失败，都用刚创建的快照恢复导入前状态。
            val rollbackDirectory = safeBackupDirectory(rollback.id)
            restoreTables(database.openHelper.writableDatabase, File(rollbackDirectory, DATABASE_FILE))
            writeStringPreferences("app_settings", currentAppSettings)
            writeStringPreferences("llm_routes", currentRoutes)
            throw error
        }
        onSettingsRestored()
        _state.value = _state.value.copy(message = "已导入 ${item.label}")
        Unit
    }

    suspend fun deleteBackup(id: String): Result<Unit> = runOperation(
        operation = DataBackupOperation.DELETING,
        targetId = id,
        errorPrefix = "删除失败",
    ) {
        val directory = safeBackupDirectory(id)
        check(directory.exists()) { "备份不存在" }
        check(directory.deleteRecursively()) { "无法删除备份" }
        Unit
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun <T> runOperation(
        operation: DataBackupOperation,
        targetId: String? = null,
        errorPrefix: String,
        block: () -> T,
    ): Result<T> =
        withContext(io) {
            runCatching {
                check(_state.value.operation == DataBackupOperation.IDLE) { "已有备份操作正在进行" }
                _state.value = _state.value.copy(operation = operation, targetId = targetId, message = null)
                block()
            }.onSuccess {
                _state.value = _state.value.copy(
                    backups = scanBackups(),
                    operation = DataBackupOperation.IDLE,
                    targetId = null,
                    message = _state.value.message ?: when (errorPrefix) {
                        "备份失败" -> "备份已完成"
                        "删除失败" -> "备份已删除"
                        else -> null
                    },
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    backups = scanBackups(),
                    operation = DataBackupOperation.IDLE,
                    targetId = null,
                    message = "$errorPrefix：${error.message ?: "未知错误"}",
                )
            }
        }

    private fun restoreTables(target: SupportSQLiteDatabase, source: File) {
        val escaped = source.absolutePath.replace("'", "''")
        target.execSQL("ATTACH DATABASE '$escaped' AS imported")
        try {
            val importedVersion = target.query("PRAGMA imported.user_version").use { cursor ->
                check(cursor.moveToFirst()) { "无法读取备份版本" }
                cursor.getInt(0)
            }
            check(importedVersion == DATABASE_VERSION) { "备份数据库版本不兼容" }
            target.beginTransaction()
            try {
                target.execSQL("PRAGMA defer_foreign_keys=ON")
                RESTORE_ORDER.forEach { table -> target.execSQL("DELETE FROM `$table`") }
                INSERT_ORDER.forEach { table ->
                    target.execSQL("INSERT INTO `$table` SELECT * FROM imported.`$table`")
                }
                target.execSQL("DELETE FROM sqlite_sequence")
                target.execSQL(
                    "INSERT INTO sqlite_sequence SELECT name, seq FROM imported.sqlite_sequence " +
                        "WHERE name IN (${TABLES.joinToString { "'$it'" }})"
                )
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        } finally {
            target.execSQL("DETACH DATABASE imported")
        }
    }

    private fun validateDatabase(file: File) {
        check(file.isFile && file.length() > 0) { "数据库文件为空" }
        val handle = android.database.sqlite.SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        try {
            handle.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "数据库完整性校验失败" }
            }
            handle.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == DATABASE_VERSION) { "备份版本不受支持" }
            }
            TABLES.forEach { table ->
                handle.rawQuery("SELECT 1 FROM `$table` LIMIT 1", null).close()
            }
        } finally {
            handle.close()
        }
    }

    private fun writeStringPreferences(name: String, destination: File) {
        val values = appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all
        Properties().apply {
            values.forEach { (key, value) -> if (value is String) setProperty(key, value) }
        }.also { properties -> FileOutputStream(destination).use { properties.store(it, null) } }
    }

    private fun readStringPreferences(source: File): Map<String, String> {
        check(source.isFile) { "备份设置文件缺失" }
        val values = Properties().apply { FileInputStream(source).use(::load) }
        return values.stringPropertyNames().associateWith(values::getProperty)
    }

    private fun readCurrentStringPreferences(name: String): Map<String, String> =
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapNotNull { (key, value) ->
            (value as? String)?.let { key to it }
        }.toMap()

    private fun writeStringPreferences(name: String, values: Map<String, String>): Boolean =
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply {
            values.forEach(::putString)
        }.commit()

    private fun writeMetadata(
        file: File,
        createdAtMillis: Long,
        databaseSize: Long,
        databaseSha256: String,
        appSettingsFile: File,
        llmRoutesFile: File,
    ) {
        Properties().apply {
            setProperty("formatVersion", FORMAT_VERSION.toString())
            setProperty("databaseVersion", DATABASE_VERSION.toString())
            setProperty("createdAtMillis", createdAtMillis.toString())
            setProperty("databaseSize", databaseSize.toString())
            setProperty("databaseSha256", databaseSha256)
            setProperty("appSettingsSize", appSettingsFile.length().toString())
            setProperty("appSettingsSha256", sha256(appSettingsFile))
            setProperty("llmRoutesSize", llmRoutesFile.length().toString())
            setProperty("llmRoutesSha256", sha256(llmRoutesFile))
        }.also { properties -> FileOutputStream(file).use { properties.store(it, null) } }
    }

    private fun scanBackups(): List<DataBackupItem> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && !it.name.startsWith('.') }
        .mapNotNull(::scanBackup)
        .sortedByDescending { it.createdAtMillis }

    private fun scanBackup(directory: File): DataBackupItem? = runCatching {
        val metadata = Properties().apply {
            FileInputStream(File(directory, METADATA_FILE)).use(::load)
        }
        check(metadata.getProperty("formatVersion")?.toInt() == FORMAT_VERSION)
        val snapshot = File(directory, DATABASE_FILE)
        check(snapshot.length() == metadata.getProperty("databaseSize").toLong())
        check(sha256(snapshot) == metadata.getProperty("databaseSha256"))
        val appSettings = File(directory, APP_SETTINGS_FILE)
        check(appSettings.length() == metadata.getProperty("appSettingsSize").toLong())
        check(sha256(appSettings) == metadata.getProperty("appSettingsSha256"))
        val llmRoutes = File(directory, LLM_ROUTES_FILE)
        check(llmRoutes.length() == metadata.getProperty("llmRoutesSize").toLong())
        check(sha256(llmRoutes) == metadata.getProperty("llmRoutesSha256"))
        val createdAt = metadata.getProperty("createdAtMillis").toLong()
        DataBackupItem(
            id = directory.name,
            createdAtMillis = createdAt,
            sizeBytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            label = DISPLAY_TIME.format(Date(createdAt)),
        )
    }.getOrNull()

    private fun safeBackupDirectory(id: String): File {
        check(id.matches(Regex("[0-9]{8}-[0-9]{6}-[a-f0-9]{8}"))) { "非法备份标识" }
        return File(root, id).canonicalFile.also {
            check(it.parentFile == root.canonicalFile) { "非法备份路径" }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BACKUP_DIRECTORY = "data-backups"
        const val DATABASE_FILE = "ci.db"
        const val APP_SETTINGS_FILE = "app_settings.xml"
        const val LLM_ROUTES_FILE = "llm_routes.xml"
        const val METADATA_FILE = "backup.properties"
        const val FORMAT_VERSION = 1
        const val DATABASE_VERSION = 5
        val TABLES = listOf(
            "domains", "quests", "tasks", "sessions", "ledger",
            "shop_items", "daily_picks", "purchases", "blockers",
        )
        val RESTORE_ORDER = listOf(
            "daily_picks", "purchases", "ledger", "sessions", "tasks",
            "blockers", "quests", "shop_items", "domains",
        )
        val INSERT_ORDER = listOf(
            "domains", "shop_items", "quests", "tasks", "sessions", "ledger",
            "daily_picks", "purchases", "blockers",
        )
        val FILE_TIME = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
        val DISPLAY_TIME = SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.CHINA)
    }
}
