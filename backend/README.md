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

## Google Sign-In

Enable the Google provider under **Firebase Console > Authentication > Sign-in method** and
configure the Android app's package name and signing fingerprints in Firebase. Download the
updated `google-services.json` for the Android app.

On the API host, set `FIREBASE_SERVICE_ACCOUNT_JSON` to the Firebase Admin service-account JSON
contents (or configure `FIREBASE_CREDENTIALS_PATH` / `FIREBASE_SERVICE_ACCOUNT_BASE64`). Apply
`sql/migrations/001_add_google_sso.sql` once to an existing MySQL database before deploying;
new databases created from `sql/schema.sql` already include the Google SSO columns.
The app exchanges its Firebase ID token at `POST /auth/google`.
The backend requires Node.js 22 or newer.

Never commit service-account credentials or `google-services.json` to version control.

## Tests

```bash
npm test                     # unit tests only (API tests are skipped without a database)

# full run, including end-to-end API tests. WARNING: drops and recreates the tables in that database.
TEST_DB_HOST=127.0.0.1 TEST_DB_USER=<user> TEST_DB_PASSWORD=<pw> TEST_DB_NAME=<empty test db> npm test
```

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
| POST | `/auth/google` | `{idToken}` Firebase ID token -> `{token, user}` |
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