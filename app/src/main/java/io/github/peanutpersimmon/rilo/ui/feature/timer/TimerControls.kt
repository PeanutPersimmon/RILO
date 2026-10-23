package io.github.peanutpersimmon.rilo.ui.feature.timer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.ui.components.AppClickableSurface
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun TimerControls(
    isStopping: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.timerPanel,
    contentColor: Color = AppTheme.colors.timerPrimaryText,
) {
    androidx.compose.foundation.layout.Column {
        AppClickableSurface(
            onClick = onStop,
            enabled = !isStopping,
            modifier = modifier.fillMaxWidth(),
            shape = AppTheme.shapes.medium,
            containerColor = containerColor,
        ) {
            Text(
                text = if (isStopping) stringResource(R.string.timer_stopping) else "结束计时",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.dimens.spaceM),
                color = contentColor,
                style = AppTheme.type.itemTitle,
                textAlign = TextAlign.Center,
            )
        }
    }
}
