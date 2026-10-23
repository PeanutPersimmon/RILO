package io.github.peanutpersimmon.rilo.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomProjectionQueriesTest {
    @Test
    fun dataChangeObserverTracksOnlyTodayAndPlanTables() {
        assertEquals(
            setOf(
                "sessions",
                "events",
                "categories",
            ),
            PROJECTION_DATA_CHANGE_TABLES.toSet(),
        )
        assertEquals(3, PROJECTION_DATA_CHANGE_TABLES.size)
    }
}
