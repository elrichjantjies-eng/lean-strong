import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useActive, useToday } from "@/app/active";
import { logWalk } from "@/db/repo";
import { weekdayOf } from "@/domain/dates";
import { programWeek } from "@/domain/schedule";
import { walkForDay } from "@/domain/session";
import { keepAwake } from "@/platform";
import { unlockAudio } from "@/platform/audio";
import { Icon } from "@/components/Icon";
import { Callout } from "@/components/ui";
import type { TimerState } from "@/features/timers/engine";
import { StopwatchRunner } from "@/features/timers/runners";
import { walkIntervalsText } from "@/features/today/labels";
import { WalkLogSheet } from "./FlowSteps";

/** Walk-day timer from Today. */
export function WalkScreen() {
  const { profile, program } = useActive();
  const today = useToday();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [timer, setTimer] = useState<TimerState | null>(null);
  const [finishing, setFinishing] = useState<number | null>(null);
  const week = Math.min(program.weeks, Math.max(1, programWeek(profile.startDate, today) ?? 1));
  const day = program.weekTemplate.find((d) => d.weekday === weekdayOf(today))!;
  const walk = walkForDay(program, day, week);
  const target =
    walk?.minutes ??
    ([Number(params.get("min")) || 30, Number(params.get("min")) || 30] as [number, number]);
  const intervals = walk ? walkIntervalsText(walk) : null;

  useEffect(() => {
    void keepAwake(true);
    return () => void keepAwake(false);
  }, []);

  return (
    <div className="min-h-dvh bg-bg" style={{ paddingTop: "var(--safe-top)" }}>
      <header className="mx-auto flex h-14 max-w-xl items-center px-2">
        <button
          type="button"
          aria-label="Close"
          onClick={() => navigate(-1)}
          className="flex size-12 items-center justify-center rounded-full text-ink active:bg-surface-2"
        >
          <Icon name="close" />
        </button>
        <p className="flex-1 text-center font-semibold text-ink">{day.title}</p>
        <span className="w-12" />
      </header>
      <main className="mx-auto max-w-xl space-y-4 px-4 pb-8" onPointerDown={unlockAudio}>
        <p className="text-center text-muted">{day.subtitle}</p>
        {intervals && (
          <Callout title="Walking intervals">
            <p>{intervals}</p>
          </Callout>
        )}
        <StopwatchRunner
          timer={timer}
          setTimer={setTimer}
          targetMin={target}
          label="Walk"
          color={program.theme.dayColors.walk}
          onFinish={(m) => setFinishing(m)}
        />
      </main>
      {finishing !== null && (
        <WalkLogSheet
          minutes={finishing || target[0]}
          onClose={() => setFinishing(null)}
          onSave={async (minutes, distanceKm) => {
            await logWalk(profile.id, today, minutes, distanceKm ? { distanceKm } : {});
            navigate("/", { replace: true });
          }}
        />
      )}
    </div>
  );
}
