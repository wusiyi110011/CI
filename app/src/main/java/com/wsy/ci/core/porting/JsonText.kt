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

package com.wsy.ci.core.porting

private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```")

/**
 * 从一段可能夹带闲话的文本里抠出 JSON 对象。
 *
 * 粘贴进来的往往是聊天 AI 的原始回复：带 markdown 围栏、前面还有一句「好的，这是……」。
 * 学习计划和商城货架两处导入共用同一套宽容策略。
 */
internal fun extractJsonObject(raw: String): String {
    val fenced = FENCE.find(raw)?.groupValues?.get(1)
    val candidate = (fenced ?: raw).trim()
    val start = candidate.indexOf('{')
    val end = candidate.lastIndexOf('}')
    return if (start in 0 until end) candidate.substring(start, end + 1) else candidate
}
