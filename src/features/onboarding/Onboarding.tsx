import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { findCallout, findCard } from "@/content/find";
import { programList } from "@/content/loader";
import type { Program } from "@/content/types";
import { createProfile, DEFAULT_REMINDERS } from "@/db/repo";
import { nextMondayOrToday, toIsoDate, weekdayName, weekdayOf } from "@/domain/dates";
import type { ReminderPrefs } from "@/domain/model";
import { placeholderValues, weightPlan } from "@/domain/weightPlan";
import { useAppTheme } from "@/app/active";
import { Icon } from "@/components/Icon";
import { RichText } from "@/components/RichText";
import { Stepper } from "@/components/Stepper";
import { Button, Callout, Card, Field, inputClass } from "@/components/ui";

type Step =
  | "welcome"
  | "program"
  | "name"
  | "start"
  | "weight"
  | "height"
  | "equipment"
  | "calories"
  | "reminders"
  | "done";

interface Draft {
  programId: string;
  name: string;
  startDate: string;
  startKg?: number;
  goalKg?: number;
  heightCm?: number;
  dumbbellKg?: number;
  kettlebellKg?: number;
  calorieTarget?: number;
  reminders: ReminderPrefs;
}

function stepsFor(program: Program | undefined): Step[] {
  const steps: Step[] = ["welcome", "program", "name", "start", "weight", "height"];
  if (program?.setting === "home") steps.push("equipment");
  steps.push("calories", "reminders", "done");
  return steps;
}

