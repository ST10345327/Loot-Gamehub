package com.gamehub.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {
    @Test
    fun storedToken_isSentAsBearerAuthorization() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor { "test-session-token" })
                .build()

            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { }

            assertEquals("Bearer test-session-token", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun missingToken_doesNotAddAuthorizationHeader() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor { null })
                .build()

            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { }

            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }
}
