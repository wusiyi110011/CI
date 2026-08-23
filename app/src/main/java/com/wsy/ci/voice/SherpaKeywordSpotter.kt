/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.wsy.ci.localmodel.download.KwsModelManifest
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.localmodel.download.LocalModelDownloadStatus
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 使用 sherpa-onnx 开放词汇 KWS 模型持续监听用户自定义的中文唤醒短语。 */
class SherpaKeywordSpotter(
    context: Context,
    private val downloads: LocalModelDownloadManager,
    private val microphoneArbiter: VoiceMicrophoneArbiter,
    private val tokenizer: WakeWordTokenizer = WakeWordTokenizer(),
) {
    private val appContext = context.applicationContext
    private val keywordFile = File(context.cacheDir, "voice-wake/keywords.txt")
    private val running = AtomicBoolean(false)
    @Volatile private var currentRecorder: AudioRecord? = null

    @SuppressLint("MissingPermission")
    suspend fun listen(
        keyword: String,
        onListening: () -> Unit,
        onDetected: () -> Unit,
    ): Result<Unit> =
        microphoneArbiter.withMicrophone { withContext(Dispatchers.IO) {
        runCatching {
            check(downloads.state.value.status == LocalModelDownloadStatus.COMPLETED) {
                "唤醒词模型未下载，请先在设置里下载"
            }
            check(running.compareAndSet(false, true)) { "唤醒词监听已经在运行" }
            val encodedKeyword = tokenizer.encode(keyword)
            keywordFile.parentFile?.mkdirs()
            keywordFile.writeText(encodedKeyword)
            var engine: KeywordSpotter? = null
            var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var recorder: AudioRecord? = null
            val shorts = ShortArray(CHUNK_SAMPLES)
            try {
                engine = buildSpotter(keywordFile)
                stream = engine.createStream()
                recorder = buildRecorder()
                currentRecorder = recorder
                recorder.startRecording()
                onListening()
                while (running.get()) {
                    val count = recorder.read(shorts, 0, shorts.size)
                    if (count == AudioRecord.ERROR_DEAD_OBJECT) error("麦克风连接已中断")
                    if (count < 0) error("麦克风读取失败（$count）")
                    if (count == 0) continue
                    val samples = FloatArray(count) { index -> shorts[index] / 32768f }
                    stream.acceptWaveform(samples, AudioRecorder.SAMPLE_RATE)
                    while (engine.isReady(stream)) engine.decode(stream)
                    if (engine.getResult(stream).keyword.isNotBlank()) {
                        onDetected()
                        engine.reset(stream)
                    }
                }
            } finally {
                running.set(false)
                currentRecorder = null
                recorder?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) runCatching { it.stop() }
                    it.release()
                }
                stream?.release()
                engine?.release()
            }
        }
    } }

    fun stop() {
        running.set(false)
        currentRecorder?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) runCatching { recorder.stop() }
        }
    }

    private fun buildSpotter(keywordFile: File): KeywordSpotter {
        val dir = downloads.activeDirectory
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = AudioRecorder.SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(dir, KwsModelManifest.ENCODER).absolutePath,
                    decoder = File(dir, KwsModelManifest.DECODER).absolutePath,
                    joiner = File(dir, KwsModelManifest.JOINER).absolutePath,
                ),
                tokens = File(dir, KwsModelManifest.TOKENS).absolutePath,
                numThreads = 1,
                provider = "cpu",
                modelingUnit = "ppinyin",
            ),
            maxActivePaths = 4,
            keywordsFile = keywordFile.absolutePath,
            keywordsScore = 1.5f,
            keywordsThreshold = 0.30f,
            numTrailingBlanks = 1,
        )
        return KeywordSpotter(assetManager = null, config = config)
    }

    @SuppressLint("MissingPermission")
    private fun buildRecorder(): AudioRecord {
        check(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) { "没有录音权限" }
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioRecorder.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(CHUNK_SAMPLES * Short.SIZE_BYTES)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioRecorder.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("唤醒词麦克风初始化失败")
        }
        return recorder
    }

    private companion object {
        /** 100 ms 音频一块；KWS 模型内部按 320 ms chunk 解码。 */
        const val CHUNK_SAMPLES = AudioRecorder.SAMPLE_RATE / 10
    }
}
