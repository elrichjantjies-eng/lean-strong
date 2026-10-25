import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import type { Program } from "@/content/types";
import { db } from "@/db/db";
import { logDaily, logWalk } from "@/db/repo";
import { setFlag, useSetting } from "@/db/settings";
import { isCheckinWeek } from "@/domain/coaching";
import { daysBetween, weekdayName, weekdayOf } from "@/domain/dates";
import type { IsoDate, Profile } from "@/domain/model";
import {
  completedSessionIds,
  isMeasurementWeek,
  phaseForWeek,
  planToday,
  programPosition,
  weekDates,
  weekStatuses,
} from "@/domain/schedule";
import { resolveSession, walkForDay } from "@/domain/session";
import { Icon } from "@/components/Icon";
import { Screen } from "@/components/Screen";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Callout, Card, ProgressBar } from "@/components/ui";
import { QuickAdd } from "./QuickAdd";
import { dayColor, walkIntervalsText } from "./labels";
import { WeekStrip } from "./WeekStrip";
import { bannerColor } from "@/theme/theme";

const BACKUP_EVERY_DAYS = 14;

export function TodayScreen() {
  const { profile, program } = useActive();
  const today = useToday();
  const pos = programPosition(program, profile.startDate, today);

  if (pos.status === "before")
    return <BeforeStart profile={profile} program={program} daysUntil={pos.daysUntil} />;
  if (pos.status === "complete") return <Complete profile={profile} program={program} />;

  const phaseColor = program.theme.phaseColors[pos.p]!;
  return (
    <Screen title={`Hi ${profile.name}`} kicker={program.subtitle}>
      <div className="mb-5">
        <p className="mb-2 text-sm font-semibold text-ink">
          Week {pos.week} of {program.weeks} · Phase {pos.phase.number} · {pos.phase.name}
        </p>
        <ProgressBar
          label="Program progress"
          value={(pos.week - 1 + (pos.dayOfWeek + 1) / 7) / program.weeks}
          color={phaseColor}
        />
      </div>
      <ActiveToday profile={profile} program={program} today={today} week={pos.week} />
    </Screen>
  );
}

