'use strict';

/**
 * Reads and validates configuration from environment variables.
 * Taking `env` as a parameter (instead of reading process.env directly) keeps this
 * function pure, so tests can call it with a fake environment.
 */
function loadConfig(env = process.env) {
  const required = (name) => {
    const value = env[name];
    if (!value) {
      throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
  };

  const jwtSecret = required('JWT_SECRET');
  if (jwtSecret.length < 16) {
    throw new Error('JWT_SECRET must be at least 16 characters long.');
  }

  return Object.freeze({
    env: env.NODE_ENV || 'development',
    port: Number(env.PORT) || 3000,
    jwtSecret,
    jwtExpiresIn: env.JWT_EXPIRES_IN || '7d',
    bcryptRounds: Number(env.BCRYPT_ROUNDS) || 12,
    db: Object.freeze({
      host: required('DB_HOST'),
      port: Number(env.DB_PORT) || 3306,
      user: required('DB_USER'),
      password: env.DB_PASSWORD ?? '',
      database: required('DB_NAME'),
      ssl: env.DB_SSL === 'true',
    }),
    // IGDB credentials are optional at start-up so the auth/list endpoints still work
    // without them; game endpoints answer 503 until they are provided.
    igdb: Object.freeze({
      clientId: env.IGDB_CLIENT_ID || null,
      clientSecret: env.IGDB_CLIENT_SECRET || null,
    }),
    jobSecret: env.JOB_SECRET || null,
  });
}

module.exports = { loadConfig };