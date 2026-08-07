package com.wsy.ci.core.porting

import com.wsy.ci.core.util.TimeFormat

/** 预览清单里的一段：小标题 + 逐条明细。 */
data class PreviewSection(val heading: String, val lines: List<String>)

/**
 * 「确认导入」前给用户看的清单：这次到底会往库里写什么。
 * [warnings] 是不拦截但要知会的情况（同名跳过、引用不到的任务线等）。
 */
data class ImportPreview(
    val summary: String,
    val sections: List<PreviewSection>,
    val warnings: List<String> = emptyList(),
)

/** 单段最多列这么多条，再多只报总数，免得对话框滚不到头。 */
private const val MAX_LINES_PER_SECTION = 30

/** 超出 [MAX_LINES_PER_SECTION] 的部分折叠成一行「还有 N 条」。 */
private fun <T> List<T>.previewLines(render: (T) -> String): List<String> {
    if (size <= MAX_LINES_PER_SECTION) return map(render)
    return take(MAX_LINES_PER_SECTION).map(render) + "… 还有 ${size - MAX_LINES_PER_SECTION} 条"
}

/**
 * 学习计划的导入清单。
 *
 * [existingDomainNames] / [existingQuestTitles] 由调用方从库里取，
 * 用来分辨「新建」还是「复用已有」，以及任务引用的任务线是否落得到实处。
 */
fun previewPlan(
    file: CiImportFile,
    existingDomainNames: Set<String> = emptySet(),
    existingQuestTitles: Set<String> = emptySet(),
): ImportPreview {
    val sections = mutableListOf<PreviewSection>()
    val warnings = mutableListOf<String>()

    file.domain?.let { d ->
        val name = d.name.trim()
        val isNew = name !in existingDomainNames
        val lines = mutableListOf(if (isNew) "新建领域「$name」" else "复用已有领域「$name」")
        if (d.titles.size == 6) {
            lines.add("头衔线：" + d.titles.joinToString(" → "))
            if (!isNew) lines.add("（该领域已有头衔线时保持原样，不覆盖）")
        }
        sections.add(PreviewSection("领域", lines))
    }

    if (file.quests.isNotEmpty()) {
        val lines = file.quests.previewLines { q ->
            buildString {
                append(if (q.type == "MAIN") "主线" else "支线")
                append(" · ")
                append(q.title.trim())
                q.deadline?.let { append("｜截止 $it") }
                if (q.chapters.isNotEmpty()) append("｜${q.chapters.size} 章")
                if (q.description.isNotBlank()) append("｜${q.description.trim()}")
            }
        }
        sections.add(PreviewSection("任务线 ${file.quests.size} 条", lines))
    }

    if (file.tasks.isNotEmpty()) {
        val knownTitles = existingQuestTitles + file.quests.map { it.title.trim() }
        val lines = file.tasks.previewLines { t ->
            val day = CiImport.parseDate(t.date)?.let { TimeFormat.shortDate(it) } ?: t.date
            val startMinute = CiImport.parseHm(t.start)
            val endMinute = CiImport.parseHm(t.end)
            val start = startMinute?.let { TimeFormat.minuteOfDay(it) } ?: t.start
            val end = if (startMinute != null && endMinute != null) {
                TimeFormat.minuteOfDay(CiImport.normalizeEndMinute(startMinute, endMinute))
            } else {
                t.end
            }
            val difficulty = CiImport.parseDifficulty(t.difficulty)?.label ?: t.difficulty
            buildString {
                append("$day $start–$end · ${t.title.trim()}（$difficulty）")
                t.quest?.trim()?.takeIf { it.isNotBlank() }?.let { append(" → $it") }
                if (t.locked) append(" 🔒")
            }
        }
        sections.add(PreviewSection("任务 ${file.tasks.size} 个", lines))

        val unresolved = file.tasks.mapNotNull { it.quest?.trim() }
            .filter { it.isNotBlank() && it !in knownTitles }
            .distinct()
        if (unresolved.isNotEmpty()) {
            warnings.add("这些任务线不存在，相关任务会按「未关联」导入：" + unresolved.joinToString("、"))
        }
    }

    val summary = buildString {
        append("将导入：")
        val parts = mutableListOf<String>()
        file.domain?.let { parts.add("领域 1 个") }
        if (file.quests.isNotEmpty()) parts.add("任务线 ${file.quests.size} 条")
        if (file.tasks.isNotEmpty()) parts.add("任务 ${file.tasks.size} 个")
        append(parts.joinToString("、"))
    }
    return ImportPreview(summary = summary, sections = sections, warnings = warnings)
}

/**
 * 商城批量上架的导入清单。[existingNames] 是货架上已有的商品名（含已下架），
 * 同名的这次会跳过，所以要单列出来。
 */
fun previewShop(items: List<ImportShopItem>, existingNames: Set<String> = emptySet()): ImportPreview {
    val (duplicated, fresh) = items.partition { it.name.trim() in existingNames }
    val sections = mutableListOf<PreviewSection>()
    val warnings = mutableListOf<String>()

    if (fresh.isNotEmpty()) {
        val lines = fresh.previewLines { item ->
            val rarity = ShopImport.parseRarity(item.rarity)?.label ?: item.rarity
            buildString {
                append("${item.emoji.trim().ifBlank { "🎁" }} ${item.name.trim()}")
                append(" · ${item.priceCi} CI（$rarity）")
                if (item.description.isNotBlank()) append("｜${item.description.trim()}")
            }
        }
        sections.add(PreviewSection("将上架 ${fresh.size} 件", lines))
    }
    if (duplicated.isNotEmpty()) {
        warnings.add(
            "跳过 ${duplicated.size} 件同名商品：" + duplicated.joinToString("、") { it.name.trim() }
        )
    }

    val summary = if (fresh.isEmpty()) {
        "没有可上架的新商品：${items.size} 件全是货架上已有的"
    } else {
        "将上架 ${fresh.size} 件商品" + if (duplicated.isNotEmpty()) "，跳过 ${duplicated.size} 件同名" else ""
    }
    return ImportPreview(summary = summary, sections = sections, warnings = warnings)
}
