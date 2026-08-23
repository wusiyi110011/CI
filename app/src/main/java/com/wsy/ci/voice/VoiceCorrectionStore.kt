/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 记录用户在确认页做过的文字修正，相同识别结果下次自动套用。 */
class VoiceCorrectionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun apply(text: String): String = corrections()[normalize(text)] ?: text

    fun remember(recognized: String, corrected: String) {
        val from = normalize(recognized)
        val to = corrected.trim()
        if (from.isEmpty() || to.isEmpty() || from == normalize(to)) return
        val next = corrections().toMutableMap().apply {
            put(from, to)
            while (size > MAX_RECORDS) remove(keys.first())
        }
        prefs.edit().putString(KEY_CORRECTIONS, json.encodeToString(next)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CORRECTIONS).apply()
    }

    fun size(): Int = corrections().size

    private fun corrections(): LinkedHashMap<String, String> {
        val raw = prefs.getString(KEY_CORRECTIONS, null) ?: return linkedMapOf()
        return runCatching { json.decodeFromString<LinkedHashMap<String, String>>(raw) }.getOrDefault(linkedMapOf())
    }

    private fun normalize(text: String): String = text.trim().replace(WHITESPACE, "")

    private companion object {
        const val PREFS_NAME = "voice_corrections"
        const val KEY_CORRECTIONS = "corrections"
        const val MAX_RECORDS = 100
        val WHITESPACE = Regex("""\s+""")
    }
}
