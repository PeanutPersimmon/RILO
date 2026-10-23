package io.github.peanutpersimmon.rilo.ui.feature.settings.report

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.presentation.settings.SleepReportPeriod
import io.github.peanutpersimmon.rilo.presentation.settings.SleepReportViewModel
import io.github.peanutpersimmon.rilo.presentation.settings.reportMonthForYear
import io.github.peanutpersimmon.rilo.presentation.settings.reportSelectableMonths
import io.github.peanutpersimmon.rilo.presentation.settings.reportSelectableYears
import io.github.peanutpersimmon.rilo.ui.components.picker.WheelPicker
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun SleepReportSettingsDialogs(viewModel: SleepReportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val period = state.period ?: return
    val current = YearMonth.now()
    val years = reportSelectableYears(state.earliestYear, current)

    Dialog(onDismissRequest = { if (!saving) viewModel.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            Surface(Modifier.width(320.dp).wrapContentHeight(), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (period == SleepReportPeriod.MONTHLY) "月度报告" else "年度报告",
                        style = MaterialTheme.typography.titleLarge, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    if (period == SleepReportPeriod.MONTHLY) {
                        MonthlyReportWheels(state.month, years, current, viewModel::month)
                    } else {
                        WheelPicker(years, state.year, viewModel::year, { "${it}年" },
                            Modifier.fillMaxWidth())
                    }
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        state.error != null -> Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::close, enabled = !saving) { Text("取消") }
                        Button(onClick = {
                            val report = state.report ?: return@Button
                            saving = true
                            scope.launch {
                                val result = runCatching {
                                    val bitmap = renderSleepReportBitmap(context, report)
                                    try { saveSleepReportBitmap(context, bitmap, report).getOrThrow() }
                                    finally { bitmap.recycle() }
                                }
                                saving = false
                                Toast.makeText(context, if (result.isSuccess) "已保存报告图片"
                                    else "导出失败：${result.exceptionOrNull()?.message ?: "请重试"}", Toast.LENGTH_SHORT).show()
                                if (result.isSuccess) viewModel.close()
                            }
                        }, enabled = state.report != null && !saving,
                            modifier = Modifier.height(AppTheme.dimens.icon + AppTheme.dimens.spaceS),
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.spaceS, vertical = 0.dp),
                            shape = AppTheme.shapes.pill) {
                            Text(if (saving) "导出中" else "导出", style = AppTheme.type.editorDescription)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MonthlyReportWheels(
    selected: YearMonth,
    yearOptions: List<Int>,
    current: YearMonth,
    onSelected: (YearMonth) -> Unit,
) {
    val monthOptions = reportSelectableMonths(selected.year, current)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        key("monthly-report-year", selected.year) {
            WheelPicker(
                values = yearOptions,
                selected = selected.year,
                onSelected = { year -> onSelected(reportMonthForYear(selected, year, current)) },
                label = { year -> "${year}年" },
                modifier = Modifier.weight(1f).testTag("report-year-wheel"),
            )
        }
        key("monthly-report-month", selected.monthValue, monthOptions) {
            WheelPicker(
                values = monthOptions,
                selected = selected.monthValue,
                onSelected = { month -> onSelected(YearMonth.of(selected.year, month)) },
                label = { month -> "${month}月" },
                modifier = Modifier.weight(1f).testTag("report-month-wheel"),
            )
        }
    }
}
