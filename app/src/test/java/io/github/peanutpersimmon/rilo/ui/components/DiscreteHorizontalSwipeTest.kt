package io.github.peanutpersimmon.rilo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscreteHorizontalSwipeTest {
    @Test fun leftAndRightAreDetectedOnlyAfterThreshold() {
        assertEquals(
            HorizontalSwipeDirection.LEFT,
            detectedHorizontalSwipe(-49f, 3f, 48f, multiplePointers = false),
        )
        assertEquals(
            HorizontalSwipeDirection.RIGHT,
            detectedHorizontalSwipe(49f, 3f, 48f, multiplePointers = false),
        )
        assertNull(detectedHorizontalSwipe(-47f, 0f, 48f, multiplePointers = false))
    }

    @Test fun verticalAndMultiTouchGesturesAreIgnored() {
        assertNull(detectedHorizontalSwipe(-80f, 100f, 48f, multiplePointers = false))
        assertNull(detectedHorizontalSwipe(-100f, 0f, 48f, multiplePointers = true))
    }

    @Test fun oneDirectionMovesAtMostOneItemAndDoesNotWrap() {
        val items = listOf("first", "second", "third")

        assertEquals("second", adjacentAfterSwipe(items, "first", HorizontalSwipeDirection.LEFT))
        assertEquals("third", adjacentAfterSwipe(items, "third", HorizontalSwipeDirection.LEFT))
        assertEquals("first", adjacentAfterSwipe(items, "first", HorizontalSwipeDirection.RIGHT))
        assertEquals("first", adjacentAfterSwipe(items, "second", HorizontalSwipeDirection.RIGHT))
    }
}
