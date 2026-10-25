/**
 * Backup and restore: one versioned JSON file with every table for every profile. Imports are
 * validated with zod before anything is written.
 */
import { z } from "zod";
import { db, PROFILE_TABLES } from "./db";

export const BACKUP_FORMAT_VERSION = 1;

const id = z.string().min(1);
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const num = z.number().finite();

const reminders = z.object({
  workoutTime: z.string(),
  workouts: z.boolean(),
  weighIn: z.boolean(),
  measurements: z.boolean(),
  checkin: z.boolean(),
  backup: z.boolean(),
  sitBreaks: z.boolean(),
  sitBreakStart: z.string(),
  sitBreakEnd: z.string(),
});

const profile = z.object({
  id,
  name: z.string(),
  programId: z.string(),
  startDate: date,
  startKg: num,
  goalKg: num,
  heightCm: num.optional(),
  calorieTarget: num.optional(),
  equipment: z.object({ dumbbellKg: num.optional(), kettlebellKg: num.optional() }),
  reminders,
  createdAt: num,
  previousProfileId: z.string().optional(),
  archived: z.boolean().optional(),
});

const sessionLog = z.object({
  id,
  profileId: id,
  programId: z.string(),
  sessionId: z.string(),
  week: num,
  phase: num,
  date,
  startedAt: num,
  finishedAt: num.optional(),
  status: z.enum(["in-progress", "completed", "skipped"]),
  isExtra: z.boolean(),
  notes: z.string(),
});

const setLog = z.object({
  id,
  sessionLogId: id,
  profileId: id,
  exerciseId: z.string(),
  blockType: z.enum(["pair", "finisher", "circuit"]),
  pairCode: z.string(),
  setIndex: num,
  weightKg: num.optional(),
  reps: num.optional(),
  seconds: num.optional(),
  perSide: z.boolean(),
  isWarmup: z.boolean(),
  variation: z.string().optional(),
  completedAt: num,
});

const activityLog = z.object({
  id,
  profileId: id,
  date,
  kind: z.enum(["walk", "finisher", "circuit", "warmup"]),
  minutes: num.optional(),
  rounds: num.optional(),
  distanceKm: num.optional(),
  sessionLogId: z.string().optional(),
});

const photo = z.object({
  id,
  profileId: id,
  date,
  /** Data URL in the file; a Blob in the database. */
  dataUrl: z.string().startsWith("data:"),
});

export const backupSchema = z.object({
  app: z.literal("lean-strong"),
  formatVersion: z.literal(BACKUP_FORMAT_VERSION),
  exportedAt: num,
  data: z.object({
    profiles: z.array(profile),
    sessionLogs: z.array(sessionLog),
    setLogs: z.array(setLog),
    activityLogs: z.array(activityLog),
    weights: z.array(z.object({ id, profileId: id, date, kg: num })),
    measurements: z.array(
      z.object({ id, profileId: id, date, week: num, values: z.record(z.string(), num) }),
    ),
    dailyNumbers: z.array(
      z.object({
        id,
        profileId: id,
        date,
        steps: num.optional(),
        waterL: num.optional(),
        calories: num.optional(),
        proteinG: num.optional(),
      }),
    ),
    fitnessTests: z.array(
      z.object({ id, profileId: id, week: num, testId: z.string(), value: num }),
    ),
    wins: z.array(
      z.object({
        id,
        profileId: id,
        label: z.string(),
        custom: z.boolean(),
        achievedOn: date.optional(),
        note: z.string().optional(),
      }),
    ),
    checkins: z.array(
      z.object({
        id,
        profileId: id,
        week: num,
        rate: num.nullable(),
        status: z.string(),
        message: z.string(),
      }),
    ),
    photos: z.array(photo).default([]),
    settings: z.array(z.object({ key: z.string(), value: z.unknown() })),
  }),
});

export type BackupFile = z.infer<typeof backupSchema>;

/** Settings that describe this device rather than the user's data. */
const LOCAL_ONLY_SETTINGS = new Set(["inProgressWorkout", "storagePersistAsked"]);

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  return (await fetch(dataUrl)).blob();
}

