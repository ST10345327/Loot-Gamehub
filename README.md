# Loot-Gamehub

![Android CI](https://github.com/ST10345327/Loot-Gamehub/actions/workflows/build.yml/badge.svg)

A game discovery and personal library app for Android, built with Kotlin and Jetpack Compose.
Users can search games (IGDB), save them to a personal library, wishlist and favourites,
compare titles and get a random recommendation with **Surprise Me**.

**Module:** OPSC6311 - Part 2: App Prototype Development

## Team
Student names & Numbers: 

ST10345327, Olebogeng Phawe 

ST10449154, Mbuso Sbusiso Dube 

ST10437200, Nkosikhona Dlamini 



## Demo video

https://www.youtube.com/watch?v=2OWWyBeUR_A

## Purpose of the app

The primary reason behind the existence of GameHub lies in addressing a certain challenge in the gaming industry: the increasing difficulty of discovering, researching and managing the video games that come out today in a more and more crowded environment.

Given the number of games that appear each year on multiple platforms (PC, PlayStation, Xbox, Nintendo, mobile), it's easy for gamers to become overwhelmed with all the possibilities of playing different games. GameHub was created to organize this chaos through one platform where users can discover new games, research them, bookmark them and track their progress in gaming.

The Purpose of GameHub
1. Game Discovery Gateway
GameHub acts as a bridge to the world of video games. Using IGDB API (Internet Game Database), GameHub gives its users access to an extensive database about all kinds of games names, dates of release, genres, platforms, ratings, descriptions, images, developers and publishers in one easy-to-use mobile interface.

2. Game Library Management
Apart from being a platform for discovery, GameHub also lets its users create and manage their personal libraries of games. The games are organized in various statuses like Want to Play, Playing and Completed.

## Design considerations

The app uses the design approach which prioritizes clearness and accessibility.

Visual Identity & UI
Visual Style: The design of the UI uses the elements from Material Design. It includes dark visual theme by default (as seen in mockups) with bright accent color for interactive elements.

Iconography: The icon of the app (Loot Game Center) has dark background with four colored icons (Square, Triangle, Cross, Circle), corresponding to controller buttons.

Typography: Clean sans-serif font. Big bold typography used for names of games, secondary typography used for additional information (game rating, release year, genre).

Screen Structure
The app has linear screen structure:
Splash Screen → Onboarding → Login/Registration → Home

Home/Discover: Screen includes personalized greeting, search bar, and categories for Trending Now, Popular Games and Upcoming Releases.

Game Details: High quality images for covers, game descriptions, rating and buttons to Add to Library, Wishlist, Favourites and Compare games.

Comparison: A separate screen, where a user can select two games and compare their statistics.

Architecture & Technology Stack
Language: Kotlin

Architecture: MVVM (Model-View-ViewModel)

Networking: Retrofit / OkHttp

Local Storage: Room Database

Backend: Custom REST API and MySQL Database

External API: IGDB (Internet Game Database)



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

<img width="1135" height="623" alt="Screenshot_22-9-2026_23546_github com" src="https://github.com/user-attachments/assets/b0a57a1f-867e-4c73-ad29-c7fa14de6ca6" />


## How to run

1. Clone the repository and open it in Android Studio.
2. Let Gradle sync, then run the `app` configuration on a device or emulator.

## AI use statement

DECLARATION OF ARTIFICIAL INTELLIGENCE USAGE
GameHub Android Application Project
Programme: Diploma in Information Technology – Software Development
Institution: Rosebank College / The IIE
Date: 22 September 2026

1. Declaration

We declare that Artificial Intelligence (AI) tools were used to support the planning, development, debugging, documentation and testing of the GameHub Android application. AI was used as a productivity aid, not as a substitute for our own knowledge, judgement or responsibility. The project team remains fully accountable for the design, implementation, testing and final submission of GameHub. This disclosure follows South African higher-education guidance recommending transparent acknowledgement of AI use (CPUT, 2026; University of Johannesburg, 2023).

2. AI Tools Used

ChatGPT (OpenAI): planning, technical explanations, troubleshooting and documentation support
GitHub Copilot / AI coding assistant: code suggestions, completion and debugging
IDE AI Agent: analysing project files, identifying errors, suggesting fixes
Other AI tools: brainstorming, wording and general development assistance

3. Areas of Use

Planning: AI helped brainstorm features and technical approaches, including game discovery, IGDB integration, authentication, libraries/wishlists, comparison, Surprise Me, settings, multilingual support (English, Setswana, isiZulu), and theming. Final decisions on scope and direction were made by the team based on assignment requirements and feasibility.

Development: AI assisted with Kotlin, Jetpack Compose, MVVM architecture, navigation, ViewModels/repositories, API integration, authentication, database work, Gradle configuration and error resolution. All suggestions were reviewed, tested and adapted by the team before inclusion.

Backend: Support was used for Node.js/Express setup, database configuration, environment variables, API endpoints and debugging server errors. Fixes were verified through direct testing rather than accepted automatically.

Debugging: AI helped interpret compiler, Gradle, Android Studio, navigation, ViewModel, backend and database errors. Every suggested fix was tested to confirm it actually resolved the issue.

Documentation: AI assisted with structuring sections, improving grammar and readability, and explaining technical concepts. All content was reviewed and edited by the team to accurately reflect the actual implementation.

Testing: AI suggested possible test cases (registration, login, search, favourites, wishlist, comparison, theming, language switching, navigation, API responses). Actual testing and verification were performed by the team on the working application.

4. Verification

AI output was never assumed accurate. Information was cross-checked against official Android, Kotlin, JetBrains and API documentation, plus our own compiler/runtime testing, in line with academic guidance on verifying AI-generated content (Stellenbosch University, 2026).

5. Academic Integrity

All key decisions architecture, features, UI, database structure, API design, testing approach and final implementation were made by the project team. AI tools are not authors of this project, as they cannot take responsibility for its academic or technical integrity (University of Johannesburg, 2023).

6. Responsibility Statement

We confirm that AI was used solely as a supporting tool, that all AI-generated suggestions were reviewed and tested, and that we remain fully responsible for the accuracy, functionality and integrity of the submitted GameHub project.

Student names & Numbers: 

ST10345327, Olebogeng Phawe 

ST10449154, Mbuso Sbusiso Dube 

ST10437200, Nkosikhona Dlamini 

Date: 22 Sep 2026 
## References

- Android Developers (2026) *Navigation with Compose*. Available at: https://developer.android.com/develop/ui/compose/navigation
- Android Developers (2026) *Material Design 3 in Compose*. Available at: https://developer.android.com/develop/ui/compose/designsystems/material3
- IGDB (2026) *IGDB API Documentation*. Available at: https://api-docs.igdb.com/
