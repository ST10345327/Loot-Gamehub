# Loot-Gamehub

![Android CI](https://github.com/ST10345327/Loot-Gamehub/actions/workflows/build.yml/badge.svg)

A game discovery and personal library app for Android, built with Kotlin and Jetpack Compose.
Users can search games (IGDB), save them to a personal library, wishlist and favourites,
compare titles and get a random recommendation with **Surprise Me**.

**Module:** OPSC6311 - Part 2: App Prototype Development

## Team

| Student number | Name |
| --- | --- |
| ST10345327 | Olebogeng Phawe |
| ST10449154 | Mbuso Sbusiso Dube |
| ST10437200 | Nkosikhona Dlamini |

## Demo video

_Link to be added (YouTube, unlisted)._

## Purpose of the app

_Short description of the problem Loot - Gamehub solves - adapt from the Part 1 Planning & Design document._

## Design considerations

_Colour palette (near-black + red), typography, Light/Dark mode, multilingual UI (English, Setswana, isiZulu), navigation. Add mockup screenshots._

## Architecture

MVVM with a repository layer:

```
com.gamehub.app
├── data
│   ├── remote      # Retrofit services (own REST API)
│   ├── local       # Room (offline cache) + DataStore (settings)
│   ├── repository  # single source of truth for the ViewModels
│   └── model       # data classes
├── ui
│   ├── theme       # colours, typography, GameHubTheme
│   ├── navigation  # routes, NavHost, bottom bar
│   ├── components  # reusable composables
│   └── <feature>   # screen + ViewModel per feature
└── util
```

## REST API and database

_Endpoint table, hosting provider, database screenshots. To be completed in Phase 2._

## Features

| Feature | Status |
| --- | --- |
| Project setup, navigation, theme, 3 languages (strings) | In progress |
| Register / login (hashed passwords, token) | Planned |
| IGDB search and game details | Planned |
| Library, wishlist, favourites | Planned |
| Settings (theme, language, sign out) | Planned |
| Game comparison, Surprise Me | Planned |
| Deferred to PoE: Google Sign-In, push notifications, full offline sync | Deferred |

## GitHub and GitHub Actions

Every push and pull request runs `.github/workflows/build.yml`, which runs the unit tests
(`./gradlew testDebugUnitTest`) and builds the debug APK (`./gradlew assembleDebug`).

_Add a screenshot of a passing run._

## How to run

1. Clone the repository and open it in Android Studio.
2. Set the development API base URL to the backend reachable from your device or emulator. The default is `http://10.0.2.2:3000/`, which reaches a backend running on the host from the standard Android emulator. Override it with either a Gradle property (`DEV_API_BASE_URL`) or an environment variable of the same name. The URL must end in `/`. Debug builds permit HTTP for local development.
3. Let Gradle sync, then run the `app` configuration on a device or emulator.

Release builds require `RELEASE_API_BASE_URL` to be configured as a hosted HTTPS URL with a trailing `/`. Set it as a local Gradle property or environment variable; do not commit production endpoints containing private infrastructure details or credentials. The release build rejects HTTP, local-only hosts, invalid ports, credentials, query strings and URL fragments. A real hosted HTTPS endpoint has not been configured in this repository. CI uses `https://api.example.test/` only as a validation/build fixture; it is not a deployed API endpoint.

The session token is encrypted using an Android Keystore AES-GCM key. Existing plaintext tokens are migrated at app startup; if the token cannot be decrypted, the saved session is cleared. The DataStore file is excluded from cloud backup and device transfer because its encryption key is device-bound. This also means saved theme, language and onboarding preferences in that file will not transfer to a new device.

Run `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew assembleDebug`, and `./gradlew assembleDebugAndroidTest` to run local checks. Assembling the Android-test APK compiles the instrumentation tests but does not execute them. GitHub Actions does not currently provision an emulator, so instrumentation tests are not executed in CI; execution requires a connected device or emulator and `./gradlew connectedDebugAndroidTest`.

## AI use statement

_Maximum 500 words - keep a running log as you work._

## References

- Android Developers (2026) *Navigation with Compose*. Available at: https://developer.android.com/develop/ui/compose/navigation
- Android Developers (2026) *Material Design 3 in Compose*. Available at: https://developer.android.com/develop/ui/compose/designsystems/material3
- IGDB (2026) *IGDB API Documentation*. Available at: https://api-docs.igdb.com/
