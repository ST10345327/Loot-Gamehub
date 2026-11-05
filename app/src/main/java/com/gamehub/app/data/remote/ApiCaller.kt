package com.gamehub.app.data.remote

import com.gamehub.app.data.local.SessionStorageException
import com.gamehub.app.utils.AppLogger
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Runs a Retrofit call and converts every outcome into an [ApiResult], so repositories and
 * ViewModels never need try/catch of their own and the app cannot crash on a network error.
 *
 * [onSessionExpired] is invoked when the server says our token is no longer valid; the
 * container uses it to delete the saved session, which sends the user back to Login.
 */
class ApiCaller(private val onSessionExpired: suspend () -> Unit) {

    suspend fun <T> call(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation (e.g. the user left the screen)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            val error = ApiErrorMapper.fromHttp(e.code(), body)
            AppLogger.warn("HTTP ${e.code()} -> $error")
            if (error == ApiError.SessionExpired) onSessionExpired()
            ApiResult.Failure(error)
        } catch (e: SocketTimeoutException) {
            AppLogger.warn("Request timed out")
            ApiResult.Failure(ApiError.Timeout)
        } catch (e: SSLException) {
            AppLogger.warn("Secure connection failed (${e.javaClass.simpleName})")
            ApiResult.Failure(ApiError.Server(null))
        } catch (e: SessionStorageException) {
            AppLogger.error("Secure session storage is unavailable", e)
            ApiResult.Failure(ApiError.SecureSessionUnavailable)
        } catch (e: IOException) {
            AppLogger.warn("Network problem: ${e.message}")
            ApiResult.Failure(ApiError.NoConnection)
        } catch (e: Exception) {
            AppLogger.error("Unexpected error during API call", e)
            ApiResult.Failure(ApiError.Unknown)
        }
    }
}