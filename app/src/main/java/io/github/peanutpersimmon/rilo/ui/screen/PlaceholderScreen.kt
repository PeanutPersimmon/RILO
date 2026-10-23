package io.github.peanutpersimmon.rilo.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.ui.components.EmptyState

/** Intentionally non-functional: confirms only the root layout and navigation skeleton. */
@Composable
fun PlaceholderScreen(body: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.skeleton_ready),
        body = body,
        modifier = modifier,
    )
}

