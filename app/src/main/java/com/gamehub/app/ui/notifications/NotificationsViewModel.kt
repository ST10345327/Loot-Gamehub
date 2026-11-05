package com.gamehub.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.model.GameNotification
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.NotificationRepository
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<GameNotification> = emptyList(),
    val error: ApiError? = null
)

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = notificationRepository.getNotifications()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, notifications = result.data) }
                }
                is ApiResult.Failure -> {
                    AppLogger.warn("Failed to load notifications: ${result.error}")
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
                }
            }
        }
    }

    fun markAsRead(id: Int) {
        viewModelScope.launch {
            when (notificationRepository.markAsRead(id)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            notifications = state.notifications.map {
                                if (it.id == id) it.copy(isRead = true) else it
                            }
                        )
                    }
                }
                is ApiResult.Failure -> {
                    AppLogger.warn("Failed to mark notification $id as read")
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            when (notificationRepository.markAllAsRead()) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            notifications = state.notifications.map { it.copy(isRead = true) }
                        )
                    }
                }
                is ApiResult.Failure -> {
                    AppLogger.warn("Failed to mark all notifications as read")
                }
            }
        }
    }
}
