'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const { createNotificationService } = require('../src/services/notificationService');
const { createJobsRouter } = require('../src/routes/jobs');
const { errorHandler } = require('../src/middleware/errorHandler');

test('notification service stores notification in DB even if push fails', async () => {
  const executeCalls = [];
  const fakePool = {
    async execute(query, params) {
      executeCalls.push({ query, params });
      if (query.includes('SELECT notifications_enabled')) {
        return [[{ notifications_enabled: true, wishlist_alerts: true, release_alerts: true }]];
      }
      if (query.includes('SELECT token')) {
        return [[{ token: 'fake-token-123456789' }]];
      }
      if (query.includes('INSERT INTO notifications')) {
        return [{ insertId: 42 }];
      }
      return [[]];
    }
  };

  const fakeMessaging = {
    async sendEachForMulticast() {
      throw new Error('FCM network failure');
    }
  };

  const service = createNotificationService({ pool: fakePool, messaging: fakeMessaging });
  const id = await service.createNotification({ userId: 1, type: 'wishlist', title: 'Test', body: 'Body', gameId: 10 });
  assert.equal(id, 42);
  assert.ok(executeCalls.some(c => c.query.includes('INSERT INTO notifications')));
});

test('notification service respects notifications_enabled = false', async () => {
  const fakePool = {
    async execute(query, params) {
      if (query.includes('SELECT notifications_enabled')) {
        return [[{ notifications_enabled: false, wishlist_alerts: true, release_alerts: true }]];
      }
      if (query.includes('INSERT INTO notifications')) {
        return [{ insertId: 43 }];
      }
      return [[]];
    }
  };

  let pushSent = false;
  const fakeMessaging = {
    async sendEachForMulticast() {
      pushSent = true;
    }
  };

  const service = createNotificationService({ pool: fakePool, messaging: fakeMessaging });
  await service.createNotification({ userId: 1, type: 'wishlist', title: 'Test', body: 'Body', gameId: 10 });
  assert.equal(pushSent, false);
});

test('notification service removes invalid tokens on registration-token-not-registered error', async () => {
  const deletedTokens = [];
  const fakePool = {
    async execute(query, params) {
      if (query.includes('SELECT notifications_enabled')) {
        return [[{ notifications_enabled: true, wishlist_alerts: true, release_alerts: true }]];
      }
      if (query.includes('SELECT token')) {
        return [[{ token: 'bad-token-xyz' }]];
      }
      if (query.includes('INSERT INTO notifications')) {
        return [{ insertId: 44 }];
      }
      if (query.includes('DELETE FROM fcm_tokens')) {
        deletedTokens.push(params[0]);
      }
      return [[]];
    }
  };

  const fakeMessaging = {
    async sendEachForMulticast() {
      return {
        responses: [
          { success: false, error: { code: 'messaging/registration-token-not-registered' } }
        ]
      };
    }
  };

  const service = createNotificationService({ pool: fakePool, messaging: fakeMessaging });
  await service.createNotification({ userId: 1, type: 'wishlist', title: 'Test', body: 'Body', gameId: 10 });
  assert.deepEqual(deletedTokens, ['bad-token-xyz']);
});

test('jobs router protects /generate-notifications with X-Job-Secret (missing, incorrect, unconfigured, valid)', async () => {
  const fakePool = {
    async execute() {
      return [[]];
    }
  };
  const fakeNotificationService = {};
  const fakeGameService = {
    async discover() {
      return { items: [] };
    }
  };
  const config = { jobSecret: 'secret123' };

  const app = express();
  app.use(express.json());
  app.use('/jobs', createJobsRouter({ pool: fakePool, notificationService: fakeNotificationService, gameService: fakeGameService, config }));
  app.use(errorHandler);

  let server;
  await new Promise((resolve) => { server = app.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;

  try {
    // 1. Missing secret -> 401
    const resMissing = await fetch(`http://127.0.0.1:${port}/jobs/generate-notifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    });
    assert.equal(resMissing.status, 401);

    // 2. Incorrect secret -> 401
    const resWrong = await fetch(`http://127.0.0.1:${port}/jobs/generate-notifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Job-Secret': 'wrongsecret' },
      body: JSON.stringify({})
    });
    assert.equal(resWrong.status, 401);

    // 3. Valid secret -> 200
    const resValid = await fetch(`http://127.0.0.1:${port}/jobs/generate-notifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Job-Secret': 'secret123' },
      body: JSON.stringify({})
    });
    assert.equal(resValid.status, 200);
    const data = await resValid.json();
    assert.equal(data.status, 'ok');

    // 4. Unconfigured secret app instance -> 401
    const unconfiguredConfig = { jobSecret: null };
    const appUnconfigured = express();
    appUnconfigured.use(express.json());
    appUnconfigured.use('/jobs', createJobsRouter({ pool: fakePool, notificationService: fakeNotificationService, gameService: fakeGameService, config: unconfiguredConfig }));
    appUnconfigured.use(errorHandler);

    let serverUnconf;
    await new Promise((resolve) => { serverUnconf = appUnconfigured.listen(0, '127.0.0.1', resolve); });
    const portUnconf = serverUnconf.address().port;
    try {
      const resUnconf = await fetch(`http://127.0.0.1:${portUnconf}/jobs/generate-notifications`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Job-Secret': 'secret123' },
        body: JSON.stringify({})
      });
      assert.equal(resUnconf.status, 401);
    } finally {
      await new Promise((resolve) => serverUnconf.close(resolve));
    }
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
