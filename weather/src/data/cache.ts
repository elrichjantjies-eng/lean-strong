/**
 * A small stale-while-revalidate cache shared by every component: data comes back instantly
 * from memory or localStorage (so the app opens offline with the last forecast), and is
 * refreshed in the background when older than its max age.
 */
import { useCallback, useEffect, useSyncExternalStore } from "react";
import { load, save } from "@/lib/storage";

export type Entry<T> = { data?: T; at: number; error?: string; loading: boolean };

const PREFIX = "skyglass:cache:";
const mem = new Map<string, Entry<unknown>>();
const inflight = new Map<string, Promise<void>>();
const listeners = new Map<string, Set<() => void>>();
const EMPTY: Entry<never> = { at: 0, loading: false };

function get<T>(key: string, persist: boolean): Entry<T> {
  let e = mem.get(key) as Entry<T> | undefined;
  if (!e) {
    const stored = persist ? load<{ data: T; at: number } | null>(PREFIX + key, null) : null;
    e = stored ? { data: stored.data, at: stored.at, loading: false } : (EMPTY as Entry<T>);
    mem.set(key, e);
  }
  return e;
}

function set<T>(key: string, e: Entry<T>, persist: boolean) {
  mem.set(key, e);
  if (persist && e.data !== undefined && !e.error) save(PREFIX + key, { data: e.data, at: e.at });
  listeners.get(key)?.forEach((l) => l());
}

export function revalidate<T>(
  key: string,
  fetcher: () => Promise<T>,
  persist = true,
): Promise<void> {
  const running = inflight.get(key);
  if (running) return running;
  const prev = get<T>(key, persist);
  set(key, { ...prev, loading: true }, false);
  const p = fetcher()
    .then((data) => set(key, { data, at: Date.now(), loading: false }, persist))
    .catch((err: unknown) => {
      set(
        key,
        { ...get<T>(key, persist), loading: false, error: String((err as Error)?.message ?? err) },
        false,
      );
    })
    .finally(() => inflight.delete(key));
  inflight.set(key, p);
  return p;
}

export function peek<T>(key: string): T | undefined {
  return get<T>(key, true).data;
}

export function useCached<T>(
  key: string | null,
  fetcher: () => Promise<T>,
  maxAgeMs: number,
  persist = true,
): Entry<T> & { refresh: () => Promise<void> } {
  const subscribe = useCallback(
    (cb: () => void) => {
      if (!key) return () => {};
      if (!listeners.has(key)) listeners.set(key, new Set());
      listeners.get(key)!.add(cb);
      return () => listeners.get(key)?.delete(cb);
    },
    [key],
  );
  const entry = useSyncExternalStore(subscribe, () =>
    key ? get<T>(key, persist) : (EMPTY as Entry<T>),
  );

  useEffect(() => {
    if (!key) return;
    const e = get<T>(key, persist);
    if (!e.loading && Date.now() - e.at > maxAgeMs) void revalidate(key, fetcher, persist);
    // The fetcher is recreated every render; the key identifies the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, maxAgeMs, persist]);

  const refresh = useCallback(
    () => (key ? revalidate(key, fetcher, persist) : Promise.resolve()),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [key, persist],
  );
  return { ...entry, refresh };
}
