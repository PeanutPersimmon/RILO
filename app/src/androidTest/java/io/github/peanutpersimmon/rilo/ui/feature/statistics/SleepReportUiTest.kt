package io.github.peanutpersimmon.rilo.ui.feature.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.peanutpersimmon.rilo.domain.service.SleepMonthlyReport
import io.github.peanutpersimmon.rilo.domain.service.SleepReportDay
import io.github.peanutpersimmon.rilo.domain.service.SleepReportMetrics
import io.github.peanutpersimmon.rilo.ui.feature.settings.SleepReportSettingsRows
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.SleepMonthlyReportContent
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.MonthlyReportWheels
import io.github.peanutpersimmon.rilo.ui.feature.today.TodayHeader
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SleepReportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsHasSeparateMonthlyAndAnnualRows() {
        var monthly = 0
        var annual = 0
        compose.setContent { RidoTheme {
            Column { SleepReportSettingsRows({ monthly++ }, { annual++ }) }
        } }
        compose.onNodeWithText("月度报告").performClick()
        compose.onNodeWithText("年度报告").performClick()
        compose.runOnIdle { assertEquals(1, monthly); assertEquals(1, annual) }
    }

    @Test fun sleepStatisticsDoesNotShowReportEntry() {
        compose.setContent { RidoTheme {
            SleepStatisticsScreen(YearMonth.of(2026, 10), emptyList(), emptyList(), {},)
        } }
        compose.onNodeWithText("报告").assertDoesNotExist()
    }

    @Test fun todayHeaderCallsTheCreateSleepActionNewSleep() {
        compose.setContent { RidoTheme { TodayHeader("15\n周四", {}, {}) } }
        compose.onNodeWithContentDescription("新增睡眠").assertExists()
        compose.onNodeWithContentDescription("补录睡眠").assertDoesNotExist()
    }

    @Test fun monthlyWheelsShowIndependentYearAndMonthLabelsWithoutFutureMonths() {
        compose.setContent { RidoTheme {
            var selected by remember { mutableStateOf(YearMonth.of(2026, 10)) }
            MonthlyReportWheels(selected, listOf(2024, 2025, 2026), YearMonth.of(2026, 10)) {
                selected = it
            }
        } }
        compose.onNode(hasText("2026年") and hasAnyAncestor(hasTestTag("report-year-wheel"))).assertExists()
        compose.onNode(hasText("10月") and hasAnyAncestor(hasTestTag("report-month-wheel"))).assertExists()
        compose.onAllNodes(hasText("2026年") and hasAnyAncestor(hasTestTag("report-month-wheel")))
            .assertCountEquals(0)
        compose.onAllNodes(hasText("11月") and hasAnyAncestor(hasTestTag("report-month-wheel")))
            .assertCountEquals(0)
    }

    @Test fun monthlyContentKeepsSleepSectionsAndFourCalendarsWithoutSubjectiveSections() {
        val metrics = SleepReportMetrics(31, 1, 1, 0, 30, 1410, 450, 480,
            emptyList(), 1, 1, 30, emptyList())
        val day = SleepReportDay(LocalDate.of(2026, 10, 1), 1410, 450, 480, 30)
        val report = SleepMonthlyReport(YearMonth.of(2026, 10), metrics, listOf(day), listOf("记录了 1 天夜间睡眠。"))
        compose.setContent { RidoTheme { SleepMonthlyReportContent(report, exportMode = false) } }
        listOf("本月概览", "作息趋势", "夜间睡眠时长", "午睡趋势", "睡眠结构", "本月日历回顾",
            "入睡日历", "起床日历", "时长日历", "午睡日历", "本月总结与小建议").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        listOf("醒来状态", "做梦", "噩梦", "夜醒").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
        compose.onNodeWithText("未记录夜晚", substring = true).assertDoesNotExist()
    }
}
