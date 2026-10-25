/**
 * Record shapes stored on the device (section 6 of the brief). Dates are local calendar dates
 * as "YYYY-MM-DD" strings; timestamps are epoch milliseconds.
 */

export type IsoDate = string;

export interface ReminderPrefs {
  workoutTime: string; // "HH:MM"
  workouts: boolean;
  weighIn: boolean;
  measurements: boolean;
  checkin: boolean;
  backup: boolean;
  sitBreaks: boolean;
  sitBreakStart: string; // "HH:MM"
  sitBreakEnd: string;
}

export interface Profile {
  id: string;
  name: string;
  programId: string;
  startDate: IsoDate;
  startKg: number;
  goalKg: number;
  heightCm?: number;
  calorieTarget?: number;
  equipment: { dumbbellKg?: number; kettlebellKg?: number };
  reminders: ReminderPrefs;
  createdAt: number;
}

export type SessionStatus = "in-progress" | "completed" | "skipped";

export interface SessionLog {
  id: string;
  profileId: string;
  programId: string;
  sessionId: string;
  week: number;
  phase: number;
  date: IsoDate;
  startedAt: number;
  finishedAt?: number;
  status: SessionStatus;
  isExtra: boolean;
  notes: string;
}

export type BlockType = "pair" | "finisher" | "circuit";

export interface SetLog {
  id: string;
  sessionLogId: string;
  profileId: string;
  exerciseId: string;
  blockType: BlockType;
  pairCode: string;
  setIndex: number;
  weightKg?: number;
  reps?: number;
  seconds?: number;
  perSide: boolean;
  isWarmup: boolean;
  variation?: string;
  completedAt: number;
}

export interface ActivityLog {
  id: string;
  profileId: string;
  date: IsoDate;
  kind: "walk" | "finisher" | "circuit" | "warmup";
  minutes?: number;
  rounds?: number;
  distanceKm?: number;
  sessionLogId?: string;
}

export interface WeightEntry {
  id: string;
  profileId: string;
  date: IsoDate;
  kg: number;
}

export interface MeasurementEntry {
  id: string;
  profileId: string;
  date: IsoDate;
  week: number;
  values: Record<string, number>;
}

export interface DailyNumbers {
  id: string;
  profileId: string;
  date: IsoDate;
  steps?: number;
  waterL?: number;
  calories?: number;
  proteinG?: number;
}

export interface FitnessTestResult {
  id: string;
  profileId: string;
  week: number;
  testId: string;
  value: number;
}

export interface Win {
  id: string;
  profileId: string;
  label: string;
  custom: boolean;
  achievedOn?: IsoDate;
  note?: string;
}

export interface Checkin {
  id: string;
  profileId: string;
  week: number;
  rate: number | null;
  status: string;
  message: string;
}

export interface Photo {
  id: string;
  profileId: string;
  date: IsoDate;
  blob: Blob;
}

export interface Setting {
  key: string;
  value: unknown;
}
