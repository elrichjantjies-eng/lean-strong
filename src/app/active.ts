import { createContext, useContext, useEffect, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { getProgram, programs } from "@/content/loader";
import type { Program } from "@/content/types";
import { db } from "@/db/db";
import { getSetting, useSetting } from "@/db/settings";
import { toIsoDate } from "@/domain/dates";
import type { IsoDate, Profile } from "@/domain/model";
import { applyTheme, type ColorMode } from "@/theme/theme";

export type ActiveState =
  | { status: "loading" }
  | { status: "none" }
  | { status: "ready"; profile: Profile; program: Program };

/** The active profile (falls back to the oldest profile if the setting is missing). */
export function useActiveState(): ActiveState {
  const profile = useLiveQuery(async () => {
    const id = await getSetting("activeProfileId");
    const byId = id ? await db.profiles.get(id) : undefined;
    return byId ?? (await db.profiles.orderBy("createdAt").first()) ?? null;
  });
  if (profile === undefined) return { status: "loading" };
  if (profile === null || !programs[profile.programId]) return { status: "none" };
  return { status: "ready", profile, program: getProgram(profile.programId) };
}

export const ActiveContext = createContext<{ profile: Profile; program: Program } | null>(null);

/** Use inside screens that require a profile. */
export function useActive(): { profile: Profile; program: Program } {
  const value = useContext(ActiveContext);
  if (!value) throw new Error("useActive outside ActiveProvider");
  return value;
}

function useSystemDark(): boolean {
  const query = "(prefers-color-scheme: dark)";
  const [dark, setDark] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mq = window.matchMedia(query);
    const on = () => setDark(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return dark;
}

/** Applies the active program's theme (or the first program's) in the chosen colour mode. */
export function useAppTheme(program: Program | undefined) {
  const mode = useSetting("themeMode") ?? "system";
  const systemDark = useSystemDark();
  const resolved: ColorMode = mode === "system" ? (systemDark ? "dark" : "light") : mode;
  const theme = (program ?? Object.values(programs)[0]!).theme;
  useEffect(() => applyTheme(theme, resolved), [theme, resolved]);
}

/** Today's local date, refreshed when the app comes back to the foreground or at midnight. */
export function useToday(): IsoDate {
  const [today, setToday] = useState(toIsoDate);
  useEffect(() => {
    const refresh = () => setToday(toIsoDate());
    const timer = window.setInterval(refresh, 60_000);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, []);
  return today;
}
