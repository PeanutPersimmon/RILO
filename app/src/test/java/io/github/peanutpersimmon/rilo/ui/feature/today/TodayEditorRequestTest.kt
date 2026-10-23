package io.github.peanutpersimmon.rilo.ui.feature.today

import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.model.TimelineState
import io.github.peanutpersimmon.rilo.presentation.today.TimelineEntryUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TodayEditorRequestTest {
    @Test fun sessionPointOpensExistingRecordEditor() {
        val request = todayEditorRequestFor(entry(TimelineEntryType.SESSION_POINT, 42))!!
        assertEquals(InlineCreateKind.SESSION, request.kind)
        assertEquals(42L, request.id)
        assertFalse(request.isActive)
        assertFalse(request.sleep)
    }

    @Test fun missingIdentityCannotCreateNewRecord() {
        assertNull(todayEditorRequestFor(entry(TimelineEntryType.SESSION_POINT, null)))
    }

    private fun entry(type: TimelineEntryType, id: Long?) = TimelineEntryUiModel(
        sourceId = id, startAtMillis = 1, endAtMillis = null, title = "Point",
        timeLabel = "00:00", durationLabel = null, entryType = type,
        state = TimelineState.ACTUAL,
        categoryLabel = null, location = null, note = null,
        isActive = false,
    )
}
