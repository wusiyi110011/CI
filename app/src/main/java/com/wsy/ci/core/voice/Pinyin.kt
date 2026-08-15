package com.wsy.ci.core.voice

/** 汉字 → 无声调拼音；非汉字原样返回小写字符。生产实现见 `voice/AndroidPinyin.kt`（ICU Transliterator）。 */
typealias PinyinOf = (Char) -> String
