package com.gamehub.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.AuthRepository
import com.gamehub.app.utils.AppLogger
import com.gamehub.app.utils.FieldError
import com.gamehub.app.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the login screen shows. The screen only draws this; it holds no logic. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isLoading: Boolean = false,
    val error: ApiError? = null,
    val isLoggedIn: Boolean = false,
    /** Non-null while the "Forgot password" dialog is open. */
    val forgotPassword: ForgotPasswordState? = null
)

/** State of the "Forgot password" dialog. */
data class ForgotPasswordState(
    val email: String = "",
    val emailError: FieldError? = null,
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val error: ApiError? = null
)

/**
 * Logic for the login screen: validates the input, calls the repository and exposes the
 * result as [uiState]. Because the state lives here, typed text survives screen rotation.
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, error = null) }
    }

    fun login() {
        val current = _uiState.value
        if (current.isLoading) return // ignore taps while a request is running

        // Validate first: no network call is made for obviously invalid input.
        val emailError = Validators.validateEmail(current.email)
        val passwordError = Validators.validateExistingPassword(current.password)
        if (emailError != null || passwordError != null) {
            AppLogger.debug("Login blocked by validation")
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(current.email, current.password)) {
                is ApiResult.Success -> {
                    AppLogger.debug("Login succeeded")
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                is ApiResult.Failure -> {
                    AppLogger.warn("Login failed: ${result.error}")
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        val current = _uiState.value
        if (current.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(idToken)) {
                is ApiResult.Success -> {
                    AppLogger.debug("Google sign-in succeeded")
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                is ApiResult.Failure -> {
                    AppLogger.warn("Google sign-in failed: ${result.error}")
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
                }
            }
        }
    }

    // ---- Forgot password dialog -------------------------------------------------------

    fun openForgotPassword() {
        // Pre-fill with whatever the user already typed as their email.
        _uiState.update { it.copy(forgotPassword = ForgotPasswordState(email = it.email)) }
    }

    fun onForgotEmailChange(value: String) {
        _uiState.update { state ->
            state.copy(forgotPassword = state.forgotPassword?.copy(email = value, emailError = null, error = null))
        }
    }

    fun dismissForgotPassword() {
        _uiState.update { it.copy(forgotPassword = null) }
    }

    fun sendResetLink() {
        val dialog = _uiState.value.forgotPassword ?: return
        if (dialog.isSending) return

        val emailError = Validators.validateEmail(dialog.email)
        if (emailError != null) {
            _uiState.update { it.copy(forgotPassword = dialog.copy(emailError = emailError)) }
            return
        }

        _uiState.update { it.copy(forgotPassword = dialog.copy(isSending = true, error = null)) }
        viewModelScope.launch {
            when (val result = authRepository.forgotPassword(dialog.email)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(forgotPassword = state.forgotPassword?.copy(isSending = false, sent = true))
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    state.copy(forgotPassword = state.forgotPassword?.copy(isSending = false, error = result.error))
                }
            }
        }
    }
}