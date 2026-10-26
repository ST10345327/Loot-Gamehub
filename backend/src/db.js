'use strict';

const fs = require('node:fs');
const mysql = require('mysql2/promise');

/** Creates the shared MySQL connection pool. */
function createPool(dbConfig) {
  const caPath = process.env.DB_SSL_CA_PATH;

  let sslOptions;
  if (dbConfig.ssl) {
    const exists = Boolean(caPath) && fs.existsSync(caPath);
    let ca;
    if (exists) {
      try {
        fs.accessSync(caPath, fs.constants.R_OK);
        ca = fs.readFileSync(caPath, 'utf8');
      } catch {
        console.error('[db] TLS CA status: configured=true exists=true readable=false');
        throw new Error('DB_SSL_CA_PATH does not point to a readable CA file.');
      }
    }

    const readable = typeof ca === 'string';
    console.info(`[db] TLS CA status: configured=${Boolean(caPath)} exists=${exists} readable=${readable}`);
    if (!caPath) {
      throw new Error('DB_SSL_CA_PATH is required when DB_SSL=true.');
    }
    if (!exists) {
      throw new Error('DB_SSL_CA_PATH does not point to an existing CA file.');
    }
    sslOptions = { ca, rejectUnauthorized: true };
  }
  }

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
    timezone: 'Z',
    decimalNumbers: true,
  });
}

module.exports = { createPool };