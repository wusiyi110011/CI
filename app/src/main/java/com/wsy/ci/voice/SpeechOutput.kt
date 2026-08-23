/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 语音结果播报接口；ViewModel 只依赖这一层，JVM 测试可替换为假实现。 */
interface SpeechOutput {
    suspend fun speak(text: String): Result<Unit>
    fun stop()
    fun shutdown()
}

/** 使用系统中文离线音色的 TTS 实现，不额外下载语音合成模型。 */
class AndroidSpeechOutput(context: Context) : SpeechOutput {
    private val appContext = context.applicationContext
    private val initialized = CompletableDeferred<Result<TextToSpeech>>()
    private val utterances = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(appContext) { status ->
            val current = engine
            if (status != TextToSpeech.SUCCESS || current == null) {
                initialized.complete(Result.failure(IllegalStateException("语音播报初始化失败")))
                return@TextToSpeech
            }
            val language = current.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                initialized.complete(Result.failure(IllegalStateException("系统没有可用的中文语音")))
                return@TextToSpeech
            }
            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { utterances.remove(it)?.complete(Result.success(Unit)) }
                }

                @Deprecated("Android 21 以后使用带 errorCode 的重载")
                override fun onError(utteranceId: String?) {
                    completeError(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    completeError(utteranceId, "语音播报失败（$errorCode）")
                }
            })
            initialized.complete(Result.success(current))
        }
    }

    override suspend fun speak(text: String): Result<Unit> {
        val content = text.trim()
        if (content.isEmpty()) return Result.success(Unit)
        val tts = initialized.await().getOrElse { return Result.failure(it) }
        val id = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Result<Unit>>()
        utterances[id] = completion
        val queued = withContext(Dispatchers.Main.immediate) {
            tts.speak(content, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        if (queued == TextToSpeech.ERROR) {
            utterances.remove(id)
            return Result.failure(IllegalStateException("语音播报启动失败"))
        }
        return completion.await()
    }

    override fun stop() {
        engine?.stop()
        val error = Result.failure<Unit>(IllegalStateException("语音播报已停止"))
        utterances.values.forEach { it.complete(error) }
        utterances.clear()
    }

    override fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }

    private fun completeError(utteranceId: String?, message: String = "语音播报失败") {
        utteranceId?.let {
            utterances.remove(it)?.complete(Result.failure(IllegalStateException(message)))
        }
    }
}
