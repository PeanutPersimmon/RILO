package io.github.peanutpersimmon.rilo.ui.feature.timer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.presentation.timer.TimerUiState
import io.github.peanutpersimmon.rilo.presentation.timer.TimerViewModel
import io.github.peanutpersimmon.rilo.ui.components.AppClickableSurface
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.ensureContrast
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState) {
        if (uiState == TimerUiState.Inactive) onBack()
    }
    BackHandler(onBack = onBack)
    TimerScreenContent(
        uiState = uiState,
        onBack = onBack,
        onStop = viewModel::stop,
        onEdit = onEdit,
        modifier = modifier,
    )
}

@Composable
fun TimerScreenContent(
    uiState: TimerUiState,
    onBack: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (Long) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = AppTheme.colors.timerBackground,
        contentColor = AppTheme.colors.timerPrimaryText,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        when (uiState) {
            TimerUiState.Loading, TimerUiState.Inactive -> Box(Modifier.fillMaxSize()) {}
            is TimerUiState.Active -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(AppTheme.dimens.pageHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppClickableSurface(
                        onClick = onBack,
                        containerColor = AppTheme.colors.timerBackground,
                        shape = AppTheme.shapes.small,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AppTheme.colors.timerPrimaryText,
                        )
                    }
                    Text(
                        text = uiState.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppTheme.dimens.spaceM),
                        color = AppTheme.colors.timerPrimaryText,
                        style = AppTheme.type.itemTitle,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    AppClickableSurface(onClick = { onEdit(uiState.sessionId) }, containerColor = AppTheme.colors.timerBackground) {
                        Text("编辑", style = AppTheme.type.secondary, color = AppTheme.colors.timerSecondaryText)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    key(uiState.sessionId) {
                        FlipClock(elapsed = uiState.elapsed)
                    }
                }
                uiState.categoryLabel?.let { category ->
                    val categoryColor = uiState.categoryColorKey?.let {
                        ensureContrast(
                            resolveCategoryColors(it, androidx.compose.material3.MaterialTheme.colorScheme).border,
                            AppTheme.colors.timerBackground,
                            minimumRatio = 4.5,
                        )
                    } ?: AppTheme.colors.timerSecondaryText
                    Text(
                        text = category,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceS),
                        color = categoryColor,
                        style = AppTheme.type.secondary,
                    )
                }
                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceS),
                        color = AppTheme.colors.timerSecondaryText,
                        style = AppTheme.type.caption,
                    )
                }
                if (uiState.kind == SessionKind.ACTIVITY) {
                    TimerControls(
                        isStopping = uiState.isStopping,
                        onStop = onStop,
                    )
                } else {
                    Text(
                        text = "睡眠记录请使用睡眠编辑或起床操作",
                        color = AppTheme.colors.timerSecondaryText,
                        style = AppTheme.type.caption,
                    )
                }
            }
        }
    }
}