export function Onboarding({ onCancel }: { onCancel?: () => void }) {
  const navigate = useNavigate();
  const today = toIsoDate();
  const [draft, setDraft] = useState<Draft>({
    programId: "",
    name: "",
    startDate: nextMondayOrToday(today),
    dumbbellKg: 5,
    reminders: DEFAULT_REMINDERS,
  });
  const [stepIndex, setStepIndex] = useState(0);
  const [saving, setSaving] = useState(false);
  const program = programList.find((p) => p.id === draft.programId);
  useAppTheme(program);

  const steps = stepsFor(program);
  const step = steps[stepIndex]!;
  const set = (changes: Partial<Draft>) => setDraft((d) => ({ ...d, ...changes }));

  const plan = useMemo(
    () =>
      program && draft.startKg && draft.goalKg
        ? weightPlan(program, draft.startKg, draft.goalKg)
        : null,
    [program, draft.startKg, draft.goalKg],
  );

  const canContinue: Record<Step, boolean> = {
    welcome: true,
    program: !!program,
    name: draft.name.trim().length > 0,
    start: /^\d{4}-\d{2}-\d{2}$/.test(draft.startDate),
    weight: !!draft.startKg && !!draft.goalKg && draft.startKg >= 30 && draft.goalKg >= 30,
    height: true,
    equipment: true,
    calories: true,
    reminders: true,
    done: true,
  };

  async function finish() {
    if (!program || !draft.startKg || !draft.goalKg) return;
    setSaving(true);
    await createProfile({
      name: draft.name.trim(),
      programId: program.id,
      startDate: draft.startDate,
      startKg: draft.startKg,
      goalKg: draft.goalKg,
      ...(draft.heightCm ? { heightCm: draft.heightCm } : {}),
      ...(draft.calorieTarget ? { calorieTarget: draft.calorieTarget } : {}),
      equipment:
        program.setting === "home"
          ? {
              ...(draft.dumbbellKg ? { dumbbellKg: draft.dumbbellKg } : {}),
              ...(draft.kettlebellKg ? { kettlebellKg: draft.kettlebellKg } : {}),
            }
          : {},
      reminders: draft.reminders,
    });
    navigate("/", { replace: true });
  }

  const next = () => (step === "done" ? void finish() : setStepIndex((i) => i + 1));
  const back = () => (stepIndex === 0 ? onCancel?.() : setStepIndex((i) => i - 1));

  return (
    <div
      className="mx-auto flex min-h-dvh max-w-xl flex-col px-4"
      style={{
        paddingTop: "calc(var(--safe-top) + 0.5rem)",
        paddingBottom: "calc(var(--safe-bottom) + 1rem)",
      }}
    >
      <div className="flex h-12 items-center justify-between">
        {stepIndex > 0 || onCancel ? (
          <button
            type="button"
            onClick={back}
            aria-label="Back"
            className="-ml-2 flex size-12 items-center justify-center rounded-full text-ink active:bg-surface-2"
          >
            <Icon name="back" />
          </button>
        ) : (
          <span />
        )}
        <div className="flex gap-1.5" aria-label={`Step ${stepIndex + 1} of ${steps.length}`}>
          {steps.map((s, i) => (
            <span
              key={s}
              className={`h-1.5 rounded-full ${i === stepIndex ? "w-5 bg-accent" : "w-1.5 bg-line"}`}
            />
          ))}
        </div>
        <span className="w-10" />
      </div>

      <div className="flex-1 py-4">
        {step === "welcome" && (
          <div className="pt-10 text-center">
            <div className="mx-auto mb-6 flex size-24 items-center justify-center rounded-3xl bg-primary text-4xl font-bold text-accent">
              LS
            </div>
            <h1 className="text-3xl font-bold text-ink">Lean & Strong</h1>
            <p className="mx-auto mt-3 max-w-sm text-lg text-muted">
              Your workouts, timers and progress in one place. Everything stays on this phone.
            </p>
          </div>
        )}

        {step === "program" && (
          <StepBody title="Pick your program">
            <div className="space-y-3">
              {programList.map((p) => {
                const selected = p.id === draft.programId;
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => set({ programId: p.id })}
                    aria-pressed={selected}
                    className={`relative block w-full rounded-2xl bg-surface p-4 text-left shadow-sm transition ${
                      selected ? "" : "ring-1 ring-line"
                    }`}
                    style={selected ? { boxShadow: `0 0 0 3px ${p.theme.accent}` } : undefined}
                  >
                    {selected && (
                      <span
                        className="absolute top-3 right-3 flex size-7 items-center justify-center rounded-full text-white"
                        style={{ background: p.theme.accent }}
                        aria-hidden="true"
                      >
                        <Icon name="check" className="size-4" />
                      </span>
                    )}
                    <p
                      className="text-xs font-bold tracking-wide uppercase"
                      style={{ color: p.theme.accent }}
                    >
                      {p.weeks} weeks · {p.setting === "gym" ? "Gym" : "Home"}
                    </p>
                    <p className="text-lg font-bold text-ink">{p.subtitle}</p>
                    <p className="mt-1 text-sm text-muted">{p.summary}</p>
                    <p className="mt-2 text-sm text-ink">
                      <span className="font-semibold">You need: </span>
                      {p.equipmentNeeded.join(" · ")}
                    </p>
                  </button>
                );
              })}
            </div>
          </StepBody>
        )}

        {step === "name" && (
          <StepBody title="What should we call you?">
            <Field label="Your name">
              <input
                className={inputClass}
                value={draft.name}
                autoFocus
                autoComplete="given-name"
                onChange={(e) => set({ name: e.target.value })}
                onKeyDown={(e) => e.key === "Enter" && canContinue.name && next()}
              />
            </Field>
          </StepBody>
        )}

        {step === "start" && (
          <StepBody
            title="When do you start?"
            intro="Weeks are counted from this day. You can change it later without losing anything."
          >
            <Field label="Start date" hint={`That's a ${weekdayName(weekdayOf(draft.startDate))}.`}>
              <input
                type="date"
                className={inputClass}
                value={draft.startDate}
                onChange={(e) => set({ startDate: e.target.value })}
              />
            </Field>
          </StepBody>
        )}

        {step === "weight" && program && (
          <StepBody
            title="Your weight"
            intro="In kilograms. Use this morning's weight if you have it."
          >
            <div className="space-y-5">
              <Stepper
                label="Current weight"
                unit="kg"
                decimals={1}
                step={0.1}
                min={30}
                max={300}
                value={draft.startKg}
                onChange={(v) => set({ startKg: v })}
              />
              <Stepper
                label="Goal weight"
                unit="kg"
                decimals={1}
                step={0.5}
                min={30}
                max={300}
                value={draft.goalKg}
                onChange={(v) => set({ goalKg: v })}
              />
              {draft.startKg && draft.goalKg && (
                <PaceNote program={program} plan={plan} draft={draft} />
              )}
            </div>
          </StepBody>
        )}

        {step === "height" && (
          <StepBody title="Your height" intro="Optional. Skip it if you like.">
            <Stepper
              label="Height"
              unit="cm"
              min={120}
              max={230}
              value={draft.heightCm}
              onChange={(v) => set({ heightCm: v })}
            />
          </StepBody>
        )}

        {step === "equipment" && (
          <StepBody title="Your weights at home" intro="The app uses these to prefill your sets.">
            <div className="space-y-5">
              <Stepper
                label="Dumbbells (each)"
                unit="kg"
                decimals={1}
                step={0.5}
                min={0}
                max={50}
                value={draft.dumbbellKg}
                onChange={(v) => set({ dumbbellKg: v })}
              />
              <Stepper
                label="Kettlebell"
                unit="kg"
                decimals={1}
                step={1}
                min={0}
                max={50}
                value={draft.kettlebellKg}
                onChange={(v) => set({ kettlebellKg: v })}
              />
            </div>
          </StepBody>
        )}

        {step === "calories" && program && (
          <StepBody
            title="Daily calorie target"
            intro="Optional. If you count calories, the app can compare your logs with it."
          >
            <Stepper
              label="Calories per day"
              unit="kcal"
              step={50}
              min={0}
              max={6000}
              value={draft.calorieTarget}
              onChange={(v) => set({ calorieTarget: v || undefined })}
            />
            <CalorieFloorWarning program={program} kcal={draft.calorieTarget} />
          </StepBody>
        )}

        {step === "reminders" && program && (
          <StepBody title="Reminders" intro="Choose what you'd like a nudge for.">
            <RemindersForm
              program={program}
              value={draft.reminders}
              onChange={(reminders) => set({ reminders })}
            />
          </StepBody>
        )}

        {step === "done" && program && (
          <StepBody title={`You're all set, ${draft.name.trim()}!`}>
            <Card>
              <dl className="space-y-2 text-ink">
                <Row label="Program" value={program.subtitle} />
                <Row
                  label="Starts"
                  value={`${weekdayName(weekdayOf(draft.startDate))} ${draft.startDate}`}
                />
                <Row label="Weight" value={`${draft.startKg} kg → ${draft.goalKg} kg`} />
                {plan && (
                  <Row label="Pace" value={`About ${plan.ratePerWeek.toFixed(1)} kg a week`} />
                )}
              </dl>
            </Card>
            <p className="mt-4 text-muted">You can change any of this later in More → Settings.</p>
          </StepBody>
        )}
      </div>

      <Button size="lg" onClick={next} disabled={!canContinue[step] || saving}>
        {step === "welcome" ? "Get started" : step === "done" ? "Let's go" : "Continue"}
      </Button>
      {(step === "height" || step === "calories") && (
        <Button variant="ghost" className="mt-2" onClick={next}>
          Skip
        </Button>
      )}
    </div>
  );
}

