package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.presentation.editor.EditorChoice
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.InlineNameEditor
import io.github.peanutpersimmon.rilo.ui.feature.categories.CategoryColorPalette
import io.github.peanutpersimmon.rilo.ui.feature.categories.CategoryColorDraft
import io.github.peanutpersimmon.rilo.ui.feature.settings.*
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors

@Composable
fun RecordCategoryPicker(choices: List<EditorChoice>, selectedId: Long?, kind: SessionKind,
    onSelected: (Long?, SessionKind) -> Unit) {
    if (kind == SessionKind.ACTIVITY) CategoryPicker(choices, selectedId) { onSelected(it, kind) }
}

@Composable
fun CategoryPicker(choices: List<EditorChoice>, selectedId: Long?, onSelected: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var colorDraft by remember { mutableStateOf(CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)) }
    val focusManager = LocalFocusManager.current
    val model = LocalMetadataViewModel.current
    val metadata = LocalMetadataState.current
    val all = if (model == null) choices else metadata.categories.map { EditorChoice(it.id, it.name, it.isArchived) }
    val selected = all.firstOrNull { it.id == selectedId }
    fun cancelCreate() {
        create = false
        focusManager.clearFocus()
        model?.clearError()
    }
    BackHandler(open && create) { cancelCreate() }
    if (selected == null) {
        EditorTool(Icons.Outlined.FolderOpen, "分类", onClick = { open = true })
    } else {
        EditorSelectionChip(
            icon = Icons.Outlined.FolderOpen,
            label = "分类",
            text = selected.name,
            onClick = { open = true },
        )
    }
    if (open) AppBottomSheet(
        title = "选择分类",
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.BodyTextRow,
        onDismissRequest = { if (create) cancelCreate() else open = false },
        headerActions = {
            if (model != null) EditorIconAction(Icons.Outlined.Add, "新建分类", {
                cancelCreate()
                colorDraft = CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)
                create = true
            }, compactSheetAction = true)
            EditorIconAction(Icons.Outlined.Close, "关闭选择分类", {
                if (create) cancelCreate() else open = false
            }, compactSheetAction = true)
        },
    ) {
        if (create && model != null) {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
                Text("名称", style = AppTheme.type.fieldLabel)
                InlineNameEditor(
                initialValue = "",
                placeholder = "输入分类名称",
                onSubmit = { name ->
                    model.createCategory(name, colorDraft.selectedKey, emptyList()) { id ->
                        onSelected(id)
                        cancelCreate()
                        open = false
                    }
                },
                onCancel = ::cancelCreate,
                onDraftChange = model::clearError,
                errorMessage = metadata.error,
                primary = true,
                )
                Text("颜色", style = AppTheme.type.fieldLabel)
                CategoryColorPalette(colorDraft.selectedKey) { colorDraft = colorDraft.select(it) }
            }
        }
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())
            .padding(top = if (create && model != null) AppTheme.dimens.spaceS else 0.dp)) {
            ChoiceRow("未分类", selectedId == null, sheetStyle = true) { cancelCreate(); onSelected(null); open = false }
            selectableChoices(all, setOfNotNull(selectedId)).forEach { category ->
                ChoiceRow(category.name, category.id == selectedId, sheetStyle = true) {
                    cancelCreate(); onSelected(category.id); open = false
                }
            }
        }

    }
}

