package com.wsy.ci.localmodel.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wsy.ci.work.LocalModelDownloadWorker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Qwen 本地模型下载的应用专属目录、状态及 WorkManager 控制入口。 */
class LocalModelDownloadManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<LocalModelDownloadState> = _state
    private val lock = Any()
    private val stopping = AtomicBoolean(false)
    private val generation = AtomicLong(prefs.getLong(KEY_GENERATION, 0L))

    val rootDirectory: File get() = File(context.filesDir, "local-model/qwen3.5-2b-mnn")
    val activeDirectory: File
        get() = File(rootDirectory, "revisions/${Qwen35ModelManifest.REVISION}")

    fun enqueue(allowMetered: Boolean = false) {
        synchronized(lock) {
            stopping.set(false)
            val nextGeneration = generation.incrementAndGet().also {
                prefs.edit().putLong(KEY_GENERATION, it).apply()
            }
            update {
                it.copy(
                    status = if (networkReady(allowMetered)) {
                        LocalModelDownloadStatus.QUEUED
                    } else {
                        LocalModelDownloadStatus.WAITING_NETWORK
                    },
                    error = null,
                )
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .build()
            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        LocalModelDownloadWorker.KEY_ALLOW_METERED to allowMetered,
                        LocalModelDownloadWorker.KEY_GENERATION to nextGeneration,
                    )
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun pause() {
        synchronized(lock) {
            stopping.set(true)
            invalidateGeneration()
            update { it.copy(status = LocalModelDownloadStatus.PAUSED) }
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    fun resume(allowMetered: Boolean = false) {
        stopping.set(false)
        enqueue(allowMetered)
    }

    fun cancel() {
        synchronized(lock) {
            stopping.set(true)
            invalidateGeneration()
            update { it.copy(status = LocalModelDownloadStatus.CANCELED) }
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    fun delete() {
        synchronized(lock) {
            stopping.set(true)
            invalidateGeneration()
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            rootDirectory.deleteRecursively()
            prefs.edit().remove(KEY_STATE).apply()
            _state.value = LocalModelDownloadState.initial(Qwen35ModelManifest.manifest)
        }
    }

    internal fun shouldStop(workerGeneration: Long): Boolean =
        workerGeneration != generation.get() || stopping.get() ||
            _state.value.status == LocalModelDownloadStatus.PAUSED ||
            _state.value.status == LocalModelDownloadStatus.CANCELED

    internal fun isGenerationActive(workerGeneration: Long): Boolean = workerGeneration == generation.get()

    internal fun markRunning() = update { it.copy(status = LocalModelDownloadStatus.DOWNLOADING, error = null) }
    internal fun markVerifying() = update { it.copy(status = LocalModelDownloadStatus.VERIFYING, error = null) }
    internal fun markCompleted() = update { it.copy(status = LocalModelDownloadStatus.COMPLETED, error = null) }
    internal fun markFailed(error: String) = update { it.copy(status = LocalModelDownloadStatus.FAILED, error = error) }
    internal fun activate() {
        val revisionDir = File(rootDirectory, "revisions/${Qwen35ModelManifest.REVISION}")
        require(revisionDir.isDirectory) { "固定 revision 目录不存在" }
        val marker = File(rootDirectory, "active_revision.next")
        marker.writeText(Qwen35ModelManifest.REVISION)
        java.nio.file.Files.move(
            marker.toPath(),
            File(rootDirectory, "active_revision").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
    internal fun updateFile(path: String, transform: (LocalModelFileProgress) -> LocalModelFileProgress) = update {
        it.copy(files = it.files.map { f -> if (f.path == path) transform(f) else f })
    }
    internal fun currentState(): LocalModelDownloadState = _state.value

    private fun update(transform: (LocalModelDownloadState) -> LocalModelDownloadState) {
        val next = transform(_state.value).copy(updatedAt = System.currentTimeMillis())
        _state.value = next
        prefs.edit().putString(KEY_STATE, json.encodeToString(next)).apply()
    }

    private fun loadState(): LocalModelDownloadState {
        val raw = prefs.getString(KEY_STATE, null) ?: return LocalModelDownloadState.initial(Qwen35ModelManifest.manifest)
        return runCatching { json.decodeFromString<LocalModelDownloadState>(raw) }.getOrElse { LocalModelDownloadState.initial(Qwen35ModelManifest.manifest) }
    }

    private fun networkReady(allowMetered: Boolean): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return allowMetered || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun invalidateGeneration() {
        val next = generation.incrementAndGet()
        prefs.edit().putLong(KEY_GENERATION, next).apply()
    }

    companion object {
        private const val PREFS = "local_model_download"
        private const val KEY_STATE = "state"
        private const val KEY_GENERATION = "generation"
        const val WORK_NAME = "qwen35-2b-mnn-download"
        const val WORK_TAG = "local-model-download"

        @Volatile private var instance: LocalModelDownloadManager? = null
        fun get(context: Context): LocalModelDownloadManager = instance ?: synchronized(this) {
            instance ?: LocalModelDownloadManager(context.applicationContext).also { instance = it }
        }
    }
}
