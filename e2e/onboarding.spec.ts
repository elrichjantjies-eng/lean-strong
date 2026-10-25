import { expect, test } from "@playwright/test";
import { onboard, setToday, shot, watchConsole } from "./helpers";

test("gym program: onboarding, catch-up and quick add", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await setToday(page, "2026-11-04T09:00:00"); // a Wednesday
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "92.4",
    goalKg: "85",
    screenshots: testInfo,
  });

  await expect(page.getByText("Week 1 of 12 · Phase 1 · Rebuild")).toBeVisible();
  // Monday's Day A wasn't done, so it's offered first; today's Day B is still there.
  await expect(page.getByText("Catch up · missed Monday")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Day A" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Day B" })).toBeVisible();
  await shot(page, testInfo, "m3-today-gym");
  await shot(page, testInfo, "m3-today-gym-full", true);

  // Quick-add weight in two taps (prefilled from the start weight).
  await page.getByRole("button", { name: /^Weight/ }).click();
  await expect(page.getByRole("textbox", { name: "Weight" })).toHaveValue("92.4");
  await page.getByRole("button", { name: "Decrease Weight" }).click();
  await shot(page, testInfo, "m3-quick-add");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("button", { name: /^Weight\s*92\.3/ })).toBeVisible();

  // Data survives a reload.
  await page.reload();
  await expect(page.getByRole("button", { name: /^Weight\s*92\.3/ })).toBeVisible();
  expect(errors).toEqual([]);
});

test("home program: pace guard, walk day and equipment", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await setToday(page, "2026-11-03T08:00:00"); // a Tuesday
  await page.goto("./");
  await page.getByRole("button", { name: "Get started" }).click();
  await page.getByRole("button", { name: /9-week home program/ }).click();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByLabel("Your name").fill("Alex");
  await page.getByRole("button", { name: "Continue" }).click();
  // Default start date is next Monday.
  await expect(page.getByLabel("Start date")).toHaveValue("2026-11-09");
  await page.getByLabel("Start date").fill("2026-11-02");
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("textbox", { name: "Current weight" }).fill("80");
  await page.getByRole("textbox", { name: "Goal weight" }).fill("70");
  // 10 kg in 9 weeks is faster than 1 kg/week: the healthy-pace guard explains it.
  await expect(page.getByText("A healthy pace")).toBeVisible();
  await expect(page.getByText(/around\s+week 10/)).toBeVisible();
  await expect(page.getByText("Why a steady 1 kg a week")).toBeVisible();
  await shot(page, testInfo, "m3-pace-guard", true);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Skip" }).click();
  await expect(page.getByRole("textbox", { name: "Dumbbells (each)" })).toHaveValue("5");
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("textbox", { name: "Calories per day" }).fill("1000");
  await expect(page.getByText("Know your floor")).toBeVisible();
  await shot(page, testInfo, "m3-calorie-floor");
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByText("Sit-break reminders")).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Let's go" }).click();

  await expect(page.getByText("Week 1 of 9 · Phase 1")).toBeVisible();
  await expect(page.getByText("45 min walk")).toBeVisible();
  await expect(page.getByText("Catch up · missed Monday")).toBeVisible();
  await shot(page, testInfo, "m3-today-home");
  expect(errors).toEqual([]);
});

test("before the start date: countdown and checklist", async ({ page }, testInfo) => {
  await setToday(page, "2026-10-29T09:00:00"); // Thursday
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });
  await expect(page.getByText("Your program starts in")).toBeVisible();
  await expect(page.getByText("4", { exact: true })).toBeVisible();
  await expect(page.getByText("Do your first weigh-in")).toBeVisible();
  await shot(page, testInfo, "m3-before-start");
});
