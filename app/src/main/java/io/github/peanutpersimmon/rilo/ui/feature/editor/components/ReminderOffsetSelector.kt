package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.AppDialog
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalTime

internal fun reminderBellVisible(date: LocalDate?, time: LocalTime?): Boolean =
    date != null && time != null

private val options = listOf(
    null to "不提醒", 0 to "准时", 5 to "提前 5 分钟", 10 to "提前 10 分钟",
    30 to "提前 30 分钟", 60 to "提前 1 小时",
)

internal fun reminderOffsetLabel(value: Int?): String =
    options.firstOrNull { it.first == value }?.second ?: "自定义 · $value 分钟"

@Composable
fun ReminderBell(label: String, value: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    var customInput by remember(value, customOpen) { mutableStateOf(value?.toString().orEmpty()) }
    var explainPermission by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        if (it) (context.applicationContext as? io.github.peanutpersimmon.rilo.reminder.RidoApplication)?.let { app ->
            io.github.peanutpersimmon.rilo.reminder.ReminderRuntime.rebuild(app)
        }
    }
    fun choose(offset: Int?) {
        onChange(offset)
        permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (value == null && offset != null && !permissionGranted && Build.VERSION.SDK_INT >= 33)
            explainPermission = true
    }
    Row(Modifier.testTag("reminder-bell-${label}-${if (value == null) "off" else "on"}")
        .clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
        if (value != null && options.none { it.first == value }) {
            Text(reminderOffsetLabel(value), Modifier.widthIn(max = 96.dp), style = AppTheme.type.caption,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(if (value == null) Icons.Outlined.NotificationsNone else Icons.Filled.Notifications,
                contentDescription = "$label：${reminderOffsetLabel(value)}",
                tint = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppTheme.dimens.icon))
        }
    }
    if (open) AppBottomSheet(label, { open = false },
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.BodyTextRow) {
        Column {
            if (value != null && !permissionGranted) Text("提醒已保存，但通知权限未开启",
                Modifier.padding(horizontal = 12.dp), style = AppTheme.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            options.forEach { (offset, text) ->
                ChoiceRow(text, value == offset, sheetStyle = true) {
                    open = false
                    choose(offset)
                }
            }
            ChoiceRow(if (value != null && options.none { it.first == value })
                reminderOffsetLabel(value) else "自定义提前时间",
                value != null && options.none { it.first == value }, sheetStyle = true) {
                open = false
                customOpen = true
            }
        }
    }
    if (customOpen) AppDialog("自定义提前时间", { customOpen = false }, actions = {
        androidx.compose.material3.TextButton(onClick = { customOpen = false }) { Text("取消") }
        androidx.compose.material3.TextButton(
            onClick = {
                val minutes = customInput.toIntOrNull()
                if (minutes != null && minutes >= 0) {
                    customOpen = false
                    choose(minutes)
                }
            },
            enabled = customInput.toIntOrNull()?.let { it >= 0 } == true,
        ) { Text("确定") }
    }) {
        OutlinedTextField(customInput, onValueChange = { customInput = it.filter(Char::isDigit) },
            label = { Text("提前分钟数") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    if (explainPermission) AppDialog("开启通知权限", { explainPermission = false }, actions = {
        androidx.compose.material3.TextButton(onClick = { explainPermission = false }) { Text("稍后") }
        androidx.compose.material3.TextButton(onClick = {
            explainPermission = false
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("继续") }
    }) { Text("允许 RILO 在提醒时间发送通知。即使暂不允许，提醒设置也会保存。") }
}
