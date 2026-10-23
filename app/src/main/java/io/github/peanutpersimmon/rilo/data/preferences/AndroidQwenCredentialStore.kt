package io.github.peanutpersimmon.rilo.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenCredentialStore
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenCredentials
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.qwenCredentialDataStore by preferencesDataStore("rilo_qwen_credentials")

class AndroidQwenCredentialStore(context: Context) : QwenCredentialStore {
    private val store = context.applicationContext.qwenCredentialDataStore
    private val mutex = Mutex()

    override suspend fun getCredentials(): QwenCredentials? = mutex.withLock {
        try {
            val values = store.data.first()
            val encrypted = values[ENCRYPTED_API_KEY] ?: return@withLock null
            val iv = values[API_KEY_IV] ?: return@withLock null
            val workspaceId = values[WORKSPACE_ID]?.trim()?.takeIf(String::isNotEmpty)
                ?: return@withLock null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return@withLock null,
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            val apiKey = String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                StandardCharsets.UTF_8)
            QwenCredentials(apiKey, workspaceId).takeIf {
                it.apiKey.isNotBlank() && it.workspaceId.isNotBlank()
            }
        } catch (_: Exception) {
            runCatching { clearStoredValues() }
            runCatching { deleteKey() }
            null
        }
    }

    override suspend fun saveCredentials(
        apiKey: String,
        workspaceId: String,
    ): Unit = mutex.withLock {
        val trimmedKey = apiKey.trim()
        val trimmedWorkspace = workspaceId.trim()

        require(trimmedKey.isNotEmpty()) { "API Key 不能为空" }
        require(trimmedWorkspace.isNotEmpty()) { "Workspace ID 不能为空" }

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                existingKey() ?: createKey(),
            )

            val encrypted = cipher.doFinal(
                trimmedKey.toByteArray(StandardCharsets.UTF_8),
            )

            store.edit {
                it[ENCRYPTED_API_KEY] =
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
                it[API_KEY_IV] =
                    Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                it[WORKSPACE_ID] = trimmedWorkspace
            }

            Unit
        } catch (_: Exception) {
            throw IllegalStateException("无法安全保存 AI 配置")
        }
    }

    override suspend fun clear() = mutex.withLock {
        var failed = false
        try {
            clearStoredValues()
        } catch (_: Exception) {
            failed = true
        }
        try {
            deleteKey()
        } catch (_: Exception) {
            failed = true
        }
        if (failed) throw IllegalStateException("无法彻底清除 AI 配置")
    }

    private suspend fun clearStoredValues() {
        store.edit {
            it.remove(ENCRYPTED_API_KEY)
            it.remove(API_KEY_IV)
            it.remove(WORKSPACE_ID)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
    private fun deleteKey() {
        val keys = keyStore()
        if (keys.containsAlias(KEY_ALIAS)) keys.deleteEntry(KEY_ALIAS)
    }

    companion object {
        const val KEY_ALIAS = "rilo_qwen_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128
        private val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        private val API_KEY_IV = stringPreferencesKey("api_key_iv")
        private val WORKSPACE_ID = stringPreferencesKey("workspace_id")
    }
}
