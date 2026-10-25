import { describe, expect, it } from "vitest";
import { getProgram } from "@/content/loader";
import { addDays, daysBetween, nextMondayOrToday, weekdayOf } from "./dates";
import {
  completedSessionIds,
  dayPlanFor,
  isMeasurementWeek,
  phaseForWeek,
  planToday,
  programPosition,
  programWeek,
  sessionOrder,
  weekDates,
  weekStatuses,
} from "./schedule";
import { sessionLog } from "@/test/factories";

const gym = getProgram("gym-12wk");
const home = getProgram("home-9wk");
const MON = "2026-11-02"; // a Monday

describe("dates", () => {
  it("counts calendar days across month and DST boundaries", () => {
    expect(daysBetween("2026-10-24", "2026-10-26")).toBe(2); // EU DST ends 25 Oct
    expect(daysBetween("2026-12-31", "2027-01-01")).toBe(1);
    expect(addDays("2026-02-28", 1)).toBe("2026-03-01");
  });

  it("finds the next Monday, or today if it's Monday", () => {
    expect(weekdayOf(MON)).toBe("mon");
    expect(nextMondayOrToday(MON)).toBe(MON);
    expect(nextMondayOrToday("2026-10-25")).toBe("2026-10-26"); // Sunday → Monday
    expect(nextMondayOrToday("2026-10-27")).toBe(MON); // Tuesday → next Monday
  });
});

describe("week and phase", () => {
  it("week at boundaries: day 0, day 6, day 7, last day, after the end", () => {
    expect(programWeek(MON, MON)).toBe(1);
    expect(programWeek(MON, addDays(MON, 6))).toBe(1);
    expect(programWeek(MON, addDays(MON, 7))).toBe(2);
    expect(programWeek(MON, addDays(MON, -1))).toBeNull();

    const last = addDays(MON, gym.weeks * 7 - 1);
    expect(programPosition(gym, MON, last)).toMatchObject({ status: "active", week: 12 });
    expect(programPosition(gym, MON, addDays(last, 1))).toEqual({ status: "complete", week: 13 });
    expect(programPosition(gym, MON, addDays(MON, -3))).toEqual({ status: "before", daysUntil: 3 });
  });

  it("picks the phase whose range contains the week", () => {
    expect([1, 4, 5, 8, 9, 12].map((w) => phaseForWeek(gym, w).number)).toEqual([1, 1, 2, 2, 3, 3]);
    expect([1, 3, 4, 6, 7, 9].map((w) => phaseForWeek(home, w).number)).toEqual([1, 1, 2, 2, 3, 3]);
    expect(programPosition(gym, MON, addDays(MON, 28))).toMatchObject({ week: 5, p: 1 });
  });

  it("keys the day plan by the real weekday, even when the start isn't a Monday", () => {
    const wed = "2026-11-04";
    expect(programWeek(wed, wed)).toBe(1);
    expect(dayPlanFor(gym, wed).sessionId).toBe("B");
    expect(dayPlanFor(gym, "2026-11-07").sessionId).toBe("H"); // Saturday
    expect(dayPlanFor(home, "2026-11-08").kind).toBe("rest"); // Sunday
    expect(weekDates(wed, 1)).toEqual([
      "2026-11-04",
      "2026-11-05",
      "2026-11-06",
      "2026-11-07",
      "2026-11-08",
      "2026-11-09",
      "2026-11-10",
    ]);
  });

  it("orders sessions by the workout days in weekTemplate", () => {
    expect(sessionOrder(gym)).toEqual(["A", "B", "C", "H"]);
    expect(sessionOrder(home)).toEqual(["A", "B", "C"]);
  });

  it("schedules measurements in week 1 and every everyWeeks after", () => {
    expect([1, 2, 3, 4, 5].map((w) => isMeasurementWeek(gym, w))).toEqual([
      true,
      false,
      true,
      false,
      true,
    ]);
    expect([1, 2, 3].map((w) => isMeasurementWeek(home, w))).toEqual([true, true, true]);
  });
});

describe("catch-up", () => {
  it("offers a missed earlier session first, keeping today's session", () => {
    const plan = planToday(gym, MON, "2026-11-04", new Set());
    expect(plan.todaySessionId).toBe("B");
    expect(plan.catchUp).toEqual({ sessionId: "A", missedDate: MON, missedWeekday: "mon" });
  });

  it("offers catch-up on a walk day too", () => {
    expect(planToday(gym, MON, "2026-11-03", new Set()).catchUp?.sessionId).toBe("A");
  });

  it("follows the order: with A done, a missed B comes next", () => {
    const plan = planToday(gym, MON, "2026-11-06", new Set(["A"]));
    expect(plan.todaySessionId).toBe("C");
    expect(plan.catchUp?.sessionId).toBe("B");
  });

  it("no catch-up when everything earlier is done or it's the session's own day", () => {
    expect(planToday(gym, MON, MON, new Set()).catchUp).toBeUndefined();
    expect(planToday(gym, MON, "2026-11-04", new Set(["A"])).catchUp).toBeUndefined();
    const done = planToday(gym, MON, "2026-11-04", new Set(["A", "B"]));
    expect(done.todayDone).toBe(true);
  });

  it("no catch-up for a session whose day comes later in a week that started midweek", () => {
    // Started Wednesday: Monday (A) is day 5 of program week 1, not missed yet.
    expect(planToday(gym, "2026-11-04", "2026-11-04", new Set()).catchUp).toBeUndefined();
  });

  it("counts only completed, non-extra sessions of that week", () => {
    const logs = [
      sessionLog({ sessionId: "A", week: 1 }),
      sessionLog({ sessionId: "B", week: 1, status: "in-progress" }),
      sessionLog({ sessionId: "C", week: 1, isExtra: true }),
      sessionLog({ sessionId: "H", week: 2 }),
    ];
    expect([...completedSessionIds(logs, 1)]).toEqual(["A"]);
  });

  it("gives each day of the week a status", () => {
    const days = weekStatuses(gym, MON, 1, "2026-11-04", new Set(["A"]), new Set());
    expect(days.map((d) => d.status)).toEqual([
      "done", // Mon A
      "missed", // Tue walk
      "today", // Wed B
      "upcoming",
      "upcoming",
      "upcoming",
      "rest",
    ]);
  });
});
