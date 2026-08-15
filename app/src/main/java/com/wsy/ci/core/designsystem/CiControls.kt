package com.wsy.ci.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wsy.ci.R

/**
 * 分段控件（日/周/月、周/月）：容器为 surfaceContainerHigh 的 pill 组，
 * 选中段浮起为 surfaceContainerLowest。
 */
@Composable
fun <T> CiSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(CiSizes.segmentedHeight)
            .clip(CiShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(CiSizes.segmentedInset),
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = CiShapes.pill,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shadowElevation = if (isSelected) CiElevation.card else CiElevation.flat,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = CiSpacing.md, vertical = CiSpacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = label(option), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 下划线式 Tab 行：选中项 primary 文字 + 2dp primary 下划线，整行底部 1dp 分割线。 */
@Composable
fun <T> CiUnderlineTabs(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.lg)) {
            options.forEach { option ->
                val isSelected = option == selected
                Column(
                    // 用文字的固有宽度约束整列，否则下划线的 fillMaxWidth 会吃掉整行、
                    // 把后面的 Tab 挤出可视区
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .heightIn(min = CiSizes.fieldHeight)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(option) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = CiSpacing.sm, bottom = CiSpacing.xs),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CiSizes.selectionIndicator)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            )
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 进度条 / 经验条：8dp 高、圆角 full，轨道为 surfaceContainerHighest。
 * [progress] 取值 0f..1f。
 */
@Composable
fun CiProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = CiSizes.progressBar,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CiShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(CiShapes.pill)
                .background(color)
        )
    }
}

/**
 * 输入框：默认 48dp 高、圆角 12、surfaceContainerLow 底 + 1dp outlineVariant 描边。
 * 比 M3 的 OutlinedTextField 更紧凑，用于对齐设计稿的表单行高。
 */
@Composable
fun CiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    height: Dp = CiSizes.fieldHeight,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val resolvedHeight = height.coerceAtLeast(CiSizes.fieldHeight)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.height(resolvedHeight),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resolvedHeight)
                    .clip(CiShapes.field)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(CiSizes.border, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
                    .padding(horizontal = CiSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * 带浮动 label 的表单输入框：圆角 12 + surfaceContainerLow 底，其余沿用 M3 行为。
 * 表单类弹窗字段多，保留 label 比 placeholder 更清楚，故与 [CiTextField] 并存。
 */
@Composable
fun CiFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = CiShapes.field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 只读的下拉选择器外观（当前值 + 手绘下拉图标），实际展开菜单由调用方接管。 */
@Composable
fun CiDropdownField(
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = CiSizes.dropdownWidth,
) {
    Row(
        modifier = modifier
            .width(width)
            .heightIn(min = CiSizes.fieldHeight)
            .clip(CiShapes.field)
            .border(CiSizes.border, MaterialTheme.colorScheme.outline, CiShapes.field)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CiSpacing.md, vertical = CiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CiFunctionIcon(
            resourceId = R.drawable.ic_ci_dropdown,
            contentDescription = if (expanded) "收起" else "展开",
            modifier = Modifier
                .size(CiSizes.compactIcon)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}
