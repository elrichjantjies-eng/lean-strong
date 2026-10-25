/**
 * Workout-player state. The whole snapshot is written to the `settings` table after every
 * action, so a workout survives the app being closed or the phone restarting.
 */
import { create } from "zustand";
import { db, newId } from "@/db/db";
import { deleteSetting, getSetting, setSetting } from "@/db/settings";
import type { IsoDate, SessionLog } from "@/domain/model";
import { completedSessionIds } from "@/domain/schedule";
import type { TimerState } from "@/features/timers/engine";
import { indexOfKey, type Mods, type Step } from "./steps";

export interface WorkoutSnapshot {
  version: 1;
  sessionLogId: string;
  profileId: string;
  programId: string;
  sessionId: string;
  week: number;
  phase: number;
  date: IsoDate;
  startedAt: number;
  mods: Mods;
  stepKey: string;
  stepIndex: number;
  /** Timer of the current step (or sub-step); reset when the step changes. */
  timer: TimerState | null;
  /** Small per-step counters, e.g. "waist.round", "cooldown.index", "finisher.choice". */
  sub: Record<string, number>;
  /** exerciseId → the "make it harder" option chosen today. */
  variations: Record<string, string>;
  /** step key → setLog id, so a set can be edited or re-done. */
  logged: Record<string, string>;
  notes: string;
}

interface PlayerStore {
  snap: WorkoutSnapshot | null;
  /** The workout that was just completed, for the summary screen. */
  finished: { snap: WorkoutSnapshot; at: number } | null;
  load: () => Promise<WorkoutSnapshot | null>;
  begin: (input: {
    profileId: string;
    programId: string;
    sessionId: string;
    week: number;
    phase: number;
    date: IsoDate;
    firstKey: string;
  }) => Promise<WorkoutSnapshot>;
  update: (fn: (s: WorkoutSnapshot) => WorkoutSnapshot) => void;
  goTo: (steps: Step[], index: number) => void;
  next: (steps: Step[]) => void;
  back: (steps: Step[]) => void;
  setTimer: (timer: TimerState | null) => void;
  setSub: (key: string, value: number, resetTimer?: boolean) => void;
  finish: () => Promise<void>;
  abandon: () => Promise<void>;
  clear: () => void;
}

function persist(snap: WorkoutSnapshot | null) {
  void (snap ? setSetting("inProgressWorkout", snap) : deleteSetting("inProgressWorkout"));
}

export const usePlayer = create<PlayerStore>((set, get) => ({
  snap: null,
  finished: null,

  async load() {
    set({ finished: null });
    const raw = (await getSetting("inProgressWorkout")) as WorkoutSnapshot | undefined;
    if (!raw || raw.version !== 1) return null;
    const log = await db.sessionLogs.get(raw.sessionLogId);
    if (!log || log.status !== "in-progress") {
      persist(null);
      return null;
    }
    set({ snap: raw });
    return raw;
  },

  async begin(input) {
    const now = Date.now();
    const log: SessionLog = {
      id: newId(),
      profileId: input.profileId,
      programId: input.programId,
      sessionId: input.sessionId,
      week: input.week,
      phase: input.phase,
      date: input.date,
      startedAt: now,
      status: "in-progress",
      isExtra: false,
      notes: "",
    };
    await db.sessionLogs.add(log);
    const snap: WorkoutSnapshot = {
      version: 1,
      sessionLogId: log.id,
      profileId: input.profileId,
      programId: input.programId,
      sessionId: input.sessionId,
      week: input.week,
      phase: input.phase,
      date: input.date,
      startedAt: now,
      mods: {},
      stepKey: input.firstKey,
      stepIndex: 1,
      timer: null,
      sub: {},
      variations: {},
      logged: {},
      notes: "",
    };
    set({ snap });
    persist(snap);
    return snap;
  },

  update(fn) {
    const snap = get().snap;
    if (!snap) return;
    const next = fn(snap);
    set({ snap: next });
    persist(next);
  },

  goTo(steps, index) {
    const i = Math.max(0, Math.min(steps.length - 1, index));
    get().update((s) => ({ ...s, stepKey: steps[i]!.key, stepIndex: i, timer: null }));
  },

  next(steps) {
    const s = get().snap;
    if (!s) return;
    get().goTo(steps, indexOfKey(steps, s.stepKey, s.stepIndex) + 1);
  },

  back(steps) {
    const s = get().snap;
    if (!s) return;
    get().goTo(steps, indexOfKey(steps, s.stepKey, s.stepIndex) - 1);
  },

  setTimer(timer) {
    get().update((s) => ({ ...s, timer }));
  },

  setSub(key, value, resetTimer = true) {
    get().update((s) => ({
      ...s,
      sub: { ...s.sub, [key]: value },
      ...(resetTimer ? { timer: null } : {}),
    }));
  },

  /** Mark the session complete. A second completion of the same session in a week is "extra". */
  async finish() {
    const s = get().snap;
    if (!s) return;
    const at = Date.now();
    set({ snap: null, finished: { snap: s, at } });
    persist(null);
    const weekLogs = await db.sessionLogs.where({ profileId: s.profileId, week: s.week }).toArray();
    const done = completedSessionIds(
      weekLogs.filter((l) => l.id !== s.sessionLogId),
      s.week,
    );
    await db.sessionLogs.update(s.sessionLogId, {
      status: "completed",
      finishedAt: at,
      isExtra: done.has(s.sessionId),
      notes: s.notes,
    });
  },

  /** Stop without completing: the logged sets are kept, the session is marked skipped. */
  async abandon() {
    const s = get().snap;
    if (!s) return;
    await db.sessionLogs.update(s.sessionLogId, { status: "skipped", notes: s.notes });
    set({ snap: null });
    persist(null);
  },

  clear() {
    set({ snap: null });
  },
}));
