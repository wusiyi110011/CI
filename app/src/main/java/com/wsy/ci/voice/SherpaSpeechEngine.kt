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

package com.wsy.ci.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.localmodel.download.LocalModelDownloadStatus
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * sherpa-onnx + SenseVoice 的离线语音识别引擎。懒加载：首次 [prepare] 才建 [OfflineRecognizer]，
 * 识别完成后闲置 [IDLE_RELEASE_DELAY_MS] 自动释放，避免和本地 Qwen 常驻抢内存
 * （229MB + 1.39GB 同时挂着风险较高，两者也不会并发推理）。
 *
 * `OfflineRecognizer` 是整段离线解码、没有流式 partial result，所以录音过程中不产出中间文字，
 * 只有 [SpeechState.Recording] 携带的时长/音量供 UI 画波形，松手后才在 [stopAndRecognize] 里
 * 一次性出全文。
 */
class SherpaSpeechEngine(
    context: Context,
    private val downloads: LocalModelDownloadManager,
    private val microphoneArbiter: VoiceMicrophoneArbiter,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val lifecycle = Mutex()
    private val recordingGuard = Any()
    private val recorder = AudioRecorder()

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var recognizer: OfflineRecognizer? = null
    private var recordingJob: Job? = null
    private var pendingAudio: CompletableDeferred<RecordedAudio>? = null
    private var idleReleaseJob: Job? = null

    override suspend fun prepare(): Result<Unit> = lifecycle.withLock {
        idleReleaseJob?.cancel()
        if (recognizer != null) return@withLock Result.success(Unit)
        if (downloads.state.value.status != LocalModelDownloadStatus.COMPLETED) {
            return@withLock Result.failure(IllegalStateException("语音识别模型未下载，请先在设置里下载"))
        }
        _state.value = SpeechState.Loading
        runCatching { withContext(Dispatchers.Default) { buildRecognizer() } }
            .onSuccess {
                recognizer = it
                _state.value = SpeechState.Ready
            }
            .onFailure { _state.value = SpeechState.Failed(it.message ?: "语音识别模型加载失败") }
            .map { }
    }

    override suspend fun startRecording(): Result<Unit> {
        if (recognizer == null) return Result.failure(IllegalStateException("语音识别引擎未就绪"))
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("没有录音权限"))
        }
        val deferred = synchronized(recordingGuard) {
            if (pendingAudio != null) {
                return Result.failure(IllegalStateException("已有一段录音正在进行"))
            }
            CompletableDeferred<RecordedAudio>().also { pendingAudio = it }
        }
        try {
            microphoneArbiter.acquire()
        } catch (error: Throwable) {
            synchronized(recordingGuard) { if (pendingAudio === deferred) pendingAudio = null }
            return Result.failure(error)
        }
        val startedAt = System.currentTimeMillis()
        _state.value = SpeechState.Recording(0L, 0f)
        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                val audio = recorder.record { amplitude ->
                    _state.value = SpeechState.Recording(System.currentTimeMillis() - startedAt, amplitude)
                }
                deferred.complete(audio)
            } catch (error: CancellationException) {
                deferred.cancel(error)
                throw error
            } catch (error: Throwable) {
                deferred.completeExceptionally(error)
                if (pendingAudio === deferred) {
                    pendingAudio = null
                    _state.value = SpeechState.Failed(error.message ?: "无法开始录音")
                }
            } finally {
                microphoneArbiter.release()
            }
        }
        return Result.success(Unit)
    }

    override suspend fun stopAndRecognize(): Result<String> {
        val deferred = synchronized(recordingGuard) { pendingAudio }
            ?: return Result.failure(IllegalStateException("尚未开始录音"))
        recorder.stop()
        val audio = runCatching { withTimeout(RECORDING_STOP_TIMEOUT_MS) { deferred.await() } }.getOrElse { error ->
            recordingJob?.cancelAndJoin()
            synchronized(recordingGuard) { if (pendingAudio === deferred) pendingAudio = null }
            _state.value = SpeechState.Failed(error.message ?: "录音失败")
            return Result.failure(error)
        }
        synchronized(recordingGuard) { if (pendingAudio === deferred) pendingAudio = null }
        if (audio.samples.size < MIN_SAMPLES) {
            _state.value = SpeechState.Ready
            return Result.failure(IllegalStateException("太短了，没听清"))
        }
        _state.value = SpeechState.Recognizing
        return runCatching {
            lifecycle.withLock {
                withContext(Dispatchers.Default) { decode(audio).ifBlank { error("没有识别到有效语音") } }
            }
        }
            .onSuccess {
                _state.value = SpeechState.Ready
                scheduleIdleRelease()
            }
            .onFailure { _state.value = SpeechState.Failed(it.message ?: "识别失败") }
    }

    override fun cancelRecording() {
        recorder.stop()
        recordingJob?.cancel()
        synchronized(recordingGuard) { pendingAudio = null }
        _state.value = SpeechState.Ready
        scheduleIdleRelease()
    }

    override suspend fun release() = lifecycle.withLock {
        idleReleaseJob?.cancel()
        recorder.stop()
        recordingJob?.cancelAndJoin()
        synchronized(recordingGuard) { pendingAudio = null }
        recognizer?.release()
        recognizer = null
        _state.value = SpeechState.Idle
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_DELAY_MS)
            release()
        }
    }

    private fun buildRecognizer(): OfflineRecognizer {
        val dir = downloads.activeDirectory
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = File(dir, "model.int8.onnx").absolutePath,
                    language = "zh",
                    useInverseTextNormalization = true,
                ),
                tokens = File(dir, "tokens.txt").absolutePath,
                numThreads = 4,
                provider = "cpu",
                modelType = "sense_voice",
            ),
        )
        return OfflineRecognizer(assetManager = null, config = config)
    }

    private fun decode(audio: RecordedAudio): String {
        val engine = recognizer ?: throw IllegalStateException("语音识别引擎未就绪")
        val stream = engine.createStream()
        try {
            stream.acceptWaveform(audio.samples, audio.sampleRateHz)
            engine.decode(stream)
            val raw = engine.getResult(stream).text
            return TAG_REGEX.replace(raw, "").trim()
        } finally {
            stream.release()
        }
    }

    companion object {
        /** SenseVoice 输出可能带 `<|zh|><|NEUTRAL|>` 这类语种/情绪标记，识别结果要剥掉。 */
        private val TAG_REGEX = Regex("""<\|[^|]*\|>""")
        private const val MIN_RECORDING_MILLIS = 300L
        private val MIN_SAMPLES = (AudioRecorder.SAMPLE_RATE * MIN_RECORDING_MILLIS / 1000).toInt()
        private const val IDLE_RELEASE_DELAY_MS = 3 * 60 * 1000L
        private const val RECORDING_STOP_TIMEOUT_MS = 3_000L
    }
}
