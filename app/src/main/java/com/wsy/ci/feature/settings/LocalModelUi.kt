package com.wsy.ci.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 设置页使用的本地模型路由标识。核心路由接线时保持该值不变。 */
const val LOCAL_MODEL_ROUTE_ID = "local"

/** 本地模型下载阶段。下载器可以把失败原因写入 [LocalModelUiState.errorMessage]。 */
enum class LocalModelInstallState {
    NOT_INSTALLED,
    WAITING_NETWORK,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    FAILED,
    INSTALLED,
}

/** 本地推理服务阶段。 */
enum class LocalModelServiceState {
    OFF,
    STARTING,
    ON,
    INFERENCING,
}

/** 设置页展示所需的最小本地模型信息，不依赖具体下载器或推理库。 */
data class LocalModelUiState(
    val modelName: String = "Compound 本地模型",
    val version: String = "v0.1.0",
    val source: String = "ModelScope · 官方镜像",
    val sizeLabel: String = "1.39GB",
    val installState: LocalModelInstallState = LocalModelInstallState.NOT_INSTALLED,
    val downloadProgress: Float = 0f,
    val serviceState: LocalModelServiceState = LocalModelServiceState.OFF,
    val errorMessage: String? = null,
)

/**
 * 本地模型下载/服务适配层。
 *
 * 设置页只依赖这组动作；真实下载器与 MNN 推理运行时由应用容器提供，Compose 与路由表
 * 不直接持有 native 对象。回调均应在返回前保证状态流已更新。
 */
interface LocalModelController {
    val state: StateFlow<LocalModelUiState>

    fun download(allowMetered: Boolean = false)
    fun pauseDownload()
    fun resumeDownload()
    fun cancelDownload()
    fun deleteModel()
    fun startService()
    fun stopService()
    fun testInference()
    fun testVision()
    fun cancelInferenceAndStop()
}

/** 仅供预览和独立 UI 测试使用的内存适配器，生产入口使用 [AppLocalModelController]。 */
class InMemoryLocalModelController : LocalModelController {
    private val mutableState = MutableStateFlow(LocalModelUiState())
    override val state: StateFlow<LocalModelUiState> = mutableState

    override fun download(allowMetered: Boolean) {
        mutableState.value = mutableState.value.copy(
            installState = LocalModelInstallState.DOWNLOADING,
            downloadProgress = 0f,
            errorMessage = null,
        )
    }

    override fun pauseDownload() {
        if (mutableState.value.installState == LocalModelInstallState.DOWNLOADING) {
            mutableState.value = mutableState.value.copy(installState = LocalModelInstallState.PAUSED)
        }
    }

    override fun resumeDownload() {
        if (mutableState.value.installState == LocalModelInstallState.PAUSED) {
            mutableState.value = mutableState.value.copy(installState = LocalModelInstallState.DOWNLOADING)
        }
    }

    override fun cancelDownload() {
        if (mutableState.value.installState == LocalModelInstallState.DOWNLOADING ||
            mutableState.value.installState == LocalModelInstallState.PAUSED
        ) {
            mutableState.value = mutableState.value.copy(
                installState = LocalModelInstallState.NOT_INSTALLED,
                downloadProgress = 0f,
                errorMessage = null,
            )
        }
    }

    override fun deleteModel() {
        mutableState.value = mutableState.value.copy(
            installState = LocalModelInstallState.NOT_INSTALLED,
            downloadProgress = 0f,
            serviceState = LocalModelServiceState.OFF,
            errorMessage = null,
        )
    }

    override fun startService() {
        if (mutableState.value.installState == LocalModelInstallState.INSTALLED) {
            mutableState.value = mutableState.value.copy(serviceState = LocalModelServiceState.STARTING)
            // 预览适配器直接完成启动。
            mutableState.value = mutableState.value.copy(serviceState = LocalModelServiceState.ON)
        }
    }

    override fun stopService() {
        if (mutableState.value.serviceState != LocalModelServiceState.OFF) {
            mutableState.value = mutableState.value.copy(serviceState = LocalModelServiceState.OFF)
        }
    }

    override fun testInference() {
        if (mutableState.value.serviceState == LocalModelServiceState.ON) {
            mutableState.value = mutableState.value.copy(serviceState = LocalModelServiceState.INFERENCING)
        }
    }

    override fun testVision() {
        if (mutableState.value.serviceState == LocalModelServiceState.ON) {
            mutableState.value = mutableState.value.copy(serviceState = LocalModelServiceState.INFERENCING)
        }
    }

    override fun cancelInferenceAndStop() {
        mutableState.value = mutableState.value.copy(serviceState = LocalModelServiceState.OFF)
    }
}
