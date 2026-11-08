import { deleteAccount, allowSync, cleanupRate, minutesSinceEpoch, upsertUser } from "./account";
import { bearerToken, error, HttpError, json, noContent, readJsonObject } from "./http";
import { verifyGoogleIdToken } from "./google";
import { verifyAccessToken } from "./jwt";
import { deletionPage, healthPage, homePage, privacyPage } from "./pages";
import { syncUser } from "./sync";
import { D1TokenStore, issueSession, logoutSession, rotateSession, systemClock } from "./tokens";
import type { Env } from "./types";
import { parseSyncRequest } from "./validate";

const MAX_BODY_BYTES = 1_000_000;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await route(request, env);
    } catch (failure) {
      if (failure instanceof HttpError) return error(failure.status, failure.code);
      console.error(failure instanceof Error ? failure.message : "unhandled request failure");
      return error(500, "internal_error");
    }
  },
} satisfies ExportedHandler<Env>;

async function route(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === "GET" && path === "/") return homePage();
  if (request.method === "GET" && path === "/health") return healthPage();
  if (request.method === "GET" && path === "/privacy") {
    return privacyPage(env.CONTACT_EMAIL);
  }
  if (request.method === "GET" && path === "/account-deletion") {
    return deletionPage(env.CONTACT_EMAIL);
  }

  if (request.method === "POST" && path === "/v1/auth/google") return googleAuth(request, env);
  if (request.method === "POST" && path === "/v1/auth/refresh") return refresh(request, env);
  if (request.method === "POST" && path === "/v1/auth/logout") return logout(request, env);
  if (request.method === "DELETE" && path === "/v1/account") return removeAccount(request, env);
  if (request.method === "POST" && path === "/v1/sync") return sync(request, env);

  if (knownPath(path)) return error(405, "method_not_allowed");
  return error(404, "not_found");
}

function knownPath(path: string): boolean {
  return path === "/" || path === "/health" || path === "/privacy" || path === "/account-deletion" ||
    path === "/v1/auth/google" || path === "/v1/auth/refresh" || path === "/v1/auth/logout" ||
    path === "/v1/account" || path === "/v1/sync";
}

async function googleAuth(request: Request, env: Env): Promise<Response> {
  const body = await readJsonObject(request, MAX_BODY_BYTES);
  const idToken = body.idToken;
  if (typeof idToken !== "string" || idToken.length === 0 || idToken.length > 16_384) {
    throw new HttpError(400, "invalid_id_token");
  }

  let identity;
  try {
    identity = await verifyGoogleIdToken(idToken, env.GOOGLE_WEB_CLIENT_ID);
  } catch {
    throw new HttpError(401, "invalid_google_token");
  }

  const now = systemClock.nowSeconds();
  await upsertUser(env.DB, identity, now);
  const session = await issueSession(new D1TokenStore(env.DB), identity.sub, env.JWT_SECRET, systemClock);
  return json({
    accessToken: session.accessToken,
    refreshToken: session.refreshToken,
    user: { id: identity.sub, email: identity.email },
  });
}

async function refresh(request: Request, env: Env): Promise<Response> {
  const body = await readJsonObject(request, MAX_BODY_BYTES);
  const token = validRefreshToken(body.refreshToken);
  const session = await rotateSession(new D1TokenStore(env.DB), token, env.JWT_SECRET, systemClock);
  return json({ accessToken: session.accessToken, refreshToken: session.refreshToken });
}

async function logout(request: Request, env: Env): Promise<Response> {
  const body = await readJsonObject(request, MAX_BODY_BYTES);
  const token = validRefreshToken(body.refreshToken);
  await logoutSession(new D1TokenStore(env.DB), token, systemClock);
  return noContent();
}

async function removeAccount(request: Request, env: Env): Promise<Response> {
  const userId = await authenticate(request, env);
  await deleteAccount(env.DB, userId);
  return noContent();
}

async function sync(request: Request, env: Env): Promise<Response> {
  const userId = await authenticate(request, env);
  const now = systemClock.nowSeconds();
  const minute = minutesSinceEpoch(now);
  if (!(await allowSync(env.DB, userId, minute))) throw new HttpError(429, "rate_limited");

  const body = await readJsonObject(request, MAX_BODY_BYTES);
  const parsed = parseSyncRequest(body);
  const result = await syncUser(env.DB, userId, parsed);

  // Cleanup is best-effort: a stale counter row must never fail a good sync.
  try {
    await cleanupRate(env.DB, minute);
  } catch {
    // Ignore cleanup failures.
  }
  return json(result);
}

async function authenticate(request: Request, env: Env): Promise<string> {
  const token = bearerToken(request);
  if (token === null) throw new HttpError(401, "missing_access_token");
  if (env.JWT_SECRET.length === 0) throw new HttpError(500, "server_misconfigured");
  const claims = await verifyAccessToken(token, env.JWT_SECRET, systemClock.nowSeconds());
  if (claims === null) throw new HttpError(401, "invalid_access_token");
  return claims.sub;
}

function validRefreshToken(value: unknown): string {
  if (typeof value !== "string" || value.length === 0 || value.length > 512) {
    throw new HttpError(400, "invalid_refresh_token");
  }
  return value;
}
