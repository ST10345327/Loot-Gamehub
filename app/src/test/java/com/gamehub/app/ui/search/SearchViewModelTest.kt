package com.gamehub.app.ui.search

import com.gamehub.app.data.model.GenreFilter
import com.gamehub.app.data.model.SearchFilters
import com.gamehub.app.data.model.PlatformFilter
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.testing.FakeGameRepository
import com.gamehub.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeGameRepository()

    private fun createViewModel() = SearchViewModel(repository)

    @Test
    fun initialMode_isNotLoading_andHasNoResults() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hasSearched)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun typingQuery_debounces_andRunsSearch() = runTest {
        val viewModel = createViewModel()
        val games = listOf(FakeGameRepository.TEST_GAME)
        repository.searchResult = ApiResult.Success(games)

        viewModel.onQueryChange("Elden")

        // Before debounce
        assertFalse(viewModel.uiState.value.isLoading)

        advanceTimeBy(500) // Default debounce is 400ms

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasSearched)
        assertEquals(games, state.results)
        assertEquals("Elden", repository.searchCalls.first().first)
    }

    @Test
    fun filterChange_runsSearchImmediately() = runTest {
        val viewModel = createViewModel()
        repository.searchResult = ApiResult.Success(emptyList())

        viewModel.onGenreSelected(GenreFilter.RPG)

        val state = viewModel.uiState.value
        assertTrue(state.hasSearched)
        assertEquals(GenreFilter.RPG, state.filters.genre)
        assertEquals(1, repository.searchCalls.size)
    }

    @Test
    fun apiFailure_showsError() = runTest {
        repository.searchResult = ApiResult.Failure(ApiError.GameServiceUnavailable)
        val viewModel = createViewModel()

        viewModel.onQueryChange("Test")
        advanceTimeBy(500)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ApiError.GameServiceUnavailable, state.error)
    }

    @Test
    fun clearFilters_resetsState() = runTest {
        val viewModel = createViewModel()
        viewModel.onGenreSelected(GenreFilter.RPG)
        assertTrue(viewModel.uiState.value.filters.genre != null)

        viewModel.clearFilters()

        assertNull(viewModel.uiState.value.filters.genre)
    }

    @Test
    fun setFilters_appliesAllValuesInOneSearch() = runTest {
        val viewModel = createViewModel()
        val filters = SearchFilters(genre = GenreFilter.RPG, platform = PlatformFilter.PS5, minRating = 8)

        viewModel.setFilters(filters)

        assertEquals(filters, viewModel.uiState.value.filters)
        assertEquals(1, repository.searchCalls.size)
        assertEquals(filters, repository.searchCalls.single().second)
    }
}
