/**
 * Weather icon artwork, defined once as simple shapes on a 64×64 grid.
 *
 * The web app renders these as SVG (src/icons/WeatherIcon.tsx) and
 * scripts/gen-android-icons.mjs turns the very same shapes into Android vector drawables for
 * the home-screen widgets, so the app and the widgets always match.
 *
 * Keep this file free of runtime TypeScript features (enums, namespaces): Node imports it
 * directly with type stripping.
 */

export type Gradient = {
  id: string;
  from: string;
  to: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
};

export type Shape =
  | { kind: "circle"; cx: number; cy: number; r: number; fill: string }
  | { kind: "rect"; x: number; y: number; w: number; h: number; r: number; fill: string }
  | { kind: "line"; x1: number; y1: number; x2: number; y2: number; stroke: string; width: number }
  | { kind: "path"; d: string; fill: string };

export type IconDef = { shapes: Shape[] };

export type IconKey =
  | "clear-day"
  | "clear-night"
  | "partly-day"
  | "partly-night"
  | "cloudy"
  | "fog"
  | "drizzle"
  | "rain"
  | "heavy-rain"
  | "showers-day"
  | "showers-night"
  | "thunder"
  | "snow"
  | "sleet";

/** Gradients are in absolute (64×64 grid) coordinates so overlapping shapes blend seamlessly. */
export const GRADIENTS: Record<string, Gradient> = {
  sun: { id: "sun", from: "#FFE27A", to: "#FF9F0A", x1: 20, y1: 18, x2: 44, y2: 46 },
  sunSmall: { id: "sunSmall", from: "#FFE27A", to: "#FF9F0A", x1: 12, y1: 10, x2: 32, y2: 32 },
  moon: { id: "moon", from: "#FFF8D6", to: "#D9CF9A", x1: 20, y1: 14, x2: 46, y2: 48 },
  moonSmall: { id: "moonSmall", from: "#FFF8D6", to: "#D9CF9A", x1: 10, y1: 8, x2: 34, y2: 32 },
  cloud: { id: "cloud", from: "#FFFFFF", to: "#C9D6E6", x1: 24, y1: 18, x2: 40, y2: 50 },
  cloudLow: { id: "cloudLow", from: "#FFFFFF", to: "#C9D6E6", x1: 24, y1: 24, x2: 40, y2: 52 },
  cloudBack: { id: "cloudBack", from: "#D9E3EE", to: "#9FB1C6", x1: 28, y1: 12, x2: 44, y2: 36 },
  cloudDark: { id: "cloudDark", from: "#C3CEDB", to: "#7D8EA3", x1: 24, y1: 14, x2: 40, y2: 46 },
  cloudStorm: { id: "cloudStorm", from: "#A9B6C6", to: "#5C6B80", x1: 24, y1: 12, x2: 40, y2: 44 },
  rain: { id: "rain", from: "#7FD3FF", to: "#2F8CFF", x1: 0, y1: 40, x2: 0, y2: 60 },
  bolt: { id: "bolt", from: "#FFE873", to: "#FFAA00", x1: 28, y1: 34, x2: 36, y2: 62 },
};

// ---- building blocks ----------------------------------------------------------------------

/** Eight sun rays around (cx, cy), from radius r1 to r2. */
function rays(
  cx: number,
  cy: number,
  r1: number,
  r2: number,
  width: number,
  stroke: string,
): Shape[] {
  const out: Shape[] = [];
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4;
    const round = (n: number) => Math.round(n * 100) / 100;
    out.push({
      kind: "line",
      x1: round(cx + Math.cos(a) * r1),
      y1: round(cy + Math.sin(a) * r1),
      x2: round(cx + Math.cos(a) * r2),
      y2: round(cy + Math.sin(a) * r2),
      stroke,
      width,
    });
  }
  return out;
}

/** A puffy cloud built from three circles on a rounded base, with a soft shadow underneath. */
function cloud(x: number, y: number, scale: number, fill: string, shadow = true): Shape[] {
  const s = (n: number) => Math.round(n * scale * 100) / 100;
  const parts = (dx: number, dy: number, f: string): Shape[] => [
    { kind: "circle", cx: x + s(14) + dx, cy: y + s(18) + dy, r: s(9), fill: f },
    { kind: "circle", cx: x + s(26) + dx, cy: y + s(12) + dy, r: s(12), fill: f },
    { kind: "circle", cx: x + s(37) + dx, cy: y + s(19) + dy, r: s(8), fill: f },
    { kind: "rect", x: x + s(5) + dx, y: y + s(17) + dy, w: s(40), h: s(10), r: s(5), fill: f },
  ];
  return [...(shadow ? parts(0, s(2.2), "#00000022") : []), ...parts(0, 0, fill)];
}

function drops(xs: number[], y: number, len: number, slant: number, width: number): Shape[] {
  return xs.map((x) => ({
    kind: "line" as const,
    x1: x,
    y1: y,
    x2: x - slant,
    y2: y + len,
    stroke: "url(#rain)",
    width,
  }));
}

