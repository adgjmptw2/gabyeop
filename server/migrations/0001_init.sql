CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE refresh_families (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  revoked_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE INDEX refresh_families_user ON refresh_families(user_id);

CREATE TABLE refresh_tokens (
  hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  family_id TEXT NOT NULL REFERENCES refresh_families(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL,
  used_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE INDEX refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX refresh_tokens_expires ON refresh_tokens(expires_at);

CREATE TABLE profiles (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  data TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  seq INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX profiles_user_seq ON profiles(user_id, seq);

CREATE TABLE weights (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  epoch_day INTEGER NOT NULL,
  weight_kg REAL NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  modified_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  seq INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, id)
);
CREATE INDEX weights_user_seq ON weights(user_id, seq);
CREATE INDEX weights_user_day ON weights(user_id, epoch_day);

CREATE TABLE meals (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  epoch_day INTEGER NOT NULL,
  meal_type TEXT NOT NULL,
  food_id TEXT,
  name TEXT NOT NULL,
  emoji TEXT NOT NULL,
  amount REAL NOT NULL,
  unit TEXT NOT NULL,
  kcal REAL NOT NULL,
  carb REAL,
  prot REAL,
  fat REAL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  seq INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, id)
);
CREATE INDEX meals_user_seq ON meals(user_id, seq);
CREATE INDEX meals_user_epoch ON meals(user_id, epoch_day);

CREATE TABLE counters (
  name TEXT PRIMARY KEY,
  value INTEGER NOT NULL
);
-- The first sync reserves a block and hands out seq 1. Starting at zero would
-- make the first client row unreachable by every `seq > since` query.
INSERT INTO counters (name, value) VALUES ('seq', 1);

CREATE TABLE rate (
  user_id TEXT NOT NULL,
  minute INTEGER NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, minute)
);
CREATE INDEX rate_minute ON rate(minute);
