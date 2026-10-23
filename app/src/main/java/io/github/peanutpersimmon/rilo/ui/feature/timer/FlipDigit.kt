package io.github.peanutpersimmon.rilo.ui.feature.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

internal const val FLIP_DURATION_MILLIS = 320

@Composable
fun FlipDigit(
    value: Char,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(value) }
    var previous by remember { mutableStateOf(value) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value != current) {
            previous = current
            current = value
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FLIP_DURATION_MILLIS,
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }

    val firstPhase = (progress.value / 0.5f).coerceIn(0f, 1f)
    val secondPhase = ((progress.value - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val bottomBase = if (progress.value < 0.5f) previous else current
    val cameraDistance = with(LocalDensity.current) { AppTheme.dimens.timerCameraDistance.toPx() }

    Surface(
        modifier = modifier
            .width(AppTheme.dimens.timerDigitWidth)
            .height(AppTheme.dimens.timerDigitHeight)
            .clearAndSetSemantics { },
        shape = AppTheme.shapes.small,
        color = AppTheme.colors.timerPanel,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            DigitHalf(value = current, topHalf = true)
            DigitHalf(value = bottomBase, topHalf = false)
            if (progress.value < 0.5f) {
                DigitHalf(
                    value = previous,
                    topHalf = true,
                    modifier = Modifier.graphicsLayer {
                        rotationX = -90f * firstPhase
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        this.cameraDistance = cameraDistance
                    },
                )
            } else if (progress.value < 1f) {
                DigitHalf(
                    value = current,
                    topHalf = false,
                    modifier = Modifier.graphicsLayer {
                        rotationX = 90f * (1f - secondPhase)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        this.cameraDistance = cameraDistance
                    },
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(AppTheme.dimens.timerDigitWidth)
                    .height(AppTheme.dimens.border),
                color = AppTheme.colors.timerDivider,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {}
        }
    }
}

@Composable
private fun BoxScope.DigitHalf(
    value: Char,
    topHalf: Boolean,
    modifier: Modifier = Modifier,
) {
    val halfHeight = AppTheme.dimens.timerDigitHeight / 2
    Box(
        modifier = modifier
            .align(if (topHalf) Alignment.TopCenter else Alignment.BottomCenter)
            .width(AppTheme.dimens.timerDigitWidth)
            .height(halfHeight)
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .width(AppTheme.dimens.timerDigitWidth)
                .height(AppTheme.dimens.timerDigitHeight)
                .offset(y = if (topHalf) 0.dp else -halfHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                color = AppTheme.colors.timerPrimaryText,
                style = AppTheme.type.number.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
        }
    }
}
