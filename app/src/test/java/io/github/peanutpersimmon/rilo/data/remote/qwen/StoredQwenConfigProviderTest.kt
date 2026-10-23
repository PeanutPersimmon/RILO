package io.github.peanutpersimmon.rilo.data.remote.qwen

import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenConfigState
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenCredentialStore
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenCredentials
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StoredQwenConfigProviderTest {
    @Test
    fun missingCredentialsAreReportedAsNotConfigured() = runBlocking {
        val provider = StoredQwenConfigProvider(FakeStore(null))

        assertSame(QwenConfigState.NotConfigured, provider.getConfiguration())
    }

    @Test
    fun storedCredentialsAreReturnedAtRuntime() = runBlocking {
        val expected = QwenCredentials("runtime-secret", "workspace-1")
        val provider = StoredQwenConfigProvider(FakeStore(expected))

        assertEquals(QwenConfigState.Configured(expected), provider.getConfiguration())
    }

    @Test
    fun storageFailureIsNotReportedAsAConnectivityError() = runBlocking {
        val provider = StoredQwenConfigProvider(FakeStore(failure = true))

        assertSame(QwenConfigState.Unavailable, provider.getConfiguration())
    }

    private class FakeStore(
        private val credentials: QwenCredentials? = null,
        private val failure: Boolean = false,
    ) : QwenCredentialStore {
        override suspend fun getCredentials(): QwenCredentials? {
            if (failure) error("storage unavailable")
            return credentials
        }

        override suspend fun saveCredentials(apiKey: String, workspaceId: String) = Unit
        override suspend fun clear() = Unit
    }
}
