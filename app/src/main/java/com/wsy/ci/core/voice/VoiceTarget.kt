package com.wsy.ci.core.voice

enum class VoiceTargetKind { TASK, QUEST, DOMAIN, SHOP_ITEM }

/** 语音指令可命中的候选对象：任务、主线/支线、领域或商城商品。 */
data class VoiceTarget(val id: Long, val name: String, val kind: VoiceTargetKind)

/** 候选对象的类型文案，确认卡片与结果提示共用。 */
val VoiceTargetKind.label: String
    get() = when (this) {
        VoiceTargetKind.TASK -> "任务"
        VoiceTargetKind.QUEST -> "主线/支线"
        VoiceTargetKind.DOMAIN -> "领域"
        VoiceTargetKind.SHOP_ITEM -> "商品"
    }

