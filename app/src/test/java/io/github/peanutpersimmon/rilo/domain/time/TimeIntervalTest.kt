package io.github.peanutpersimmon.rilo.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeIntervalTest {
    @Test fun adjacentHalfOpenIntervalsDoNotIntersect() {
        assertFalse(TimeInterval(10, 20).intersects(TimeInterval(20, 30)))
    }

    @Test fun intersectionReturnsSharedPositiveDuration() {
        assertEquals(TimeInterval(15, 20), TimeInterval(10, 20).intersection(TimeInterval(15, 25)))
        assertNull(TimeInterval(10, 20).intersection(TimeInterval(20, 30)))
    }

    @Test fun subtractMiddleReturnsTwoPieces() {
        assertEquals(
            listOf(TimeInterval(10, 15), TimeInterval(20, 30)),
            TimeInterval(10, 30).subtract(TimeInterval(15, 20)),
        )
    }

    @Test fun clipUsesSameIntersectionSemantics() {
        assertEquals(TimeInterval(15, 20), TimeInterval(10, 20).clipTo(TimeInterval(15, 30)))
    }

    @Test fun containsUsesExclusiveEnd() {
        val interval = TimeInterval(10, 20)
        assertTrue(interval.contains(10))
        assertFalse(interval.contains(20))
        assertEquals(10L, interval.durationMillis)
    }
}
