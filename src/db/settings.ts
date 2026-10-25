import { useLiveQuery } from "dexie-react-hooks";
import { db } from "./db";

/** Typed keys stored in the `settings` table. */
export interface SettingsMap {
  activeProfileId: string;
  lastBackupAt: number;
  themeMode: "system" | "light" | "dark";
  sound: boolean;
  haptics: boolean;
  barbellStepKg: number;
  dumbbellStepKg: number;
  storagePersistAsked: boolean;
  /** Per-profile flags, keyed `${profileId}:${flag}`. */
  flags: Record<string, boolean>;
  inProgressWorkout: unknown;
}

export const SETTING_DEFAULTS: { [K in keyof SettingsMap]?: SettingsMap[K] } = {
  themeMode: "system",
  sound: true,
  haptics: true,
  barbellStepKg: 2.5,
  dumbbellStepKg: 1,
  flags: {},
};

export async function getSetting<K extends keyof SettingsMap>(
  key: K,
): Promise<SettingsMap[K] | undefined> {
  const row = await db.settings.get(key);
  return (row?.value as SettingsMap[K] | undefined) ?? SETTING_DEFAULTS[key];
}

export async function setSetting<K extends keyof SettingsMap>(key: K, value: SettingsMap[K]) {
  await db.settings.put({ key, value });
}

export async function deleteSetting(key: keyof SettingsMap) {
  await db.settings.delete(key);
}

/** Live value of a setting (undefined while loading or unset without a default). */
export function useSetting<K extends keyof SettingsMap>(key: K): SettingsMap[K] | undefined {
  return useLiveQuery(() => getSetting(key), [key]);
}

export async function setFlag(profileId: string, flag: string, value = true) {
  const flags = (await getSetting("flags")) ?? {};
  await setSetting("flags", { ...flags, [`${profileId}:${flag}`]: value });
}

export function useFlag(profileId: string | undefined, flag: string): boolean {
  const flags = useSetting("flags");
  return !!profileId && !!flags?.[`${profileId}:${flag}`];
}
