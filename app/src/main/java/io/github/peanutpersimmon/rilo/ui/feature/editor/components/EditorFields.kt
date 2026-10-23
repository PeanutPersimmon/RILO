package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import io.github.peanutpersimmon.rilo.ui.components.AppTextField
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("M/d")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

enum class EditorTimeRole { START, END, GENERIC }

internal fun editorTimeIcon(role: EditorTimeRole) = when (role) {
    EditorTimeRole.START -> Icons.Outlined.PlayArrow
    EditorTimeRole.END -> Icons.Outlined.Stop
    EditorTimeRole.GENERIC -> Icons.Outlined.Schedule
}

internal fun EditorTimeRole.iconTestTag(): String =
    "editor-time-icon-${name.lowercase()}"

@Composable fun TitleField(value: String, onChange: (String) -> Unit) =
    EditorInput(value, onChange)

@Composable fun NoteField(value: String, onChange: (String) -> Unit) =
    EditorInput(value, onChange, description = true)

@Composable fun LocationField(value: String, onChange: (String) -> Unit) =
    LocationTool(value, onChange)

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) =
    AppTextField(
        value = value,
        onValueChange = { candidate -> onChange(candidate.filter(Char::isDigit)) },
        label = label,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    )

@Composable
fun DateField(label: String, value: LocalDate?, onClick: () -> Unit, onClear: (() -> Unit)? = null) {
    EditorTimeTool(
        Icons.Outlined.CalendarMonth,
        label,
        value?.format(dateFormatter),
        onClick,
        onClear = onClear.takeIf { value != null },
    )
}

@Composable
fun TimeField(
    label: String,
    value: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    EditorTimeTool(
        Icons.Outlined.Schedule,
        label,
        value?.format(timeFormatter),
        onClick,
        modifier,
        onClear.takeIf { value != null },
    )
}

@Composable
fun DateTimeField(
    label: String,
    role: EditorTimeRole,
    date: LocalDate?,
    time: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    valueStyle: TextStyle = AppTheme.type.editorDescription,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val value = when {
        date == null -> "未选择"
        time == null -> date.format(dateFormatter)
        else -> "${date.format(dateFormatter)} ${time.format(timeFormatter)}"
    }
    PickerField(
        label,
        role,
        value,
        onClick,
        modifier,
        onClear.takeIf { date != null },
        valueStyle,
        trailingContent,
    )
}

@Composable
fun PickerField(
    label: String,
    role: EditorTimeRole,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    valueStyle: TextStyle = AppTheme.type.editorDescription,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    EditorTimeTool(
        icon = editorTimeIcon(role),
        iconTestTag = role.iconTestTag(),
        label = label,
        summary = value.takeUnless { it == "未选择" || it == "未填写" },
        onClick = onClick,
        modifier = modifier,
        onClear = onClear,
        textStyle = valueStyle,
        trailingContent = trailingContent,
    )
}

/** Compact one-line entry backed by the existing combined date/time picker. */
@Composable
fun SplitDateTimeField(
    label: String,
    role: EditorTimeRole,
    date: LocalDate?,
    time: LocalTime?,
    picker: io.github.peanutpersimmon.rilo.ui.components.picker.PickerHostState,
    modifier: Modifier = Modifier,
    onDateChange: (LocalDate?) -> Unit = {},
    onTimeChange: (LocalTime?) -> Unit = {},
    onClear: (() -> Unit)? = null,
    maxDateTime: LocalDateTime? = null,
    onDateTimeChange: ((LocalDate?, LocalTime?) -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    DateTimeField(
        label = label,
        role = role,
        date = date,
        time = time,
        modifier = modifier,
        onClick = {
            picker.show(
                initialDate = date ?: LocalDate.now(),
                initialTime = time,
                timeOptional = true,
                maxDateTime = maxDateTime,
            ) { selectedDate, selectedTime ->
                if (onDateTimeChange != null) onDateTimeChange(selectedDate, selectedTime)
                else {
                    onDateChange(selectedDate)
                    onTimeChange(selectedTime)
                }
            }
        },
        onClear = onClear,
        trailingContent = trailingContent,
    )
}
