import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import { db } from "@/db/db";
import { logWalk } from "@/db/repo";
import { daysBetween, weekdayName, weekdayOf } from "@/domain/dates";
import type { IsoDate } from "@/domain/model";
import {
  completedSessionIds,
  phaseForWeek,
  programPosition,
  programWeek,
  sessionOrder,
  weekStatuses,
  type WeekDay,
} from "@/domain/schedule";
import { walkForDay } from "@/domain/session";
import { Screen } from "@/components/Screen";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button } from "@/components/ui";
import { PhaseTable } from "@/features/guide/Sections";
import { dayColor } from "@/features/today/labels";
import { bannerColor } from "@/theme/theme";

export function PlanScreen() {
  const { profile, program } = useActive();
  const today = useToday();
  const [params, setParams] = useSearchParams();
  const selected = params.get("date");
  const pos = programPosition(program, profile.startDate, today);
  const data = useLiveQuery(async () => {
    const [logs, activities] = await Promise.all([
      db.sessionLogs.where("profileId").equals(profile.id).toArray(),
      db.activityLogs.where("profileId").equals(profile.id).toArray(),
    ]);
    return {
      logs,
      walkDates: new Set(activities.filter((a) => a.kind === "walk").map((a) => a.date)),
    };
  }, [profile.id]);

  const weeks = Array.from({ length: program.weeks }, (_, i) => i + 1);
  const grid = data
    ? weeks.map((w) =>
        weekStatuses(
          program,
          profile.startDate,
          w,
          today,
          completedSessionIds(data.logs, w),
          data.walkDates,
        ),
      )
    : [];
  const selectedDay = selected ? grid.flat().find((d) => d.date === selected) : undefined;

  return (
    <Screen title="Your plan" kicker={program.subtitle}>
      <section aria-labelledby="cal-heading" className="mb-6">
        <h2 id="cal-heading" className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">
          Calendar
        </h2>
        <div className="mb-1 grid grid-cols-[2.5rem_repeat(7,1fr)] gap-1 text-center text-xs font-semibold text-muted">
          <span />
          {grid[0]?.map((d) => (
            <span key={d.date}>{weekdayOf(d.date).slice(0, 2).toUpperCase()}</span>
          ))}
        </div>
        <ol className="space-y-1">
          {grid.map((days, wi) => {
            const week = wi + 1;
            const phase = phaseForWeek(program, week);
            const isNow = pos.status === "active" && pos.week === week;
            return (
              <li key={week} className="grid grid-cols-[2.5rem_repeat(7,1fr)] items-center gap-1">
                <span
                  className={`rounded-md py-1 text-center text-xs font-bold ${isNow ? "text-white" : "text-ink"}`}
                  style={{
                    background: isNow
                      ? bannerColor(program.theme.phaseColors[phase.number - 1]!)
                      : "transparent",
                    borderLeft: `4px solid ${program.theme.phaseColors[phase.number - 1]}`,
                  }}
                  aria-label={`Week ${week}, phase ${phase.number}`}
                >
                  W{week}
                </span>
                {days.map((d) => (
                  <DayCell
                    key={d.date}
                    day={d}
                    program={program}
                    today={today}
                    onClick={() => setParams({ date: d.date })}
                  />
                ))}
              </li>
            );
          })}
        </ol>
        <Legend />
      </section>

      <section aria-labelledby="sessions-heading" className="mb-6">
        <h2
          id="sessions-heading"
          className="mb-2 text-sm font-bold tracking-wide text-muted uppercase"
        >
          Sessions
        </h2>
        <div className="grid grid-cols-2 gap-2">
          {sessionOrder(program).map((id) => {
            const s = program.sessions[id]!;
            const color = program.theme.dayColors[id] ?? s.color;
            return (
              <Link
                key={id}
                to={`/plan/session/${id}`}
                className="rounded-2xl p-3 text-white shadow-sm"
                style={{ background: bannerColor(color) }}
              >
                <p className="text-xs font-bold tracking-wide uppercase">{s.weekdayLabel}</p>
                <p className="text-lg font-bold">{s.title}</p>
                <p className="text-sm font-medium">{s.focus}</p>
              </Link>
            );
          })}
        </div>
      </section>

      <section aria-labelledby="phases-heading">
        <h2
          id="phases-heading"
          className="mb-2 text-sm font-bold tracking-wide text-muted uppercase"
        >
          Phases
        </h2>
        <PhaseTable
          program={program}
          currentPhase={pos.status === "active" ? pos.phase.number : undefined}
        />
      </section>

      {selectedDay && (
        <DaySheet
          day={selectedDay}
          today={today}
          onClose={() => setParams({})}
          profileId={profile.id}
        />
      )}
    </Screen>
  );
}

