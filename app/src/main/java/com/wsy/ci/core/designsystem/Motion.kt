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
