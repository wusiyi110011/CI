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

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setMotionMode(mode: MotionMode) {
        prefs.edit().putString(KEY_MOTION_MODE, mode.name).apply()
        _motionMode.value = mode
    }

    /** 导入数据备份后重新读取磁盘偏好，并立即刷新界面主题。 */
    fun reload() {
        _themeMode.value = readThemeMode()
        _motionMode.value = readMotionMode()
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

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MOTION_MODE = "motion_mode"
    }
}
