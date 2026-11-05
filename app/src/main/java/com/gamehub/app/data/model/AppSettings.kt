package com.gamehub.app.data.model

/** Appearance choices (Part 1, section 8.2). */
enum class ThemeMode(val apiValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromApi(value: String?): ThemeMode = entries.firstOrNull { it.apiValue == value } ?: SYSTEM
    }
}

/** Interface languages (Part 1, section 8.4). `tag` is the BCP-47 language code used by AppCompat. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    SETSWANA("tn", "Setswana"),
    ISIZULU("zu", "isiZulu");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val notificationsEnabled: Boolean = true,
    val wishlistAlerts: Boolean = true,
    val releaseAlerts: Boolean = true
)
