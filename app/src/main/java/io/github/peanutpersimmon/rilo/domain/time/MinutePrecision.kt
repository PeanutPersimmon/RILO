package io.github.peanutpersimmon.rilo.domain.time

import io.github.peanutpersimmon.rilo.domain.model.Session

private const val MILLIS_PER_MINUTE = 60_000L

/** Converts an instant to the minute precision used by Session editors. */
fun Long.toMinutePrecision(): Long = this - Math.floorMod(this, MILLIS_PER_MINUTE)

fun Long.isMinutePrecision(): Boolean = Math.floorMod(this, MILLIS_PER_MINUTE) == 0L

fun Session.withMinutePrecision(): Session = copy(
    startAtMillis = startAtMillis.toMinutePrecision(),
    endAtMillis = endAtMillis?.toMinutePrecision(),
)
