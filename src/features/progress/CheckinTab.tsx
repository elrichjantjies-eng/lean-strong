import { useEffect } from "react";
import { Link } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import { findCard } from "@/content/find";
import type { Program } from "@/content/types";
import { db, newId } from "@/db/db";
import { isCheckinWeek, type CheckinResult } from "@/domain/coaching";
import type { Checkin, MeasurementEntry } from "@/domain/model";
import { checkinFor, measurementTrend, waistFieldId } from "@/domain/progress";
import { programWeek } from "@/domain/schedule";
import { RichText } from "@/components/RichText";
import { Callout, Card } from "@/components/ui";

const STATUS_COLOR: Record<string, string> = {
  stalled: "#b54708",
  slow: "#b54708",
  "on-track": "#067647",
  fast: "#b42318",
  locked: "var(--app-muted)",
};
const STATUS_LABEL: Record<string, string> = {
  stalled: "Stalled",
  slow: "A little slow",
  "on-track": "On track",
  fast: "Faster than planned",
  locked: "Not enough data yet",
};

export function CheckinTab() {
  const { profile, program } = useActive();
  const today = useToday();
  const current = programWeek(profile.startDate, today);
  const data = useLiveQuery(async () => {
    const [weights, daily, measurements, stored] = await Promise.all([
      db.weights.where("profileId").equals(profile.id).toArray(),
      db.dailyNumbers.where("profileId").equals(profile.id).toArray(),
      db.measurements.where("profileId").equals(profile.id).toArray(),
      db.checkins.where("profileId").equals(profile.id).toArray(),
    ]);
    return { weights, daily, measurements, stored };
  }, [profile.id]);

  const week = current === null ? null : Math.min(current, program.weeks);
  const result =
    data && week !== null && isCheckinWeek(program, week)
      ? checkinFor(program, profile.startDate, week, data.weights, data.daily)
      : null;
  const storedNow = data?.stored.find((c) => c.week === week);

  // Store this week's check-in once it can be given, so the history stays visible.
  useEffect(() => {
    if (!result || result.status === "locked" || week === null || storedNow) return;
    void db.checkins.add({
      id: newId(),
      profileId: profile.id,
      week,
      rate: result.rate,
      status: result.status,
      message: result.message,
    });
  }, [result, week, storedNow, profile.id]);

  if (!data) return null;
  const weeksList = Array.from({ length: program.weeks }, (_, i) => i + 1).filter((w) =>
    isCheckinWeek(program, w),
  );
  const history = [...data.stored].filter((c) => c.week !== week).sort((a, b) => b.week - a.week);

  return (
    <div className="space-y-4">
      {week === null || !isCheckinWeek(program, week) ? (
        <Card>
          <p className="font-semibold text-ink">No check-in this week.</p>
          <p className="text-sm text-muted">
            Check-ins happen in weeks {weeksList.join(", ")}. Keep logging your weight 3–4 mornings
            a week.
          </p>
        </Card>
      ) : (
        result && (
          <CheckinCard
            program={program}
            week={week}
            result={result}
            measurements={data.measurements}
          />
        )
      )}
      {history.length > 0 && (
        <section aria-labelledby="checkin-history">
          <h2
            id="checkin-history"
            className="mb-2 text-sm font-bold tracking-wide text-muted uppercase"
          >
            Earlier check-ins
          </h2>
          <ul className="space-y-2">
            {history.map((c) => (
              <HistoryItem key={c.id} c={c} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function HistoryItem({ c }: { c: Checkin }) {
  return (
    <li className="rounded-2xl bg-surface p-3 ring-1 ring-line">
      <p className="text-sm font-bold" style={{ color: STATUS_COLOR[c.status] }}>
        Week {c.week} · {STATUS_LABEL[c.status] ?? c.status}
        {c.rate !== null ? ` · ${c.rate.toFixed(2)} kg/week` : ""}
      </p>
      <p className="text-sm text-ink">
        <RichText text={c.message} />
      </p>
    </li>
  );
}

function CheckinCard({
  program,
  week,
  result,
  measurements,
}: {
  program: Program;
  week: number;
  result: CheckinResult;
  measurements: MeasurementEntry[];
}) {
  const waistId = waistFieldId(program);
  const waist = waistId ? measurementTrend(measurements, waistId) : null;
  const unit = program.tracking.measurements.fields.find((f) => f.id === waistId)?.unit ?? "cm";
  const floorCard = findCard(program, "Know your floor");
  const color = STATUS_COLOR[result.status]!;

  return (
    <div className="space-y-3">
      <Card className="border-l-8" style={{ borderLeftColor: color }}>
        <p className="text-sm font-bold tracking-wide uppercase" style={{ color }}>
          Week {week} check-in · {STATUS_LABEL[result.status]}
        </p>
        {result.status === "locked" ? (
          <p className="mt-1 text-lg text-ink">{result.message}.</p>
        ) : (
          <>
            <p className="mt-1 text-3xl font-bold text-ink tabular-nums">
              {result.rate >= 0 ? "−" : "+"}
              {Math.abs(result.rate).toFixed(2)} kg/week
            </p>
            <p className="text-sm text-muted">
              Weekly averages, week {result.fromWeek} → week {result.toWeek}
            </p>
            <p className="mt-3 text-lg text-ink">
              <RichText text={result.message} />
            </p>
          </>
        )}
      </Card>

      {result.status !== "locked" && result.calorieWarning && floorCard && (
        <Callout tone="accent" title={floorCard.title}>
          {floorCard.items.map((item, i) => (
            <p key={i}>
              <RichText text={item} />
            </p>
          ))}
        </Callout>
      )}

      <Card>
        <p className="text-sm font-bold tracking-wide text-muted uppercase">Waist</p>
        {waist ? (
          <p className="text-ink">
            <span className="text-xl font-bold">
              {waist.change > 0 ? "+" : ""}
              {waist.change.toFixed(1)} {unit}
            </span>{" "}
            since you started
            {waist.sincePrevious !== null && (
              <span className="text-muted">
                {" "}
                · {waist.sincePrevious > 0 ? "+" : ""}
                {waist.sincePrevious.toFixed(1)} since last time
              </span>
            )}
          </p>
        ) : (
          <p className="text-muted">No waist measurements yet.</p>
        )}
        <Link
          to="/progress?tab=measurements"
          className="mt-2 inline-flex min-h-12 items-center font-semibold text-accent-ink"
        >
          Log measurements →
        </Link>
      </Card>

      {program.coaching.notes.length > 0 && (
        <Callout title="Good to know">
          {program.coaching.notes.map((n, i) => (
            <p key={i}>
              <RichText text={n} />
            </p>
          ))}
        </Callout>
      )}
    </div>
  );
}
