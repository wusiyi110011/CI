/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import android.media.AudioManager
import android.media.ToneGenerator
import com.wsy.ci.core.settings.AppSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceWakeState {
    data object Off : VoiceWakeState
    data object Loading : VoiceWakeState
    data object Listening : VoiceWakeState
    data object WakeDetected : VoiceWakeState
    data class Capturing(val elapsedMillis: Long, val amplitude: Float) : VoiceWakeState
    data object Recognizing : VoiceWakeState
    data class Failed(val message: String) : VoiceWakeState
}

/**
 * 唤醒词服务的单一运行时：KWS 与命令录音串行切换，任一时刻只有一个 AudioRecord 所有者。
 */
class VoiceWakeRuntime(
    private val settings: AppSettings,
    private val keywordSpotter: SherpaKeywordSpotter,
    private val speechEngine: SpeechEngine,
    private val commandBus: VoiceCommandBus,
) {
    private val active = AtomicBoolean(false)
    private val capturing = AtomicBoolean(false)
    private val _state = MutableStateFlow<VoiceWakeState>(VoiceWakeState.Off)
    val state: StateFlow<VoiceWakeState> = _state.asStateFlow()

    suspend fun run(): Result<Unit> {
        if (!active.compareAndSet(false, true)) return Result.success(Unit)
        return try {
            while (active.get() && settings.wakeWordEnabled.value) {
                _state.value = VoiceWakeState.Loading
                var detected = false
                val result = keywordSpotter.listen(
                    keyword = settings.wakePhrase.value,
                    onListening = {
                        if (active.get()) _state.value = VoiceWakeState.Listening
                    },
                    onDetected = {
                        detected = true
                        keywordSpotter.stop()
                    },
                )
                if (result.isFailure && active.get()) {
                    val message = result.exceptionOrNull()?.message ?: "唤醒词监听失败"
                    _state.value = VoiceWakeState.Failed(message)
                    delay(FAILURE_DISPLAY_MILLIS)
                    return Result.failure(result.exceptionOrNull() ?: IllegalStateException(message))
                }
                if (!active.get() || !settings.wakeWordEnabled.value) break
                if (!detected) continue
                _state.value = VoiceWakeState.WakeDetected
                playReadyTone()
                capturing.set(true)
                val captureResult = try {
                    captureCommand()
                } finally {
                    capturing.set(false)
                }
                captureResult.onSuccess(commandBus::emit).onFailure { error ->
                    _state.value = VoiceWakeState.Failed(error.message ?: "唤醒后录音失败")
                    delay(FAILURE_DISPLAY_MILLIS)
                }
                // 单次识别失败不关闭常驻监听，短暂停顿后重新进入 KWS。
                if (active.get()) delay(RESTART_DELAY_MS)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.value = VoiceWakeState.Failed(error.message ?: "唤醒词监听失败")
            delay(FAILURE_DISPLAY_MILLIS)
            Result.failure(error)
        } finally {
            active.set(false)
            keywordSpotter.stop()
            if (capturing.getAndSet(false)) speechEngine.cancelRecording()
            if (_state.value !is VoiceWakeState.Failed) _state.value = VoiceWakeState.Off
        }
    }

    fun stop() {
        active.set(false)
        keywordSpotter.stop()
        if (capturing.getAndSet(false)) speechEngine.cancelRecording()
        _state.value = VoiceWakeState.Off
    }

    private suspend fun captureCommand(): Result<String> {
        speechEngine.prepare().getOrElse { return Result.failure(it) }
        speechEngine.startRecording().getOrElse { return Result.failure(it) }
        val startedAt = System.currentTimeMillis()
        var voiceSeen = false
        var lastVoiceAt = startedAt
        while (active.get()) {
            val now = System.currentTimeMillis()
            val recording = speechEngine.state.value as? SpeechState.Recording
            if (recording != null) {
                _state.value = VoiceWakeState.Capturing(recording.elapsedMillis, recording.amplitude)
                if (recording.amplitude >= VOICE_AMPLITUDE_THRESHOLD) {
                    voiceSeen = true
                    lastVoiceAt = now
                }
            }
            val elapsed = now - startedAt
            if (voiceSeen && now - lastVoiceAt >= END_SILENCE_MILLIS) break
            if (!voiceSeen && elapsed >= WAIT_FOR_SPEECH_MILLIS) break
            if (elapsed >= MAX_COMMAND_MILLIS) break
            delay(POLL_MILLIS)
        }
        _state.value = VoiceWakeState.Recognizing
        return speechEngine.stopAndRecognize()
    }

    private suspend fun playReadyTone() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MILLIS.toInt())
            delay(TONE_DURATION_MILLIS + TONE_GAP_MILLIS)
        } finally {
            tone.release()
        }
    }

    private companion object {
        const val VOICE_AMPLITUDE_THRESHOLD = 0.012f
        const val END_SILENCE_MILLIS = 900L
        const val WAIT_FOR_SPEECH_MILLIS = 4_000L
        const val MAX_COMMAND_MILLIS = 12_000L
        const val POLL_MILLIS = 50L
        const val RESTART_DELAY_MS = 250L
        const val FAILURE_DISPLAY_MILLIS = 1_500L
        const val TONE_VOLUME = 70
        const val TONE_DURATION_MILLIS = 120L
        const val TONE_GAP_MILLIS = 100L
    }
}
