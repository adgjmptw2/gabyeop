const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

export function bytesToBase64(bytes: Uint8Array): string {
  let output = "";
  for (let index = 0; index < bytes.length; index += 3) {
    const a = bytes[index] ?? 0;
    const b = bytes[index + 1] ?? 0;
    const c = bytes[index + 2] ?? 0;
    const value = (a << 16) | (b << 8) | c;
    output += ALPHABET.charAt((value >> 18) & 63);
    output += ALPHABET.charAt((value >> 12) & 63);
    output += index + 1 < bytes.length ? ALPHABET.charAt((value >> 6) & 63) : "=";
    output += index + 2 < bytes.length ? ALPHABET.charAt(value & 63) : "=";
  }
  return output;
}

export function base64ToBytes(value: string): Uint8Array {
  const clean = value.replace(/=+$/u, "");
  if (clean.length % 4 === 1) throw new Error("invalid base64");
  const output = new Uint8Array(Math.floor((clean.length * 6) / 8));
  let buffer = 0;
  let bits = 0;
  let offset = 0;
  for (const character of clean) {
    const digit = ALPHABET.indexOf(character);
    if (digit < 0) throw new Error("invalid base64");
    buffer = (buffer << 6) | digit;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      output[offset] = (buffer >> bits) & 0xff;
      offset += 1;
    }
  }
  return output;
}

export function bytesToBase64Url(bytes: Uint8Array): string {
  return bytesToBase64(bytes).replace(/\+/gu, "-").replace(/\//gu, "_").replace(/=+$/u, "");
}

export function base64UrlToBytes(value: string): Uint8Array {
  return base64ToBytes(value.replace(/-/gu, "+").replace(/_/gu, "/"));
}

export function textToBase64Url(value: string): string {
  return bytesToBase64Url(new TextEncoder().encode(value));
}

export function base64UrlToText(value: string): string {
  return new TextDecoder().decode(base64UrlToBytes(value));
}

export function utf8Length(value: string): number {
  return new TextEncoder().encode(value).byteLength;
}
