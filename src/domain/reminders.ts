/**
 * What to remind about, and when. Used two ways: an .ics calendar file for iPhone (web apps
 * can't schedule notifications) and local notifications on Android.
 */
import type { Program } from "@/content/types";
import { addDays, weekdayIndex, WEEKDAYS } from "./dates";
import type { IsoDate, Profile } from "./model";
import { isCheckinWeek } from "./coaching";
import { isMeasurementWeek } from "./schedule";

const pad = (n: number) => String(n).padStart(2, "0");

function parseTime(hhmm: string): [number, number] {
  const [h, m] = hhmm.split(":").map(Number);
  return [h ?? 18, m ?? 0];
}

/** First date on or after `from` that falls on weekday index `wd` (0 = Monday). */
export function firstOnOrAfter(from: IsoDate, wd: number): IsoDate {
  return addDays(from, (wd - weekdayIndex(from) + 7) % 7);
}

export const programEnd = (program: Program, startDate: IsoDate) =>
  addDays(startDate, program.weeks * 7 - 1);

// ---------- .ics ----------

function icsEscape(text: string): string {
  return text
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}

const icsDate = (d: IsoDate) => d.replace(/-/g, "");
const icsDateTime = (d: IsoDate, [h, m]: [number, number]) => `${icsDate(d)}T${pad(h)}${pad(m)}00`;
const BYDAY = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];

/** Fold lines longer than 75 octets, as RFC 5545 requires. */
function fold(line: string): string {
  const out: string[] = [];
  let rest = line;
  while (new TextEncoder().encode(rest).length > 75) {
    let cut = 75;
    while (new TextEncoder().encode(rest.slice(0, cut)).length > 75) cut--;
    out.push(rest.slice(0, cut));
    rest = " " + rest.slice(cut);
  }
  out.push(rest);
  return out.join("\r\n");
}

interface IcsEvent {
  uid: string;
  start: IsoDate;
  time: [number, number];
  minutes: number;
  rrule?: string;
  summary: string;
  description?: string;
}

/**
 * A calendar file with recurring events: each workout day at the chosen time, weigh-in
 * mornings, measurement days and check-ins, for the length of the program.
 */
export function buildIcs(program: Program, profile: Profile, now: Date = new Date()): string {
  const r = profile.reminders;
  const until = `${icsDate(programEnd(program, profile.startDate))}T235959`;
  const time = parseTime(r.workoutTime);
  const events: IcsEvent[] = [];

  if (r.workouts) {
    program.weekTemplate.forEach((day, wd) => {
      if (day.kind !== "workout" || !day.sessionId) return;
      const s = program.sessions[day.sessionId]!;
      events.push({
        uid: `workout-${s.id}`,
        start: firstOnOrAfter(profile.startDate, wd),
        time,
        minutes: 60,
        rrule: `FREQ=WEEKLY;BYDAY=${BYDAY[wd]};UNTIL=${until}`,
        summary: `${s.title} · ${s.focus}`,
        description: `Lean & Strong: open the app and tap Start workout.`,
      });
    });
  }
  if (r.weighIn) {
    const days = program.tracking.weighIn.suggestedDays.map((d) => WEEKDAYS.indexOf(d));
    const first = days.map((wd) => firstOnOrAfter(profile.startDate, wd)).sort()[0]!;
    events.push({
      uid: "weigh-in",
      start: first,
      time: [7, 0],
      minutes: 10,
      rrule:
        days.length === 7
          ? `FREQ=DAILY;UNTIL=${until}`
          : `FREQ=WEEKLY;BYDAY=${days.map((d) => BYDAY[d]).join(",")};UNTIL=${until}`,
      summary: "Weigh-in",
      description: program.tracking.weighIn.text,
    });
  }
  if (r.measurements) {
    const every = program.tracking.measurements.everyWeeks;
    events.push({
      uid: "measurements",
      start: profile.startDate,
      time: [7, 15],
      minutes: 10,
      rrule: `FREQ=WEEKLY;INTERVAL=${every};UNTIL=${until}`,
      summary: "Measurements day",
      description: "Lean & Strong: log your measurements in Progress.",
    });
  }
  if (r.checkin) {
    const every = program.coaching.reviewEveryWeeks;
    events.push({
      uid: "check-in",
      start: addDays(profile.startDate, 14), // the first check-in is at the start of week 3
      time: [7, 30],
      minutes: 10,
      rrule: `FREQ=WEEKLY;INTERVAL=${every};UNTIL=${until}`,
      summary: "Weekly check-in",
      description: "Lean & Strong: your check-in is ready in Progress.",
    });
  }

  const stamp = now
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}/, "");
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Lean & Strong//Workout reminders//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    `X-WR-CALNAME:${icsEscape(`Lean & Strong · ${profile.name}`)}`,
  ];
  for (const e of events) {
    lines.push(
      "BEGIN:VEVENT",
      `UID:${e.uid}-${profile.id}@lean-strong`,
      `DTSTAMP:${stamp}`,
      `DTSTART:${icsDateTime(e.start, e.time)}`,
      `DURATION:PT${e.minutes}M`,
      ...(e.rrule ? [`RRULE:${e.rrule}`] : []),
      `SUMMARY:${icsEscape(e.summary)}`,
      ...(e.description ? [`DESCRIPTION:${icsEscape(e.description)}`] : []),
      "BEGIN:VALARM",
      "ACTION:DISPLAY",
      `DESCRIPTION:${icsEscape(e.summary)}`,
      "TRIGGER:PT0M",
      "END:VALARM",
      "END:VEVENT",
    );
  }
  lines.push("END:VCALENDAR");
  return lines.map(fold).join("\r\n") + "\r\n";
}

