package io.github.peanutpersimmon.rilo.ui.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.domain.service.StatisticsPeriod
import io.github.peanutpersimmon.rilo.ui.components.CompactCapsule
import io.github.peanutpersimmon.rilo.ui.components.CompactDateNavigation
import io.github.peanutpersimmon.rilo.ui.components.PageNavBand
import io.github.peanutpersimmon.rilo.ui.components.SegmentedControlLevel
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
internal fun <T> StatisticsTextTabs(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    level: SegmentedControlLevel = SegmentedControlLevel.SECONDARY,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.pageHorizontal),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
    ) {
        items.forEach { item ->
            CompactCapsule(
                label(item),
                item == selected,
                { onSelect(item) },
                Modifier.weight(1f),
                level,
            )
        }
    }
}

@Composable
internal fun StatisticsPeriodNavigation(
    period: StatisticsPeriod,
    onGranularity: (StatisticsGranularity) -> Unit,
    onShift: (Long) -> Unit,
    onDateClick: () -> Unit,
) {
    // The granularity tabs occupy the band's reserved level-two slot and the date row the
    // date slot, so this band is exactly navBandHeight tall and matches Plan's band.
    PageNavBand(
        secondary = {
            StatisticsTextTabs(
                StatisticsGranularity.entries,
                period.granularity,
                { it.label },
                onGranularity,
            )
        },
        dateNavigation = {
            CompactDateNavigation(
                period.compactLabel(),
                { onShift(-1) },
                { onShift(1) },
                onDateClick,
                Modifier.padding(horizontal = AppTheme.dimens.pageHorizontal),
            )
        },
    )
}

private fun StatisticsPeriod.compactLabel(): String {
    val first = start
    return when (granularity) {
        StatisticsGranularity.DAY ->
            first.year.toString() + "." + first.monthValue + "." + first.dayOfMonth
        StatisticsGranularity.WEEK -> {
            val last = endExclusive.minusDays(1)
            val prefix = first.year.toString() + "." + first.monthValue + "." +
                first.dayOfMonth + "–"
            prefix + (if (first.year == last.year) "" else last.year.toString() + ".") +
                last.monthValue + "." + last.dayOfMonth
        }
        StatisticsGranularity.MONTH -> first.year.toString() + "." + first.monthValue
        StatisticsGranularity.YEAR -> first.year.toString()
    }
}
