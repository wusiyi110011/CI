package com.wsy.ci.core.voice.skill

import android.content.Context
import com.wsy.ci.core.data.ShopRepository
import com.wsy.ci.core.data.TimerRepository
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.voice.PinyinOf
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.feature.schedule.RescheduleFlow
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject

/**
 * 语音可到达的 app 页面。放在 core 层用纯枚举表达，避免把 `MainActivity` 里的
 * `Destination`（挂 Android 资源）带进无 Android 依赖的解析链路，由 ViewModel 负责映射。
 */
enum class SkillDestination(val label: String) {
    TODAY("今日"),
    CALENDAR("日程"),
    QUEST("任务"),
    SHOP("商城"),
    STATS("复盘"),
    SETTINGS("设置"),
}

/** 一个技能执行完的结果。 */
sealed interface SkillOutcome {
    /**
     * [scheduleTasks] 非空表示这是日程查询，UI 直接进结果浮层而不是普通成功提示；
     * [title] 是结果浮层的标题，默认「已完成」，查询类技能（如领域进度）自述更贴切的标题。
     */
    data class Done(
        val message: String = "",
        val navigateTo: SkillDestination? = null,
        val scheduleTasks: List<TaskEntity>? = null,
        val title: String = "已完成",
    ) : SkillOutcome

    data class Failed(val message: String) : SkillOutcome
}

/** 确认卡片用的结构化预览；[dangerous] 为 true 触发警示样式（删除/花 CI 币类操作）。 */
data class SkillPreview(val title: String, val lines: List<String> = emptyList(), val dangerous: Boolean = false)

/** 技能参数：规则层解析或 LLM 参数校验后的键值对。 */
typealias SkillArgs = Map<String, Any?>

/**
 * 规则匹配和 LLM 参数校验用的上下文。全部参数注入、不碰 Android API，保持可测。
 * [hasRunningSession] 表示当前是否有一个进行中的专注 session（含不挂任务的自由专注），
 * 供「做完了」这类词的消歧：计时中的完成一律走结算（StopTimer），而不是直接把任务标完成。
 */
data class SkillRuleContext(
    val today: LocalDate,
    val nowMinute: Int,
    val candidates: List<VoiceTarget>,
    val pinyinOf: PinyinOf,
    val hasRunningSession: Boolean = false,
)

/**
 * 真正执行用的上下文：持有 Android Context 与用得到的仓库，由 `VoiceViewModel` 组装。
 * [updateWidgets] 按 `CiWidgetUpdater.updateAll` 注入，保证改动 task/session 数据后小组件刷新。
 */
class SkillExecutionContext(
    val appContext: Context,
    val db: CiDatabase,
    val timer: TimerRepository,
    val shop: ShopRepository,
    val rescheduleFlow: RescheduleFlow,
    val updateWidgets: suspend () -> Unit,
)

/** 一次已解析、可执行的技能调用：确认卡片据此渲染预览，用户点执行后调用 [AppSkill.execute]。 */
data class SkillInvocation(val skill: AppSkill, val args: SkillArgs)

/**
 * 一个可被语音调用的 app 功能：自描述 name/说明/参数/执行函数，登记进 [SkillRegistry] 即生效。
 *
 * 规则匹配（[matchRule]）只负责「快」：每个 skill 的触发词与其它 skill 保持 disjoint，
 * 先命中先返回；规则兜不住时由 LLM 在全部技能说明里裁决，[parseLlmArgs] 对裁决结果做候选校验，
 * 拒绝编造，校验不过退化回「未识别」，不静默执行。
 */
interface AppSkill {
    /** 稳定标识，兼作 LLM 输出里 skill 字段的值。 */
    val id: String

    /** 塞进 LLM system prompt 的一行功能说明 + 参数格式。 */
    val llmSpec: String

    /** 规则层：未命中返回 null。 */
    fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs?

    /** LLM 裁决后的参数校验：把 JsonObject 转成与 [matchRule] 一致的 args，拒绝编造返回 null。 */
    fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs?

    fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview

    suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome
}
