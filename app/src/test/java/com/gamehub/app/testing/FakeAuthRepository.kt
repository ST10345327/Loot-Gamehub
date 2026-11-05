package com.gamehub.app.testing

import com.gamehub.app.data.model.User
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A stand-in for the real repository. Tests set the result each call should return and can
 * afterwards check which calls were made, without any network.
 */
class FakeAuthRepository : AuthRepository {

    var loginResult: ApiResult<User> = ApiResult.Success(TEST_USER)
    var registerResult: ApiResult<User> = ApiResult.Success(TEST_USER)
    var googleLoginResult: ApiResult<User> = ApiResult.Success(TEST_USER)
    var forgotPasswordResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Triple<String, String, String>>()
    val googleLoginCalls = mutableListOf<String>()
    val forgotPasswordCalls = mutableListOf<String>()

    private val user = MutableStateFlow<User?>(null)
    private val seen = MutableStateFlow(false)

    override val currentUser: Flow<User?> = user
    override val onboardingSeen: Flow<Boolean> = seen

    fun setSignedIn(value: User?) { user.value = value }
    fun setOnboardingSeen(value: Boolean) { seen.value = value }

    override suspend fun register(username: String, email: String, password: String): ApiResult<User> {
        registerCalls.add(Triple(username, email, password))
        return registerResult
    }

    override suspend fun login(email: String, password: String): ApiResult<User> {
        loginCalls.add(email to password)
        return loginResult
    }

    override suspend fun signInWithGoogle(idToken: String): ApiResult<User> {
        googleLoginCalls.add(idToken)
        return googleLoginResult
    }

    override suspend fun forgotPassword(email: String): ApiResult<Unit> {
        forgotPasswordCalls.add(email)
        return forgotPasswordResult
    }

    override suspend fun signOut() { user.value = null }

    override suspend fun markOnboardingSeen() { seen.value = true }

    companion object {
        val TEST_USER = User(id = 1, username = "tester", email = "tester@example.com")
    }
}