export async function exportBackup(options: { includePhotos?: boolean } = {}): Promise<BackupFile> {
  const [
    profiles,
    sessionLogs,
    setLogs,
    activityLogs,
    weights,
    measurements,
    dailyNumbers,
    fitnessTests,
    wins,
    checkins,
    settings,
  ] = await Promise.all([
    db.profiles.toArray(),
    db.sessionLogs.toArray(),
    db.setLogs.toArray(),
    db.activityLogs.toArray(),
    db.weights.toArray(),
    db.measurements.toArray(),
    db.dailyNumbers.toArray(),
    db.fitnessTests.toArray(),
    db.wins.toArray(),
    db.checkins.toArray(),
    db.settings.toArray(),
  ]);
  const photos = options.includePhotos
    ? await Promise.all(
        (await db.photos.toArray()).map(async (p) => ({
          id: p.id,
          profileId: p.profileId,
          date: p.date,
          dataUrl: await blobToDataUrl(p.blob),
        })),
      )
    : [];
  return {
    app: "lean-strong",
    formatVersion: BACKUP_FORMAT_VERSION,
    exportedAt: Date.now(),
    data: {
      profiles,
      sessionLogs,
      setLogs,
      activityLogs,
      weights,
      measurements,
      dailyNumbers,
      fitnessTests,
      wins,
      checkins,
      photos,
      settings: settings.filter((s) => !LOCAL_ONLY_SETTINGS.has(s.key)),
    },
  };
}

export type ParseResult = { ok: true; backup: BackupFile } | { ok: false; error: string };

/** Parse and validate a backup file's text. */
export function parseBackup(text: string): ParseResult {
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    return { ok: false, error: "This file isn't a Lean & Strong backup (it isn't valid JSON)." };
  }
  const result = backupSchema.safeParse(json);
  if (!result.success) {
    const app = (json as { app?: unknown } | null)?.app;
    return {
      ok: false,
      error:
        app !== "lean-strong"
          ? "This file isn't a Lean & Strong backup."
          : "This backup is damaged or from a newer version of the app.",
    };
  }
  return { ok: true, backup: result.data };
}

export interface BackupPreview {
  exportedAt: number;
  profiles: { name: string; programId: string }[];
  counts: {
    workouts: number;
    sets: number;
    weighIns: number;
    measurements: number;
    photos: number;
  };
}

export function previewBackup(b: BackupFile): BackupPreview {
  return {
    exportedAt: b.exportedAt,
    profiles: b.data.profiles.map((p) => ({ name: p.name, programId: p.programId })),
    counts: {
      workouts: b.data.sessionLogs.filter((l) => l.status === "completed").length,
      sets: b.data.setLogs.length,
      weighIns: b.data.weights.length,
      measurements: b.data.measurements.length,
      photos: b.data.photos.length,
    },
  };
}

/**
 * Restore a backup. "replace" wipes everything first; "merge" adds the backup's records and
 * overwrites records with the same id (so importing the same backup twice changes nothing).
 */
export async function importBackup(b: BackupFile, mode: "merge" | "replace"): Promise<void> {
  const photos = await Promise.all(
    b.data.photos.map(async (p) => ({
      id: p.id,
      profileId: p.profileId,
      date: p.date,
      blob: await dataUrlToBlob(p.dataUrl),
    })),
  );
  const tables = [db.profiles, db.settings, ...PROFILE_TABLES.map((t) => db[t])];
  await db.transaction("rw", tables, async () => {
    if (mode === "replace") {
      for (const t of tables) await t.clear();
    }
    await db.profiles.bulkPut(b.data.profiles);
    await db.sessionLogs.bulkPut(b.data.sessionLogs);
    await db.setLogs.bulkPut(b.data.setLogs);
    await db.activityLogs.bulkPut(b.data.activityLogs);
    // Weights and daily numbers are one per day: on merge, a backup row replaces the same day.
    for (const w of b.data.weights) {
      await db.weights.where({ profileId: w.profileId, date: w.date }).delete();
    }
    await db.weights.bulkPut(b.data.weights);
    for (const d of b.data.dailyNumbers) {
      await db.dailyNumbers.where({ profileId: d.profileId, date: d.date }).delete();
    }
    await db.dailyNumbers.bulkPut(b.data.dailyNumbers);
    await db.measurements.bulkPut(b.data.measurements);
    await db.fitnessTests.bulkPut(b.data.fitnessTests);
    await db.wins.bulkPut(b.data.wins);
    for (const c of b.data.checkins) {
      await db.checkins.where({ profileId: c.profileId, week: c.week }).delete();
    }
    await db.checkins.bulkPut(b.data.checkins);
    await db.photos.bulkPut(photos);
    const settings = b.data.settings.filter((s) => !LOCAL_ONLY_SETTINGS.has(s.key));
    if (mode === "replace") await db.settings.bulkPut(settings);
    else {
      // On merge keep this device's choices; only fill in what's missing.
      for (const s of settings) if (!(await db.settings.get(s.key))) await db.settings.put(s);
    }
  });
}

export function backupFileName(now = new Date()): string {
  const d = now.toISOString().slice(0, 10);
  return `lean-strong-backup-${d}.json`;
}
