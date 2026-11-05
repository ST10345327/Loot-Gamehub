package com.gamehub.app.ui.details

import com.gamehub.app.data.model.GameStatus
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.testing.FakeGameRepository
import com.gamehub.app.testing.FakeLibraryRepository
import com.gamehub.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gameRepository = FakeGameRepository()
    private val libraryRepository = FakeLibraryRepository()

    private fun createViewModel(gameId: Int = 1) = GameDetailsViewModel(gameId, gameRepository, libraryRepository)

    @Test
    fun initialMode_loadsDetailsAndStatus() = runTest {
        val details = FakeGameRepository.TEST_DETAILS
        val status = GameStatus(isFavourite = true)
        gameRepository.detailsResult = ApiResult.Success(details)
        gameRepository.statusResult = ApiResult.Success(status)

        val viewModel = createViewModel(details.game.id)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(details, state.details)
        assertEquals(status, state.status)
    }

    @Test
    fun toggleFavorite_updatesState() = runTest {
        gameRepository.detailsResult = ApiResult.Success(FakeGameRepository.TEST_DETAILS)
        gameRepository.statusResult = ApiResult.Success(GameStatus(isFavourite = false))
        libraryRepository.saveResult = ApiResult.Success(FakeLibraryRepository.TEST_SAVED_GAME)

        val viewModel = createViewModel()
        viewModel.toggleFavourite()

        assertTrue(viewModel.uiState.value.status.isFavourite)
        assertFalse(viewModel.uiState.value.isUpdating)
    }

    @Test
    fun toggleWishlist_updatesState() = runTest {
        gameRepository.detailsResult = ApiResult.Success(FakeGameRepository.TEST_DETAILS)
        gameRepository.statusResult = ApiResult.Success(GameStatus(isInWishlist = false))
        libraryRepository.saveResult = ApiResult.Success(FakeLibraryRepository.TEST_SAVED_GAME)

        val viewModel = createViewModel()
        viewModel.toggleWishlist()

        assertTrue(viewModel.uiState.value.status.isInWishlist)
    }

    @Test
    fun setLibraryStatus_updatesState() = runTest {
        gameRepository.detailsResult = ApiResult.Success(FakeGameRepository.TEST_DETAILS)
        libraryRepository.saveResult = ApiResult.Success(FakeLibraryRepository.TEST_SAVED_GAME)

        val viewModel = createViewModel()
        viewModel.setLibraryStatus(LibraryStatus.PLAYING)

        assertEquals(LibraryStatus.PLAYING, viewModel.uiState.value.status.libraryStatus)
    }

    @Test
    fun loadFailure_showsError() = runTest {
        gameRepository.detailsResult = ApiResult.Failure(ApiError.NoConnection)

        val viewModel = createViewModel()

        assertEquals(ApiError.NoConnection, viewModel.uiState.value.error)
    }

    @Test
    fun statusFailure_isExposedAndPreventsMutationsWithUnknownStatus() = runTest {
        gameRepository.detailsResult = ApiResult.Success(FakeGameRepository.TEST_DETAILS)
        gameRepository.statusResult = ApiResult.Failure(ApiError.NoConnection)

        val viewModel = createViewModel()

        assertEquals(FakeGameRepository.TEST_DETAILS, viewModel.uiState.value.details)
        assertEquals(ApiError.NoConnection, viewModel.uiState.value.statusError)
        viewModel.toggleFavourite()
        viewModel.toggleWishlist()

        assertTrue(libraryRepository.addFavoriteCalls.isEmpty())
        assertTrue(libraryRepository.addWishlistCalls.isEmpty())
    }
}
