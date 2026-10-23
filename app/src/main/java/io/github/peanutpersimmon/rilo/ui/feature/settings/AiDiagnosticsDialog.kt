package io.github.peanutpersimmon.rilo.ui.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceAgentDiagnosticsStore
import io.github.peanutpersimmon.rilo.ui.components.AppDialog
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun AiDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val agentDiagnostics by VoiceAgentDiagnosticsStore.state.collectAsStateWithLifecycle()
    var copied by remember { mutableStateOf(false) }
    val text = agentDiagnostics.asText()
    AppDialog("AI 诊断", onDismiss, actions = {
        TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("RILO AI 诊断", text))
            copied = true
        }) { Text(if (copied) "已复制" else "复制") }
        TextButton(onClick = onDismiss) { Text("关闭") }
    }) {
        SelectionContainer {
            Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = AppTheme.type.caption)
        }
    }
}
