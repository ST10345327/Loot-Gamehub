'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const mysql = require('mysql2/promise');

const { createPool } = require('../src/db');

test('createPool passes the configured CA with certificate verification enabled', (t) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'gamehub-ca-'));
  const caPath = path.join(directory, 'ca.pem');
  const caContents = 'test CA certificate contents';
  fs.writeFileSync(caPath, caContents);
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));

  const previousCaPath = process.env.DB_SSL_CA_PATH;
  t.after(() => {
    if (previousCaPath === undefined) delete process.env.DB_SSL_CA_PATH;
    else process.env.DB_SSL_CA_PATH = previousCaPath;
  });
  process.env.DB_SSL_CA_PATH = caPath;

  const createPoolMock = t.mock.method(mysql, 'createPool', () => ({}));
  createPool({ ssl: true });

  assert.deepEqual(createPoolMock.mock.calls[0].arguments[0].ssl, {
    ca: caContents,
    rejectUnauthorized: true,
  });
});

test('createPool rejects TLS configuration without a CA path', (t) => {
  const previousCaPath = process.env.DB_SSL_CA_PATH;
  t.after(() => {
    if (previousCaPath === undefined) delete process.env.DB_SSL_CA_PATH;
    else process.env.DB_SSL_CA_PATH = previousCaPath;
  });
  delete process.env.DB_SSL_CA_PATH;

  assert.throws(() => createPool({ ssl: true }), /DB_SSL_CA_PATH is required/);
});

test('createPool rejects a CA path that does not exist', (t) => {
  const previousCaPath = process.env.DB_SSL_CA_PATH;
  t.after(() => {
    if (previousCaPath === undefined) delete process.env.DB_SSL_CA_PATH;
    else process.env.DB_SSL_CA_PATH = previousCaPath;
  });
  process.env.DB_SSL_CA_PATH = path.join(os.tmpdir(), 'gamehub-missing-ca.pem');

  assert.throws(() => createPool({ ssl: true }), /existing CA file/);
});

test('createPool reports an unreadable CA without exposing its path', (t) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'gamehub-ca-'));
  const caPath = path.join(directory, 'ca.pem');
  fs.writeFileSync(caPath, 'test CA certificate contents');
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));

  const previousCaPath = process.env.DB_SSL_CA_PATH;
  t.after(() => {
    if (previousCaPath === undefined) delete process.env.DB_SSL_CA_PATH;
    else process.env.DB_SSL_CA_PATH = previousCaPath;
  });
  process.env.DB_SSL_CA_PATH = caPath;

  t.mock.method(fs, 'accessSync', () => {
    throw new Error('permission denied');
  });
  const errorLog = t.mock.method(console, 'error', () => {});

  assert.throws(() => createPool({ ssl: true }), /readable CA file/);
  assert.deepEqual(errorLog.mock.calls[0].arguments, [
    '[db] TLS CA status: configured=true exists=true readable=false',
  ]);
  assert.equal(errorLog.mock.calls[0].arguments[0].includes(caPath), false);
});
