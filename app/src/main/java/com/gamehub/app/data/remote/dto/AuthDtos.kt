package com.gamehub.app.data.remote.dto

/*
 * DTOs ("data transfer objects") mirror the JSON the REST API sends and receives.
 * Response fields are nullable with defaults because Gson fills missing fields with null;
 * the repository checks them before turning a DTO into a model.
 */

data class RegisterRequest(val username: String, val email: String, val password: String)

data class LoginRequest(val email: String, val password: String)

data class ForgotPasswordRequest(val email: String)

data class GoogleLoginRequest(val idToken: String)

data class MessageResponse(val message: String? = null)

data class UserDto(
    val id: Int? = null,
    val username: String? = null,
    val email: String? = null
)

/** Answer of /auth/register and /auth/login: a login token plus the user. */
data class AuthResponse(val token: String? = null, val user: UserDto? = null)

/** Shape of every error the API returns: { "error": { "code": "...", "message": "..." } }. */
data class ErrorResponse(val error: ErrorBody? = null)

data class ErrorBody(val code: String? = null, val message: String? = null)

data class SettingsRequest(
    val preferredLanguage: String? = null,
    val themePreference: String? = null,
    val notificationsEnabled: Boolean? = null,
    val wishlistAlerts: Boolean? = null,
    val releaseAlerts: Boolean? = null
)