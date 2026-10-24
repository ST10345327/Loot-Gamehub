package com.gamehub.app

import android.app.Application
import com.gamehub.app.di.AppContainer
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs once when the app process starts. Its only job is to create the [AppContainer] that
 * holds the app's shared services. It must be named in AndroidManifest.xml (android:name).
 */
class LootApplication : Application() {

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            AppLogger.error("Could not migrate the saved session token", error)
        }
    )

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch { container.migrateLegacySessionToken() }
        AppLogger.debug("Loot - Gamehub application started")
    }
}
