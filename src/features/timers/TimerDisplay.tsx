import { displaySeconds, elapsedMs, formatClock, remainingMs, type TimerState } from "./engine";

/** Big, arm's-length digits. Countdowns show time left; stopwatches show time elapsed. */
export function TimerDisplay({
  timer,
  now,
  size = "xl",
  label,
}: {
  timer: TimerState | null;
  now: number;
  size?: "xl" | "lg";
  label?: string;
}) {
  const seconds = !timer
    ? 0
    : timer.durationMs === null
      ? Math.floor(elapsedMs(timer, now) / 1000)
      : displaySeconds(remainingMs(timer, now));
  return (
    <div className="text-center" role="timer" aria-live="off" aria-label={label}>
      <span
        className={`font-bold text-ink tabular-nums ${size === "xl" ? "text-[6rem] leading-none" : "text-6xl"}`}
      >
        {formatClock(seconds)}
      </span>
    </div>
  );
}

export function TimerRing({
  fraction,
  color,
  children,
}: {
  fraction: number;
  color: string;
  children: React.ReactNode;
}) {
  const r = 46;
  const c = 2 * Math.PI * r;
  return (
    <div className="relative mx-auto aspect-square w-full max-w-[18rem]">
      <svg viewBox="0 0 100 100" className="absolute inset-0 -rotate-90" aria-hidden="true">
        <circle cx="50" cy="50" r={r} fill="none" stroke="var(--app-surface-2)" strokeWidth="6" />
        <circle
          cx="50"
          cy="50"
          r={r}
          fill="none"
          stroke={color}
          strokeWidth="6"
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c * (1 - Math.max(0, Math.min(1, fraction)))}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">{children}</div>
    </div>
  );
}
