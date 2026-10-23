package io.github.peanutpersimmon.rilo.domain.service

object SleepReportFileName {
    fun forReport(report: SleepReport): String = when (report) {
        is SleepMonthlyReport -> "RILO_Sleep_Monthly_%04d_%02d.png".format(report.month.year, report.month.monthValue)
        is SleepAnnualReport -> "RILO_Sleep_Annual_%04d.png".format(report.year)
    }
}
