package com.gamehub.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.model.DiscoverCategory
import com.gamehub.app.data.model.Game
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.GameRepository
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class HomeUiState(
    val trending: List<Game> = emptyList(),
    val popular: List<Game> = emptyList(),
    val isLoading: Boolean = true,
    val error: ApiError? = null
)

/** Loads the Trending and Popular rows shown on Home concurrently. */
class HomeViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val (trending, popular) = coroutineScope {
                val trendingRequest = async { gameRepository.discover(DiscoverCategory.TRENDING) }
                val popularRequest = async { gameRepository.discover(DiscoverCategory.POPULAR) }
                trendingRequest.await() to popularRequest.await()
            }
            val firstError = (trending as? ApiResult.Failure)?.error ?: (popular as? ApiResult.Failure)?.error
            if (firstError != null) AppLogger.warn("Home rows failed to load: $firstError")

            _uiState.update {
                it.copy(
                    trending = (trending as? ApiResult.Success)?.data ?: emptyList(),
                    popular = (popular as? ApiResult.Success)?.data ?: emptyList(),
                    isLoading = false,
                    error = firstError
                )
            }
        }
    }
}