import { readFileSync } from "node:fs";
import { expect, test, type Page } from "@playwright/test";
import type { Program } from "../src/content/types";
import { onboard, setToday, shot, watchConsole } from "./helpers";

const load = (id: string): Program =>
  JSON.parse(readFileSync(`src/content/programs/${id}.json`, "utf8")) as Program;
const gym = load("gym-12wk");
const home = load("home-9wk");

/** Wait for every <img> on the page and return the ones that failed to load. */
async function brokenImages(page: Page): Promise<string[]> {
  return page.evaluate(async () => {
    const imgs = [...document.images];
    await Promise.all(
      imgs.map((img) =>
        img.complete
          ? null
          : new Promise((resolve) => {
              img.addEventListener("load", resolve, { once: true });
              img.addEventListener("error", resolve, { once: true });
            }),
      ),
    );
    return imgs.filter((img) => img.naturalWidth === 0).map((img) => img.src);
  });
}

async function noUnfilledPlaceholders(page: Page) {
  const text = await page.locator("main").innerText();
  expect(text).not.toMatch(/\{[a-zA-Z]+\}/);
  expect(text).not.toContain("**");
}

const programs = [
  { key: "gym" as const, data: gym, name: "Sam" },
  { key: "home" as const, data: home, name: "Alex" },
];

for (const p of programs) {
  test(`${p.key}: every guide page, session page and exercise renders`, async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    const errors = watchConsole(page);
    await setToday(page, "2026-11-11T09:00:00"); // week 2
    await onboard(page, {
      program: p.key,
      name: p.name,
      startDate: "2026-11-02",
      startKg: "88",
      goalKg: "80",
    });

    for (const guide of p.data.pages) {
      await page.goto(`./#/more/guide/${guide.id}`);
      await expect(page.getByRole("heading", { name: guide.title, level: 1 })).toBeVisible();
      await noUnfilledPlaceholders(page);
      await shot(page, testInfo, `m5-guide-${p.key}-${guide.id}`, true);
    }

    for (const id of Object.keys(p.data.sessions)) {
      await page.goto(`./#/plan/session/${id}`);
      await expect(page.getByText("Coach's notes")).toBeVisible();
      await noUnfilledPlaceholders(page);
      if (id === "A") await shot(page, testInfo, `m5-session-${p.key}-A`, true);
    }

    for (const ex of Object.values(p.data.exercises)) {
      await page.goto(`./#/exercises/${ex.id}`);
      await expect(page.getByRole("heading", { name: ex.name, level: 1 })).toBeVisible();
      expect(await brokenImages(page), ex.id).toEqual([]);
      const stepsToggle = page.getByRole("button", { name: "Steps" });
      if (await stepsToggle.count()) {
        await stepsToggle.click();
        expect(await brokenImages(page), `${ex.id} frames`).toEqual([]);
      }
    }
    expect(errors).toEqual([]);
  });
}

test("plan calendar, library and exercise detail", async ({ page }, testInfo) => {
  await setToday(page, "2026-11-11T09:00:00");
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Plan" }).click();
  await expect(page.getByRole("heading", { name: "Your plan" })).toBeVisible();
  await expect(page.getByLabel("Week 2, phase 1")).toBeVisible();
  await shot(page, testInfo, "m5-plan");
  await page.getByRole("button", { name: /Monday 2026-11-09: Day A/ }).click();
  await expect(page.getByRole("button", { name: "Log this workout" })).toBeVisible();
  await shot(page, testInfo, "m5-plan-day");
  await page.getByRole("button", { name: "Close" }).click();

  await page
    .getByRole("navigation", { name: "Main" })
    .getByRole("link", { name: "Exercises" })
    .click();
  await page.getByRole("searchbox", { name: "Search exercises" }).fill("squat");
  await expect(page.getByRole("link", { name: /Barbell Back Squat/ })).toBeVisible();
  await page.getByRole("searchbox", { name: "Search exercises" }).fill("");
  await shot(page, testInfo, "m5-library");
  await page.getByRole("link", { name: /Barbell Bench Press/ }).click();
  await expect(page.getByText("Where it's used")).toBeVisible();
  await expect(page.getByText("Day A · A1")).toBeVisible();
  await shot(page, testInfo, "m5-exercise", true);
});
