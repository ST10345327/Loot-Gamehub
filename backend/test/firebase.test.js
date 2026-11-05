'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { generateKeyPairSync } = require('node:crypto');
const { getAuth } = require('firebase-admin/auth');
const { initFirebase } = require('../src/services/firebase');

test('Firebase Admin v14 initializes from service-account JSON', () => {
  const { privateKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  });
  const serviceAccount = {
    project_id: 'gamehub-test',
    client_email: 'firebase-admin@gamehub-test.iam.gserviceaccount.com',
    private_key: privateKey,
  };

  const app = initFirebase({ FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount) });

  assert.equal(app.name, '[DEFAULT]');
  assert.equal(getAuth(app).app, app);
});
