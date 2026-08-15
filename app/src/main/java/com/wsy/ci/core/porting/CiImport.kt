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

package com.wsy.ci.core.porting

import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.timeline.MINUTES_PER_DAY
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
        file.quests
            .groupBy { it.title.trim() }
            .filterKeys { it.isNotEmpty() }
            .filterValues { it.size > 1 }
            .keys
            .forEach { errors.add("任务线标题不能重复：$it") }
        file.tasks.forEachIndexed { i, t ->
            val where = "tasks[$i]"
            if (t.title.isBlank()) errors.add("$where.title 不能为空")
            if (parseDate(t.date) == null) errors.add("$where.date 格式应为 yyyy-MM-dd")
            val start = parseHm(t.start)
            val end = parseHm(t.end)
            if (start == null) errors.add("$where.start 格式应为 HH:mm")
            if (end == null) errors.add("$where.end 格式应为 HH:mm")
            if (start != null && end != null && end == start) {
                errors.add("$where 开始与结束时间不能相同")
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

    /** 结束早于开始表示跨过午夜，转成相对起始日零点的分钟数。 */
    fun normalizeEndMinute(startMinute: Int, endMinute: Int): Int =
        if (endMinute < startMinute) endMinute + MINUTES_PER_DAY else endMinute

    fun parseDifficulty(text: String): Difficulty? =
        Difficulty.entries.firstOrNull { it.name == text.trim().uppercase() }

    /** 给外部 AI 的模板：包含格式、约束与排程要求，可直接连同学习资料一起发送。 */
    val TEMPLATE = """
你要为“复利”学习日程应用生成一份可直接导入的计划。请完整理解以下要求后再生成。

输出要求：
1. 最终答案只能包含一个合法 JSON 对象，不要使用 Markdown 代码围栏，不要附解释、注释或省略号。
2. 必须输出全部任务，不能用“其余类似”“后续略”等文字代替。
3. 所有键名和枚举值必须与示例一致；字符串使用双引号，最后一个字段后不要加逗号。
4. 顶层 version 固定为 1。domain、quests、tasks 至少提供一项；设计完整新计划时应三项都提供。

规划原则：
1. domain 表示长期学习领域。name 要简洁明确；titles 必须恰好 6 个，按成长等级从低到高排列，不能多也不能少。
2. quests 表示任务线。MAIN 是有明确成果和截止日期的主线，SIDE 是可长期重复并计算连击的习惯支线。任务线标题必须唯一。
3. 如果资料属于同一套连续课程或同一个总目标，优先只建立一条 MAIN，把全部章节和任务关联到这条主线，不要为了课程模块随意拆成多条主线。
4. MAIN 的 deadline 使用 yyyy-MM-dd；chapters 按学习顺序列出，每章包含预计小时数和真实可用的资源。SIDE 通常不需要 deadline 和 chapters。
5. tasks 必须是能在一个时间段内完成的具体行动，按日期与时间完整展开。标题要能单独识别，不要只写“继续学习”。
6. 每个属于某条任务线的任务都必须填写 quest，其值必须与 quests 中对应 title 逐字一致。只有确实不属于任何任务线的临时任务才可省略 quest。
7. date 使用 yyyy-MM-dd；start 和 end 使用 24 小时制 HH:mm。结束时间早于开始时间表示跨午夜；开始与结束不能相同。
8. difficulty 只能是 EASY、NORMAL、HARD、EPIC：机械阅读或整理用 EASY，一般学习用 NORMAL，高强度理解或练习用 HARD，关键攻坚或大型产出用 EPIC。
9. locked 为 true 表示排程时不可自动移动，仅用于固定课程、考试或硬性时间；普通学习任务应为 false。
10. 同一天的任务不能重叠。结合用户可用时段、截止日期、休息日和学习负荷安排；长内容应拆成多个清晰任务并留出合理休息。
11. note 用于记录资料范围、页码、验收标准或注意事项；没有必要时填空字符串。

下面是字段齐全且可直接导入的示例。请根据用户的真实目标、资料、可用时间和截止日期替换所有示例内容：
{
  "version": 1,
  "domain": {
    "name": "深度学习",
    "titles": ["神经网络学徒", "模型实验员", "训练工程师", "架构研究者", "智能系统专家", "深度学习领航者"]
  },
  "quests": [
    {
      "type": "MAIN",
      "title": "完成深度学习系统课程",
      "description": "从基础原理到独立完成一个可复现的图像分类项目",
      "deadline": "2026-12-31",
      "chapters": [
        {
          "title": "神经网络基础",
          "hours": 8,
          "resources": ["课程第 1—4 讲", "教材第 1—2 章"]
        },
        {
          "title": "图像分类项目",
          "hours": 12,
          "resources": ["项目说明书", "训练数据集"]
        }
      ]
    }
  ],
  "tasks": [
    {
      "title": "学习第 1 讲并整理前向传播笔记",
      "date": "2026-08-17",
      "start": "19:00",
      "end": "20:30",
      "difficulty": "HARD",
      "quest": "完成深度学习系统课程",
      "locked": false,
      "note": "完成课程第 1 讲，笔记至少包含 3 个关键概念和 1 个疑问"
    },
    {
      "title": "完成前向传播练习并核对答案",
      "date": "2026-08-18",
      "start": "19:00",
      "end": "20:00",
      "difficulty": "NORMAL",
      "quest": "完成深度学习系统课程",
      "locked": false,
      "note": "完成教材第 1 章练习 1—6 题，记录错题原因"
    }
  ]
}

生成前自检：JSON 能被标准解析器读取；version 为 1；头衔恰好 6 个；任务线标题不重复；每个任务的 quest 与任务线标题完全一致；日期与时间格式正确；任务没有时间冲突；没有注释、占位符或省略内容。
""".trimIndent()
}
