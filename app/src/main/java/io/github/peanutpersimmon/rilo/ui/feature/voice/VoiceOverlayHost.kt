package io.github.peanutpersimmon.rilo.ui.feature.voice

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.components.FloatingNavigation
import io.github.peanutpersimmon.rilo.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** Observes presses on the ancestor path without consuming clicks, drags or scrolling. */
fun Modifier.observeOutsideVoicePress(bounds: () -> Rect?, origin: () -> Offset, onOutside: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let {
                    if (bounds()?.contains(it.position + origin()) != true) onOutside()
                }
            }
        }
    }

/** Only the floating input consumes IME insets. Navigation overlays page content. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceOverlayHost(voiceOpen: Boolean, content: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit, voiceInput: @Composable () -> Unit,
    snackbar: @Composable () -> Unit,
    floatingOverlayVisible: Boolean = false,
    floatingOverlay: @Composable () -> Unit = {},
    imeInsets: WindowInsets = WindowInsets.ime) {
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val open by rememberUpdatedState(voiceOpen)
    val navInset = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp() }
    val bottomHeight = navInset + FloatingNavigation.bottomPadding + FloatingNavigation.height
    val imeVisible = imeInsets.getBottom(density) > 0 || WindowInsets.isImeVisible
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
        .observeOutsideVoicePress({ if (open) bounds else null }, { origin }) {
            if (open) { focus.clearFocus(); keyboard?.hide() }
        }) {
        CompositionLocalProvider(
            LocalFloatingNavigationClearance provides (if (imeVisible) 0.dp else
                FloatingNavigation.height + FloatingNavigation.bottomPadding + AppTheme.dimens.spaceS),
        ) { content() }
        if (!imeVisible) Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        if (!imeVisible && floatingOverlayVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = bottomHeight + 8.dp),
            ) { floatingOverlay() }
        }
        if (voiceOpen) {
            Box(Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(imeInsets.union(WindowInsets(bottom = bottomHeight)))
                .padding(bottom = 8.dp)) {
                Box(Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) { voiceInput() }
            }
        }
        if (!imeVisible) {
            val inputHeight = if (voiceOpen) with(density) { (bounds?.height ?: 0f).toDp() } + 8.dp else 0.dp
            val floatingHeight = if (floatingOverlayVisible) 48.dp else 0.dp
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomHeight + inputHeight + floatingHeight + 8.dp)) { snackbar() }
        }
    }
}
