package com.wsy.ci.localmodel.download

/**
 * 一个本地模型的下载身份：目录、SharedPreferences、WorkManager 名称、通知全部隔离，
 * 使 [LocalModelDownloadManager] 可以按 [id] 同时管理多个互不干扰的模型下载。
 */
data class LocalModelSpec(
    val id: String,
    /** filesDir/local-model/<directoryName> */
    val directoryName: String,
    val prefsName: String,
    val workName: String,
    val notificationId: Int,
    val notificationTitle: String,
    val manifest: LocalModelManifest,
    /** 非空表示该文件是需要解压的归档包（如 tar.bz2）。 */
    val archiveFilePath: String? = null,
    val urlOf: (LocalModelFile) -> String,
)

object LocalModelSpecs {
    /** 各字段保持与改造前的硬编码值完全一致，已下载用户零迁移，不需要重新下载 1.39 GB。 */
    val QWEN35 = LocalModelSpec(
        id = "qwen35-2b-mnn",
        directoryName = "qwen3.5-2b-mnn",
        prefsName = "local_model_download",
        workName = "qwen35-2b-mnn-download",
        notificationId = 3502,
        notificationTitle = "正在下载 Qwen3.5-2B 本地模型",
        manifest = Qwen35ModelManifest.manifest,
        urlOf = { file -> Qwen35ModelManifest.url(file) },
    )

    val SENSE_VOICE = LocalModelSpec(
        id = "sensevoice-int8",
        directoryName = "sensevoice-int8",
        prefsName = "asr_model_download",
        workName = "sensevoice-download",
        notificationId = 3503,
        notificationTitle = "正在下载语音识别模型",
        manifest = SenseVoiceManifest.manifest,
        archiveFilePath = "sensevoice.tar.bz2",
        urlOf = { SenseVoiceManifest.DOWNLOAD_URL },
    )

    private val all = listOf(QWEN35, SENSE_VOICE)

    fun byId(id: String): LocalModelSpec? = all.firstOrNull { it.id == id }
}