function ActiveToday({
  profile,
  program,
  today,
  week,
}: {
  profile: Profile;
  program: Program;
  today: IsoDate;
  week: number;
}) {
  const dates = weekDates(profile.startDate, week);
  const data = useLiveQuery(async () => {
    const [logs, activities, weightToday, measurements, checkin] = await Promise.all([
      db.sessionLogs.where({ profileId: profile.id, week }).toArray(),
      db.activityLogs
        .where("[profileId+date]")
        .between([profile.id, dates[0]!], [profile.id, dates[6]!], true, true)
        .toArray(),
      db.weights.where({ profileId: profile.id, date: today }).first(),
      db.measurements.where({ profileId: profile.id, week }).count(),
      db.checkins.where({ profileId: profile.id, week }).first(),
    ]);
    return { logs, activities, weightToday, measurements, checkin };
  }, [profile.id, week, today]);
  const lastBackupAt = useSetting("lastBackupAt");
  const [now] = useState(() => Date.now());

  if (!data) return null;
  const completed = completedSessionIds(data.logs, week);
  const plan = planToday(program, profile.startDate, today, completed);
  const days = weekStatuses(
    program,
    profile.startDate,
    week,
    today,
    completed,
    new Set(data.activities.filter((a) => a.kind === "walk").map((a) => a.date)),
  );
  const inProgress = data.logs.find((l) => l.status === "in-progress");

  const nudges: { text: string; to?: string }[] = [];
  if (program.tracking.weighIn.suggestedDays.includes(weekdayOf(today)) && !data.weightToday) {
    nudges.push({ text: "Weigh-in morning: log today's weight." });
  }
  if (isMeasurementWeek(program, week) && data.measurements === 0) {
    nudges.push({ text: "Measurements are due this week.", to: "/progress?tab=measurements" });
  }
  if (isCheckinWeek(program, week) && !data.checkin) {
    nudges.push({ text: "Your weekly check-in is ready.", to: "/progress?tab=checkin" });
  }
  const backupDue = now - (lastBackupAt ?? profile.createdAt) > BACKUP_EVERY_DAYS * 86_400_000;
  if (backupDue) nudges.push({ text: "Time for a backup of your data.", to: "/more/backup" });
  if (week < program.weeks) {
    const nextPhase = phaseForWeek(program, week + 1);
    if (nextPhase.number !== phaseForWeek(program, week).number) {
      nudges.push({
        text: `Next week Phase ${nextPhase.number} · ${nextPhase.name} starts. ${nextPhase.summary.sets}.`,
      });
    }
  }

  return (
    <div className="space-y-5">
      {inProgress && (
        <Callout tone="accent" title="Workout in progress">
          <p>{program.sessions[inProgress.sessionId]?.title} isn't finished yet.</p>
          <Link
            to={`/workout/${inProgress.sessionId}?resume=${inProgress.id}`}
            className="mt-2 inline-flex min-h-12 items-center font-semibold text-accent-ink"
          >
            Resume workout →
          </Link>
        </Callout>
      )}

      {plan.catchUp && !inProgress && (
        <SessionCard
          program={program}
          sessionId={plan.catchUp.sessionId}
          week={week}
          kicker={`Catch up · missed ${weekdayName(plan.catchUp.missedWeekday)}`}
          primary
        />
      )}

      {plan.day.kind === "workout" &&
        plan.todaySessionId &&
        (plan.todayDone ? (
          <Card>
            <p className="font-semibold text-ink">
              <span aria-hidden="true">✓ </span>
              {program.sessions[plan.todaySessionId]?.title} done. Nice work!
            </p>
            <p className="text-sm text-muted">Log your steps and water below, then rest up.</p>
          </Card>
        ) : (
          <SessionCard
            program={program}
            sessionId={plan.todaySessionId}
            week={week}
            kicker={`Today · ${weekdayName(weekdayOf(today))}`}
            primary={!plan.catchUp}
          />
        ))}

      {plan.day.kind !== "workout" && (
        <WalkOrRestCard
          program={program}
          profile={profile}
          today={today}
          week={week}
          walked={data.activities.some((a) => a.kind === "walk" && a.date === today)}
        />
      )}

      <section aria-labelledby="week-heading">
        <h2 id="week-heading" className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">
          This week
        </h2>
        <WeekStrip program={program} days={days} />
      </section>

      <section aria-labelledby="log-heading">
        <h2 id="log-heading" className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">
          Log today
        </h2>
        <QuickAdd program={program} profile={profile} today={today} />
      </section>

      {nudges.length > 0 && (
        <section aria-label="Reminders" className="space-y-2">
          {nudges.map((n) =>
            n.to ? (
              <Link
                key={n.text}
                to={n.to}
                className="flex min-h-12 items-center justify-between gap-3 rounded-xl bg-info-soft px-4 py-2 text-ink"
              >
                <span>{n.text}</span>
                <Icon name="chevron" className="size-5 shrink-0 text-muted" />
              </Link>
            ) : (
              <p key={n.text} className="rounded-xl bg-info-soft px-4 py-3 text-ink">
                {n.text}
              </p>
            ),
          )}
        </section>
      )}

      <Link to="/log-past" className="block text-center text-sm font-semibold text-accent-ink">
        Log a past workout
      </Link>
    </div>
  );
}

function SessionCard({
  program,
  sessionId,
  week,
  kicker,
  primary,
}: {
  program: Program;
  sessionId: string;
  week: number;
  kicker: string;
  primary?: boolean;
}) {
  const navigate = useNavigate();
  const resolved = resolveSession(program, sessionId, week);
  const s = resolved.session;
  const color = program.theme.dayColors[s.id] ?? s.color;
  return (
    <Card flush>
      <div className="px-4 pt-3 pb-4 text-white" style={{ background: bannerColor(color) }}>
        <p className="text-xs font-bold tracking-wide uppercase">{kicker}</p>
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-2xl font-bold">{s.title}</h2>
          <p className="text-right font-semibold">{s.focus}</p>
        </div>
      </div>
      <div className="space-y-3 p-4">
        <p className="text-sm text-muted">
          About {resolved.estimatedMinutes} min · {resolved.pairs.length} pairs
          {resolved.finisher ? ` · ${resolved.finisher.name}` : ""}
          {resolved.waistCircuit ? ` · ${resolved.waistCircuit.title}` : ""}
        </p>
        {s.equipment && s.equipment.length > 0 && (
          <p className="text-sm text-ink">
            <span className="font-semibold">You need: </span>
            {s.equipment.join(" · ")}
          </p>
        )}
        <Button
          size="lg"
          variant={primary ? "primary" : "secondary"}
          className="w-full"
          onClick={() => navigate(`/workout/${s.id}`)}
        >
          <Icon name="play" className="size-5" /> Start workout
        </Button>
        <Link
          to={`/plan/session/${s.id}`}
          className="block text-center text-sm font-semibold text-accent-ink"
        >
          See the plan for {s.title}
        </Link>
      </div>
    </Card>
  );
}

