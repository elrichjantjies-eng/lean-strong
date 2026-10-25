import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useActive, useToday } from "@/app/active";
import { deleteProfileData, updateProfile } from "@/db/repo";
import { setSetting, useSetting } from "@/db/settings";
import { db } from "@/db/db";
import type { Profile, ReminderPrefs } from "@/domain/model";
import { buildIcs } from "@/domain/reminders";
import { toIsoDate } from "@/domain/dates";
import { isNative } from "@/platform";
import { shareFile } from "@/platform/files";
import {
  notificationPermission,
  requestNotificationPermission,
  syncReminders,
} from "@/platform/notifications";
import { Screen } from "@/components/Screen";
import { Stepper } from "@/components/Stepper";
import { Button, Callout, Card, Field, inputClass } from "@/components/ui";
import { CalorieFloorWarning, RemindersForm } from "@/features/onboarding/Onboarding";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="mb-6">
      <h2 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">{title}</h2>
      {children}
    </section>
  );
}

function Toggle({
  label,
  checked,
  onChange,
  hint,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  hint?: string;
}) {
  return (
    <label className="flex min-h-14 items-center justify-between gap-4 border-b border-line py-2 last:border-0">
      <span>
        <span className="block font-medium text-ink">{label}</span>
        {hint && <span className="block text-sm text-muted">{hint}</span>}
      </span>
      <input
        type="checkbox"
        className="size-6 shrink-0 accent-[var(--app-accent)]"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
    </label>
  );
}

