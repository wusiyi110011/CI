package com.wsy.ci.work

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.localmodel.download.LocalModelDownloadStatus
import com.wsy.ci.localmodel.download.Qwen35ModelManifest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载固定 revision 的 Qwen 模型；每个文件先写 .part，校验后才进入 staging。 */
class LocalModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val manager = LocalModelDownloadManager.get(applicationContext)
    private val client = OkHttpClient()
    private val generation = inputData.getLong(KEY_GENERATION, -1L)

    override suspend fun doWork(): Result {
        setForeground(downloadForegroundInfo())
        return withContext(Dispatchers.IO) {
            if (manager.currentState().status == LocalModelDownloadStatus.CANCELED) return@withContext Result.success()
            val staging = File(manager.rootDirectory, "revisions/${Qwen35ModelManifest.REVISION}.staging")
            val finalDir = File(manager.rootDirectory, "revisions/${Qwen35ModelManifest.REVISION}")
            try {
                manager.rootDirectory.mkdirs()
                val remaining = Qwen35ModelManifest.manifest.totalBytes - manager.currentState().downloadedBytes
                if (manager.rootDirectory.usableSpace < remaining + MIN_FREE_SPACE) {
                    manager.markFailed("可用空间不足，需要剩余文件大小外再预留 512 MiB")
                    return@withContext Result.failure()
                }
                manager.markRunning()
                staging.mkdirs()
                for (file in Qwen35ModelManifest.manifest.files) {
                    if (isStopped || manager.shouldStop(generation)) return@withContext Result.success()
                    val target = File(staging, file.path)
                    target.parentFile?.mkdirs()
                    if (!downloadAndVerify(file.path, file.size, file.sha256, target)) {
                        if (isStopped || manager.shouldStop(generation)) return@withContext Result.success()
                        manager.markFailed("文件校验失败：${file.path}")
                        return@withContext Result.failure()
                    }
                }
                if (isStopped || manager.shouldStop(generation)) return@withContext Result.success()
                if (finalDir.exists()) finalDir.deleteRecursively()
                runCatching {
                    Files.move(staging.toPath(), finalDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    // 某些文件系统不声明 ATOMIC_MOVE；同一应用目录内退化为普通移动，仍不会暴露 staging。
                    Files.move(staging.toPath(), finalDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                manager.activate()
                manager.markCompleted()
                Result.success()
            } catch (e: IOException) {
                if (isStopped || manager.shouldStop(generation)) Result.success()
                else {
                    manager.markFailed(e.message ?: "网络下载失败")
                    Result.retry()
                }
            } catch (e: Exception) {
                if (manager.isGenerationActive(generation)) manager.markFailed(e.message ?: "模型下载失败")
                Result.failure()
            }
        }
    }

    private fun downloadForegroundInfo(): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "本地模型下载", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 Qwen3.5-2B 本地模型")
            .setContentText("可在应用设置中暂停、继续或取消")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun downloadAndVerify(path: String, size: Long, sha256: String, target: File): Boolean {
        repeat(2) { attempt ->
            val part = File(target.parentFile, "${target.name}.part")
            val offset = part.length().coerceAtMost(size)
            manager.updateFile(path) { it.copy(downloaded = offset, status = com.wsy.ci.localmodel.download.LocalModelFileStatus.DOWNLOADING, error = null) }
            val requestBuilder = Request.Builder().url(Qwen35ModelManifest.url(Qwen35ModelManifest.manifest.file(path)!!))
            if (offset > 0L) requestBuilder.header("Range", "bytes=$offset-")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 416) throw IOException("HTTP ${response.code}")
                val body = response.body
                if (response.code == 416 && part.length() == size) {
                    // 已完整下载，直接进入校验。
                } else {
                    val append = offset > 0L && response.code == 206
                    body ?: throw IOException("响应无内容")
                    body.byteStream().use { input ->
                        FileOutputStream(part, append).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER)
                            var downloaded = if (append) offset else 0L
                            while (true) {
                                if (isStopped || manager.shouldStop(generation)) return false
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                downloaded += n
                                manager.updateFile(path) { it.copy(downloaded = downloaded.coerceAtMost(size), status = com.wsy.ci.localmodel.download.LocalModelFileStatus.DOWNLOADING) }
                            }
                        }
                    }
                }
            }
            manager.markVerifying()
            if (part.length() != size || sha256(part) != sha256) {
                manager.updateFile(path) { it.copy(downloaded = part.length(), status = com.wsy.ci.localmodel.download.LocalModelFileStatus.FAILED, error = "大小或 SHA256 不匹配") }
                part.delete()
                if (attempt == 1) return false
            } else {
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                manager.updateFile(path) { it.copy(downloaded = size, status = com.wsy.ci.localmodel.download.LocalModelFileStatus.COMPLETED, error = null) }
                manager.markRunning()
                return true
            }
        }
        return false
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_ALLOW_METERED = "allow_metered"
        const val KEY_GENERATION = "generation"
        private const val DEFAULT_BUFFER = 128 * 1024
        private const val CHANNEL_ID = "local_model_download"
        private const val NOTIFICATION_ID = 3502
        private const val MIN_FREE_SPACE = 512L * 1024L * 1024L
    }
}
