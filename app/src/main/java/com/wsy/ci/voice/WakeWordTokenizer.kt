/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import android.icu.text.Transliterator

/** 把中文唤醒短语转成 WenetSpeech KWS 模型使用的“声母 + 带调韵母”token。 */
class WakeWordTokenizer {
    private val transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names; NFC; Lower()")
    }

    fun encode(keyword: String): String {
        val original = keyword.trim().replace(WHITESPACE, "_")
        require(original.length in MIN_LENGTH..MAX_LENGTH) { "唤醒词需要 2～12 个字符" }
        val tokens = transliterator.transliterate(original.replace('_', ' '))
            .split(SYLLABLE_SEPARATOR)
            .filter { it.isNotBlank() }
            .flatMap(::splitSyllable)
        require(tokens.isNotEmpty()) { "唤醒词无法转换成拼音" }
        return "${tokens.joinToString(" ")} :$BOOST_SCORE #$TRIGGER_THRESHOLD @$original"
    }

    private fun splitSyllable(raw: String): List<String> {
        val syllable = raw.filter { it.isLetter() }.lowercase()
        if (syllable.isEmpty()) return emptyList()
        val initial = INITIALS.firstOrNull(syllable::startsWith) ?: return listOf(syllable)
        val finalPart = syllable.removePrefix(initial)
        return if (finalPart.isEmpty()) listOf(initial) else listOf(initial, finalPart)
    }

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 12
        private const val BOOST_SCORE = 1.5f
        private const val TRIGGER_THRESHOLD = 0.30f
        private val INITIALS = listOf(
            "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
            "j", "q", "x", "r", "z", "c", "s", "y", "w",
        )
        private val WHITESPACE = Regex("""\s+""")
        private val SYLLABLE_SEPARATOR = Regex("""[\s'’·_-]+""")
    }
}
