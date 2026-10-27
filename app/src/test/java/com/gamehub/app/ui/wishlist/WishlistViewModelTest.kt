package com.gamehub.app.ui.wishlist

import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.remote.ApiError
import com.gamehub.app.data.remote.ApiResult
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
class WishlistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()

    private fun createViewModel() = WishlistViewModel(repository)

    @Test
    fun initialMode_loadsWishlistAndFavorites() = runTest {
        val wishlist = listOf(FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 1))
        val favorites = listOf(FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 2))
        repository.wishlistResult = ApiResult.Success(wishlist)
        repository.favoritesResult = ApiResult.Success(favorites)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(wishlist, state.wishlist)
        assertEquals(favorites, state.favourites)
        assertEquals(wishlist, state.visibleGames)
    }

    @Test
    fun tabSwitch_changesVisibleGames() = runTest {
        val wishlist = listOf(FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 1))
        val favorites = listOf(FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 2))
        repository.wishlistResult = ApiResult.Success(wishlist)
        repository.favoritesResult = ApiResult.Success(favorites)

        val viewModel = createViewModel()
        viewModel.selectTab(WishlistTab.FAVOURITES)

        val state = viewModel.uiState.value
        assertEquals(favorites, state.visibleGames)
    }

    @Test
    fun refresh_reloadsFavorites() = runTest {
        val viewModel = createViewModel()
        val favorites = listOf(FakeLibraryRepository.TEST_SAVED_GAME.copy(gameId = 3))
        repository.favoritesResult = ApiResult.Success(favorites)

        viewModel.refresh()
        viewModel.selectTab(WishlistTab.FAVOURITES)

        assertEquals(favorites, viewModel.uiState.value.visibleGames)
    }

    @Test
    fun moveToLibrary_removesFromWishlist() = runTest {
        val game = FakeLibraryRepository.TEST_SAVED_GAME
        repository.wishlistResult = ApiResult.Success(listOf(game))

        val viewModel = createViewModel()
        viewModel.moveToLibrary(game.gameId)

        assertTrue(viewModel.uiState.value.wishlist.isEmpty())
        assertEquals(listOf(game.gameId to LibraryStatus.WANT_TO_PLAY), repository.moveToLibraryCalls)
    }

    @Test
    fun loadFailure_showsError() = runTest {
        repository.wishlistResult = ApiResult.Failure(ApiError.NoConnection)

        val viewModel = createViewModel()

        assertEquals(ApiError.NoConnection, viewModel.uiState.value.error)
    }
}
