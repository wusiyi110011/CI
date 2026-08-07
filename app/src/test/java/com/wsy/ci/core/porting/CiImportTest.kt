package com.wsy.ci.core.porting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CiImportTest {

    private val validJson = """
        {
          "version": 1,
          "domain": {"name": "深度学习", "titles": ["a","b","c","d","e","f"]},
          "quests": [
            {"type": "MAIN", "title": "主线A", "deadline": "2026-12-31",
             "chapters": [{"title": "第一章", "hours": 8}]},
            {"type": "SIDE", "title": "背单词"}
          ],
          "tasks": [
            {"title": "看书", "date": "2026-07-29", "start": "19:00", "end": "20:30",
             "difficulty": "HARD", "quest": "主线A"}
          ]
        }
    """.trimIndent()

    @Test
    fun `合法文件解析成功`() {
        val result = CiImport.parse(validJson)
        assertTrue(result is ImportParseResult.Ok)
        val file = (result as ImportParseResult.Ok).file
        assertEquals("深度学习", file.domain?.name)
        assertEquals(2, file.quests.size)
        assertEquals(1, file.tasks.size)
    }

    @Test
    fun `容忍markdown围栏和闲话`() {
        val wrapped = "好的，这是你的计划：\n```json\n$validJson\n```\n希望有帮助！"
        assertTrue(CiImport.parse(wrapped) is ImportParseResult.Ok)
    }

    @Test
    fun `非法JSON报错`() {
        assertTrue(CiImport.parse("not json") is ImportParseResult.Err)
    }

    @Test
    fun `空内容报错`() {
        val result = CiImport.parse("""{"version":1}""")
        assertTrue(result is ImportParseResult.Err)
        assertTrue((result as ImportParseResult.Err).errors.any { it.contains("至少填一项") })
    }

    @Test
    fun `头衔数量必须为6或不填`() {
        val result = CiImport.parse(
            """{"version":1,"domain":{"name":"x","titles":["a","b"]}}"""
        )
        assertTrue(result is ImportParseResult.Err)
        assertTrue((result as ImportParseResult.Err).errors.any { it.contains("恰好 6 个") })
    }

    @Test
    fun `任务时间与难度校验`() {
        val result = CiImport.parse(
            """
            {"version":1,"tasks":[
              {"title":"t","date":"2026-13-01","start":"25:00","end":"08:00","difficulty":"SUPER"}
            ]}
            """.trimIndent()
        )
        assertTrue(result is ImportParseResult.Err)
        val errors = (result as ImportParseResult.Err).errors
        assertTrue(errors.any { it.contains("date") })
        assertTrue(errors.any { it.contains("start") })
        assertTrue(errors.any { it.contains("difficulty") })
    }

    @Test
    fun `结束早于开始按跨午夜解析`() {
        val result = CiImport.parse(
            """
            {"version":1,"tasks":[
              {"title":"t","date":"2026-07-29","start":"20:00","end":"19:00"}
            ]}
            """.trimIndent()
        )
        assertTrue(result is ImportParseResult.Ok)
        assertEquals(19 * 60 + 24 * 60, CiImport.normalizeEndMinute(20 * 60, 19 * 60))
    }

    @Test
    fun `开始与结束相同报错`() {
        val result = CiImport.parse(
            """{"version":1,"tasks":[{"title":"t","date":"2026-07-29","start":"20:00","end":"20:00"}]}"""
        )
        assertTrue(result is ImportParseResult.Err)
        assertTrue((result as ImportParseResult.Err).errors.any { it.contains("不能相同") })
    }

    @Test
    fun `version不对报错`() {
        val result = CiImport.parse("""{"version":2,"quests":[{"title":"q"}]}""")
        assertTrue(result is ImportParseResult.Err)
    }

    @Test
    fun `模板本身包含合法JSON`() {
        assertTrue(CiImport.parse(CiImport.TEMPLATE) is ImportParseResult.Ok)
    }
}
