/** 5.1 Dates, weeks and phases, plus the Today/catch-up rules from 4.2 and 5.2. */
import type { DayPlan, Phase, Program, Weekday } from "@/content/types";
import { addDays, daysBetween, weekdayIndex, weekdayOf } from "./dates";
import type { IsoDate, SessionLog } from "./model";

export type ProgramPosition =
  | { status: "before"; daysUntil: number }
  | { status: "active"; week: number; phase: Phase; p: number; dayOfWeek: number }
  | { status: "complete"; week: number };

/** `week = floor(daysBetween(startDate, today) / 7) + 1`; null before the start. */
export function programWeek(startDate: IsoDate, date: IsoDate): number | null {
  const days = daysBetween(startDate, date);
  return days < 0 ? null : Math.floor(days / 7) + 1;
}

export function phaseForWeek(program: Program, week: number): Phase {
  const phase = program.phases.find((ph) => ph.weeks[0] <= week && week <= ph.weeks[1]);
  if (phase) return phase;
  // Outside the program (e.g. logging after the end): clamp to the first or last phase.
  return week < 1 ? program.phases[0]! : program.phases[program.phases.length - 1]!;
}

export function programPosition(
  program: Program,
  startDate: IsoDate,
  today: IsoDate,
): ProgramPosition {
  const days = daysBetween(startDate, today);
  if (days < 0) return { status: "before", daysUntil: -days };
  const week = Math.floor(days / 7) + 1;
  if (week > program.weeks) return { status: "complete", week };
  const phase = phaseForWeek(program, week);
  return { status: "active", week, phase, p: phase.number - 1, dayOfWeek: days % 7 };
}

/** The plan for a calendar date comes from the real weekday (Monday first). */
export function dayPlanFor(program: Program, date: IsoDate): DayPlan {
  return program.weekTemplate[weekdayIndex(date)]!;
}

/** The 7 dates of program week `week` (which starts on the start date's weekday). */
export function weekDates(startDate: IsoDate, week: number): IsoDate[] {
  const first = addDays(startDate, (week - 1) * 7);
  return Array.from({ length: 7 }, (_, i) => addDays(first, i));
}

/** Session order within a week: the order of workout days in weekTemplate. */
export function sessionOrder(program: Program): string[] {
  const order: string[] = [];
  for (const day of program.weekTemplate) {
    if (day.kind === "workout" && day.sessionId && !order.includes(day.sessionId)) {
      order.push(day.sessionId);
    }
  }
  return order;
}

/** Sessions completed (not extra) in program week `week`. */
export function completedSessionIds(logs: SessionLog[], week: number): Set<string> {
  return new Set(
    logs
      .filter((l) => l.week === week && l.status === "completed" && !l.isExtra)
      .map((l) => l.sessionId),
  );
}

export interface TodayPlan {
  date: IsoDate;
  day: DayPlan;
  /** Today's own session, if today is a workout day. */
  todaySessionId?: string;
  todayDone: boolean;
  /** The next session in order, when its day has already passed this week. */
  catchUp?: { sessionId: string; missedDate: IsoDate; missedWeekday: Weekday };
}

/**
 * What to show on Today. The plan says to do the next session in order, so if an earlier
 * session this program week was missed it's offered first as a catch-up.
 */
export function planToday(
  program: Program,
  startDate: IsoDate,
  today: IsoDate,
  completed: Set<string>,
): TodayPlan {
  const day = dayPlanFor(program, today);
  const week = programWeek(startDate, today);
  const plan: TodayPlan = {
    date: today,
    day,
    todaySessionId: day.kind === "workout" ? day.sessionId : undefined,
    todayDone: day.kind === "workout" && !!day.sessionId && completed.has(day.sessionId),
  };
  if (week === null) return plan;

  const next = sessionOrder(program).find((id) => !completed.has(id));
  if (!next) return plan;
  const scheduled = weekDates(startDate, week).find(
    (d) => dayPlanFor(program, d).sessionId === next,
  );
  if (scheduled && daysBetween(scheduled, today) > 0) {
    plan.catchUp = { sessionId: next, missedDate: scheduled, missedWeekday: weekdayOf(scheduled) };
  }
  return plan;
}

export type DayStatus = "done" | "missed" | "today" | "upcoming" | "rest";

export interface WeekDay {
  date: IsoDate;
  day: DayPlan;
  status: DayStatus;
}

/** Status of each day in a program week, for the week strip and the calendar. */
export function weekStatuses(
  program: Program,
  startDate: IsoDate,
  week: number,
  today: IsoDate,
  completed: Set<string>,
  activityDates: Set<IsoDate>,
): WeekDay[] {
  return weekDates(startDate, week).map((date) => {
    const day = dayPlanFor(program, date);
    const offset = daysBetween(today, date);
    const done =
      day.kind === "workout"
        ? !!day.sessionId && completed.has(day.sessionId)
        : activityDates.has(date);
    let status: DayStatus;
    if (done) status = "done";
    else if (offset === 0) status = "today";
    else if (day.kind === "rest") status = "rest";
    else if (offset > 0) status = "upcoming";
    else status = day.walk?.optional ? "rest" : "missed";
    return { date, day, status };
  });
}

/** Measurements are due in week 1 and then every `everyWeeks` weeks. */
export function isMeasurementWeek(program: Program, week: number): boolean {
  return (week - 1) % program.tracking.measurements.everyWeeks === 0;
}
