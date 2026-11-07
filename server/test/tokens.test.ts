import { describe, expect, it } from "vitest";
import { HttpError } from "../src/http";
import {
  issueSession, logoutSession, rotateSession, systemClock,
  type Clock, type RefreshRecord, type TokenStore,
} from "../src/tokens";
import { verifyAccessToken } from "../src/jwt";

class MemoryTokenStore implements TokenStore {
  readonly records = new Map<string, RefreshRecord & { familyRevoked: boolean }>();
  private nextFamily = 1;
  private readonly familyUsers = new Map<string, string>();

  async find(hash: string): Promise<RefreshRecord | null> {
    const record = this.records.get(hash);
    if (record === undefined) return null;
    return { ...record, revokedAt: record.revokedAt ?? null };
  }

  async create(familyId: string, userId: string, hash: string, now: number, expiresAt: number): Promise<void> {
    this.familyUsers.set(familyId, userId);
    this.records.set(hash, { hash, userId, familyId, expiresAt, usedAt: null, revokedAt: null, familyRevoked: false });
  }

  async rotate(oldHash: string, newHash: string, userId: string, familyId: string, now: number, expiresAt: number): Promise<void> {
    const previous = this.records.get(oldHash);
    if (previous === undefined || previous.usedAt !== null || previous.revokedAt !== null) {
      throw new HttpError(401, "refresh_token_reused");
    }
    previous.usedAt = now;
    this.records.set(newHash, { hash: newHash, userId, familyId, expiresAt, usedAt: null, revokedAt: null, familyRevoked: false });
  }

  async revokeFamily(familyId: string, now: number): Promise<void> {
    for (const record of this.records.values()) {
      if (record.familyId === familyId) record.revokedAt = now;
    }
  }

  async revokeAll(userId: string, now: number): Promise<void> {
    for (const record of this.records.values()) {
      if (record.userId === userId) record.revokedAt = now;
    }
  }

  async deleteToken(hash: string): Promise<void> {
    this.records.delete(hash);
  }

  familyCount(): number { return this.familyUsers.size; }
}

function clockAt(seconds: number): Clock {
  return { nowSeconds: () => seconds };
}

describe("refresh token rotation", () => {
  const secret = "refresh-test-secret";

  it("issues an access token and a 32-byte base64url refresh token", async () => {
    const store = new MemoryTokenStore();
    const session = await issueSession(store, "user-1", secret, clockAt(1_000));
    expect(session.refreshToken).toMatch(/^[A-Za-z0-9_-]{43}$/u);
    expect(store.records.size).toBe(1);
    const stored = store.records.get(session.refreshHash);
    expect(stored?.expiresAt).toBe(1_000 + 60 * 24 * 60 * 60);
    const claims = await verifyAccessToken(session.accessToken, secret, 1_010);
    expect(claims?.sub).toBe("user-1");
  });

  it("rotates once and invalidates the old token", async () => {
    const store = new MemoryTokenStore();
    const first = await issueSession(store, "user-1", secret, clockAt(1_000));
    const second = await rotateSession(store, first.refreshToken, secret, clockAt(1_001));
    expect(second.refreshToken).not.toBe(first.refreshToken);
    await expect(rotateSession(store, first.refreshToken, secret, clockAt(1_002)))
      .rejects.toMatchObject({ code: "refresh_token_reused" });
  });

  it("revokes the whole family when an already-used token is replayed", async () => {
    const store = new MemoryTokenStore();
    const first = await issueSession(store, "user-1", secret, clockAt(1_000));
    const second = await rotateSession(store, first.refreshToken, secret, clockAt(1_001));
    await expect(rotateSession(store, first.refreshToken, secret, clockAt(1_002))).rejects.toThrow(HttpError);
    await expect(rotateSession(store, second.refreshToken, secret, clockAt(1_003)))
      .rejects.toMatchObject({ code: "refresh_token_revoked" });
  });

  it("rejects an unknown token and an expired token", async () => {
    const store = new MemoryTokenStore();
    await expect(rotateSession(store, "unknown", secret, clockAt(1_000)))
      .rejects.toMatchObject({ code: "invalid_refresh_token" });
    const session = await issueSession(store, "user-1", secret, clockAt(1_000));
    await expect(rotateSession(store, session.refreshToken, secret, clockAt(1_000 + 60 * 24 * 60 * 60)))
      .rejects.toMatchObject({ code: "refresh_token_expired" });
  });

  it("ends the family on logout", async () => {
    const store = new MemoryTokenStore();
    const session = await issueSession(store, "user-1", secret, clockAt(1_000));
    await logoutSession(store, session.refreshToken, clockAt(1_001));
    await expect(rotateSession(store, session.refreshToken, secret, clockAt(1_002)))
      .rejects.toMatchObject({ code: "refresh_token_revoked" });
  });

  it("keeps each login's family independent", async () => {
    const store = new MemoryTokenStore();
    const first = await issueSession(store, "user-1", secret, clockAt(1_000));
    const second = await issueSession(store, "user-1", secret, clockAt(1_000));
    expect(store.familyCount()).toBe(2);
    await rotateSession(store, first.refreshToken, secret, clockAt(1_001));
    const rotated = await rotateSession(store, second.refreshToken, secret, clockAt(1_001));
    expect(rotated.userId).toBe("user-1");
  });
});

describe("systemClock", () => {
  it("returns whole Unix seconds", () => {
    expect(Number.isInteger(systemClock.nowSeconds())).toBe(true);
  });
});
