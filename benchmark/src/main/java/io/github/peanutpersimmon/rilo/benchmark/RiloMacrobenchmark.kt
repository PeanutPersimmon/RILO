package io.github.peanutpersimmon.rilo.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

private const val TARGET_PACKAGE = "io.github.peanutpersimmon.rilo.benchmarktarget"
private const val UI_TIMEOUT = 8_000L
private const val STARTUP_ITERATIONS = 5
private const val TRACE_SETTLE_MILLIS = 250L
private const val NAVIGATION_CAPTURE_MILLIS = 500L
private const val ACTIVE_SESSION_CAPTURE_MILLIS = 12_000L
private const val STATISTICS_ITERATIONS = 3

@RunWith(AndroidJUnit4::class)
class RiloMacrobenchmark {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStart() = startup {
        waitForDescription("新增")
    }

    @Test
    fun todayFirstContent() = startup {
        waitForDescription("新增")
    }

    @Test
    fun todayToPlan() = navigation("计划")

    @Test
    fun planToStatistics() = navigation("统计", startAt = "计划")

    @Test
    fun statisticsToSettings() = navigation("设置", startAt = "统计")

    @Test
    fun settingsToToday() = navigation("今天", startAt = "设置")

    @Test
    fun activeSessionFrames() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startTargetAndWait()
            clickDescription("今天")
            ensureActiveRecord()
        },
    ) {
        waitForDescription("新增")
        Thread.sleep(TRACE_SETTLE_MILLIS)
        // Let the production second ticker drive sustained, otherwise idle Today frames.
        Thread.sleep(ACTIVE_SESSION_CAPTURE_MILLIS)
    }

    @Test
    fun planMonthFrames() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startTargetAndWait()
            clickDescription("计划")
            clickText("日视图")
            clickDescription("今天")
        },
    ) {
        Thread.sleep(TRACE_SETTLE_MILLIS)
        clickDescription("计划")
        clickText("月视图")
        Thread.sleep(NAVIGATION_CAPTURE_MILLIS)
        device.waitForIdle()
    }

    @Test
    fun statisticsYearAndSleepChartFrames() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = STATISTICS_ITERATIONS,
        setupBlock = {
            pressHome()
            startTargetAndWait()
            clickDescription("统计")
            clickText("分类")
            clickText("年")
            waitForText("总实际时长")
        },
    ) {
        clickText("睡眠")
        waitForText("日历")
        clickText("统计")
        waitForText("起床时间分布")
        clickText("入睡")
        waitForText("入睡时间分布")
        clickText("小憩")
        waitForText("小憩时长分布")
        clickText("起床")
        waitForText("起床时间分布")
        clickText("分类")
        waitForText("总实际时长")
        // A second real navigation/metric cycle increases frame coverage without animation or taps on selected tabs.
        clickText("睡眠")
        waitForText("起床时间分布")
        clickText("入睡")
        waitForText("入睡时间分布")
        clickText("小憩")
        waitForText("小憩时长分布")
        clickText("起床")
        waitForText("起床时间分布")
        clickText("分类")
        waitForText("总实际时长")
        device.waitForIdle()
    }

    private fun startup(afterStart: MacrobenchmarkScope.() -> Unit) = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = STARTUP_ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        afterStart()
    }

    private fun navigation(destination: String, startAt: String? = null) = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            clickDescription(startAt ?: "今天")
        },
    ) {
        Thread.sleep(TRACE_SETTLE_MILLIS)
        clickDescription(destination)
        if (destination == "设置") {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 2,
                12,
            )
        }
        Thread.sleep(NAVIGATION_CAPTURE_MILLIS)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.clickDescription(description: String) {
        repeat(3) {
            val target = device.wait(Until.findObject(By.desc(description)), UI_TIMEOUT)
                ?: error("Missing content description: $description")
            try {
                target.click()
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
        error("Stale content-description target after retries: $description")
    }

    private fun MacrobenchmarkScope.clickText(text: String) {
        repeat(3) {
            val target = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT)
                ?: error("Missing text: $text")
            try {
                target.click()
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
        error("Stale text target after retries: $text")
    }

    private fun MacrobenchmarkScope.waitForDescription(description: String) {
        check(device.wait(Until.hasObject(By.desc(description)), UI_TIMEOUT)) {
            "Missing content description: $description"
        }
    }

    private fun MacrobenchmarkScope.waitForText(text: String) {
        check(device.wait(Until.hasObject(By.text(text)), UI_TIMEOUT)) {
            "Missing text: $text"
        }
    }

    private fun MacrobenchmarkScope.startTargetAndWait() {
        repeat(3) { attempt ->
            try {
                startActivityAndWait()
            } catch (error: IllegalStateException) {
                if (device.hasObject(By.pkg(TARGET_PACKAGE))) return
                if (attempt == 2) throw error
            }
            if (device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 2_000L)) return
        }
        error("Benchmark target did not become foreground: $TARGET_PACKAGE")
    }

    private fun MacrobenchmarkScope.ensureActiveRecord() {
        if (device.hasObject(By.text(Pattern.compile("\\d{2}:\\d{2}:\\d{2}")))) return
        clickDescription("新增")
        clickText("新建记录")
        val title = device.wait(Until.findObject(By.clazz("android.widget.EditText")), UI_TIMEOUT)
            ?: error("Record title field was not found")
        title.text = "Benchmark Active"
        clickDescription("现在开始")
        device.waitForIdle()
    }
}
