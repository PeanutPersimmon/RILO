package io.github.peanutpersimmon.rilo.ui.components.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.peanutpersimmon.rilo.ui.components.DatePickerLauncher
import io.github.peanutpersimmon.rilo.ui.components.TimePickerLauncher
import io.github.peanutpersimmon.rilo.ui.components.DateTimePickerLauncher
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private data class DateRequest(val initial: LocalDate, val callback: (LocalDate?) -> Unit)
private data class TimeRequest(val initial: LocalTime, val callback: (LocalTime?) -> Unit)
private data class DateTimeRequest(
    val date: LocalDate,
    val time: LocalTime?,
    val timeOptional: Boolean,
    val maxDateTime: LocalDateTime?,
    val callback: (LocalDate, LocalTime?) -> Unit,
)

@Stable
class PickerHostState internal constructor() : DatePickerLauncher, TimePickerLauncher, DateTimePickerLauncher {
    private var dateRequest by mutableStateOf<DateRequest?>(null)
    private var timeRequest by mutableStateOf<TimeRequest?>(null)
    private var dateTimeRequest by mutableStateOf<DateTimeRequest?>(null)

    override fun show(initialDate: LocalDate?, onResult: (LocalDate?) -> Unit) {
        dateRequest = DateRequest(initialDate ?: LocalDate.now(), onResult)
    }

    override fun show(initialTime: LocalTime?, onResult: (LocalTime?) -> Unit) {
        timeRequest = TimeRequest(initialTime ?: LocalTime.now(), onResult)
    }

    override fun show(
        initialDate: LocalDate,
        initialTime: LocalTime?,
        timeOptional: Boolean,
        onResult: (LocalDate, LocalTime?) -> Unit,
    ) {
        show(initialDate, initialTime, timeOptional, null, onResult)
    }

    fun show(
        initialDate: LocalDate,
        initialTime: LocalTime?,
        timeOptional: Boolean,
        maxDateTime: LocalDateTime?,
        onResult: (LocalDate, LocalTime?) -> Unit,
    ) {
        dateTimeRequest = DateTimeRequest(initialDate, initialTime, timeOptional, maxDateTime, onResult)
    }

    @Composable
    internal fun Host() {
        dateRequest?.let { request ->
            AppDatePicker(
                initialDate = request.initial,
                onDismiss = { dateRequest = null },
                onConfirm = { request.callback(it); dateRequest = null },
            )
        }
        timeRequest?.let { request ->
            AppTimePicker(
                initialTime = request.initial,
                onDismiss = { timeRequest = null },
                onConfirm = { request.callback(it); timeRequest = null },
            )
        }
        dateTimeRequest?.let { request ->
            AppDateTimePicker(
                initialDate = request.date,
                initialTime = request.time,
                timeOptional = request.timeOptional,
                maxDateTime = request.maxDateTime,
                onDismiss = { dateTimeRequest = null },
                onConfirm = {
                    request.callback(it.date, it.time)
                    dateTimeRequest = null
                },
            )
        }
    }
}

@Composable fun rememberPickerHostState(): PickerHostState = remember { PickerHostState() }
@Composable fun PickerHost(state: PickerHostState) = state.Host()
