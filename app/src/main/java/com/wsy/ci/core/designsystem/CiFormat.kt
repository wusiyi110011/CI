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

package com.wsy.ci.core.designsystem

/** CI 币金额：千分位分组 + 单位后缀，如 `3,420 CI`。 */
fun formatCi(value: Long): String = "%,d CI".format(value)

/** 带符号的流水金额，如 `+186` / `−96`（负号用 U+2212 排版减号，与设计稿一致）。 */
fun formatSignedAmount(value: Long): String =
    if (value < 0) "−%,d".format(-value) else "+%,d".format(value)
