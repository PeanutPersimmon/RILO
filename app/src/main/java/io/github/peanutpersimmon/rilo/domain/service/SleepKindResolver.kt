package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import java.time.ZoneId

/** One decision point for the subtype of an already selected Sleep intent. */
object SleepKindResolver {
    fun explicitKind(sourceText: String): SessionKind? {
        val nap = listOf("午睡", "小睡", "小憩").any(sourceText::contains)
        val night = listOf("昨晚", "夜间", "夜里").any(sourceText::contains)
        return when {
            nap && !night -> SessionKind.NAP
            night && !nap -> SessionKind.NIGHT_SLEEP
            else -> null
        }
    }

    fun resolve(
        modelKind: SessionKind?,
        startAtMillis: Long?,
        endAtMillis: Long?,
        zoneId: ZoneId,
        sourceText: String = "",
        userKind: SessionKind? = null,
    ): SessionKind? {
        require(modelKind == null || modelKind != SessionKind.ACTIVITY)
        require(userKind == null || userKind != SessionKind.ACTIVITY)
        userKind?.let { return it }
        explicitKind(sourceText)?.let { return it }
        if (startAtMillis != null && endAtMillis != null && endAtMillis > startAtMillis)
            return SleepCheckInPolicy.inferKind(startAtMillis, endAtMillis, zoneId)
        return modelKind
    }

    /** Room v1 requires a concrete kind for an open sleep interval. Finish resolves it again. */
    fun activeStorageKind(resolved: SessionKind?): SessionKind = resolved ?: SessionKind.NIGHT_SLEEP
}