function WalkOrRestCard({
  program,
  profile,
  today,
  week,
  walked,
}: {
  program: Program;
  profile: Profile;
  today: IsoDate;
  week: number;
  walked: boolean;
}) {
  const navigate = useNavigate();
  const [logging, setLogging] = useState(false);
  const day = program.weekTemplate.find((d) => d.weekday === weekdayOf(today))!;
  const walk = walkForDay(program, day, week);
  const color = dayColor(program, day);
  const [lo, hi] = walk?.minutes ?? [0, 0];
  const intervals = walk ? walkIntervalsText(walk) : null;

  return (
    <Card flush className="border-l-8" style={{ borderLeftColor: color }}>
      <div className="p-4">
        <p className="text-xs font-bold tracking-wide text-muted uppercase">
          Today · {weekdayName(day.weekday)}
        </p>
        <h2 className="text-2xl font-bold text-ink">{day.title}</h2>
        <p className="text-muted">{day.subtitle}</p>
        {walk && (
          <p className="mt-3 text-lg font-semibold text-ink">
            {lo === hi ? `${lo} min` : `${lo}–${hi} min`} walk{walk.optional ? " (optional)" : ""}
          </p>
        )}
        {intervals && <p className="mt-1 text-sm text-ink">Walking intervals: {intervals}</p>}
        {walked && <p className="mt-2 font-semibold text-ink">✓ Walk logged today</p>}
      </div>
      {walk && (
        <div className="grid grid-cols-2 gap-2 px-4 pb-4">
          <Button onClick={() => navigate(`/walk?min=${hi}`)}>
            <Icon name="play" className="size-5" /> Start walk
          </Button>
          <Button variant="secondary" onClick={() => setLogging(true)}>
            Log walk
          </Button>
        </div>
      )}
      {logging && (
        <LogWalkSheet
          defaultMinutes={hi}
          onClose={() => setLogging(false)}
          onSave={async (minutes, steps) => {
            await logWalk(profile.id, today, minutes);
            if (steps !== undefined) await logDaily(profile.id, today, { steps });
            setLogging(false);
          }}
        />
      )}
    </Card>
  );
}

function LogWalkSheet({
  defaultMinutes,
  onClose,
  onSave,
}: {
  defaultMinutes: number;
  onClose: () => void;
  onSave: (minutes: number, steps?: number) => void;
}) {
  const [minutes, setMinutes] = useState<number | undefined>(defaultMinutes);
  const [steps, setSteps] = useState<number | undefined>();
  return (
    <Sheet open title="Log a walk" onClose={onClose}>
      <div className="space-y-5">
        <Stepper
          label="Minutes"
          unit="min"
          step={5}
          max={300}
          value={minutes}
          onChange={setMinutes}
        />
        <Stepper
          label="Steps today (optional)"
          step={500}
          max={100_000}
          value={steps}
          onChange={setSteps}
        />
        <Button
          size="lg"
          className="w-full"
          disabled={!minutes}
          onClick={() => minutes && onSave(minutes, steps)}
        >
          Save
        </Button>
      </div>
    </Sheet>
  );
}

