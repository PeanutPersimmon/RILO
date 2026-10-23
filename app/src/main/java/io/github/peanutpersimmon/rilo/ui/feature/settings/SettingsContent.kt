package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.theme.*
import io.github.peanutpersimmon.rilo.ui.components.PageHeader
import io.github.peanutpersimmon.rilo.ui.components.AppDialog
import io.github.peanutpersimmon.rilo.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rilo.ui.feature.categories.CategoryManagementSheet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rilo.presentation.settings.QwenSettingsViewModel
import io.github.peanutpersimmon.rilo.presentation.settings.SleepReportPeriod
import io.github.peanutpersimmon.rilo.presentation.settings.SleepReportViewModel
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.SleepReportSettingsDialogs
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.peanutpersimmon.rilo.reminder.ReminderRuntime
import io.github.peanutpersimmon.rilo.reminder.RidoApplication
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.peanutpersimmon.rilo.backup.BackupUiPhase
import io.github.peanutpersimmon.rilo.backup.BackupViewModel

internal enum class NotificationRowAction { REQUEST_PERMISSION, OPEN_SYSTEM_SETTINGS }

internal fun notificationRowAction(sdk: Int, permissionGranted: Boolean,
    requestedBefore: Boolean): NotificationRowAction =
    if (sdk >= 33 && !permissionGranted && !requestedBefore)
        NotificationRowAction.REQUEST_PERMISSION else NotificationRowAction.OPEN_SYSTEM_SETTINGS

internal enum class ExactAlarmRowAction { SPECIAL_ACCESS, APP_DETAILS }
internal fun exactAlarmRowAction(sdk: Int, granted: Boolean): ExactAlarmRowAction =
    if (sdk >= 31) ExactAlarmRowAction.SPECIAL_ACCESS else ExactAlarmRowAction.APP_DETAILS

