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

import com.wsy.ci.localmodel.runtime.LocalInferenceRequest
import com.wsy.ci.localmodel.runtime.LocalImageInferenceRequest
import com.wsy.ci.localmodel.runtime.LocalModelBridge
import com.wsy.ci.localmodel.runtime.LocalModelController
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRoutingTest {
    @Test
    fun `旧端点值和新关键字都能解析`() {
        assertTrue(LlmRouteParser.parse(null) is LlmRoute.Default)
        assertTrue(LlmRouteParser.parse("default") is LlmRoute.Default)
        assertTrue(LlmRouteParser.parse("local") is LlmRoute.Local)
        assertTrue(LlmRouteParser.parse("off") is LlmRoute.Off)
        assertTrue(LlmRouteParser.parse("deepseek-pro") is LlmRoute.Api)
        assertTrue(LlmRouteParser.parse("api:deepseek-flash") is LlmRoute.Api)
        assertTrue(LlmRouteParser.parse("nonsense") is LlmRoute.Invalid)
    }

    @Test
    fun `显式端点缺少自己的Key时不会静默改投其他供应商`() {
        val resolved = resolveApiEndpoint(
            task = LlmTaskType.TITLE_GEN,
            selection = LlmRoute.Api(LlmEndpoints.MIMO),
            hasKey = { it == LlmEndpoints.KEY_DEEPSEEK },
        )

        assertNull(resolved)
    }

    @Test
    fun `默认文本路由缺Key时仍可回退到已配置端点`() {
        val resolved = resolveApiEndpoint(
            task = LlmTaskType.TITLE_GEN,
            selection = LlmRoute.Default,
            hasKey = { it == LlmEndpoints.KEY_DEEPSEEK },
        )

        assertEquals(LlmEndpoints.DEEPSEEK_FLASH, resolved)
    }

    @Test
    fun `JSON字符串里的括号不会提前截断`() {
        val json = """["数组 arr[i]", "右花括号 }", "引号 \" 内仍有 ]"]"""

        assertEquals(json, extractJson("模型说明：$json\n以上"))
    }

    @Test
    fun `任务本地预算符合约定`() {
        assertEquals(LocalGenerationOptions(true, 2048), LlmTaskType.ROUTE_GEN.localGenerationOptions)
        // 复盘关思考链：本地小模型的思考链吃预算且容易把正文带成英文
        assertEquals(LocalGenerationOptions(false, 1536), LlmTaskType.REVIEW_ANALYSIS.localGenerationOptions)
        assertEquals(LocalGenerationOptions(false, 512), LlmTaskType.NL_PARSE.localGenerationOptions)
        assertEquals(LocalGenerationOptions(false, 384), LlmTaskType.ITEM_PRICING.localGenerationOptions)
        assertEquals(LocalGenerationOptions(false, 256), LlmTaskType.TITLE_GEN.localGenerationOptions)
        assertEquals(LocalGenerationOptions(false, 512), LlmTaskType.IMAGE_UNDERSTAND.localGenerationOptions)
    }

    @Test
    fun `RGBA 图片请求校验尺寸`() {
        val request = LlmImageRequest(ByteArray(2 * 3 * 4), 2, 3, "sys", "usr")
        assertEquals(24, request.rgba.size)
    }

    @Test
    fun `本地控制器按提交顺序串行执行`() = runBlocking {
        val bridge = RecordingBridge()
        val controller = LocalModelController(bridge, "model")
        assertTrue(controller.start() is com.wsy.ci.localmodel.runtime.LocalModelResult.Success)
        supervisorScope {
            val first = async { controller.generate("", "a", LocalGenerationOptions(false, 8)) }
            val second = async { controller.generate("", "b", LocalGenerationOptions(false, 8)) }
            first.await()
            second.await()
        }
        assertEquals(listOf("回复 OK", "a", "b"), bridge.calls)
        controller.stop()
        assertEquals(1, bridge.unloads)
    }

    @Test
    fun `启动后冒烟请求使用本地桥`() = runBlocking {
        val bridge = RecordingBridge()
        val controller = LocalModelController(bridge, "model")
        controller.start()
        val result = controller.smokeTest()
        assertTrue(result is com.wsy.ci.localmodel.runtime.LocalModelResult.Success)
        assertEquals(listOf("回复 OK", "回复 OK"), bridge.calls)
        controller.close()
    }

    @Test
    fun `Local 路由失败不会调用 API`() = runBlocking {
        val api = CountingGateway(LlmResult.Success("api"))
        val local = CountingGateway(
            LlmResult.Failure(
                "本地失败",
                LlmError(LlmErrorCode.LOCAL_INFERENCE_FAILED, "本地失败", LlmErrorSource.LOCAL),
            )
        )
        val router = LlmRouter(
            settings = object : LlmRouteProvider {
                override fun routeSelection(task: LlmTaskType): LlmRoute = LlmRoute.Local
            },
            api = api,
            local = local,
        )
        val result = router.complete(LlmTaskType.NL_PARSE, "", "")
        assertTrue(result is LlmResult.Failure)
        assertEquals(0, api.calls)
        assertEquals(1, local.calls)
        assertEquals(LlmErrorCode.LOCAL_INFERENCE_FAILED, (result as LlmResult.Failure).error.code)
    }

    @Test
    fun `stop 会取消当前和排队请求且可再次启动`() = runBlocking {
        val bridge = RecordingBridge(block = true)
        val controller = LocalModelController(bridge, "model")
        controller.start()
        val first = async { controller.generate("", "a", LocalGenerationOptions(false, 8)) }
        bridge.entered.await(2, TimeUnit.SECONDS)
        val second = async { controller.generate("", "b", LocalGenerationOptions(false, 8)) }
        controller.stop()
        assertTrue(first.await() is com.wsy.ci.localmodel.runtime.LocalModelResult.Failure)
        assertTrue(second.await() is com.wsy.ci.localmodel.runtime.LocalModelResult.Failure)
        assertEquals(1, bridge.unloads)
        assertTrue(controller.start() is com.wsy.ci.localmodel.runtime.LocalModelResult.Success)
        controller.close()
    }

    private class RecordingBridge(private val block: Boolean = false) : LocalModelBridge {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val entered = CountDownLatch(1)
        var unloads = 0
        @Volatile var cancelled = false

        override fun load(modelPath: String) = true
        override fun unload() { unloads++ }
        override fun cancel() { cancelled = true }
        override fun generate(request: LocalInferenceRequest): String {
            calls += request.userPrompt
            entered.countDown()
            if (block && request.userPrompt != "回复 OK") {
                while (!cancelled) Thread.sleep(2)
            }
            return request.userPrompt
        }
        override fun generateImage(request: LocalImageInferenceRequest): String = "image"
    }

    private class CountingGateway(private val response: LlmResult) : LlmGateway {
        var calls = 0
        override suspend fun isAvailable(task: LlmTaskType): Boolean = true
        override suspend fun complete(task: LlmTaskType, systemPrompt: String, userPrompt: String): LlmResult {
            calls++
            return response
        }
    }
}
