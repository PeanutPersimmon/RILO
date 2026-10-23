package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

val LocalBottomSheetVisibility = compositionLocalOf<((Boolean) -> Unit)?> { null }

/** The final visible element determines the optical inset inside its layout box. */
enum class SheetBottomVisual { Surface, BodyTextRow, ChipSlot, Special }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    bottomVisual: SheetBottomVisual = SheetBottomVisual.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibility = LocalBottomSheetVisibility.current
    // The shared visual target is spaceXl. A 36dp body row leaves 6dp below its
    // 24sp line box; a 36dp chip slot leaves 2dp below its 32dp surface.
    val bottomPadding = when (bottomVisual) {
        SheetBottomVisual.Surface -> AppTheme.dimens.spaceXl
        SheetBottomVisual.BodyTextRow ->
            AppTheme.dimens.spaceXl - (AppTheme.dimens.spaceS - AppTheme.dimens.spaceXs / 2)
        SheetBottomVisual.ChipSlot -> AppTheme.dimens.spaceXl - AppTheme.dimens.spaceXs / 2
        SheetBottomVisual.Special -> AppTheme.dimens.spaceL
    }
    DisposableEffect(visibility) {
        visibility?.invoke(true)
        onDispose { visibility?.invoke(false) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = AppTheme.shapes.bottomSheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.sheetHandlePadding), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(AppTheme.dimens.sheetHandleWidth)
                        .height(AppTheme.dimens.sheetHandleHeight)
                        .background(
                            color = MaterialTheme.colorScheme.outline,
                            shape = AppTheme.shapes.small,
                        ),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = AppTheme.dimens.pageHorizontal)
                .padding(bottom = bottomPadding),
        ) {
            title?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.dimens.sheetHeaderContentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(it, Modifier.weight(1f), style = AppTheme.type.sheetTitle)
                    headerAction?.invoke()
                    headerActions?.invoke(this)
                }
            }
            content()
        }
    }
}
