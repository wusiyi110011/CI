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

package com.wsy.ci.llm

import com.wsy.ci.localmodel.runtime.LocalModelController
import com.wsy.ci.localmodel.runtime.LocalModelResult

/**
 * 本地 MNN 网关：把业务任务映射为固定的推理预算，绝不在本地失败时转发到 API。
 * 模型生命周期由 [LocalModelController] 的 start/stop 管理，主线程可直接调用对应方法。
 */
class MnnLlmGateway(
    private val controller: LocalModelController,
) : LlmGateway {

    val state = controller.state

    override suspend fun isAvailable(task: LlmTaskType): Boolean =
        controller.state.value.let { it == com.wsy.ci.localmodel.runtime.LocalModelState.Ready ||
            it == com.wsy.ci.localmodel.runtime.LocalModelState.Busy }

    override suspend fun complete(
        task: LlmTaskType,
        systemPrompt: String,
        userPrompt: String,
    ): LlmResult = controller.generate(
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        options = task.localGenerationOptions,
    ).toLlmResult()

    override suspend fun completeImage(task: LlmTaskType, request: LlmImageRequest): LlmResult =
        controller.generateImage(request).toLlmResult()

    suspend fun start(): LocalModelResult = controller.start()

    suspend fun smokeTest(): LocalModelResult = controller.smokeTest()

    suspend fun visualSmokeTest(request: LlmImageRequest): LocalModelResult = controller.generateImage(request)

    suspend fun stop() = controller.stop()

    suspend fun cancelCurrent() = controller.cancelCurrent()

    suspend fun close() = controller.close()

    private fun LocalModelResult.toLlmResult(): LlmResult = when (this) {
        is LocalModelResult.Success -> LlmResult.Success(content)
        is LocalModelResult.Failure -> LlmResult.Failure(error.message, error)
    }
}
