package com.gamehub.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamehub.app.data.model.GameDetails
import com.gamehub.app.data.model.GameStatus
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.GameRepository
import com.gamehub.app.data.repository.LibraryRepository
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameDetailsUiState(
    val isLoading: Boolean = true,
    val details: GameDetails? = null,
    val status: GameStatus = GameStatus(),
    val error: ApiError? = null,
    val statusError: ApiError? = null,
    /** True while a favourite/wishlist/library tap is in flight, to stop double taps. */
    val isUpdating: Boolean = false
)

class GameDetailsViewModel(
    private val gameId: Int,
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailsUiState())
    val uiState: StateFlow<GameDetailsUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val detailsResult = gameRepository.details(gameId)
            val statusResult = gameRepository.status(gameId)

            when (detailsResult) {
                is ApiResult.Success -> _uiState.update {
                    val loadedStatus = statusResult as? ApiResult.Success
                    val failedStatus = statusResult as? ApiResult.Failure
                    if (failedStatus != null) {
                        AppLogger.warn("Game status failed to load: ${failedStatus.error}")
                    }
                    it.copy(
                        isLoading = false,
                        details = detailsResult.data,
                        status = loadedStatus?.data ?: GameStatus(),
                        statusError = failedStatus?.error
                    )
                }
                is ApiResult.Failure -> {
                    AppLogger.warn("Game details failed to load: ${detailsResult.error}")
                    _uiState.update { it.copy(isLoading = false, error = detailsResult.error) }
                }
            }
        }
    }

    fun toggleFavourite() {
        val current = _uiState.value
        if (current.statusError != null) return
        val details = current.details ?: return
        val currentlyFavourite = current.status.isFavourite
        runUpdate {
            val result = if (currentlyFavourite) libraryRepository.removeFavourite(gameId) else libraryRepository.addFavourite(details.game)
            if (result is ApiResult.Success) _uiState.update { it.copy(status = it.status.copy(isFavourite = !currentlyFavourite)) }
            (result as? ApiResult.Failure)?.error
        }
    }

    fun toggleWishlist() {
        val current = _uiState.value
        if (current.statusError != null) return
        val details = current.details ?: return
        val currentlyWishlisted = current.status.isInWishlist
        runUpdate {
            val result = if (currentlyWishlisted) libraryRepository.removeFromWishlist(gameId) else libraryRepository.addToWishlist(details.game)
            if (result is ApiResult.Success) _uiState.update { it.copy(status = it.status.copy(isInWishlist = !currentlyWishlisted)) }
            (result as? ApiResult.Failure)?.error
        }
    }

    /** Tapping the already-selected status removes the game from the library (status = null). */
    fun setLibraryStatus(status: LibraryStatus?) {
        val current = _uiState.value
        if (current.statusError != null) return
        val details = current.details ?: return
        runUpdate {
            val result = if (status == null) libraryRepository.removeFromLibrary(gameId) else libraryRepository.saveToLibrary(details.game, status)
            if (result is ApiResult.Success) _uiState.update { it.copy(status = it.status.copy(libraryStatus = status)) }
            (result as? ApiResult.Failure)?.error
        }
    }

    private fun runUpdate(action: suspend () -> ApiError?) {
        if (_uiState.value.isUpdating) return
        _uiState.update { it.copy(isUpdating = true, error = null) }
        viewModelScope.launch {
            val error = action()
            if (error != null) AppLogger.warn("Game action failed: $error")
            _uiState.update { it.copy(isUpdating = false, error = error) }
        }
    }

    companion object {
        /** Built per-screen (not through the shared factory) because it needs the game id from navigation. */
        fun factory(gameId: Int, gameRepository: GameRepository, libraryRepository: LibraryRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { GameDetailsViewModel(gameId, gameRepository, libraryRepository) } }
    }
}