function BeforeStart({
  profile,
  program,
  daysUntil,
}: {
  profile: Profile;
  program: Program;
  daysUntil: number;
}) {
  const today = useToday();
  const data = useLiveQuery(
    async () => ({
      weights: await db.weights.where("profileId").equals(profile.id).count(),
      measurements: await db.measurements.where("profileId").equals(profile.id).count(),
    }),
    [profile.id],
  );
  const flags = useSetting("flags") ?? {};
  const flag = (k: string) => !!flags[`${profile.id}:${k}`];
  const howItWorks = program.pages[0];
  const items = [
    {
      key: "equipment",
      label: `Get your equipment ready: ${program.equipmentNeeded.join(", ")}`,
      done: flag("equipment"),
    },
    { key: "weighIn", label: "Do your first weigh-in", done: (data?.weights ?? 0) > 0 },
    {
      key: "measure",
      label: "Take your first measurements",
      done: (data?.measurements ?? 0) > 0,
      to: "/progress?tab=measurements",
    },
    ...(howItWorks
      ? [
          {
            key: "guide",
            label: `Read “${howItWorks.title}”`,
            done: flag("guide"),
            to: `/more/guide/${howItWorks.id}`,
          },
        ]
      : []),
  ];
  const startWeekday = weekdayName(weekdayOf(profile.startDate));
  return (
    <Screen title={`Hi ${profile.name}`} kicker={program.subtitle}>
      <Card className="mb-5 text-center">
        <p className="text-sm font-semibold text-muted uppercase">Your program starts in</p>
        <p className="text-5xl font-bold text-ink tabular-nums">{daysUntil}</p>
        <p className="font-semibold text-ink">
          day{daysUntil === 1 ? "" : "s"} · {startWeekday} {profile.startDate}
        </p>
      </Card>
      <h2 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">Get ready</h2>
      <Card className="mb-5 py-1">
        <ul>
          {items.map((it) => (
            <li
              key={it.key}
              className="flex min-h-14 items-center gap-3 border-b border-line py-2 last:border-0"
            >
              <button
                type="button"
                aria-label={it.done ? `${it.label}: done` : `Mark done: ${it.label}`}
                aria-pressed={it.done}
                onClick={() => void setFlag(profile.id, it.key, !it.done)}
                className="flex size-8 shrink-0 items-center justify-center rounded-full ring-2 ring-line"
                style={
                  it.done ? { background: "var(--app-accent-strong)", color: "#fff" } : undefined
                }
              >
                {it.done && <Icon name="check" className="size-5" />}
              </button>
              {it.to ? (
                <Link
                  to={it.to}
                  onClick={() => it.key === "guide" && void setFlag(profile.id, "guide")}
                  className="flex-1 text-ink underline-offset-2 hover:underline"
                >
                  {it.label}
                </Link>
              ) : (
                <span className="flex-1 text-ink">{it.label}</span>
              )}
            </li>
          ))}
        </ul>
      </Card>
      <h2 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">Log today</h2>
      <div className="mb-5">
        <QuickAdd program={program} profile={profile} today={today} />
      </div>
      <h2 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">The program</h2>
      <Card>
        <p className="text-ink">{program.summary}</p>
        <ul className="mt-3 space-y-1.5">
          {program.weekTemplate.map((d) => (
            <li key={d.weekday} className="flex items-center gap-3 text-sm">
              <span className="w-10 font-semibold text-muted">
                {weekdayName(d.weekday).slice(0, 3)}
              </span>
              <span
                className="size-3 rounded-full"
                style={{ background: dayColor(program, d) }}
                aria-hidden="true"
              />
              <span className="text-ink">
                {d.title} <span className="text-muted">· {d.subtitle}</span>
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </Screen>
  );
}

function Complete({ profile, program }: { profile: Profile; program: Program }) {
  const days = daysBetween(profile.startDate, useToday());
  return (
    <Screen title="Program complete!" kicker={program.subtitle}>
      <Card className="text-center">
        <p className="text-lg text-ink">
          You finished all {program.weeks} weeks, {profile.name}. That's {days} days of showing up.
        </p>
        <Link
          to="/complete"
          className="mt-4 inline-flex min-h-12 items-center justify-center rounded-xl bg-accent-strong px-5 font-semibold text-on-accent"
        >
          See your results
        </Link>
      </Card>
    </Screen>
  );
}
