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

package com.wsy.ci.core.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsTest {

    @Test
    fun `语音偏好默认值符合安全默认`() {
        assertEquals(VoiceAutoExecuteLevel.OFF, VoiceAutoExecuteLevel.fromStoredName(null))
        assertEquals(AppSettings.DEFAULT_WAKE_PHRASE, "小复利")
        assertFalse(AppSettings.DEFAULT_WAKE_WORD_ENABLED)
        assertFalse(AppSettings.DEFAULT_TTS_ENABLED)
        assertFalse(AppSettings.DEFAULT_WAKE_PROMPT_SHOWN)
        assertTrue(AppSettings.DEFAULT_CORRECTION_LEARNING_ENABLED)
    }

    @Test
    fun `非法自动执行等级回退为始终确认`() {
        assertEquals(VoiceAutoExecuteLevel.OFF, VoiceAutoExecuteLevel.fromStoredName("UNKNOWN"))
        assertEquals(VoiceAutoExecuteLevel.SAFE, VoiceAutoExecuteLevel.fromStoredName("SAFE"))
    }

    @Test
    fun `唤醒词会按trim后长度校验`() {
        assertNull(AppSettings.validateWakePhrase("  小复利  "))
        assertEquals("唤醒词不能为空。", AppSettings.validateWakePhrase("   "))
        assertTrue(AppSettings.validateWakePhrase("一")!!.contains("2～12"))
        assertTrue(AppSettings.validateWakePhrase("一二三四五六七八九十一二三")!!.contains("2～12"))
    }

    @Test
    fun `唤醒词长度按Unicode字符计算且仅接受中文`() {
        assertTrue(AppSettings.validateWakePhrase("😊好")!!.contains("仅支持中文"))
        assertTrue(AppSettings.validateWakePhrase("hello")!!.contains("仅支持中文"))
        assertTrue(AppSettings.validateWakePhrase("小\n利")!!.contains("控制字符"))
    }

    @Test
    fun `reload重新读取字符串偏好并刷新全部语音状态`() {
        val context = MemoryContext()
        val settings = AppSettings(context)
        assertFalse(settings.wakeWordEnabled.value)
        assertFalse(settings.ttsEnabled.value)
        assertEquals(VoiceAutoExecuteLevel.OFF, settings.voiceAutoExecuteLevel.value)
        assertTrue(settings.correctionLearningEnabled.value)

        context.preferences.edit()
            .putString("voice_wake_word_enabled", "true")
            .putString("voice_wake_phrase", "  小复利助手  ")
            .putString("voice_tts_enabled", "true")
            .putString("voice_auto_execute_level", "MODERATE")
            .putString("voice_wake_prompt_shown", "true")
            .putString("voice_correction_learning_enabled", "false")
            .apply()

        settings.reload()

        assertTrue(settings.wakeWordEnabled.value)
        assertEquals("小复利助手", settings.wakePhrase.value)
        assertTrue(settings.ttsEnabled.value)
        assertEquals(VoiceAutoExecuteLevel.MODERATE, settings.voiceAutoExecuteLevel.value)
        assertTrue(settings.wakePromptShown.value)
        assertFalse(settings.correctionLearningEnabled.value)
    }

    private class MemoryContext : ContextWrapper(null) {
        val preferences = MemorySharedPreferences()

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
    }

    private class MemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            values[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = MemoryEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class MemoryEditor : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = put(key, values)
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
            override fun remove(key: String?): SharedPreferences.Editor {
                updates[key.orEmpty()] = REMOVED
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() = applyChanges()

            private fun put(key: String?, value: Any?): SharedPreferences.Editor {
                updates[key.orEmpty()] = value
                return this
            }

            private fun applyChanges() {
                if (clear) values.clear()
                updates.forEach { (key, value) ->
                    if (value === REMOVED) values.remove(key) else values[key] = value
                }
            }
        }

        private companion object {
            val REMOVED = Any()
        }
    }
}
