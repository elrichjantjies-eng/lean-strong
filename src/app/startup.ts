import { useEffect } from "react";
import type { Program } from "@/content/types";
import { getSetting, setSetting } from "@/db/settings";
import { toIsoDate } from "@/domain/dates";
import type { Profile } from "@/domain/model";
import { isNative } from "@/platform";
import { syncReminders } from "@/platform/notifications";

/** One-off and per-profile tasks once a profile is ready. */
export function useStartupTasks(profile: Profile | undefined, program: Program | undefined) {
  // Web: ask the browser not to evict our data (once).
  useEffect(() => {
    if (isNative || !profile) return;
    void (async () => {
      if (await getSetting("storagePersistAsked")) return;
      await navigator.storage?.persist?.().catch(() => false);
      await setSetting("storagePersistAsked", true);
    })();
  }, [profile]);

  // Android: keep scheduled reminders in step with the profile (dates move on, settings change).
  useEffect(() => {
    if (!isNative || !profile || !program) return;
    void (async () => {
      const last = await getSetting("lastBackupAt");
      await syncReminders(program, profile, toIsoDate(), last ? toIsoDate(new Date(last)) : null);
    })();
  }, [profile, program]);
}
