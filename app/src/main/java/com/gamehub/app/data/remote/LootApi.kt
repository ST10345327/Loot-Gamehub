package com.gamehub.app.data.remote

import com.gamehub.app.data.remote.dto.AuthResponse
import com.gamehub.app.data.remote.dto.FcmTokenRequest
import com.gamehub.app.data.remote.dto.ForgotPasswordRequest
import com.gamehub.app.data.remote.dto.GoogleLoginRequest
import com.gamehub.app.data.remote.dto.GameDetailsDto
import com.gamehub.app.data.remote.dto.GameDto
import com.gamehub.app.data.remote.dto.GameStatusDto
import com.gamehub.app.data.remote.dto.LoginRequest
import com.gamehub.app.data.remote.dto.MessageResponse
import com.gamehub.app.data.remote.dto.MoveToLibraryRequest
import com.gamehub.app.data.remote.dto.NotificationListDto
import com.gamehub.app.data.remote.dto.RegisterRequest
import com.gamehub.app.data.remote.dto.SavedGameDto
import com.gamehub.app.data.remote.dto.SavedGameListDto
import com.gamehub.app.data.remote.dto.SavedGameRequest
import com.gamehub.app.data.remote.dto.SearchResponseDto
import com.gamehub.app.data.remote.dto.UnreadCountDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import com.gamehub.app.data.remote.dto.SettingsRequest
import com.gamehub.app.data.remote.dto.UserDto

/** The app's own REST API. See backend/README.md for the full endpoint list. */
interface LootApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): AuthResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): MessageResponse

    @PATCH("me/settings")
    suspend fun updateSettings(@Body body: SettingsRequest): UserDto

    // ---- Games (IGDB proxy) ------------------------------------------------------------

    @GET("games/search")
    suspend fun searchGames(
        @Query("q") q: String?,
        @Query("genre") genre: String?,
        @Query("platform") platform: String?,
        @Query("minRating") minRating: Int?,
        @Query("releasedAfter") releasedAfter: Int?,
        @Query("releasedBefore") releasedBefore: Int?,
        @Query("sort") sort: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): SearchResponseDto

    @GET("games/discover/{category}")
    suspend fun discoverGames(@Path("category") category: String): SearchResponseDto

    @GET("games/random")
    suspend fun randomGame(
        @Query("genre") genre: String?,
        @Query("platform") platform: String?,
        @Query("minRating") minRating: Int?
    ): GameDto

    @GET("games/{id}")
    suspend fun gameDetails(@Path("id") id: Int): GameDetailsDto

    @GET("me/games/{gameId}/status")
    suspend fun gameStatus(@Path("gameId") gameId: Int): GameStatusDto

    // ---- Library ------------------------------------------------------------------------

    @GET("library")
    suspend fun getLibrary(@Query("status") status: String?): SavedGameListDto

    @PUT("library/{gameId}")
    suspend fun putLibraryGame(@Path("gameId") gameId: Int, @Body body: SavedGameRequest): SavedGameDto

    @DELETE("library/{gameId}")
    suspend fun deleteLibraryGame(@Path("gameId") gameId: Int)

    // ---- Favourites ---------------------------------------------------------------------

    @GET("favourites")
    suspend fun getFavourites(): SavedGameListDto

    @PUT("favourites/{gameId}")
    suspend fun putFavourite(@Path("gameId") gameId: Int, @Body body: SavedGameRequest): SavedGameDto

    @DELETE("favourites/{gameId}")
    suspend fun deleteFavourite(@Path("gameId") gameId: Int)

    // ---- Wishlist -----------------------------------------------------------------------

    @GET("wishlist")
    suspend fun getWishlist(): SavedGameListDto

    @PUT("wishlist/{gameId}")
    suspend fun putWishlistGame(@Path("gameId") gameId: Int, @Body body: SavedGameRequest): SavedGameDto

    @DELETE("wishlist/{gameId}")
    suspend fun deleteWishlistGame(@Path("gameId") gameId: Int)

    @POST("wishlist/{gameId}/move-to-library")
    suspend fun moveWishlistToLibrary(@Path("gameId") gameId: Int, @Body body: MoveToLibraryRequest): SavedGameDto

    // ---- Notifications ------------------------------------------------------------------

    @GET("notifications")
    suspend fun getNotifications(): NotificationListDto

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): UnreadCountDto

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): MessageResponse

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): MessageResponse

    @POST("me/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest): MessageResponse

    @HTTP(method = "DELETE", path = "me/fcm-token", hasBody = true)
    suspend fun unregisterFcmToken(@Body body: FcmTokenRequest?): MessageResponse
}
