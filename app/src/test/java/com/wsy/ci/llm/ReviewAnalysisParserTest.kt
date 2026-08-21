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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAnalysisParserTest {

    /** 真机抓到的原始坏输出：insights 数组没闭合就写下 "risks":[，标准解析直接报错。 */
    private val realBrokenOutput = """
        {"insights":[ "近期任务完成率从85%大幅提升至85%，但新增计划仅提升20%，
        且'每日阅读'等核心习惯仍有空白或断档现象", "risks":[ "认知类课程（经济学）
        长期缺乏持续投入，导致整体进度滞后于其他技能", "身体疲劳累积可能影响连续训练
        质量及后续恢复效率"], "actions": ["今晚立即开始并完成经济学课程的第二章学习，
        建立正向反馈循环", "将每天固定时间预留出至少1小时进行阅读练习，打破空白状态",
        "在训练间隙安排拉伸活动，预防肌肉僵硬带来的疲劳积累"]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `真实坏输出能被容错提取救回`() {
        val parsed = ReviewAnalysisParser.parse(realBrokenOutput)

        assertEquals(1, parsed.insights.size)
        assertTrue(parsed.insights[0].startsWith("近期任务完成率"))
        assertTrue(parsed.insights[0].endsWith("断档现象"))
        assertEquals(2, parsed.risks.size)
        assertEquals("身体疲劳累积可能影响连续训练质量及后续恢复效率", parsed.risks[1])
        assertEquals(3, parsed.actions.size)
        assertEquals(
            "在训练间隙安排拉伸活动，预防肌肉僵硬带来的疲劳积累",
            parsed.actions.last(),
        )
    }

    @Test
    fun `合法JSON同样提得出三组`() {
        val parsed = ReviewAnalysisParser.parse(
            """{"insights":["一","二"],"risks":["风险"],"actions":["做","再做"]}""",
        )

        assertEquals(listOf("一", "二"), parsed.insights)
        assertEquals(listOf("风险"), parsed.risks)
        assertEquals(listOf("做", "再做"), parsed.actions)
    }

    @Test
    fun `带代码围栏和前缀废话也能提取`() {
        val raw = "好的，以下是分析：\n```json\n{\"insights\":[\"洞察\"],\"risks\":[],\"actions\":[\"行动\"]}\n```"
        val parsed = ReviewAnalysisParser.parse(raw)

        assertEquals(listOf("洞察"), parsed.insights)
        assertTrue(parsed.risks.isEmpty())
        assertEquals(listOf("行动"), parsed.actions)
    }

    @Test
    fun `键的缺失或全空返回空列表由调用方判无效`() {
        val parsed = ReviewAnalysisParser.parse("""{"risks":["只有风险"]}""")

        assertTrue(parsed.insights.isEmpty())
        assertEquals(listOf("只有风险"), parsed.risks)
        assertTrue(parsed.actions.isEmpty())
    }

    @Test
    fun `token截断时未闭合字符串收下半句`() {
        val truncated = """{"insights":["第一条完整","第二条被截断的半"""
        val parsed = ReviewAnalysisParser.parse(truncated)

        assertEquals(listOf("第一条完整", "第二条被截断的半"), parsed.insights)
    }

    @Test
    fun `字符串内的转义与引号不被误切`() {
        val parsed = ReviewAnalysisParser.parse(
            // wire 里的 \" 与 \n 都是两字符转义序列，扫描器不能把它们当结束符
            """{"insights":["含\"引号\"与\n换行","普通"]}""",
        )

        assertEquals("含\"引号\"与\n换行", parsed.insights[0])
        assertEquals("普通", parsed.insights[1])
    }

    @Test
    fun `数组内出现嵌套对象时安全停住不越界`() {
        // insights 后面跟的不是字符串而是对象——收工不崩，后续键照常提取
        val parsed = ReviewAnalysisParser.parse(
            """{"insights":[{"bad":1}],"risks":["风险"],"actions":[]}""",
        )

        assertTrue(parsed.insights.isEmpty())
        assertEquals(listOf("风险"), parsed.risks)
    }

    @Test
    fun `值写成单个字符串时按换行拆成多条`() {
        // 真机实测第二种坏法：模型把整个列表写成一个字符串，多条目用 \n 分隔
        val parsed = ReviewAnalysisParser.parse(
            """{"insights":"第一条洞察。\n第二条洞察。\n第三条洞察。","risks":["风险"],"actions":["行动"]}""",
        )

        assertEquals(listOf("第一条洞察。", "第二条洞察。", "第三条洞察。"), parsed.insights)
        assertEquals(listOf("风险"), parsed.risks)
    }

    @Test
    fun `尾逗号与杂散符号不影响提取`() {
        val parsed = ReviewAnalysisParser.parse(
            """{"insights":[ "一", "二",],"risks":[ "风险", ]" ,"actions":[ "行动" ]}""",
        )

        assertEquals(listOf("一", "二"), parsed.insights)
        assertEquals(listOf("风险"), parsed.risks)
        assertEquals(listOf("行动"), parsed.actions)
    }

    @Test
    fun `中文弯引号定界的字符串能被干净提取`() {
        // 真机实测第三种坏法：弯引号开头、直引号结尾；正文内的弯引号强调不受影响
        val parsed = ReviewAnalysisParser.parse(
            """{"insights":[ “第一条带“强调”的内容", "第二条" ],"risks":[],"actions":[]}""",
        )

        assertEquals("第一条带“强调”的内容", parsed.insights[0])
        assertEquals("第二条", parsed.insights[1])
    }

    @Test
    fun `完全不是JSON时返回全空`() {
        val parsed = ReviewAnalysisParser.parse("抱歉，我无法完成这个任务。")

        assertTrue(parsed.insights.isEmpty())
        assertTrue(parsed.risks.isEmpty())
        assertTrue(parsed.actions.isEmpty())
    }
}
