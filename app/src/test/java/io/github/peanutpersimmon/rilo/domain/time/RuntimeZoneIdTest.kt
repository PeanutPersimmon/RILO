package io.github.peanutpersimmon.rilo.domain.time

import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RuntimeZoneIdTest {
    private lateinit var original: ZoneId

    @Before fun rememberOriginal() { original = RuntimeZoneId.current }
    @After fun restoreOriginal() { RuntimeZoneId.refresh(original) }

    @Test fun refreshUpdatesTheSharedRuntimeSourceImmediately() {
        val changed = ZoneId.of("Pacific/Auckland")

        assertEquals(changed, RuntimeZoneId.refresh(changed))
        assertEquals(changed, RuntimeZoneId.current)
        assertEquals(changed, RuntimeZoneId.changes.value)
    }
}
