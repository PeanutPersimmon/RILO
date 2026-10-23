package io.github.peanutpersimmon.rilo.data.remote.qwen

import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenConfigProvider
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenConfigState
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenCredentialStore
import kotlinx.coroutines.CancellationException

class StoredQwenConfigProvider(
    private val credentials: QwenCredentialStore,
) : QwenConfigProvider {
    override suspend fun getConfiguration(): QwenConfigState = try {
        credentials.getCredentials()?.let(QwenConfigState::Configured)
            ?: QwenConfigState.NotConfigured
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        QwenConfigState.Unavailable
    }
}
