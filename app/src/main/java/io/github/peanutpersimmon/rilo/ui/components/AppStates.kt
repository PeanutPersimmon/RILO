package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    MessageState(title = title, body = body, modifier = modifier)
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    MessageState(title = title, body = body, modifier = modifier, isError = true)
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    modifier: Modifier,
    isError: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppTheme.dimens.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = AppTheme.type.itemTitle,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = AppTheme.type.secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
