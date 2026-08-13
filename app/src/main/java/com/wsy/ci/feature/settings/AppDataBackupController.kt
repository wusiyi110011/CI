package com.wsy.ci.feature.settings

import com.wsy.ci.core.backup.DataBackupManager
import com.wsy.ci.core.backup.DataBackupOperation
import com.wsy.ci.widget.CiWidgetUpdater
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 把备份引擎状态映射为设置页状态，并在导入后刷新桌面小组件。 */
class AppDataBackupController(
    context: Context,
    private val manager: DataBackupManager,
) : DataBackupController {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val state: StateFlow<DataBackupUiState> = manager.state.map { source ->
        DataBackupUiState(
            entries = source.backups.map {
                BackupItem(it.id, it.createdAtMillis, it.sizeBytes, it.label)
            },
            backingUp = source.operation == DataBackupOperation.CREATING,
            importingId = source.targetId?.takeIf { source.operation == DataBackupOperation.RESTORING },
            deletingId = source.targetId?.takeIf { source.operation == DataBackupOperation.DELETING },
            message = source.message?.takeUnless { it.contains("失败") },
            errorMessage = source.message?.takeIf { it.contains("失败") },
        )
    }.stateIn(scope, SharingStarted.Eagerly, DataBackupUiState())

    override fun refresh() = manager.refresh()

    override fun createBackup() {
        scope.launch { manager.createBackup() }
    }

    override fun restoreBackup(id: String) {
        scope.launch {
            if (manager.restoreBackup(id).isSuccess) CiWidgetUpdater.updateAll(appContext)
        }
    }

    override fun deleteBackup(id: String) {
        scope.launch { manager.deleteBackup(id) }
    }
}
