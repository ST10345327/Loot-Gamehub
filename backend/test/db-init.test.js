'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { validateInitTarget } = require('../scripts/init-db');

const validTarget = {
  DB_HOST: 'mysql-example.aivencloud.com',
  DB_PORT: '12345',
  DB_USER: 'gamehub',
  DB_PASSWORD: 'test-password',
  DB_NAME: 'defaultdb',
  DB_SSL: 'true',
  DB_SSL_CA_PATH: 'C:\\certs\\ca.pem',
  DB_INIT_CONFIRM_HOST: 'mysql-example.aivencloud.com',
  DB_INIT_CONFIRM_DATABASE: 'defaultdb',
};

test('database initializer accepts an explicitly confirmed TLS target', () => {
  assert.doesNotThrow(() => validateInitTarget(validTarget));
});

test('database initializer requires TLS and a configured CA path', () => {
  assert.throws(() => validateInitTarget({ ...validTarget, DB_SSL: 'false' }), /DB_SSL=true/);
  assert.throws(() => validateInitTarget({ ...validTarget, DB_SSL_CA_PATH: '' }), /DB_SSL_CA_PATH/);
});

test('database initializer rejects local database hosts', () => {
  assert.throws(() => validateInitTarget({ ...validTarget, DB_HOST: 'localhost' }), /local database/);
  assert.throws(() => validateInitTarget({ ...validTarget, DB_HOST: '127.0.0.1' }), /local database/);
});

test('database initializer requires explicit host and database confirmation', () => {
  assert.throws(() => validateInitTarget({ ...validTarget, DB_INIT_CONFIRM_HOST: '' }), /Confirm the target/);
  assert.throws(() => validateInitTarget({ ...validTarget, DB_INIT_CONFIRM_DATABASE: 'gamehub' }), /Confirm the target/);
});

test('database initializer requires a database password', () => {
  assert.throws(() => validateInitTarget({ ...validTarget, DB_PASSWORD: '' }), /DB_PASSWORD/);
});

test('database initializer rejects an invalid or missing port', () => {
  assert.throws(() => validateInitTarget({ ...validTarget, DB_PORT: '' }), /DB_PORT/);
  assert.throws(() => validateInitTarget({ ...validTarget, DB_PORT: 'not-a-port' }), /valid TCP port/);
});
