import { bytesToBase64Url } from "./base64";
import { HttpError } from "./http";
import { signAccessToken } from "./jwt";
import { changesOf, runBatch } from "./d1";

export const REFRESH_LIFETIME_SECONDS = 60 * 24 * 60 * 60;
export const ACCESS_LIFETIME_SECONDS = 15 * 60;

export interface RefreshRecord {
  hash: string;
  userId: string;
  familyId: string;
  expiresAt: number;
  usedAt: number | null;
  revokedAt: number | null;
}

export interface TokenStore {
  find(hash: string): Promise<RefreshRecord | null>;
  /** Persist a new family and its first token atomically. */
  create(familyId: string, userId: string, hash: string, now: number, expiresAt: number): Promise<void>;
  /** Mark the token used and insert its successor in one batch. */
  rotate(oldHash: string, newHash: string, userId: string, familyId: string, now: number, expiresAt: number): Promise<void>;
  revokeFamily(familyId: string, now: number): Promise<void>;
  revokeAll(userId: string, now: number): Promise<void>;
  deleteToken(hash: string): Promise<void>;
}

export interface IssuedTokens {
  accessToken: string;
  refreshToken: string;
}

export interface Clock {
  nowSeconds(): number;
}

export const systemClock: Clock = {
  nowSeconds: () => Math.floor(Date.now() / 1000),
};

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function randomRefreshToken(): string {
  return bytesToBase64Url(crypto.getRandomValues(new Uint8Array(32)));
}

function randomFamilyId(): string {
  return bytesToBase64Url(crypto.getRandomValues(new Uint8Array(16)));
}

export interface IssuedSession extends IssuedTokens {
  familyId: string;
  refreshHash: string;
}

export async function issueSession(
  store: TokenStore,
  userId: string,
  secret: string,
  clock: Clock,
): Promise<IssuedSession> {
  const now = clock.nowSeconds();
  const familyId = randomFamilyId();
  const refreshToken = randomRefreshToken();
  const refreshHash = await sha256Hex(refreshToken);
  await store.create(familyId, userId, refreshHash, now, now + REFRESH_LIFETIME_SECONDS);
  const accessToken = await signAccessToken(userId, secret, now, ACCESS_LIFETIME_SECONDS);
  return { accessToken, refreshToken, familyId, refreshHash };
}

export interface RefreshResult extends IssuedTokens {
  userId: string;
  familyId: string;
}

/**
 * Rotate a refresh token. A replayed token revokes its whole family, so a stolen
 * token and the legitimate holder cannot both keep refreshing indefinitely.
 */
export async function rotateSession(
  store: TokenStore,
  presented: string,
  secret: string,
  clock: Clock,
): Promise<RefreshResult> {
  const now = clock.nowSeconds();
  const hash = await sha256Hex(presented);
  const record = await store.find(hash);
  if (record === null) throw new HttpError(401, "invalid_refresh_token");

  if (record.revokedAt !== null) throw new HttpError(401, "refresh_token_revoked");
  if (record.usedAt !== null) {
    await store.revokeFamily(record.familyId, now);
    throw new HttpError(401, "refresh_token_reused");
  }
  if (record.expiresAt <= now) {
    await store.deleteToken(hash);
    throw new HttpError(401, "refresh_token_expired");
  }

  const refreshToken = randomRefreshToken();
  const nextHash = await sha256Hex(refreshToken);
  await store.rotate(hash, nextHash, record.userId, record.familyId, now, now + REFRESH_LIFETIME_SECONDS);
  const accessToken = await signAccessToken(record.userId, secret, now, ACCESS_LIFETIME_SECONDS);
  return { accessToken, refreshToken, userId: record.userId, familyId: record.familyId };
}

export async function logoutSession(store: TokenStore, presented: string, clock: Clock): Promise<void> {
  const hash = await sha256Hex(presented);
  const record = await store.find(hash);
  if (record !== null) await store.revokeFamily(record.familyId, clock.nowSeconds());
}

export class D1TokenStore implements TokenStore {
  constructor(private readonly db: D1Database) {}

  async find(hash: string): Promise<RefreshRecord | null> {
    const row = await this.db
      .prepare(
        `SELECT t.hash, t.user_id, t.family_id, t.expires_at, t.used_at, f.revoked_at
         FROM refresh_tokens t JOIN refresh_families f ON f.id = t.family_id
         WHERE t.hash = ?`,
      )
      .bind(hash)
      .first<{
        hash: string;
        user_id: string;
        family_id: string;
        expires_at: number;
        used_at: number | null;
        revoked_at: number | null;
      }>();
    if (row === null) return null;
    return {
      hash: row.hash,
      userId: row.user_id,
      familyId: row.family_id,
      expiresAt: row.expires_at,
      usedAt: row.used_at,
      revokedAt: row.revoked_at,
    };
  }

  async create(familyId: string, userId: string, hash: string, now: number, expiresAt: number): Promise<void> {
    await runBatch(this.db, [
      this.db.prepare("INSERT INTO refresh_families (id, user_id, created_at) VALUES (?, ?, ?)").bind(familyId, userId, now),
      this.db
        .prepare("INSERT INTO refresh_tokens (hash, user_id, family_id, expires_at, created_at) VALUES (?, ?, ?, ?, ?)")
        .bind(hash, userId, familyId, expiresAt, now),
    ]);
  }

  async rotate(
    oldHash: string,
    newHash: string,
    userId: string,
    familyId: string,
    now: number,
    expiresAt: number,
  ): Promise<void> {
    const results = await runBatch(this.db, [
      this.db
        .prepare("UPDATE refresh_tokens SET used_at = ? WHERE hash = ? AND used_at IS NULL")
        .bind(now, oldHash),
      this.db
        .prepare(
          `INSERT INTO refresh_tokens (hash, user_id, family_id, expires_at, created_at)
           SELECT ?, ?, ?, ?, ? WHERE changes() = 1`,
        )
        .bind(newHash, userId, familyId, expiresAt, now),
    ]);
    const updated = changesOf(results[0]);
    const inserted = changesOf(results[1]);
    if (updated === 0 || inserted === 0) throw new HttpError(401, "refresh_token_reused");
  }

  async revokeFamily(familyId: string, now: number): Promise<void> {
    await runBatch(this.db, [
      this.db.prepare("UPDATE refresh_families SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").bind(now, familyId),
      this.db.prepare("DELETE FROM refresh_tokens WHERE family_id = ?").bind(familyId),
    ]);
  }

  async revokeAll(userId: string, now: number): Promise<void> {
    await runBatch(this.db, [
      this.db.prepare("UPDATE refresh_families SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL").bind(now, userId),
      this.db.prepare("DELETE FROM refresh_tokens WHERE user_id = ?").bind(userId),
    ]);
  }

  async deleteToken(hash: string): Promise<void> {
    await this.db.prepare("DELETE FROM refresh_tokens WHERE hash = ?").bind(hash).run();
  }
}
