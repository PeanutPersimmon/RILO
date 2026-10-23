package io.github.peanutpersimmon.rilo.ui.feature.settings.report

import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual.AnnualReportImageRenderer
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual.AnnualSleepReport
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnualSleepReportRendererTest {
    @Test fun annualPosterKeepsSleepFixedSizeWithoutSubjectiveData() {
        val report = AnnualSleepReport(2026, 1, 1, 1f, "23:30", "07:30", "8小时0分",
            emptyList(), 1, 1, "30分钟", emptyList(), listOf("平均入睡 23:30"), listOf("记录了 1 天。"))
        val image = AnnualReportImageRenderer.renderAnnualReportBitmap(
            InstrumentationRegistry.getInstrumentation().targetContext, report)
        try {
            assertEquals(1080, image.width)
            assertEquals(2400, image.height)
        } finally { image.recycle() }
    }
}
