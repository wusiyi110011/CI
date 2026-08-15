package com.wsy.ci.core.voice

/**
 * 在一段识别文本里模糊定位候选任务/主线/领域名，解决语音识别对专有名词不准的问题。
 * 对每个候选名，在文本上按候选名长度 ±2 滑窗取子串，分别算汉字级和拼音级的归一化编辑距离，
 * 取两者较优值作为该候选得分；全局取最高分，低于 [threshold] 视为未命中。
 */
object VoiceTargetMatcher {

    fun match(
        text: String,
        candidates: List<VoiceTarget>,
        pinyinOf: PinyinOf,
        threshold: Double = 0.62,
    ): VoiceTarget? {
        var best: VoiceTarget? = null
        var bestScore = 0.0
        for (candidate in candidates) {
            val score = scoreCandidate(text, candidate.name, pinyinOf)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best.takeIf { bestScore >= threshold }
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
