package com.wsy.ci.voice

import android.icu.text.Transliterator
import com.wsy.ci.core.voice.PinyinOf

/**
 * 汉字 → 无声调拼音，用 ICU 内置的 Han-Latin/Names 音译表（API 24+ 系统自带，零额外依赖），
 * 再经 NFD 分解、剥掉声调组合符号得到纯字母。非汉字按约定原样转小写返回。
 */
val androidPinyinOf: PinyinOf = { ch ->
    val letters = pinyinTransliterator.transliterate(ch.toString()).filter { it.isLetter() }
    letters.ifEmpty { ch.lowercaseChar().toString() }.lowercase()
}

/** 构造较重，全进程缓存单例。 */
private val pinyinTransliterator: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin/Names; NFD; [:Nonspacing Mark:] Remove; NFC")
}