@Composable
fun SettingsContent(mode: ThemeMode, onNight: (Boolean) -> Unit,
    qwenSettingsProvider: () -> QwenSettingsViewModel,
    planViewModel: PlanViewModel,
    sleepReportViewModelProvider: () -> SleepReportViewModel,
    modifier: Modifier = Modifier) {
    val qwenSettings = remember(qwenSettingsProvider) { qwenSettingsProvider() }
    var sleepReportViewModel by remember { mutableStateOf<SleepReportViewModel?>(null) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var aiDiagnosticsOpen by remember { mutableStateOf(false) }
    var qwenConfigOpen by remember { mutableStateOf(false) }
    var planOrderOpen by remember { mutableStateOf(false) }
    var exportNotice by remember { mutableStateOf(false) }
    var typedConfirmation by remember { mutableStateOf("") }
    var aboutOpen by remember { mutableStateOf(false) }
    val qwen by qwenSettings.uiState.collectAsStateWithLifecycle()
    val backupViewModel: BackupViewModel = viewModel()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        uri -> uri?.let(backupViewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> uri?.let(backupViewModel::prepare)
    }
    LaunchedEffect(backup.phase) {
        if (backup.phase != BackupUiPhase.AWAIT_TYPED_CONFIRM) typedConfirmation = ""
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionTick by remember { mutableIntStateOf(0) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick++
                (context.applicationContext as? RidoApplication)?.let { app -> ReminderRuntime.rebuild(app) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionTick++
        (context.applicationContext as? RidoApplication)?.let { app -> ReminderRuntime.rebuild(app) }
    }
    fun openNotificationSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationRequested = true
        permissionTick++
        (context.applicationContext as? RidoApplication)?.let { app -> ReminderRuntime.rebuild(app) }
        if (!granted && (context as? Activity)?.let { activity ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.POST_NOTIFICATIONS)
            } == true) {
            openNotificationSettings()
        }
    }
    val notificationsGranted = permissionTick.let {
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val exactGranted = permissionTick.let {
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val navigationClearance = LocalFloatingNavigationClearance.current
    Column(modifier.fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(bottom = navigationClearance)
        .padding(top = AppTheme.dimens.pageHeaderTop),
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
        PageHeader { Text("设置", style = AppTheme.type.pageTitle) }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AppTheme.dimens.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
        ) {
            SettingGroup {
                Row(Modifier.fillMaxWidth().height(AppTheme.dimens.settingsRowHeight)
                    .padding(start = AppTheme.dimens.spaceM, end = AppTheme.dimens.spaceS),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("外观", Modifier.weight(1f), style = AppTheme.type.secondary)
                    ModeIcon(Icons.Outlined.LightMode, "日间", mode == ThemeMode.Light) { onNight(false) }
                    ModeIcon(Icons.Outlined.DarkMode, "夜间", mode == ThemeMode.Dark) { onNight(true) }
                }
                SettingDivider()
                SettingRow("计划视图顺序") { planOrderOpen = true }
            }
            SettingGroup {
                SettingRow("通知权限", status = if (notificationsGranted) "已开启" else "未开启") {
                    val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    when (notificationRowAction(Build.VERSION.SDK_INT, granted, notificationRequested)) {
                        NotificationRowAction.REQUEST_PERMISSION ->
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        NotificationRowAction.OPEN_SYSTEM_SETTINGS -> openNotificationSettings()
                    }
                }
                SettingDivider()
                SettingRow("准时提醒", status = if (exactGranted) "已允许" else "未允许") {
                    val intent = if (exactAlarmRowAction(Build.VERSION.SDK_INT, exactGranted) ==
                        ExactAlarmRowAction.SPECIAL_ACCESS)
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    try { exactLauncher.launch(intent) } catch (_: android.content.ActivityNotFoundException) {
                        exactLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    }
                }
            }
            SettingGroup {
                SettingRow("分类与关键词") { categoriesOpen = true }
            }
            SettingGroup {
                SettingRow("导出数据", enabled = backup.phase == BackupUiPhase.IDLE) { exportNotice = true }
                SettingDivider()
                SettingRow("导入数据", enabled = backup.phase == BackupUiPhase.IDLE) {
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
                backup.message?.let { message ->
                    Text(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = AppTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingGroup {
                SleepReportSettingsRows(
                    onMonthly = {
                        sleepReportViewModelProvider().also { sleepReportViewModel = it }
                            .open(SleepReportPeriod.MONTHLY)
                    },
                    onAnnual = {
                        sleepReportViewModelProvider().also { sleepReportViewModel = it }
                            .open(SleepReportPeriod.ANNUAL)
                    },
                )
            }
            SettingGroup {
                SettingRow("AI 配置", status = if (qwen.configured) "已配置" else "未配置") {
                    qwenSettings.refresh()
                    qwenConfigOpen = true
                }
                SettingDivider()
                SettingRow("AI 诊断") { aiDiagnosticsOpen = true }
            }
            SettingGroup {
    SettingRow("关于") {
        aboutOpen = true
    }
}

        }
    }
    if (categoriesOpen) CategoryManagementSheet { categoriesOpen = false }
    if (planOrderOpen) PlanViewOrderSheet(planViewModel) { planOrderOpen = false }
    if (qwenConfigOpen) QwenConfigSheet(qwenSettings) { qwenConfigOpen = false }
    if (aiDiagnosticsOpen) AiDiagnosticsDialog { aiDiagnosticsOpen = false }
    sleepReportViewModel?.let { SleepReportSettingsDialogs(it) }
    if (exportNotice) AppDialog("导出数据", { exportNotice = false }, actions = {
        TextButton(onClick = { exportNotice = false }) { Text("取消") }
        TextButton(onClick = {
            exportNotice = false
            exportLauncher.launch(backupViewModel.suggestedFileName())
        }) { Text("选择保存位置") }
    }) { Text("备份包含日程、记录和睡眠等个人数据，请妥善保管。") }
    if (backup.phase == BackupUiPhase.AWAIT_FIRST_CONFIRM) AppDialog(
        "覆盖当前数据？", backupViewModel::cancelImport, actions = {
            TextButton(onClick = backupViewModel::cancelImport) { Text("取消") }
            TextButton(onClick = backupViewModel::continueImport) { Text("继续") }
        }) {
        Text("导入将覆盖当前所有 RILO 数据，包括日程、记录、睡眠、分类、关键词规则及提醒设置。")
    }
    if (backup.phase == BackupUiPhase.AWAIT_TYPED_CONFIRM) AppDialog(
        "确认导入", backupViewModel::cancelImport, actions = {
            TextButton(onClick = backupViewModel::cancelImport) { Text("取消") }
            TextButton(onClick = { backupViewModel.confirmImport(typedConfirmation) },
                enabled = typedConfirmation == "确认导入") { Text("导入并覆盖") }
        }) {
        Column {
            Text("请输入“确认导入”以覆盖当前 RILO 数据。")
            OutlinedTextField(value = typedConfirmation, onValueChange = { typedConfirmation = it },
                singleLine = true, label = { Text("确认导入") })
        }
    }
    if (backup.phase == BackupUiPhase.RESTART_REQUIRED || backup.phase == BackupUiPhase.RESTART_LAUNCHING) {
        AppDialog(
            title = if (backup.message == "导入成功") "导入成功" else "导入完成",
            onDismissRequest = {},
            actions = {
                TextButton(onClick = backupViewModel::restartNow,
                    enabled = backup.phase == BackupUiPhase.RESTART_REQUIRED) { Text("重新启动") }
            },
        ) { Text(backup.message ?: "请重新启动 RILO。") }
    }
    if (aboutOpen) {
        AboutDialog(
            onDismiss = { aboutOpen = false }
        )
    }
}

@Composable
internal fun SleepReportSettingsRows(onMonthly: () -> Unit, onAnnual: () -> Unit) {
    Text("睡眠报告", Modifier.padding(start = AppTheme.dimens.spaceM, top = AppTheme.dimens.spaceS),
        style = AppTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingRow("月度报告", onClick = onMonthly)
    SettingDivider()
    SettingRow("年度报告", onClick = onAnnual)
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column( content = content)
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
internal fun SettingRow(
    label: String,
    status: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(AppTheme.dimens.settingsRowHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppTheme.dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, Modifier.weight(1f), style = AppTheme.type.secondary,
            color = MaterialTheme.colorScheme.onSurface)
        status?.let {
            Text(it, style = AppTheme.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun ModeIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(AppTheme.dimens.settingsRowHeight)
        .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
        .semantics { this.selected = selected; role = Role.RadioButton },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(AppTheme.dimens.spaceXl).then(
            if (selected) Modifier.background(colors.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label,
                tint = if (selected) colors.surface else colors.onSurfaceVariant,
                modifier = Modifier.size(AppTheme.dimens.icon))
        }
    }
}

