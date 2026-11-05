package com.gamehub.app.ui.common

import androidx.annotation.StringRes
import com.gamehub.app.R
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.utils.FieldError

/** Maps an API failure to a translated message. `when` must cover every case, so adding a new error forces a message. */
@StringRes
fun ApiError.messageRes(): Int = when (this) {
    ApiError.NoConnection -> R.string.error_no_connection
    ApiError.Timeout -> R.string.error_timeout
    ApiError.SessionExpired -> R.string.error_session_expired
    ApiError.InvalidCredentials -> R.string.error_invalid_credentials
    ApiError.EmailTaken -> R.string.field_email_taken
    ApiError.UsernameTaken -> R.string.field_username_taken
    ApiError.WrongPassword -> R.string.error_wrong_password
    ApiError.NotFound -> R.string.error_not_found
    ApiError.RateLimited -> R.string.error_rate_limited
    ApiError.GameServiceUnavailable -> R.string.error_service_unavailable
    ApiError.SecureSessionUnavailable -> R.string.error_secure_session_storage
    is ApiError.Validation -> R.string.error_validation
    is ApiError.Server -> R.string.error_server
    ApiError.Unknown -> R.string.error_unknown
}

/** Maps a form-field problem to a translated message. */
@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.field_required
    FieldError.INVALID_EMAIL -> R.string.field_invalid_email
    FieldError.USERNAME_LENGTH -> R.string.field_username_length
    FieldError.USERNAME_CHARACTERS -> R.string.field_username_chars
    FieldError.PASSWORD_TOO_SHORT -> R.string.field_password_short
    FieldError.PASSWORD_TOO_LONG -> R.string.field_password_long
    FieldError.PASSWORD_NEEDS_NUMBER -> R.string.field_password_number
    FieldError.PASSWORDS_DO_NOT_MATCH -> R.string.field_passwords_mismatch
    FieldError.EMAIL_TAKEN -> R.string.field_email_taken
    FieldError.USERNAME_TAKEN -> R.string.field_username_taken
}