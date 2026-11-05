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
      const serviceAccount = JSON.parse(serviceAccountJson);
      credential = admin.credential.cert(serviceAccount);
    } else if (serviceAccountBase64) {
      const decoded = Buffer.from(serviceAccountBase64, 'base64').toString('utf8');
      const serviceAccount = JSON.parse(decoded);
      credential = admin.credential.cert(serviceAccount);
    } else if (credentialsPath) {
      const serviceAccount = require(credentialsPath);
      credential = admin.credential.cert(serviceAccount);
    } else {
      if (env.NODE_ENV === 'test' || env.FIREBASE_MOCK === 'true') {
        console.info('[firebase] Running in mock/test mode without credentials.');
        if (admin.apps.length === 0) {
          try {
            admin.initializeApp({ projectId: 'gamehub-test' });
          } catch {}
        }
        firebaseInitialized = true;
        return admin;
      }
      console.warn('[firebase] Warning: Firebase service account credentials not configured. Push notifications will be skipped.');
      return null;
    }

    if (admin.apps.length === 0) {
      admin.initializeApp({ credential });
    }
    firebaseInitialized = true;
    console.info('[firebase] Firebase Admin SDK initialized successfully.');
  } catch (error) {
    console.error('[firebase] Failed to initialize Firebase Admin SDK:', error.message);
    if (env.NODE_ENV === 'production' && env.FIREBASE_STRICT === 'true') {
      throw error;
    }
  }

  return admin;
}

function getMessaging() {
  if (admin.apps.length === 0) {
    initFirebase();
  }
  return admin.apps.length > 0 ? admin.messaging() : null;
}

module.exports = { initFirebase, getMessaging };
