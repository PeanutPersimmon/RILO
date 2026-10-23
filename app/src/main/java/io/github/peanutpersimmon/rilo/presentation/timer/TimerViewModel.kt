package io.github.peanutpersimmon.rilo.presentation.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.presentation.editor.userMessage
import io.github.peanutpersimmon.rilo.domain.usecase.GetClassificationLabelsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveActiveSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StopActiveSessionUseCase
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rilo.presentation.time.SystemCurrentTimeProvider
import io.github.peanutpersimmon.rilo.presentation.time.alignedSecondTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModel(
    observeActiveSession: ObserveActiveSessionUseCase,
    private val stopActiveSession: StopActiveSessionUseCase,
    private val getLabels: GetClassificationLabelsUseCase,
    private val timeProvider: CurrentTimeProvider = SystemCurrentTimeProvider,
) : ViewModel() {
    private val stopping = MutableStateFlow(false)
    private val stopError = MutableStateFlow<String?>(null)
    private val activeDetails = observeActiveSession().onEach { session ->
        if (session == null) stopping.value = false
    }.mapLatest { session ->
        val category = session?.categoryId?.let { getLabels().categoriesById[it] }
        session to category
    }

    private val timedActiveDetails = activeDetails.flatMapLatest { details ->
        if (details.first == null) flowOf(details to timeProvider.nowMillis())
        else alignedSecondTicks(timeProvider).map { nowMillis -> details to nowMillis }
    }

    val uiState = combine(
        timedActiveDetails,
        stopping,
        stopError,
    ) { (details, nowMillis), isStopping, errorMessage ->
        val (session, category) = details
        if (session == null) {
            TimerUiState.Inactive
        } else {
            TimerUiState.Active(
                sessionId = session.id,
                kind = session.kind,
                startAtMillis = session.startAtMillis,
                title = session.title,
                categoryLabel = category?.name,
                categoryColorKey = category?.colorKey,
                elapsed = elapsedFrom(session.startAtMillis, nowMillis),
                isStopping = isStopping,
                errorMessage = errorMessage,
            )
        }
    }.catch {
        emit(TimerUiState.Inactive)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimerUiState.Loading,
    )

    fun stop() = finish()

    private fun finish() {
        if (stopping.value) return
        val active = uiState.value as? TimerUiState.Active ?: return
        if (active.kind != SessionKind.ACTIVITY) {
            stopError.value = "睡眠记录请使用睡眠编辑或起床操作"
            return
        }
        stopping.value = true
        viewModelScope.launch {
            stopping.value = true
            stopError.value = null
            when (val result = stopActiveSession(timeProvider.nowMillis())) {
                is DomainResult.Failure -> {
                    stopping.value = false
                    stopError.value = result.error.userMessage()
                }
                is DomainResult.Success -> Unit
            }
        }
    }

    fun clearError() {
        stopError.update { null }
    }

    companion object {
        fun factory(
            observeActiveSession: ObserveActiveSessionUseCase,
            stopActiveSession: StopActiveSessionUseCase,
            getLabels: GetClassificationLabelsUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(
                observeActiveSession,
                stopActiveSession,
                getLabels,
            ) as T
        }
    }
}
