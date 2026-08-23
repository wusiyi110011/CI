/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.feature.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.Destination
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.settings.VoiceAutoExecuteLevel
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetMatcher
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillInvocation
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRisk
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

/** [Destination] 是 internal，这些界面状态也只在应用内使用。 */
internal sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object FirstUse : VoiceUiState
    data object Preparing : VoiceUiState
    data class Recording(val elapsedMillis: Long, val amplitude: Float, val cancelling: Boolean) : VoiceUiState
    data object Recognizing : VoiceUiState
    data class Disambiguate(val text: String, val candidates: List<VoiceTarget>) : VoiceUiState
    data class Confirm(
        val text: String,
        val invocation: SkillInvocation?,
        val preview: SkillPreview?,
        val risk: SkillRisk?,
        val canReinterpret: Boolean,
    ) : VoiceUiState
    data object Executing : VoiceUiState
    data class ScheduleResult(val tasks: List<TaskEntity>) : VoiceUiState
    data class SkillResult(
        val message: String,
        val navigateTo: Destination? = null,
        val title: String = "已完成",
    ) : VoiceUiState
    data class Error(val message: String, val canOpenSettings: Boolean = true) : VoiceUiState
}

/**
 * 语音状态机：准备 → 录音 → 识别 → 消歧/确认 → 执行。
 * 手动长按和后台唤醒词最终都汇入 [resolveIntent]，不存在两套执行逻辑。
 */
