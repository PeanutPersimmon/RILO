package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class AppNavigationItem(val label: String, val icon: ImageVector)

/** Exact geometry from MyLibrary ui/theme/Dimens.kt and LibraryBottomBar.kt. */
object FloatingNavigation {
    val height = 52.dp
    val radius = 28.dp
    val shadow = 4.dp
    val bottomPadding = 36.dp
    val horizontalPadding = 14.dp
    val itemSize = 40.dp
    val itemSpacing = 8.dp
    val iconSize = 22.dp
    val actionSize = 52.dp
    val actionIconSize = 26.dp
    val actionGap = 12.dp
}

/** Scroll-content clearance within a page viewport; the navigation itself remains an overlay. */
val LocalFloatingNavigationClearance = compositionLocalOf { 0.dp }

@Composable
fun AppNavigationBar(items: List<AppNavigationItem>, selectedIndex: Int, onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier, onVoiceClick: () -> Unit = {}, onVoiceLongClick: () -> Unit = {},
    onVoiceRelease: () -> Unit = {}, onVoiceCancel: () -> Unit = {}) {
    val g = FloatingNavigation
    val clickAction = rememberUpdatedState(onVoiceClick)
    val longAction = rememberUpdatedState(onVoiceLongClick)
    val releaseAction = rememberUpdatedState(onVoiceRelease)
    val cancelAction = rememberUpdatedState(onVoiceCancel)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = g.bottomPadding),
        contentAlignment = Alignment.BottomCenter) {
        Row(horizontalArrangement = Arrangement.spacedBy(g.actionGap), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(g.horizontalPadding * 2 + g.itemSize * items.size + g.itemSpacing * (items.size - 1), g.height),
                shape = RoundedCornerShape(g.radius), color = MaterialTheme.colorScheme.surface,
                shadowElevation = g.shadow, tonalElevation = 0.dp) {
                Row(Modifier.padding(horizontal = g.horizontalPadding), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(g.itemSpacing)) {
                    items.forEachIndexed { index, item ->
                        val chosen = selectedIndex == index
                        Box(Modifier.size(g.itemSize)
                            .background(if (chosen) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                            .semantics { selected = chosen; role = Role.Tab }
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !chosen) { onItemSelected(index) },
                            contentAlignment = Alignment.Center) {
                            Icon(item.icon, item.label, Modifier.size(g.iconSize),
                                tint = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Surface(Modifier.size(g.actionSize), shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                shadowElevation = g.shadow, tonalElevation = 0.dp) {
                Box(Modifier.fillMaxSize().testTag("quick_input_microphone")
                    .semantics {
                        role = Role.Button
                        onClick("文字快速输入") { clickAction.value(); true }
                        onLongClick("语音快速输入") { longAction.value(); true }
                    }
                    .pointerInput(Unit) {
                        var held = false
                        detectTapGestures(
                            onPress = {
                                held = false
                                val released = tryAwaitRelease()
                                if (held) {
                                    if (released) releaseAction.value() else cancelAction.value()
                                }
                            },
                            onLongPress = { held = true; longAction.value() },
                            onTap = { clickAction.value() },
                        )
                    }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, "快速输入", Modifier.size(g.actionIconSize), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
