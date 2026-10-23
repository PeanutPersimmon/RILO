package io.github.peanutpersimmon.rilo.presentation.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerElapsedTest {
    @Test fun zeroSeconds() {
        assertEquals("00:00:00", elapsedFrom(10_000, 10_000).clockText)
    }

    @Test fun fiftyNineSeconds() {
        assertEquals("00:00:59", elapsedFrom(0, 59_999).clockText)
    }

    @Test fun sixtySeconds() {
        assertEquals("00:01:00", elapsedFrom(0, 60_000).clockText)
    }

    @Test fun oneHour() {
        assertEquals("01:00:00", elapsedFrom(0, 3_600_000).clockText)
    }

    @Test fun moreThanTwentyFourHours() {
        assertEquals("25:02:03", elapsedFrom(0, 25 * 3_600_000L + 2 * 60_000L + 3_000L).clockText)
    }

    @Test fun processReopenReconstructsFromPersistedStart() {
        val persistedStart = 1_000L
        assertEquals("01:00:00", elapsedFrom(persistedStart, persistedStart + 3_600_000L).clockText)
    }

    @Test fun elapsedDoesNotDependOnNumberOfTicks() {
        val direct = elapsedFrom(0, 10_000)
        elapsedFrom(0, 1_000)
        elapsedFrom(0, 9_000)
        assertEquals(direct, elapsedFrom(0, 10_000))
    }

    @Test fun nineToZeroFlips() {
        assertTrue(FlipDigitTransition('9', '0').shouldFlip)
    }

    @Test fun zeroToOneFlips() {
        assertTrue(FlipDigitTransition('0', '1').shouldFlip)
    }

    @Test fun unchangedDigitDoesNotFlip() {
        assertFalse(FlipDigitTransition('4', '4').shouldFlip)
    }

    @Test fun hoursExpandBeyondTwoDigits() {
        val elapsed = elapsedFrom(0, 123 * 3_600_000L)
        assertEquals("123", elapsed.hoursText)
        assertEquals("123:00:00", elapsed.clockText)
    }
}
