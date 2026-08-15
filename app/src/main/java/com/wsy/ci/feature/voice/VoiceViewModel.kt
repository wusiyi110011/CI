package com.wsy.ci.feature.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.Destination
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.voice.VoiceCommandParser
import com.wsy.ci.core.voice.VoiceIntent
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.toVoiceIntent
import com.wsy.ci.llm.LlmParsed
import com.wsy.ci.llm.LlmTaskType
import com.wsy.ci.voice.SpeechState
import com.wsy.ci.voice.androidPinyinOf
import com.wsy.ci.widget.CiWidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class Recording(val elapsedMillis: Long, val amplitude: Float, val cancelling: Boolean) : VoiceUiState
    data object Recognizing : VoiceUiState
    data class Confirm(val text: String, val intent: VoiceIntent) : VoiceUiState
    data class ScheduleResult(val tasks: List<TaskEntity>) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

/**
 * 长按 AI 图标语音指令的状态机：录音 → 整段识别 → 规则解析意图 → 确认 → 执行。
 * 三个场景分派到 [com.wsy.ci.feature.schedule.RescheduleFlow]（记占位/重排，和「一句话调整」
 * 共用同一条 diff 预览 + 撤销链路）和 [com.wsy.ci.voice.VoiceCommandExecutor]（查询 / 开始任务）。
 */
class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as CiApp).container
    private val speechEngine = container.speechEngine
    private val executor = container.voiceCommandExecutor

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    /** 一次性导航事件：语音执行成功后切到对应屏幕，由 CiRoot 收集消费。[Destination] 是 internal，此处跟随。 */
    internal val navigationEvents = MutableSharedFlow<Destination>(extraBufferCapacity = 1)

    private var cancelling = false
    private var recordingObserveJob: Job? = null
    private var cachedCandidates: List<VoiceTarget> = emptyList()

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
        cachedCandidates = executor.candidates()
        var intent = parseText(text)
        // 规则解析器兜不住，且用户配了 LLM 路由时才多花一次调用；LLM 不可用是正常路径，
        // 直接把 Unknown 留给用户手改文字，不能报错了事。
        if (intent is VoiceIntent.Unknown && container.llmService.isAvailable(LlmTaskType.NL_PARSE)) {
            intent = resolveWithLlm(text) ?: intent
        }
        _uiState.value = VoiceUiState.Confirm(text, intent)
    }

    private suspend fun resolveWithLlm(text: String): VoiceIntent? {
        val parsed = container.llmService.parseVoiceCommand(text, cachedCandidates)
        return (parsed as? LlmParsed.Ok)?.value?.toVoiceIntent(cachedCandidates, text)
    }

    private fun parseText(text: String): VoiceIntent {
        val today = LocalDate.now()
        val nowMinute = TimeFormat.millisToMinuteOfDay(System.currentTimeMillis())
        return VoiceCommandParser.parse(text, today, nowMinute, cachedCandidates, androidPinyinOf)
    }

    /** 确认浮层里手改文字：重新走一遍规则解析器，让意图卡片跟着更新。 */
    fun onTextEdited(text: String) {
        val current = _uiState.value as? VoiceUiState.Confirm ?: return
        _uiState.value = current.copy(text = text, intent = parseText(text))
    }

    fun execute(intent: VoiceIntent) {
        viewModelScope.launch {
            when (intent) {
                is VoiceIntent.BlockTime -> {
                    container.rescheduleFlow.confirmBlockers(intent.toParsedBlockers())
                    _uiState.value = VoiceUiState.Idle
                    navigationEvents.emit(Destination.TODAY)
                }
                is VoiceIntent.QuerySchedule -> {
                    val tasks = executor.querySchedule(intent.fromEpochDay, intent.toEpochDay)
                    _uiState.value = VoiceUiState.ScheduleResult(tasks)
                }
                is VoiceIntent.StartTask -> {
                    executor.startTask(intent.target)
                        .onSuccess {
                            CiWidgetUpdater.updateAll(getApplication())
                            _uiState.value = VoiceUiState.Idle
                            navigationEvents.emit(Destination.TODAY)
                        }
                        .onFailure { fail(it.message ?: "开始失败") }
                }
                is VoiceIntent.Unknown -> Unit // 确认浮层已禁用执行按钮，理论上不会走到这
            }
        }
    }

    fun openCalendar() {
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Idle
            navigationEvents.emit(Destination.CALENDAR)
        }
    }

    fun dismiss() {
        _uiState.value = VoiceUiState.Idle
    }

    private fun fail(message: String) {
        _uiState.value = VoiceUiState.Error(message)
    }
}
