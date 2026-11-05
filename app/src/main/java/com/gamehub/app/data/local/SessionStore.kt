package com.gamehub.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamehub.app.data.model.User
import com.gamehub.app.utils.AppLogger
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The app's single DataStore file. Declared once per process, as DataStore requires. */
val Context.gameHubDataStore: DataStore<Preferences> by preferencesDataStore(name = "gamehub_prefs")

/**
 * Remembers who is signed in (login token + basic user info) and whether the onboarding
 * has been seen. Backed by DataStore, so it survives app restarts.
 *
 * Tokens are encrypted with an Android Keystore key. Existing plaintext DataStore tokens are
 * encrypted on first access and retained if migration fails.
 */
class SessionStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = intPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
    }

    private val tokenMutex = Mutex()

    /** DataStore data, but a corrupted/unreadable file is treated as "empty" instead of crashing. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) {
            AppLogger.error("Could not read saved session", error)
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    /** The signed-in user, or null when signed out. Emits again whenever it changes. */
    val user: Flow<User?> = safeData.map { prefs ->
        val token = prefs[Keys.TOKEN]
        val id = prefs[Keys.USER_ID]
        if (token.isNullOrBlank() || id == null) {
            null
        } else {
            User(id = id, username = prefs[Keys.USERNAME].orEmpty(), email = prefs[Keys.EMAIL].orEmpty())
        }
    }

    val onboardingSeen: Flow<Boolean> = safeData.map { it[Keys.ONBOARDING_SEEN] ?: false }

    /** The saved login token, or null. Legacy plaintext values migrate on first access. */
    suspend fun token(): String? = tokenMutex.withLock {
        val storedToken = safeData.first()[Keys.TOKEN] ?: return@withLock null
        if (storedToken.startsWith(ENCRYPTED_PREFIX)) {
            return@withLock try {
                decrypt(storedToken)
            } catch (error: Exception) {
                throw SessionStorageException(error)
            }
        }

        try {
            val encryptedToken = encrypt(storedToken)
            dataStore.edit { prefs ->
                if (prefs[Keys.TOKEN] == storedToken) prefs[Keys.TOKEN] = encryptedToken
            }
            storedToken
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw SessionStorageException(error)
        }
    }

    suspend fun save(token: String, user: User) = tokenMutex.withLock {
        try {
            val encryptedToken = encrypt(token)
            dataStore.edit { prefs ->
                prefs[Keys.TOKEN] = encryptedToken
                prefs[Keys.USER_ID] = user.id
                prefs[Keys.USERNAME] = user.username
                prefs[Keys.EMAIL] = user.email
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw SessionStorageException(error)
        }
    }

    /** Signs out. The onboarding flag is kept: a returning user should not see the intro again. */
    suspend fun clear() = tokenMutex.withLock {
        dataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.EMAIL)
        }
    }

    suspend fun markOnboardingSeen() {
        dataStore.edit { it[Keys.ONBOARDING_SEEN] = true }
    }

    private fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encryptedBytes = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + encryptedBytes.size)
            .put(cipher.iv)
            .put(encryptedBytes)
            .array()
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        require(payload.size > GCM_IV_BYTES) { "Invalid encrypted session value" }
        val buffer = ByteBuffer.wrap(payload)
        val iv = ByteArray(GCM_IV_BYTES).also(buffer::get)
        val encryptedBytes = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gamehub.session.token.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

class SessionStorageException(cause: Throwable) :
    IllegalStateException("Secure session storage is unavailable", cause)