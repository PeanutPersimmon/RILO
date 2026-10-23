package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    stacked: Boolean = false,
    showLabel: Boolean = true,
    sheetCompact: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    if (sheetCompact) {
        Surface(
            modifier = modifier.fillMaxWidth().height(AppTheme.dimens.sheetRowVisualHeight),
            shape = AppTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = AppTheme.dimens.spaceS, end = AppTheme.dimens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 3,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    textStyle = AppTheme.type.editorInput.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text(label, style = AppTheme.type.editorInput,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            innerField()
                        }
                    },
                )
                trailingContent?.invoke()
            }
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (stacked) {
            if (showLabel) Text(label, style = AppTheme.type.fieldLabel,
                color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.dimens.editorRowHeight)
                    .semantics { contentDescription = label }
                    .padding(vertical = AppTheme.dimens.spaceS),
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 3,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                textStyle = AppTheme.type.editorInput.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(
                            label, style = AppTheme.type.editorInput,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        innerField()
                    }
                },
            )
            HorizontalDivider(color = AppTheme.colors.border)
        } else {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTheme.dimens.editorRowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceM),
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .width(AppTheme.dimens.editorLabelWidth),
                style = AppTheme.type.fieldLabel,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label }
                    .padding(vertical = AppTheme.dimens.spaceS),
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 3,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                textStyle = AppTheme.type.fieldValue.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) {
                            Text("未填写", style = AppTheme.type.fieldValue, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        innerField()
                    }
                },
            )
        }
            HorizontalDivider(color = AppTheme.colors.border)
        }
    }
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppClickableSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppTheme.shapes.medium,
        containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = AppTheme.dimens.spaceM,
                vertical = AppTheme.dimens.spaceS,
            ),
            style = AppTheme.type.itemTitle,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
