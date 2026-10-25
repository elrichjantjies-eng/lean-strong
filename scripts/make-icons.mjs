/**
 * Renders the original "LS" monogram (accent on navy) to every icon the app needs, using the
 * bundled Poppins font so the result is the same on every machine.
 *
 *   node scripts/make-icons.mjs
 *
 * Writes PWA icons to public/icons/ and, when android/ exists, Android launcher icons and the
 * splash image into android/app/src/main/res/.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { chromium } from "@playwright/test";

const NAVY = "#1c2a4a";
const ACCENT = "#f0642d";
const font = readFileSync(
  "node_modules/@fontsource/poppins/files/poppins-latin-700-normal.woff2",
).toString("base64");

/** The monogram on a 512 canvas. `scale` shrinks it into the maskable safe zone. */
function svg({ rounded, scale = 1, background = true }) {
  const t = (256 * (1 - scale)).toFixed(1);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs><style>@font-face{font-family:P;src:url(data:font/woff2;base64,${font}) format("woff2");font-weight:700}</style></defs>
  ${background ? `<rect width="512" height="512" rx="${rounded ? 112 : 0}" fill="${NAVY}"/>` : ""}
  <g transform="translate(${t} ${t}) scale(${scale})">
    <text x="256" y="298" text-anchor="middle" font-family="P" font-weight="700" font-size="212" letter-spacing="-6" fill="${ACCENT}">LS</text>
    <g fill="#ffffff">
      <rect x="150" y="346" width="212" height="16" rx="8"/>
      <rect x="128" y="322" width="26" height="64" rx="8"/>
      <rect x="358" y="322" width="26" height="64" rx="8"/>
      <rect x="110" y="334" width="16" height="40" rx="6"/>
      <rect x="386" y="334" width="16" height="40" rx="6"/>
    </g>
  </g>
</svg>`;
}

const outputs = [
  // PWA / web
  { file: "public/icons/icon-192.png", size: 192, svg: svg({ rounded: true }), transparent: true },
  { file: "public/icons/icon-512.png", size: 512, svg: svg({ rounded: true }), transparent: true },
  { file: "public/icons/maskable-192.png", size: 192, svg: svg({ rounded: false, scale: 0.78 }) },
  { file: "public/icons/maskable-512.png", size: 512, svg: svg({ rounded: false, scale: 0.78 }) },
  {
    file: "public/icons/apple-touch-icon-180.png",
    size: 180,
    svg: svg({ rounded: false, scale: 0.9 }),
  },
  { file: "public/favicon-32.png", size: 32, svg: svg({ rounded: true }), transparent: true },
];

// Android launcher icons (adaptive: navy background colour + transparent foreground layer).
const RES = "android/app/src/main/res";
if (existsSync(RES)) {
  const densities = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
  for (const [d, k] of Object.entries(densities)) {
    outputs.push(
      {
        file: `${RES}/mipmap-${d}/ic_launcher.png`,
        size: 48 * k,
        svg: svg({ rounded: true }),
        transparent: true,
      },
      {
        file: `${RES}/mipmap-${d}/ic_launcher_round.png`,
        size: 48 * k,
        svg: svg({ rounded: true }).replace('rx="112"', 'rx="256"'),
        transparent: true,
      },
      // Foreground is 108 dp with the visible area the middle 72 dp: scale 0.62 keeps it inside.
      {
        file: `${RES}/mipmap-${d}/ic_launcher_foreground.png`,
        size: 108 * k,
        svg: svg({ rounded: false, scale: 0.62, background: false }),
        transparent: true,
      },
    );
  }
  outputs.push({
    file: `${RES}/drawable/splash_logo.png`,
    size: 288,
    svg: svg({ rounded: true }),
    transparent: true,
  });
}

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
for (const o of outputs) {
  await page.setViewportSize({ width: o.size, height: o.size });
  await page.setContent(
    `<html><body style="margin:0;background:transparent">${o.svg.replace('width="512" height="512"', `width="${o.size}" height="${o.size}"`)}</body></html>`,
  );
  await page.evaluate(() => document.fonts.ready);
  mkdirSync(dirname(o.file), { recursive: true });
  const png = await page.screenshot({
    omitBackground: !!o.transparent,
    clip: { x: 0, y: 0, width: o.size, height: o.size },
  });
  writeFileSync(o.file, png);
  console.log("wrote", o.file);
}
writeFileSync(
  join("public", "favicon.svg"),
  svg({ rounded: true })
    .replace(/<defs>.*?<\/defs>/s, "")
    .replace('font-family="P"', 'font-family="Poppins, Arial, sans-serif"'),
);
await browser.close();
