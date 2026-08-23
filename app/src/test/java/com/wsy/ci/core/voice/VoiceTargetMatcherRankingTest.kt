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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** 候选排名与近似同分消歧的纯逻辑测试。 */
class VoiceTargetMatcherRankingTest {
    private val pinyin: PinyinOf = { it.lowercaseChar().toString() }

    @Test
    fun `排名结果按分数从高到低`() {
        val ranked = VoiceTargetMatcher.rank(
            "开始英语听力",
            listOf(
                VoiceTarget(1, "机器学习", VoiceTargetKind.TASK),
                VoiceTarget(2, "英语听力", VoiceTargetKind.TASK),
            ),
            pinyin,
        )
        assertEquals(2L, ranked.first().target.id)
        assertTrue(ranked.first().score >= ranked.last().score)
    }

    @Test
    fun `同名候选被判定为歧义而不是静默取第一个`() {
        val candidates = listOf(
            VoiceTarget(1, "背单词", VoiceTargetKind.TASK),
            VoiceTarget(2, "背单词", VoiceTargetKind.TASK),
        )
        val ranked = VoiceTargetMatcher.rank("背单词", candidates, pinyin)
        assertTrue(VoiceTargetMatcher.isAmbiguous(ranked))
        assertNull(VoiceTargetMatcher.match("背单词", candidates, pinyin))
    }
}
