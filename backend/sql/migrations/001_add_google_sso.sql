-- Migration: Add Google SSO support (password_hash nullable, add google_uid)
ALTER TABLE users
  MODIFY COLUMN password_hash VARCHAR(255) NULL,
  ADD COLUMN google_uid VARCHAR(128) NULL AFTER email,
  ADD CONSTRAINT uq_users_google_uid UNIQUE (google_uid);
