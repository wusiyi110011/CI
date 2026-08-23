/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 唤醒监听服务与界面之间的进程内命令总线。 */
class VoiceCommandBus {
    private val _commands = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    fun emit(text: String) {
        text.trim().takeIf { it.isNotEmpty() }?.let(_commands::tryEmit)
    }

    /** 界面消费后清掉 replay，防止旋转或返回应用时重复执行。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumed() {
        _commands.resetReplayCache()
    }
}
