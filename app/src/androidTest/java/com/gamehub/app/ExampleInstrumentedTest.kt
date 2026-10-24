package com.gamehub.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamehub.app.data.local.SessionTokenCipher
import android.util.Base64
import java.security.GeneralSecurityException

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun applicationIdMatchesExpectedId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.gamehub.app", appContext.packageName)
    }

    @Test
    fun sessionTokenIsEncryptedAndCanBeRestored() {
        val token = "test-session-token"
        val cipher = SessionTokenCipher()

        val encrypted = cipher.encrypt(token)

        assertTrue(cipher.isEncrypted(encrypted))
        assertNotEquals(token, encrypted)
        assertEquals(token, cipher.decrypt(encrypted))
    }

    @Test
    fun sessionTokenCipherRejectsTamperedCiphertext() {
        val cipher = SessionTokenCipher()
        val encrypted = cipher.encrypt("test-session-token")
        val payload = Base64.decode(encrypted.removePrefix("enc:v1:"), Base64.NO_WRAP)
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        val tampered = "enc:v1:" + Base64.encodeToString(payload, Base64.NO_WRAP)

        try {
            cipher.decrypt(tampered)
            fail("Tampered ciphertext should fail authentication")
        } catch (_: GeneralSecurityException) {
            // GCM authentication must reject altered ciphertext.
        }
    }
}