package com.wsy.ci.localmodel.runtime

import com.wsy.ci.llm.LlmImageRequest
import com.wsy.ci.llm.LocalGenerationOptions

/** 传给本地推理后端的文本请求。 */
data class LocalInferenceRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val options: LocalGenerationOptions,
)

/** 传给本地视觉模型的请求；RGBA 仍由 Kotlin 层校验尺寸和长度。 */
data class LocalImageInferenceRequest(
    val image: LlmImageRequest,
    val options: LocalGenerationOptions,
)

/**
 * MNN/JNI 的最小桥接面。实现只负责同步调用 native，不负责队列、生命周期和路由。
 * native 侧可以用异常表达加载或推理错误，控制器会转换为结构化错误。
 */
interface LocalModelBridge {
    fun load(modelPath: String): Boolean
    fun unload()
    fun generate(request: LocalInferenceRequest): String
    fun generateImage(request: LocalImageInferenceRequest): String
    fun cancel()
}

/** MNN JNI 默认实现。库加载错误延迟到首次启动模型时报告，不能阻断应用冷启动。 */
class JniMnnNativeBridge : LocalModelBridge {
    override fun load(modelPath: String): Boolean {
        nativeLoadError?.let { throw IllegalStateException("MNN 运行库加载失败：${it.message}", it) }
        return nativeLoad(modelPath)
    }

    override fun unload() = nativeUnload()

    override fun generate(request: LocalInferenceRequest): String = nativeGenerate(
        request.systemPrompt,
        request.userPrompt,
        request.options.thinking,
        request.options.maxTokens,
    )

    override fun generateImage(request: LocalImageInferenceRequest): String = nativeGenerateImage(
        request.image.rgba,
        request.image.width,
        request.image.height,
        request.image.systemPrompt,
        request.image.userPrompt,
        request.options.thinking,
        request.options.maxTokens,
    )

    override fun cancel() = nativeCancel()

    private external fun nativeLoad(modelPath: String): Boolean
    private external fun nativeUnload()
    private external fun nativeGenerate(
        systemPrompt: String,
        userPrompt: String,
        thinking: Boolean,
        maxTokens: Int,
    ): String

    private external fun nativeGenerateImage(
        rgba: ByteArray,
        width: Int,
        height: Int,
        systemPrompt: String,
        userPrompt: String,
        thinking: Boolean,
        maxTokens: Int,
    ): String

    private external fun nativeCancel()

    companion object {
        private val nativeLoadError: Throwable? = runCatching {
            System.loadLibrary("MNN")
            System.loadLibrary("cimnn")
        }.exceptionOrNull()
    }
}
