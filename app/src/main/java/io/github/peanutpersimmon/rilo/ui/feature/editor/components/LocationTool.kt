package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.ui.feature.settings.LocalMetadataViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.AppTextField
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun LocationTool(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    EditorTool(Icons.Outlined.Place, "地点", value.takeIf { it.isNotBlank() }, { open = true })
    if (open) {
        val model = LocalMetadataViewModel.current
        val recent = model?.recentLocations?.collectAsStateWithLifecycle()?.value.orEmpty()
        LaunchedEffect(model) { model?.refreshRecentLocations() }
        var draft by remember { mutableStateOf(value) }
        AppBottomSheet("地点", { open = false },
            bottomVisual = if (recent.isEmpty())
                io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Surface
            else io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.BodyTextRow,
            headerActions = {
            EditorIconAction(Icons.Outlined.Check, "确认地点", { onChange(draft); open = false },
                compactSheetAction = true)
            EditorIconAction(Icons.Outlined.Close, "关闭地点", { open = false },
                compactSheetAction = true)
        }) {
            AppTextField(draft, { draft = it }, label = "输入地点", singleLine = true,
                sheetCompact = true, trailingContent = {
                    if (draft.isNotEmpty()) Box(
                        Modifier.size(AppTheme.dimens.sheetRowVisualHeight).clickable { draft = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空地点",
                            modifier = Modifier.size(AppTheme.dimens.editorIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
            if (recent.isNotEmpty()) Column(Modifier.padding(top = AppTheme.dimens.spaceS)) {
                Text("最近使用", style = AppTheme.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                recent.forEach { place ->
                    Box(Modifier.fillMaxWidth()
                        .heightIn(min = AppTheme.dimens.sheetRowVisualHeight - AppTheme.dimens.spaceXs)
                        .clickable { draft = place }
                        .padding(vertical = AppTheme.dimens.spaceS - AppTheme.dimens.spaceXs / 2),
                        contentAlignment = Alignment.CenterStart) {
                        Text(place, style = AppTheme.type.body)
                    }
                }
            }
        }
    }
}
