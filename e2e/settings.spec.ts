import { readFileSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { onboard, setToday, shot, watchConsole } from "./helpers";

test.beforeEach(async ({ page }) => {
  // Use the download path rather than a native share sheet the test can't tap.
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "canShare", { value: undefined, configurable: true });
  });
});

test("export, reset, import: the backup round trip", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await setToday(page, "2026-11-10T08:00:00");
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });

  // Some data: a weigh-in and steps.
  await page.getByRole("button", { name: /^Weight/ }).click();
  await page.getByRole("textbox", { name: "Weight" }).fill("89.2");
  await page.getByRole("button", { name: "Save" }).click();
  await page.getByRole("button", { name: /^Steps/ }).click();
  await page.getByRole("textbox", { name: "Steps" }).fill("7500");
  await page.getByRole("button", { name: "Save" }).click();

  // Export.
  await page.goto("./#/more/backup");
  await expect(page.getByText("Last backup:")).toContainText("never");
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Back up now" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("lean-strong-backup-2026-11-10.json");
  const file = testInfo.outputPath("backup.json");
  await download.saveAs(file);
  const json = JSON.parse(readFileSync(file, "utf8"));
  expect(json).toMatchObject({ app: "lean-strong", formatVersion: 1 });
  expect(json.data.weights).toHaveLength(1);
  await expect(page.getByRole("status")).toContainText("Backup saved");
  await shot(page, testInfo, "m7-backup");

  // Reset the profile (type the name to confirm).
  await page.goto("./#/more/settings");
  await page.getByRole("button", { name: "Reset this profile…" }).click();
  const del = page.getByRole("button", { name: "Delete Sam's data" });
  await expect(del).toBeDisabled();
  await page.getByLabel("Type Sam to confirm").fill("Sam");
  await del.click();
  await expect(page.getByRole("button", { name: "Get started" })).toBeVisible(); // back to onboarding

  // Import: onboarding a throwaway profile, then restore over it.
  await onboard(page, {
    program: "home",
    name: "Temp",
    startDate: "2026-11-02",
    startKg: "70",
    goalKg: "65",
  });
  await page.goto("./#/more/backup");
  await page.getByLabel("Choose a backup file").setInputFiles(file);
  await expect(page.getByText("• Sam · 12-week fat-loss strength program")).toBeVisible();
  await expect(page.getByText(/1 weigh-in\b/)).toBeVisible();
  await shot(page, testInfo, "m7-restore-preview");
  await page.getByRole("button", { name: "Replace everything on this phone…" }).click();
  await page.getByRole("button", { name: "Yes, replace everything" }).click();
  await expect(page.getByRole("status")).toContainText("Restored");

  await page.goto("./#/");
  await expect(page.getByRole("heading", { name: "Hi Sam" })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Weight\s*89\.2/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Steps\s*7,500/ })).toBeVisible();
  expect(errors).toEqual([]);
});

test("rejects a file that isn't a backup", async ({ page }) => {
  await setToday(page, "2026-11-10T08:00:00");
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });
  await page.goto("./#/more/backup");
  await page.getByLabel("Choose a backup file").setInputFiles({
    name: "bad.json",
    mimeType: "application/json",
    buffer: Buffer.from('{"x":1}'),
  });
  await expect(page.getByText("This file isn't a Lean & Strong backup.")).toBeVisible();
});

test("settings: calendar file, theme and profile edits", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await setToday(page, "2026-11-10T08:00:00");
  await onboard(page, {
    program: "home",
    name: "Alex",
    startDate: "2026-11-02",
    startKg: "80",
    goalKg: "72",
  });
  await page.goto("./#/more/settings");
  await shot(page, testInfo, "m7-settings", true);

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Add workouts to my calendar" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("lean-strong-workouts.ics");
  const ics = readFileSync(await download.path(), "utf8");
  expect(ics).toContain("BEGIN:VCALENDAR");
  expect(ics).toContain("SUMMARY:Workout A · Legs\\, Back & Waist");

  await page.getByRole("radio", { name: "Dark" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await shot(page, testInfo, "m7-dark");
  await page.getByRole("radio", { name: "Light" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");

  await page.getByLabel("Name").fill("Alexandra");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("button", { name: "Saved ✓" })).toBeVisible();
  await page.goto("./#/");
  await expect(page.getByRole("heading", { name: "Hi Alexandra" })).toBeVisible();
  expect(errors).toEqual([]);
});
