package com.gamehub.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamehub.app.data.model.User
import com.gamehub.app.utils.AppLogger
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The app's single DataStore file. Declared once per process, as DataStore requires. */
val Context.gameHubDataStore: DataStore<Preferences> by preferencesDataStore(name = "gamehub_prefs")

/**
 * Remembers who is signed in (login token + basic user info) and whether onboarding has been
 * seen. The token is encrypted with an Android Keystore key before it is persisted.
 */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: SessionTokenCryptography = SessionTokenCipher()
) {

    private object Keys {
        val TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = intPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val SESSION_GENERATION = longPreferencesKey("session_generation")
    }

    private data class SessionSnapshot(
        val token: String?,
        val userId: Int?,
        val username: String?,
        val email: String?,
        val generation: Long
    )

    private data class UsableSession(val token: String, val snapshot: SessionSnapshot)

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
        val snapshot = prefs.toSessionSnapshot()
        if (snapshot.token.isNullOrBlank() || snapshot.userId == null) {
            null
        } else {
            getUsableToken(snapshot)?.snapshot?.let { current ->
                User(
                    id = current.userId ?: return@let null,
                    username = current.username.orEmpty(),
                    email = current.email.orEmpty()
                )
            }
        }
    }

    val onboardingSeen: Flow<Boolean> = safeData.map { it[Keys.ONBOARDING_SEEN] ?: false }

    /** The saved login token, or null. Used by the network layer. */
    suspend fun token(): String? {
        val snapshot = safeData.first().toSessionSnapshot()
        if (snapshot.token.isNullOrBlank()) return null
        return getUsableToken(snapshot)?.token
    }

    /** Clears the session only if the token rejected by the server is still current. */
    suspend fun clearIfTokenMatches(failedToken: String) {
        val snapshot = safeData.first().toSessionSnapshot()
        if (snapshot.token.isNullOrBlank()) return
        val usable = getUsableToken(snapshot) ?: return
        if (usable.token != failedToken) return

        dataStore.edit { prefs ->
            if (matchesSession(prefs, usable.snapshot)) {
                clearSession(prefs)
                advanceGeneration(prefs)
            }
        }
    }

    private suspend fun getUsableToken(snapshot: SessionSnapshot): UsableSession? {
        val stored = snapshot.token ?: return null
        if (!tokenCipher.isEncrypted(stored)) {
            val migrated = migrateLegacyToken(snapshot) ?: return null
            val usable = getUsableToken(migrated) ?: return null
            return usable.takeIf { it.token == stored }
        }

        val token = try {
            tokenCipher.decrypt(stored)
        } catch (error: CancellationException) {
            throw error
        } catch (error: GeneralSecurityException) {
            invalidateUnreadableToken(snapshot, error)
            return null
        } catch (error: IllegalArgumentException) {
            invalidateUnreadableToken(snapshot, error)
            return null
        }
        if (!isCurrentSession(snapshot)) return null
        return UsableSession(token, snapshot)
    }

    suspend fun save(token: String, user: User) {
        var encryptionFailure: GeneralSecurityException? = null
        dataStore.edit { prefs ->
            val encryptedToken = try {
                tokenCipher.encrypt(token)
            } catch (error: GeneralSecurityException) {
                if (prefs[Keys.TOKEN]?.let { !tokenCipher.isEncrypted(it) } == true) {
                    clearSession(prefs)
                    advanceGeneration(prefs)
                }
                encryptionFailure = error
                return@edit
            }
            prefs[Keys.TOKEN] = encryptedToken
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.EMAIL] = user.email
            advanceGeneration(prefs)
        }
        encryptionFailure?.let { throw it }
    }

    /** Upgrades tokens persisted by earlier app versions without interrupting valid sessions. */
    suspend fun migrateLegacyToken() {
        val snapshot = safeData.first().toSessionSnapshot()
        if (!snapshot.token.isNullOrBlank()) getUsableToken(snapshot)
    }

    private suspend fun migrateLegacyToken(snapshot: SessionSnapshot): SessionSnapshot? {
        val legacyToken = snapshot.token ?: return null
        var migrated: SessionSnapshot? = null
        var encryptionFailed = false
        dataStore.edit { prefs ->
            if (matchesSession(prefs, snapshot)) {
                try {
                    val encryptedToken = tokenCipher.encrypt(legacyToken)
                    prefs[Keys.TOKEN] = encryptedToken
                    val generation = advanceGeneration(prefs)
                    migrated = snapshot.copy(token = encryptedToken, generation = generation)
                } catch (_: GeneralSecurityException) {
                    clearSession(prefs)
                    advanceGeneration(prefs)
                    encryptionFailed = true
                }
            }
        }
        if (encryptionFailed) {
            AppLogger.error("Could not encrypt saved session token; clearing the session")
            return null
        }
        if (migrated != null) return migrated

        val current = safeData.first().toSessionSnapshot()
        return current.takeIf {
            it.generation == snapshot.generation + 1 &&
                sameUser(it, snapshot) &&
                it.token != null &&
                tokenCipher.isEncrypted(it.token)
        }
    }

    private suspend fun invalidateUnreadableToken(snapshot: SessionSnapshot, error: Exception) {
        AppLogger.error("Could not decrypt saved session token; clearing the session", error)
        dataStore.edit { prefs ->
            if (matchesSession(prefs, snapshot)) {
                clearSession(prefs)
                advanceGeneration(prefs)
            }
        }
    }

    /** Signs out. The onboarding flag is kept: a returning user should not see the intro again. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            clearSession(prefs)
            advanceGeneration(prefs)
        }
    }

    private suspend fun isCurrentSession(snapshot: SessionSnapshot): Boolean {
        var isCurrent = false
        dataStore.edit { prefs ->
            isCurrent = matchesSession(prefs, snapshot)
        }
        return isCurrent
    }

    private fun Preferences.toSessionSnapshot() = SessionSnapshot(
        token = this[Keys.TOKEN],
        userId = this[Keys.USER_ID],
        username = this[Keys.USERNAME],
        email = this[Keys.EMAIL],
        generation = this[Keys.SESSION_GENERATION] ?: 0L
    )

    private fun matchesSession(prefs: Preferences, snapshot: SessionSnapshot): Boolean =
        prefs[Keys.TOKEN] == snapshot.token &&
            prefs[Keys.USER_ID] == snapshot.userId &&
            prefs[Keys.USERNAME] == snapshot.username &&
            prefs[Keys.EMAIL] == snapshot.email &&
            (prefs[Keys.SESSION_GENERATION] ?: 0L) == snapshot.generation

    private fun sameUser(first: SessionSnapshot, second: SessionSnapshot): Boolean =
        first.userId == second.userId &&
            first.username == second.username &&
            first.email == second.email

    private fun advanceGeneration(prefs: MutablePreferences): Long {
        val generation = (prefs[Keys.SESSION_GENERATION] ?: 0L) + 1
        prefs[Keys.SESSION_GENERATION] = generation
        return generation
    }

    private fun clearSession(prefs: MutablePreferences) {
        prefs.remove(Keys.TOKEN)
        prefs.remove(Keys.USER_ID)
        prefs.remove(Keys.USERNAME)
        prefs.remove(Keys.EMAIL)
    }

    suspend fun markOnboardingSeen() {
        dataStore.edit { it[Keys.ONBOARDING_SEEN] = true }
    }
}