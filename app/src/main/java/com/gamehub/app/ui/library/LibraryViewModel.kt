package com.gamehub.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.model.SavedGame
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.LibraryRepository
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val allGames: List<SavedGame> = emptyList(),
    val selectedFilter: LibraryStatus? = null,
    val isLoading: Boolean = true,
    val error: ApiError? = null
) {
    /** Games shown for the currently selected tab; null means "All". */
    val visibleGames: List<SavedGame> get() = if (selectedFilter == null) allGames else allGames.filter { it.status == selectedFilter }
}

class LibraryViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var hasResumedOnce = false
    private var refreshPending = false

    init { load() }

    fun retry() = load()
    fun onResume() {
        if (hasResumedOnce) {
            if (_uiState.value.isLoading) refreshPending = true else load()
        }
        hasResumedOnce = true
    }

    fun selectFilter(status: LibraryStatus?) { _uiState.update { it.copy(selectedFilter = status) } }

    fun removeGame(gameId: Int) {
        viewModelScope.launch {
            when (val result = libraryRepository.removeFromLibrary(gameId)) {
                is ApiResult.Success -> _uiState.update { it.copy(allGames = it.allGames.filterNot { g -> g.gameId == gameId }) }
                is ApiResult.Failure -> AppLogger.warn("Could not remove library game: ${result.error}")
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = libraryRepository.getLibrary()) {
                is ApiResult.Success -> _uiState.update { it.copy(allGames = result.data, isLoading = false) }
                is ApiResult.Failure -> {
                    AppLogger.warn("Library failed to load: ${result.error}")
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
                }
            }
            if (refreshPending) {
                refreshPending = false
                load()
            }
        }
    }
}