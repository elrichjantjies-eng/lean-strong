import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { DEFAULT_REMINDERS } from "@/db/repo";
import type { Profile } from "./model";
import { buildIcs, capacitorWeekday, firstOnOrAfter, programEnd, reminderPlan } from "./reminders";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");

const profile = (over: Partial<Profile> = {}): Profile => ({
  id: "p1",
  name: "Sam",
  programId: "gym-12wk",
  startDate: "2026-11-04", // a Wednesday
  startKg: 90,
  goalKg: 85,
  equipment: {},
  reminders: { ...DEFAULT_REMINDERS, workoutTime: "18:30" },
  createdAt: 1,
  ...over,
});

describe("dates", () => {
  it("finds the first matching weekday on or after a date", () => {
    expect(firstOnOrAfter("2026-11-04", 2)).toBe("2026-11-04"); // Wed itself
    expect(firstOnOrAfter("2026-11-04", 0)).toBe("2026-11-09"); // next Mon
    expect(programEnd(gym, "2026-11-04")).toBe("2027-01-26");
  });
});

describe(".ics calendar", () => {
  const ics = buildIcs(gym, profile(), new Date("2026-10-25T12:00:00Z"));
  const lines = ics.split("\r\n");

  it("is a valid calendar with CRLF line endings", () => {
    expect(lines[0]).toBe("BEGIN:VCALENDAR");
    expect(ics.endsWith("END:VCALENDAR\r\n")).toBe(true);
    expect(lines.every((l) => new TextEncoder().encode(l).length <= 75)).toBe(true);
  });

  it("repeats each workout day weekly at the chosen time until the program ends", () => {
    expect(ics).toContain("UID:workout-A-p1@lean-strong");
    expect(ics).toContain("DTSTART:20261109T183000"); // first Monday on/after the start
    expect(ics).toContain("RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20270126T235959");
    expect(ics).toContain("RRULE:FREQ=WEEKLY;BYDAY=SA;UNTIL=20270126T235959"); // Home day
    expect(ics).toContain("SUMMARY:Day A · Chest & Back");
  });

  it("adds weigh-ins on the suggested days and check-ins every reviewEveryWeeks", () => {
    expect(ics).toContain("RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR,SU;UNTIL=20270126T235959");
    expect(ics).toContain("DTSTART:20261118T073000"); // start + 14 days = week 3
    expect(ics).toContain("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20270126T235959");
    const homeIcs = buildIcs(home, profile({ programId: "home-9wk" }));
    expect(homeIcs).toContain("RRULE:FREQ=DAILY"); // home weighs in every morning
  });

  it("leaves out what's switched off and escapes text", () => {
    const quiet = buildIcs(
      gym,
      profile({ reminders: { ...DEFAULT_REMINDERS, workouts: false, weighIn: false } }),
    );
    expect(quiet).not.toContain("UID:workout-");
    expect(quiet).not.toContain("UID:weigh-in");
    expect(ics).toContain("X-WR-CALNAME:Lean & Strong · Sam");
    expect(buildIcs(gym, profile({ name: "A, B; C" }))).toContain("Lean & Strong · A\\, B\\; C");
  });
});

describe("Android reminder plan", () => {
  it("schedules workouts weekly and one-off measurement and check-in days", () => {
    const plan = reminderPlan(gym, profile({ startDate: "2026-11-02" }), "2026-11-02", null);
    const workouts = plan.filter((r) => r.id >= 100 && r.id < 200);
    expect(workouts.map((r) => r.weekly?.weekday)).toEqual([0, 2, 4, 5]);
    expect(workouts[0]).toMatchObject({ title: "Day A today", weekly: { hour: 18, minute: 30 } });
    const checkins = plan.filter((r) => r.id >= 400 && r.id < 500).map((r) => r.at?.date);
    expect(checkins).toEqual([
      "2026-11-16",
      "2026-11-30",
      "2026-12-14",
      "2026-12-28",
      "2027-01-11",
    ]);
    const measures = plan.filter((r) => r.id >= 300 && r.id < 400).map((r) => r.at?.date);
    expect(measures[0]).toBe("2026-11-02");
    expect(measures).toHaveLength(6); // weeks 1, 3, 5, 7, 9, 11
  });

  it("skips dates already past and repeats the backup reminder every 14 days", () => {
    const plan = reminderPlan(
      gym,
      profile({ startDate: "2026-11-02" }),
      "2026-12-01",
      "2026-11-20",
    );
    expect(plan.some((r) => r.at && r.at.date < "2026-12-01")).toBe(false);
    const backups = plan.filter((r) => r.id >= 500 && r.id < 600).map((r) => r.at?.date);
    expect(backups.slice(0, 3)).toEqual(["2026-12-04", "2026-12-18", "2027-01-01"]);
  });

  it("sit-break reminders only for home-9wk, weekdays between work hours", () => {
    const p = profile({
      programId: "home-9wk",
      reminders: { ...DEFAULT_REMINDERS, sitBreaks: true },
    });
    const breaks = reminderPlan(home, p, "2026-11-02", null).filter((r) => r.id >= 1000);
    // 09:00–17:00, every 50 min (middle of 45–60, rounded down to 5 min).
    expect(
      breaks
        .filter((r) => r.weekly?.weekday === 0)
        .map((r) => `${r.weekly!.hour}:${r.weekly!.minute}`),
    ).toEqual(["9:50", "10:40", "11:30", "12:20", "13:10", "14:0", "14:50", "15:40", "16:30"]);
    expect(new Set(breaks.map((r) => r.weekly!.weekday))).toEqual(new Set([0, 1, 2, 3, 4]));
    expect(
      reminderPlan(
        gym,
        profile({ reminders: { ...DEFAULT_REMINDERS, sitBreaks: true } }),
        "2026-11-02",
        null,
      ).some((r) => r.id >= 1000),
    ).toBe(false);
  });

  it("ids are unique and in range", () => {
    const p = profile({
      programId: "home-9wk",
      reminders: { ...DEFAULT_REMINDERS, sitBreaks: true },
    });
    const ids = reminderPlan(home, p, "2026-11-02", null).map((r) => r.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(Math.max(...ids)).toBeLessThan(5000);
  });

  it("converts weekdays to Capacitor's Sunday-first numbering", () => {
    expect([0, 1, 5, 6].map(capacitorWeekday)).toEqual([2, 3, 7, 1]);
  });
});
