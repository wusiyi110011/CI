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

package com.wsy.ci.core.stats

/** 一次专注落在统计周期内的实际片段，区间仍为左闭右开。 */
data class SessionTimeSlice(val startAt: Long, val endAt: Long) {
    init {
        require(endAt > startAt) { "专注统计片段不能为空" }
    }
}

/** 把专注裁剪到统计周期 [rangeStart, rangeEndExclusive)，无交集时返回 null。 */
fun intersectSessionTime(
    startAt: Long,
    endAt: Long,
    rangeStart: Long,
    rangeEndExclusive: Long,
): SessionTimeSlice? {
    require(rangeEndExclusive > rangeStart) { "统计周期不能为空" }
    val clippedStart = maxOf(startAt, rangeStart)
    val clippedEnd = minOf(endAt, rangeEndExclusive)
    return if (clippedEnd > clippedStart) SessionTimeSlice(clippedStart, clippedEnd) else null
}
