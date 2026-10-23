package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.peanutpersimmon.rilo.presentation.settings.MetadataViewModel
import io.github.peanutpersimmon.rilo.presentation.settings.MetadataUiState

val LocalMetadataViewModel = staticCompositionLocalOf<MetadataViewModel?> { null }
val LocalMetadataState = staticCompositionLocalOf { MetadataUiState() }
