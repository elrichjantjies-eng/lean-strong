import { useEffect, useRef, useState } from "react";
import { useSetting } from "@/db/settings";
import { beep } from "@/platform/audio";
import { haptic } from "@/platform";
import { displaySeconds, isRunning, remainingMs, type TimerState } from "./engine";

/** Current time, refreshed several times a second while `active`, and on return to the app. */
export function useNow(active = true, everyMs = 200): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const tick = () => setNow(Date.now());
    const id = window.setInterval(tick, everyMs);
    document.addEventListener("visibilitychange", tick);
    window.addEventListener("focus", tick);
    return () => {
      window.clearInterval(id);
      document.removeEventListener("visibilitychange", tick);
      window.removeEventListener("focus", tick);
    };
  }, [active, everyMs]);
  return now;
}

/**
 * Beep and buzz at 3-2-1-0 on a running countdown and call `onDone` once when it reaches zero.
 * Cues only fire for seconds we actually see tick by: coming back to an already-finished timer
 * calls `onDone` without a burst of beeps.
 */
export function useCountdownCues(
  timer: TimerState | null,
  now: number,
  onDone?: () => void,
  cues = true,
) {
  const sound = useSetting("sound") ?? true;
  const haptics = useSetting("haptics") ?? true;
  const last = useRef<number | null>(null);
  const fired = useRef(false);
  const doneRef = useRef(onDone);
  useEffect(() => {
    doneRef.current = onDone;
  });

  const running = !!timer && isRunning(timer) && timer.durationMs !== null;
  const secs = timer && timer.durationMs !== null ? displaySeconds(remainingMs(timer, now)) : null;

  useEffect(() => {
    if (!running || secs === null) {
      last.current = null;
      fired.current = false;
      return;
    }
    const prev = last.current;
    last.current = secs;
    if (prev === null) {
      if (secs === 0 && !fired.current) {
        fired.current = true;
        doneRef.current?.();
      }
      return;
    }
    if (secs === prev) return;
    if (cues && secs <= 3 && secs >= 1 && prev - secs === 1) {
      if (sound) beep("tick");
      if (haptics) void haptic("tick");
    }
    if (secs === 0 && !fired.current) {
      fired.current = true;
      if (cues && prev <= 3) {
        if (sound) beep("go");
        if (haptics) void haptic("done");
      }
      doneRef.current?.();
    }
  }, [running, secs, cues, sound, haptics]);
}
