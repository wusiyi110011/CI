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

package com.wsy.ci.core.voice.skill.skills

/**
 * 各 skill 共享的触发词表。共享同一个词族的 skill（完成类 / 删除类）靠
 * 「目标 kind + 附加条件」在登记顺序上消歧，词表本身必须保持一致。
 */
internal object SkillKeywords {

    /** 完成类：StopTimer / CompleteTask / CompleteQuest 共享，靠目标 kind 与计时状态消歧。 */
    val FINISH_WORDS = listOf("完成了", "完成", "结束", "停止", "收工", "做完了", "学完了", "搞定", "干完了", "搞完了")

    /** 删除类：DeleteTask / DeleteQuest 共享，靠目标 kind 消歧。 */
    val DELETE_WORDS = listOf("删掉", "删除", "移除", "清除")
}