function StepBody({
  title,
  intro,
  children,
}: {
  title: string;
  intro?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-ink">{title}</h1>
      {intro && <p className="mt-1 text-muted">{intro}</p>}
      <div className="mt-5">{children}</div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right font-semibold">{value}</dd>
    </div>
  );
}

function PaceNote({
  program,
  plan,
  draft,
}: {
  program: Program;
  plan: ReturnType<typeof weightPlan>;
  draft: Draft;
}) {
  if (!plan) {
    return (
      <Callout>
        <p>
          No weight-loss target, so the app will simply track your weight and skip the weight
          coaching.
        </p>
      </Callout>
    );
  }
  const values = placeholderValues(program, draft.startKg!, draft.goalKg!);
  const model = findCallout(program, "Why a steady");
  return (
    <div className="space-y-3">
      {plan.capped && (
        <Callout tone="accent" title="A healthy pace">
          <p>
            Your goal would need more than{" "}
            <strong>{program.weightPlan.maxRateKgPerWeek} kg a week</strong>, so the plan uses that
            healthy maximum. You'll reach {draft.goalKg} kg around{" "}
            <strong>week {plan.weeksToGoal}</strong>
            {plan.beyondProgram ? `, a little after the ${program.weeks}-week program ends.` : "."}
          </p>
        </Callout>
      )}
      {!plan.capped && (
        <Callout>
          <p>
            That's about <strong>{plan.ratePerWeek.toFixed(1)} kg a week</strong> over{" "}
            {program.weeks} weeks.
          </p>
        </Callout>
      )}
      {model && (
        <Callout tone="accent" title={model.title}>
          {model.items.map((item, i) => (
            <p key={i}>
              <RichText text={item} values={values} />
            </p>
          ))}
        </Callout>
      )}
    </div>
  );
}

