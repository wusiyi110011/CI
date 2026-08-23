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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** 一次录音的结果：归一化到 [-1, 1] 的采样点（sherpa-onnx 要求浮点输入），以及采样率。 */
data class RecordedAudio(val samples: FloatArray, val sampleRateHz: Int)

/**
 * 16kHz 单声道 PCM 录音器。按块读取、累积样本，每块顺带算 RMS 音量推给 [onAmplitude] 供
 * 录音浮层画波形动画。预分配 60 秒上限的缓冲区，防止误按长按录爆内存，用完即自动停止。
 */
class AudioRecorder {
    @Volatile private var recording = false
    private val inUse = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    suspend fun record(onAmplitude: (Float) -> Unit): RecordedAudio {
        check(inUse.compareAndSet(false, true)) { "录音设备正在使用中" }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 10 * BYTES_PER_SAMPLE)
        try {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                error("录音设备初始化失败")
            }

            val maxSamples = SAMPLE_RATE * MAX_DURATION_SECONDS
            val buffer = FloatArray(maxSamples)
            val chunk = ShortArray(minBuffer / BYTES_PER_SAMPLE)
            var written = 0
            recording = true
            try {
                audioRecord.startRecording()
                while (recording && written < maxSamples) {
                    val n = audioRecord.read(chunk, 0, chunk.size)
                    if (n == AudioRecord.ERROR_DEAD_OBJECT) error("录音设备连接已中断")
                    if (n == AudioRecord.ERROR_INVALID_OPERATION) error("录音设备状态异常")
                    if (n == AudioRecord.ERROR_BAD_VALUE) error("录音参数无效")
                    if (n < 0) error("录音读取失败（$n）")
                    if (n == 0) continue
                    val count = n.coerceAtMost(maxSamples - written)
                    var sumSquares = 0.0
                    for (i in 0 until count) {
                        val f = chunk[i] / 32768f
                        buffer[written + i] = f
                        sumSquares += (f * f).toDouble()
                    }
                    written += count
                    onAmplitude(sqrt(sumSquares / count).toFloat())
                }
            } finally {
                recording = false
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    runCatching { audioRecord.stop() }
                }
                audioRecord.release()
            }
            return RecordedAudio(buffer.copyOf(written), SAMPLE_RATE)
        } finally {
            recording = false
            inUse.set(false)
        }
    }

    /** 让 [record] 的循环在处理完当前块后正常返回，而不是从外部强行打断。 */
    fun stop() {
        recording = false
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_DURATION_SECONDS = 60
        private const val BYTES_PER_SAMPLE = 2
    }
}
