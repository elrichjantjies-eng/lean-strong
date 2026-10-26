import { useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import { db } from "@/db/db";
import { logWeight } from "@/db/repo";
import { weightChartData } from "@/domain/progress";
import { LineChart } from "@/components/charts";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Card, Field, inputClass } from "@/components/ui";

const fmt = (n: number | null | undefined, d = 1) =>
  n === null || n === undefined ? "–" : n.toFixed(d);

const STATUS = {
  ahead: { label: "Ahead of plan", color: "#067647" },
  "on-track": { label: "On track", color: "#067647" },
  behind: { label: "Behind plan", color: "#b54708" },
} as const;

export function WeightTab() {
  const { profile, program } = useActive();
  const [adding, setAdding] = useState(false);
  const entries = useLiveQuery(
    () => db.weights.where("profileId").equals(profile.id).sortBy("date"),
    [profile.id],
  );
  if (!entries) return null;
  const data = weightChartData(program, profile, entries);
  const ticks = Array.from({ length: program.weeks + 1 }, (_, i) => i).filter(
    (w) => w % (program.weeks > 9 ? 3 : 1) === 0 || w === program.weeks,
  );

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-muted">Lost so far</p>
            <p className="text-3xl font-bold text-ink tabular-nums">
              {data.lostSoFar === null ? "Nothing yet" : `${fmt(data.lostSoFar)} kg`}
            </p>
            <p className="text-sm text-muted">
              From {profile.startKg} kg · goal {profile.goalKg} kg
            </p>
          </div>
          {data.status && (
            <span
              className="rounded-full px-3 py-1 text-sm font-bold text-white"
              style={{ background: STATUS[data.status].color }}
            >
              {STATUS[data.status].label}
            </span>
          )}
        </div>
        <Button className="mt-3 w-full" onClick={() => setAdding(true)}>
          Log weight
        </Button>
      </Card>

      {entries.length > 0 ? (
        <Card>
          <LineChart
            title="Weight over the program"
            series={[
              ...(data.target.length
                ? [
                    {
                      id: "target",
                      label: "Target path",
                      color: "var(--app-muted)",
                      points: data.target,
                      dashed: true,
                      width: 1.5,
                    },
                  ]
                : []),
              {
                id: "daily",
                label: "Daily",
                color: "var(--app-muted)",
                points: data.daily,
                dots: true,
                width: 0,
              },
              {
                id: "avg",
                label: "Weekly average",
                color: "var(--app-accent)",
                points: data.weekly,
                dots: true,
              },
            ]}
            band={data.band.length ? data.band : undefined}
            refLines={
              data.target.length
                ? [{ y: data.goalKg, label: `Goal ${data.goalKg} kg`, color: "var(--app-ink)" }]
                : []
            }
            markers={
              data.checkpoint
                ? [
                    {
                      ...data.checkpoint,
                      label: `Week ${data.checkpoint.x} checkpoint ${data.checkpoint.y} kg`,
                      color: "var(--app-primary)",
                    },
                  ]
                : []
            }
            xTicks={ticks}
            xLabel={(x) => (x === 0 ? "Start" : `W${x}`)}
            height={230}
          />
        </Card>
      ) : (
        <p className="text-muted">No weigh-ins yet. {program.tracking.weighIn.text}</p>
      )}

      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <caption className="sr-only">Weekly weight compared with the target</caption>
          <thead>
            <tr className="bg-surface-2 text-left text-xs font-bold text-muted uppercase">
              <th className="px-3 py-2">Week</th>
              <th className="px-3 py-2 text-right">Target</th>
              <th className="px-3 py-2 text-right">My average</th>
              <th className="px-3 py-2 text-right">Diff</th>
            </tr>
          </thead>
          <tbody>
            {data.rows.map((r) => (
              <tr key={r.week} className="border-t border-line">
                <td className="px-3 py-2 font-semibold text-ink">{r.week}</td>
                <td className="px-3 py-2 text-right text-muted tabular-nums">{fmt(r.target)}</td>
                <td className="px-3 py-2 text-right font-semibold text-ink tabular-nums">
                  {fmt(r.average)}
                </td>
                <td
                  className="px-3 py-2 text-right tabular-nums"
                  style={{
                    color: r.diff === null ? undefined : r.diff <= 0 ? "#067647" : "#b54708",
                  }}
                >
                  {r.diff === null
                    ? "–"
                    : Math.abs(r.diff) < 0.05
                      ? "0.0"
                      : `${r.diff > 0 ? "+" : ""}${fmt(r.diff)}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {entries.length > 0 && (
        <details className="rounded-2xl bg-surface p-4 ring-1 ring-line">
          <summary className="min-h-8 cursor-pointer font-semibold text-ink">
            All weigh-ins ({entries.length})
          </summary>
          <ul className="mt-2 divide-y divide-line">
            {[...entries].reverse().map((e) => (
              <li key={e.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-muted">{e.date}</span>
                <span className="flex items-center gap-3">
                  <span className="font-semibold text-ink tabular-nums">{e.kg} kg</span>
                  <button
                    type="button"
                    className="min-h-10 px-2 text-sm font-semibold text-accent-ink"
                    onClick={() => void db.weights.delete(e.id)}
                    aria-label={`Delete weigh-in on ${e.date}`}
                  >
                    Delete
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}

      {adding && (
        <LogWeightSheet
          initialKg={entries[entries.length - 1]?.kg ?? profile.startKg}
          onClose={() => setAdding(false)}
          onSave={async (date, kg) => {
            await logWeight(profile.id, date, kg);
            setAdding(false);
          }}
        />
      )}
    </div>
  );
}

function LogWeightSheet({
  initialKg,
  onClose,
  onSave,
}: {
  initialKg: number;
  onClose: () => void;
  onSave: (date: string, kg: number) => void;
}) {
  const today = useToday();
  const [date, setDate] = useState(today);
  const [kg, setKg] = useState<number | undefined>(initialKg);
  return (
    <Sheet open title="Log weight" onClose={onClose}>
      <div className="space-y-4">
        <Field label="Date">
          <input
            type="date"
            className={inputClass}
            value={date}
            max={today}
            onChange={(e) => setDate(e.target.value)}
          />
        </Field>
        <Stepper
          label="Weight"
          unit="kg"
          step={0.1}
          decimals={1}
          min={30}
          max={300}
          value={kg}
          onChange={setKg}
        />
        <Button size="lg" className="w-full" disabled={!kg} onClick={() => kg && onSave(date, kg)}>
          Save
        </Button>
      </div>
    </Sheet>
  );
}
