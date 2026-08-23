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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 界面明暗：默认跟随系统，也允许手动钉死。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("明亮"),
    DARK("黑暗"),
    ;

    /** [systemDark] 是系统当前的明暗，只有 [SYSTEM] 才会用到它。 */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 动效偏好：跟随系统，也允许明确开启标准动效或切换为弱动效。 */
enum class MotionMode(val label: String) {
    SYSTEM("跟随系统"),
    FULL("标准动效"),
    REDUCED("弱动效"),
    ;

    fun reduceMotion(systemReduced: Boolean): Boolean = when (this) {
        SYSTEM -> systemReduced
        FULL -> false
        REDUCED -> true
    }
}

/** 语音指令自动执行等级。等级越高，越少弹出确认；默认保持关闭以避免误操作。 */
enum class VoiceAutoExecuteLevel(val label: String, val description: String) {
    OFF("始终确认", "每条会改动数据的指令都先确认"),
    SAFE("安全操作", "仅自动执行查询和页面导航等只读操作"),
    MODERATE("适度自动", "计时与可撤销的任务操作也可自动执行"),
    ;

    companion object {
        /** 存储值来自备份或旧版本时，未知值按最保守的关闭处理。 */
        fun fromStoredName(raw: String?): VoiceAutoExecuteLevel =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

/**
 * 与 LLM 无关的本机偏好（普通 SharedPreferences，无敏感数据）。
 *
 * 主题要在 Activity 重建前就生效，所以值以 StateFlow 暴露，写入即刷新整棵 Compose 树。
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _motionMode = MutableStateFlow(readMotionMode())
    val motionMode: StateFlow<MotionMode> = _motionMode.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(readBoolean(KEY_WAKE_WORD_ENABLED, DEFAULT_WAKE_WORD_ENABLED))
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _wakePhrase = MutableStateFlow(readWakePhrase())
    val wakePhrase: StateFlow<String> = _wakePhrase.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(readBoolean(KEY_TTS_ENABLED, DEFAULT_TTS_ENABLED))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _voiceAutoExecuteLevel = MutableStateFlow(readVoiceAutoExecuteLevel())
    val voiceAutoExecuteLevel: StateFlow<VoiceAutoExecuteLevel> = _voiceAutoExecuteLevel.asStateFlow()

    private val _wakePromptShown = MutableStateFlow(readBoolean(KEY_WAKE_PROMPT_SHOWN, DEFAULT_WAKE_PROMPT_SHOWN))
    val wakePromptShown: StateFlow<Boolean> = _wakePromptShown.asStateFlow()

    private val _correctionLearningEnabled = MutableStateFlow(
        readBoolean(KEY_CORRECTION_LEARNING_ENABLED, DEFAULT_CORRECTION_LEARNING_ENABLED),
    )
    val correctionLearningEnabled: StateFlow<Boolean> = _correctionLearningEnabled.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setMotionMode(mode: MotionMode) {
        prefs.edit().putString(KEY_MOTION_MODE, mode.name).apply()
        _motionMode.value = mode
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        writeBoolean(KEY_WAKE_WORD_ENABLED, enabled)
        _wakeWordEnabled.value = enabled
    }

    /**
     * 保存唤醒词前统一 trim；返回 false 表示校验失败，此时不会写入磁盘或更新 StateFlow。
     * 界面可用 [validateWakePhrase] 提供具体中文提示。
     */
    fun setWakePhrase(raw: String): Boolean {
        val normalized = raw.trim()
        if (validateWakePhrase(normalized) != null) return false
        prefs.edit().putString(KEY_WAKE_PHRASE, normalized).apply()
        _wakePhrase.value = normalized
        return true
    }

    fun setTtsEnabled(enabled: Boolean) {
        writeBoolean(KEY_TTS_ENABLED, enabled)
        _ttsEnabled.value = enabled
    }

    fun setVoiceAutoExecuteLevel(level: VoiceAutoExecuteLevel) {
        prefs.edit().putString(KEY_VOICE_AUTO_EXECUTE_LEVEL, level.name).apply()
        _voiceAutoExecuteLevel.value = level
    }

    fun setWakePromptShown(shown: Boolean) {
        writeBoolean(KEY_WAKE_PROMPT_SHOWN, shown)
        _wakePromptShown.value = shown
    }

    fun setCorrectionLearningEnabled(enabled: Boolean) {
        writeBoolean(KEY_CORRECTION_LEARNING_ENABLED, enabled)
        _correctionLearningEnabled.value = enabled
    }

    /** 导入数据备份后重新读取磁盘偏好，并立即刷新界面主题。 */
    fun reload() {
        _themeMode.value = readThemeMode()
        _motionMode.value = readMotionMode()
        _wakeWordEnabled.value = readBoolean(KEY_WAKE_WORD_ENABLED, DEFAULT_WAKE_WORD_ENABLED)
        _wakePhrase.value = readWakePhrase()
        _ttsEnabled.value = readBoolean(KEY_TTS_ENABLED, DEFAULT_TTS_ENABLED)
        _voiceAutoExecuteLevel.value = readVoiceAutoExecuteLevel()
        _wakePromptShown.value = readBoolean(KEY_WAKE_PROMPT_SHOWN, DEFAULT_WAKE_PROMPT_SHOWN)
        _correctionLearningEnabled.value = readBoolean(
            KEY_CORRECTION_LEARNING_ENABLED,
            DEFAULT_CORRECTION_LEARNING_ENABLED,
        )
    }

    /** 存的是枚举名；万一读到旧值或脏值，退回跟随系统而不是崩掉。 */
    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    private fun readMotionMode(): MotionMode {
        val raw = prefs.getString(KEY_MOTION_MODE, null) ?: return MotionMode.SYSTEM
        return MotionMode.entries.firstOrNull { it.name == raw } ?: MotionMode.SYSTEM
    }

    private fun readVoiceAutoExecuteLevel(): VoiceAutoExecuteLevel =
        VoiceAutoExecuteLevel.fromStoredName(prefs.getString(KEY_VOICE_AUTO_EXECUTE_LEVEL, null))

    private fun readWakePhrase(): String {
        val raw = prefs.getString(KEY_WAKE_PHRASE, null) ?: DEFAULT_WAKE_PHRASE
        val normalized = raw.trim()
        return normalized.takeIf { validateWakePhrase(it) == null } ?: DEFAULT_WAKE_PHRASE
    }

    private fun readBoolean(key: String, default: Boolean): Boolean = when (prefs.getString(key, null)) {
        "true" -> true
        "false" -> false
        else -> default
    }

    private fun writeBoolean(key: String, value: Boolean) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    companion object {
        const val DEFAULT_WAKE_PHRASE = "小复利"
        const val MIN_WAKE_PHRASE_LENGTH = 2
        const val MAX_WAKE_PHRASE_LENGTH = 12

        /** 返回 null 表示合法；长度按 Unicode code point 计算，避免 emoji 被拆成两个字符。 */
        fun validateWakePhrase(raw: String): String? {
            val normalized = raw.trim()
            val length = normalized.codePointCount(0, normalized.length)
            val codePoints = normalized.codePoints().toArray()
            return when {
                normalized.isEmpty() -> "唤醒词不能为空。"
                length !in MIN_WAKE_PHRASE_LENGTH..MAX_WAKE_PHRASE_LENGTH ->
                    "唤醒词需为 $MIN_WAKE_PHRASE_LENGTH～$MAX_WAKE_PHRASE_LENGTH 个字符。"
                normalized.any { it.isISOControl() } -> "唤醒词不能包含控制字符。"
                codePoints.any { codePoint ->
                    !Character.isWhitespace(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN
                } -> "当前离线唤醒模型仅支持中文唤醒词。"
                else -> null
            }
        }
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MOTION_MODE = "motion_mode"
        private const val KEY_WAKE_WORD_ENABLED = "voice_wake_word_enabled"
        private const val KEY_WAKE_PHRASE = "voice_wake_phrase"
        private const val KEY_TTS_ENABLED = "voice_tts_enabled"
        private const val KEY_VOICE_AUTO_EXECUTE_LEVEL = "voice_auto_execute_level"
        private const val KEY_WAKE_PROMPT_SHOWN = "voice_wake_prompt_shown"
        private const val KEY_CORRECTION_LEARNING_ENABLED = "voice_correction_learning_enabled"
        const val DEFAULT_WAKE_WORD_ENABLED = false
        const val DEFAULT_TTS_ENABLED = false
        const val DEFAULT_WAKE_PROMPT_SHOWN = false
        const val DEFAULT_CORRECTION_LEARNING_ENABLED = true
    }
}
