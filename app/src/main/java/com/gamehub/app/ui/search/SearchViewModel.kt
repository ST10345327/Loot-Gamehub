package com.gamehub.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.model.Game
import com.gamehub.app.data.model.GenreFilter
import com.gamehub.app.data.model.PlatformFilter
import com.gamehub.app.data.model.ReleasePeriod
import com.gamehub.app.data.model.SearchFilters
import com.gamehub.app.data.model.SortOption
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.GameRepository
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val results: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: ApiError? = null,
    val showFilterSheet: Boolean = false
)

private const val DEBOUNCE_MS = 400L

/** Search screen logic: debounces typing, re-runs the search immediately whenever a filter changes. */
class SearchViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank() && _uiState.value.filters.activeCount == 0) {
            _uiState.update { it.copy(results = emptyList(), hasSearched = false, isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS) // wait for the user to stop typing before calling the API
            runSearch()
        }
    }

    fun onGenreSelected(genre: GenreFilter?) = updateFilters { it.copy(genre = genre) }
    fun onPlatformSelected(platform: PlatformFilter?) = updateFilters { it.copy(platform = platform) }
    fun onMinRatingChange(rating: Int) = updateFilters { it.copy(minRating = rating) }
    fun onPeriodSelected(period: ReleasePeriod) = updateFilters { it.copy(period = period) }
    fun onSortSelected(sort: SortOption) = updateFilters { it.copy(sort = sort) }
    fun clearFilters() = updateFilters { SearchFilters() }
    fun setFilters(filters: SearchFilters) {
        _uiState.update { it.copy(filters = filters, showFilterSheet = false) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    fun openFilterSheet() { _uiState.update { it.copy(showFilterSheet = true) } }
    fun closeFilterSheet() { _uiState.update { it.copy(showFilterSheet = false) } }
    fun retry() { viewModelScope.launch { runSearch() } }

    private fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        _uiState.update { it.copy(filters = transform(it.filters)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    private suspend fun runSearch() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }

        when (val result = gameRepository.search(state.query.takeIf { it.isNotBlank() }, state.filters)) {
            is ApiResult.Success -> _uiState.update { it.copy(results = result.data, isLoading = false, hasSearched = true) }
            is ApiResult.Failure -> {
                AppLogger.warn("Search failed: ${result.error}")
                _uiState.update { it.copy(isLoading = false, hasSearched = true, error = result.error) }
            }
        }
    }
}