'use strict';

const { cert, getApp, getApps, initializeApp } = require('firebase-admin/app');

function initFirebase(env = process.env) {
  if (getApps().length > 0) return getApp();

  const serviceAccountJson = env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const credentialsPath = env.FIREBASE_CREDENTIALS_PATH;
  const serviceAccountBase64 = env.FIREBASE_SERVICE_ACCOUNT_BASE64;

  try {
    let credential = null;
    if (serviceAccountJson) {
      credential = cert(JSON.parse(serviceAccountJson));
    } else if (serviceAccountBase64) {
      const decoded = Buffer.from(serviceAccountBase64, 'base64').toString('utf8');
      credential = cert(JSON.parse(decoded));
    } else if (credentialsPath) {
      credential = cert(require(credentialsPath));
    } else {
      if (env.NODE_ENV === 'test' || env.FIREBASE_MOCK === 'true') {
        console.info('[firebase] Running in mock/test mode without credentials.');
        return initializeApp({ projectId: 'gamehub-test' });
      }
      console.warn('[firebase] Warning: Firebase service account credentials not configured.');
      return null;
    }

    const app = initializeApp({ credential });
    console.info('[firebase] Firebase Admin SDK initialized successfully.');
    return app;
  } catch (error) {
    console.error('[firebase] Failed to initialize Firebase Admin SDK:', error.message);
    throw error;
  }
}

module.exports = { initFirebase };
