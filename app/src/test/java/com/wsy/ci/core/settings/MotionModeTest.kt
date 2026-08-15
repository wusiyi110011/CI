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
