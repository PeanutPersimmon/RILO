package io.github.peanutpersimmon.rilo.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenConnectionResult
import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenRealtimeClient
import io.github.peanutpersimmon.rilo.domain.voice.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QwenSettingsUiState(
    val loading: Boolean = true,
    val configured: Boolean = false,
    val workspaceId: String = "",
    val saving: Boolean = false,
    val testing: Boolean = false,
    val connectionStatus: String? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = saving || testing
}

class QwenSettingsViewModel(
    private val credentials: QwenCredentialStore,
    private val configuration: QwenConfigProvider,
    private val client: QwenRealtimeClient,
) : ViewModel() {
    private val state = MutableStateFlow(QwenSettingsUiState())
    val uiState = state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        state.update { it.copy(loading = true, error = null) }
        when (val result = withContext(Dispatchers.IO) { configuration.getConfiguration() }) {
            is QwenConfigState.Configured -> state.value = QwenSettingsUiState(
                loading = false, configured = true, workspaceId = result.credentials.workspaceId)
            QwenConfigState.NotConfigured -> state.value = QwenSettingsUiState(loading = false)
            QwenConfigState.Unavailable -> state.value = QwenSettingsUiState(
                loading = false, error = "无法读取 AI 配置")
        }
    }

    fun save(apiKey: String, workspaceId: String, onSaved: () -> Unit) = viewModelScope.launch {
        val key = apiKey.trim()
        val workspace = workspaceId.trim()
        if (key.isEmpty() || workspace.isEmpty()) {
            state.update { it.copy(error = if (key.isEmpty()) "API Key 不能为空" else "Workspace ID 不能为空") }
            return@launch
        }
        state.update { it.copy(saving = true, error = null, connectionStatus = null) }
        try {
            credentials.saveCredentials(key, workspace)
            state.value = QwenSettingsUiState(loading = false, configured = true, workspaceId = workspace)
            onSaved()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            state.update { it.copy(saving = false, error = "AI 配置保存失败") }
        }
    }

    fun clear(onCleared: () -> Unit = {}) = viewModelScope.launch {
        state.update { it.copy(saving = true, error = null, connectionStatus = null) }
        try {
            credentials.clear()
            state.value = QwenSettingsUiState(loading = false)
            onCleared()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            state.update { it.copy(saving = false, error = "AI 配置清除失败") }
        }
    }

    fun testConnection(draftApiKey: String, draftWorkspaceId: String) = viewModelScope.launch {
        state.update { it.copy(testing = true, connectionStatus = "正在连接…", error = null) }
        val key = draftApiKey.trim()
        val workspace = draftWorkspaceId.trim()
        val config = if (key.isNotEmpty() || workspace != state.value.workspaceId) {
            if (key.isEmpty() || workspace.isEmpty()) null else QwenCredentials(key, workspace)
        } else {
            (configuration.getConfiguration() as? QwenConfigState.Configured)?.credentials
        }
        if (config == null) {
            state.update { it.copy(testing = false, connectionStatus = "连接失败：请完整填写并保存配置") }
            return@launch
        }
        val result = try {
            client.testConnection(config)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            QwenConnectionResult.Failed("无法连接服务")
        }
        state.update { it.copy(testing = false, connectionStatus = when (result) {
            QwenConnectionResult.Success -> "连接成功"
            is QwenConnectionResult.Failed -> "连接失败：${result.reason}"
        }) }
    }

    fun clearError() { state.update { it.copy(error = null) } }

    companion object {
        fun factory(credentials: QwenCredentialStore, configuration: QwenConfigProvider,
            client: QwenRealtimeClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                QwenSettingsViewModel(credentials, configuration, client) as T
        }
    }
}
