import { base64UrlToBytes, base64UrlToText, bytesToBase64Url, textToBase64Url } from "./base64";

const encoder = new TextEncoder();

export interface AccessClaims {
  sub: string;
  iat: number;
  exp: number;
}

export async function signAccessToken(
  userId: string,
  secret: string,
  nowSeconds: number,
  lifetimeSeconds = 15 * 60,
): Promise<string> {
  if (secret.length === 0) throw new Error("JWT_SECRET is not configured");
  const header = textToBase64Url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = textToBase64Url(
    JSON.stringify({ sub: userId, iat: nowSeconds, exp: nowSeconds + lifetimeSeconds }),
  );
  const input = `${header}.${payload}`;
  const signature = await hmac(secret, input);
  return `${input}.${bytesToBase64Url(signature)}`;
}

export async function verifyAccessToken(
  token: string,
  secret: string,
  nowSeconds: number,
): Promise<AccessClaims | null> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const headerPart = parts[0];
    const payloadPart = parts[1];
    const signaturePart = parts[2];
    if (headerPart === undefined || payloadPart === undefined || signaturePart === undefined) return null;
    const header = parseObject(base64UrlToText(headerPart));
    if (header.alg !== "HS256") return null;
    const expected = await hmac(secret, `${headerPart}.${payloadPart}`);
    const actual = base64UrlToBytes(signaturePart);
    if (!equalBytes(expected, actual)) return null;
    const payload = parseObject(base64UrlToText(payloadPart));
    if (
      typeof payload.sub !== "string" ||
      payload.sub.length === 0 ||
      typeof payload.iat !== "number" ||
      typeof payload.exp !== "number" ||
      !Number.isFinite(payload.iat) ||
      !Number.isFinite(payload.exp) ||
      payload.exp <= nowSeconds
    ) {
      return null;
    }
    return { sub: payload.sub, iat: payload.iat, exp: payload.exp };
  } catch {
    return null;
  }
}

async function hmac(secret: string, data: string): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, encoder.encode(data)));
}

function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= (left[index] ?? 0) ^ (right[index] ?? 0);
  }
  return difference === 0;
}

function parseObject(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("invalid JWT segment");
  }
  return parsed as Record<string, unknown>;
}
