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

package com.wsy.ci.feature.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.Destination
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillInvocation
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.VoiceSkillRouter
import com.wsy.ci.llm.LlmParsed
import com.wsy.ci.llm.LlmTaskType
import com.wsy.ci.voice.SpeechState
import com.wsy.ci.voice.androidPinyinOf
import com.wsy.ci.voice.loadVoiceTargets
import com.wsy.ci.widget.CiWidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [Destination] 是 internal，这个状态携带它，故整体 internal 不对外暴露。 */
internal sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class Recording(val elapsedMillis: Long, val amplitude: Float, val cancelling: Boolean) : VoiceUiState
    data object Recognizing : VoiceUiState

    /** 确认浮层：[invocation] 为 null 即未识别，执行按钮禁用（安全网）。 */
    data class Confirm(val text: String, val invocation: SkillInvocation?, val preview: SkillPreview?) : VoiceUiState

    data class ScheduleResult(val tasks: List<TaskEntity>) : VoiceUiState

    /** 技能执行成功的结果浮层；[navigateTo] 非空时给一个「去 XX」按钮。 */
    data class SkillResult(
        val message: String,
        val navigateTo: Destination? = null,
        val title: String = "已完成",
    ) : VoiceUiState

    data class Error(val message: String) : VoiceUiState
}

/**
 * 长按 AI 图标语音指令的状态机：录音 → 整段识别 → 技能路由（规则层优先、LLM 兜底）→ 确认 → 执行。
 * 执行统一走 `SkillInvocation.skill.execute`，加新能力 = 注册新 skill，这里不再按意图分叉。
 */
