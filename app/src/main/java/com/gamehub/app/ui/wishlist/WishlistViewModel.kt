package com.gamehub.app.ui.wishlist

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

enum class WishlistTab { WISHLIST, FAVOURITES }

data class WishlistUiState(
    val tab: WishlistTab = WishlistTab.WISHLIST,
    val wishlist: List<SavedGame> = emptyList(),
    val favourites: List<SavedGame> = emptyList(),
    val isLoading: Boolean = true,
    val error: ApiError? = null
) {
    val visibleGames: List<SavedGame> get() = if (tab == WishlistTab.WISHLIST) wishlist else favourites
}

class WishlistViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()
    fun refresh() = load()
    fun selectTab(tab: WishlistTab) { _uiState.update { it.copy(tab = tab) } }

    fun removeWishlistGame(gameId: Int) {
        viewModelScope.launch {
            when (val result = libraryRepository.removeFromWishlist(gameId)) {
                is ApiResult.Success -> _uiState.update { it.copy(wishlist = it.wishlist.filterNot { g -> g.gameId == gameId }) }
                is ApiResult.Failure -> AppLogger.warn("Could not remove wishlist game: ${result.error}")
            }
        }
    }

    fun removeFavourite(gameId: Int) {
        viewModelScope.launch {
            when (val result = libraryRepository.removeFavourite(gameId)) {
                is ApiResult.Success -> _uiState.update { it.copy(favourites = it.favourites.filterNot { g -> g.gameId == gameId }) }
                is ApiResult.Failure -> AppLogger.warn("Could not remove favourite: ${result.error}")
            }
        }
    }

    /** Moves a wishlist game into the library (as Want to Play), then drops it from this screen's wishlist. */
    fun moveToLibrary(gameId: Int) {
        viewModelScope.launch {
            when (val result = libraryRepository.moveToLibrary(gameId, LibraryStatus.WANT_TO_PLAY)) {
                is ApiResult.Success -> {
                    AppLogger.debug("Moved game $gameId to library")
                    _uiState.update { it.copy(wishlist = it.wishlist.filterNot { g -> g.gameId == gameId }) }
                }
                is ApiResult.Failure -> AppLogger.warn("Could not move game to library: ${result.error}")
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val wishlistResult = libraryRepository.getWishlist()
            val favouritesResult = libraryRepository.getFavourites()
            val firstError = (wishlistResult as? ApiResult.Failure)?.error ?: (favouritesResult as? ApiResult.Failure)?.error

            _uiState.update {
                it.copy(
                    wishlist = (wishlistResult as? ApiResult.Success)?.data ?: emptyList(),
                    favourites = (favouritesResult as? ApiResult.Success)?.data ?: emptyList(),
                    isLoading = false,
                    error = firstError
                )
            }
        }
    }
}