import { useRef, useState } from "react";
import { programs } from "@/content/loader";
import {
  backupFileName,
  exportBackup,
  importBackup,
  parseBackup,
  previewBackup,
  type BackupFile,
} from "@/db/backup";
import { db } from "@/db/db";
import { setSetting, useSetting } from "@/db/settings";
import { shareFile } from "@/platform/files";
import { Screen } from "@/components/Screen";
import { Button, Callout, Card } from "@/components/ui";

const DAY = 86_400_000;
const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

export function BackupScreen() {
  const lastBackupAt = useSetting("lastBackupAt");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState<BackupFile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmReplace, setConfirmReplace] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const [now] = useState(() => Date.now());
  const overdue = !lastBackupAt || now - lastBackupAt > 14 * DAY;

  async function doExport() {
    setBusy(true);
    setMessage(null);
    try {
      const backup = await exportBackup({ includePhotos: true });
      const outcome = await shareFile(
        backupFileName(),
        JSON.stringify(backup),
        "application/json",
        "Lean & Strong backup",
      );
      if (outcome !== "cancelled") {
        await setSetting("lastBackupAt", backup.exportedAt);
        setMessage(
          outcome === "downloaded"
            ? "Backup saved to your Downloads. Keep a copy somewhere safe, like Google Drive or iCloud."
            : "Backup shared. Keep it somewhere safe.",
        );
      }
    } finally {
      setBusy(false);
    }
  }

  async function pick(file: File) {
    setError(null);
    setPending(null);
    const result = parseBackup(await file.text());
    if (!result.ok) setError(result.error);
    else setPending(result.backup);
  }

  async function restore(mode: "merge" | "replace") {
    if (!pending) return;
    setBusy(true);
    await importBackup(pending, mode);
    if (mode === "replace" || !(await db.settings.get("activeProfileId"))) {
      const first = pending.data.profiles[0];
      if (first) await setSetting("activeProfileId", first.id);
    }
    setBusy(false);
    setPending(null);
    setConfirmReplace(false);
    setMessage(
      mode === "replace"
        ? "Restored. This phone now has exactly what was in the backup."
        : "Merged into this phone's data.",
    );
  }

  const preview = pending ? previewBackup(pending) : null;

  return (
    <Screen title="Backup & restore" back="/more">
      <div className="space-y-5">
        <Callout
          tone={overdue ? "accent" : "info"}
          title={overdue ? "Time for a backup" : "Your data is backed up"}
        >
          <p>
            Last backup:{" "}
            <strong>{lastBackupAt ? new Date(lastBackupAt).toLocaleDateString() : "never"}</strong>.
          </p>
          <p>
            Your history lives only on this phone. Back up every couple of weeks, and always before
            changing phones.
          </p>
        </Callout>

        <Card className="space-y-3">
          <h2 className="font-bold text-ink">Back up</h2>
          <p className="text-sm text-muted">
            Saves one file with everything for every profile on this phone. Send it to yourself, or
            save it to Drive, iCloud or Files.
          </p>
          <Button size="lg" className="w-full" disabled={busy} onClick={() => void doExport()}>
            Back up now
          </Button>
        </Card>

        <Card className="space-y-3">
          <h2 className="font-bold text-ink">Restore</h2>
          <p className="text-sm text-muted">
            Pick a Lean & Strong backup file. You'll see what's in it before anything changes.
          </p>
          <input
            ref={fileInput}
            type="file"
            accept="application/json,.json"
            aria-label="Choose a backup file"
            className="sr-only"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void pick(f);
              e.target.value = "";
            }}
          />
          <Button
            variant="secondary"
            size="lg"
            className="w-full"
            onClick={() => fileInput.current?.click()}
          >
            Choose a backup file
          </Button>
          {error && <p className="font-semibold text-[#b42318]">{error}</p>}
          {preview && (
            <div className="space-y-3 rounded-xl bg-surface-2 p-3">
              <p className="font-semibold text-ink">
                Backup from {new Date(preview.exportedAt).toLocaleString()}
              </p>
              <ul className="text-sm text-ink">
                {preview.profiles.map((p) => (
                  <li key={p.name}>
                    • {p.name} · {programs[p.programId]?.subtitle ?? p.programId}
                  </li>
                ))}
              </ul>
              <p className="text-sm text-muted">
                {[
                  plural(preview.counts.workouts, "workout"),
                  plural(preview.counts.sets, "set"),
                  plural(preview.counts.weighIns, "weigh-in"),
                  plural(preview.counts.measurements, "measurement"),
                  ...(preview.counts.photos ? [plural(preview.counts.photos, "photo")] : []),
                ].join(" · ")}
              </p>
              <Button className="w-full" disabled={busy} onClick={() => void restore("merge")}>
                Merge with this phone's data
              </Button>
              {!confirmReplace ? (
                <Button
                  variant="secondary"
                  className="w-full"
                  onClick={() => setConfirmReplace(true)}
                >
                  Replace everything on this phone…
                </Button>
              ) : (
                <div className="space-y-2">
                  <p className="text-sm text-ink">
                    This deletes everything on this phone first and puts the backup in its place.
                    {overdue &&
                      " You haven't backed up this phone recently: consider backing up first."}
                  </p>
                  <Button
                    variant="danger"
                    className="w-full"
                    disabled={busy}
                    onClick={() => void restore("replace")}
                  >
                    Yes, replace everything
                  </Button>
                </div>
              )}
            </div>
          )}
        </Card>
        {message && (
          <p role="status" className="rounded-xl bg-info-soft p-3 text-ink">
            {message}
          </p>
        )}
      </div>
    </Screen>
  );
}
