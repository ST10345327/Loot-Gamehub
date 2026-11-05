# GameHub - Final Release Notes

**Project:** GameHub  
**Release:** Final Version  
**Platform:** Android  
**Technologies:** Kotlin, XML, Node.js, Express, MySQL, Firebase Authentication, IGDB API

## 1. Release Overview

GameHub has grown from a prototype with basic game search and details into a game discovery and management application. This release adds authentication, personal game collections, game comparison, random discovery, customizable settings, notifications, and limited offline access.

## 2. Features

### 2.1 Authentication and Account Management
- User registration and login with email and password.
- Input validation and error handling.
- Password hashing on the backend.
- Session management to keep users signed in.
- Google Sign-In using Firebase Authentication and Android Credential Manager.
- Backend verification of Firebase ID tokens.
- Google account identification using unique Google UIDs.
- Protection against automatically linking Google sign-in to an existing password-protected account.
- Google Sign-In tested successfully.

### 2.2 Game Search and Discovery
- IGDB API integration for game information.
- Game search and discovery.
- Game details including titles, descriptions, release dates, genres, platforms, ratings, and artwork where available.

### 2.3 Personal Game Management
- Personal game library, favourites, and wishlist.
- Ability to move games from the wishlist into the library.

### 2.4 Game Comparison and Random Discovery
- Game comparison for selected titles.
- **Surprise Me** feature for random game discovery.

### 2.5 Settings and Personalisation
- Light, Dark, and System theme options.
- English, Setswana, and isiZulu language preferences.
- Preferences are saved and retained between app sessions.

### 2.6 Notifications
- Wishlist-related and game-release notifications.
- Backend notification-generation functionality.
- Protected backend endpoints for scheduled notification generation.
- Notification delivery tested successfully.

### 2.7 Offline Functionality
- The Settings section can be accessed and used offline.
- Offline access is limited to Settings. Other features—including authentication, game discovery, library and wishlist data, and notifications—require an internet connection.

### 2.8 Backend and Security
- Node.js and Express backend with MySQL database integration and migration support.
- Authentication and protected API endpoints.
- Firebase Admin SDK verification for Google Sign-In.
- Backend validation and error handling.
- Automated tests for authentication and other backend functionality.

## 3. Testing and Quality Assurance

The final development report recorded:

- Backend: 38 tests passed.
- Android: 86 unit tests passed.
- Android debug build completed successfully.
- Installed and launched on a physical Android phone.
- Google Sign-In tested successfully.
- Notifications tested successfully.
- Settings access tested and confirmed to work offline.

## 4. Final Summary

The final version of GameHub expands the original game-search prototype with email and Google authentication, game discovery, a personal library, favourites, a wishlist, game comparison, random discovery, customizable settings, notifications, and limited offline access.

Offline functionality is specifically limited to Settings; the rest of the app depends on online services. The Android app and backend work together to provide authentication, game data, user collections, and notifications.
