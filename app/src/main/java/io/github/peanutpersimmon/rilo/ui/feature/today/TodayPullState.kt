package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** Two-state height animation owned only by the floating reminder surface. */
@Stable
internal class TodayPullState(private val range: Float) {
    var offset by mutableFloatStateOf(0f)
        private set
    val fraction: Float get() = if (range > 0f) (offset / range).coerceIn(0f, 1f) else 0f
    val fullyExpanded: Boolean get() = fraction >= 0.999f
    private var animation: Job? = null

    suspend fun settle(expand: Boolean) {
        animation?.cancel()
        animation = currentCoroutineContext().job
        val target = if (expand) range else 0f
        try {
            animate(offset, target, animationSpec = tween(180)) { value, _ -> offset = value }
        } finally {
            animation = null
        }
    }
}