/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.voice.ChineseTimeParser
import com.wsy.ci.core.voice.TimeSpan
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetKind
import com.wsy.ci.core.voice.VoiceTargetMatcher
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.difficultyOrNull
import com.wsy.ci.core.voice.skill.epochDayOrNull
import com.wsy.ci.core.voice.skill.minuteOrNull
import com.wsy.ci.core.voice.skill.targetIdOrNull
import com.wsy.ci.core.voice.skill.targetOrNull
import com.wsy.ci.core.voice.skill.textOrNull
import com.wsy.ci.core.voice.skill.validTaskTime
import com.wsy.ci.core.voice.skill.byIdAndKinds
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val DAY_MINUTES = 24 * 60

/** 创建一个计划任务；没有给出完整时间时不静默猜测，交给 LLM 或用户重说。 */
object CreateTaskSkill : AppSkill {
    override val id = "create_task"
    override val risk = SkillRisk.MODERATE
    override val llmSpec =
        "创建一个计划任务；args: {\"title\":\"任务名\",\"date\":\"yyyy-MM-dd\",\"start\":\"HH:mm\",\"end\":\"HH:mm\",\"difficulty\":\"EASY|NORMAL|HARD|EPIC\",\"note\":\"备注\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (CREATE_WORDS.none { text.contains(it) }) return null
        val span = ChineseTimeParser.parseSpans(text, ctx.today, ctx.nowMinute).firstOrNull() ?: return null
        val title = extractTaskTitle(text) ?: return null
        if (!validTaskTime(span.epochDay, span.startMinute, span.endMinute, ctx.today.toEpochDay())) return null
        return mapOf(
            "title" to title,
            "epochDay" to span.epochDay,
            "startMinute" to span.startMinute,
            "endMinute" to span.endMinute,
            "difficulty" to difficultyFromText(text),
            "note" to extractNote(text).orEmpty(),
        )
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val title = args.textOrNull("title", 120) ?: return null
        val day = args.epochDayOrNull("date", "epochDay") ?: return null
        val start = args.minuteOrNull("start", "startMinute") ?: return null
        val end = args.minuteOrNull("end", "endMinute") ?: return null
        if (!validTaskTime(day, start, end, ctx.today.toEpochDay())) return null
        val domainElement = args["domainId"]
        val questElement = args["questId"]
        val domainId = (domainElement as? JsonPrimitive)?.longOrNull
        val questId = (questElement as? JsonPrimitive)?.longOrNull
        if ((domainElement != null && domainId == null) || (questElement != null && questId == null)) return null
        if (domainId != null && ctx.candidates.byIdAndKinds(domainId, setOf(VoiceTargetKind.DOMAIN)) == null) return null
        if (questId != null && ctx.candidates.byIdAndKinds(questId, setOf(VoiceTargetKind.QUEST)) == null) return null
        val difficulty = args.difficultyOrNull() ?: Difficulty.NORMAL
        val note = args.textOrNull("note", 300).orEmpty()
        return mapOf(
            "title" to title,
            "epochDay" to day,
            "startMinute" to start,
            "endMinute" to end,
            "difficulty" to difficulty,
            "note" to note,
            "domainId" to domainId,
            "questId" to questId,
        )
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview =
        SkillPreview("创建任务", lines = listOf(taskSummary(args), "难度：${(args["difficulty"] as? Difficulty)?.label ?: "一般"}"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val title = (args["title"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return SkillOutcome.Failed("任务名称不能为空")
        val day = args["epochDay"] as? Long ?: return SkillOutcome.Failed("日期已失效，请重新说一次")
        val start = (args["startMinute"] as? Number)?.toInt() ?: return SkillOutcome.Failed("开始时间已失效，请重新说一次")
        val end = (args["endMinute"] as? Number)?.toInt() ?: return SkillOutcome.Failed("结束时间已失效，请重新说一次")
        if (!validTaskTime(day, start, end, LocalDate.now().toEpochDay())) return SkillOutcome.Failed("任务时间必须是今天或未来，且结束晚于开始")
        val questId = (args["questId"] as? Number)?.toLong()
        val domainId = (args["domainId"] as? Number)?.toLong()
        val quest = questId?.let { ctx.db.questDao().byId(it) }
        if (questId != null && (quest == null || quest.status != QuestStatus.ACTIVE)) return SkillOutcome.Failed("任务线已失效或不是进行中状态")
        if (domainId != null && ctx.db.domainDao().byId(domainId) == null) return SkillOutcome.Failed("学习领域已失效，请重新选择")
        if (quest != null && domainId != null && domainId != quest.domainId) {
            return SkillOutcome.Failed("任务线与学习领域不一致，请重新选择")
        }
        val task = TaskEntity(
            title = title,
            epochDay = day,
            startMinute = start,
            endMinute = end,
            domainId = quest?.domainId ?: domainId,
            questId = questId,
            difficulty = args["difficulty"] as? Difficulty ?: Difficulty.NORMAL,
            note = (args["note"] as? String).orEmpty().trim(),
        )
        ctx.db.taskDao().insert(task)
        ctx.updateWidgets()
        return SkillOutcome.Done("已创建任务「$title」", title = "任务已创建")
    }
}

/** 移动一个任务到新的日期和时间。 */
object MoveTaskSkill : AppSkill {
    override val id = "move_task"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "移动任务的日期和时间；args: {\"targetId\":数字id,\"date\":\"yyyy-MM-dd\",\"start\":\"HH:mm\",\"end\":\"HH:mm\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (MOVE_WORDS.none { text.contains(it) }) return null
        val target = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.TASK } ?: return null
        val span = ChineseTimeParser.parseSpans(text, ctx.today, ctx.nowMinute).firstOrNull() ?: return null
        if (!validTaskTime(span.epochDay, span.startMinute, span.endMinute, ctx.today.toEpochDay())) return null
        return mapOf("target" to target, "epochDay" to span.epochDay, "startMinute" to span.startMinute, "endMinute" to span.endMinute)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val target = args.targetOrNull(ctx, VoiceTargetKind.TASK) ?: return null
        val day = args.epochDayOrNull("date", "epochDay") ?: return null
        val start = args.minuteOrNull("start", "startMinute") ?: return null
        val end = args.minuteOrNull("end", "endMinute") ?: return null
        if (!validTaskTime(day, start, end, ctx.today.toEpochDay())) return null
        return mapOf("target" to target, "epochDay" to day, "startMinute" to start, "endMinute" to end)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview =
        SkillPreview("移动任务", lines = listOf(taskSummary(args)))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val target = args["target"] as? VoiceTarget ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val task = ctx.db.taskDao().byId(target.id) ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        if (task.locked) return SkillOutcome.Failed("「${task.title}」已锁定，请先解锁再移动")
        if (ctx.db.sessionDao().openSession()?.taskId == task.id) return SkillOutcome.Failed("正在计时的任务不能移动")
        val day = args["epochDay"] as? Long ?: return SkillOutcome.Failed("日期已失效，请重新说一次")
        val start = (args["startMinute"] as? Number)?.toInt() ?: return SkillOutcome.Failed("开始时间已失效，请重新说一次")
        val end = (args["endMinute"] as? Number)?.toInt() ?: return SkillOutcome.Failed("结束时间已失效，请重新说一次")
        if (!validTaskTime(day, start, end, LocalDate.now().toEpochDay())) return SkillOutcome.Failed("任务时间必须是今天或未来，且结束晚于开始")
        ctx.db.taskDao().update(task.copy(epochDay = day, startMinute = start, endMinute = end))
        ctx.updateWidgets()
        return SkillOutcome.Done("已将「${task.title}」移动到 ${LocalDate.ofEpochDay(day)} ${hm(start)}")
    }
}

/** 锁定任务，排程引擎不会移动锁定块。 */
object LockTaskSkill : TaskLockSkill(true)

/** 解锁任务，恢复排程引擎的可移动资格。 */
object UnlockTaskSkill : TaskLockSkill(false)

open class TaskLockSkill(private val locked: Boolean) : AppSkill {
    override val id: String = if (locked) "lock_task" else "unlock_task"
    override val risk = SkillRisk.MODERATE
    override val llmSpec: String = "${if (locked) "锁定" else "解锁"}一个任务；args: {\"targetId\":候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        val words = if (locked) LOCK_WORDS else UNLOCK_WORDS
        if (words.none { text.contains(it) }) return null
        val target = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.TASK } ?: return null
        return mapOf("target" to target)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? =
        args.targetOrNull(ctx, VoiceTargetKind.TASK)?.let { mapOf("target" to it) }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview =
        SkillPreview(if (locked) "锁定任务" else "解锁任务", lines = listOf("任务 · ${targetName(args, ctx)}"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val target = args["target"] as? VoiceTarget ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val task = ctx.db.taskDao().byId(target.id) ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        if (task.locked == locked) return SkillOutcome.Failed("「${task.title}」已经${if (locked) "锁定" else "解锁"}")
        ctx.db.taskDao().update(task.copy(locked = locked))
        ctx.updateWidgets()
        return SkillOutcome.Done("已${if (locked) "锁定" else "解锁"}「${task.title}」")
    }

    private companion object {
        val LOCK_WORDS = listOf("锁定", "固定住", "别移动")
        val UNLOCK_WORDS = listOf("解锁", "取消固定", "不再固定")
    }
}

/** 修改任务难度，后续结算按新的难度系数计算。 */
object SetTaskDifficultySkill : AppSkill {
    override val id = "set_task_difficulty"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "修改任务难度；args: {\"targetId\":数字id,\"difficulty\":\"EASY|NORMAL|HARD|EPIC\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (DIFFICULTY_WORDS.none { text.contains(it) }) return null
        val target = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.TASK } ?: return null
        val difficulty = difficultyFromText(text) ?: return null
        return mapOf("target" to target, "difficulty" to difficulty)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val target = args.targetOrNull(ctx, VoiceTargetKind.TASK) ?: return null
        val difficulty = args.difficultyOrNull() ?: return null
        return mapOf("target" to target, "difficulty" to difficulty)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) = SkillPreview("修改难度", lines = listOf("任务 · ${targetName(args, ctx)}", "难度：${(args["difficulty"] as? Difficulty)?.label ?: "未知"}"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val target = args["target"] as? VoiceTarget ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val difficulty = args["difficulty"] as? Difficulty ?: return SkillOutcome.Failed("难度参数无效")
        val task = ctx.db.taskDao().byId(target.id) ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        ctx.db.taskDao().update(task.copy(difficulty = difficulty))
        ctx.updateWidgets()
        return SkillOutcome.Done("已将「${task.title}」难度改为${difficulty.label}")
    }
}

