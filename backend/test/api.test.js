'use strict';

/**
 * End-to-end API tests against a REAL MySQL/MariaDB database (IGDB is replaced by a fake).
 * They are skipped unless a test database is configured, e.g.:
 *
 * Configure TEST_DB_* to a dedicated database whose name ends in "_test", and set
 * TEST_DB_ALLOW_RESET=true to confirm that this suite may drop and recreate its tables.
 *
 * WARNING: the tables in that database are dropped and recreated on every run. Never point
 * these variables at a development or production database.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

const { loadConfig } = require('../src/config');
const { createPool } = require('../src/db');
const { createApp } = require('../src/app');

const skip = process.env.TEST_DB_HOST ? false : 'Set TEST_DB_HOST (and friends) to run API tests.';

const fakeGame = { id: 119133, name: 'Elden Ring', coverUrl: null, rating: 9.5, releaseYear: 2022, releaseDate: '2022-02-25', genres: ['RPG'], platforms: ['PC'] };
const fakeGameService = {
  search: async (params) => ({ items: [{ ...fakeGame, name: `Result for ${params.q}` }], hasMore: false }),
  details: async (id) => ({ ...fakeGame, id, description: 'x', developers: [], publishers: [], screenshots: [], similarGames: [] }),
  discover: async () => ({ items: [fakeGame], hasMore: false }),
  random: async () => fakeGame,
};

const snapshot = (name, extra = {}) => ({ name, coverUrl: 'https://images.igdb.com/a.jpg', rating: 8.1, releaseYear: 2020, genre: 'RPG', platforms: 'PC, PS5', ...extra });

let server;
let baseUrl;
let pool;

async function api(method, route, { token, body, raw } = {}) {
  const response = await fetch(baseUrl + route, {
    method,
    headers: { ...(body !== undefined || raw ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: raw ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}

async function register(username, email = `${username}@example.com`, password = 'passw0rd1') {
  const res = await api('POST', '/auth/register', { body: { username, email, password } });
  assert.equal(res.status, 201, JSON.stringify(res.body));
  return res.body;
}

test.before(async () => {
  if (skip) return;
  const required = ['TEST_DB_USER', 'TEST_DB_NAME'];
  const missing = required.filter((name) => !process.env[name]);
  if (missing.length > 0) {
    throw new Error(`Database integration tests need ${missing.join(' and ')} when TEST_DB_HOST is set.`);
  }
  if (!/_tests?$/i.test(process.env.TEST_DB_NAME)) {
    throw new Error('Refusing destructive test setup: TEST_DB_NAME must end in "_test" or "_tests".');
  }
  if (process.env.TEST_DB_ALLOW_RESET !== 'true') {
    throw new Error('Refusing destructive test setup: set TEST_DB_ALLOW_RESET=true for a dedicated test database.');
  }
  if (process.env.DB_NAME && process.env.DB_NAME === process.env.TEST_DB_NAME) {
    throw new Error('Refusing destructive test setup: TEST_DB_NAME must differ from DB_NAME.');
  }

  const dbConfig = {
    host: process.env.TEST_DB_HOST,
    port: Number(process.env.TEST_DB_PORT) || 3306,
    user: process.env.TEST_DB_USER,
    password: process.env.TEST_DB_PASSWORD ?? '',
    database: process.env.TEST_DB_NAME,
  };

  // Fresh schema for every run.
  const admin = await mysql.createConnection({ ...dbConfig, multipleStatements: true });
  await admin.query('SET FOREIGN_KEY_CHECKS = 0; DROP TABLE IF EXISTS recently_viewed, wishlist, favourites, library, users; SET FOREIGN_KEY_CHECKS = 1;');
  await admin.query(fs.readFileSync(path.join(__dirname, '..', 'sql', 'schema.sql'), 'utf8'));
  await admin.end();

  const config = loadConfig({
    JWT_SECRET: 'test-secret-that-is-long-enough',
    BCRYPT_ROUNDS: '4', // fast hashing for tests only
    DB_HOST: dbConfig.host, DB_PORT: String(dbConfig.port), DB_USER: dbConfig.user, DB_PASSWORD: dbConfig.password, DB_NAME: dbConfig.database,
  });
  pool = createPool(config.db);
  const app = createApp({ config, pool, gameService: fakeGameService, rateLimits: { authMax: 1000, globalMax: 10000 } });
  await new Promise((resolve) => { server = app.listen(0, '127.0.0.1', resolve); });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  if (skip) return;
  await new Promise((resolve) => server.close(resolve));
  await pool.end();
});

test('register stores a bcrypt hash and never returns it', { skip }, async () => {
  const { token, user } = await register('alice_1');
  assert.ok(token);
  assert.equal(user.email, 'alice_1@example.com');
  assert.equal(user.passwordHash, undefined);
  assert.equal(user.preferredLanguage, 'en');

  const [rows] = await pool.query('SELECT password_hash FROM users WHERE user_id = ?', [user.id]);
  assert.match(rows[0].password_hash, /^\$2[aby]\$/);
  assert.notEqual(rows[0].password_hash, 'passw0rd1');
});

test('register rejects duplicates and invalid input', { skip }, async () => {
  await register('bob_1');
  const sameEmail = await api('POST', '/auth/register', { body: { username: 'bob_other', email: 'bob_1@example.com', password: 'passw0rd1' } });
  assert.equal(sameEmail.status, 409);
  assert.equal(sameEmail.body.error.code, 'EMAIL_TAKEN');

  const sameName = await api('POST', '/auth/register', { body: { username: 'bob_1', email: 'other@example.com', password: 'passw0rd1' } });
  assert.equal(sameName.body.error.code, 'USERNAME_TAKEN');

  const invalid = await api('POST', '/auth/register', { body: { username: 'x', email: 'nope', password: 'short' } });
  assert.equal(invalid.status, 400);
  assert.deepEqual(Object.keys(invalid.body.error.details).sort(), ['email', 'password', 'username']);

  const malformed = await api('POST', '/auth/register', { raw: '{not json' });
  assert.equal(malformed.status, 400);
  assert.equal(malformed.body.error.code, 'VALIDATION_ERROR');
});

test('login succeeds with the right password and fails identically otherwise', { skip }, async () => {
  await register('carol_1');
  const ok = await api('POST', '/auth/login', { body: { email: 'CAROL_1@example.com', password: 'passw0rd1' } });
  assert.equal(ok.status, 200);
  assert.ok(ok.body.token);

  const wrong = await api('POST', '/auth/login', { body: { email: 'carol_1@example.com', password: 'wrongpass1' } });
  const unknown = await api('POST', '/auth/login', { body: { email: 'nobody@example.com', password: 'wrongpass1' } });
  assert.equal(wrong.status, 401);
  assert.equal(wrong.body.error.code, 'INVALID_CREDENTIALS');
  assert.deepEqual(unknown.body.error, wrong.body.error);
});

test('protected routes need a valid token', { skip }, async () => {
  const none = await api('GET', '/me');
  assert.equal(none.status, 401);
  assert.equal(none.body.error.code, 'UNAUTHORIZED');
  const forged = await api('GET', '/library', { token: 'not.a.token' });
  assert.equal(forged.status, 401);
});

test('settings can be read and updated, invalid values are rejected', { skip }, async () => {
  const { token } = await register('dave_1');
  const before = await api('GET', '/me', { token });
  assert.equal(before.body.user.themePreference, 'system');

  const updated = await api('PATCH', '/me/settings', { token, body: { preferredLanguage: 'zu', themePreference: 'dark', wishlistAlerts: false } });
  assert.equal(updated.status, 200);
  assert.equal(updated.body.user.preferredLanguage, 'zu');
  assert.equal(updated.body.user.themePreference, 'dark');
  assert.equal(updated.body.user.wishlistAlerts, false);
  assert.equal(updated.body.user.releaseAlerts, true);

  assert.equal((await api('PATCH', '/me/settings', { token, body: { preferredLanguage: 'fr' } })).status, 400);
  assert.equal((await api('PATCH', '/me/settings', { token, body: {} })).status, 400);
});

test('change password checks the current password', { skip }, async () => {
  const { token } = await register('erin_1');
  const wrong = await api('POST', '/auth/change-password', { token, body: { currentPassword: 'nope12345', newPassword: 'newpassw0rd' } });
  assert.equal(wrong.body.error.code, 'WRONG_PASSWORD');

  const weak = await api('POST', '/auth/change-password', { token, body: { currentPassword: 'passw0rd1', newPassword: 'nodigits' } });
  assert.equal(weak.status, 400);

  const ok = await api('POST', '/auth/change-password', { token, body: { currentPassword: 'passw0rd1', newPassword: 'newpassw0rd' } });
  assert.equal(ok.status, 200);
  assert.equal((await api('POST', '/auth/login', { body: { email: 'erin_1@example.com', password: 'newpassw0rd' } })).status, 200);
  assert.equal((await api('POST', '/auth/login', { body: { email: 'erin_1@example.com', password: 'passw0rd1' } })).status, 401);
});

test('library: add, change status (no duplicate), filter, status lookup, delete', { skip }, async () => {
  const { token } = await register('frank_1');
  const put = await api('PUT', '/library/100', { token, body: snapshot('Game A', { status: 'want_to_play' }) });
  assert.equal(put.status, 200);
  assert.equal(put.body.status, 'want_to_play');
  assert.equal(put.body.rating, 8.1); // DECIMAL comes back as a number

  await api('PUT', '/library/101', { token, body: snapshot('Game B', { status: 'completed' }) });
  await api('PUT', '/library/100', { token, body: snapshot('Game A', { status: 'playing' }) });

  const all = await api('GET', '/library', { token });
  assert.equal(all.body.items.length, 2, 'upsert must not create a duplicate row');
  const playing = await api('GET', '/library?status=playing', { token });
  assert.deepEqual(playing.body.items.map((g) => g.gameId), [100]);
  assert.equal((await api('GET', '/library?status=bogus', { token })).status, 400);

  const status = await api('GET', '/me/games/100/status', { token });
  assert.deepEqual(status.body, { libraryStatus: 'playing', isFavourite: false, isInWishlist: false });

  assert.equal((await api('DELETE', '/library/100', { token })).status, 204);
  assert.equal((await api('DELETE', '/library/100', { token })).status, 204, 'delete is idempotent');
  assert.equal((await api('GET', '/library', { token })).body.items.length, 1);
  assert.equal((await api('PUT', '/library/100', { token, body: snapshot('No status') })).status, 400);
});

test('favourites and wishlist keep their own rows', { skip }, async () => {
  const { token } = await register('gina_1');
  await api('PUT', '/favourites/7', { token, body: snapshot('Fav') });
  await api('PUT', '/wishlist/8', { token, body: snapshot('Wish') });

  assert.deepEqual((await api('GET', '/favourites', { token })).body.items.map((g) => g.gameId), [7]);
  assert.deepEqual((await api('GET', '/wishlist', { token })).body.items.map((g) => g.gameId), [8]);
  const status = await api('GET', '/me/games/8/status', { token });
  assert.equal(status.body.isInWishlist, true);
  assert.equal(status.body.isFavourite, false);
});

test('move-to-library is atomic and returns 404 when the game is not wishlisted', { skip }, async () => {
  const { token } = await register('hank_1');
  await api('PUT', '/wishlist/50', { token, body: snapshot('Moving') });

  const moved = await api('POST', '/wishlist/50/move-to-library', { token, body: { status: 'playing' } });
  assert.equal(moved.status, 200);
  assert.equal(moved.body.status, 'playing');
  assert.equal((await api('GET', '/wishlist', { token })).body.items.length, 0);
  assert.equal((await api('GET', '/library', { token })).body.items[0].name, 'Moving');

  const missing = await api('POST', '/wishlist/999/move-to-library', { token });
  assert.equal(missing.status, 404);
});

test('users cannot see each other\'s data', { skip }, async () => {
  const a = await register('ivy_1');
  const b = await register('jack_1');
  await api('PUT', '/library/1', { token: a.token, body: snapshot('Private', { status: 'playing' }) });
  assert.equal((await api('GET', '/library', { token: b.token })).body.items.length, 0);
  assert.equal((await api('GET', '/me/games/1/status', { token: b.token })).body.libraryStatus, null);
});

test('recently viewed keeps the newest first, de-duplicates and can be cleared', { skip }, async () => {
  const { token } = await register('kate_1');
  await api('POST', '/recent/1', { token, body: snapshot('One') });
  await api('POST', '/recent/2', { token, body: snapshot('Two') });
  await api('POST', '/recent/1', { token, body: snapshot('One') }); // viewed again -> moves to top
  const list = await api('GET', '/recent', { token });
  assert.deepEqual(list.body.items.map((g) => g.gameId), [1, 2]);

  assert.equal((await api('DELETE', '/recent', { token })).status, 204);
  assert.equal((await api('GET', '/recent', { token })).body.items.length, 0);
});

test('recently viewed is capped at 20 entries', { skip }, async () => {
  const { token, user } = await register('liam_1');
  for (let id = 1; id <= 23; id += 1) {
    await api('POST', `/recent/${id}`, { token, body: snapshot(`G${id}`) });
  }
  const [rows] = await pool.query('SELECT COUNT(*) AS n FROM recently_viewed WHERE user_id = ?', [user.id]);
  assert.equal(rows[0].n, 20);
});

test('game endpoints validate input and delegate to the service', { skip }, async () => {
  const { token } = await register('mia_1');
  const search = await api('GET', '/games/search?q=zelda&genre=rpg', { token });
  assert.equal(search.body.items[0].name, 'Result for zelda');
  assert.equal((await api('GET', '/games/search?genre=nonsense', { token })).status, 400);
  assert.equal((await api('GET', '/games/discover/nope', { token })).status, 400);
  assert.equal((await api('GET', '/games/discover/trending', { token })).status, 200);
  assert.equal((await api('GET', '/games/random', { token })).body.name, 'Elden Ring');
  assert.equal((await api('GET', '/games/42', { token })).body.id, 42);
  assert.equal((await api('GET', '/games/abc', { token })).status, 400);
  assert.equal((await api('GET', '/games/search')).status, 401);
});

test('health check and unknown routes', { skip }, async () => {
  assert.equal((await api('GET', '/health')).body.status, 'ok');
  const missing = await api('GET', '/nope');
  assert.equal(missing.status, 404);
  assert.equal(missing.body.error.code, 'NOT_FOUND');
});