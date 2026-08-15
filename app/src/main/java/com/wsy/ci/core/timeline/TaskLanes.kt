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

package com.wsy.ci.core.timeline

/** 一个时间区间 [startMinute, endMinute)，当日分钟数表示。 */
data class Span(val startMinute: Int, val endMinute: Int)

/** 分栏位置：处在第 [lane] 栏（0 起），这一簇重叠块共占 [laneCount] 栏。 */
data class TaskLane(val lane: Int, val laneCount: Int)

/**
 * 时间线计划轨的横向分栏：时段相交的块并排显示，互不遮挡。
 *
 * 做法是经典的区间图贪心染色——按起始时间扫描，每个块塞进第一条已空出的栏；
 * 一簇「传递重叠」的块共享同一个栏数，所以同一簇里的块等宽对齐，
 * 簇与簇之间互不影响（前一簇散场后，后面的块重新独占整轨宽度）。
 */
object TaskLanes {

    /** 返回与 [spans] 同序的分栏结果。空列表进、空列表出。 */
    fun assign(spans: List<Span>): List<TaskLane> {
        if (spans.isEmpty()) return emptyList()

        val order = spans.indices.sortedWith(
            compareBy({ spans[it].startMinute }, { spans[it].endMinute })
        )
        val laneOf = IntArray(spans.size)
        val countOf = IntArray(spans.size) { 1 }

        // 当前这一簇：已占用各栏的结束时刻，以及簇内块的下标
        var laneEnds = mutableListOf<Int>()
        var cluster = mutableListOf<Int>()
        var clusterEnd = Int.MIN_VALUE

        fun closeCluster() {
            cluster.forEach { countOf[it] = laneEnds.size }
            laneEnds = mutableListOf()
            cluster = mutableListOf()
            clusterEnd = Int.MIN_VALUE
        }

        for (index in order) {
            val span = spans[index]
            // 与整簇都不相交了，说明上一簇结束，栏数就此定死
            if (cluster.isNotEmpty() && span.startMinute >= clusterEnd) closeCluster()

            val free = laneEnds.indexOfFirst { it <= span.startMinute }
            val lane = if (free >= 0) {
                laneEnds[free] = span.endMinute
                free
            } else {
                laneEnds.add(span.endMinute)
                laneEnds.lastIndex
            }
            laneOf[index] = lane
            cluster.add(index)
            clusterEnd = maxOf(clusterEnd, span.endMinute)
        }
        closeCluster()

        return spans.indices.map { TaskLane(laneOf[it], countOf[it]) }
    }
}