// ---------- Android local notifications ----------

export interface PlannedReminder {
  id: number;
  title: string;
  body: string;
  /** One-off at this local date and time… */
  at?: { date: IsoDate; hour: number; minute: number };
  /** …or repeating weekly (weekday 0 = Monday). */
  weekly?: { weekday: number; hour: number; minute: number };
}

export const REMINDER_ID_MIN = 100;
export const REMINDER_ID_MAX = 4999;
const BACKUP_EVERY_DAYS = 14;

/** Minutes since midnight → [hour, minute]. */
const hm = (mins: number): [number, number] => [Math.floor(mins / 60), mins % 60];

export function reminderPlan(
  program: Program,
  profile: Profile,
  today: IsoDate,
  lastBackupDate: IsoDate | null,
): PlannedReminder[] {
  const r = profile.reminders;
  const out: PlannedReminder[] = [];
  const end = programEnd(program, profile.startDate);
  const inRange = (d: IsoDate) => d >= today && d <= end;
  const [wh, wm] = parseTime(r.workoutTime);

  if (r.workouts) {
    program.weekTemplate.forEach((day, wd) => {
      if (day.kind !== "workout" || !day.sessionId) return;
      const s = program.sessions[day.sessionId]!;
      out.push({
        id: 100 + wd,
        title: `${s.title} today`,
        body: `${s.focus}. Tap to open your workout.`,
        weekly: { weekday: wd, hour: wh, minute: wm },
      });
    });
  }
  if (r.weighIn) {
    program.tracking.weighIn.suggestedDays.forEach((d) => {
      const wd = WEEKDAYS.indexOf(d);
      out.push({
        id: 200 + wd,
        title: "Weigh-in morning",
        body: "Step on the scale after the toilet, before breakfast.",
        weekly: { weekday: wd, hour: 7, minute: 0 },
      });
    });
  }
  for (let w = 1; w <= program.weeks; w++) {
    const date = addDays(profile.startDate, (w - 1) * 7);
    if (!inRange(date)) continue;
    if (r.measurements && isMeasurementWeek(program, w)) {
      out.push({
        id: 300 + w,
        title: "Measurements day",
        body: "Take your measurements and log them in Progress.",
        at: { date, hour: 7, minute: 15 },
      });
    }
    if (r.checkin && isCheckinWeek(program, w)) {
      out.push({
        id: 400 + w,
        title: "Your weekly check-in is ready",
        body: "See how your weekly average is moving.",
        at: { date, hour: 7, minute: 30 },
      });
    }
  }
  if (r.backup) {
    let next = addDays(lastBackupDate ?? today, BACKUP_EVERY_DAYS);
    if (next < today) next = today;
    for (let i = 0; i < 6; i++, next = addDays(next, BACKUP_EVERY_DAYS)) {
      out.push({
        id: 500 + i,
        title: "Time for a backup",
        body: "Save a copy of your workout history: More → Backup.",
        at: { date: next, hour: 19, minute: 0 },
      });
    }
  }
  const sit = program.tracking.sitBreaks;
  if (r.sitBreaks && sit) {
    // Middle of the range, rounded down to 5 min (45–60 → 50), never below the minimum.
    const mid = (sit.everyMinutes[0] + sit.everyMinutes[1]) / 2;
    const every = Math.max(sit.everyMinutes[0], Math.floor(mid / 5) * 5);
    const [sh, sm] = parseTime(r.sitBreakStart);
    const [eh, em] = parseTime(r.sitBreakEnd);
    const startMin = sh * 60 + sm;
    const endMin = eh * 60 + em;
    let n = 0;
    for (let wd = 0; wd < 5; wd++) {
      for (let t = startMin + every; t < endMin; t += every) {
        const [h, m] = hm(t);
        out.push({
          id: 1000 + n++,
          title: "Time to move",
          body: sit.text,
          weekly: { weekday: wd, hour: h, minute: m },
        });
      }
    }
  }
  return out;
}

/** Capacitor counts weekdays from Sunday = 1; ours start at Monday = 0. */
export const capacitorWeekday = (wd: number) => (wd === 6 ? 1 : wd + 2);
