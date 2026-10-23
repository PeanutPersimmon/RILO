package io.github.peanutpersimmon.rilo.ui.feature.timer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.presentation.timer.TimerElapsedUiModel
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun FlipClock(
    elapsed: TimerElapsedUiModel,
    modifier: Modifier = Modifier,
) {
    val accessibilityLabel = stringResource(
        R.string.timer_elapsed_accessibility,
        elapsed.hoursText.toLongOrNull() ?: 0L,
        elapsed.minutesText.toIntOrNull() ?: 0,
        elapsed.secondsText.toIntOrNull() ?: 0,
    )
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.timerDigitGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DigitGroup(elapsed.hoursText)
        ClockSeparator()
        DigitGroup(elapsed.minutesText)
        ClockSeparator()
        DigitGroup(elapsed.secondsText)
    }
}

@Composable
private fun DigitGroup(value: String) {
    value.forEach { digit -> FlipDigit(digit) }
}

@Composable
private fun ClockSeparator() {
    Text(
        text = ":",
        color = AppTheme.colors.timerSecondaryText,
        style = AppTheme.type.number.copy(fontFamily = FontFamily.Monospace),
    )
}

