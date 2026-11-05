'use strict';

const express = require('express');
const { asyncHandler, Errors } = require('../utils/errors');

function createJobsRouter({ pool, notificationService, gameService, config }) {
  const router = express.Router();

  router.post(
    '/generate-notifications',
    asyncHandler(async (req, res) => {
      const secretHeader = req.headers['x-job-secret'] || '';
      const expectedSecret = config.jobSecret || process.env.JOB_SECRET;

      if (!expectedSecret || secretHeader !== expectedSecret) {
        throw Errors.unauthorized('Invalid or missing job secret.');
      }

      let generatedCount = 0;

      // 1. Generate notifications for wishlist items (upcoming releases)
      try {
        const [wishlistItems] = await pool.execute(
          `SELECT w.user_id AS userId, w.game_id AS gameId, w.game_name AS gameName
           FROM wishlist w`
        );

        for (const item of wishlistItems) {
          const [[user], [wishlistCheck]] = await Promise.all([
            pool.execute('SELECT notifications_enabled, wishlist_alerts FROM users WHERE user_id = ?', [item.userId]),
            pool.execute('SELECT 1 FROM wishlist WHERE user_id = ? AND game_id = ?', [item.userId, item.gameId])
          ]);

          if (user && user[0] && Boolean(user[0].notifications_enabled) && Boolean(user[0].wishlist_alerts) && wishlistCheck.length > 0) {
            const notificationId = await notificationService.createNotification({
              userId: item.userId,
              type: 'wishlist',
              title: 'Wishlist Update',
              body: `${item.gameName} from your wishlist has new updates or is releasing soon!`,
              gameId: item.gameId,
            });
            if (notificationId) generatedCount++;
          }
        }
      } catch (err) {
        console.error('[jobs] Error generating wishlist notifications:', err.message);
      }

      // 2. Generate notifications for trending / new releases
      try {
        const trending = await gameService.discover('trending');
        const [users] = await pool.execute(
          `SELECT user_id AS userId FROM users WHERE notifications_enabled = TRUE AND release_alerts = TRUE`
        );

        for (const u of users) {
          for (const game of trending.items.slice(0, 2)) {
            const notificationId = await notificationService.createNotification({
              userId: u.userId,
              type: 'release',
              title: 'New Game Release / Trending',
              body: `Check out trending game: ${game.name}!`,
              gameId: game.id,
            });
            if (notificationId) generatedCount++;
          }
        }
      } catch (err) {
        console.error('[jobs] Error generating release notifications:', err.message);
      }

      res.json({ status: 'ok', generatedCount });
    })
  );

  return router;
}

module.exports = { createJobsRouter };
