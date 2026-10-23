package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.spaceM),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = AppTheme.dimens.dialogMaxWidth),
                shape = AppTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(AppTheme.dimens.border, AppTheme.colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(AppTheme.dimens.spaceM),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
                ) {
                    Text(title, style = AppTheme.type.sectionTitle)
                    content()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            AppTheme.dimens.spaceS,
                            Alignment.End,
                        ),
                        content = actions,
                    )
                }
            }
        }
    }
}
