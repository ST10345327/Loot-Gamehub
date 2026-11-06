'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadConfig } = require('../src/config');
const { createPool } = require('../src/db');
const { createApp } = require('../src/app');
const { getAuth } = require('firebase-admin/auth');
const { initFirebase } = require('../src/services/firebase');
const { resetTestDatabase } = require('../test-support/resetTestDatabase');

const hasTestDatabase = Boolean(process.env.TEST_DB_HOST);
const skip = hasTestDatabase ? false : 'Set TEST_DB_HOST and test database variables to run Google Auth API tests.';

const fakeGameService = {
  search: async () => ({ items: [], hasMore: false }),
  details: async (id) => ({ id, name: 'Game' }),
  discover: async () => ({ items: [], hasMore: false }),
  random: async () => ({ id: 1, name: 'Game' }),
};

let server;
let baseUrl;
let pool;

async function api(method, route, body) {
  const response = await fetch(baseUrl + route, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}

test.before(async () => {
  if (skip) return;

  const required = ['TEST_DB_USER', 'TEST_DB_NAME', 'TEST_DB_ALLOW_RESET'];
  const missing = required.filter((name) => !process.env[name]);
  if (missing.length > 0) {
    throw new Error(`Google Auth tests require ${missing.join(', ')}.`);
  }
  if (!/_tests?$/i.test(process.env.TEST_DB_NAME) || process.env.TEST_DB_ALLOW_RESET !== 'true') {
    throw new Error('Refusing destructive Google Auth test setup without a dedicated *_test database and TEST_DB_ALLOW_RESET=true.');
  }
  if (process.env.DB_NAME && process.env.DB_NAME === process.env.TEST_DB_NAME) {
    throw new Error('Refusing to reset the configured application database.');
  }

  const dbConfig = {
    host: process.env.TEST_DB_HOST,
    port: Number(process.env.TEST_DB_PORT) || 3306,
    user: process.env.TEST_DB_USER,
    password: process.env.TEST_DB_PASSWORD ?? '',
    database: process.env.TEST_DB_NAME,
  };
  await resetTestDatabase(dbConfig);

  const config = loadConfig({
    JWT_SECRET: 'test-secret-that-is-long-enough',
    BCRYPT_ROUNDS: '4',
    DB_HOST: dbConfig.host,
    DB_PORT: String(dbConfig.port),
    DB_USER: dbConfig.user,
    DB_PASSWORD: dbConfig.password,
    DB_NAME: dbConfig.database,
  });
  pool = createPool(config.db);

  const firebaseApp = initFirebase({ NODE_ENV: 'test', FIREBASE_MOCK: 'true' });
  getAuth(firebaseApp).verifyIdToken = async (idToken) => {
    if (idToken === 'valid-new-user') {
      return { uid: 'google_uid_new', email: 'newgoogle@example.com', email_verified: true, name: 'New Google User' };
    }
    if (idToken === 'unverified-email') {
      return { uid: 'google_uid_unverified', email: 'unverified@example.com', email_verified: false };
    }
    if (idToken === 'expired-token') {
      const error = new Error('Token expired');
      error.code = 'auth/id-token-expired';
      throw error;
    }
    const error = new Error('Invalid token');
    error.code = 'auth/argument-error';
    throw error;
  };

  const app = createApp({ config, pool, gameService: fakeGameService, rateLimits: { authMax: 1000, globalMax: 10000 } });
  await new Promise((resolve) => {
    server = app.listen(0, '127.0.0.1', resolve);
  });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  if (skip || !server) return;
  await new Promise((resolve) => server.close(resolve));
  await pool.end();
});

test('POST /auth/google is mounted and validates the token body', async () => {
  const config = loadConfig({
    JWT_SECRET: 'test-secret-that-is-long-enough',
    BCRYPT_ROUNDS: '4',
    DB_HOST: '127.0.0.1',
    DB_USER: 'test',
    DB_NAME: 'gamehub_test',
  });
  const app = createApp({ config, pool: { execute: async () => { throw new Error('Unexpected database access'); } }, gameService: fakeGameService });
  const localServer = await new Promise((resolve) => {
    const instance = app.listen(0, '127.0.0.1', () => resolve(instance));
  });

  try {
    const response = await fetch(`http://127.0.0.1:${localServer.address().port}/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    const body = await response.json();
    assert.equal(response.status, 400);
    assert.equal(body.error.code, 'VALIDATION_ERROR');
  } finally {
    await new Promise((resolve, reject) => localServer.close((error) => error ? reject(error) : resolve()));
  }
});

test('POST /auth/google validates Firebase ID tokens', { skip }, async () => {
  const missing = await api('POST', '/auth/google', {});
  assert.equal(missing.status, 400);
  assert.equal(missing.body.error.code, 'VALIDATION_ERROR');

  const invalid = await api('POST', '/auth/google', { idToken: 'invalid-token' });
  assert.equal(invalid.status, 401);
  assert.equal(invalid.body.error.code, 'UNAUTHORIZED');

  const expired = await api('POST', '/auth/google', { idToken: 'expired-token' });
  assert.equal(expired.status, 401);
  assert.equal(expired.body.error.code, 'TOKEN_EXPIRED');

  const unverified = await api('POST', '/auth/google', { idToken: 'unverified-email' });
  assert.equal(unverified.status, 400);
  assert.equal(unverified.body.error.code, 'VALIDATION_ERROR');
});

test('POST /auth/google provisions a Google-only user and reuses the account', { skip }, async () => {
  const first = await api('POST', '/auth/google', { idToken: 'valid-new-user' });
  assert.equal(first.status, 200);
  assert.ok(first.body.token);
  assert.equal(first.body.user.email, 'newgoogle@example.com');
  assert.equal(first.body.user.passwordHash, undefined);

  const [rows] = await pool.query('SELECT password_hash, google_uid FROM users WHERE user_id = ?', [first.body.user.id]);
  assert.equal(rows[0].password_hash, null);
  assert.equal(rows[0].google_uid, 'google_uid_new');

  const repeated = await api('POST', '/auth/google', { idToken: 'valid-new-user' });
  assert.equal(repeated.status, 200);
  assert.equal(repeated.body.user.id, first.body.user.id);
});
