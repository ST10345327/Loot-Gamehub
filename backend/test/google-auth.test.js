'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

const { loadConfig } = require('../src/config');
const { createPool } = require('../src/db');
const { createApp } = require('../src/app');
const { initFirebase } = require('../src/services/firebase');

const skip = process.env.TEST_DB_HOST ? false : 'Set TEST_DB_HOST (and friends) to run Google Auth API tests.';

const fakeGameService = {
  search: async () => ({ items: [], hasMore: false }),
  details: async (id) => ({ id, name: 'Game' }),
  discover: async () => ({ items: [], hasMore: false }),
  random: async () => ({ id: 1, name: 'Game' }),
};

let server;
let baseUrl;
let pool;
let adminAuth;

async function api(method, route, { token, body, raw } = {}) {
  const response = await fetch(baseUrl + route, {
    method,
    headers: { ...(body !== undefined || raw ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: raw ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}

test.before(async () => {
  if (skip) return;
  const dbConfig = {
    host: process.env.TEST_DB_HOST,
    port: Number(process.env.TEST_DB_PORT) || 3306,
    user: process.env.TEST_DB_USER,
    password: process.env.TEST_DB_PASSWORD ?? '',
    database: process.env.TEST_DB_NAME,
  };

  const adminConn = await mysql.createConnection({ ...dbConfig, multipleStatements: true });
  await adminConn.query('SET FOREIGN_KEY_CHECKS = 0; DROP TABLE IF EXISTS recently_viewed, wishlist, favourites, library, users; SET FOREIGN_KEY_CHECKS = 1;');
  await adminConn.query(fs.readFileSync(path.join(__dirname, '..', 'sql', 'schema.sql'), 'utf8'));
  await adminConn.end();

  const config = loadConfig({
    JWT_SECRET: 'test-secret-that-is-long-enough',
    BCRYPT_ROUNDS: '4',
    DB_HOST: dbConfig.host, DB_PORT: String(dbConfig.port), DB_USER: dbConfig.user, DB_PASSWORD: dbConfig.password, DB_NAME: dbConfig.database,
  });
  pool = createPool(config.db);

  // Initialize firebase in test mode and mock auth
  const firebaseAdmin = initFirebase({ NODE_ENV: 'test', FIREBASE_MOCK: 'true' });
  adminAuth = firebaseAdmin.auth();
  adminAuth.verifyIdToken = async (idToken) => {
    if (idToken === 'valid-new-user') {
      return { uid: 'google_uid_new', email: 'newgoogle@example.com', email_verified: true, name: 'New Google User' };
    }
    if (idToken === 'valid-existing-user') {
      return { uid: 'google_uid_existing', email: 'existinggoogle@example.com', email_verified: true, name: 'Existing Google User' };
    }
    if (idToken === 'conflict-password-user') {
      return { uid: 'google_uid_conflict', email: 'passworduser@example.com', email_verified: true, name: 'Password User' };
    }
    if (idToken === 'unverified-email') {
      return { uid: 'google_uid_unver', email: 'unverified@example.com', email_verified: false, name: 'Unverified' };
    }
    if (idToken === 'expired-token') {
      const err = new Error('Token expired');
      err.code = 'auth/id-token-expired';
      throw err;
    }
    const err = new Error('Invalid token');
    err.code = 'auth/argument-error';
    throw err;
  };

  const app = createApp({ config, pool, gameService: fakeGameService, rateLimits: { authMax: 1000, globalMax: 10000 } });
  await new Promise((resolve) => { server = app.listen(0, '127.0.0.1', resolve); });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  if (skip) return;
  await new Promise((resolve) => server.close(resolve));
  await pool.end();
});

test('POST /auth/google rejects missing or malformed ID token', { skip }, async () => {
  const res1 = await api('POST', '/auth/google', { body: {} });
  assert.equal(res1.status, 400);
  assert.equal(res1.body.error.code, 'VALIDATION_ERROR');

  const res2 = await api('POST', '/auth/google', { body: { idToken: '' } });
  assert.equal(res2.status, 400);
});

test('POST /auth/google rejects invalid Firebase ID token', { skip }, async () => {
  const res = await api('POST', '/auth/google', { body: { idToken: 'invalid-token' } });
  assert.equal(res.status, 401);
  assert.equal(res.body.error.code, 'UNAUTHORIZED');
});

test('POST /auth/google rejects expired Firebase ID token', { skip }, async () => {
  const res = await api('POST', '/auth/google', { body: { idToken: 'expired-token' } });
  assert.equal(res.status, 401);
  assert.equal(res.body.error.code, 'TOKEN_EXPIRED');
});

test('POST /auth/google rejects unverified email', { skip }, async () => {
  const res = await api('POST', '/auth/google', { body: { idToken: 'unverified-email' } });
  assert.equal(res.status, 400);
  assert.equal(res.body.error.code, 'VALIDATION_ERROR');
});

test('POST /auth/google provisions new Google user successfully with null password_hash and returns JWT', { skip }, async () => {
  const res = await api('POST', '/auth/google', { body: { idToken: 'valid-new-user' } });
  assert.equal(res.status, 200);
  assert.ok(res.body.token);
  assert.equal(res.body.user.email, 'newgoogle@example.com');
  assert.equal(res.body.user.passwordHash, undefined);

  // Verify in database that password_hash is NULL and google_uid is set
  const [rows] = await pool.query('SELECT password_hash, google_uid FROM users WHERE user_id = ?', [res.body.user.id]);
  assert.equal(rows[0].password_hash, null);
  assert.equal(rows[0].google_uid, 'google_uid_new');
});

test('POST /auth/google logs in existing Google user on repeated requests (idempotent)', { skip }, async () => {
  const res1 = await api('POST', '/auth/google', { body: { idToken: 'valid-existing-user' } });
  assert.equal(res1.status, 200);
  const userId = res1.body.user.id;

  const res2 = await api('POST', '/auth/google', { body: { idToken: 'valid-existing-user' } });
  assert.equal(res2.status, 200);
  assert.equal(res2.body.user.id, userId);
});

test('POST /auth/google safely rejects automatic linking when email already belongs to a password account', { skip }, async () => {
  // Register a password account with email passworduser@example.com
  const regRes = await api('POST', '/auth/register', { body: { username: 'password_user', email: 'passworduser@example.com', password: 'passw0rd1' } });
  assert.equal(regRes.status, 201);

  // Try signing in with Google using same email
  const res = await api('POST', '/auth/google', { body: { idToken: 'conflict-password-user' } });
  assert.equal(res.status, 409);
  assert.equal(res.body.error.code, 'CONFLICT');
});
