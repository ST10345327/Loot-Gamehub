package com.gamehub.app.ui.auth

import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.testing.FakeAuthRepository
import com.gamehub.app.testing.MainDispatcherRule
import com.gamehub.app.utils.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    private fun createViewModel() = LoginViewModel(repository)

    @Test
    fun emptyFields_showErrors_andNeverCallTheServer() {
        val viewModel = createViewModel()

        viewModel.login()

        val state = viewModel.uiState.value
        assertEquals(FieldError.REQUIRED, state.emailError)
        assertEquals(FieldError.REQUIRED, state.passwordError)
        assertTrue(repository.loginCalls.isEmpty())
        assertFalse(state.isLoggedIn)
    }

    @Test
    fun malformedEmail_isRejectedLocally() {
        val viewModel = createViewModel()
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("secret123")

        viewModel.login()

        assertEquals(FieldError.INVALID_EMAIL, viewModel.uiState.value.emailError)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun validCredentials_logIn_andPassThemToTheRepository() {
        val viewModel = createViewModel()
        viewModel.onEmailChange("player@example.com")
        viewModel.onPasswordChange("secret123")

        viewModel.login()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("player@example.com" to "secret123"), repository.loginCalls)
    }

    @Test
    fun wrongPassword_showsTheServerError_andStaysOnTheScreen() {
        repository.loginResult = ApiResult.Failure(ApiError.InvalidCredentials)
        val viewModel = createViewModel()
        viewModel.onEmailChange("player@example.com")
        viewModel.onPasswordChange("wrong-password1")

        viewModel.login()

        val state = viewModel.uiState.value
        assertEquals(ApiError.InvalidCredentials, state.error)
        assertFalse(state.isLoggedIn)
        assertFalse(state.isLoading)
    }

    @Test
    fun typingAgain_clearsThePreviousError() {
        repository.loginResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = createViewModel()
        viewModel.onEmailChange("player@example.com")
        viewModel.onPasswordChange("secret123")
        viewModel.login()
        assertEquals(ApiError.NoConnection, viewModel.uiState.value.error)

        viewModel.onPasswordChange("secret1234")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun forgotPassword_validatesThenReportsSuccess() {
        val viewModel = createViewModel()
        viewModel.openForgotPassword()
        assertNotNull(viewModel.uiState.value.forgotPassword)

        viewModel.sendResetLink() // empty email
        assertEquals(FieldError.REQUIRED, viewModel.uiState.value.forgotPassword?.emailError)
        assertTrue(repository.forgotPasswordCalls.isEmpty())

        viewModel.onForgotEmailChange("player@example.com")
        viewModel.sendResetLink()

        assertEquals(true, viewModel.uiState.value.forgotPassword?.sent)
        assertEquals(listOf("player@example.com"), repository.forgotPasswordCalls)

        viewModel.dismissForgotPassword()
        assertNull(viewModel.uiState.value.forgotPassword)
    }

    @Test
    fun validGoogleToken_logsIn_andPassesTokenToRepository() {
        val viewModel = createViewModel()
        viewModel.signInWithGoogle("firebase-id-token-123")

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("firebase-id-token-123"), repository.googleLoginCalls)
    }
}