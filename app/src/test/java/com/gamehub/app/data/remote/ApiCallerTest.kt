package com.gamehub.app.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Checks that every kind of failure becomes an [ApiResult] instead of crashing the app. */
class ApiCallerTest {

    private val expiredTokens = mutableListOf<String?>()
    private val caller = ApiCaller(onSessionExpired = { expiredTokens += it })

    private fun httpError(code: Int, body: String, token: String? = null): HttpException {
        val request = Request.Builder()
            .url("https://api.example.test/")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        val rawResponse = OkHttpResponse.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .body("".toResponseBody())
            .build()
        return HttpException(
            Response.error<Any>(body.toResponseBody("application/json".toMediaType()), rawResponse)
        )
    }

    @Test
    fun success_isWrapped() = runTest {
        assertEquals(ApiResult.Success(42), caller.call { 42 })
    }

    @Test
    fun expiredToken_clearsTheSession() = runTest {
        val result = caller.call<Unit> {
            throw httpError(
                401,
                """{"error":{"code":"TOKEN_EXPIRED","message":"x"}}""",
                token = "expired-token"
            )
        }

        assertEquals(ApiResult.Failure(ApiError.SessionExpired), result)
        assertEquals(listOf("expired-token"), expiredTokens)
    }

    @Test
    fun wrongPassword_doesNotClearTheSession() = runTest {
        val result = caller.call<Unit> {
            throw httpError(401, """{"error":{"code":"INVALID_CREDENTIALS","message":"x"}}""")
        }

        assertEquals(ApiResult.Failure(ApiError.InvalidCredentials), result)
        assertEquals(emptyList<String?>(), expiredTokens)
    }

    @Test
    fun ioProblem_meansNoConnection() = runTest {
        assertEquals(ApiResult.Failure(ApiError.NoConnection), caller.call<Unit> { throw IOException("offline") })
    }

    @Test
    fun timeout_isReportedSeparately() = runTest {
        assertEquals(ApiResult.Failure(ApiError.Timeout), caller.call<Unit> { throw SocketTimeoutException() })
    }

    @Test
    fun anythingUnexpected_becomesUnknown_insteadOfCrashing() = runTest {
        assertEquals(ApiResult.Failure(ApiError.Unknown), caller.call<Unit> { throw IllegalStateException("bug") })
    }
}