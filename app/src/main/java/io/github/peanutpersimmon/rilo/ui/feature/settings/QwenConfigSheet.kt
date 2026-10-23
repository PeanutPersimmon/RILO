package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.presentation.settings.QwenSettingsViewModel
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.AppTextField
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import androidx.compose.ui.unit.dp

private enum class EditingConfigField { API_KEY, WORKSPACE_ID }

@Composable
fun QwenConfigSheet(
    viewModel: QwenSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }
    var workspaceId by remember { mutableStateOf("") }
    var editingField by remember { mutableStateOf<EditingConfigField?>(null) }
    LaunchedEffect(state.loading, state.workspaceId) {
        if (!state.loading && workspaceId.isEmpty()) workspaceId = state.workspaceId
    }
    val canSave = !state.busy && apiKey.isNotBlank() && workspaceId.isNotBlank()
    AppBottomSheet("AI 配置", onDismiss,
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.BodyTextRow,
        headerActions = {
        EditorIconAction(Icons.Outlined.Check, "保存 AI 配置", {
            viewModel.save(apiKey, workspaceId) { onDismiss() }
        }, enabled = canSave, compactSheetAction = true)
        EditorIconAction(Icons.Outlined.Close, "关闭 AI 配置", onDismiss, compactSheetAction = true)
    }) {
        Column {
            Column {
                ConfigReadRow("状态", if (state.loading) "加载中…" else if (state.configured) "已配置" else "未配置")
                ConfigReadRow("API Key", when {
                    apiKey.isNotBlank() -> "待保存"
                    state.configured -> "已配置"
                    else -> "未配置"
                }) { editingField = if (editingField == EditingConfigField.API_KEY) null else EditingConfigField.API_KEY }
                if (editingField == EditingConfigField.API_KEY) {
                    Text("修改时请重新输入完整 Key", style = AppTheme.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; viewModel.clearError() },
                        label = "API Key",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        stacked = true,
                        showLabel = false,
                        sheetCompact = true,
                    )
                }
                ConfigReadRow("Workspace ID", workspaceId.ifBlank { "未配置" }) {
                    editingField = if (editingField == EditingConfigField.WORKSPACE_ID) null else EditingConfigField.WORKSPACE_ID
                }
                if (editingField == EditingConfigField.WORKSPACE_ID) {
                    AppTextField(
                        value = workspaceId,
                        onValueChange = { workspaceId = it; viewModel.clearError() },
                        label = "Workspace ID",
                        singleLine = true,
                        stacked = true,
                        showLabel = false,
                        sheetCompact = true,
                    )
                    if (state.configured && apiKey.isBlank()) Text(
                        "保存更改需重新输入完整 API Key",
                        style = AppTheme.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.connectionStatus?.let {
                Text(it, style = AppTheme.type.caption,
                    color = if (it == "连接成功") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error?.let {
                Text(it, style = AppTheme.type.caption, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(AppTheme.dimens.spaceS))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(AppTheme.dimens.spaceS))
            ConfigActionRow(
                label = if (state.testing) "正在连接…" else "测试连接",
                enabled = !state.busy && (state.configured || apiKey.isNotBlank()),
                color = MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.testConnection(apiKey, workspaceId) },
            )
            ConfigActionRow(label = "清除 AI 配置", enabled = state.configured && !state.busy,
                color = MaterialTheme.colorScheme.error, onClick = {
                    viewModel.clear {
                        apiKey = ""
                        workspaceId = ""
                        editingField = null
                    }
                })
        }
    }
}

@Composable
private fun ConfigActionRow(label: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = AppTheme.dimens.sheetRowVisualHeight - AppTheme.dimens.spaceXs)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppTheme.dimens.spaceS,
                vertical = AppTheme.dimens.spaceS - AppTheme.dimens.spaceXs / 2),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = AppTheme.type.body,
            color = color.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
private fun ConfigReadRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val labelStyle = AppTheme.type.fieldLabel
    val valueStyle = AppTheme.type.fieldValue
    val measurer = rememberTextMeasurer()
    val gap = with(LocalDensity.current) { AppTheme.dimens.spaceS.roundToPx() }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.dimens.spaceXl)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppTheme.dimens.spaceS,
                vertical = AppTheme.dimens.spaceS - AppTheme.dimens.spaceXs / 2),
        contentAlignment = Alignment.CenterStart,
    ) {
        val fitsOneLine = measurer.measure(AnnotatedString(label), style = labelStyle,
            maxLines = 1).size.width + gap +
            measurer.measure(AnnotatedString(value), style = valueStyle,
                maxLines = 1).size.width <= constraints.maxWidth
        if (fitsOneLine) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = labelStyle)
            Spacer(Modifier.weight(1f))
            Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
            Text(label, style = labelStyle)
            Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true)
        }
    }
}
