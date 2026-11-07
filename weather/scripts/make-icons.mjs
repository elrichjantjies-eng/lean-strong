/**
 * Renders the Skyglass app icon (the sun-and-cloud weather icon on a glass tile over a blue
 * sky) to every size the web app and the Android app need.
 *
 *   node scripts/make-icons.mjs
 *
 * Writes public/icons/*, public/favicon.svg and, when android/ exists, the Android launcher
 * icons and splash image. Uses Playwright's Chromium (set CHROME_PATH to use another build).
 */
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { chromium } from "@playwright/test";
import { iconSvg } from "./icon-svg.mjs";

/** The icon on a 512 canvas. `scale` shrinks the artwork into the maskable safe zone. */
function svg({ rounded = true, scale = 1, background = true, radius } = {}) {
  const rx = radius ?? (rounded ? 112 : 0);
  const art = iconSvg("partly-day", { size: 300, prefix: "a" })
    .replace("<svg ", `<svg x="${106 + (1 - scale) * 150}" y="${118 + (1 - scale) * 150}" `)
    .replace('width="300" height="300"', `width="${300 * scale}" height="${300 * scale}"`);
  const t = (256 * (1 - scale)).toFixed(1);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stop-color="#1b4fb5"/><stop offset="1" stop-color="#6fb2f2"/></linearGradient>
    <linearGradient id="glass" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity=".34"/><stop offset="1" stop-color="#ffffff" stop-opacity=".08"/></linearGradient>
    <radialGradient id="glow" cx=".78" cy=".12" r=".7"><stop offset="0" stop-color="#fff6c8" stop-opacity=".55"/><stop offset="1" stop-color="#fff6c8" stop-opacity="0"/></radialGradient>
  </defs>
  ${background ? `<rect width="512" height="512" rx="${rx}" fill="url(#bg)"/><rect width="512" height="512" rx="${rx}" fill="url(#glow)"/>` : ""}
  <g transform="translate(${t} ${t}) scale(${scale})">
    <rect x="96" y="96" width="320" height="320" rx="76" fill="url(#glass)" stroke="#ffffff" stroke-opacity=".45" stroke-width="3"/>
    <rect x="98" y="98" width="316" height="150" rx="74" fill="#ffffff" opacity=".07"/>
  </g>
  ${art}
</svg>`;
}

const outputs = [
  ["public/icons/icon-192.png", 192, svg()],
  ["public/icons/icon-512.png", 512, svg()],
  ["public/icons/maskable-512.png", 512, svg({ rounded: false, scale: 0.78 })],
  ["public/icons/apple-touch-icon-180.png", 180, svg({ rounded: false })],
];

const res = "android/app/src/main/res";
if (existsSync(res)) {
  const densities = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
  for (const [d, k] of Object.entries(densities)) {
    outputs.push([`${res}/mipmap-${d}/ic_launcher.png`, Math.round(48 * k), svg()]);
    outputs.push([
      `${res}/mipmap-${d}/ic_launcher_round.png`,
      Math.round(48 * k),
      svg({ radius: 256 }),
    ]);
    // Adaptive icon foreground: 108dp canvas, artwork inside the 66dp safe zone.
    outputs.push([
      `${res}/mipmap-${d}/ic_launcher_foreground.png`,
      Math.round(108 * k),
      svg({ background: false, rounded: false, scale: 0.62 }),
    ]);
  }
  // Splash: the rounded icon, centred on the night-sky colour by drawable/splash.xml.
  outputs.push([`${res}/drawable-nodpi/splash_icon.png`, 432, svg()]);
}

const browser = await chromium.launch(
  process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {},
);
const page = await browser.newPage();
for (const [file, size, markup] of outputs) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(
    `<html><body style="margin:0;background:transparent">${markup.replace('width="512" height="512"', `width="${size}" height="${size}"`)}</body></html>`,
  );
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(
    file,
    await page.screenshot({
      omitBackground: true,
      clip: { x: 0, y: 0, width: size, height: size },
    }),
  );
}
await browser.close();
writeFileSync(join("public", "favicon.svg"), svg());
console.log(`${outputs.length} icons written`);
