/** Renders a shared weather icon (src/icons/defs.ts) to an SVG string, for build scripts. */
import { ICONS, gradientsUsed } from "../src/icons/defs.ts";

export function iconSvg(key, { size = 64, prefix = key } = {}) {
  const def = ICONS[key];
  const paint = (p) => p.replace(/^url\(#(\w+)\)$/, `url(#${prefix}-$1)`);
  const grads = gradientsUsed(def)
    .map(
      (g) =>
        `<linearGradient id="${prefix}-${g.id}" gradientUnits="userSpaceOnUse" x1="${g.x1}" y1="${g.y1}" x2="${g.x2}" y2="${g.y2}"><stop offset="0" stop-color="${g.from}"/><stop offset="1" stop-color="${g.to}"/></linearGradient>`,
    )
    .join("");
  const shapes = def.shapes
    .map((s) => {
      if (s.kind === "circle")
        return `<circle cx="${s.cx}" cy="${s.cy}" r="${s.r}" fill="${paint(s.fill)}"/>`;
      if (s.kind === "rect")
        return `<rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" rx="${s.r}" fill="${paint(s.fill)}"/>`;
      if (s.kind === "line")
        return `<line x1="${s.x1}" y1="${s.y1}" x2="${s.x2}" y2="${s.y2}" stroke="${paint(s.stroke)}" stroke-width="${s.width}" stroke-linecap="round"/>`;
      return `<path d="${s.d}" fill="${paint(s.fill)}"/>`;
    })
    .join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="${size}" height="${size}"><defs>${grads}</defs>${shapes}</svg>`;
}

export const ICON_KEYS = Object.keys(ICONS);