function flake(cx: number, cy: number, r: number): Shape[] {
  const out: Shape[] = [];
  for (let i = 0; i < 3; i++) {
    const a = (i * Math.PI) / 3 + Math.PI / 6;
    const dx = Math.round(Math.cos(a) * r * 100) / 100;
    const dy = Math.round(Math.sin(a) * r * 100) / 100;
    out.push({
      kind: "line",
      x1: cx - dx,
      y1: cy - dy,
      x2: cx + dx,
      y2: cy + dy,
      stroke: "#FFFFFF",
      width: 2.2,
    });
  }
  return out;
}

const bolt: Shape = {
  kind: "path",
  d: "M33 36 L25 50 L31 50 L28 62 L40 45 L33.5 45 L37 36 Z",
  fill: "url(#bolt)",
};

// Crescent: outer circle minus an offset circle, drawn as two arcs.
const moonFull: Shape = {
  kind: "path",
  d: "M37.5 13.5 A19 19 0 1 0 51.5 41 A15 15 0 0 1 37.5 13.5 Z",
  fill: "url(#moon)",
};
const moonSmall: Shape = {
  kind: "path",
  d: "M25 7 A13 13 0 1 0 34.6 25.8 A10.3 10.3 0 0 1 25 7 Z",
  fill: "url(#moonSmall)",
};

const sunFull: Shape[] = [
  { kind: "circle", cx: 32, cy: 32, r: 21, fill: "#FFB3001A" },
  ...rays(32, 32, 17.5, 24, 3.6, "#FFC23D"),
  { kind: "circle", cx: 32, cy: 32, r: 12.5, fill: "url(#sun)" },
];
const sunSmall: Shape[] = [
  ...rays(22, 21, 12.5, 17, 3, "#FFC23D"),
  { kind: "circle", cx: 22, cy: 21, r: 9, fill: "url(#sunSmall)" },
];

// ---- the icon set -------------------------------------------------------------------------

export const ICONS: Record<IconKey, IconDef> = {
  "clear-day": { shapes: sunFull },
  "clear-night": {
    shapes: [
      moonFull,
      { kind: "circle", cx: 50, cy: 14, r: 1.6, fill: "#FFFFFFCC" },
      { kind: "circle", cx: 56, cy: 25, r: 1.1, fill: "#FFFFFF99" },
      { kind: "circle", cx: 44, cy: 7, r: 1, fill: "#FFFFFF99" },
    ],
  },
  "partly-day": { shapes: [...sunSmall, ...cloud(10, 22, 1.05, "url(#cloudLow)")] },
  "partly-night": { shapes: [moonSmall, ...cloud(10, 22, 1.05, "url(#cloudLow)")] },
  cloudy: {
    shapes: [...cloud(16, 8, 0.85, "url(#cloudBack)", false), ...cloud(6, 20, 1.08, "url(#cloud)")],
  },
  fog: {
    shapes: [
      ...cloud(8, 8, 1, "url(#cloud)"),
      { kind: "line", x1: 12, y1: 46, x2: 52, y2: 46, stroke: "#E3EAF2", width: 3.4 },
      { kind: "line", x1: 18, y1: 53, x2: 46, y2: 53, stroke: "#E3EAF2B3", width: 3.4 },
    ],
  },
  drizzle: { shapes: [...cloud(8, 6, 1, "url(#cloud)"), ...drops([22, 32, 42], 44, 4, 1.5, 3)] },
  rain: { shapes: [...cloud(8, 6, 1, "url(#cloudDark)"), ...drops([21, 31, 41], 42, 9, 3, 3.2)] },
  "heavy-rain": {
    shapes: [...cloud(8, 6, 1, "url(#cloudStorm)"), ...drops([17, 25, 33, 41, 49], 42, 12, 4, 3.2)],
  },
  "showers-day": {
    shapes: [
      ...sunSmall,
      ...cloud(10, 18, 1.05, "url(#cloudLow)"),
      ...drops([25, 35, 45], 47, 8, 2.5, 3),
    ],
  },
  "showers-night": {
    shapes: [
      moonSmall,
      ...cloud(10, 18, 1.05, "url(#cloudLow)"),
      ...drops([25, 35, 45], 47, 8, 2.5, 3),
    ],
  },
  thunder: {
    shapes: [...cloud(8, 4, 1, "url(#cloudStorm)"), bolt, ...drops([18, 46], 41, 8, 2.5, 3)],
  },
  snow: {
    shapes: [
      ...cloud(8, 6, 1, "url(#cloud)"),
      ...flake(21, 48, 4.5),
      ...flake(33, 54, 4.5),
      ...flake(45, 47, 4.5),
    ],
  },
  sleet: {
    shapes: [
      ...cloud(8, 6, 1, "url(#cloudDark)"),
      ...drops([21, 41], 43, 8, 2.5, 3),
      ...flake(31, 51, 4.5),
    ],
  },
};

/** Gradient ids an icon uses (so a renderer only has to define those). */
export function gradientsUsed(def: IconDef): Gradient[] {
  const ids = new Set<string>();
  for (const s of def.shapes) {
    const paint = s.kind === "line" ? s.stroke : s.fill;
    const m = /^url\(#(\w+)\)$/.exec(paint);
    if (m) ids.add(m[1]);
  }
  return [...ids].map((id) => GRADIENTS[id]);
}
