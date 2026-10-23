package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.ui.feature.editor.event.EventEditorSheet
import io.github.peanutpersimmon.rilo.ui.feature.editor.session.SessionEditorSheet
import io.github.peanutpersimmon.rilo.ui.showUndo
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.presentation.today.TimelineEntryUiModel
import kotlinx.coroutines.launch

enum class InlineCreateKind { EVENT, SESSION }

data class TodayEditorRequest(
    val kind: InlineCreateKind,
    val id: Long? = null,
    val isActive: Boolean = false,
    val sleep: Boolean = false,
    val sourceEventId: Long? = null,
    val sleepWarning: String? = null,
)

internal fun todayEditorRequestFor(entry: TimelineEntryUiModel): TodayEditorRequest? {
    val id = entry.sourceId ?: return null
    return when (entry.entryType) {
        TimelineEntryType.SESSION_BLOCK, TimelineEntryType.SESSION_POINT ->
            TodayEditorRequest(InlineCreateKind.SESSION, id, isActive = entry.isActive,
                sleep = entry.sessionKind == SessionKind.NIGHT_SLEEP || entry.sessionKind == SessionKind.NAP)
        TimelineEntryType.EVENT_BLOCK -> TodayEditorRequest(InlineCreateKind.EVENT, id)
        TimelineEntryType.WAKE_POINT, TimelineEntryType.SLEEP_POINT ->
            TodayEditorRequest(InlineCreateKind.SESSION, id, sleep = true)
    }
}

/** Routes UI requests only. All fields and actions live in the shared editors. */
@Composable
fun TodayEditorSheet(
    request: TodayEditorRequest,
    eventViewModel: EventEditorViewModel,
    sessionViewModel: SessionEditorViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onSessionStarted: () -> Unit,
    onEventRecord: (Long) -> Unit = {},
    onEventChanged: () -> Unit = {},
) {
    if (request.sleep) {
        SleepEditorSheet(sessionViewModel, request.id, onDismiss, warning = request.sleepWarning)
        return
    }
    when (request.kind) {
        InlineCreateKind.EVENT -> EventEditorSheet(eventViewModel, request.id, onDismiss,
            onCreateRecord = onEventRecord, onChanged = onEventChanged) { snapshot, restore ->
            onDismiss()
            scope.launch { showUndo(snackbar, snapshot, restore) }
        }
        InlineCreateKind.SESSION -> SessionEditorSheet(
            sessionViewModel, request.id, onDismiss, onSessionStarted,
            isActive = request.isActive, sourceEventId = request.sourceEventId,
            onDeleted = { snapshot, restore ->
                onDismiss()
                scope.launch { showUndo(snackbar, snapshot, restore) }
            },
        )
    }
}
