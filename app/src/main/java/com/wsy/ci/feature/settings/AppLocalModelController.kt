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

import android.content.Context
import android.graphics.BitmapFactory
import com.wsy.ci.R
import com.wsy.ci.llm.LlmImageRequest
import com.wsy.ci.llm.MnnLlmGateway
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.localmodel.download.LocalModelDownloadStatus
import com.wsy.ci.localmodel.runtime.LocalModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性动作反馈（女巫头像成功/错误表情）展示多久后回落到常驻状态。 */
private const val ACTION_FEEDBACK_DURATION_MS = 3_000L

/** 把下载器与 MNN 运行时合并为设置页和导航栏共用的真实控制器。 */
class AppLocalModelController(
    private val context: Context,
    private val downloads: LocalModelDownloadManager,
    private val gateway: MnnLlmGateway,
) : LocalModelController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actionMessage = MutableStateFlow<String?>(null)
    private val actionFeedback = MutableStateFlow<LocalModelActionFeedback?>(null)
    private var feedbackClearJob: Job? = null

    private fun postActionFeedback(message: String, feedback: LocalModelActionFeedback) {
        feedbackClearJob?.cancel()
        actionMessage.value = message
        actionFeedback.value = feedback
        feedbackClearJob = scope.launch {
            delay(ACTION_FEEDBACK_DURATION_MS)
            actionMessage.value = null
            actionFeedback.value = null
        }
    }

    override val state: StateFlow<LocalModelUiState> = combine(
        downloads.state,
        gateway.state,
        actionMessage,
        actionFeedback,
    ) { download, runtime, message, feedback ->
        val install = when (download.status) {
            LocalModelDownloadStatus.WAITING_NETWORK -> LocalModelInstallState.WAITING_NETWORK
            LocalModelDownloadStatus.QUEUED -> LocalModelInstallState.QUEUED
            LocalModelDownloadStatus.DOWNLOADING -> LocalModelInstallState.DOWNLOADING
            LocalModelDownloadStatus.PAUSED -> LocalModelInstallState.PAUSED
            LocalModelDownloadStatus.VERIFYING -> LocalModelInstallState.VERIFYING
            LocalModelDownloadStatus.COMPLETED -> LocalModelInstallState.INSTALLED
            LocalModelDownloadStatus.FAILED -> LocalModelInstallState.FAILED
            LocalModelDownloadStatus.IDLE,
            LocalModelDownloadStatus.CANCELED -> LocalModelInstallState.NOT_INSTALLED
        }
        val service = when (runtime) {
            LocalModelState.Stopped,
            is LocalModelState.Failed -> LocalModelServiceState.OFF
            LocalModelState.Starting,
            LocalModelState.Stopping -> LocalModelServiceState.STARTING
            LocalModelState.Ready -> LocalModelServiceState.ON
            LocalModelState.Busy -> LocalModelServiceState.INFERENCING
        }
        LocalModelUiState(
            modelName = "Qwen3.5-2B · MNN",
            version = "MNN 3.6.1 · 固定 revision b9ae8c8f",
            source = "ModelScope · MNN 官方转换",
            sizeLabel = "1.39 GB",
            installState = install,
            downloadProgress = download.fraction,
            serviceState = service,
            errorMessage = message ?: download.error ?: (runtime as? LocalModelState.Failed)?.error?.message,
            actionFeedback = feedback,
        )
    }.stateIn(scope, SharingStarted.Eagerly, LocalModelUiState())

    override fun download(allowMetered: Boolean) = downloads.enqueue(allowMetered)
    override fun pauseDownload() = downloads.pause()
    override fun resumeDownload() = downloads.resume()
    override fun cancelDownload() = downloads.cancel()

    override fun deleteModel() {
        scope.launch {
            gateway.stop()
            downloads.delete()
        }
    }

    override fun startService() {
        feedbackClearJob?.cancel()
        actionMessage.value = null
        actionFeedback.value = null
        scope.launch {
            val result = gateway.start()
            if (result is com.wsy.ci.localmodel.runtime.LocalModelResult.Failure) {
                postActionFeedback(result.error.message, LocalModelActionFeedback.ERROR)
            }
        }
    }

    override fun stopService() {
        scope.launch { gateway.stop() }
    }

    override fun testInference() {
        scope.launch {
            when (val result = gateway.smokeTest()) {
                is com.wsy.ci.localmodel.runtime.LocalModelResult.Success ->
                    postActionFeedback("本地模型测试成功：${result.content.trim()}", LocalModelActionFeedback.SUCCESS)
                is com.wsy.ci.localmodel.runtime.LocalModelResult.Failure ->
                    postActionFeedback(result.error.message, LocalModelActionFeedback.ERROR)
            }
        }
    }

    override fun testVision() {
        scope.launch {
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_ai_local_on)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val rgba = ByteArray(pixels.size * 4)
            pixels.forEachIndexed { index, color ->
                val offset = index * 4
                rgba[offset] = ((color shr 16) and 0xff).toByte()
                rgba[offset + 1] = ((color shr 8) and 0xff).toByte()
                rgba[offset + 2] = (color and 0xff).toByte()
                rgba[offset + 3] = ((color ushr 24) and 0xff).toByte()
            }
            val result = gateway.visualSmokeTest(
                LlmImageRequest(
                    rgba = rgba,
                    width = bitmap.width,
                    height = bitmap.height,
                    systemPrompt = "你是图片理解测试器。",
                    userPrompt = "请用一句简体中文准确描述图片中的人物。",
                )
            )
            bitmap.recycle()
            when (result) {
                is com.wsy.ci.localmodel.runtime.LocalModelResult.Success ->
                    postActionFeedback("图片测试成功：${result.content.trim()}", LocalModelActionFeedback.SUCCESS)
                is com.wsy.ci.localmodel.runtime.LocalModelResult.Failure ->
                    postActionFeedback(result.error.message, LocalModelActionFeedback.ERROR)
            }
        }
    }

    override fun cancelInferenceAndStop() {
        scope.launch {
            gateway.cancelCurrent()
            gateway.stop()
        }
    }
}
