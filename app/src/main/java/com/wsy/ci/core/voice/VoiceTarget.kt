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

