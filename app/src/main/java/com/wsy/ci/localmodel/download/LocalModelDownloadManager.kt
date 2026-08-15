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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 本地模型下载的应用专属目录、状态及 WorkManager 控制入口；按 [spec] 参数化，一个模型一份实例。 */
class LocalModelDownloadManager private constructor(
    private val context: Context,
    val spec: LocalModelSpec,
) {
    private val prefs = context.getSharedPreferences(spec.prefsName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<LocalModelDownloadState> = _state
    private val lock = Any()
    private val stopping = AtomicBoolean(false)
    private val generation = AtomicLong(prefs.getLong(KEY_GENERATION, 0L))

    val rootDirectory: File get() = File(context.filesDir, "local-model/${spec.directoryName}")
    val activeDirectory: File
        get() = File(rootDirectory, "revisions/${spec.manifest.revision}")

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
                        LocalModelDownloadWorker.KEY_MODEL_ID to spec.id,
                    )
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(spec.workName, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun pause() {
        synchronized(lock) {
            stopping.set(true)
            invalidateGeneration()
            update { it.copy(status = LocalModelDownloadStatus.PAUSED) }
            WorkManager.getInstance(context).cancelUniqueWork(spec.workName)
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
            WorkManager.getInstance(context).cancelUniqueWork(spec.workName)
        }
    }

    fun delete() {
        synchronized(lock) {
            stopping.set(true)
            invalidateGeneration()
            WorkManager.getInstance(context).cancelUniqueWork(spec.workName)
            rootDirectory.deleteRecursively()
            prefs.edit().remove(KEY_STATE).apply()
            _state.value = LocalModelDownloadState.initial(spec.manifest)
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
        val revisionDir = File(rootDirectory, "revisions/${spec.manifest.revision}")
        require(revisionDir.isDirectory) { "固定 revision 目录不存在" }
        val marker = File(rootDirectory, "active_revision.next")
        marker.writeText(spec.manifest.revision)
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
        val raw = prefs.getString(KEY_STATE, null) ?: return LocalModelDownloadState.initial(spec.manifest)
        return runCatching { json.decodeFromString<LocalModelDownloadState>(raw) }.getOrElse { LocalModelDownloadState.initial(spec.manifest) }
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
        private const val KEY_STATE = "state"
        private const val KEY_GENERATION = "generation"
        const val WORK_TAG = "local-model-download"

        private val instances = ConcurrentHashMap<String, LocalModelDownloadManager>()

        /** 同一个 [spec] 在整个进程内只有一份实例，多个模型互不干扰。 */
        fun get(context: Context, spec: LocalModelSpec): LocalModelDownloadManager =
            instances.getOrPut(spec.id) { LocalModelDownloadManager(context.applicationContext, spec) }
    }
}
