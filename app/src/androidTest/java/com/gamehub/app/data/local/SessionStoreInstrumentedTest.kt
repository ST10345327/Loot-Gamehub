package com.gamehub.app.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreInstrumentedTest {
    @Test
    fun legacyPlaintextToken_isMigratedWithoutLosingSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "session-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val tokenKey = stringPreferencesKey("auth_token")
        val userIdKey = intPreferencesKey("user_id")

        try {
            store.edit {
                it[tokenKey] = "legacy-session-token"
                it[userIdKey] = 42
            }

            val sessionStore = SessionStore(store)
            assertEquals("legacy-session-token", sessionStore.token())

            val persistedToken = store.data.first()[tokenKey]
            assertTrue(persistedToken.orEmpty().startsWith("enc:v1:"))
            assertNotEquals("legacy-session-token", persistedToken)
            assertEquals(42, store.data.first()[userIdKey])
            assertEquals("legacy-session-token", sessionStore.token())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun newlySavedToken_isEncryptedAndCanBeReadBack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "session-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val sessionStore = SessionStore(store)
        val tokenKey = stringPreferencesKey("auth_token")

        try {
            sessionStore.save("new-session-token", com.gamehub.app.data.model.User(7, "player", "player@example.test"))

            val persistedToken = store.data.first()[tokenKey]
            assertTrue(persistedToken.orEmpty().startsWith("enc:v1:"))
            assertNotEquals("new-session-token", persistedToken)
            assertEquals("new-session-token", sessionStore.token())
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
