import { expect, test, type Page } from "@playwright/test";
import { onboard, setToday, shot, watchConsole } from "./helpers";

async function logWeight(page: Page, date: string, kg: string) {
  await page.getByRole("button", { name: "Log weight" }).click();
  await page.getByLabel("Date").fill(date);
  await page.getByRole("textbox", { name: "Weight", exact: true }).fill(kg);
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

test("log weight for 3 weeks and see the check-in", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await setToday(page, "2026-11-15T08:00:00"); // Sunday of week 2
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90.6",
    goalKg: "84",
  });
  await page.goto("./#/progress?tab=weight");

  // Week 1 average 90.0, week 2 average 89.4.
  for (const [date, kg] of [
    ["2026-11-02", "90.2"],
    ["2026-11-04", "90.0"],
    ["2026-11-06", "89.8"],
    ["2026-11-09", "89.6"],
    ["2026-11-11", "89.4"],
    ["2026-11-13", "89.2"],
  ] as const) {
    await logWeight(page, date, kg);
  }
  // "My average" is the third column of the weekly table.
  const avg = (week: number) =>
    page
      .locator("tbody tr")
      .nth(week - 1)
      .locator("td")
      .nth(2);
  await expect(avg(1)).toHaveText("90.0");
  await expect(avg(2)).toHaveText("89.4");
  await shot(page, testInfo, "m6-weight", true);

  // Week 3 begins: the check-in is ready on Today and in Progress.
  await setToday(page, "2026-11-16T08:00:00");
  await page.goto("./#/");
  await expect(page.getByText("Your weekly check-in is ready.")).toBeVisible();
  await page.getByText("Your weekly check-in is ready.").click();
  await expect(page.getByText("Week 3 check-in · On track")).toBeVisible();
  await expect(page.getByText("−0.60 kg/week")).toBeVisible();
  await expect(page.getByText(/perfect, change nothing/)).toBeVisible();
  await shot(page, testInfo, "m6-checkin", true);

  // Viewing it stores it, so the nudge goes away.
  await page.goto("./#/");
  await expect(page.getByText("Week 1 of 12")).toHaveCount(0);
  await expect(page.getByText("Your weekly check-in is ready.")).toHaveCount(0);
  expect(errors).toEqual([]);
});

test("check-in waits for enough data", async ({ page }) => {
  await setToday(page, "2026-11-16T08:00:00"); // week 3
  await onboard(page, {
    program: "home",
    name: "Alex",
    startDate: "2026-11-02",
    startKg: "80",
    goalKg: "72",
  });
  await page.goto("./#/progress?tab=checkin");
  await expect(
    page.getByText("Log your weight for another week to unlock your check-in."),
  ).toBeVisible();
});

test("measurements, steps, wins and tests", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await setToday(page, "2026-11-10T08:00:00");
  await onboard(page, {
    program: "home",
    name: "Alex",
    startDate: "2026-11-02",
    startKg: "80",
    goalKg: "72",
  });

  await page.goto("./#/progress?tab=measurements");
  await expect(page.getByText("Measurements due")).toBeVisible();
  await page.getByRole("button", { name: "Log measurements" }).click();
  await page.getByLabel("Date").fill("2026-11-03");
  await page.getByRole("textbox", { name: "Waist at navel (cm)" }).fill("92");
  await page.getByRole("textbox", { name: "Energy (1–10)" }).fill("6");
  await page.getByRole("button", { name: "Save" }).click();
  await page.getByRole("button", { name: "Log measurements" }).click();
  await page.getByRole("textbox", { name: "Waist at navel (cm)" }).fill("90.5");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("-1.5 cm since you started")).toBeVisible();
  await shot(page, testInfo, "m6-measurements", true);

  await page.getByRole("tab", { name: "Steps" }).click();
  await page.getByRole("button", { name: "Log steps" }).click();
  await page.getByRole("textbox", { name: "Steps" }).fill("9500");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("9,500").first()).toBeVisible();
  await shot(page, testInfo, "m6-steps", true);

  await page.getByRole("tab", { name: "Wins" }).click();
  await page
    .getByRole("button", { name: /not yet/ })
    .first()
    .click();
  await page.getByRole("button", { name: "Mark as achieved" }).click();
  await expect(page.getByText(/1 of \d+ so far/)).toBeVisible();
  await shot(page, testInfo, "m6-wins");

  await page.getByRole("tab", { name: "Fitness tests" }).click();
  await page.getByRole("button", { name: "Do the Push-ups in a row (knee or full) test" }).click();
  await page.getByRole("textbox", { name: /Result/ }).fill("14");
  await page.getByRole("button", { name: "Save result" }).click();
  await expect(page.getByRole("cell", { name: "14", exact: true })).toBeVisible();
  await shot(page, testInfo, "m6-tests");
  expect(errors).toEqual([]);
});
