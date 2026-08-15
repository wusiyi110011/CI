package com.wsy.ci.core.voice

/** 一段占位时间：哪一天、当天哪段分钟区间。 */
data class TimeSpan(val epochDay: Long, val startMinute: Int, val endMinute: Int)
