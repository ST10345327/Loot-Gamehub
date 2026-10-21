package com.gamehub.app.ui.settings

import com.gamehub.app.data.model.AppLanguage
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.testing.FakeSettingsRepository
import com.gamehub.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    private fun createViewModel() = SettingsViewModel(repository)

    @Test
    fun setThemeMode_updatesState() = runTest {
        val viewModel = createViewModel()

        viewModel.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun setLanguage_updatesState() = runTest {
        val viewModel = createViewModel()

        viewModel.setLanguage(AppLanguage.SETSWANA)

        assertEquals(AppLanguage.SETSWANA, viewModel.uiState.value.language)
    }

    @Test
    fun appLanguage_fromTag_returnsExpectedLanguage() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SETSWANA, AppLanguage.fromTag("tn"))
        assertEquals(AppLanguage.ISIZULU, AppLanguage.fromTag("zu"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("invalid"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
    }

    @Test
    fun appLanguage_propertiesAreConfigured() {
        assertEquals("en", AppLanguage.ENGLISH.tag)
        assertEquals("English", AppLanguage.ENGLISH.nativeName)
        assertEquals("tn", AppLanguage.SETSWANA.tag)
        assertEquals("Setswana", AppLanguage.SETSWANA.nativeName)
        assertEquals("zu", AppLanguage.ISIZULU.tag)
        assertEquals("isiZulu", AppLanguage.ISIZULU.nativeName)
    }
}
