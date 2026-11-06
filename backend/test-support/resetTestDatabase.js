'use strict';

const fs = require('node:fs');
const path = require('node:path');
const mysql = require('mysql2/promise');

async function resetTestDatabase(dbConfig) {
  const admin = await mysql.createConnection(dbConfig);
  try {
    for (const table of ['notifications', 'fcm_tokens', 'recently_viewed', 'wishlist', 'favourites', 'library', 'users']) {
      await admin.query(`DROP TABLE IF EXISTS \`${table}\``);
    }

    const schema = fs.readFileSync(path.join(__dirname, '..', 'sql', 'schema.sql'), 'utf8');
    const statements = schema.split(';').filter((statement) => /\bCREATE TABLE IF NOT EXISTS\b/i.test(statement));
    if (statements.length === 0) {
      throw new Error('No CREATE TABLE statements found in the API test schema.');
    }
    for (const statement of statements) {
      await admin.query(statement);
    }
  } finally {
    await admin.end();
  }
}

module.exports = { resetTestDatabase };
