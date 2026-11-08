CREATE TABLE notes (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  epoch_day INTEGER NOT NULL,
  memo TEXT NOT NULL,
  note TEXT NOT NULL,
  water_ml INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  seq INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, epoch_day)
);
CREATE INDEX notes_user_seq ON notes(user_id, seq);

ALTER TABLE weights ADD COLUMN body_fat_pct REAL;

CREATE TABLE fasts (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  start_at INTEGER NOT NULL,
  end_at INTEGER,
  target_hours INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  seq INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, id)
);
CREATE INDEX fasts_user_seq ON fasts(user_id, seq);
