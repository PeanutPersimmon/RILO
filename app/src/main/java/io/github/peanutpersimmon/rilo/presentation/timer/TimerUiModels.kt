package io.github.peanutpersimmon.rilo.presentation.timer

data class TimerElapsedUiModel(
    val elapsedMillis: Long,
    val hoursText: String,
    val minutesText: String,
    val secondsText: String,
) {
    val clockText: String = "$hoursText:$minutesText:$secondsText"
    val digits: List<Char> = hoursText.toList() + minutesText.toList() + secondsText.toList()
}

fun elapsedFrom(startAtMillis: Long, nowMillis: Long): TimerElapsedUiModel {
    val elapsedMillis = (nowMillis - startAtMillis).coerceAtLeast(0L)
    val totalSeconds = elapsedMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return TimerElapsedUiModel(
        elapsedMillis = elapsedMillis,
        hoursText = hours.toString().padStart(2, '0'),
        minutesText = minutes.toString().padStart(2, '0'),
        secondsText = seconds.toString().padStart(2, '0'),
    )
}

data class FlipDigitTransition(
    val previous: Char,
    val current: Char,
) {
    val shouldFlip: Boolean = previous != current
}

