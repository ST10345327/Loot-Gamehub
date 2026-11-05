'use strict';

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const { ApiError, asyncHandler } = require('./utils/errors');
const { requireAuth } = require('./middleware/auth');
const { notFoundHandler, errorHandler } = require('./middleware/errorHandler');
const { createAuthRouter } = require('./routes/auth');
const { createMeRouter } = require('./routes/me');
const { createGamesRouter } = require('./routes/games');
const { createSavedGamesRouter, createWishlistRouter } = require('./routes/savedGames');
const { createRecentRouter } = require('./routes/recent');
const { createNotificationsRouter, createFcmTokenRouter } = require('./routes/notifications');
const { createNotificationService } = require('./services/notificationService');
const { getMessaging } = require('./services/firebase');
const { createJobsRouter } = require('./routes/jobs');

const tooManyRequests = (req, res, next) =>
  next(new ApiError(429, 'RATE_LIMITED', 'Too many requests. Please wait a moment and try again.'));

/** Logs "METHOD /path status time" for every request. Bodies and headers are never logged. */
function requestLogger(req, res, next) {
  const start = Date.now();
  res.on('finish', () => {
    console.log(`${req.method} ${req.originalUrl.split('?')[0]} ${res.statusCode} ${Date.now() - start}ms`);
  });
  next();
}

/**
 * Builds the Express app. Dependencies (config, database pool, game service) are passed in
 * rather than created here, so tests can supply a test database and a fake IGDB.
 */
function createApp({ config, pool, gameService, rateLimits = {} }) {
  const { authMax = 30, globalMax = 600 } = rateLimits;
  const app = express();

  app.set('trust proxy', 1); // hosts such as Render sit behind a proxy; needed for correct client IPs
  app.disable('x-powered-by');
  app.use(helmet());
  app.use(cors());
  app.use(express.json({ limit: '50kb' }));
  app.use(requestLogger);
  app.use(
    rateLimit({ windowMs: 15 * 60 * 1000, limit: globalMax, standardHeaders: 'draft-7', legacyHeaders: false, handler: tooManyRequests })
  );

  app.get('/', (req, res) => res.json({ name: 'Loot - Gamehub API', status: 'ok' }));
  app.get(
    '/health',
    asyncHandler(async (req, res) => {
      await pool.query('SELECT 1');
      res.json({ status: 'ok' });
    })
  );

  // Stricter limit on account endpoints to slow down password guessing.
  const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: authMax,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    handler: tooManyRequests,
  });
  app.use('/auth', authLimiter, createAuthRouter({ pool, config }));

  // Everything below needs a valid token.
  const protect = requireAuth(config);
  const messaging = getMessaging();
  const notificationService = createNotificationService({ pool, messaging });
  app.use('/me', protect, createMeRouter({ pool }), createFcmTokenRouter({ notificationService }));
  app.use('/games', protect, createGamesRouter({ gameService }));
  app.use('/library', protect, createSavedGamesRouter({ pool, table: 'library', hasStatus: true }));
  app.use('/favourites', protect, createSavedGamesRouter({ pool, table: 'favourites' }));
  app.use('/wishlist', protect, createWishlistRouter({ pool }));
  app.use('/recent', protect, createRecentRouter({ pool }));
  app.use('/notifications', protect, createNotificationsRouter({ notificationService }));
  app.use('/jobs', createJobsRouter({ pool, notificationService, gameService, config }));

  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}

module.exports = { createApp };
