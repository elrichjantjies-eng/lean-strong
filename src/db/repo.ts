/** Small write helpers. Reads happen in components via useLiveQuery. */
import type { DailyNumbers, IsoDate, Profile, ReminderPrefs } from "@/domain/model";
import { db, newId, PROFILE_TABLES } from "./db";
import { setSetting } from "./settings";

export const DEFAULT_REMINDERS: ReminderPrefs = {
  workoutTime: "18:00",
  workouts: true,
  weighIn: true,
  measurements: true,
  checkin: true,
  backup: true,
  sitBreaks: false,
  sitBreakStart: "09:00",
  sitBreakEnd: "17:00",
};

export async function createProfile(input: Omit<Profile, "id" | "createdAt">): Promise<Profile> {
  const profile: Profile = { ...input, id: newId(), createdAt: Date.now() };
  await db.profiles.add(profile);
  await setSetting("activeProfileId", profile.id);
  return profile;
}

export async function updateProfile(id: string, changes: Partial<Omit<Profile, "id">>) {
  await db.profiles.update(id, changes);
}

/** One weight per day; logging again replaces it. */
export async function logWeight(profileId: string, date: IsoDate, kg: number) {
  await db.transaction("rw", db.weights, async () => {
    const existing = await db.weights.where({ profileId, date }).first();
    if (existing) await db.weights.update(existing.id, { kg });
    else await db.weights.add({ id: newId(), profileId, date, kg });
  });
}

/** Daily totals: one row per day, fields merged (editing replaces that field). */
export async function logDaily(
  profileId: string,
  date: IsoDate,
  values: Partial<Pick<DailyNumbers, "steps" | "waterL" | "calories" | "proteinG">>,
) {
  await db.transaction("rw", db.dailyNumbers, async () => {
    const existing = await db.dailyNumbers.where({ profileId, date }).first();
    if (existing) await db.dailyNumbers.update(existing.id, values);
    else await db.dailyNumbers.add({ id: newId(), profileId, date, ...values });
  });
}

export async function logWalk(
  profileId: string,
  date: IsoDate,
  minutes: number,
  extra: { distanceKm?: number; sessionLogId?: string } = {},
) {
  await db.activityLogs.add({ id: newId(), profileId, date, kind: "walk", minutes, ...extra });
}

/** Delete everything belonging to a profile (the profile row itself too). */
export async function deleteProfileData(profileId: string, keepProfile = false) {
  await db.transaction("rw", [db.profiles, ...PROFILE_TABLES.map((t) => db[t])], async () => {
    for (const t of PROFILE_TABLES) await db[t].where("profileId").equals(profileId).delete();
    if (!keepProfile) await db.profiles.delete(profileId);
  });
}
