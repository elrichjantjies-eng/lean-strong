import { useState } from "react";
import Dexie from "dexie";
import { useLiveQuery } from "dexie-react-hooks";
import type { Program } from "@/content/types";
import { db } from "@/db/db";
import { logDaily, logWeight } from "@/db/repo";
import type { IsoDate, Profile } from "@/domain/model";
import { Sheet } from "@/components/Sheet";
import { Stepper } from "@/components/Stepper";
import { Button } from "@/components/ui";

type Kind = "weight" | "steps" | "waterL" | "calories" | "proteinG";

const meta: Record<
  Kind,
  { label: string; unit: string; step: number; decimals: number; max: number }
> = {
  weight: { label: "Weight", unit: "kg", step: 0.1, decimals: 1, max: 300 },
  steps: { label: "Steps", unit: "steps", step: 500, decimals: 0, max: 100_000 },
  waterL: { label: "Water", unit: "L", step: 0.25, decimals: 2, max: 10 },
  calories: { label: "Calories", unit: "kcal", step: 50, decimals: 0, max: 10_000 },
  proteinG: { label: "Protein", unit: "g", step: 5, decimals: 0, max: 500 },
};

export function QuickAdd({
  program,
  profile,
  today,
}: {
  program: Program;
  profile: Profile;
  today: IsoDate;
}) {
  const [open, setOpen] = useState<Kind | null>(null);
  const data = useLiveQuery(async () => {
    const [weightToday, lastWeight, daily] = await Promise.all([
      db.weights.where({ profileId: profile.id, date: today }).first(),
      db.weights
        .where("[profileId+date]")
        .between([profile.id, Dexie.minKey], [profile.id, Dexie.maxKey])
        .last(),
      db.dailyNumbers.where({ profileId: profile.id, date: today }).first(),
    ]);
    return { weightToday, lastWeight, daily };
  }, [profile.id, today]);

  const kinds: Kind[] = ["weight", "steps"];
  if (program.tracking.daily.water) kinds.push("waterL");
  if (program.tracking.daily.calories) kinds.push("calories");
  if (program.tracking.daily.protein) kinds.push("proteinG");

  const current = (k: Kind): number | undefined =>
    k === "weight" ? data?.weightToday?.kg : data?.daily?.[k];

  return (
    <>
      <div className="grid grid-cols-3 gap-2 min-[400px]:grid-cols-5">
        {kinds.map((k) => {
          const v = current(k);
          return (
            <button
              key={k}
              type="button"
              onClick={() => setOpen(k)}
              className="flex min-h-18 flex-col items-center justify-center rounded-xl bg-surface px-1 py-2 shadow-sm ring-1 ring-line active:bg-surface-2"
            >
              <span className="text-xs font-semibold text-muted">{meta[k].label}</span>
              <span className="text-base font-bold text-ink tabular-nums">
                {v === undefined ? "+" : k === "steps" ? v.toLocaleString() : v}
              </span>
              {v !== undefined && <span className="text-[0.7rem] text-muted">{meta[k].unit}</span>}
            </button>
          );
        })}
      </div>
      {open && (
        <QuickAddSheet
          kind={open}
          initial={
            current(open) ??
            (open === "weight" ? (data?.lastWeight?.kg ?? profile.startKg) : undefined)
          }
          onClose={() => setOpen(null)}
          onSave={async (v) => {
            if (open === "weight") await logWeight(profile.id, today, v);
            else await logDaily(profile.id, today, { [open]: v });
            setOpen(null);
          }}
        />
      )}
    </>
  );
}

function QuickAddSheet({
  kind,
  initial,
  onClose,
  onSave,
}: {
  kind: Kind;
  initial: number | undefined;
  onClose: () => void;
  onSave: (v: number) => void;
}) {
  const m = meta[kind];
  const [value, setValue] = useState<number | undefined>(initial);
  return (
    <Sheet open title={`Today's ${m.label.toLowerCase()}`} onClose={onClose}>
      <Stepper
        label={m.label}
        unit={m.unit}
        step={m.step}
        decimals={m.decimals}
        max={m.max}
        value={value}
        onChange={setValue}
      />
      <Button
        size="lg"
        className="mt-5 w-full"
        disabled={value === undefined}
        onClick={() => value !== undefined && onSave(value)}
      >
        Save
      </Button>
    </Sheet>
  );
}
