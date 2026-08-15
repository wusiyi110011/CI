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

package com.wsy.ci.feature.settings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 设置页展示的一份备份记录；实际文件由备份引擎按 [id] 管理。 */
data class BackupItem(
    val id: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val label: String = "复利数据备份",
)

/** 数据备份区的 UI 状态，避免设置页直接依赖数据库或文件系统。 */
data class DataBackupUiState(
    val entries: List<BackupItem> = emptyList(),
    val backingUp: Boolean = false,
    val importingId: String? = null,
    val deletingId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

/**
 * 数据备份/导入适配接口。
 *
 * 设置页只负责确认、列表和状态呈现；真实实现可以在应用容器中接入 Room 导出、校验和
 * 原子替换。方法返回后应及时通过 [state] 暴露进行中、成功或失败状态。
 */
interface DataBackupController {
    val state: StateFlow<DataBackupUiState>

    fun refresh()
    fun createBackup()
    fun restoreBackup(id: String)
    fun deleteBackup(id: String)
}

/** 预览与独立 UI 测试用的占位控制器，不执行真实文件读写。 */
class InMemoryBackupController : DataBackupController {
    private val mutableState = MutableStateFlow(DataBackupUiState())
    override val state: StateFlow<DataBackupUiState> = mutableState

    override fun refresh() = Unit

    override fun createBackup() {
        val now = System.currentTimeMillis()
        val entry = BackupItem(
            id = now.toString(),
            createdAtMillis = now,
            // 占位实现不生成文件；接入备份引擎后由真实字节数替换。
            sizeBytes = 0L,
        )
        mutableState.value = mutableState.value.copy(
            entries = (listOf(entry) + mutableState.value.entries).sortedByDescending { it.createdAtMillis },
            message = "备份记录已创建（占位）",
            errorMessage = null,
        )
    }

    override fun restoreBackup(id: String) {
        val entry = mutableState.value.entries.firstOrNull { it.id == id }
        mutableState.value = mutableState.value.copy(
            importingId = null,
            message = if (entry == null) "备份不存在或已被删除" else "已导入 ${formatBackupTime(entry.createdAtMillis)} 的备份（占位）",
            errorMessage = if (entry == null) "找不到备份文件" else null,
        )
    }

    override fun deleteBackup(id: String) {
        val existed = mutableState.value.entries.any { it.id == id }
        mutableState.value = mutableState.value.copy(
            entries = mutableState.value.entries.filterNot { it.id == id },
            deletingId = null,
            message = if (existed) "备份已删除（占位）" else "备份不存在或已被删除",
            errorMessage = if (existed) null else "找不到备份文件",
        )
    }
}

fun formatBackupTime(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
}.getOrDefault("时间未知")

fun formatBackupSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(Locale.CHINA, bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(Locale.CHINA, bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(Locale.CHINA, bytes / (1024.0 * 1024.0 * 1024.0))
}
