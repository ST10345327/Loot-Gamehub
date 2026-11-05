'use strict';

/**
 * Service handling FCM token registration, notification history creation,
 * read status, unread counts, and dispatching pushes.
 */
function createNotificationService({ pool, messaging = null }) {
  return {
    async registerToken(userId, token) {
      if (!token || typeof token !== 'string') return;
      await pool.execute(
        `INSERT INTO fcm_tokens (user_id, token, updated_at)
         VALUES (?, ?, CURRENT_TIMESTAMP(3))
         ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3)`,
        [userId, token]
      );
    },

    async removeToken(userId, token) {
      if (!token) {
        await pool.execute('DELETE FROM fcm_tokens WHERE user_id = ?', [userId]);
      } else {
        await pool.execute('DELETE FROM fcm_tokens WHERE user_id = ? AND token = ?', [userId, token]);
      }
    },

    async getNotifications(userId) {
      const [rows] = await pool.execute(
        `SELECT id, user_id AS userId, type, title, body, game_id AS gameId, is_read AS isRead, created_at AS createdAt
         FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50`,
        [userId]
      );
      return rows.map((r) => ({ ...r, isRead: Boolean(r.isRead) }));
    },

    async getUnreadCount(userId) {
      const [rows] = await pool.execute(
        'SELECT COUNT(*) AS count FROM notifications WHERE user_id = ? AND is_read = FALSE',
        [userId]
      );
      return rows[0].count;
    },

    async markAsRead(userId, notificationId) {
      const [result] = await pool.execute(
        'UPDATE notifications SET is_read = TRUE WHERE id = ? AND user_id = ?',
        [notificationId, userId]
      );
      return result.affectedRows > 0;
    },

    async markAllAsRead(userId) {
      await pool.execute('UPDATE notifications SET is_read = TRUE WHERE user_id = ?', [userId]);
    },

    async createNotification({ userId, type, title, body, gameId = null }) {
      // 1. Prevent duplicate notifications for the same user, game, and type within the last 24 hours
      if (gameId) {
        const [existing] = await pool.execute(
          `SELECT id FROM notifications
           WHERE user_id = ? AND type = ? AND game_id = ? AND created_at >= NOW() - INTERVAL 24 HOUR`,
          [userId, type, gameId]
        );
        if (existing.length > 0) return null;
      }

      // 2. Persist notification record in the database independently of push-delivery success
      const [result] = await pool.execute(
        `INSERT INTO notifications (user_id, type, title, body, game_id, is_read, created_at)
         VALUES (?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP(3))`,
        [userId, type, title, body, gameId]
      );
      const insertId = result.insertId;

      // 3. Dispatch push notification respecting global and category preferences
      try {
        const [users] = await pool.execute(
          `SELECT notifications_enabled, wishlist_alerts, release_alerts FROM users WHERE user_id = ?`,
          [userId]
        );
        if (users.length > 0) {
          const user = users[0];
          const notificationsEnabled = Boolean(user.notifications_enabled);
          const wishlistAlerts = Boolean(user.wishlist_alerts);
          const releaseAlerts = Boolean(user.release_alerts);

          let shouldSend = notificationsEnabled;
          if (shouldSend) {
            if ((type === 'wishlist' || type === 'upcoming') && !wishlistAlerts) shouldSend = false;
            if ((type === 'release' || type === 'new_release') && !releaseAlerts) shouldSend = false;
          }

          if (shouldSend) {
            const [tokensRows] = await pool.execute(
              `SELECT token FROM fcm_tokens WHERE user_id = ?`,
              [userId]
            );
            if (tokensRows.length > 0 && messaging) {
              const tokens = tokensRows.map((r) => r.token);
              const message = {
                notification: { title, body },
                data: {
                  title,
                  body,
                  ...(gameId ? { gameId: String(gameId), nav_route: `game/${gameId}` } : {}),
                },
                tokens,
              };

              const response = await messaging.sendEachForMulticast(message);
              if (response && response.responses) {
                response.responses.forEach(async (resp, idx) => {
                  if (!resp.success) {
                    const errCode = resp.error && resp.error.code;
                    if (
                      errCode === 'messaging/registration-token-not-registered' ||
                      errCode === 'messaging/invalid-registration-token' ||
                      errCode === 'NOT_FOUND' ||
                      (resp.error && resp.error.status === 'NOT_FOUND')
                    ) {
                      const failedToken = tokens[idx];
                      const masked = failedToken.length > 6 ? failedToken.substring(0, 6) + '...' : '***';
                      console.info(`[notifications] Removing invalid/expired FCM token (${masked})`);
                      try {
                        await pool.execute('DELETE FROM fcm_tokens WHERE token = ?', [failedToken]);
                      } catch (dbErr) {
                        console.error('[notifications] Failed to delete invalid token:', dbErr.message);
                      }
                    }
                  }
                });
              }
            }
          }
        }
      } catch (err) {
        console.error('[notifications] Failed to dispatch push notification:', err.message);
      }

      return insertId;
    },
  };
}

module.exports = { createNotificationService };
