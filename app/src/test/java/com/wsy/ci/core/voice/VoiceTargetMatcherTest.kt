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

package com.wsy.ci.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTargetMatcherTest {

    /** 测试用拼音表：只覆盖用例里出现的汉字，非汉字按约定返回小写原字符。 */
    private val pinyinTable = mapOf(
        '所' to "suo", '有' to "you", '权' to "quan",
        '锁' to "suo", '全' to "quan",
        '英' to "ying", '语' to "yu", '听' to "ting", '力' to "li",
        '机' to "ji", '器' to "qi", '学' to "xue", '习' to "xi",
    )
    private val pinyinOf: PinyinOf = { ch -> pinyinTable[ch] ?: ch.lowercaseChar().toString() }

    private fun target(id: Long, name: String) = VoiceTarget(id, name, VoiceTargetKind.TASK)

    @Test
    fun `文本中含候选名原文直接命中`() {
        val candidates = listOf(target(1, "Rust 所有权"), target(2, "英语听力"))
        val result = VoiceTargetMatcher.match("开始 Rust 所有权那个任务", candidates, pinyinOf)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `同音字识别错误靠拼音兜底命中`() {
        // 语音把「所有权」听成同音的「锁有全」，汉字完全不同但拼音一致
        val candidates = listOf(target(1, "所有权"), target(2, "英语听力"))
        val result = VoiceTargetMatcher.match("开始锁有全这个任务", candidates, pinyinOf)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `多候选时返回得分最高的一个`() {
        val candidates = listOf(target(1, "机器学习"), target(2, "英语听力"))
        val result = VoiceTargetMatcher.match("开始学习英语听力课程", candidates, pinyinOf)
        assertEquals(2L, result?.id)
    }

    @Test
    fun `完全无关文本低于阈值返回null`() {
        val candidates = listOf(target(1, "所有权"))
        val result = VoiceTargetMatcher.match("随便说点什么完全不相关的话", candidates, pinyinOf)
        assertNull(result)
    }

    @Test
    fun `候选列表为空返回null`() {
        val result = VoiceTargetMatcher.match("开始机器学习", emptyList(), pinyinOf)
        assertNull(result)
    }

    @Test
    fun `自定义阈值可以放宽或收紧命中条件`() {
        val candidates = listOf(target(1, "机器学习"))
        // 只沾边提到「学习」，与候选名相似度中等，默认阈值下不命中
        assertNull(VoiceTargetMatcher.match("随便学习一下别的", candidates, pinyinOf))
        val loose = VoiceTargetMatcher.match("随便学习一下别的", candidates, pinyinOf, threshold = 0.3)
        assertEquals(1L, loose?.id)
    }
}
