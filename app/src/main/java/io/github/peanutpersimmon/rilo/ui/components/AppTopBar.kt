package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(AppTheme.dimens.topBarHeight)
            .padding(horizontal = AppTheme.dimens.pageHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) {
            Box(
                modifier = Modifier.padding(end = AppTheme.dimens.spaceS),
                contentAlignment = Alignment.Center,
            ) { navigation() }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = AppTheme.type.pageTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        actions()
    }
}
