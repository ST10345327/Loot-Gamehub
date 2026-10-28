package com.gamehub.app.testing

import com.gamehub.app.data.model.Game
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.model.SavedGame
import com.gamehub.app.data.remote.ApiResult
import com.gamehub.app.data.repository.LibraryRepository

class FakeLibraryRepository : LibraryRepository {
    var libraryResult: ApiResult<List<SavedGame>> = ApiResult.Success(emptyList())
    var favoritesResult: ApiResult<List<SavedGame>> = ApiResult.Success(emptyList())
    var wishlistResult: ApiResult<List<SavedGame>> = ApiResult.Success(emptyList())
    var saveResult: ApiResult<SavedGame> = ApiResult.Success(TEST_SAVED_GAME)
    var removeResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var moveResult: ApiResult<SavedGame> = ApiResult.Success(TEST_SAVED_GAME)

    var getLibraryCalls = 0
    val removeLibraryCalls = mutableListOf<Int>()
    val removeWishlistCalls = mutableListOf<Int>()
    val removeFavoriteCalls = mutableListOf<Int>()
    val moveToLibraryCalls = mutableListOf<Pair<Int, LibraryStatus>>()

    override suspend fun getLibrary(status: LibraryStatus?): ApiResult<List<SavedGame>> {
        getLibraryCalls++
        return libraryResult
    }

    override suspend fun saveToLibrary(game: Game, status: LibraryStatus): ApiResult<SavedGame> = saveResult

    override suspend fun removeFromLibrary(gameId: Int): ApiResult<Unit> {
        removeLibraryCalls.add(gameId)
        return removeResult
    }

    override suspend fun getFavourites(): ApiResult<List<SavedGame>> = favoritesResult

    override suspend fun addFavourite(game: Game): ApiResult<SavedGame> = saveResult

    override suspend fun removeFavourite(gameId: Int): ApiResult<Unit> {
        removeFavoriteCalls.add(gameId)
        return removeResult
    }

    override suspend fun getWishlist(): ApiResult<List<SavedGame>> = wishlistResult

    override suspend fun addToWishlist(game: Game): ApiResult<SavedGame> = saveResult

    override suspend fun removeFromWishlist(gameId: Int): ApiResult<Unit> {
        removeWishlistCalls.add(gameId)
        return removeResult
    }

    override suspend fun moveToLibrary(gameId: Int, status: LibraryStatus): ApiResult<SavedGame> {
        moveToLibraryCalls.add(gameId to status)
        return moveResult
    }

    companion object {
        val TEST_SAVED_GAME = SavedGame(gameId = 1, name = "Test Game", coverUrl = null, rating = 8.5, releaseYear = 2024, genre = "Action", platforms = "PC", status = LibraryStatus.WANT_TO_PLAY)
    }
}
