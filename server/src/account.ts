import { resultRows, runBatch } from "./d1";

export async function deleteAccount(db: D1Database, userId: string): Promise<void> {
  await runBatch(db, [
    db.prepare("DELETE FROM refresh_tokens WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM refresh_families WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM profiles WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM weights WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM meals WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM notes WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM rate WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM users WHERE id = ?").bind(userId),
  ]);
}

export interface GoogleAccount {
  sub: string;
  email: string;
}

export async function upsertUser(db: D1Database, account: GoogleAccount, now: number): Promise<void> {
  await db
    .prepare(
      `INSERT INTO users (id, email, created_at) VALUES (?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET email = excluded.email`,
    )
    .bind(account.sub, account.email, now)
    .run();
}

export function minutesSinceEpoch(nowSeconds: number): number {
  return Math.floor(nowSeconds / 60);
}

export async function allowSync(db: D1Database, userId: string, minute: number, limit = 30): Promise<boolean> {
  const rows = await db
    .prepare(
      `INSERT INTO rate (user_id, minute, count) VALUES (?, ?, 1)
       ON CONFLICT(user_id, minute) DO UPDATE SET count = count + 1
       RETURNING count`,
    )
    .bind(userId, minute)
    .all<{ count: number }>();
  const count = resultRows<{ count: number }>(rows)[0]?.count ?? 1;
  return count <= limit;
}

export async function cleanupRate(db: D1Database, minute: number): Promise<void> {
  await db.prepare("DELETE FROM rate WHERE minute < ?").bind(minute - 2).run();
}

