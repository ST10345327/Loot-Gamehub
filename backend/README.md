# Loot - Gamehub API

Node.js + Express + MySQL REST API for the Loot - Gamehub Android app.

- **Accounts:** register / login with bcrypt-hashed passwords and JWT tokens.
- **Personal data:** library (with status), favourites, wishlist, recently viewed, user settings.
- **IGDB proxy:** the app never talks to IGDB directly. The Twitch client secret stays on this server.

## Run it locally

```bash
cd backend
npm install
cp .env.example .env        # then fill in JWT_SECRET, DB_* and IGDB_* values
npm run db:init             # creates the tables from sql/schema.sql
npm run dev                 # http://localhost:3000
```

Android emulator -> your PC: use `http://10.0.2.2:3000/` as the API base URL.
Physical phone: use your PC's LAN IP (`http://192.168.x.x:3000/`) and the same Wi-Fi network.

Getting IGDB credentials: create an application at https://dev.twitch.tv/console/apps
(needs a Twitch account with two-factor authentication), then copy the Client ID and generate a Client Secret.

## Tests

```bash
npm run test:unit             # deterministic tests; no database or external API calls
npm test                      # all tests; DB integration cases are skipped without TEST_DB_HOST
npm run test:integration      # API/MySQL tests only

# Full run, including MySQL API tests. This DROPS and recreates tables in gamehub_test.
TEST_DB_HOST=127.0.0.1 TEST_DB_PORT=3306 TEST_DB_USER=<test-user> \
TEST_DB_PASSWORD=<test-password> TEST_DB_NAME=gamehub_test \
TEST_DB_ALLOW_RESET=true npm test
```

The integration suite only runs when `TEST_DB_HOST` is set. To prevent accidental data loss,
it also requires `TEST_DB_NAME` to end in `_test` or `_tests` and the explicit
`TEST_DB_ALLOW_RESET=true` opt-in. If `DB_NAME` is set, the test database must differ from it.
Use a dedicated, empty database and a test-only account; never point these variables at
development or production data. The suite drops and recreates its tables before running.

PowerShell example:

```powershell
$env:TEST_DB_HOST = "127.0.0.1"
$env:TEST_DB_PORT = "3306"
$env:TEST_DB_USER = "gamehub_test_user"
$env:TEST_DB_PASSWORD = "<test-only-password>"
$env:TEST_DB_NAME = "gamehub_test"
$env:TEST_DB_ALLOW_RESET = "true"
npm test
```

The GitHub Actions backend job starts an ephemeral MySQL 8 service and runs the complete
suite against the dedicated `gamehub_test` database. The CI database credentials are
test-only and are not used by the application deployment.

## Hosting (needed for the demo video)

1. **Database:** create a MySQL database on a free host (check current free tiers: Aiven, TiDB Cloud, Railway...).
   Set `DB_SSL=true` if the host requires TLS, then run `npm run db:init` once against it.
2. **API:** deploy the `backend/` folder to a Node host (Render, Railway...).
   Build command `npm install`, start command `npm start`, and set the environment variables from `.env.example`.
3. Put the resulting `https://...` address into `ApiConfig.kt` in the Android app.

Free tiers may sleep when idle, so the first request can take up to a minute. The app allows for this.

## Endpoints

All routes except `/auth/*` and `/health` need `Authorization: Bearer <token>`.
Errors always look like `{ "error": { "code": "...", "message": "...", "details": { ... } } }`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | `{username, email, password}` -> `{token, user}` |
| POST | `/auth/login` | `{email, password}` -> `{token, user}` |
| POST | `/auth/forgot-password` | `{email}` -> generic message (no email service in the prototype) |
| POST | `/auth/change-password` | `{currentPassword, newPassword}` |
| GET | `/me` | current user and settings |
| PATCH | `/me/settings` | `{preferredLanguage, themePreference, notificationsEnabled, wishlistAlerts, releaseAlerts}` (all optional) |
| GET | `/me/games/:gameId/status` | `{libraryStatus, isFavourite, isInWishlist}` |
| GET | `/games/search` | `q, genre, platform, minRating, releasedAfter, releasedBefore, sort, limit, offset` |
| GET | `/games/discover/:category` | `trending`, `popular`, `upcoming`, `top-rated` |
| GET | `/games/random` | `genre, platform, minRating` |
| GET | `/games/:id` | full game details |
| GET / PUT / DELETE | `/library`, `/library/:gameId` | `PUT` body needs `status` (`want_to_play`, `playing`, `completed`) plus the game snapshot |
| GET / PUT / DELETE | `/favourites`, `/favourites/:gameId` | snapshot body |
| GET / PUT / DELETE | `/wishlist`, `/wishlist/:gameId` | snapshot body |
| POST | `/wishlist/:gameId/move-to-library` | `{status?}` moves the game in one transaction |
| GET / POST / DELETE | `/recent`, `/recent/:gameId` | last 20 viewed games |
| GET | `/health` | database connectivity check |

**Game snapshot** (body of `PUT`/`POST`): `{ name, coverUrl, rating, releaseYear, genre, platforms }`.
Lists store it so they can be shown without one IGDB request per game.

## Security notes

- Passwords are hashed with bcrypt (`bcryptjs`); the hash is never returned by any endpoint.
- Login answers identically for "unknown email" and "wrong password", with a constant-time-style dummy compare.
- All SQL uses bound parameters. Table and column names are built only from constants in our own code.
- Auth endpoints are rate limited; request bodies are limited to 50 KB; `helmet` sets safe headers.
- Secrets live only in environment variables (`.env` is git-ignored).