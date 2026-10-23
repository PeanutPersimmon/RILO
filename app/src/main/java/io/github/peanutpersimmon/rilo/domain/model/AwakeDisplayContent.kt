package io.github.peanutpersimmon.rilo.domain.model

import java.time.LocalDate

/** A display-relevant time span whose owner date is determined before axis resolution. */
data class AwakeDisplayContent(
    val ownerDate: LocalDate,
    val startAtMillis: Long,
    val endAtMillis: Long = startAtMillis,
) {
    init {
        require(endAtMillis >= startAtMillis)
    }
}
