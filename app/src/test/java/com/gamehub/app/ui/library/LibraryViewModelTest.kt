package com.gamehub.app.ui.library

import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.testing.FakeLibraryRepository
import com.gamehub.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()

    private fun createViewModel() = LibraryViewModel(repository)

    @Test
    fun initialMode_loadsLibrary() = runTest {
        val games = listOf(FakeLibraryRepository.TEST_SAVED_GAME)
        repository.libraryResult = ApiResult.Success(games)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(games, state.allGames)
        assertEquals(games, state.visibleGames)
    }

    @Test
    fun filter_filtersVisibleGames() = runTest {
        val playing = FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 1, status = LibraryStatus.PLAYING)
        val completed = FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 2, status = LibraryStatus.COMPLETED)
        repository.libraryResult = ApiResult.Success(listOf(playing, completed))

        val viewModel = createViewModel()
        viewModel.selectFilter(LibraryStatus.PLAYING)

        val state = viewModel.uiState.value
        assertEquals(listOf(playing, completed), state.allGames)
        assertEquals(listOf(playing), state.visibleGames)
    }

    @Test
    fun removeGame_updatesState() = runTest {
        val game = FakeLibraryRepository.TEST_SAVED_GAME
        repository.libraryResult = ApiResult.Success(listOf(game))

        val viewModel = createViewModel()
        viewModel.removeGame(game.gameId)

        assertTrue(viewModel.uiState.value.allGames.isEmpty())
        assertEquals(listOf(game.gameId), repository.removeLibraryCalls)
    }

    @Test
    fun loadFailure_showsError() = runTest {
        repository.libraryResult = ApiResult.Failure(ApiError.NoConnection)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ApiError.NoConnection, state.error)
    }

    @Test
    fun refresh_reloadsLibraryAndSkipsWhileRequestIsInFlight() = runTest {
        repository.libraryGate = CompletableDeferred()
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.refresh()
        assertEquals(1, repository.getLibraryCalls.size)

        repository.libraryGate?.complete(Unit)
        assertFalse(viewModel.uiState.value.isLoading)
        repository.libraryGate = null
        viewModel.refresh()
        assertEquals(2, repository.getLibraryCalls.size)
    }
}
