'use strict';

const admin = require('firebase-admin');

let firebaseInitialized = false;

function initFirebase(env = process.env) {
  if (firebaseInitialized) return admin;

  const serviceAccountJson = env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const credentialsPath = env.FIREBASE_CREDENTIALS_PATH;
  const serviceAccountBase64 = env.FIREBASE_SERVICE_ACCOUNT_BASE64;

  try {
    let credential = null;
    if (serviceAccountJson) {
      credential = admin.credential.cert(JSON.parse(serviceAccountJson));
    } else if (serviceAccountBase64) {
      const decoded = Buffer.from(serviceAccountBase64, 'base64').toString('utf8');
      credential = admin.credential.cert(JSON.parse(decoded));
    } else if (credentialsPath) {
      credential = admin.credential.cert(require(credentialsPath));
    } else {
      if (env.NODE_ENV === 'test' || env.FIREBASE_MOCK === 'true') {
        console.info('[firebase] Running in mock/test mode without credentials.');
        if (admin.apps.length === 0) {
          admin.initializeApp({ projectId: 'gamehub-test' });
        }
        firebaseInitialized = true;
        return admin;
      }
      console.warn('[firebase] Warning: Firebase service account credentials not configured.');
      return null;
    }

    if (admin.apps.length === 0) {
      admin.initializeApp({ credential });
    }
    firebaseInitialized = true;
    console.info('[firebase] Firebase Admin SDK initialized successfully.');
  } catch (error) {
    console.error('[firebase] Failed to initialize Firebase Admin SDK:', error.message);
    throw error;
  }

  return admin;
}

module.exports = { initFirebase };
