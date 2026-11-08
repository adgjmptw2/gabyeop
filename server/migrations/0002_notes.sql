CREATE TABLE notes (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  epoch_day INTEGER NOT NULL,
  memo TEXT NOT NULL,
  note TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  seq INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, epoch_day)
);
CREATE INDEX notes_user_seq ON notes(user_id, seq);
