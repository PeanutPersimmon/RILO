package io.github.peanutpersimmon.rilo.ui.feature.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.repository.CategoryUsage
import io.github.peanutpersimmon.rilo.domain.service.CategoryKeywordNormalizer
import io.github.peanutpersimmon.rilo.domain.service.ReservedKeywordPolicy
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.AppDialog
import io.github.peanutpersimmon.rilo.ui.components.CompactChip
import io.github.peanutpersimmon.rilo.ui.components.CompactChipFlow
import io.github.peanutpersimmon.rilo.ui.components.CurrentRootItemRow
import io.github.peanutpersimmon.rilo.ui.components.InlineNameEditor
import io.github.peanutpersimmon.rilo.ui.components.MetadataAction
import io.github.peanutpersimmon.rilo.ui.components.MetadataActionDialog
import io.github.peanutpersimmon.rilo.ui.components.RootChipRow
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorTextAction
import io.github.peanutpersimmon.rilo.ui.feature.settings.LocalMetadataState
import io.github.peanutpersimmon.rilo.ui.feature.settings.LocalMetadataViewModel
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors

@Composable
fun CategoryManagementSheet(onDismiss: () -> Unit) {
    val model = LocalMetadataViewModel.current ?: return
    val state = LocalMetadataState.current
    DisposableEffect(model) {
        model.setKeywordObservationActive(true)
        onDispose { model.setKeywordObservationActive(false) }
    }
    val focusManager = LocalFocusManager.current
    val active = state.categories
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = active.firstOrNull { it.id == selectedId } ?: active.firstOrNull()
    var addingCategory by remember { mutableStateOf(false) }
    var newColorDraft by remember { mutableStateOf(CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)) }
    var renamingCategoryId by remember { mutableStateOf<Long?>(null) }
    var addingKeyword by remember { mutableStateOf(false) }
    var renamingKeywordId by remember { mutableStateOf<Long?>(null) }
    var colorEditingId by remember { mutableStateOf<Long?>(null) }
    var colorDraft by remember { mutableStateOf(CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)) }
    var keywordAction by remember { mutableStateOf<KeywordRule?>(null) }
    var deletingCategory by remember { mutableStateOf<Pair<Category, CategoryUsage>?>(null) }
    var deletingKeyword by remember { mutableStateOf<Pair<Long, KeywordRule>?>(null) }
    val inputOpen = addingCategory || renamingCategoryId != null ||
        addingKeyword || renamingKeywordId != null

    fun cancelInput() {
        addingCategory = false
        renamingCategoryId = null
        addingKeyword = false
        renamingKeywordId = null
        colorEditingId = null
        focusManager.clearFocus()
        model.clearError()
    }
    fun closeStep() {
        when {
            inputOpen -> cancelInput()
            colorEditingId != null -> colorEditingId = null
            else -> onDismiss()
        }
    }
    BackHandler(inputOpen || colorEditingId != null) { closeStep() }
    LaunchedEffect(selected?.id, renamingKeywordId, state.categoryKeywords) {
        if (renamingKeywordId != null && selected?.let { category ->
                state.categoryKeywords[category.id].orEmpty().none { it.id == renamingKeywordId }
            } != false) cancelInput()
        if (selected == null && addingKeyword) cancelInput()
    }

    AppBottomSheet(
        title = "分类与关键词",
        bottomVisual = when {
            selected == null -> io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Surface
            else -> io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.ChipSlot
        },
        onDismissRequest = ::closeStep,
        headerAction = { EditorIconAction(Icons.Outlined.Close, "关闭分类与关键词", ::closeStep, compactSheetAction = true) },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        ) {
            RootChipRow(
                items = active,
                id = Category::id,
                name = Category::name,
                selectedId = selected?.id,
                adding = addingCategory,
                renamingId = renamingCategoryId,
                errorMessage = state.error,
                onSelect = { cancelInput(); selectedId = it },
                onAdd = {
                    cancelInput()
                    newColorDraft = CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)
                    addingCategory = true
                },
                onCreate = { name ->
                    model.createCategory(name, newColorDraft.selectedKey, emptyList()) { id ->
                        selectedId = id
                        addingCategory = false
                        focusManager.clearFocus()
                    }
                },
                onRename = { id, name ->
                    active.firstOrNull { it.id == id }?.let { category ->
                        model.updateCategory(id, name, category.colorKey, category.sortOrder) {
                            renamingCategoryId = null
                            focusManager.clearFocus()
                        }
                    }
                },
                onCancelInput = ::cancelInput,
                onDraftChange = model::clearError,
                validateNew = { draft ->
                    when {
                        active.any { it.name.equals(draft, ignoreCase = true) } -> "已有同名分类"
                        else -> null
                    }
                },
                validateRename = { category, draft ->
                    if (active.any { it.id != category.id && it.name.equals(draft, ignoreCase = true) })
                        "已有同名分类" else null
                },
                onReorder = model::reorderCategories,
            )

            if (addingCategory) {
                Text("颜色", style = AppTheme.type.fieldLabel)
                CategoryColorPalette(newColorDraft.selectedKey) { newColorDraft = newColorDraft.select(it) }
            }

            if (selected == null) {
                Text("暂无分类", style = AppTheme.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val category = selected
                val keywords = state.categoryKeywords[category.id].orEmpty()
                CurrentRootItemRow(
                    name = category.name,
                    detail = categoryKeywordCountText(keywords.size),
                    leading = { CategorySwatch(category.colorKey) },
                    onRename = {
                        cancelInput()
                        renamingCategoryId = category.id
                    },
                    onColor = {
                        if (colorEditingId == category.id) colorEditingId = null
                        else {
                            cancelInput()
                            colorDraft = CategoryColorDraft(category.colorKey)
                            colorEditingId = category.id
                        }
                    },
                    onDelete = {
                        cancelInput()
                        model.getCategoryUsage(category.id) { usage ->
                            deletingCategory = category to usage
                        }
                    },
                )
                if (colorEditingId == category.id) {
                    Text("颜色", style = AppTheme.type.fieldLabel)
                    CategoryColorPalette(colorDraft.selectedKey) { colorDraft = colorDraft.select(it) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)) {
                        EditorTextAction("取消", { colorEditingId = null }, compactSheetAction = true)
                        EditorTextAction("保存颜色", {
                            model.updateCategory(category.id, category.name, colorDraft.selectedKey, category.sortOrder) {
                                colorEditingId = null
                            }
                        }, compactSheetAction = true)
                    }
                }
                CompactChipFlow {
                    keywords.forEach { rule ->
                        if (renamingKeywordId == rule.id) {
                            key(rule.id) {
                                InlineNameEditor(
                                    initialValue = rule.keyword,
                                    placeholder = "关键词",
                                    onSubmit = { keyword ->
                                        model.updateCategoryKeyword(category.id, rule.id, keyword, rule.priority) {
                                            renamingKeywordId = null
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onCancel = ::cancelInput,
                                    normalize = CategoryKeywordNormalizer::normalize,
                                    onDraftChange = model::clearError,
                                    validate = { keyword -> keywordError(keyword, keywords, rule.id) },
                                    errorMessage = state.error,
                                )
                            }
                        } else CompactChip(rule.keyword, { cancelInput(); keywordAction = rule })
                    }
                    if (addingKeyword) {
                        key("add-${category.id}") {
                            InlineNameEditor(
                                initialValue = "",
                                placeholder = "输入关键词",
                                onSubmit = { keyword ->
                                    model.addCategoryKeyword(category.id, keyword) {
                                        addingKeyword = false
                                        focusManager.clearFocus()
                                    }
                                },
                                onCancel = ::cancelInput,
                                normalize = CategoryKeywordNormalizer::normalize,
                                onDraftChange = model::clearError,
                                validate = { keyword -> keywordError(keyword, keywords) },
                                errorMessage = state.error,
                            )
                        }
                    } else CompactChip("+", { cancelInput(); addingKeyword = true })
                }
            }

            state.error?.let { InlineError(it) }
            state.message?.let { InlineInfo(it) }
        }
    }

    keywordAction?.let { rule ->
        MetadataActionDialog(rule.keyword, listOf(
            MetadataAction("重命名") {
                keywordAction = null
                cancelInput()
                renamingKeywordId = rule.id
            },
            MetadataAction("删除关键词规则") {
                keywordAction = null
                selected?.let { deletingKeyword = it.id to rule }
            },
        )) { keywordAction = null }
    }
    deletingKeyword?.let { (categoryId, rule) ->
        AppDialog("删除关键词规则「${rule.keyword}」？", { deletingKeyword = null }, actions = {
            EditorTextAction("取消", { deletingKeyword = null })
            EditorTextAction("删除", {
                model.deleteCategoryKeyword(categoryId, rule.id)
                deletingKeyword = null
            })
        }) { Text("删除后该关键词规则不再用于自动分类。",
            style = AppTheme.type.editorDescription) }
    }
    deletingCategory?.let { (category, usage) ->
        AppDialog("删除分类？", { deletingCategory = null }, actions = {
            EditorTextAction("取消", { deletingCategory = null })
            EditorTextAction("删除", {
                model.deleteCategory(category.id) {
                    if (selectedId == category.id) selectedId = null
                }
                deletingCategory = null
            })
        }) {
            Text(categoryDeleteConfirmationText(category.name, usage),
                style = AppTheme.type.editorDescription)
        }
    }
}

