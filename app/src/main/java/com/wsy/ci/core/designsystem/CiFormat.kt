package com.wsy.ci.core.designsystem

/** CI 币金额：千分位分组 + 单位后缀，如 `3,420 CI`。 */
fun formatCi(value: Long): String = "%,d CI".format(value)

/** 带符号的流水金额，如 `+186` / `−96`（负号用 U+2212 排版减号，与设计稿一致）。 */
fun formatSignedAmount(value: Long): String =
    if (value < 0) "−%,d".format(-value) else "+%,d".format(value)
