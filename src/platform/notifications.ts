/** Android local notifications for reminders. A no-op on the web (iPhone uses the .ics file). */
import { LocalNotifications, type LocalNotificationSchema } from "@capacitor/local-notifications";
import type { Program } from "@/content/types";
import type { IsoDate, Profile } from "@/domain/model";
import {
  capacitorWeekday,
  REMINDER_ID_MAX,
  REMINDER_ID_MIN,
  reminderPlan,
} from "@/domain/reminders";
import { isNative } from "./index";

export type PermissionState = "granted" | "denied" | "prompt" | "unsupported";

export async function notificationPermission(): Promise<PermissionState> {
  if (!isNative) return "unsupported";
  const p = await LocalNotifications.checkPermissions();
  return p.display === "granted" ? "granted" : p.display === "denied" ? "denied" : "prompt";
}

/** Ask for permission (Android 13+ shows a system prompt). */
export async function requestNotificationPermission(): Promise<PermissionState> {
  if (!isNative) return "unsupported";
  const p = await LocalNotifications.requestPermissions();
  return p.display === "granted" ? "granted" : "denied";
}

function toLocalDate(date: IsoDate, hour: number, minute: number): Date {
  const [y, m, d] = date.split("-").map(Number) as [number, number, number];
  return new Date(y, m - 1, d, hour, minute);
}

/** Replace all scheduled reminders with the plan for this profile. */
export async function syncReminders(
  program: Program,
  profile: Profile,
  today: IsoDate,
  lastBackupDate: IsoDate | null,
): Promise<number> {
  if (!isNative) return 0;
  if ((await notificationPermission()) !== "granted") return 0;

  const pending = await LocalNotifications.getPending();
  const ours = pending.notifications.filter(
    (n) => n.id >= REMINDER_ID_MIN && n.id <= REMINDER_ID_MAX,
  );
  if (ours.length)
    await LocalNotifications.cancel({ notifications: ours.map((n) => ({ id: n.id })) });

  const notifications: LocalNotificationSchema[] = reminderPlan(
    program,
    profile,
    today,
    lastBackupDate,
  ).map((r) => ({
    id: r.id,
    title: r.title,
    body: r.body,
    schedule: r.weekly
      ? {
          on: {
            weekday: capacitorWeekday(r.weekly.weekday),
            hour: r.weekly.hour,
            minute: r.weekly.minute,
          },
          allowWhileIdle: true,
        }
      : { at: toLocalDate(r.at!.date, r.at!.hour, r.at!.minute), allowWhileIdle: true },
  }));
  if (notifications.length) await LocalNotifications.schedule({ notifications });
  return notifications.length;
}
