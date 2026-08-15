package com.wsy.ci.work

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.wsy.ci.localmodel.download.ArchiveExtractor
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.localmodel.download.LocalModelDownloadStatus
import com.wsy.ci.localmodel.download.LocalModelFileStatus
import com.wsy.ci.localmodel.download.LocalModelSpec
import com.wsy.ci.localmodel.download.LocalModelSpecs
import com.wsy.ci.localmodel.download.LocalModelVerifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 按 [LocalModelSpec] 下载固定 revision 的模型文件；每个文件先写 .part，校验后才进入 staging。 */
class LocalModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val spec: LocalModelSpec? = LocalModelSpecs.byId(inputData.getString(KEY_MODEL_ID) ?: "")
    private val manager by lazy { LocalModelDownloadManager.get(applicationContext, spec!!) }
    private val client = OkHttpClient()
    private val generation = inputData.getLong(KEY_GENERATION, -1L)

    override suspend fun doWork(): Result {
        val spec = spec ?: return Result.failure()
        setForeground(downloadForegroundInfo(spec))
        return withContext(Dispatchers.IO) {
            if (manager.currentState().status == LocalModelDownloadStatus.CANCELED) return@withContext Result.success()
            val staging = File(manager.rootDirectory, "revisions/${spec.manifest.revision}.staging")
            val finalDir = File(manager.rootDirectory, "revisions/${spec.manifest.revision}")
            try {
                manager.rootDirectory.mkdirs()

                // 归档类模型解压耗时较长，很容易在一次执行窗口内跑不完被系统打断；重试时
                // 不能无脑清空 finalDir 从头再下载一遍，先看看上次的进度能不能直接接上。
                if (spec.archiveFilePath != null && finalDir.isDirectory) {
                    if (ARCHIVE_ENTRY_WHITELIST.all { File(finalDir, it).isFile }) {
                        manager.markRunning()
                        return@withContext finishArchive(spec, finalDir)
                    }
                    val archiveFile = File(finalDir, spec.archiveFilePath)
                    val archiveManifest = spec.manifest.file(spec.archiveFilePath)
                    if (archiveManifest != null &&
                        LocalModelVerifier.verify(archiveFile, archiveManifest.size, archiveManifest.sha256)
                    ) {
                        manager.markRunning()
                        return@withContext finishArchive(spec, finalDir)
                    }
                }

                val remaining = spec.manifest.totalBytes - manager.currentState().downloadedBytes
                if (manager.rootDirectory.usableSpace < remaining + MIN_FREE_SPACE) {
                    manager.markFailed("可用空间不足，需要剩余文件大小外再预留 512 MiB")
                    return@withContext Result.failure()
                }
                manager.markRunning()
                staging.mkdirs()
                for (file in spec.manifest.files) {
                    if (isStopped || manager.shouldStop(generation)) return@withContext Result.success()
                    val target = File(staging, file.path)
                    target.parentFile?.mkdirs()
                    if (!downloadAndVerify(spec, file.path, file.size, file.sha256, target)) {
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
                if (spec.archiveFilePath != null) {
                    return@withContext finishArchive(spec, finalDir)
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

    /**
     * 解压（若已经全部解压出来则跳过，衔接被打断后的重试）+ 清理归档 + 激活 + 标记完成。
     * 解压失败时标记失败并返回 Failure；archive 已经不在也不报错，delete() 本身就是幂等的。
     */
    private fun finishArchive(spec: LocalModelSpec, finalDir: File): Result {
        val archive = File(finalDir, spec.archiveFilePath!!)
        if (!ARCHIVE_ENTRY_WHITELIST.all { File(finalDir, it).isFile }) {
            try {
                ArchiveExtractor.extract(archive, finalDir, ARCHIVE_ENTRY_WHITELIST)
            } catch (e: Exception) {
                manager.markFailed("模型解压失败：${e.message}")
                return Result.failure()
            }
        }
        archive.delete()
        manager.activate()
        manager.markCompleted()
        return Result.success()
    }

    private fun downloadForegroundInfo(spec: LocalModelSpec): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "本地模型下载", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(spec.notificationTitle)
            .setContentText("可在应用设置中暂停、继续或取消")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(
            spec.notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun downloadAndVerify(spec: LocalModelSpec, path: String, size: Long, sha256: String, target: File): Boolean {
        repeat(2) { attempt ->
            val part = File(target.parentFile, "${target.name}.part")
            val offset = part.length().coerceAtMost(size)
            manager.updateFile(path) { it.copy(downloaded = offset, status = LocalModelFileStatus.DOWNLOADING, error = null) }
            val requestBuilder = Request.Builder().url(spec.urlOf(spec.manifest.file(path)!!))
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
                                manager.updateFile(path) { it.copy(downloaded = downloaded.coerceAtMost(size), status = LocalModelFileStatus.DOWNLOADING) }
                            }
                        }
                    }
                }
            }
            manager.markVerifying()
            if (!LocalModelVerifier.verify(part, size, sha256)) {
                manager.updateFile(path) { it.copy(downloaded = part.length(), status = LocalModelFileStatus.FAILED, error = "大小或 SHA256 不匹配") }
                part.delete()
                if (attempt == 1) return false
            } else {
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                manager.updateFile(path) { it.copy(downloaded = size, status = LocalModelFileStatus.COMPLETED, error = null) }
                manager.markRunning()
                return true
            }
        }
        return false
    }

    companion object {
        const val KEY_ALLOW_METERED = "allow_metered"
        const val KEY_GENERATION = "generation"
        const val KEY_MODEL_ID = "model_id"
        private val ARCHIVE_ENTRY_WHITELIST = setOf("model.int8.onnx", "tokens.txt")
        private const val DEFAULT_BUFFER = 128 * 1024
        private const val CHANNEL_ID = "local_model_download"
        private const val MIN_FREE_SPACE = 512L * 1024L * 1024L
    }
}
