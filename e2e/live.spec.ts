/**
 * Checks the deployed site. Runs only when LIVE_URL is set, e.g.
 *   LIVE_URL=https://elrichjantjies-eng.github.io/lean-strong/ npx playwright test e2e/live.spec.ts
 */
import { expect, test } from "@playwright/test";

const LIVE_URL = process.env.LIVE_URL;
test.skip(!LIVE_URL, "Set LIVE_URL to check the deployed site");
test.use({ baseURL: LIVE_URL });

test("the live site installs and works offline", async ({ page, context, browserName }) => {
  test.skip(browserName !== "chromium", "Service-worker checks run in Chromium");
  test.setTimeout(180_000); // the first visit downloads the whole 7.6 MB precache
  await page.goto("./");
  await expect(page.getByRole("button", { name: "Get started" })).toBeVisible();
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => true));
  await page.waitForFunction(() => !!navigator.serviceWorker.controller, undefined, {
    timeout: 60_000,
  });

  const cdp = await context.newCDPSession(page);
  const { installabilityErrors } = await cdp.send("Page.getInstallabilityErrors");
  expect(installabilityErrors).toEqual([]);

  await context.setOffline(true);
  await page.reload();
  await page.getByRole("button", { name: "Get started" }).click();
  await page.getByRole("button", { name: /12-week fat-loss/ }).click();
  await expect(page.getByRole("button", { name: "Continue" })).toBeEnabled();
  const imgOk = await page.evaluate(async () => {
    const r = await fetch("assets/exercises/gym-12wk/bench_press-loop.webp");
    return r.ok && (await r.blob()).size > 0;
  });
  expect(imgOk).toBe(true);
  await context.setOffline(false);
});
