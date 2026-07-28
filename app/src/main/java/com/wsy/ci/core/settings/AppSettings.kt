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

/**
 * 与 LLM 无关的本机偏好（普通 SharedPreferences，无敏感数据）。
 *
 * 主题要在 Activity 重建前就生效，所以值以 StateFlow 暴露，写入即刷新整棵 Compose 树。
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    /** 存的是枚举名；万一读到旧值或脏值，退回跟随系统而不是崩掉。 */
    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
