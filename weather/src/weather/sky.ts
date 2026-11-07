import type { Forecast } from "@/data/types";
import type { SkyTheme } from "@/components/Sky";
import { condition } from "./codes";
import { today } from "./insights";
import { nowSec } from "@/lib/time";

/** The background for a forecast: what the sky is doing and whether it's day, night or golden hour. */
export function skyTheme(f: Forecast | undefined): SkyTheme {
  if (!f) return { kind: "clear", phase: "day", intensity: 0 };
  const now = nowSec();
  const c = condition(f.current.code);
  const d = today(f, now);
  const golden = Math.abs(now - d.sunrise) < 45 * 60 || Math.abs(now - d.sunset) < 45 * 60;
  const phase = golden ? "golden" : f.current.isDay ? "day" : "night";
  const kind = c.sky === "clear" && f.current.cloud > 60 ? "partly" : c.sky;
  return { kind, phase, intensity: c.intensity };
}
