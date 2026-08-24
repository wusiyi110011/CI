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

package com.wsy.ci.core.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wsy.ci.core.db.CiDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
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
    val pendingImport: DataBackupItem? = null,
    val message: String? = null,
)

enum class DataBackupOperation { IDLE, CREATING, EXPORTING, PREPARING_IMPORT, RESTORING, DELETING }

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
    private val sharedRoot = File(appContext.cacheDir, SHARED_BACKUP_DIRECTORY)
    private val pendingRoot = File(appContext.cacheDir, PENDING_IMPORT_DIRECTORY)
    private val _state = MutableStateFlow(DataBackupState())
    val state: StateFlow<DataBackupState> = _state.asStateFlow()

    init {
        cleanupSharedBackups()
        pendingRoot.deleteRecursively()
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
        val id = newBackupId(now)
        val staging = File(root, ".$id.tmp")
        val destination = File(root, id)
        check(staging.mkdirs()) { "无法创建备份暂存目录" }
        try {
            val snapshot = File(staging, BackupArchive.DATABASE_FILE)
            database.openHelper.writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf(snapshot.absolutePath),
            )
            validateDatabase(snapshot)
            writeStringPreferences("app_settings", File(staging, BackupArchive.APP_SETTINGS_FILE))
            writeStringPreferences("llm_routes", File(staging, BackupArchive.LLM_ROUTES_FILE))
            writeMetadata(
                file = File(staging, BackupArchive.METADATA_FILE),
                createdAtMillis = now,
                databaseSize = snapshot.length(),
                databaseSha256 = BackupArchive.sha256(snapshot),
                appSettingsFile = File(staging, BackupArchive.APP_SETTINGS_FILE),
                llmRoutesFile = File(staging, BackupArchive.LLM_ROUTES_FILE),
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
        restoreValidatedBackup(source, item)
    }

    suspend fun exportBackup(id: String): Result<File> = runOperation(
        operation = DataBackupOperation.EXPORTING,
        targetId = id,
        errorPrefix = "导出失败",
    ) {
        cleanupSharedBackups()
        val source = safeBackupDirectory(id)
        val item = scanBackup(source) ?: error("备份不存在或已损坏")
        validateDatabase(File(source, BackupArchive.DATABASE_FILE))
        sharedRoot.mkdirs()
        val destination = File(
            sharedRoot,
            "复利数据备份-${FILE_TIME.format(Date(item.createdAtMillis))}.zip",
        )
        BackupArchive.pack(source, destination)
        destination
    }

    suspend fun prepareExternalBackup(openInput: () -> InputStream): Result<DataBackupItem> = runOperation(
        operation = DataBackupOperation.PREPARING_IMPORT,
        errorPrefix = "文件校验失败",
    ) {
        _state.value = _state.value.copy(pendingImport = null)
        pendingRoot.deleteRecursively()
        check(pendingRoot.mkdirs()) { "无法创建导入暂存目录" }
        val now = System.currentTimeMillis()
        val id = newBackupId(now)
        val staging = File(pendingRoot, id)
        try {
            openInput().use { BackupArchive.extract(it, staging) }
            val info = BackupArchive.validate(staging, DATABASE_VERSION)
            validateDatabase(File(staging, BackupArchive.DATABASE_FILE))
            val item = DataBackupItem(
                id = id,
                createdAtMillis = info.createdAtMillis,
                sizeBytes = info.sizeBytes,
                label = DISPLAY_TIME.format(Date(info.createdAtMillis)),
            )
            _state.value = _state.value.copy(pendingImport = item, message = "备份文件已校验")
            item
        } catch (error: Throwable) {
            pendingRoot.deleteRecursively()
            throw error
        }
    }

    suspend fun restorePreparedBackup(): Result<Unit> {
        val item = _state.value.pendingImport
            ?: return Result.failure(IllegalStateException("没有待导入的备份文件"))
        return runOperation(
            operation = DataBackupOperation.RESTORING,
            targetId = item.id,
            errorPrefix = "导入失败",
        ) {
            val staging = safePendingDirectory(item.id)
            val info = BackupArchive.validate(staging, DATABASE_VERSION)
            validateDatabase(File(staging, BackupArchive.DATABASE_FILE))
            root.mkdirs()
            val destination = safeBackupDirectory(item.id)
            check(!destination.exists()) { "同名备份已存在" }
            if (!staging.renameTo(destination)) {
                staging.copyRecursively(destination, overwrite = false)
                check(staging.deleteRecursively()) { "无法清理导入暂存文件" }
            }
            pendingRoot.deleteRecursively()
            val stored = DataBackupItem(
                id = item.id,
                createdAtMillis = info.createdAtMillis,
                sizeBytes = info.sizeBytes,
                label = DISPLAY_TIME.format(Date(info.createdAtMillis)),
            )
            _state.value = _state.value.copy(pendingImport = null)
            restoreValidatedBackup(destination, stored)
        }
    }

    fun cancelPreparedBackup() {
        pendingRoot.deleteRecursively()
        _state.value = _state.value.copy(pendingImport = null, message = null)
    }

    fun setMessage(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    private fun restoreValidatedBackup(source: File, item: DataBackupItem) {
        BackupArchive.validate(source, DATABASE_VERSION)
        val sourceDatabase = File(source, BackupArchive.DATABASE_FILE)
        validateDatabase(sourceDatabase)
        val desiredAppSettings = readStringPreferences(File(source, BackupArchive.APP_SETTINGS_FILE))
        val desiredRoutes = readStringPreferences(File(source, BackupArchive.LLM_ROUTES_FILE))
        val currentAppSettings = readCurrentStringPreferences("app_settings")
        val currentRoutes = readCurrentStringPreferences("llm_routes")
        // 导入前强制留一份当前数据快照；即使用户选错，仍能从列表一键恢复。
        val rollback = createSnapshot()
        BackupRestoreGuard.run(
            apply = {
                restoreTables(database.openHelper.writableDatabase, sourceDatabase)
                check(writeStringPreferences("app_settings", desiredAppSettings)) { "应用设置写入失败" }
                check(writeStringPreferences("llm_routes", desiredRoutes)) { "模型路由写入失败" }
            },
            rollback = {
                // 数据库与设置任一环节失败，都用刚创建的快照恢复导入前状态。
                val rollbackDirectory = safeBackupDirectory(rollback.id)
                val failures = listOf(
                    runCatching {
                        restoreTables(
                            database.openHelper.writableDatabase,
                            File(rollbackDirectory, BackupArchive.DATABASE_FILE),
                        )
                    }.exceptionOrNull(),
                    runCatching {
                        check(writeStringPreferences("app_settings", currentAppSettings)) { "回滚应用设置失败" }
                    }.exceptionOrNull(),
                    runCatching {
                        check(writeStringPreferences("llm_routes", currentRoutes)) { "回滚模型路由失败" }
                    }.exceptionOrNull(),
                ).filterNotNull()
                if (failures.isNotEmpty()) {
                    throw IllegalStateException("导入失败后的自动回滚未完整完成", failures.first()).apply {
                        failures.drop(1).forEach(::addSuppressed)
                    }
                }
            },
        )
        onSettingsRestored()
        _state.value = _state.value.copy(message = "已导入 ${item.label}")
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
            setProperty("formatVersion", BackupArchive.FORMAT_VERSION.toString())
            setProperty("databaseVersion", DATABASE_VERSION.toString())
            setProperty("createdAtMillis", createdAtMillis.toString())
            setProperty("databaseSize", databaseSize.toString())
            setProperty("databaseSha256", databaseSha256)
            setProperty("appSettingsSize", appSettingsFile.length().toString())
            setProperty("appSettingsSha256", BackupArchive.sha256(appSettingsFile))
            setProperty("llmRoutesSize", llmRoutesFile.length().toString())
            setProperty("llmRoutesSha256", BackupArchive.sha256(llmRoutesFile))
        }.also { properties -> FileOutputStream(file).use { properties.store(it, null) } }
    }

    private fun scanBackups(): List<DataBackupItem> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && !it.name.startsWith('.') }
        .mapNotNull(::scanBackup)
        .sortedByDescending { it.createdAtMillis }

    private fun scanBackup(directory: File): DataBackupItem? = runCatching {
        val info = BackupArchive.validate(directory)
        DataBackupItem(
            id = directory.name,
            createdAtMillis = info.createdAtMillis,
            sizeBytes = info.sizeBytes,
            label = DISPLAY_TIME.format(Date(info.createdAtMillis)),
        )
    }.getOrNull()

    private fun safeBackupDirectory(id: String): File {
        checkBackupId(id)
        return File(root, id).canonicalFile.also {
            check(it.parentFile == root.canonicalFile) { "非法备份路径" }
        }
    }

    private fun safePendingDirectory(id: String): File {
        checkBackupId(id)
        return File(pendingRoot, id).canonicalFile.also {
            check(it.parentFile == pendingRoot.canonicalFile) { "非法导入暂存路径" }
        }
    }

    private fun checkBackupId(id: String) {
        check(id.matches(Regex("[0-9]{8}-[0-9]{6}-[a-f0-9]{8}"))) { "非法备份标识" }
    }

    private fun newBackupId(now: Long): String =
        "${FILE_TIME.format(Date(now))}-${UUID.randomUUID().toString().take(8)}"

    private fun cleanupSharedBackups(now: Long = System.currentTimeMillis()) {
        sharedRoot.listFiles().orEmpty().forEach { file ->
            if (now - file.lastModified() > SHARED_BACKUP_MAX_AGE_MILLIS) file.deleteRecursively()
        }
    }

    private companion object {
        const val BACKUP_DIRECTORY = "data-backups"
        const val SHARED_BACKUP_DIRECTORY = "shared-backups"
        const val PENDING_IMPORT_DIRECTORY = "pending-backup-import"
        const val SHARED_BACKUP_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
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
