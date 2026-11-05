'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const v = require('../src/utils/validation');

const validationDetails = (fn) => {
  try {
    fn();
  } catch (err) {
    assert.equal(err.code, 'VALIDATION_ERROR');
    return err.details;
  }
  assert.fail('expected a VALIDATION_ERROR');
};

test('password rules: 8+ characters and at least one number', () => {
  assert.equal(v.validatePassword('abc12345'), null);
  assert.match(v.validatePassword('short1'), /at least 8/);
  assert.match(v.validatePassword('nodigitshere'), /number/);
  assert.match(v.validatePassword('a1'.repeat(40)), /at most 72/);
  assert.ok(v.validatePassword(undefined));
});

test('username and email rules', () => {
  assert.equal(v.validateUsername('olebogeng_1'), null);
  assert.ok(v.validateUsername('ab'));
  assert.ok(v.validateUsername('has space'));
  assert.equal(v.validateEmail('a@b.co'), null);
  assert.ok(v.validateEmail('not-an-email'));
});

test('parseRegistration trims, lower-cases the email and reports every bad field', () => {
  const ok = v.parseRegistration({ username: ' Gamer_1 ', email: ' Me@Example.COM ', password: 'passw0rdX' });
  assert.deepEqual(ok, { username: 'Gamer_1', email: 'me@example.com', password: 'passw0rdX' });

  const details = validationDetails(() => v.parseRegistration({ username: 'x', email: 'bad', password: 'weak' }));
  assert.deepEqual(Object.keys(details).sort(), ['email', 'password', 'username']);
});

test('parseGoogleLogin trims a Firebase ID token and rejects missing tokens', () => {
  assert.deepEqual(v.parseGoogleLogin({ idToken: ' token ' }), { idToken: 'token' });
  validationDetails(() => v.parseGoogleLogin({}));
  validationDetails(() => v.parseGoogleLogin({ idToken: '  ' }));
  validationDetails(() => v.parseGoogleLogin(null));
});

test('parseSettings whitelists fields and needs at least one', () => {
  assert.deepEqual(v.parseSettings({ preferredLanguage: 'zu', wishlistAlerts: false }), {
    preferred_language: 'zu',
    wishlist_alerts: false,
  });
  validationDetails(() => v.parseSettings({ preferredLanguage: 'fr' }));
  validationDetails(() => v.parseSettings({ themePreference: 'purple' }));
  validationDetails(() => v.parseSettings({}));
});

test('parseGameId accepts positive integers only', () => {
  assert.equal(v.parseGameId('1942'), 1942);
  validationDetails(() => v.parseGameId('abc'));
  validationDetails(() => v.parseGameId('0'));
  validationDetails(() => v.parseGameId('99999999999'));
});

test('parseSavedGame validates the snapshot and the status', () => {
  const game = v.parseSavedGame(
    { name: 'Elden Ring', coverUrl: 'https://images.igdb.com/x.jpg', rating: 9.5, releaseYear: 2022, status: 'playing' },
    { requireStatus: true }
  );
  assert.equal(game.status, 'playing');
  assert.equal(game.genre, null);

  validationDetails(() => v.parseSavedGame({ name: '' }));
  validationDetails(() => v.parseSavedGame({ name: 'X', coverUrl: 'http://insecure' }));
  validationDetails(() => v.parseSavedGame({ name: 'X', rating: 11 }));
  validationDetails(() => v.parseSavedGame({ name: 'X' }, { requireStatus: true }));
});