internal fun categoryKeywordCountText(count: Int): String = "（$count）"

internal fun categoryDeleteConfirmationText(name: String, usage: CategoryUsage): String =
    if (usage.isUsed) {
        "该分类正在被 ${usage.eventCount} 条日程和 ${usage.sessionCount} 条记录使用。\n" +
            "删除分类不会删除这些内容，它们将变为未分类。\n" +
            "关联的关键词规则也会被删除。"
    } else {
        "确定删除“$name”吗？\n该分类及其关键词规则将被删除。"
    }

private fun keywordError(keyword: String, rules: List<KeywordRule>, excludingId: Long? = null): String? = when {
    ReservedKeywordPolicy.isReserved(keyword) -> "该关键词由睡眠体系处理"
    rules.any { it.id != excludingId && CategoryKeywordNormalizer.normalize(it.keyword) ==
        CategoryKeywordNormalizer.normalize(keyword) } -> "关键词已存在"
    else -> null
}

@Composable
private fun CategorySwatch(colorKey: String) {
    val colors = resolveCategoryColors(colorKey, MaterialTheme.colorScheme)
    Surface(Modifier.size(AppTheme.dimens.icon), shape = AppTheme.shapes.pill,
        color = colors.container, border = BorderStroke(AppTheme.dimens.border, colors.border)) {}
}

@Composable
private fun InlineError(message: String) {
    Text(message, Modifier.padding(vertical = AppTheme.dimens.spaceXs),
        style = AppTheme.type.caption, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun InlineInfo(message: String) {
    Text(message, Modifier.padding(vertical = AppTheme.dimens.spaceXs),
        style = AppTheme.type.caption, color = MaterialTheme.colorScheme.primary)
}