export function CalorieFloorWarning({ program, kcal }: { program: Program; kcal?: number }) {
  const floor = program.weightPlan.calorieFloorKcal;
  if (floor === null || !kcal || kcal >= floor) return null;
  const card = findCard(program, "Know your floor");
  return (
    <div className="mt-4">
      <Callout tone="accent" title={card?.title ?? "Know your floor"}>
        {card ? (
          card.items.map((item, i) => (
            <p key={i}>
              <RichText text={item} />
            </p>
          ))
        ) : (
          <p>That's below {floor} kcal a day.</p>
        )}
      </Callout>
    </div>
  );
}

export function RemindersForm({
  program,
  value,
  onChange,
  platformNote = true,
}: {
  program: Program;
  value: ReminderPrefs;
  onChange: (v: ReminderPrefs) => void;
  platformNote?: boolean;
}) {
  const toggle = (key: keyof ReminderPrefs, label: string, hint?: string) => (
    <label className="flex min-h-14 items-center justify-between gap-4 border-b border-line py-2 last:border-0">
      <span>
        <span className="block font-medium text-ink">{label}</span>
        {hint && <span className="block text-sm text-muted">{hint}</span>}
      </span>
      <input
        type="checkbox"
        className="size-6 shrink-0 accent-[var(--app-accent)]"
        checked={!!value[key]}
        onChange={(e) => onChange({ ...value, [key]: e.target.checked })}
      />
    </label>
  );
  const weighDays = program.tracking.weighIn.suggestedDays;
  return (
    <div className="space-y-4">
      <Field label="Workout reminder time">
        <input
          type="time"
          className={inputClass}
          value={value.workoutTime}
          onChange={(e) => onChange({ ...value, workoutTime: e.target.value })}
        />
      </Field>
      <Card className="py-1">
        {toggle("workouts", "Workout days")}
        {toggle(
          "weighIn",
          "Weigh-in mornings",
          weighDays.length === 7
            ? "Every morning"
            : weighDays.map((d) => weekdayName(d).slice(0, 3)).join(", "),
        )}
        {toggle("measurements", "Measurement day")}
        {toggle("checkin", "Weekly check-in")}
        {toggle("backup", "Backup reminder", "Every 2 weeks")}
        {program.tracking.sitBreaks &&
          toggle("sitBreaks", "Sit-break reminders", program.tracking.sitBreaks.text)}
      </Card>
      {program.tracking.sitBreaks && value.sitBreaks && (
        <div className="grid grid-cols-2 gap-3">
          <Field label="Work starts">
            <input
              type="time"
              className={inputClass}
              value={value.sitBreakStart}
              onChange={(e) => onChange({ ...value, sitBreakStart: e.target.value })}
            />
          </Field>
          <Field label="Work ends">
            <input
              type="time"
              className={inputClass}
              value={value.sitBreakEnd}
              onChange={(e) => onChange({ ...value, sitBreakEnd: e.target.value })}
            />
          </Field>
        </div>
      )}
      {platformNote && (
        <p className="text-sm text-muted">
          On Android these arrive as notifications. On iPhone you can add your workouts to the
          Calendar app from More → Settings.
        </p>
      )}
    </div>
  );
}
