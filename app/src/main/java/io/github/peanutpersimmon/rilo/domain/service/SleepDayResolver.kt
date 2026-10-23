package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.Session
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A completed sleep belongs to its wake day; an open sleep belongs to its bed day. */
object SleepDayResolver {
    fun resolve(session: Session, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(session.endAtMillis ?: session.startAtMillis).atZone(zone).toLocalDate()
}
