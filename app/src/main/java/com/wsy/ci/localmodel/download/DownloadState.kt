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

import kotlinx.serialization.Serializable

@Serializable
enum class LocalModelDownloadStatus {
    IDLE, WAITING_NETWORK, QUEUED, DOWNLOADING, PAUSED, VERIFYING, COMPLETED, FAILED, CANCELED,
}

@Serializable
enum class LocalModelFileStatus { PENDING, DOWNLOADING, COMPLETED, FAILED }

@Serializable
data class LocalModelFileProgress(
    val path: String,
    val size: Long,
    val downloaded: Long = 0L,
    val status: LocalModelFileStatus = LocalModelFileStatus.PENDING,
    val error: String? = null,
) {
    val fraction: Float get() = if (size <= 0) 0f else (downloaded.toFloat() / size).coerceIn(0f, 1f)
}

@Serializable
data class LocalModelDownloadState(
    val status: LocalModelDownloadStatus = LocalModelDownloadStatus.IDLE,
    val files: List<LocalModelFileProgress> = emptyList(),
    val error: String? = null,
    val updatedAt: Long = 0L,
) {
    val downloadedBytes: Long get() = files.sumOf { it.downloaded.coerceAtMost(it.size) }
    val totalBytes: Long get() = files.sumOf { it.size }
    val fraction: Float get() = if (totalBytes <= 0L) 0f else downloadedBytes.toFloat() / totalBytes
    val isTerminal: Boolean get() = status == LocalModelDownloadStatus.COMPLETED || status == LocalModelDownloadStatus.FAILED

    companion object {
        fun initial(manifest: LocalModelManifest): LocalModelDownloadState =
            LocalModelDownloadState(files = manifest.files.map { LocalModelFileProgress(it.path, it.size) })
    }
}

/** 纯 Kotlin 的并发安全状态持有器；持久化层由应用侧注入。 */
class LocalModelDownloadStateHolder(
    initial: LocalModelDownloadState,
    private val persist: (LocalModelDownloadState) -> Unit = {},
) {
    private val lock = Any()
    @Volatile private var current = initial
    fun get(): LocalModelDownloadState = synchronized(lock) { current }
    fun update(transform: (LocalModelDownloadState) -> LocalModelDownloadState): LocalModelDownloadState = synchronized(lock) {
        current = transform(current).copy(updatedAt = System.currentTimeMillis())
        persist(current)
        current
    }
}
