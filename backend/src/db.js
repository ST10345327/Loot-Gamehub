'use strict';

const fs = require('node:fs');
const mysql = require('mysql2/promise');

/** Creates the shared MySQL connection pool. */
function createPool(dbConfig) {
  const caPath = process.env.DB_SSL_CA_PATH;

  const sslOptions = dbConfig.ssl
    ? {
        rejectUnauthorized: true,
        ...(caPath ? { ca: fs.readFileSync(caPath, 'utf8') } : {}),
      }
    : undefined;

  return mysql.createPool({
    host: dbConfig.host,
    port: dbConfig.port,
    user: dbConfig.user,
    password: dbConfig.password,
    database: dbConfig.database,
    ssl: sslOptions,
    waitForConnections: true,
    connectionLimit: 10,
    charset: 'utf8mb4',
    timezone: 'Z',        // store and read DATETIME values as UTC
    decimalNumbers: true, // return DECIMAL columns (ratings) as numbers, not strings
  });
}

module.exports = { createPool };