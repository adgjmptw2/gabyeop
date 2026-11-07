import { base64UrlToBytes, base64UrlToText } from "./base64";

const JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const ISSUERS = new Set(["accounts.google.com", "https://accounts.google.com"]);
const encoder = new TextEncoder();

interface Jwk {
  kid: string;
  kty: string;
  n: string;
  e: string;
  alg?: string;
  use?: string;
}

export interface GoogleIdentity {
  sub: string;
  email: string;
}

let cachedKeys: Jwk[] = [];
let cacheExpiresAt = 0;
let cacheGeneration = 0;
let pendingRefresh: Promise<Jwk[]> | null = null;

export async function verifyGoogleIdToken(
  token: string,
  clientId: string,
  nowMs = Date.now(),
): Promise<GoogleIdentity> {
  if (clientId.length === 0) throw new Error("GOOGLE_WEB_CLIENT_ID is not configured");
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("invalid Google token");
  const headerPart = parts[0];
  const payloadPart = parts[1];
  const signaturePart = parts[2];
  if (headerPart === undefined || payloadPart === undefined || signaturePart === undefined) {
    throw new Error("invalid Google token");
  }

  const header = parseObject(base64UrlToText(headerPart));
  if (header.alg !== "RS256" || typeof header.kid !== "string") {
    throw new Error("invalid Google token header");
  }
  const claims = parseObject(base64UrlToText(payloadPart));
  validateClaims(claims, clientId, nowMs);

  const key = await findKey(header.kid, nowMs);
  if (key === null) throw new Error("unknown Google signing key");
  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    { kty: key.kty, n: key.n, e: key.e, alg: "RS256", ext: true },
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    base64UrlToBytes(signaturePart),
    encoder.encode(`${headerPart}.${payloadPart}`),
  );
  if (!valid) throw new Error("invalid Google token signature");
  return { sub: String(claims.sub), email: String(claims.email) };
}

function validateClaims(claims: Record<string, unknown>, clientId: string, nowMs: number): void {
  if (typeof claims.iss !== "string" || !ISSUERS.has(claims.iss)) throw new Error("invalid issuer");
  if (claims.aud !== clientId) throw new Error("invalid audience");
  if (typeof claims.exp !== "number" || claims.exp * 1000 <= nowMs) throw new Error("expired Google token");
  if (claims.email_verified !== true && claims.email_verified !== "true") {
    throw new Error("unverified email");
  }
  if (typeof claims.sub !== "string" || claims.sub.length === 0) throw new Error("missing subject");
  if (typeof claims.email !== "string" || claims.email.length === 0) throw new Error("missing email");
}

async function findKey(kid: string, nowMs: number): Promise<Jwk | null> {
  if (nowMs >= cacheExpiresAt) await refreshKeys();
  const hit = cachedKeys.find((key) => key.kid === kid);
  if (hit !== undefined) return hit;
  await refreshKeys(true);
  return cachedKeys.find((key) => key.kid === kid) ?? null;
}

async function refreshKeys(force = false): Promise<Jwk[]> {
  if (!force && pendingRefresh !== null) return pendingRefresh;
  if (pendingRefresh !== null) return pendingRefresh;
  const generation = cacheGeneration;
  pendingRefresh = fetch(JWKS_URL, { headers: { accept: "application/json" } })
    .then(async (response) => {
      if (!response.ok) throw new Error(`Google JWKS ${response.status}`);
      const maxAge = cacheSeconds(response.headers.get("cache-control"));
      const keys = parseKeys(await response.json());
      if (generation === cacheGeneration) {
        cachedKeys = keys;
        cacheExpiresAt = Date.now() + maxAge * 1000;
        cacheGeneration += 1;
      }
      return keys;
    })
    .finally(() => {
      pendingRefresh = null;
    });
  return pendingRefresh;
}

function parseKeys(body: unknown): Jwk[] {
  if (typeof body !== "object" || body === null) throw new Error("invalid JWKS");
  const keys = (body as { keys?: unknown }).keys;
  if (!Array.isArray(keys)) throw new Error("invalid JWKS");
  const output: Jwk[] = [];
  for (const item of keys) {
    if (typeof item !== "object" || item === null) continue;
    const key = item as Record<string, unknown>;
    if (
      typeof key.kid !== "string" ||
      key.kty !== "RSA" ||
      typeof key.n !== "string" ||
      typeof key.e !== "string"
    ) {
      continue;
    }
    const parsed: Jwk = { kid: key.kid, kty: key.kty, n: key.n, e: key.e };
    if (typeof key.alg === "string") parsed.alg = key.alg;
    if (typeof key.use === "string") parsed.use = key.use;
    output.push(parsed);
  }
  return output;
}

function cacheSeconds(cacheControl: string | null): number {
  if (cacheControl === null) return 300;
  const match = /(?:^|,)\s*max-age=(\d+)/iu.exec(cacheControl);
  if (match?.[1] === undefined) return 300;
  const value = Number(match[1]);
  return Number.isFinite(value) && value > 0 ? Math.min(value, 86_400) : 300;
}

function parseObject(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("invalid Google token payload");
  }
  return parsed as Record<string, unknown>;
}
