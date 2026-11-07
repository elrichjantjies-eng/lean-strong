import { useEffect, useState } from "react";
import { nowSec } from "./time";

/** The current time in Unix seconds, updated every `everyMs` (and when the app comes back). */
export function useNow(everyMs = 60_000): number {
  const [now, setNow] = useState(nowSec);
  useEffect(() => {
    const tick = () => setNow(nowSec());
    const t = setInterval(tick, everyMs);
    const onVisible = () => document.visibilityState === "visible" && tick();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearInterval(t);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [everyMs]);
  return now;
}
