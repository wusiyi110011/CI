package com.wsy.ci.core.porting

import com.wsy.ci.core.economy.Difficulty
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * CI 导入格式 v1：在外部（人或 AI）设计好学习计划后一键导入。
 * 顶层字段全部可选组合：domain（领域+头衔线）、quests（主线/支线）、tasks(具体日程)。
 */
@Serializable
data class ImportChapter(
    val title: String,
    val hours: Double = 0.0,
    val resources: List<String> = emptyList(),
)

@Serializable
data class ImportQuest(
    /** MAIN 主线 / SIDE 支线。 */
    val type: String = "SIDE",
    val title: String,
    val description: String = "",
    /** 仅主线：截止日期 yyyy-MM-dd。 */
    val deadline: String? = null,
    val chapters: List<ImportChapter> = emptyList(),
)

@Serializable
data class ImportDomain(
    val name: String,
    /** 恰好 6 个由低到高的头衔名；空则用通用兜底表。 */
    val titles: List<String> = emptyList(),
)

@Serializable
data class ImportTask(
    val title: String,
    /** yyyy-MM-dd */
    val date: String,
    /** HH:mm */
    val start: String,
    val end: String,
    /** EASY 轻松 / NORMAL 一般 / HARD 烧脑 / EPIC 攻坚 */
    val difficulty: String = "NORMAL",
    /** 关联任务线：填 quests 里或已存在的任务线标题。 */
    val quest: String? = null,
    val locked: Boolean = false,
    val note: String = "",
)

@Serializable
data class CiImportFile(
    val version: Int = 1,
    val domain: ImportDomain? = null,
    val quests: List<ImportQuest> = emptyList(),
    val tasks: List<ImportTask> = emptyList(),
)

/** 解析后的校验结果：错误清单为空即可导入。 */
sealed interface ImportParseResult {
    data class Ok(val file: CiImportFile) : ImportParseResult
    data class Err(val errors: List<String>) : ImportParseResult
}

object CiImport {

    const val SUPPORTED_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): ImportParseResult {
        val file = try {
            json.decodeFromString<CiImportFile>(extractJsonObject(text))
        } catch (e: Exception) {
            return ImportParseResult.Err(listOf("JSON 解析失败：${e.message?.take(120)}"))
        }
        val errors = validate(file)
        return if (errors.isEmpty()) ImportParseResult.Ok(file) else ImportParseResult.Err(errors)
    }

    fun validate(file: CiImportFile): List<String> {
        val errors = mutableListOf<String>()
        if (file.version != SUPPORTED_VERSION) {
            errors.add("version 必须为 $SUPPORTED_VERSION")
        }
        if (file.domain == null && file.quests.isEmpty() && file.tasks.isEmpty()) {
            errors.add("内容为空：domain / quests / tasks 至少填一项")
        }
        file.domain?.let {
            if (it.name.isBlank()) errors.add("domain.name 不能为空")
            if (it.titles.isNotEmpty() && it.titles.size != 6) {
                errors.add("domain.titles 要么不填，要么恰好 6 个（当前 ${it.titles.size} 个）")
            }
        }
        file.quests.forEachIndexed { i, q ->
            val where = "quests[$i]"
            if (q.title.isBlank()) errors.add("$where.title 不能为空")
            if (q.type !in listOf("MAIN", "SIDE")) errors.add("$where.type 必须是 MAIN 或 SIDE")
            q.deadline?.let {
                if (parseDate(it) == null) errors.add("$where.deadline 格式应为 yyyy-MM-dd")
            }
        }
        val questTitles = file.quests.map { it.title }.toSet()
        file.tasks.forEachIndexed { i, t ->
            val where = "tasks[$i]"
            if (t.title.isBlank()) errors.add("$where.title 不能为空")
            if (parseDate(t.date) == null) errors.add("$where.date 格式应为 yyyy-MM-dd")
            val start = parseHm(t.start)
            val end = parseHm(t.end)
            if (start == null) errors.add("$where.start 格式应为 HH:mm")
            if (end == null) errors.add("$where.end 格式应为 HH:mm")
            if (start != null && end != null && end <= start) {
                errors.add("$where 结束时间必须晚于开始时间")
            }
            if (parseDifficulty(t.difficulty) == null) {
                errors.add("$where.difficulty 必须是 EASY/NORMAL/HARD/EPIC")
            }
            // 引用未知任务线只提示不拦截：也可能引用库里已有的任务线
            if (t.quest != null && t.quest.isBlank()) errors.add("$where.quest 不能为空字符串")
        }
        return errors
    }

    fun parseDate(text: String): Long? = try {
        LocalDate.parse(text.trim()).toEpochDay()
    } catch (_: DateTimeParseException) {
        null
    }

    fun parseHm(text: String): Int? {
        val parts = text.trim().split(":", "：")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun parseDifficulty(text: String): Difficulty? =
        Difficulty.entries.firstOrNull { it.name == text.trim().uppercase() }

    /** 给外部 AI 的模板：所有字段齐全的最小示例，可直接改内容后导入。 */
    val TEMPLATE = """
请按下面的 JSON 格式帮我设计学习计划，只输出 JSON：
{
  "version": 1,
  "domain": {
    "name": "领域名（如：深度学习）",
    "titles": ["头衔1", "头衔2", "头衔3", "头衔4", "头衔5", "头衔6"]
  },
  "quests": [
    {
      "type": "MAIN",
      "title": "主线标题（主线最多同时2条）",
      "description": "一句话描述",
      "deadline": "2026-12-31",
      "chapters": [
        {"title": "第一章名", "hours": 8, "resources": ["资源链接或书名"]}
      ]
    },
    {
      "type": "SIDE",
      "title": "支线标题（每天重复的习惯，吃连击加成）",
      "description": "如：每天背50个单词"
    }
  ],
  "tasks": [
    {
      "title": "具体任务名",
      "date": "2026-07-29",
      "start": "19:00",
      "end": "20:30",
      "difficulty": "HARD",
      "quest": "主线标题（引用上面 quests 或已存在的任务线标题，可不填）",
      "locked": false,
      "note": "备注可不填"
    }
  ]
}
字段说明：difficulty 取 EASY(×0.8)/NORMAL(×1.0)/HARD(×1.3)/EPIC(×1.6)；
titles 要么不填要么恰好6个；deadline/chapters 仅主线需要；全部块均可省略。
""".trimIndent()
}