class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as CiApp).container
    private val speechEngine = container.speechEngine
    private val router: VoiceSkillRouter = container.voiceSkillRouter
    private val settings = container.appSettings

    internal val navigationEvents = MutableSharedFlow<Destination>(extraBufferCapacity = 1)

    private val executionContext = SkillExecutionContext(
        appContext = getApplication(),
        db = container.db,
        timer = container.timerRepository,
        shop = container.shopRepository,
        quest = container.questRepository,
        rescheduleFlow = container.rescheduleFlow,
        updateWidgets = { CiWidgetUpdater.updateAll(getApplication()) },
        stats = container.voiceStatsRepository,
    )

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    internal val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var cancelling = false
    private var gestureHeld = false
    private var recordingObserveJob: Job? = null
    private var startRecordingJob: Job? = null
    private var recognitionJob: Job? = null
    private var cachedRuleContext: SkillRuleContext? = null
    private var originalRecognizedText = ""
    private var executing = false

    init {
        viewModelScope.launch {
            container.voiceCommandBus.commands.collect { command ->
                container.voiceCommandBus.consumed()
                handleWakeCommand(command)
            }
        }
    }

    fun onVoiceStart() {
        if (_uiState.value != VoiceUiState.Idle) return
        gestureHeld = true
        cancelling = false
        if (!settings.wakePromptShown.value) {
            settings.setWakePromptShown(true)
            _uiState.value = VoiceUiState.FirstUse
            return
        }
        container.voiceWakeRuntime.stop()
        beginManualRecording()
    }

    private fun beginManualRecording() {
        _uiState.value = VoiceUiState.Preparing
        recordingObserveJob?.cancel()
        recordingObserveJob = viewModelScope.launch {
            speechEngine.state.collect { state ->
                when (state) {
                    is SpeechState.Recording -> if (gestureHeld) {
                        _uiState.value = VoiceUiState.Recording(state.elapsedMillis, state.amplitude, cancelling)
                    }
                    is SpeechState.Failed -> fail(state.message)
                    else -> Unit
                }
            }
        }
        startRecordingJob?.cancel()
        startRecordingJob = viewModelScope.launch {
            speechEngine.prepare().getOrElse {
                fail(it.message ?: "语音识别未就绪")
                return@launch
            }
            if (!gestureHeld) return@launch
            speechEngine.startRecording().getOrElse {
                fail(it.message ?: "无法开始录音")
            }
        }
    }

    fun onVoiceDragCancelChanged(isCancelling: Boolean) {
        cancelling = isCancelling
        (_uiState.value as? VoiceUiState.Recording)?.let {
            _uiState.value = it.copy(cancelling = isCancelling)
        }
    }

    fun onVoiceEnd() {
        gestureHeld = false
        when (_uiState.value) {
            VoiceUiState.Preparing -> {
                startRecordingJob?.cancel()
                speechEngine.cancelRecording()
                _uiState.value = VoiceUiState.Idle
            }
            is VoiceUiState.Recording -> recognizeManualRecording()
            else -> Unit
        }
    }

    private fun recognizeManualRecording() {
        recordingObserveJob?.cancel()
        val requestText = originalRecognizedText
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            _uiState.value = VoiceUiState.Recognizing
            val text = speechEngine.stopAndRecognize().getOrElse {
                fail(it.message ?: "识别失败")
                return@launch
            }
            if (requestText == originalRecognizedText) acceptRecognizedText(text)
        }
    }

    fun onVoiceCancel() {
        gestureHeld = false
        startRecordingJob?.cancel()
        recordingObserveJob?.cancel()
        recognitionJob?.cancel()
        speechEngine.cancelRecording()
        _uiState.value = VoiceUiState.Idle
    }

    private suspend fun acceptRecognizedText(rawText: String) {
        originalRecognizedText = rawText.trim()
        val corrected = if (settings.correctionLearningEnabled.value) {
            container.voiceCorrectionStore.apply(originalRecognizedText)
        } else {
            originalRecognizedText
        }
        resolveIntent(corrected)
    }

    private suspend fun resolveIntent(text: String, allowLlm: Boolean = true, checkAmbiguity: Boolean = true) {
        val ruleContext = buildRuleContext()
        cachedRuleContext = ruleContext
        var invocation = router.matchByRule(text, ruleContext)
        if (invocation == null && checkAmbiguity) {
            val ranked = VoiceTargetMatcher.rank(text, ruleContext.candidates, ruleContext.pinyinOf, AMBIGUITY_THRESHOLD)
            if (VoiceTargetMatcher.isAmbiguous(ranked)) {
                val top = ranked.first().score
                _uiState.value = VoiceUiState.Disambiguate(
                    text,
                    ranked.takeWhile { top - it.score <= VoiceTargetMatcher.DEFAULT_TIE_MARGIN }
                        .take(MAX_DISAMBIGUATION_CANDIDATES)
                        .map { it.target },
                )
                return
            }
        }
        if (invocation == null && allowLlm && container.llmService.isAvailable(LlmTaskType.NL_PARSE)) {
            invocation = resolveWithLlm(text, ruleContext)
        }
        if (invocation != null && shouldAutoExecute(invocation)) {
            performExecute(invocation, text)
        } else {
            showConfirm(text, invocation, ruleContext)
        }
    }

    private suspend fun resolveWithLlm(text: String, ruleContext: SkillRuleContext): SkillInvocation? {
        val parsed = container.llmService.parseSkillCall(
            text = text,
            skills = container.voiceSkillRegistry.skills,
            candidates = ruleContext.candidates,
        )
        return (parsed as? LlmParsed.Ok)?.value?.let { router.matchFromLlm(it, ruleContext) }
    }

    private suspend fun showConfirm(text: String, invocation: SkillInvocation?, ruleContext: SkillRuleContext) {
        _uiState.value = VoiceUiState.Confirm(
            text = text,
            invocation = invocation,
            preview = invocation?.let { it.skill.preview(it.args, ruleContext) },
            risk = invocation?.skill?.risk,
            canReinterpret = container.llmService.isAvailable(LlmTaskType.NL_PARSE),
        )
    }

    private suspend fun buildRuleContext(): SkillRuleContext = SkillRuleContext(
        today = LocalDate.now(),
        nowMinute = TimeFormat.millisToMinuteOfDay(System.currentTimeMillis()),
        candidates = loadVoiceTargets(container.db),
        pinyinOf = androidPinyinOf,
        hasRunningSession = container.db.sessionDao().openSession() != null,
    )

    fun onTextEdited(text: String) {
        val ruleContext = cachedRuleContext ?: return
        val invocation = router.matchByRule(text, ruleContext)
        viewModelScope.launch { showConfirm(text, invocation, ruleContext) }
    }

    /** 手改后可明确要求 LLM 再理解一次，不在每个输入字符时发起请求。 */
    fun reinterpret() {
        val current = _uiState.value as? VoiceUiState.Confirm ?: return
        if (!current.canReinterpret) return
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Recognizing
            val ruleContext = buildRuleContext()
            cachedRuleContext = ruleContext
            val invocation = resolveWithLlm(current.text, ruleContext)
            showConfirm(current.text, invocation, ruleContext)
        }
    }

    fun chooseCandidate(target: VoiceTarget) {
        val current = _uiState.value as? VoiceUiState.Disambiguate ?: return
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Recognizing
            val base = buildRuleContext()
            val selectedContext = base.copy(candidates = listOf(target))
            cachedRuleContext = selectedContext
            val invocation = router.matchByRule(current.text, selectedContext)
                ?: if (container.llmService.isAvailable(LlmTaskType.NL_PARSE)) {
                    resolveWithLlm(current.text, selectedContext)
                } else null
            showConfirm(current.text, invocation, selectedContext)
        }
    }

    fun execute(invocation: SkillInvocation) {
        val text = (_uiState.value as? VoiceUiState.Confirm)?.text.orEmpty()
        performExecute(invocation, text)
    }

    private fun performExecute(invocation: SkillInvocation, confirmedText: String) {
        if (executing) return
        executing = true
        rememberCorrectionIfNeeded(confirmedText)
        _uiState.value = VoiceUiState.Executing
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

    private fun rememberCorrectionIfNeeded(confirmedText: String) {
        if (settings.correctionLearningEnabled.value && originalRecognizedText.isNotBlank()) {
            container.voiceCorrectionStore.remember(originalRecognizedText, confirmedText)
        }
    }

    private suspend fun handleDone(outcome: SkillOutcome.Done) {
        outcome.scheduleTasks?.let {
            _uiState.value = VoiceUiState.ScheduleResult(it)
            speakIfEnabled("查到${it.size}条安排")
            return
        }
        val destination = outcome.navigateTo?.toDestination()
        when {
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
        speakIfEnabled(outcome.message)
    }

    private fun speakIfEnabled(text: String) {
        if (!settings.ttsEnabled.value || text.isBlank()) return
        viewModelScope.launch { container.speechOutput.speak(text) }
    }

    private fun shouldAutoExecute(invocation: SkillInvocation): Boolean = when (settings.voiceAutoExecuteLevel.value) {
        VoiceAutoExecuteLevel.OFF -> false
        VoiceAutoExecuteLevel.SAFE -> invocation.skill.risk == SkillRisk.SAFE
        VoiceAutoExecuteLevel.MODERATE -> invocation.skill.risk == SkillRisk.SAFE ||
            invocation.skill.id in MODERATE_AUTO_EXECUTE_IDS
    }

    private suspend fun handleWakeCommand(command: String) {
        val normalized = command.filterNot { it.isWhitespace() || it in "，。！？,.!?" }
        when (val current = _uiState.value) {
            is VoiceUiState.Confirm -> when {
                CANCEL_WORDS.any(normalized::contains) -> dismiss()
                CONFIRM_WORDS.any(normalized::contains) && current.invocation != null -> {
                    if (current.risk == SkillRisk.DANGEROUS) {
                        speakIfEnabled("该操作风险较高，请在屏幕上点击执行")
                    } else {
                        performExecute(current.invocation, current.text)
                    }
                }
                else -> acceptRecognizedText(command)
            }
            is VoiceUiState.Disambiguate -> when {
                CANCEL_WORDS.any(normalized::contains) -> dismiss()
                normalized.contains("第一个") || normalized == "一" -> current.candidates.getOrNull(0)?.let(::chooseCandidate)
                normalized.contains("第二个") || normalized == "二" -> current.candidates.getOrNull(1)?.let(::chooseCandidate)
                normalized.contains("第三个") || normalized == "三" -> current.candidates.getOrNull(2)?.let(::chooseCandidate)
                else -> acceptRecognizedText(command)
            }
            VoiceUiState.Idle -> acceptRecognizedText(command)
            else -> Unit
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

    fun openCalendar() = navigateTo(Destination.CALENDAR)

    internal fun navigateTo(destination: Destination) {
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Idle
            navigationEvents.emit(destination)
        }
    }

    fun openVoiceSettings() = navigateTo(Destination.SETTINGS)

    fun dismiss() {
        gestureHeld = false
        recognitionJob?.cancel()
        _uiState.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        speechEngine.cancelRecording()
        super.onCleared()
    }

    private fun fail(message: String) {
        gestureHeld = false
        _uiState.value = VoiceUiState.Error(message)
    }

    private companion object {
        const val AMBIGUITY_THRESHOLD = 0.62
        const val MAX_DISAMBIGUATION_CANDIDATES = 3
        val CONFIRM_WORDS = listOf("确认", "执行", "好的", "可以")
        val CANCEL_WORDS = listOf("取消", "算了", "不要")
        val MODERATE_AUTO_EXECUTE_IDS = setOf(
            "start_timer",
            "create_task",
            "move_task",
            "lock_task",
            "unlock_task",
            "set_task_difficulty",
            "set_task_note",
            "set_quest_deadline",
            "undo_reschedule",
            "block_time",
        )
    }
}
