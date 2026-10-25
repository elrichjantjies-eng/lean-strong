import { useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive, useToday } from "@/app/active";
import { db, newId } from "@/db/db";
import { measurementTrend, waistFieldId } from "@/domain/progress";
import { isMeasurementWeek, programWeek } from "@/domain/schedule";
import { LineChart } from "@/components/charts";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button, Callout, Card, Field, inputClass } from "@/components/ui";

const signed = (n: number, unit: string) => `${n > 0 ? "+" : ""}${n.toFixed(1)} ${unit}`;

export function MeasurementsTab() {
  const { profile, program } = useActive();
  const today = useToday();
  const [open, setOpen] = useState(false);
  const entries = useLiveQuery(
    () => db.measurements.where("profileId").equals(profile.id).sortBy("date"),
    [profile.id],
  );
  if (!entries) return null;
  const m = program.tracking.measurements;
  const week = Math.min(program.weeks, Math.max(1, programWeek(profile.startDate, today) ?? 1));
  const dueNow = isMeasurementWeek(program, week) && !entries.some((e) => e.week === week);
  const waistId = waistFieldId(program);
  const waistField = m.fields.find((f) => f.id === waistId);
  const waist = waistId ? measurementTrend(entries, waistId) : null;
  const waistFocus = program.setting === "home";
  const others = m.fields.filter((f) => !(waistFocus && f.id === waistId));

  return (
    <div className="space-y-4">
      {dueNow && (
        <Callout tone="accent" title="Measurements due">
          <p>
            Week {week}: take your measurements{" "}
            {m.everyWeeks === 1 ? "every week" : `every ${m.everyWeeks} weeks`}.
          </p>
        </Callout>
      )}
      <Button size="lg" className="w-full" onClick={() => setOpen(true)}>
        Log measurements
      </Button>

      {waistFocus && waistField && (
        <Card className="border-l-8" style={{ borderLeftColor: "var(--app-accent)" }}>
          <p className="text-sm font-bold tracking-wide text-accent-ink uppercase">Your waist</p>
          {waist ? (
            <>
              <p className="text-4xl font-bold text-ink tabular-nums">
                {waist.latest} {waistField.unit}
              </p>
              <p className="font-semibold text-ink">
                {signed(waist.change, waistField.unit)} since you started
                {waist.sincePrevious !== null && (
                  <span className="text-muted">
                    {" "}
                    · {signed(waist.sincePrevious, waistField.unit)} since last time
                  </span>
                )}
              </p>
              {waist.points.length > 1 && (
                <div className="mt-3">
                  <LineChart
                    title={`${waistField.label} trend`}
                    series={[
                      {
                        id: "w",
                        label: waistField.label,
                        color: "var(--app-accent)",
                        points: waist.points.map((p) => ({ x: p.week, y: p.value })),
                        dots: true,
                      },
                    ]}
                    xLabel={(x) => `W${x}`}
                    height={170}
                  />
                </div>
              )}
            </>
          ) : (
            <p className="text-muted">{waistField.hint}</p>
          )}
        </Card>
      )}

      <div className="grid gap-3">
        {others.map((f) => {
          const t = measurementTrend(entries, f.id);
          return (
            <Card key={f.id}>
              <div className="flex items-baseline justify-between gap-3">
                <p className="font-bold text-ink">{f.label}</p>
                {t && (
                  <p className="text-right text-ink tabular-nums">
                    <span className="text-lg font-bold">
                      {t.latest} {f.unit === "1–10" ? "/ 10" : f.unit}
                    </span>
                    {f.unit !== "1–10" && (
                      <span className="block text-sm text-muted">
                        {signed(t.change, f.unit)} since start
                      </span>
                    )}
                  </p>
                )}
              </div>
              {!t && <p className="text-sm text-muted">{f.hint}</p>}
              {t && t.points.length > 1 && (
                <LineChart
                  title={`${f.label} trend`}
                  series={[
                    {
                      id: f.id,
                      label: f.label,
                      color: "var(--app-primary)",
                      points: t.points.map((p) => ({ x: p.week, y: p.value })),
                      dots: true,
                    },
                  ]}
                  xLabel={(x) => `W${x}`}
                  height={140}
                />
              )}
            </Card>
          );
        })}
      </div>

      {open && (
        <LogMeasurementsSheet
          onClose={() => setOpen(false)}
          last={entries[entries.length - 1]?.values ?? {}}
        />
      )}
    </div>
  );
}

function LogMeasurementsSheet({
  onClose,
  last,
}: {
  onClose: () => void;
  last: Record<string, number>;
}) {
  const { profile, program } = useActive();
  const today = useToday();
  const [date, setDate] = useState(today);
  const [values, setValues] = useState<Record<string, number | undefined>>(last);
  const fields = program.tracking.measurements.fields;
  return (
    <Sheet open title="Log measurements" onClose={onClose}>
      <div className="space-y-5">
        <Field label="Date">
          <input
            type="date"
            className={inputClass}
            value={date}
            max={today}
            onChange={(e) => setDate(e.target.value)}
          />
        </Field>
        {fields.map((f) => {
          const scale = f.unit === "1–10";
          return (
            <div key={f.id}>
              <Stepper
                label={scale ? `${f.label} (1–10)` : `${f.label} (${f.unit})`}
                unit={scale ? undefined : f.unit}
                step={scale ? 1 : 0.5}
                decimals={scale ? 0 : 1}
                min={scale ? 1 : 0}
                max={scale ? 10 : 300}
                value={values[f.id]}
                onChange={(v) => setValues((s) => ({ ...s, [f.id]: v }))}
              />
              <p className="mt-1 text-center text-sm text-muted">{f.hint}</p>
            </div>
          );
        })}
        <Button
          size="lg"
          className="w-full"
          onClick={async () => {
            const clean = Object.fromEntries(
              Object.entries(values).filter((e): e is [string, number] => typeof e[1] === "number"),
            );
            const week = Math.min(
              program.weeks,
              Math.max(1, programWeek(profile.startDate, date) ?? 1),
            );
            await db.measurements.add({
              id: newId(),
              profileId: profile.id,
              date,
              week,
              values: clean,
            });
            onClose();
          }}
        >
          Save
        </Button>
      </div>
    </Sheet>
  );
}
