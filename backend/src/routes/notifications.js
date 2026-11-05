'use strict';

const express = require('express');
const { asyncHandler, Errors } = require('../utils/errors');
const { parseGameId } = require('../utils/validation');

/**
 * Notification routes (all require authentication):
 *   GET    /notifications                 List user's notifications
 *   GET    /notifications/unread-count    Get unread count
 *   POST   /notifications/:id/read        Mark specific notification as read
 *   POST   /notifications/read-all        Mark all notifications as read
 *   POST   /me/fcm-token                  Register/update FCM device token
 *   DELETE /me/fcm-token                  Unregister FCM device token
 */
function createNotificationsRouter({ notificationService }) {
  const router = express.Router();

  router.get(
    '/',
    asyncHandler(async (req, res) => {
      const items = await notificationService.getNotifications(req.userId);
      res.json({ items });
    })
  );

  router.get(
    '/unread-count',
    asyncHandler(async (req, res) => {
      const count = await notificationService.getUnreadCount(req.userId);
      res.json({ count });
    })
  );

  router.post(
    '/:id/read',
    asyncHandler(async (req, res) => {
      const notificationId = Number(req.params.id);
      if (!Number.isInteger(notificationId) || notificationId <= 0) {
        throw Errors.validation({ id: 'Must be a positive integer.' });
      }
      const success = await notificationService.markAsRead(req.userId, notificationId);
      if (!success) {
        throw Errors.notFound('Notification not found.');
      }
      res.json({ status: 'ok' });
    })
  );

  router.post(
    '/read-all',
    asyncHandler(async (req, res) => {
      await notificationService.markAllAsRead(req.userId);
      res.json({ status: 'ok' });
    })
  );

  return router;
}

function createFcmTokenRouter({ notificationService }) {
  const router = express.Router();

  router.post(
    '/fcm-token',
    asyncHandler(async (req, res) => {
      const { token } = req.body;
      if (!token || typeof token !== 'string' || token.isBlank?.() || token.trim() === '') {
        throw Errors.validation({ token: 'Token is required.' });
      }
      await notificationService.registerToken(req.userId, token.trim());
      res.json({ status: 'ok' });
    })
  );

  router.delete(
    '/fcm-token',
    asyncHandler(async (req, res) => {
      const { token } = req.body;
      await notificationService.removeToken(req.userId, token ? token.trim() : null);
      res.json({ status: 'ok' });
    })
  );

  return router;
}

module.exports = { createNotificationsRouter, createFcmTokenRouter };
