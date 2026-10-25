import Dexie, { type EntityTable } from "dexie";
import type {
  ActivityLog,
  Checkin,
  DailyNumbers,
  FitnessTestResult,
  MeasurementEntry,
  Photo,
  Profile,
  SessionLog,
  SetLog,
  Setting,
  WeightEntry,
  Win,
} from "@/domain/model";

export class LeanStrongDB extends Dexie {
  profiles!: EntityTable<Profile, "id">;
  sessionLogs!: EntityTable<SessionLog, "id">;
  setLogs!: EntityTable<SetLog, "id">;
  activityLogs!: EntityTable<ActivityLog, "id">;
  weights!: EntityTable<WeightEntry, "id">;
  measurements!: EntityTable<MeasurementEntry, "id">;
  dailyNumbers!: EntityTable<DailyNumbers, "id">;
  fitnessTests!: EntityTable<FitnessTestResult, "id">;
  wins!: EntityTable<Win, "id">;
  checkins!: EntityTable<Checkin, "id">;
  photos!: EntityTable<Photo, "id">;
  settings!: EntityTable<Setting, "key">;

  constructor(name = "lean-strong") {
    super(name);
    // Schema version 1. Add new versions below; never edit this one after release.
    this.version(1).stores({
      profiles: "id, createdAt",
      sessionLogs: "id, profileId, [profileId+date], [profileId+week], [profileId+status]",
      setLogs: "id, sessionLogId, profileId, [profileId+exerciseId]",
      activityLogs: "id, profileId, [profileId+date], sessionLogId",
      weights: "id, profileId, &[profileId+date]",
      measurements: "id, profileId, [profileId+date], [profileId+week]",
      dailyNumbers: "id, profileId, &[profileId+date]",
      fitnessTests: "id, profileId, [profileId+week]",
      wins: "id, profileId",
      checkins: "id, profileId, &[profileId+week]",
      photos: "id, profileId, [profileId+date]",
      settings: "key",
    });
  }
}

export const db = new LeanStrongDB();

/** All profile-scoped tables, for backup and reset. */
export const PROFILE_TABLES = [
  "sessionLogs",
  "setLogs",
  "activityLogs",
  "weights",
  "measurements",
  "dailyNumbers",
  "fitnessTests",
  "wins",
  "checkins",
  "photos",
] as const;

export function newId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