function Segmented<T extends string | number>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="grid auto-cols-fr grid-flow-col gap-1 rounded-xl bg-surface-2 p-1"
    >
      {options.map((o) => (
        <button
          key={String(o.value)}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={`min-h-11 rounded-lg px-2 text-sm font-semibold ${
            value === o.value ? "bg-accent-strong text-on-accent shadow-sm" : "text-muted"
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function SettingsScreen() {
  const { profile, program } = useActive();
  return (
    <Screen title="Settings" back="/more">
      <ProfileSettings key={profile.id} profile={profile} />
      <ReminderSettings key={`r-${profile.id}`} profile={profile} />
      <AppSettings />
      <DangerZone profile={profile} programTitle={program.subtitle} />
    </Screen>
  );
}

function ProfileSettings({ profile }: { profile: Profile }) {
  const { program } = useActive();
  const [draft, setDraft] = useState(profile);
  const [saved, setSaved] = useState(false);
  const set = (changes: Partial<Profile>) => {
    setDraft((d) => ({ ...d, ...changes }));
    setSaved(false);
  };
  const dirty = JSON.stringify(draft) !== JSON.stringify(profile);
  return (
    <Section title="Profile">
      <Card className="space-y-4">
        <Field label="Name">
          <input
            className={inputClass}
            value={draft.name}
            onChange={(e) => set({ name: e.target.value })}
          />
        </Field>
        <Field
          label="Start date"
          hint="Weeks are counted from this day. Your logs are kept if you change it."
        >
          <input
            type="date"
            className={inputClass}
            value={draft.startDate}
            onChange={(e) => e.target.value && set({ startDate: e.target.value })}
          />
        </Field>
        <Stepper
          label="Starting weight"
          unit="kg"
          step={0.1}
          decimals={1}
          min={30}
          max={300}
          value={draft.startKg}
          onChange={(v) => v && set({ startKg: v })}
        />
        <Stepper
          label="Goal weight"
          unit="kg"
          step={0.5}
          decimals={1}
          min={30}
          max={300}
          value={draft.goalKg}
          onChange={(v) => v && set({ goalKg: v })}
        />
        <Stepper
          label="Height (optional)"
          unit="cm"
          min={0}
          max={230}
          value={draft.heightCm}
          onChange={(v) => set({ heightCm: v || undefined })}
        />
        <div>
          <Stepper
            label="Daily calorie target (optional)"
            unit="kcal"
            step={50}
            max={6000}
            value={draft.calorieTarget}
            onChange={(v) => set({ calorieTarget: v || undefined })}
          />
          <CalorieFloorWarning program={program} kcal={draft.calorieTarget} />
        </div>
        {program.setting === "home" && (
          <>
            <Stepper
              label="Dumbbells (each)"
              unit="kg"
              step={0.5}
              decimals={1}
              max={50}
              value={draft.equipment.dumbbellKg}
              onChange={(v) => set({ equipment: { ...draft.equipment, dumbbellKg: v } })}
            />
            <Stepper
              label="Kettlebell"
              unit="kg"
              decimals={1}
              max={50}
              value={draft.equipment.kettlebellKg}
              onChange={(v) => set({ equipment: { ...draft.equipment, kettlebellKg: v } })}
            />
          </>
        )}
        <Button
          className="w-full"
          disabled={!dirty || !draft.name.trim()}
          onClick={async () => {
            const { id, ...changes } = draft;
            await updateProfile(id, { ...changes, name: changes.name.trim() });
            setSaved(true);
          }}
        >
          {saved && !dirty ? "Saved ✓" : "Save profile"}
        </Button>
      </Card>
    </Section>
  );
}

function ReminderSettings({ profile }: { profile: Profile }) {
  const { program } = useActive();
  const today = useToday();
  const lastBackupAt = useSetting("lastBackupAt");
  const [prefs, setPrefs] = useState<ReminderPrefs>(profile.reminders);
  const [status, setStatus] = useState<string | null>(null);

  async function save() {
    await updateProfile(profile.id, { reminders: prefs });
    if (isNative) {
      let perm = await notificationPermission();
      if (perm === "prompt") perm = await requestNotificationPermission();
      if (perm !== "granted") {
        setStatus(
          "Notifications are off for this app. Turn them on in Android Settings → Apps → Lean & Strong → Notifications.",
        );
        return;
      }
      const n = await syncReminders(
        program,
        { ...profile, reminders: prefs },
        today,
        lastBackupAt ? toIsoDate(new Date(lastBackupAt)) : null,
      );
      setStatus(`Saved. ${n} reminders are set on this phone.`);
    } else setStatus("Saved.");
  }

  return (
    <Section title="Reminders">
      <Card className="space-y-4">
        <RemindersForm program={program} value={prefs} onChange={setPrefs} platformNote={false} />
        <Button className="w-full" onClick={() => void save()}>
          Save reminders
        </Button>
        {status && <p className="text-sm text-ink">{status}</p>}
        {!isNative && (
          <Callout title="On iPhone">
            <p>
              Web apps on iPhone can't send reminders on a schedule. Instead, add your workouts to
              the Calendar app: tap the button, then open the file and choose{" "}
              <strong>Add All</strong>.
            </p>
            <Button
              variant="secondary"
              className="mt-2 w-full"
              onClick={() =>
                void shareFile(
                  "lean-strong-workouts.ics",
                  buildIcs(program, { ...profile, reminders: prefs }),
                  "text/calendar",
                  "Lean & Strong workouts",
                )
              }
            >
              Add workouts to my calendar
            </Button>
          </Callout>
        )}
      </Card>
    </Section>
  );
}

function AppSettings() {
  const theme = useSetting("themeMode") ?? "system";
  const sound = useSetting("sound") ?? true;
  const haptics = useSetting("haptics") ?? true;
  const barbell = useSetting("barbellStepKg") ?? 2.5;
  const dumbbell = useSetting("dumbbellStepKg") ?? 1;
  return (
    <Section title="App">
      <Card className="space-y-4">
        <div>
          <p className="mb-1 text-sm font-semibold text-ink">Theme</p>
          <Segmented
            label="Theme"
            value={theme}
            onChange={(v) => void setSetting("themeMode", v)}
            options={[
              { value: "system", label: "System" },
              { value: "light", label: "Light" },
              { value: "dark", label: "Dark" },
            ]}
          />
        </div>
        <div className="py-1">
          <Toggle
            label="Sounds"
            hint="Beeps at 3-2-1 on timers"
            checked={sound}
            onChange={(v) => void setSetting("sound", v)}
          />
          <Toggle
            label="Vibration"
            hint={
              isNative
                ? undefined
                : "iPhone web apps can't vibrate; sounds and the screen do the job"
            }
            checked={haptics}
            onChange={(v) => void setSetting("haptics", v)}
          />
        </div>
        <div>
          <p className="mb-1 text-sm font-semibold text-ink">
            Weight steps: barbell, machine, cable
          </p>
          <Segmented
            label="Barbell and machine weight step"
            value={barbell}
            onChange={(v) => void setSetting("barbellStepKg", v)}
            options={[1, 1.25, 2.5, 5].map((v) => ({ value: v, label: `${v} kg` }))}
          />
        </div>
        <div>
          <p className="mb-1 text-sm font-semibold text-ink">Weight steps: dumbbell, kettlebell</p>
          <Segmented
            label="Dumbbell weight step"
            value={dumbbell}
            onChange={(v) => void setSetting("dumbbellStepKg", v)}
            options={[0.5, 1, 2].map((v) => ({ value: v, label: `${v} kg` }))}
          />
        </div>
      </Card>
    </Section>
  );
}

function DangerZone({ profile, programTitle }: { profile: Profile; programTitle: string }) {
  const navigate = useNavigate();
  const lastBackupAt = useSetting("lastBackupAt");
  const [typed, setTyped] = useState("");
  const [open, setOpen] = useState(false);
  return (
    <Section title="Danger zone">
      <Card className="space-y-3 ring-[#b42318]/30">
        {!open ? (
          <Button variant="secondary" className="w-full" onClick={() => setOpen(true)}>
            Reset this profile…
          </Button>
        ) : (
          <>
            <p className="text-ink">
              This deletes <strong>{profile.name}</strong> ({programTitle}) and all of their
              workouts, weigh-ins and measurements from this phone. It can't be undone.
            </p>
            <p className="text-sm text-muted">
              Last backup: {lastBackupAt ? new Date(lastBackupAt).toLocaleDateString() : "never"}.{" "}
              <Link to="/more/backup" className="font-semibold text-accent-ink">
                Back up first
              </Link>
            </p>
            <Field label={`Type ${profile.name} to confirm`}>
              <input
                className={inputClass}
                value={typed}
                onChange={(e) => setTyped(e.target.value)}
                autoComplete="off"
              />
            </Field>
            <Button
              variant="danger"
              className="w-full"
              disabled={typed.trim() !== profile.name}
              onClick={async () => {
                await deleteProfileData(profile.id);
                const next = await db.profiles.orderBy("createdAt").first();
                if (next) await setSetting("activeProfileId", next.id);
                else await db.settings.delete("activeProfileId");
                navigate("/", { replace: true });
              }}
            >
              Delete {profile.name}'s data
            </Button>
            <Button variant="ghost" className="w-full" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </>
        )}
      </Card>
    </Section>
  );
}
