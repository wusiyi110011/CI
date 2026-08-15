package com.wsy.ci.core.designsystem

/** 完整设计规范中的动效时长，页面不得自行散落毫秒常量。 */
object CiMotion {
    const val PRESS = 140
    const val STATE = 220
    const val PANEL = 260
    const val COMPLETE = 520
    const val MILESTONE = 1_050
    const val REDUCED = 180

    fun duration(normal: Int, reduced: Boolean): Int = if (reduced) REDUCED else normal
}
