'use strict';

/**
 * Creates the tables from sql/schema.sql on the database named in .env.
 * Safe to run more than once (every statement uses CREATE TABLE IF NOT EXISTS).
 * Usage: npm run db:init
 */
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const { createSslOptions } = require('../src/db');

function validateInitTarget(env) {
  const required = ['DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'DB_NAME'];
  const missing = required.filter((name) => !env[name]);
  if (missing.length > 0) {
    throw new Error(`Missing required database configuration: ${missing.join(', ')}.`);
  }
  if (env.DB_SSL !== 'true' || !env.DB_SSL_CA_PATH) {
    throw new Error('Database initialization requires DB_SSL=true and DB_SSL_CA_PATH.');
  }
  if (/^(localhost|127(?:\.\d{1,3}){0,3}|::1)$/i.test(env.DB_HOST)) {
    throw new Error('Refusing to initialize a local database.');
  }
  const port = Number(env.DB_PORT);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('DB_PORT must be a valid TCP port.');
  }
  if (env.DB_INIT_CONFIRM_HOST !== env.DB_HOST || env.DB_INIT_CONFIRM_DATABASE !== env.DB_NAME) {
    throw new Error('Confirm the target by setting DB_INIT_CONFIRM_HOST and DB_INIT_CONFIRM_DATABASE to the intended values.');
  }
}

async function main() {
  validateInitTarget(process.env);
  const { DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, DB_SSL } = process.env;

  const connection = await mysql.createConnection({
    host: DB_HOST,
    port: Number(DB_PORT),
    user: DB_USER,
    password: DB_PASSWORD ?? '',
    database: DB_NAME,
    ssl: createSslOptions({ ssl: DB_SSL === 'true' }),
    multipleStatements: true,
  });

  try {
    const sql = fs.readFileSync(path.join(__dirname, '..', 'sql', 'schema.sql'), 'utf8');
    await connection.query(sql);
    const [tables] = await connection.query('SHOW TABLES');
    console.log('Database ready. Tables:', tables.map((row) => Object.values(row)[0]).join(', '));
  } finally {
    await connection.end();
  }
}

if (require.main === module) {
  main().catch((err) => {
    const code = typeof err?.code === 'string' ? ` (${err.code})` : '';
    console.error(`Database setup failed${code}. Check database connectivity and configuration.`);
    process.exit(1);
  });
}

module.exports = { validateInitTarget };