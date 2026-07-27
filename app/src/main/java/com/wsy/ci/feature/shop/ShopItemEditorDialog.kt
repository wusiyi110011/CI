package com.wsy.ci.feature.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiShapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.economy.Rarity

/** 商品上架/编辑。价格支持「元」快捷换算（1元≈20CI），品质按价格档自动建议、可手动改。 */
@Composable
fun ShopItemEditorDialog(
    initial: ShopItemEntity,
    onSave: (ShopItemEntity) -> Unit,
    onDelete: ((ShopItemEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var priceText by remember { mutableStateOf(initial.priceCi.toString()) }
    var yuanText by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf(initial.rarity) }
    var rarityTouched by remember { mutableStateOf(initial.id != 0L) }
    var error by remember { mutableStateOf<String?>(null) }

    fun autoSuggestRarity() {
        if (!rarityTouched) {
            priceText.toLongOrNull()?.let { rarity = Economy.suggestRarity(it) }
        }
    }

    AlertDialog(
        shape = CiShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "上架商品" else "编辑商品") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CiFormField(
                        value = emoji, onValueChange = { emoji = it },
                        label = "图标", singleLine = true,
                        modifier = Modifier.width(80.dp),
                    )
                    CiFormField(
                        value = name, onValueChange = { name = it },
                        label = "名称", singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CiFormField(
                    value = description, onValueChange = { description = it },
                    label = "描述（可选）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CiFormField(
                        value = priceText,
                        onValueChange = { priceText = it; autoSuggestRarity() },
                        label = "价格 CI", singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                    CiFormField(
                        value = yuanText,
                        onValueChange = {
                            yuanText = it
                            it.toDoubleOrNull()?.let { yuan ->
                                priceText = (yuan * Economy.CI_PER_YUAN).toLong().toString()
                                autoSuggestRarity()
                            }
                        },
                        label = "按元换算(1元=20CI)", singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                }
                Text("品质（影响每日精选出现概率 45/35/15/5）")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rarity.entries.forEach { r ->
                        FilterChip(
                            selected = rarity == r,
                            onClick = { rarity = r; rarityTouched = true },
                            label = { Text(r.label) },
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceText.toLongOrNull()
                if (name.isBlank()) { error = "名称不能为空"; return@TextButton }
                if (price == null || price <= 0) { error = "价格需为正整数"; return@TextButton }
                onSave(
                    initial.copy(
                        name = name.trim(),
                        description = description.trim(),
                        emoji = emoji.ifBlank { "🎁" },
                        priceCi = price,
                        rarity = rarity,
                    )
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(initial); onDismiss() }) { Text("下架") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
