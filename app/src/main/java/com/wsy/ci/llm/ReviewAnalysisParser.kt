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

package com.wsy.ci.llm

/**
 * 复盘输出的容错解析。
 *
 * 本地小模型写 JSON 最常见的坏法不是缺字段，而是整体结构塌掉——实测案例：
 * `insights` 数组忘了闭合就直接写下 `"risks":[`，标准解析器在 offset 78 直接报错，
 * 但每个键名下面的字符串列表本身完好。所以这里不做整体解析：定位到每个键名的
 * `[` 之后，用字符串扫描器逐个抠出字面量，直到数组正常闭合、或出现下一个
 * 「键名＋冒号」（数组没闭就开新键）、或输入结束（token 截断）。
 */
object ReviewAnalysisParser {

    /**
     * 定位某键名的值起点。[isString] 为 false 找 `[`（数组），为 true 找 `"`
     * （模型偶尔把整个列表写成一个字符串，多条目用换行分隔）。
     * 返回值起点的下标；两种形态都没有返回 null。
     */
    private fun valueStartAfter(raw: String, key: String, isString: Boolean): Int? {
        val terminator = if (isString) "[\"“”]" else "\\["
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*$terminator")
        val match = pattern.find(raw) ?: return null
        return if (isString) match.range.last else match.range.last + 1
    }

    /** 下一个 token 若已是「键名＋冒号」，说明当前数组没闭合就开了新键，必须停。 */
    private val NEXT_KEY = Regex("\"[^\"]*\"\\s*:")

    private fun isNextKeyAt(raw: String, index: Int): Boolean =
        NEXT_KEY.find(raw, index)?.range?.first == index

    /**
     * 抠出 [raw] 中键 [key] 对应的全部字符串值。
     * 值是数组就逐个提取元素；值是单个字符串则按换行拆成多条（模型把列表
     * 写成一个字符串的坏法）。其余字节（数字、裸词、杂散符号）一律跳过。
     */
    fun stringList(raw: String, key: String): List<String> {
        val asString = valueStartAfter(raw, key, isString = true)
        if (asString != null && (valueStartAfter(raw, key, isString = false) ?: Int.MAX_VALUE) > asString) {
            val (value, _, _) = scanString(raw, asString)
            return unescape(value).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        }
        val start = valueStartAfter(raw, key, isString = false) ?: return emptyList()
        val values = mutableListOf<String>()
        var i = start
        while (i < raw.length) {
            val c = raw[i]
            when {
                c.isWhitespace() || c == ',' || c == ':' -> i++
                c == ']' -> return values
                c == '"' && isNextKeyAt(raw, i) -> return values
                c == '{' || c == '[' -> return values
                // 中文弯引号也常被模型拿来做定界符（实测：“内容"），一并认领
                c == '"' || c == '“' || c == '”' -> {
                    val (value, nextIndex, closed) = scanString(raw, i)
                    values.add(unescape(value))
                    if (!closed) return values
                    i = nextIndex
                }
                else -> i++
            }
        }
        return values
    }

    /**
     * [index] 处的字符之后是否紧跟 JSON 结构位置（ASCII 逗号/右括号，忽略空白）。
     * 中文正文的标点是全角，不会被误判成结构符。
     */
    private fun isStructuralAfter(raw: String, index: Int): Boolean {
        var j = index
        while (j < raw.length && raw[j].isWhitespace()) j++
        if (j >= raw.length) return true
        return raw[j] == ',' || raw[j] == ']' || raw[j] == '}'
    }

    /**
     * 从 [start]（指向开头引号）扫一个字符串字面量。
     *
     * 闭合判定：直引号永远闭合（JSON 标准语义）；弯引号只有在后面紧跟结构
     * 位置时才算闭合——模型混用弯直引号做定界符时（实测 “内容"），结尾后面
     * 必然是逗号或右括号，而正文强调的弯引号后面跟着的是文字。返回原始转义
     * 内容、结束下标与是否见到了闭合引号——token 截断时闭合引号不存在，
     * 此时收下半句比整段丢弃好，调用方据 [closed] 决定是否提前收工。
     */
    private fun scanString(raw: String, start: Int): Triple<String, Int, Boolean> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\') {
                // 尾部悬空的反斜杠：截断现场，连同其后内容一并收尾
                if (i + 1 >= raw.length) return Triple(sb.toString(), i + 1, false)
                sb.append(c).append(raw[i + 1])
                i += 2
                continue
            }
            val closes = when (c) {
                '"' -> true
                '“', '”' -> isStructuralAfter(raw, i + 1)
                else -> false
            }
            if (closes) return Triple(sb.toString(), i + 1, true)
            sb.append(c)
            i++
        }
        return Triple(sb.toString(), i, false)
    }

    /** 标准 JSON 字符串反转义；坏转义序列原样保留，交给读者宽容。 */
    private fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                sb.append(c)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'u' -> {
                    val hexEnd = i + 6
                    val code = raw.substring(i + 2, hexEnd.coerceAtMost(raw.length))
                        .toIntOrNull(16)
                    if (code != null && hexEnd <= raw.length) {
                        sb.append(code.toChar())
                        i = hexEnd
                        continue
                    }
                    sb.append(c)
                }
                else -> sb.append(next)
            }
            i += 2
        }
        return sb.toString()
    }

    /** 三段一起提；全部为空视为无有效内容。 */
    fun parse(raw: String): ReviewAnalysis = ReviewAnalysis(
        insights = stringList(extractJson(raw), "insights"),
        risks = stringList(extractJson(raw), "risks"),
        actions = stringList(extractJson(raw), "actions"),
    )
}
