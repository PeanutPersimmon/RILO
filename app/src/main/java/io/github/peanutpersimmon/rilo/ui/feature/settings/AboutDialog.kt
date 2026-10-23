package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.components.AppDialog

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    AppDialog(
        title = "RILO",
        onDismissRequest = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "关闭",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "记录计划，也记录真实发生的生活。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row {
                Text(
                    text = "开发版",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "PeanutPersimmon",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/PeanutPersimmon")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = "使用 OpenAI 提供的 AI 工具辅助产品设计、代码开发与调试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}