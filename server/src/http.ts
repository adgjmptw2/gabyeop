export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(code);
    this.name = "HttpError";
  }
}

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

export function noContent(): Response {
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}

export function error(status: number, code: string): Response {
  return json({ error: code }, status);
}

export async function readJson(request: Request, limit = 1_000_000): Promise<unknown> {
  const declared = request.headers.get("content-length");
  if (declared !== null && Number(declared) > limit) {
    throw new HttpError(413, "payload_too_large");
  }
  const buffer = await request.arrayBuffer();
  if (buffer.byteLength > limit) throw new HttpError(413, "payload_too_large");
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(buffer));
  } catch {
    throw new HttpError(400, "invalid_json");
  }
}

export async function readJsonObject(request: Request, limit = 1_000_000): Promise<Record<string, unknown>> {
  const value = await readJson(request, limit);
  if (!isObject(value)) throw new HttpError(400, "invalid_body");
  return value;
}

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function requireString(value: unknown, code: string, maxLength = 16_384): string {
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new HttpError(400, code);
  }
  return value;
}

export function optionalString(value: unknown, code: string, maxLength: number): string | null {
  if (value === undefined || value === null) return null;
  return requireString(value, code, maxLength);
}

export function bearerToken(request: Request): string | null {
  const header = request.headers.get("authorization");
  if (header === null) return null;
  const match = /^Bearer\s+([^\s]+)$/iu.exec(header);
  return match?.[1] ?? null;
}
