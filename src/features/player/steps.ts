/**
 * The guided flow for one session as a flat list of steps. Keys are stable, so adding or
 * removing a set rebuilds the list without losing the user's place.
 */
import type { ResolvedSession } from "@/domain/session";

/** Per-item changes made during the workout (keyed by item code, e.g. "A1"). */
export interface ItemMods {
  sets?: number;
  exerciseId?: string;
  skipped?: string; // reason
}
export type Mods = Record<string, ItemMods>;

export type Step =
  | { key: "overview"; type: "overview" }
  | { key: "warmup-rope"; type: "rope" }
  | { key: "warmup-mobility"; type: "mobility" }
  | { key: "warmup-sets"; type: "warmupSets" }
  | {
      key: string;
      type: "set";
      pairIndex: number;
      itemIndex: 0 | 1;
      setIndex: number;
      setsTotal: number;
    }
  | { key: string; type: "switch"; pairIndex: number; itemIndex: 0 | 1; setIndex: number }
  | { key: string; type: "rest"; pairIndex: number; setIndex: number; seconds: number }
  | { key: "finisher"; type: "finisher" }
  | { key: "waist"; type: "waist" }
  | { key: "walk"; type: "walk" }
  | { key: "cooldown"; type: "cooldown" }
  | { key: "summary"; type: "summary" };

export type SetStep = Extract<Step, { type: "set" }>;

export const SWITCH_SECONDS = 15;

export const setKey = (pairIndex: number, itemIndex: number, setIndex: number) =>
  `set:${pairIndex}:${itemIndex}:${setIndex}`;

export function itemSets(
  resolved: ResolvedSession,
  mods: Mods,
  pairIndex: number,
  itemIndex: 0 | 1,
) {
  const item = resolved.pairs[pairIndex]!.items[itemIndex];
  const m = mods[item.code];
  if (m?.skipped) return 0;
  return m?.sets ?? item.sets;
}

export function buildSteps(resolved: ResolvedSession, mods: Mods): Step[] {
  const s = resolved.session;
  const steps: Step[] = [{ key: "overview", type: "overview" }];
  steps.push({ key: "warmup-rope", type: "rope" });
  if (s.warmup.mobility.length) steps.push({ key: "warmup-mobility", type: "mobility" });
  if (s.warmup.warmupSets) steps.push({ key: "warmup-sets", type: "warmupSets" });

  // Power sets: A1 set 1 → (switch) → A2 set 1 → rest → A1 set 2 → …
  const pairSteps: Step[] = [];
  resolved.pairs.forEach((_, pi) => {
    const counts = [itemSets(resolved, mods, pi, 0), itemSets(resolved, mods, pi, 1)] as const;
    const rounds = Math.max(counts[0], counts[1]);
    for (let si = 0; si < rounds; si++) {
      const inRound = ([0, 1] as const).filter((ii) => si < counts[ii]);
      inRound.forEach((ii, n) => {
        if (n > 0)
          pairSteps.push({
            key: `switch:${pi}:${si}`,
            type: "switch",
            pairIndex: pi,
            itemIndex: ii,
            setIndex: si,
          });
        pairSteps.push({
          key: setKey(pi, ii, si),
          type: "set",
          pairIndex: pi,
          itemIndex: ii,
          setIndex: si,
          setsTotal: counts[ii],
        });
      });
      pairSteps.push({
        key: `rest:${pi}:${si}`,
        type: "rest",
        pairIndex: pi,
        setIndex: si,
        seconds: resolved.restSec,
      });
    }
  });
  // No rest after the very last set: the finisher (or cool-down) comes next.
  if (pairSteps[pairSteps.length - 1]?.type === "rest") pairSteps.pop();
  steps.push(...pairSteps);

  if (resolved.finisher) steps.push({ key: "finisher", type: "finisher" });
  if (resolved.waistCircuit) steps.push({ key: "waist", type: "waist" });
  if (resolved.walkAfterMin) steps.push({ key: "walk", type: "walk" });
  if (s.cooldown.stretches.length || s.cooldown.text)
    steps.push({ key: "cooldown", type: "cooldown" });
  steps.push({ key: "summary", type: "summary" });
  return steps;
}

/** Index of `key`, or of the step that now sits where it was (after sets were removed). */
export function indexOfKey(steps: Step[], key: string, fallbackIndex = 0): number {
  const i = steps.findIndex((s) => s.key === key);
  return i >= 0 ? i : Math.min(fallbackIndex, steps.length - 1);
}
