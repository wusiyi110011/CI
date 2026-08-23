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

/**
 * 在一段识别文本里模糊定位候选任务/主线/领域名，解决语音识别对专有名词不准的问题。
 * 对每个候选名，在文本上按候选名长度 ±2 滑窗取子串，分别算汉字级和拼音级的归一化编辑距离，
 * 取两者较优值作为该候选得分；全局取最高分，低于 [threshold] 视为未命中。
 */
object VoiceTargetMatcher {

    /** 一个候选在文本中的归一化匹配分数，分数越高越可信。 */
    data class RankedTarget(val target: VoiceTarget, val score: Double)

    /** 排名结果的最低分；低于此值的候选不会进入消歧列表。 */
    private const val DEFAULT_THRESHOLD = 0.62

    /** 最高分与次高分差距不超过此值时视为近似同分，必须交给用户确认。 */
    const val DEFAULT_TIE_MARGIN = 0.05

    /**
     * 对所有候选按分数从高到低排名。返回值是纯数据，不执行任何副作用，
     * 供确认卡片展示候选和测试消歧边界。
     */
    fun rank(
        text: String,
        candidates: List<VoiceTarget>,
        pinyinOf: PinyinOf,
        threshold: Double = 0.0,
    ): List<RankedTarget> = candidates
        .asSequence()
        .map { RankedTarget(it, scoreCandidate(text, it.name, pinyinOf)) }
        .filter { it.score >= threshold }
        .sortedWith(compareByDescending<RankedTarget> { it.score }.thenBy { it.target.id })
        .toList()

    /** 与 [rank] 等价的描述性 API，保留给需要显式表达「候选排名」的调用方。 */
    fun rankCandidates(
        text: String,
        candidates: List<VoiceTarget>,
        pinyinOf: PinyinOf,
        threshold: Double = 0.0,
    ): List<RankedTarget> = rank(text, candidates, pinyinOf, threshold)

    /** [rank] 的兼容别名，强调返回「带分数的匹配结果」。 */
    fun matchWithScores(
        text: String,
        candidates: List<VoiceTarget>,
        pinyinOf: PinyinOf,
        threshold: Double = 0.0,
    ): List<RankedTarget> = rank(text, candidates, pinyinOf, threshold)

    /** 判断前两名是否近似同分；同名候选会得到 0 分差，必然返回 true。 */
    fun isNearTie(
        ranked: List<RankedTarget>,
        tieMargin: Double = DEFAULT_TIE_MARGIN,
    ): Boolean = ranked.size >= 2 &&
        ranked[0].score - ranked[1].score <= tieMargin.coerceAtLeast(0.0)

    /** 便于调用方直接对两个分数做近似同分判定。 */
    fun isNearTie(topScore: Double, secondScore: Double, tieMargin: Double = DEFAULT_TIE_MARGIN): Boolean =
        topScore - secondScore <= tieMargin.coerceAtLeast(0.0)

    /** [isNearTie] 的语义别名，供确认流程直接表达「候选是否有歧义」。 */
    fun isAmbiguous(ranked: List<RankedTarget>, tieMargin: Double = DEFAULT_TIE_MARGIN): Boolean =
        isNearTie(ranked, tieMargin)

    fun match(
        text: String,
        candidates: List<VoiceTarget>,
        pinyinOf: PinyinOf,
        threshold: Double = DEFAULT_THRESHOLD,
        tieMargin: Double = DEFAULT_TIE_MARGIN,
    ): VoiceTarget? {
        val ranked = rank(text, candidates, pinyinOf, threshold)
        if (ranked.isEmpty() || isNearTie(ranked, tieMargin)) return null
        return ranked.first().target
    }

    private fun scoreCandidate(text: String, name: String, pinyinOf: PinyinOf): Double {
        if (name.isEmpty() || text.isEmpty()) return 0.0
        val namePinyin = name.map(pinyinOf).joinToString("")
        val minWindow = (name.length - 2).coerceAtLeast(1)
        val maxWindow = (name.length + 2).coerceAtMost(text.length)
        if (minWindow > maxWindow) return 0.0
        var best = 0.0
        for (window in minWindow..maxWindow) {
            for (start in 0..(text.length - window)) {
                val sub = text.substring(start, start + window)
                val hanScore = normalizedSimilarity(sub, name)
                val subPinyin = sub.map(pinyinOf).joinToString("")
                val pinyinScore = normalizedSimilarity(subPinyin, namePinyin)
                best = maxOf(best, hanScore, pinyinScore)
            }
        }
        return best
    }

    private fun normalizedSimilarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }
}
