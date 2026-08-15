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

import kotlinx.coroutines.flow.StateFlow

sealed interface SpeechState {
    data object Idle : SpeechState
    data object Loading : SpeechState
    data object Ready : SpeechState
    data class Recording(val elapsedMillis: Long, val amplitude: Float) : SpeechState
    data object Recognizing : SpeechState
    data class Failed(val message: String) : SpeechState
}

/** 离线语音识别引擎的抽象面：录音、识别、生命周期。 */
interface SpeechEngine {
    val state: StateFlow<SpeechState>
    suspend fun prepare(): Result<Unit>
    suspend fun startRecording(): Result<Unit>

    /** 停止录音并整段识别；返回识别文本。上滑取消请改调 [cancelRecording]。 */
    suspend fun stopAndRecognize(): Result<String>
    fun cancelRecording()
    suspend fun release()
}
