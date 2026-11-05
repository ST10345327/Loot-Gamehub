package com.gamehub.app.data.remote

/**
 * Every network call ends up as either [Success] or [Failure]. Repositories never throw for
 * expected problems such as "no internet" or "wrong password"; ViewModels turn a [Failure]
 * into a message the user can read.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/**
 * The kinds of failure the UI knows how to explain. Keeping these as types (not English
 * strings) means the messages can be translated into Setswana and isiZulu.
 */
sealed interface ApiError {
    data object NoConnection : ApiError
    data object Timeout : ApiError
    data object SessionExpired : ApiError
    data object InvalidCredentials : ApiError
    data object EmailTaken : ApiError
    data object UsernameTaken : ApiError
    data object WrongPassword : ApiError
    data object NotFound : ApiError
    data object RateLimited : ApiError
    data object GameServiceUnavailable : ApiError
    data object SecureSessionUnavailable : ApiError
    data class Validation(val message: String?) : ApiError
    data class Server(val message: String?) : ApiError
    data object Unknown : ApiError
}

/** Transforms the data of a successful result; a failure passes through unchanged. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}