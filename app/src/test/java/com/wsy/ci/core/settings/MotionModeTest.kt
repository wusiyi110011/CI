package com.wsy.ci.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionModeTest {

    @Test
    fun `跟随系统时仅在系统关闭动画后启用弱动效`() {
        assertFalse(MotionMode.SYSTEM.reduceMotion(systemReduced = false))
        assertTrue(MotionMode.SYSTEM.reduceMotion(systemReduced = true))
    }

    @Test
    fun `明确选择标准或弱动效时覆盖系统状态`() {
        assertFalse(MotionMode.FULL.reduceMotion(systemReduced = true))
        assertTrue(MotionMode.REDUCED.reduceMotion(systemReduced = false))
    }
}
