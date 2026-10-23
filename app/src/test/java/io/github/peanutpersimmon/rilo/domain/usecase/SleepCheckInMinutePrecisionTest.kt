package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import java.lang.reflect.Proxy
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCheckInMinutePrecisionTest {
    @Test
    fun widgetClockInstantsAreStoredAtEditorMinutePrecision() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val rawStart = 37_456L
        val rawWake = 10 * 60 * 60 * 1_000L + 50 * 60 * 1_000L + 37_456L
        var started: Session? = null
        var finishedAt: Long? = null
        val repository = Proxy.newProxyInstance(
            RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "startSessionIfIdle" -> {
                    started = arguments!![0] as Session
                    DomainResult.Success(7L)
                }
                "finishSleepCheckIn" -> {
                    finishedAt = arguments!![0] as Long
                    DomainResult.Success(requireNotNull(started).copy(id = 7, endAtMillis = finishedAt))
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository

        assertTrue(StartSleepCheckInUseCase(repository) { rawStart }() is DomainResult.Success)
        assertTrue(FinishSleepCheckInUseCase(repository, { rawWake }, { zone })() is DomainResult.Success)

        assertEquals(0L, started?.startAtMillis)
        assertEquals(0L, started?.createdAtMillis)
        assertEquals(rawWake.toMinutePrecision(), finishedAt)
        assertEquals(10 * 60 * 60 * 1_000L + 50 * 60 * 1_000L, finishedAt)
    }
}
