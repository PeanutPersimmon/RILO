package io.github.peanutpersimmon.rilo.ui.components.picker

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import java.time.LocalDate
import java.time.LocalTime

@Preview(showBackground = true)
@Composable
private fun DatePickerPreview() = RidoTheme {
    AppDatePicker(LocalDate.of(2026, 10, 14), {}, {})
}

@Preview(showBackground = true)
@Composable
private fun TimePickerPreview() = RidoTheme {
    AppTimePicker(LocalTime.of(14, 30), {}, {})
}
