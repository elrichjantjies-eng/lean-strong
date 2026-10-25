import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { expect, test, type Page } from "@playwright/test";
import { onboard, setToday, shot } from "./helpers";

const PRECACHE_LIMIT = 3 * 1024 * 1024; // matches maximumFileSizeToCacheInBytes

/** Wait until the service worker is installed (precache done) and controls the page. */
async function waitForServiceWorker(page: Page) {
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => true));
  await page.waitForFunction(() => !!navigator.serviceWorker.controller, undefined, {
    timeout: 20_000,
  });
}

test("manifest and service worker meet Chrome's installability criteria", async ({
  page,
  browserName,
}) => {
  test.skip(browserName !== "chromium", "Installability is checked with Chrome DevTools");
  await page.goto("./");
  await waitForServiceWorker(page);
  const manifest = await (await page.request.get("manifest.webmanifest")).json();
  expect(manifest).toMatchObject({
    name: "Lean & Strong",
    display: "standalone",
    start_url: "/",
    scope: "/",
  });
  expect(
    manifest.icons.map((i: { sizes: string; purpose: string }) => `${i.sizes}:${i.purpose}`),
  ).toEqual(["192x192:any", "512x512:any", "192x192:maskable", "512x512:maskable"]);
  const cdp = await page.context().newCDPSession(page);
  const { installabilityErrors } = await cdp.send("Page.getInstallabilityErrors");
  expect(installabilityErrors).toEqual([]);
});

test("works offline after the first load", async ({ page, context, browserName }) => {
  test.skip(browserName !== "chromium", "Offline service-worker test runs in Chromium");
  await setToday(page, "2026-11-04T09:00:00");
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });
  await waitForServiceWorker(page);

  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Hi Sam" })).toBeVisible();

  // Lazy screens and every exercise animation come from the precache.
  await page.goto("./#/exercises/bench_press");
  await expect(page.getByRole("heading", { name: "Barbell Bench Press" })).toBeVisible();
  const ok = await page.evaluate(async () => {
    const img = document.querySelector<HTMLImageElement>("img[alt$='demonstration']")!;
    if (!img.complete) await new Promise((r) => img.addEventListener("load", r, { once: true }));
    return img.naturalWidth > 0;
  });
  expect(ok).toBe(true);
  await page.goto("./#/workout/B");
  await expect(page.getByRole("button", { name: "Start", exact: true })).toBeVisible();
  await page.goto("./#/progress?tab=weight");
  await expect(page.getByRole("heading", { name: "Progress" })).toBeVisible();
  await shot(page, test.info(), "m8-offline");
  await context.setOffline(false);
});

test("every precached file is under the Workbox size limit", () => {
  const walk = (dir: string): string[] =>
    readdirSync(dir).flatMap((f) => {
      const p = join(dir, f);
      return statSync(p).isDirectory() ? walk(p) : [p];
    });
  const big = walk("dist").filter((f) => statSync(f).size > PRECACHE_LIMIT);
  expect(big).toEqual([]);
});

test("iPhone Safari shows the Add to Home Screen steps", async ({
  page,
  browserName,
}, testInfo) => {
  test.skip(browserName !== "webkit", "iPhone only");
  await setToday(page, "2026-11-04T09:00:00");
  await onboard(page, {
    program: "home",
    name: "Alex",
    startDate: "2026-11-02",
    startKg: "80",
    goalKg: "72",
  });
  await expect(page.getByRole("complementary", { name: "Install the app" })).toBeVisible();
  await expect(page.getByText("Add to Home Screen").first()).toBeVisible();
  await shot(page, testInfo, "m8-install-banner");
  await page.getByRole("button", { name: "Not now" }).click();
  await expect(page.getByRole("complementary", { name: "Install the app" })).toHaveCount(0);
});
