package io.github.peanutpersimmon.rilo.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidQwenCredentialStoreTest {
    @Test
    fun apiKeyIsEncryptedAndClearRemovesStorageAndKeyAlias() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AndroidQwenCredentialStore(context)
        val apiKey = "sentinel-api-key-that-must-not-be-plaintext"
        val workspaceId = "workspace-test"

        store.clear()
        try {
            store.saveCredentials(apiKey, workspaceId)

            assertEquals(apiKey, store.getCredentials()?.apiKey)
            assertEquals(workspaceId, store.getCredentials()?.workspaceId)
            val persisted = context.filesDir.resolve(
                "datastore/rilo_qwen_credentials.preferences_pb",
            ).readBytes().toString(StandardCharsets.ISO_8859_1)
            assertFalse(persisted.contains(apiKey))
            assertTrue(androidKeyStore().containsAlias(AndroidQwenCredentialStore.KEY_ALIAS))

            store.clear()

            assertNull(store.getCredentials())
            assertFalse(androidKeyStore().containsAlias(AndroidQwenCredentialStore.KEY_ALIAS))
        } finally {
            store.clear()
        }
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
}