/** 修改任务备注。 */
object SetTaskNoteSkill : AppSkill {
    override val id = "set_task_note"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "修改任务备注；args: {\"targetId\":数字id,\"note\":\"备注内容\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (NOTE_WORDS.none { text.contains(it) }) return null
        val target = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.TASK } ?: return null
        val note = extractNote(text) ?: return null
        return mapOf("target" to target, "note" to note)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val target = args.targetOrNull(ctx, VoiceTargetKind.TASK) ?: return null
        val note = args.textOrNull("note", 300) ?: return null
        return mapOf("target" to target, "note" to note)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) = SkillPreview("修改备注", lines = listOf("任务 · ${targetName(args, ctx)}", "备注：${args["note"] as? String ?: ""}"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val target = args["target"] as? VoiceTarget ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val note = (args["note"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return SkillOutcome.Failed("备注不能为空")
        val task = ctx.db.taskDao().byId(target.id) ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        ctx.db.taskDao().update(task.copy(note = note))
        ctx.updateWidgets()
        return SkillOutcome.Done("已更新「${task.title}」的备注")
    }
}

/** 设置任务线截止日期。 */
object SetQuestDeadlineSkill : AppSkill {
    override val id = "set_quest_deadline"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "设置任务线截止日期；args: {\"targetId\":数字id,\"date\":\"yyyy-MM-dd\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (DEADLINE_WORDS.none { text.contains(it) }) return null
        val target = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.QUEST } ?: return null
        val day = ChineseTimeParser.parseDateRange(text, ctx.today)?.first ?: return null
        if (day < ctx.today.toEpochDay()) return null
        return mapOf("target" to target, "deadlineEpochDay" to day)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val target = args.targetOrNull(ctx, VoiceTargetKind.QUEST) ?: return null
        val day = args.epochDayOrNull("date", "deadlineEpochDay") ?: return null
        if (day < ctx.today.toEpochDay() || day > ctx.today.toEpochDay() + 365L * 5) return null
        return mapOf("target" to target, "deadlineEpochDay" to day)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) =
        SkillPreview("设置截止日期", lines = listOf("任务线 · ${targetName(args, ctx)}", "截止：${args["deadlineEpochDay"] ?: "未知"}"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val target = args["target"] as? VoiceTarget ?: return SkillOutcome.Failed("任务线已失效，请重新说一次")
        val day = args["deadlineEpochDay"] as? Long ?: return SkillOutcome.Failed("截止日期已失效，请重新说一次")
        if (day < LocalDate.now().toEpochDay()) return SkillOutcome.Failed("截止日期不能早于今天")
        val quest = ctx.db.questDao().byId(target.id) ?: return SkillOutcome.Failed("找不到这条任务线，可能已被删除")
        if (quest.type != QuestType.MAIN) return SkillOutcome.Failed("只能为主线设置截止日期")
        ctx.db.questDao().update(quest.copy(deadlineEpochDay = day))
        return SkillOutcome.Done("已将「${quest.title}」截止日期设为 ${LocalDate.ofEpochDay(day)}")
    }
}

private fun taskSummary(args: SkillArgs): String {
    val title = args["title"] as? String ?: (args["target"] as? VoiceTarget)?.name ?: "未知"
    val day = args["epochDay"] as? Long
    val start = args["startMinute"] as? Int
    val end = args["endMinute"] as? Int
    return if (day != null && start != null && end != null) "$title · ${LocalDate.ofEpochDay(day)} ${hm(start)}–${hm(end)}" else title
}

private fun targetName(args: SkillArgs, ctx: SkillRuleContext): String =
    (args["target"] as? VoiceTarget)?.name
        ?: (args.targetIdOrNull()?.let { id -> ctx.candidates.firstOrNull { it.id == id }?.name })
        ?: "未知"

private fun hm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private fun difficultyFromText(text: String): Difficulty? = when {
    text.contains("攻坚") || text.contains("极难") -> Difficulty.EPIC
    text.contains("困难") || text.contains("烧脑") || text.contains("难度高") -> Difficulty.HARD
    text.contains("简单") || text.contains("轻松") -> Difficulty.EASY
    text.contains("一般") || text.contains("普通") -> Difficulty.NORMAL
    else -> null
}

private fun extractNote(text: String): String? =
    Regex("""(?:备注|心得|学到|记下)[:：]?\s*(.+)$""")
        .find(text)?.groupValues?.getOrNull(1)?.trim()
        ?.takeIf { it.isNotBlank() && it.length <= 300 }

private fun extractTaskTitle(text: String): String? {
    val raw = Regex("""(?:创建|新增|添加|安排)(?:一个|个)?任务(?:叫|名为|是)?[:：]?\s*(.+)""")
        .find(text)?.groupValues?.getOrNull(1) ?: return null
    val cleaned = raw
        .replace(Regex("今天|明天|后天|大后天|这周|本周|下周|上午|下午|晚上|早上|中午|全天"), " ")
        .replace(Regex("[0-2]?\\d:[0-5]\\d|[一二三四五六七八九十两\\d]{1,3}点(?:半|[一二三四五六七八九十两\\d]{1,2}分?)?"), " ")
        .replace(Regex("\\s*(到|至|~|－|-)\\s*"), " ")
        .replace(Regex("[，,。；;].*$"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '，', ',', '。', '；', ';')
        .removeSuffix("任务")
        .trim()
    return cleaned.takeIf { it.isNotBlank() && it.length <= 120 }
}

private val CREATE_WORDS = listOf("创建任务", "新增任务", "添加任务", "安排任务", "建个任务")
private val MOVE_WORDS = listOf("移动任务", "挪一下", "改到", "调整到", "换到")
private val DIFFICULTY_WORDS = listOf("难度", "简单", "轻松", "一般", "普通", "困难", "烧脑", "攻坚", "极难")
private val NOTE_WORDS = listOf("备注", "心得", "学到", "记下")
private val DEADLINE_WORDS = listOf("截止", "期限", "最后期限")
