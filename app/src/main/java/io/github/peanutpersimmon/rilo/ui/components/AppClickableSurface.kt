package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun AppClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.86f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "restrainedPressFeedback",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer { this.alpha = alpha },
        enabled = enabled,
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource,
        content = content,
    )
}