class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as CiApp).container
    private val speechEngine = container.speechEngine
    private val router: VoiceSkillRouter = container.voiceSkillRouter

    /** 一次性导航事件：语音执行成功后切到对应屏幕，由 CiRoot 收集消费。[Destination] 是 internal，此处跟随。 */
    internal val navigationEvents = MutableSharedFlow<Destination>(extraBufferCapacity = 1)

    private val executionContext = SkillExecutionContext(
        appContext = getApplication(),
        db = container.db,
        timer = container.timerRepository,
        shop = container.shopRepository,
        rescheduleFlow = container.rescheduleFlow,
        updateWidgets = { CiWidgetUpdater.updateAll(getApplication()) },
    )

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    internal val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var cancelling = false
    private var recordingObserveJob: Job? = null
    private var cachedRuleContext: SkillRuleContext? = null
    private var executing = false

    /** 长按成立时调用：乐观地立即展示录音浮层，prepare/startRecording 的失败会转成 [VoiceUiState.Error]。 */
    fun onVoiceStart() {
        cancelling = false
        _uiState.value = VoiceUiState.Recording(0L, 0f, cancelling = false)
        recordingObserveJob?.cancel()
        recordingObserveJob = viewModelScope.launch {
            speechEngine.state.collect { state ->
                if (state is SpeechState.Recording) {
                    _uiState.value = VoiceUiState.Recording(state.elapsedMillis, state.amplitude, cancelling)
                }
            }
        }
        viewModelScope.launch {
            val prepared = speechEngine.prepare()
            if (prepared.isFailure) {
                fail(prepared.exceptionOrNull()?.message ?: "语音识别未就绪")
                return@launch
            }
            val started = speechEngine.startRecording()
            if (started.isFailure) {
                fail(started.exceptionOrNull()?.message ?: "无法开始录音")
            }
        }
    }

    /** 手指相对按下点上滑超过取消阈值时调用，驱动浮层转 error 色。 */
    fun onVoiceDragCancelChanged(isCancelling: Boolean) {
        cancelling = isCancelling
        (_uiState.value as? VoiceUiState.Recording)?.let {
            _uiState.value = it.copy(cancelling = isCancelling)
        }
    }

    /** 松手结束：停止录音、整段识别、解析意图，进入确认浮层。 */
    fun onVoiceEnd() {
        recordingObserveJob?.cancel()
        if (_uiState.value !is VoiceUiState.Recording) {
            // prepare/startRecording 已经失败并把状态切到了 Error，不要用「尚未开始录音」
            // 这种含糊的话覆盖掉刚才更有用的失败原因。
            return
        }
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Recognizing
            val text = speechEngine.stopAndRecognize().getOrElse {
                fail(it.message ?: "识别失败")
                return@launch
            }
            resolveIntent(text)
        }
    }

    /** 上滑取消：不识别，直接回到 Idle。 */
    fun onVoiceCancel() {
        recordingObserveJob?.cancel()
        speechEngine.cancelRecording()
        _uiState.value = VoiceUiState.Idle
    }

    private suspend fun resolveIntent(text: String) {
        val ruleContext = buildRuleContext()
        cachedRuleContext = ruleContext
        var invocation = router.matchByRule(text, ruleContext)
        // 规则层兜不住，且用户配了 LLM 路由时才多花一次调用；LLM 不可用是正常路径，
        // 直接把 null（未识别）留给用户手改文字，不能报错了事。
        if (invocation == null && container.llmService.isAvailable(LlmTaskType.NL_PARSE)) {
            invocation = resolveWithLlm(text, ruleContext)
        }
        showConfirm(text, invocation, ruleContext)
    }

    private suspend fun resolveWithLlm(text: String, ruleContext: SkillRuleContext): SkillInvocation? {
        val parsed = container.llmService.parseSkillCall(
            text = text,
            skills = container.voiceSkillRegistry.skills,
            candidates = ruleContext.candidates,
        )
        return (parsed as? LlmParsed.Ok)?.value?.let { router.matchFromLlm(it, ruleContext) }
    }

    private fun showConfirm(text: String, invocation: SkillInvocation?, ruleContext: SkillRuleContext) {
        _uiState.value = VoiceUiState.Confirm(
            text = text,
            invocation = invocation,
            preview = invocation?.let { it.skill.preview(it.args, ruleContext) },
        )
    }

    private suspend fun buildRuleContext(): SkillRuleContext = SkillRuleContext(
        today = LocalDate.now(),
        nowMinute = TimeFormat.millisToMinuteOfDay(System.currentTimeMillis()),
        candidates = loadVoiceTargets(container.db),
        pinyinOf = androidPinyinOf,
        // 以 open session 为准而不是 RUNNING 状态的任务：自由专注（不挂任务）时也有进行中的 session
        hasRunningSession = container.db.sessionDao().openSession() != null,
    )

    /** 确认浮层里手改文字：只重走规则层（快、无网络），让意图卡片跟着更新。 */
    fun onTextEdited(text: String) {
        val current = _uiState.value as? VoiceUiState.Confirm ?: return
        val ruleContext = cachedRuleContext ?: return
        val invocation = router.matchByRule(text, ruleContext)
        showConfirm(text, invocation, ruleContext)
    }

    /** 执行已确认的技能调用：按返回的 [SkillOutcome] 更新 UI 状态、可选触发导航。 */
    fun execute(invocation: SkillInvocation) {
        // in-flight 锁：确认浮层上双击「执行」不能并发跑两次（购买会重复扣款、删除会重复执行）
        if (executing) return
        executing = true
        viewModelScope.launch {
            try {
                when (val outcome = invocation.skill.execute(invocation.args, executionContext)) {
                    is SkillOutcome.Done -> handleDone(outcome)
                    is SkillOutcome.Failed -> fail(outcome.message)
                }
            } finally {
                executing = false
            }
        }
    }

    private suspend fun handleDone(outcome: SkillOutcome.Done) {
        outcome.scheduleTasks?.let {
            _uiState.value = VoiceUiState.ScheduleResult(it)
            return
        }
        val destination = outcome.navigateTo?.toDestination()
        when {
            // 有结果文案又有跳转：弹结果浮层，给「去 XX」按钮，不静默跳走
            destination != null && outcome.message.isNotBlank() ->
                _uiState.value = VoiceUiState.SkillResult(outcome.message, destination, outcome.title)
            destination != null -> {
                _uiState.value = VoiceUiState.Idle
                navigationEvents.emit(destination)
            }
            outcome.message.isNotBlank() ->
                _uiState.value = VoiceUiState.SkillResult(outcome.message, title = outcome.title)
            else -> _uiState.value = VoiceUiState.Idle
        }
    }

    private fun SkillDestination.toDestination(): Destination = when (this) {
        SkillDestination.TODAY -> Destination.TODAY
        SkillDestination.CALENDAR -> Destination.CALENDAR
        SkillDestination.QUEST -> Destination.QUEST
        SkillDestination.SHOP -> Destination.SHOP
        SkillDestination.STATS -> Destination.STATS
        SkillDestination.SETTINGS -> Destination.SETTINGS
    }

    fun openCalendar() {
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Idle
            navigationEvents.emit(Destination.CALENDAR)
        }
    }

    /** 结果浮层「去 XX」按钮：关浮层并导航到对应页面。 */
    internal fun navigateTo(destination: Destination) {
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Idle
            navigationEvents.emit(destination)
        }
    }

    fun dismiss() {
        _uiState.value = VoiceUiState.Idle
    }

    private fun fail(message: String) {
        _uiState.value = VoiceUiState.Error(message)
    }
}
