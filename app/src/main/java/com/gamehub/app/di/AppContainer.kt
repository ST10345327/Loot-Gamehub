package com.gamehub.app.di

import com.gamehub.app.data.local.SettingsStore
import com.gamehub.app.data.repository.SettingsRepository
import com.gamehub.app.data.repository.SettingsRepositoryImpl
import android.content.Context
import android.content.pm.ApplicationInfo
import com.gamehub.app.data.local.SessionStore
import com.gamehub.app.data.local.gameHubDataStore
import com.gamehub.app.data.remote.ApiCaller
import com.gamehub.app.data.remote.ApiConfig
import com.gamehub.app.data.remote.AuthInterceptor
import com.gamehub.app.data.remote.LootApi
import com.gamehub.app.data.repository.AuthRepository
import com.gamehub.app.data.repository.AuthRepositoryImpl
import com.gamehub.app.data.repository.GameRepository
import com.gamehub.app.data.repository.GameRepositoryImpl
import com.gamehub.app.data.repository.LibraryRepository
import com.gamehub.app.data.repository.LibraryRepositoryImpl
import java.util.Calendar
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Creates the app's shared objects once ("manual dependency injection"). Lives as long as the app process. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val sessionStore = SessionStore(appContext.gameHubDataStore)

    private val settingsStore = SettingsStore(appContext.gameHubDataStore)
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(settingsStore)
    private val apiCaller = ApiCaller(onSessionExpired = { token ->
        if (token != null) sessionStore.clearIfTokenMatches(token)
    })
    private val okHttpClient: OkHttpClient = buildOkHttpClient()

    private val api: LootApi = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LootApi::class.java)

    val authRepository: AuthRepository = AuthRepositoryImpl(api, apiCaller, sessionStore)

    val gameRepository: GameRepository = GameRepositoryImpl(
        api = api,
        apiCaller = apiCaller,
        currentYear = Calendar.getInstance().get(Calendar.YEAR)
    )

    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(api, apiCaller)

    suspend fun migrateLegacySessionToken() = sessionStore.migrateLegacyToken()

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Free hosting tiers "sleep" when idle; waking up can take close to a minute.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor { sessionStore.token() })

        val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                }
            )
        }
        return builder.build()
    }
}
