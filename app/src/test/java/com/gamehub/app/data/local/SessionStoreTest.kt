package com.gamehub.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gamehub.app.data.model.User
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionStoreTest {
    @Test
    fun requestTimeMigrationEncryptsLegacyTokenAndRemovesPlaintext() = runBlocking {
        withFixture(TestCipher()) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            assertEquals(TEST_TOKEN, sessionStore.token())

            val prefs = dataStore.data.first()
            assertEquals("encrypted:$TEST_TOKEN", prefs[TOKEN_KEY])
            assertNotEquals(TEST_TOKEN, prefs[TOKEN_KEY])
            assertStoredSession(prefs, "encrypted:$TEST_TOKEN", USER_ID, "player", "player@example.test")
        }
    }

    @Test
    fun startupMigrationEncryptsLegacyTokenAndRemovesPlaintext() = runBlocking {
        withFixture(TestCipher()) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            sessionStore.migrateLegacyToken()

            assertEquals("encrypted:$TEST_TOKEN", dataStore.data.first()[TOKEN_KEY])
            assertEquals(TEST_TOKEN, sessionStore.token())
            assertStoredSession(
                dataStore.data.first(),
                "encrypted:$TEST_TOKEN",
                USER_ID,
                "player",
                "player@example.test"
            )
        }
    }

    @Test
    fun concurrentRequestMigrationsKeepOneEncryptedSession() = runBlocking {
        val cipher = TestCipher()
        withFixture(cipher) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            val results = coroutineScope {
                listOf(
                    async { sessionStore.token() },
                    async { sessionStore.token() }
                ).map { it.await() }
            }

            assertEquals(listOf(TEST_TOKEN, TEST_TOKEN), results)
            assertEquals(1, cipher.encryptionCount)
            assertStoredSession(
                dataStore.data.first(),
                "encrypted:$TEST_TOKEN",
                USER_ID,
                "player",
                "player@example.test"
            )
        }
    }

    @Test
    fun requestTimeEncryptionFailureClearsLegacySession() = runBlocking {
        withFixture(TestCipher(failEncryption = true)) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            assertNull(sessionStore.token())
            assertSessionIsCleared(dataStore, sessionStore)
        }
    }

    @Test
    fun startupEncryptionFailureClearsLegacySession() = runBlocking {
        withFixture(TestCipher(failEncryption = true)) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            sessionStore.migrateLegacyToken()

            assertSessionIsCleared(dataStore, sessionStore)
        }
    }

    @Test
    fun userFlowDoesNotRestoreSessionWhenLegacyEncryptionFails() = runBlocking {
        withFixture(TestCipher(failEncryption = true)) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            assertNull(sessionStore.user.first())
            assertSessionIsCleared(dataStore, sessionStore)
        }
    }

    @Test
    fun corruptCiphertextCannotRestoreUsableSession() = runBlocking {
        withFixture(TestCipher()) { dataStore, sessionStore ->
            dataStore.edit { prefs ->
                prefs[TOKEN_KEY] = "encrypted:corrupt"
                prefs[USER_ID_KEY] = USER_ID
                prefs[USERNAME_KEY] = "player"
                prefs[EMAIL_KEY] = "player@example.test"
            }

            assertNull(sessionStore.token())
            assertSessionIsCleared(dataStore, sessionStore)
        }
    }

    @Test
    fun existingEncryptedSessionContinuesToWork() = runBlocking {
        withFixture(TestCipher()) { dataStore, sessionStore ->
            seedSession(dataStore, "encrypted:$TEST_TOKEN")

            assertEquals(TEST_TOKEN, sessionStore.token())
            assertEquals(User(USER_ID, "player", "player@example.test"), sessionStore.user.first())
            assertStoredSession(
                dataStore.data.first(),
                "encrypted:$TEST_TOKEN",
                USER_ID,
                "player",
                "player@example.test"
            )
        }
    }

    @Test
    fun failedDecryptionOfOldTokenCannotClearNewerSession() = runBlocking {
        val barrier = OperationBarrier()
        val cipher = TestCipher(failDecryptionFor = "old-token", beforeDecrypt = barrier::block)
        withFixture(cipher) { dataStore, sessionStore ->
            seedSession(dataStore, "encrypted:old-token", USER_ID, "old-player", "old@example.test")
            val oldRead = async(Dispatchers.IO) { sessionStore.token() }
            barrier.awaitStarted()

            sessionStore.save("new-token", User(84, "new-player", "new@example.test"))
            barrier.release()

            assertNull(oldRead.await())
            assertStoredSession(
                dataStore.data.first(),
                "encrypted:new-token",
                84,
                "new-player",
                "new@example.test"
            )
            assertEquals("new-token", sessionStore.token())
        }
    }

    @Test
    fun failedLegacyMigrationCannotRemoveNewerSession() = runBlocking {
        val barrier = OperationBarrier()
        val cipher = TestCipher(
            failEncryptionFor = TEST_TOKEN,
            beforeIsEncrypted = { value -> if (value == TEST_TOKEN) barrier.block() }
        )
        withFixture(cipher) { dataStore, sessionStore ->
            seedLegacySession(dataStore)
            val oldMigration = async(Dispatchers.IO) { sessionStore.token() }
            barrier.awaitStarted()

            sessionStore.save("new-token", User(84, "new-player", "new@example.test"))
            barrier.release()

            assertNull(oldMigration.await())
            assertStoredSession(
                dataStore.data.first(),
                "encrypted:new-token",
                84,
                "new-player",
                "new@example.test"
            )
            assertEquals("new-token", sessionStore.token())
        }
    }

    @Test
    fun migrationEncryptionFailureCannotClearLoginWaitingBehindIt() = runBlocking {
        val barrier = OperationBarrier()
        val cipher = TestCipher(
            failEncryptionFor = TEST_TOKEN,
            beforeEncrypt = { token -> if (token == TEST_TOKEN) barrier.block() }
        )
        withFixture(cipher) { dataStore, sessionStore ->
            seedLegacySession(dataStore)
            val oldMigration = async(Dispatchers.IO) { sessionStore.token() }
            barrier.awaitStarted()
            val newLogin = async(Dispatchers.IO) {
                sessionStore.save("new-token", User(84, "new-player", "new@example.test"))
            }
            barrier.release()

            assertNull(oldMigration.await())
            newLogin.await()
            assertStoredSession(
                dataStore.data.first(),
                "encrypted:new-token",
                84,
                "new-player",
                "new@example.test"
            )
            assertEquals("new-token", sessionStore.token())
        }
    }

    @Test
    fun expiredOldTokenCannotClearNewerSession() = runBlocking {
        withFixture(TestCipher()) { dataStore, sessionStore ->
            seedSession(dataStore, "encrypted:old-token", USER_ID, "old-player", "old@example.test")
            sessionStore.save("new-token", User(84, "new-player", "new@example.test"))

            sessionStore.clearIfTokenMatches("old-token")

            assertStoredSession(
                dataStore.data.first(),
                "encrypted:new-token",
                84,
                "new-player",
                "new@example.test"
            )
            assertEquals("new-token", sessionStore.token())
        }
    }

    @Test
    fun signingOutDuringOlderTokenReadCannotRestoreSession() = runBlocking {
        val barrier = OperationBarrier()
        val cipher = TestCipher(beforeDecrypt = barrier::block)
        withFixture(cipher) { dataStore, sessionStore ->
            seedSession(dataStore, "encrypted:$TEST_TOKEN")
            val oldRead = async(Dispatchers.IO) { sessionStore.token() }
            barrier.awaitStarted()

            sessionStore.clear()
            barrier.release()

            assertNull(oldRead.await())
            assertSessionIsCleared(dataStore, sessionStore)
        }
    }

    @Test
    fun migrationEncryptionFailureLeavesNoPlaintextTokenUsable() = runBlocking {
        withFixture(TestCipher(failEncryptionFor = TEST_TOKEN)) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            assertNull(sessionStore.token())
            assertSessionIsCleared(dataStore, sessionStore)
            assertNull(dataStore.data.first()[TOKEN_KEY])
        }
    }

    @Test
    fun saveEncryptionFailureClearsExistingPlaintextSession() = runBlocking {
        withFixture(TestCipher(failEncryptionFor = "new-token")) { dataStore, sessionStore ->
            seedLegacySession(dataStore)

            try {
                sessionStore.save("new-token", User(84, "new-player", "new@example.test"))
                fail("Expected session encryption failure to propagate")
            } catch (_: GeneralSecurityException) {
                // Encryption failure must not persist the new session or retain legacy plaintext.
            }

            assertSessionIsCleared(dataStore, sessionStore)
            assertNull(dataStore.data.first()[TOKEN_KEY])
        }
    }

    @Test
    fun cancellationDuringDecryptionIsPropagatedWithoutInvalidatingSession() = runBlocking {
        withFixture(TestCipher(decryptionCancellation = true)) { dataStore, sessionStore ->
            seedSession(dataStore, "encrypted:$TEST_TOKEN")

            try {
                sessionStore.token()
                fail("Expected decryption cancellation to propagate")
            } catch (_: CancellationException) {
                // Cancellation remains a cancellation, not an authentication failure.
            }

            assertStoredSession(
                dataStore.data.first(),
                "encrypted:$TEST_TOKEN",
                USER_ID,
                "player",
                "player@example.test"
            )
        }
    }

    private suspend fun seedLegacySession(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    ) {
        seedSession(dataStore, TEST_TOKEN)
    }

    private suspend fun seedSession(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        token: String,
        userId: Int = USER_ID,
        username: String = "player",
        email: String = "player@example.test"
    ) {
        dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_ID_KEY] = userId
            prefs[USERNAME_KEY] = username
            prefs[EMAIL_KEY] = email
        }
    }

    private fun assertStoredSession(
        prefs: androidx.datastore.preferences.core.Preferences,
        token: String,
        userId: Int,
        username: String,
        email: String
    ) {
        assertEquals(token, prefs[TOKEN_KEY])
        assertEquals(userId, prefs[USER_ID_KEY])
        assertEquals(username, prefs[USERNAME_KEY])
        assertEquals(email, prefs[EMAIL_KEY])
    }

    private suspend fun assertSessionIsCleared(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        sessionStore: SessionStore
    ) {
        val prefs = dataStore.data.first()
        assertNull(prefs[TOKEN_KEY])
        assertNull(prefs[USER_ID_KEY])
        assertNull(prefs[USERNAME_KEY])
        assertNull(prefs[EMAIL_KEY])
        assertNull(sessionStore.user.first())
    }

    private suspend fun withFixture(
        cipher: SessionTokenCryptography,
        block: suspend (
            androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
            SessionStore
        ) -> Unit
    ) {
        val directory = Files.createTempDirectory("gamehub-session-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(directory, "preferences.preferences_pb") }
        )
        try {
            block(dataStore, SessionStore(dataStore, cipher))
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    private class TestCipher(
        private val failEncryption: Boolean = false,
        private val failEncryptionFor: String? = null,
        private val failDecryptionFor: String? = null,
        private val decryptionCancellation: Boolean = false,
        private val beforeEncrypt: (String) -> Unit = {},
        private val beforeIsEncrypted: (String) -> Unit = {},
        private val beforeDecrypt: () -> Unit = {}
    ) : SessionTokenCryptography {
        var encryptionCount = 0
            private set

        override fun isEncrypted(value: String): Boolean {
            beforeIsEncrypted(value)
            return value.startsWith("encrypted:")
        }

        override fun encrypt(token: String): String {
            encryptionCount++
            beforeEncrypt(token)
            if (failEncryption || token == failEncryptionFor) {
                throw GeneralSecurityException("Test encryption failure")
            }
            return "encrypted:$token"
        }

        override fun decrypt(value: String): String {
            beforeDecrypt()
            if (decryptionCancellation) throw CancellationException("Test cancellation")
            if (value == "encrypted:$failDecryptionFor" ||
                !isEncrypted(value) ||
                value == "encrypted:corrupt"
            ) {
                throw GeneralSecurityException("Test ciphertext is invalid")
            }
            return value.removePrefix("encrypted:")
        }
    }

    private class OperationBarrier {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun block() {
            started.countDown()
            check(released.await(10, TimeUnit.SECONDS)) { "Barrier was not released in time" }
        }

        suspend fun awaitStarted() {
            assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
        }

        fun release() {
            released.countDown()
        }
    }

    private companion object {
        const val TEST_TOKEN = "legacy-test-token"
        const val USER_ID = 42
        val TOKEN_KEY = stringPreferencesKey("auth_token")
        val USER_ID_KEY = intPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")
        val EMAIL_KEY = stringPreferencesKey("email")
    }
}
