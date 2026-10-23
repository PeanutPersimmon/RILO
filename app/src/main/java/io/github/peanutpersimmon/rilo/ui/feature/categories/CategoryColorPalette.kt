package io.github.peanutpersimmon.rilo.ui.feature.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import io.github.peanutpersimmon.rilo.ui.theme.ensureContrast
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

private val displayOrder = listOf(
    CategoryColorKey.RED,
    CategoryColorKey.ORANGE,
    CategoryColorKey.AMBER,
    CategoryColorKey.OLIVE,
    CategoryColorKey.GREEN,
    CategoryColorKey.TEAL,
    CategoryColorKey.BLUE,
    CategoryColorKey.PURPLE,
    CategoryColorKey.PINK,
    CategoryColorKey.BROWN,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryColorPalette(
    selectedKey: String,
    modifier: Modifier = Modifier,
    onSelected: (CategoryColorKey) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val preview = resolveCategoryColors(selectedKey, scheme)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 5,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
        ) {
            displayOrder.forEach { key ->
                CategoryColorOption(key, selectedKey, onSelected)
            }
        }
        Row {
            CategoryColorOption(CategoryColorKey.NEUTRAL, selectedKey, onSelected)
        }
        Surface(shape = AppTheme.shapes.small, color = preview.container) {
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(AppTheme.dimens.spaceXs).height(32.dp).background(preview.base))
                Text("分类卡片预览", Modifier.padding(start = AppTheme.dimens.spaceS),
                    style = AppTheme.type.caption, color = preview.content)
            }
        }
    }
}

@Composable
private fun CategoryColorOption(
    key: CategoryColorKey,
    selectedKey: String,
    onSelected: (CategoryColorKey) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isSelected = key.storageKey == selectedKey
    val colors = resolveCategoryColors(key.storageKey, scheme)
    Surface(
        modifier = Modifier.size(28.dp).clickable { onSelected(key) }.semantics {
            selected = isSelected
            role = Role.RadioButton
            contentDescription = "${colorLabel(key)}${if (isSelected) "，已选择" else ""}"
        },
        shape = CircleShape,
        color = colors.base,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp,
            if (isSelected) scheme.onSurface else scheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp),
                tint = ensureContrast(scheme.onSurface, colors.base, 4.5))
        }
    }
}

private fun colorLabel(key: CategoryColorKey): String = when (key) {
    CategoryColorKey.NEUTRAL -> "中性"
    CategoryColorKey.RED -> "红色"
    CategoryColorKey.ORANGE -> "橙色"
    CategoryColorKey.AMBER -> "黄色"
    CategoryColorKey.OLIVE -> "黄绿色"
    CategoryColorKey.GREEN -> "绿色"
    CategoryColorKey.TEAL -> "青色"
    CategoryColorKey.BLUE -> "蓝色"
    CategoryColorKey.PURPLE -> "紫色"
    CategoryColorKey.PINK -> "粉色"
    CategoryColorKey.BROWN -> "棕色"
}
