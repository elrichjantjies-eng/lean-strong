import type { Theme } from "@/content/types";

export type ColorMode = "light" | "dark";

/** Darken (amount < 0) or lighten (amount > 0) a #rrggbb colour by mixing with black or white. */
export function mix(hex: string, amount: number): string {
  const n = parseInt(hex.slice(1), 16);
  const channels = [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  const target = amount < 0 ? 0 : 255;
  const t = Math.abs(amount);
  return `#${channels
    .map((c) =>
      Math.round(c + (target - c) * t)
        .toString(16)
        .padStart(2, "0"),
    )
    .join("")}`;
}

function luminance(hex: string): number {
  const n = parseInt(hex.slice(1), 16);
  const [r, g, b] = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (hi + 0.05) / (lo + 0.05);
}

/** Step `hex` towards black (dir -1) or white (dir 1) until it reaches `ratio` against `against`. */
export function ensureContrast(
  hex: string,
  against: string,
  ratio = 4.5,
  dir: 1 | -1 = -1,
): string {
  let out = hex;
  for (let t = 0.05; contrast(out, against) < ratio && t <= 1; t += 0.05) out = mix(hex, dir * t);
  return out;
}

/** Turn a program theme into the CSS variables the stylesheet reads. */
export function themeVariables(theme: Theme, mode: ColorMode): Record<string, string> {
  const shared = {
    "--app-accent": theme.accent,
    // Filled buttons use a shade of the accent that keeps white text at AA contrast.
    "--app-accent-strong": ensureContrast(theme.accent, "#ffffff"),
    "--app-on-accent": "#ffffff",
    "--app-panel": theme.panel, // illustrations always sit on the light panel
    "--app-phase-1": theme.phaseColors[0],
    "--app-phase-2": theme.phaseColors[1],
    "--app-phase-3": theme.phaseColors[2],
  };
  if (mode === "light") {
    return {
      ...shared,
      "--app-primary": theme.primary,
      "--app-on-primary": "#ffffff",
      "--app-accent-ink": ensureContrast(theme.accent, theme.accentSoft),
      "--app-accent-soft": theme.accentSoft,
      "--app-bg": "#f6f7fb",
      "--app-surface": "#ffffff",
      "--app-surface-2": "#eef1f7",
      "--app-ink": theme.ink,
      "--app-muted": "#5f6880",
      "--app-line": theme.line,
      "--app-info-soft": "#eaf0fb",
    };
  }
  return {
    ...shared,
    "--app-primary": "#22325a",
    "--app-on-primary": "#ffffff",
    "--app-accent-ink": ensureContrast(theme.accent, "#1f2a46", 4.5, 1),
    "--app-accent-soft": `color-mix(in srgb, ${theme.accent} 18%, #141c30)`,
    "--app-bg": "#0e1424",
    "--app-surface": "#172038",
    "--app-surface-2": "#1f2a46",
    "--app-ink": "#e9edf5",
    "--app-muted": "#a4adc2",
    "--app-line": "#2b3754",
    "--app-info-soft": "#1b2846",
  };
}

export function applyTheme(theme: Theme, mode: ColorMode): void {
  const root = document.documentElement;
  for (const [key, value] of Object.entries(themeVariables(theme, mode))) {
    root.style.setProperty(key, value);
  }
  root.dataset.theme = mode;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute("content", mode === "dark" ? "#0e1424" : theme.primary);
}