function DayCell({
  day,
  program,
  today,
  onClick,
}: {
  day: WeekDay;
  program: ReturnType<typeof useActive>["program"];
  today: IsoDate;
  onClick: () => void;
}) {
  const color = dayColor(program, day.day);
  const letter =
    day.day.kind === "workout" ? day.day.sessionId : day.day.kind === "walk" ? "W" : "";
  const done = day.status === "done";
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`${weekdayName(weekdayOf(day.date))} ${day.date}: ${day.day.title}, ${day.status}`}
      className={`flex aspect-square min-h-10 items-center justify-center rounded-lg text-xs font-bold ${
        day.date === today ? "ring-2 ring-[var(--app-accent)]" : ""
      }`}
      style={
        done
          ? { background: bannerColor(color), color: "#fff" }
          : day.status === "missed"
            ? {
                background: "var(--app-surface-2)",
                color: "var(--app-muted)",
                boxShadow: `inset 0 0 0 1.5px ${color}`,
              }
            : day.day.kind === "rest"
              ? { background: "transparent", color: "var(--app-muted)" }
              : {
                  background: "var(--app-surface)",
                  color: "var(--app-ink)",
                  boxShadow: `inset 0 0 0 1.5px ${color}`,
                }
      }
    >
      {done ? "✓" : day.status === "missed" ? "·" : letter}
    </button>
  );
}

function Legend() {
  return (
    <p className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
      <span>✓ done</span>
      <span>· missed</span>
      <span>Letter = upcoming session (W = walk)</span>
    </p>
  );
}

function DaySheet({
  day,
  today,
  profileId,
  onClose,
}: {
  day: WeekDay;
  today: IsoDate;
  profileId: string;
  onClose: () => void;
}) {
  const { profile, program } = useActive();
  const navigate = useNavigate();
  const [minutes, setMinutes] = useState<number | undefined>();
  const week = programWeek(profile.startDate, day.date) ?? 1;
  const past = daysBetween(day.date, today) >= 0;
  const walk = walkForDay(program, day.day, week);
  const sessionId = day.day.sessionId;
  const title = `${weekdayName(weekdayOf(day.date))} ${day.date}`;

  return (
    <Sheet open title={title} onClose={onClose}>
      <div className="space-y-3">
        <p className="text-lg font-bold text-ink">{day.day.title}</p>
        <p className="text-muted">
          {day.day.subtitle} · Week {week} · {day.status === "done" ? "Done ✓" : day.status}
        </p>
        {sessionId && (
          <>
            {day.date === today && day.status !== "done" && (
              <Button
                size="lg"
                className="w-full"
                onClick={() => navigate(`/workout/${sessionId}`)}
              >
                Start workout
              </Button>
            )}
            {past && (
              <Button
                variant="secondary"
                size="lg"
                className="w-full"
                onClick={() => navigate(`/log-past?date=${day.date}&session=${sessionId}`)}
              >
                Log this workout
              </Button>
            )}
            <Button
              variant="ghost"
              className="w-full"
              onClick={() => navigate(`/plan/session/${sessionId}`)}
            >
              See the plan for {program.sessions[sessionId]?.title}
            </Button>
          </>
        )}
        {walk && past && (
          <div className="space-y-3">
            <Stepper
              label="Walk minutes"
              unit="min"
              step={5}
              max={300}
              value={minutes ?? walk.minutes[1]}
              onChange={setMinutes}
            />
            <Button
              size="lg"
              className="w-full"
              onClick={async () => {
                await logWalk(profileId, day.date, minutes ?? walk.minutes[1]);
                onClose();
              }}
            >
              Log walk
            </Button>
          </div>
        )}
      </div>
    </Sheet>
  );
}
