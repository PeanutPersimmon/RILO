package io.github.peanutpersimmon.rilo.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel

class EditorViewModelFactory(
    private val repository: RidoCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        EventEditorViewModel::class.java -> EventEditorViewModel(
            GetEventForEditorUseCase(repository), GetEditorOptionsUseCase(repository), SaveEventUseCase(repository),
            DeleteEventUseCase(repository), RestoreEventUseCase(repository),
            initialClassification = InitialClassificationUseCase(repository),
        )
        SessionEditorViewModel::class.java -> SessionEditorViewModel(
            GetSessionForEditorUseCase(repository), GetEditorOptionsUseCase(repository), CreatePastSessionUseCase(repository),
            StartSessionUseCase(repository), SaveSessionEditUseCase(repository), DeleteSessionWithUndoUseCase(repository),
            RestoreSessionUseCase(repository),
            getEvent = GetEventForEditorUseCase(repository), observeChanges = ObserveTimelineDataChangesUseCase(repository),
            getPreviousActualEnd = GetPreviousActualEndUseCase(repository),
            initialClassification = InitialClassificationUseCase(repository),
        )
        else -> error("Unsupported editor ViewModel: ${modelClass.name}")
    } as T
}
