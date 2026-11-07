import type { Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const fixture = (name: string) =>
  readFileSync(fileURLToPath(new URL(`./fixtures/${name}`, import.meta.url)), "utf8");

/** The moment the fixtures were captured: Saturday 7 Nov 2026, 13:00 in Cape Town. */
export const FIXTURE_TIME = new Date(1794050400 * 1000);

/** Serves every network request from fixtures so tests are fast, offline and repeatable. */
export async function mockNetwork(page: Page, opts: { warnings?: boolean } = {}) {
  const json = (body: string) => ({ status: 200, contentType: "application/json", body });
  await page.route("https://api.open-meteo.com/**", (r) =>
    r.fulfill(json(fixture("forecast.json"))),
  );
  await page.route("https://air-quality-api.open-meteo.com/**", (r) =>
    r.fulfill(json(fixture("air.json"))),
  );
  await page.route("https://marine-api.open-meteo.com/**", (r) =>
    r.fulfill(json(fixture("marine.json"))),
  );
  await page.route("https://geocoding-api.open-meteo.com/**", (r) =>
    r.fulfill(json(fixture("search.json"))),
  );
  await page.route("https://api.bigdatacloud.net/**", (r) =>
    r.fulfill(json(fixture("reverse.json"))),
  );
  await page.route("**/saws/forecast/*.json", (r) =>
    r.fulfill(json(fixture("saws-forecast.json"))),
  );
  await page.route("**/saws/cities.json", (r) => r.fulfill(json(fixture("saws-cities.json"))));
  await page.route("**/saws/alerts.json", (r) =>
    r.fulfill(
      json(
        opts.warnings === false
          ? JSON.stringify({ fetchedAt: Date.now(), warnings: [], storms: [] })
          : fixture("saws-alerts.json"),
      ),
    ),
  );
  await page.route("https://api.rainviewer.com/**", (r) =>
    r.fulfill(
      json(
        JSON.stringify({
          host: "https://tilecache.rainviewer.com",
          radar: { past: [], nowcast: [] },
        }),
      ),
    ),
  );
  await page.route("https://view.eumetsat.int/**", (r) =>
    r.request().url().includes("GetCapabilities")
      ? r.fulfill({
          status: 200,
          contentType: "text/xml",
          body: '<Dimension name="time" units="ISO8601">2022-12-09T00:45:00.000Z/2026-11-07T10:45:00.000Z/PT15M</Dimension>',
        })
      : r.fulfill({ status: 204, body: "" }),
  );
  // Map tiles and radar images: an empty response is enough.
  await page.route(/server\.arcgisonline\.com|tilecache\.rainviewer\.com/, (r) =>
    r.fulfill({ status: 204, body: "" }),
  );
}

export async function setup(page: Page, opts: { warnings?: boolean } = {}) {
  await page.clock.setFixedTime(FIXTURE_TIME);
  await mockNetwork(page, opts);
}
