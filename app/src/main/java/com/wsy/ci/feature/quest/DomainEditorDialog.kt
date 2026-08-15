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

package com.wsy.ci.feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.title.Titles

/** 编辑领域名与恰好六级头衔名。 */
@Composable
internal fun DomainEditorDialog(
    initial: DomainEntity,
    onSave: (name: String, titles: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.id, initial.name) { mutableStateOf(initial.name) }
    val titles = remember(initial.id, initial.titlesJson) {
        mutableStateListOf(*Titles.titleLine(initial).take(Economy.MAX_LEVEL).toTypedArray())
    }
    var error by remember { mutableStateOf<String?>(null) }

    CiFormDialog(
        title = if (initial.id == 0L) "添加领域头衔" else "编辑领域头衔",
        onDismiss = onDismiss,
        confirmLabel = "保存",
        onConfirm = {
            val trimmedName = name.trim()
            val trimmedTitles = titles.map(String::trim)
            when {
                trimmedName.isBlank() -> error = "领域名不能为空"
                trimmedTitles.size != Economy.MAX_LEVEL -> error = "请填写恰好 ${Economy.MAX_LEVEL} 个头衔"
                trimmedTitles.any { it.isBlank() } -> error = "头衔名称不能为空"
                else -> onSave(trimmedName, trimmedTitles)
            }
        },
        dismissLabel = "取消",
        width = CiSizes.dialogWideWidth,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.sm),
        ) {
            CiFormField(
                value = name,
                onValueChange = { name = it },
                label = "领域名",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "六级头衔（从 Lv.1 到 Lv.6，均不能为空）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            titles.forEachIndexed { index, title ->
                CiFormField(
                    value = title,
                    onValueChange = { titles[index] = it },
                    label = "Lv.${index + 1} 头衔",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
