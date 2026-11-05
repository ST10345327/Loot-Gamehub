package com.gamehub.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.model.User
import com.gamehub.app.data.repository.AuthRepository
import com.gamehub.app.data.repository.NotificationRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide view of the sign-in state. The navigation host watches [isLoggedIn]: when it turns
 * false (sign out, or the server rejecting an expired token) the user is sent to Login.
 */
class SessionViewModel(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    /** The signed-in user, or null. */
    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null while the saved session is still being read from disk; then true or false. */
    val isLoggedIn: StateFlow<Boolean?> = authRepository.currentUser
        .map { user -> user != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    try {
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val token = task.result
                                if (!token.isNullOrBlank()) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        try {
                                            notificationRepository.registerFcmToken(token)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun markOnboardingSeen() {
        viewModelScope.launch { authRepository.markOnboardingSeen() }
    }
}
