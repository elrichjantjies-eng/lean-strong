import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { sessionLog, setLog } from "@/test/factories";
import { backupFileName, exportBackup, importBackup, parseBackup, previewBackup } from "./backup";
import { db } from "./db";
import { DEFAULT_REMINDERS } from "./repo";

async function seed() {
  await db.profiles.add({
    id: "p1",
    name: "Sam",
    programId: "gym-12wk",
    startDate: "2026-11-02",
    startKg: 90,
    goalKg: 85,
    equipment: {},
    reminders: DEFAULT_REMINDERS,
    createdAt: 1,
  });
  await db.sessionLogs.add(sessionLog({ id: "s1", profileId: "p1" }));
  await db.setLogs.bulkAdd([
    setLog({ id: "x1", sessionLogId: "s1", weightKg: 60, reps: 10 }),
    setLog({ id: "x2", sessionLogId: "s1", setIndex: 1, weightKg: 60, reps: 9, variation: "Slow" }),
  ]);
  await db.weights.bulkAdd([
    { id: "w1", profileId: "p1", date: "2026-11-02", kg: 90 },
    { id: "w2", profileId: "p1", date: "2026-11-03", kg: 89.6 },
  ]);
  await db.measurements.add({
    id: "m1",
    profileId: "p1",
    date: "2026-11-02",
    week: 1,
    values: { waist: 95 },
  });
  await db.dailyNumbers.add({
    id: "d1",
    profileId: "p1",
    date: "2026-11-02",
    steps: 8000,
    waterL: 2,
  });
  await db.activityLogs.add({
    id: "a1",
    profileId: "p1",
    date: "2026-11-03",
    kind: "walk",
    minutes: 40,
  });
  await db.fitnessTests.add({ id: "f1", profileId: "p1", week: 1, testId: "plank", value: 45 });
  await db.wins.add({
    id: "n1",
    profileId: "p1",
    label: "Jeans fit",
    custom: true,
    achievedOn: "2026-11-10",
  });
  await db.checkins.add({
    id: "c1",
    profileId: "p1",
    week: 3,
    rate: 0.5,
    status: "on-track",
    message: "Good",
  });
  await db.settings.bulkPut([
    { key: "activeProfileId", value: "p1" },
    { key: "themeMode", value: "dark" },
    { key: "inProgressWorkout", value: { version: 1 } },
  ]);
}

async function snapshot() {
  const all = await Promise.all([
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
    db.settings.where("key").notEqual("inProgressWorkout").toArray(),
  ]);
  return all.map((rows) =>
    [...rows].sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))),
  );
}

beforeEach(async () => {
  await db.delete();
  await db.open();
});

describe("backup", () => {
  it("round-trips: export → reset → import gives identical data", async () => {
    await seed();
    const before = await snapshot();
    const text = JSON.stringify(await exportBackup());

    await db.delete();
    await db.open();
    expect(await db.profiles.count()).toBe(0);

    const parsed = parseBackup(text);
    if (!parsed.ok) throw new Error(parsed.error);
    await importBackup(parsed.backup, "replace");
    expect(await snapshot()).toEqual(before);
  });

  it("leaves device-only settings out of the file", async () => {
    await seed();
    const b = await exportBackup();
    expect(b.data.settings.map((s) => s.key).sort()).toEqual(["activeProfileId", "themeMode"]);
  });

  it("merging the same backup twice changes nothing", async () => {
    await seed();
    const b = await exportBackup();
    await importBackup(b, "merge");
    await importBackup(b, "merge");
    expect(await db.setLogs.count()).toBe(2);
    expect(await db.weights.count()).toBe(2);
  });

  it("merge keeps this device's settings and adds new records", async () => {
    await seed();
    const b = await exportBackup();
    await db.settings.put({ key: "themeMode", value: "light" });
    await db.weights.clear();
    await importBackup(b, "merge");
    expect((await db.settings.get("themeMode"))?.value).toBe("light");
    expect(await db.weights.count()).toBe(2);
  });

  it("previews what a backup contains", async () => {
    await seed();
    const p = previewBackup(await exportBackup());
    expect(p.profiles).toEqual([{ name: "Sam", programId: "gym-12wk" }]);
    expect(p.counts).toEqual({ workouts: 1, sets: 2, weighIns: 2, measurements: 1, photos: 0 });
  });

  it("rejects files that aren't valid backups", () => {
    expect(parseBackup("not json")).toMatchObject({ ok: false });
    expect(parseBackup('{"hello":1}')).toEqual({
      ok: false,
      error: "This file isn't a Lean & Strong backup.",
    });
    expect(parseBackup('{"app":"lean-strong","formatVersion":99}')).toMatchObject({
      ok: false,
      error: "This backup is damaged or from a newer version of the app.",
    });
  });

  it("names the file by date", () => {
    expect(backupFileName(new Date("2026-11-20T10:00:00Z"))).toBe(
      "lean-strong-backup-2026-11-20.json",
    );
  });
});
