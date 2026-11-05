'use strict';

const { Errors } = require('./errors');

// Rules from the Part 1 design: "8+ characters, includes number".
// bcrypt only uses the first 72 bytes of a password, so longer input is rejected.
const USERNAME_RE = /^[A-Za-z0-9_]{3,30}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const LANGUAGES = ['en', 'tn', 'zu'];
const THEMES = ['system', 'light', 'dark'];
const STATUSES = ['want_to_play', 'playing', 'completed'];

const isString = (v) => typeof v === 'string';

function validateEmail(email) {
  if (!isString(email) || !EMAIL_RE.test(email.trim()) || email.trim().length > 100) {
    return 'Enter a valid email address.';
  }
  return null;
}

function validateUsername(username) {
  if (!isString(username) || !USERNAME_RE.test(username.trim())) {
    return 'Username must be 3-30 characters: letters, numbers or underscore.';
  }
  return null;
}

function validatePassword(password) {
  if (!isString(password) || password.length < 8) return 'Password must be at least 8 characters.';
  if (password.length > 72) return 'Password must be at most 72 characters.';
  if (!/\d/.test(password)) return 'Password must contain at least one number.';
  return null;
}

/** Throws a VALIDATION_ERROR ApiError if `errors` has any entries. */
function assertNoErrors(errors) {
  const cleaned = Object.fromEntries(Object.entries(errors).filter(([, msg]) => msg));
  if (Object.keys(cleaned).length > 0) throw Errors.validation(cleaned);
}

/** Validates and normalises the body of POST /auth/register. */
function parseRegistration(body = {}) {
  assertNoErrors({
    username: validateUsername(body.username),
    email: validateEmail(body.email),
    password: validatePassword(body.password),
  });
  return {
    username: body.username.trim(),
    email: body.email.trim().toLowerCase(),
    password: body.password,
  };
}

/** Validates the body of POST /auth/login. Only presence is checked so no password rules leak. */
function parseLogin(body = {}) {
  assertNoErrors({
    email: validateEmail(body.email),
    password: isString(body.password) && body.password.length > 0 ? null : 'Password is required.',
  });
  return { email: body.email.trim().toLowerCase(), password: body.password };
}

function parseChangePassword(body = {}) {
  assertNoErrors({
    currentPassword:
      isString(body.currentPassword) && body.currentPassword.length > 0
        ? null
        : 'Current password is required.',
    newPassword: validatePassword(body.newPassword),
  });
  return { currentPassword: body.currentPassword, newPassword: body.newPassword };
}

function parseGoogleLogin(body = {}) {
  if (!body || typeof body.idToken !== 'string' || body.idToken.trim().length === 0) {
    throw Errors.validation({ idToken: 'Firebase ID token is required.' });
  }
  return { idToken: body.idToken.trim() };
}

/** Validates PATCH /me/settings. Every field is optional but must be valid when present. */
function parseSettings(body = {}) {
  const errors = {};
  const update = {};

  if (body.preferredLanguage !== undefined) {
    if (LANGUAGES.includes(body.preferredLanguage)) update.preferred_language = body.preferredLanguage;
    else errors.preferredLanguage = `Must be one of: ${LANGUAGES.join(', ')}.`;
  }
  if (body.themePreference !== undefined) {
    if (THEMES.includes(body.themePreference)) update.theme_preference = body.themePreference;
    else errors.themePreference = `Must be one of: ${THEMES.join(', ')}.`;
  }
  const booleanFields = {
    notificationsEnabled: 'notifications_enabled',
    wishlistAlerts: 'wishlist_alerts',
    releaseAlerts: 'release_alerts',
  };
  for (const [field, column] of Object.entries(booleanFields)) {
    if (body[field] === undefined) continue;
    if (typeof body[field] === 'boolean') update[column] = body[field];
    else errors[field] = 'Must be true or false.';
  }

  assertNoErrors(errors);
  if (Object.keys(update).length === 0) {
    throw Errors.validation({ body: 'Provide at least one setting to update.' });
  }
  return update;
}

/** Parses a positive integer route parameter such as an IGDB game id. */
function parseGameId(value) {
  if (!/^\d{1,10}$/.test(String(value))) throw Errors.validation({ gameId: 'Must be a number.' });
  const id = Number(value);
  if (id < 1 || id > 2147483647) throw Errors.validation({ gameId: 'Must be a valid game id.' });
  return id;
}

/**
 * Validates the game "snapshot" the app sends when saving a game to a list.
 * `requireStatus` is true for the library, where a status is mandatory.
 */
function parseSavedGame(body = {}, { requireStatus = false } = {}) {
  const errors = {};

  if (!isString(body.name) || body.name.trim().length === 0 || body.name.length > 255) {
    errors.name = 'Name is required (max 255 characters).';
  }
  if (body.coverUrl != null && (!isString(body.coverUrl) || !/^https:\/\//.test(body.coverUrl) || body.coverUrl.length > 500)) {
    errors.coverUrl = 'Must be an https URL (max 500 characters).';
  }
  if (body.rating != null && (typeof body.rating !== 'number' || body.rating < 0 || body.rating > 10)) {
    errors.rating = 'Must be a number between 0 and 10.';
  }
  if (body.releaseYear != null && (!Number.isInteger(body.releaseYear) || body.releaseYear < 1900 || body.releaseYear > 2100)) {
    errors.releaseYear = 'Must be a year between 1900 and 2100.';
  }
  if (body.genre != null && (!isString(body.genre) || body.genre.length > 100)) {
    errors.genre = 'Must be text (max 100 characters).';
  }
  if (body.platforms != null && (!isString(body.platforms) || body.platforms.length > 255)) {
    errors.platforms = 'Must be text (max 255 characters).';
  }
  if (requireStatus && !STATUSES.includes(body.status)) {
    errors.status = `Must be one of: ${STATUSES.join(', ')}.`;
  }
  assertNoErrors(errors);

  return {
    name: body.name.trim(),
    coverUrl: body.coverUrl ?? null,
    rating: body.rating ?? null,
    releaseYear: body.releaseYear ?? null,
    genre: body.genre ?? null,
    platforms: body.platforms ?? null,
    status: requireStatus ? body.status : undefined,
  };
}

/** Validates an optional status value (used by move-to-library and the library filter). */
function parseOptionalStatus(value) {
  if (value === undefined || value === null || value === '') return null;
  if (!STATUSES.includes(value)) throw Errors.validation({ status: `Must be one of: ${STATUSES.join(', ')}.` });
  return value;
}

module.exports = {
  LANGUAGES,
  THEMES,
  STATUSES,
  validateEmail,
  validateUsername,
  validatePassword,
  parseRegistration,
  parseLogin,
  parseChangePassword,
  parseGoogleLogin,
  parseSettings,
  parseGameId,
  parseSavedGame,
  parseOptionalStatus,
};