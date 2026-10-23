package io.github.peanutpersimmon.rilo.domain.model

import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import java.time.LocalDate

enum class BoundarySource {
    REAL,
    DEFAULT,
    NOW,
}

/** Display-only awake window. Real sleep facts remain Session records. */
data class AwakeDisplayWindow(
    val anchorDate: LocalDate,
    val displayInterval: TimeInterval,
    val actualWakeAtMillis: Long?,
    val actualSleepAtMillis: Long?,
    val startSource: BoundarySource,
    val endSource: BoundarySource,
    val actualWakeSessionId: Long? = null,
    val actualSleepSessionId: Long? = null,
) {
    init {
        require(startSource != BoundarySource.NOW) { "An awake window cannot start at NOW." }
        require(startSource != BoundarySource.REAL || actualWakeAtMillis == displayInterval.startMillis)
        require(endSource != BoundarySource.REAL || actualSleepAtMillis == displayInterval.endMillis)
        require(startSource == BoundarySource.REAL || actualWakeAtMillis == null)
        require(endSource == BoundarySource.REAL || actualSleepAtMillis == null)
    }
}
