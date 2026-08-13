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
