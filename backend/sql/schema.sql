-- GameHub database schema (MySQL 8 / MariaDB 10.5+).
-- Matches the Part 1 ERD, with two deliberate additions:
--   1. Saved-game tables keep a small snapshot of the game (name, cover, rating...) so the
--      Library / Wishlist / Favourites screens render without one IGDB lookup per game.
--   2. Notification preferences live as columns on `users` (no separate settings table needed).
-- Ordering columns use millisecond precision (DATETIME(3)) so items added or viewed within the
-- same second still sort correctly.
-- Every user-owned row is deleted automatically when the user is deleted (ON DELETE CASCADE),
-- and a game can only appear once per user per list (UNIQUE user_id + game_id).

CREATE TABLE IF NOT EXISTS users (
  user_id               INT UNSIGNED NOT NULL AUTO_INCREMENT,
  username              VARCHAR(30)  NOT NULL,
  email                 VARCHAR(100) NOT NULL,
  password_hash         VARCHAR(255) NULL,                -- bcrypt hash, or NULL for Google-only users
  google_uid            VARCHAR(128) NULL,                -- Firebase UID for Google SSO
  preferred_language    ENUM('en','tn','zu')              NOT NULL DEFAULT 'en',
  theme_preference      ENUM('system','light','dark')     NOT NULL DEFAULT 'system',
  notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
  wishlist_alerts       BOOLEAN      NOT NULL DEFAULT TRUE,
  release_alerts        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_active           DATETIME     NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT uq_users_email    UNIQUE (email),
  CONSTRAINT uq_users_username UNIQUE (username),
  CONSTRAINT uq_users_google_uid UNIQUE (google_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS library (
  lib_id       INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      INT UNSIGNED NOT NULL,
  game_id      INT UNSIGNED NOT NULL,                     -- IGDB game id
  status       ENUM('want_to_play','playing','completed') NOT NULL DEFAULT 'want_to_play',
  game_name    VARCHAR(255) NOT NULL,
  cover_url    VARCHAR(500) NULL,
  rating       DECIMAL(3,1) NULL,                         -- 0.0 - 10.0
  release_year SMALLINT UNSIGNED NULL,
  genre        VARCHAR(100) NULL,
  platforms    VARCHAR(255) NULL,
  added_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (lib_id),
  CONSTRAINT uq_library_user_game UNIQUE (user_id, game_id),
  CONSTRAINT fk_library_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS favourites (
  fav_id       INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      INT UNSIGNED NOT NULL,
  game_id      INT UNSIGNED NOT NULL,
  game_name    VARCHAR(255) NOT NULL,
  cover_url    VARCHAR(500) NULL,
  rating       DECIMAL(3,1) NULL,
  release_year SMALLINT UNSIGNED NULL,
  genre        VARCHAR(100) NULL,
  platforms    VARCHAR(255) NULL,
  added_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (fav_id),
  CONSTRAINT uq_favourites_user_game UNIQUE (user_id, game_id),
  CONSTRAINT fk_favourites_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wishlist (
  wish_id      INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      INT UNSIGNED NOT NULL,
  game_id      INT UNSIGNED NOT NULL,
  game_name    VARCHAR(255) NOT NULL,
  cover_url    VARCHAR(500) NULL,
  rating       DECIMAL(3,1) NULL,
  release_year SMALLINT UNSIGNED NULL,
  genre        VARCHAR(100) NULL,
  platforms    VARCHAR(255) NULL,
  added_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (wish_id),
  CONSTRAINT uq_wishlist_user_game UNIQUE (user_id, game_id),
  CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recently_viewed (
  view_id      INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      INT UNSIGNED NOT NULL,
  game_id      INT UNSIGNED NOT NULL,
  game_name    VARCHAR(255) NOT NULL,
  cover_url    VARCHAR(500) NULL,
  rating       DECIMAL(3,1) NULL,
  release_year SMALLINT UNSIGNED NULL,
  genre        VARCHAR(100) NULL,
  platforms    VARCHAR(255) NULL,
  viewed_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),  -- always written as UTC by the API
  PRIMARY KEY (view_id),
  CONSTRAINT uq_recent_user_game UNIQUE (user_id, game_id),
  CONSTRAINT fk_recent_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;