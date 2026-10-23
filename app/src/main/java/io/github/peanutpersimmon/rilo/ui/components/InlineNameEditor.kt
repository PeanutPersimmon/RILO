package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** An input chip hosted by the current sheet, with no second Window. */
@Composable
fun InlineNameEditor(
    initialValue: String,
    placeholder: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    validate: (String) -> String? = { null },
    normalize: (String) -> String = String::trim,
    onDraftChange: () -> Unit = {},
    errorMessage: String? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var localError by remember(initialValue) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme

    fun submit() {
        val draft = normalize(value)
        localError = if (draft.isEmpty()) "名称不能为空" else validate(draft)
        if (localError == null) onSubmit(draft)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.primary),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it; localError = null; onDraftChange() },
                    modifier = Modifier
                        .widthIn(min = if (primary) 120.dp else 100.dp, max = 210.dp)
                        .height(if (primary) 32.dp else 28.dp)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = placeholder }
                        .padding(horizontal = AppTheme.dimens.tagChipHorizontalPadding,
                            vertical = AppTheme.dimens.spaceXs),
                    singleLine = true,
                    textStyle = (if (primary) AppTheme.type.fieldLabel else AppTheme.type.editorDescription)
                        .copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text(placeholder, color = colors.onSurfaceVariant,
                                style = if (primary) AppTheme.type.fieldLabel else AppTheme.type.editorDescription)
                            inner()
                        }
                    },
                )
            }
            Box(Modifier.size(AppTheme.dimens.minimumTouchTarget, AppTheme.dimens.editorIconSize)
                .clickable(onClick = ::submit), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, "保存", Modifier.size(AppTheme.dimens.editorIconSize), tint = colors.primary)
            }
            Box(Modifier.size(AppTheme.dimens.minimumTouchTarget, AppTheme.dimens.editorIconSize)
                .clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "取消", Modifier.size(AppTheme.dimens.editorIconSize), tint = colors.onSurfaceVariant)
            }
        }
        (localError ?: errorMessage)?.let {
            Text(it, style = AppTheme.type.caption, color = colors.error)
        }
    }
}
