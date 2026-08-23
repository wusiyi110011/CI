/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 进程内唯一的麦克风仲裁器，确保 KWS 与整段 ASR 绝不同时持有 AudioRecord。 */
class VoiceMicrophoneArbiter {
    private val mutex = Mutex()

    suspend fun <T> withMicrophone(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun acquire() = mutex.lock()

    fun release() {
        if (mutex.isLocked) mutex.unlock()
    }
}
