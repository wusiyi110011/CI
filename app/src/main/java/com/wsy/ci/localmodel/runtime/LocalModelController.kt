package com.wsy.ci.localmodel.runtime

import com.wsy.ci.llm.LlmError
import com.wsy.ci.llm.LlmErrorCode
import com.wsy.ci.llm.LlmErrorSource
import com.wsy.ci.llm.LlmImageRequest
import com.wsy.ci.llm.LocalGenerationOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface LocalModelState {
    data object Stopped : LocalModelState
    data object Starting : LocalModelState
    data object Ready : LocalModelState
    data object Busy : LocalModelState
    data object Stopping : LocalModelState
    data class Failed(val error: LlmError) : LocalModelState
}

sealed interface LocalModelResult {
    data class Success(val content: String) : LocalModelResult
    data class Failure(val error: LlmError) : LocalModelResult
}

/**
 * 本地模型生命周期和单队列控制器。
 *
 * 控制器只负责一份模型实例、串行请求和可取消的生命周期；具体 MNN/JNI 由
 * [LocalModelBridge] 提供。`stop` 会先通知 bridge 取消当前 native 推理，再释放模型，
 * 因此切后台时可以安全调用并在下次进入前台重新 `start`。
 */
class LocalModelController(
    private val bridge: LocalModelBridge,
    private val modelPath: String,
    parentScope: CoroutineScope? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val preflight: suspend () -> Unit = {},
) {
    private val scope = parentScope ?: CoroutineScope(Job() + dispatcher)
    private val ownsScope = parentScope == null
    private val lifecycle = Mutex()
    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.Stopped)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private var queue: Channel<Work>? = null
    private var worker: Job? = null
    @Volatile
    private var activeWork: Work? = null
    private val closed = AtomicBoolean(false)

    suspend fun start(): LocalModelResult {
        return lifecycle.withLock {
            when (val current = _state.value) {
                LocalModelState.Ready,
                LocalModelState.Busy -> return@withLock LocalModelResult.Success("ready")
                LocalModelState.Starting,
                LocalModelState.Stopping -> return@withLock LocalModelResult.Failure(
                    localError(LlmErrorCode.LOCAL_NOT_READY, "本地模型正在切换状态，请稍后重试")
                )
                LocalModelState.Stopped,
                is LocalModelState.Failed -> Unit
            }
            if (closed.get()) {
                return@withLock LocalModelResult.Failure(
                    localError(LlmErrorCode.LOCAL_UNAVAILABLE, "本地模型控制器已关闭")
                )
            }
            _state.value = LocalModelState.Starting
            try {
                preflight()
            } catch (e: Throwable) {
                val error = localError(
                    LlmErrorCode.LOCAL_LOAD_FAILED,
                    "本地模型文件校验失败：${e.message ?: e.javaClass.simpleName}",
                )
                _state.value = LocalModelState.Failed(error)
                return@withLock LocalModelResult.Failure(error)
            }
            val loaded = try {
                withContext(dispatcher) { bridge.load(modelPath) }
            } catch (e: CancellationException) {
                _state.value = LocalModelState.Stopped
                throw e
            } catch (e: Throwable) {
                _state.value = LocalModelState.Failed(
                    localError(LlmErrorCode.LOCAL_LOAD_FAILED, "本地模型加载失败：${e.message ?: e.javaClass.simpleName}")
                )
                return@withLock LocalModelResult.Failure((state.value as LocalModelState.Failed).error)
            }
            if (!loaded) {
                val error = localError(LlmErrorCode.LOCAL_LOAD_FAILED, "本地模型加载失败")
                _state.value = LocalModelState.Failed(error)
                return@withLock LocalModelResult.Failure(error)
            }
            queue = Channel(Channel.UNLIMITED)
            worker = scope.launch(dispatcher) { consume(queue as ReceiveChannel<Work>) }
            _state.value = LocalModelState.Ready
            val smoke = submit(
                Work.Text(
                    LocalInferenceRequest(
                        systemPrompt = "你是本地模型健康检查器。只回复 OK。",
                        userPrompt = "回复 OK",
                        options = LocalGenerationOptions(thinking = false, maxTokens = 8),
                    )
                )
            )
            if (smoke is LocalModelResult.Failure) {
                try {
                    bridge.unload()
                } catch (_: Throwable) {
                    // 启动失败时尽力释放，原始冒烟错误优先返回。
                }
                queue?.close()
                worker?.cancel()
                queue = null
                worker = null
                _state.value = LocalModelState.Failed(smoke.error)
                smoke
            } else {
                _state.value = LocalModelState.Ready
                LocalModelResult.Success("ready")
            }
        }
    }

    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        options: LocalGenerationOptions,
    ): LocalModelResult = submit(Work.Text(LocalInferenceRequest(systemPrompt, userPrompt, options)))

    suspend fun generateImage(request: LlmImageRequest): LocalModelResult = submit(
        Work.Image(LocalImageInferenceRequest(request, requestTaskOptions(request)))
    )

    /** 仅用于启动后的最小回路检查，不会访问网络。 */
    suspend fun smokeTest(): LocalModelResult = generate(
        systemPrompt = "你是本地模型健康检查器。只回复 OK。",
        userPrompt = "回复 OK",
        options = LocalGenerationOptions(thinking = false, maxTokens = 8),
    )

    suspend fun cancelCurrent() {
        // cancel 必须能在调用线程立即到达 native；不能排到可能被 native 推理占满的同一线程池。
        bridge.cancel()
    }

    suspend fun stop() {
        lifecycle.withLock {
            if (_state.value == LocalModelState.Stopped) return@withLock
            _state.value = LocalModelState.Stopping
            // cancel() 必须先于 worker cancellation，让 native 有机会中断阻塞推理。
            try {
                bridge.cancel()
            } catch (_: Throwable) {
                // 释放流程继续执行；native 错误由 unload 和下次 start 重新暴露。
            }
            val closingQueue = queue
            closingQueue?.close()
            worker?.cancel()
            worker?.join()
            activeWork?.result?.complete(
                LocalModelResult.Failure(localError(LlmErrorCode.LOCAL_CANCELLED, "本地模型请求已取消"))
            )
            while (true) {
                val pending = closingQueue?.tryReceive()?.getOrNull() ?: break
                pending.result.complete(
                    LocalModelResult.Failure(localError(LlmErrorCode.LOCAL_CANCELLED, "本地模型请求已取消"))
                )
            }
            queue = null
            worker = null
            withContext(NonCancellable + dispatcher) {
                try {
                    bridge.unload()
                } catch (_: Throwable) {
                    // stop 必须幂等且尽力释放，不能因 native unload 异常卡住退后台。
                }
            }
            _state.value = LocalModelState.Stopped
        }
    }

    /** 关闭控制器并释放其内部 scope；关闭后不可再次 start。 */
    suspend fun close() {
        closed.set(true)
        stop()
        if (ownsScope) scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun submit(work: Work): LocalModelResult {
        val current = _state.value
        if (current !is LocalModelState.Ready && current !is LocalModelState.Busy) {
            val error = when (current) {
                is LocalModelState.Failed -> current.error
                else -> localError(LlmErrorCode.LOCAL_NOT_READY, "本地模型尚未启动")
            }
            return LocalModelResult.Failure(error)
        }
        val result = work.result
        val channel = queue
            ?: return LocalModelResult.Failure(localError(LlmErrorCode.LOCAL_NOT_READY, "本地模型队列尚未启动"))
        try {
            channel.send(work)
            return result.await()
        } catch (e: CancellationException) {
            result.cancel(e)
            if (activeWork === work) {
                // 调用方主动取消当前请求时同步通知 native；排队请求不会误伤队头任务。
                try {
                    bridge.cancel()
                } catch (_: Throwable) {
                    // native 取消失败仍让调用方正常收到协程取消。
                }
            }
            throw e
        } catch (e: Throwable) {
            return LocalModelResult.Failure(
                localError(LlmErrorCode.LOCAL_UNAVAILABLE, "本地模型队列不可用：${e.message ?: e.javaClass.simpleName}")
            )
        }
    }

    private suspend fun consume(channel: ReceiveChannel<Work>) {
        for (work in channel) {
            if (!work.result.isActive) continue
            activeWork = work
            _state.value = LocalModelState.Busy
            val output = try {
                withContext(dispatcher) {
                    when (work) {
                        is Work.Text -> LocalModelResult.Success(bridge.generate(work.request))
                        is Work.Image -> LocalModelResult.Success(bridge.generateImage(work.request))
                    }
                }
            } catch (e: CancellationException) {
                LocalModelResult.Failure(localError(LlmErrorCode.LOCAL_CANCELLED, "本地模型请求已取消"))
            } catch (e: Throwable) {
                val code = if (e.message?.contains("已取消") == true) {
                    LlmErrorCode.LOCAL_CANCELLED
                } else {
                    LlmErrorCode.LOCAL_INFERENCE_FAILED
                }
                LocalModelResult.Failure(
                    localError(code, if (code == LlmErrorCode.LOCAL_CANCELLED) "本地模型请求已取消" else "本地模型推理失败：${e.message ?: e.javaClass.simpleName}")
                )
            }
            work.result.complete(output)
            activeWork = null
            if (_state.value == LocalModelState.Busy) _state.value = LocalModelState.Ready
        }
    }

    private fun requestTaskOptions(@Suppress("UNUSED_PARAMETER") request: LlmImageRequest): LocalGenerationOptions =
        LocalGenerationOptions(thinking = false, maxTokens = 512)

    private fun localError(code: LlmErrorCode, message: String) =
        LlmError(code, message, source = LlmErrorSource.LOCAL)

    private sealed class Work(val result: CompletableDeferred<LocalModelResult> = CompletableDeferred()) {
        class Text(val request: LocalInferenceRequest) : Work()
        class Image(val request: LocalImageInferenceRequest) : Work()
    }
}
