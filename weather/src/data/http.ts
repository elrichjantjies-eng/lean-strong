import { Capacitor, CapacitorHttp } from "@capacitor/core";

export class HttpError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/** GET JSON with a timeout. */
export async function getJson<T>(url: string, timeoutMs = 15000): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new HttpError(`${res.status} ${res.statusText}`, res.status);
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * GET from a server that doesn't allow browsers to read its responses (no CORS headers), such
 * as weathersa.co.za. Inside the Android app the request goes through the native HTTP stack,
 * which isn't subject to CORS. Returns the body as text (SAWS answers some requests in plain
 * text, e.g. "No warnings found").
 */
export async function getNativeText(
  url: string,
  timeoutMs = 20000,
): Promise<{ status: number; body: string }> {
  if (!Capacitor.isNativePlatform())
    throw new HttpError("Native HTTP is only available in the app", 0);
  const res = await CapacitorHttp.get({
    url,
    responseType: "text",
    connectTimeout: timeoutMs,
    readTimeout: timeoutMs,
    headers: { Accept: "application/json" },
  });
  const body = typeof res.data === "string" ? res.data : JSON.stringify(res.data);
  return { status: res.status, body };
}

export const canReadSawsLive = () => Capacitor.isNativePlatform();
