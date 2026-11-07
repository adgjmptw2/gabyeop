import { describe, expect, it } from "vitest";
import { signAccessToken, verifyAccessToken } from "../src/jwt";

const secret = "test-secret-that-is-long-enough";

describe("access tokens", () => {
  it("signs and verifies HS256 claims", async () => {
    const token = await signAccessToken("google-sub-123", secret, 1_000, 900);
    const claims = await verifyAccessToken(token, secret, 1_500);
    expect(claims).toEqual({ sub: "google-sub-123", iat: 1_000, exp: 1_900 });
  });

  it("rejects an expired token at the exact expiry second", async () => {
    const token = await signAccessToken("user", secret, 1_000, 60);
    expect(await verifyAccessToken(token, secret, 1_059)).not.toBeNull();
    expect(await verifyAccessToken(token, secret, 1_060)).toBeNull();
  });

  it("rejects a token signed with another secret", async () => {
    const token = await signAccessToken("user", secret, 1_000, 60);
    expect(await verifyAccessToken(token, "another-secret", 1_010)).toBeNull();
  });

  it("rejects tampered payloads and malformed tokens", async () => {
    const token = await signAccessToken("user", secret, 1_000, 60);
    const [header, , signature] = token.split(".");
    const altered = `${header}.${btoa(JSON.stringify({ sub: "attacker", iat: 1_000, exp: 2_000 })).replaceAll("=", "")}.${signature}`;
    expect(await verifyAccessToken(altered, secret, 1_010)).toBeNull();
    expect(await verifyAccessToken("not-a-token", secret, 1_010)).toBeNull();
    expect(await verifyAccessToken(`${token}.extra`, secret, 1_010)).toBeNull();
  });

  it("rejects an empty signing secret", async () => {
    await expect(signAccessToken("user", "", 1_000)).rejects.toThrow("JWT_SECRET");
  });
});
