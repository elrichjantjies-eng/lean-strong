import { expect, test, type Page } from "@playwright/test";
import { onboard, shot, watchConsole } from "./helpers";

/** A running clock we can fast-forward (timers are computed from timestamps). */
async function installClock(page: Page, isoLocal: string) {
  await page.clock.install({ time: new Date(isoLocal) });
}

const doneButton = (page: Page) => page.getByRole("button", { name: "Done", exact: true });

async function startWorkout(page: Page) {
  await page.getByRole("button", { name: "Start workout" }).first().click();
  await expect(page.getByRole("button", { name: "Start", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Start", exact: true }).click();
}

async function ropeWarmup(page: Page) {
  await expect(page.getByText("Warm-up").first()).toBeVisible();
  await page.getByRole("button", { name: "Start", exact: true }).click();
  await page.clock.fastForward("10:00");
}

/** Do every power set: fill weight when empty, tap Done, let switch/rest timers run out. */
async function powerSets(
  page: Page,
  testInfo: Parameters<typeof shot>[1],
  sets: number,
  restSec: number,
) {
  for (let i = 0; i < sets; i++) {
    await expect(doneButton(page)).toBeVisible();
    const weight = page.getByRole("textbox", { name: "Weight", exact: true });
    if ((await weight.count()) && !(await weight.inputValue())) await weight.fill("20");
    if (i === 0) await shot(page, testInfo, `m4-set-${restSec}`);
    await doneButton(page).click();
    if (i === sets - 1) break;
    // Either a 15 s switch or the rest after the pair: wait for its timer, then read which.
    const timer = page.getByRole("timer");
    await expect(timer).toBeVisible();
    const isRest = (await timer.getAttribute("aria-label")) === "Rest time left";
    if (i === 1) await shot(page, testInfo, `m4-rest-${restSec}`);
    await page.clock.fastForward((isRest ? restSec + 1 : 16) * 1000);
  }
}

test("gym: complete a full Day A with timers fast-forwarded", async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const errors = watchConsole(page);
  await installClock(page, "2026-11-02T07:00:00"); // Monday: Day A
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });
  await expect(page.getByRole("heading", { name: "Day A" })).toBeVisible();
  await page.getByRole("button", { name: "Start workout" }).click();
  await expect(page.getByText("A1").first()).toBeVisible();
  await shot(page, testInfo, "m4-overview", true);
  await page.getByRole("button", { name: "Start", exact: true }).click();

  await ropeWarmup(page);
  await expect(page.getByRole("heading", { name: "Mobility" })).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Start power sets" }).click();

  await expect(page.getByText("Use about 60% of what you think you can lift")).toBeVisible();
  await powerSets(page, testInfo, 24, 90);

  // Finisher: rope intervals.
  await expect(page.getByRole("heading", { name: "Rope intervals" })).toBeVisible();
  await page.getByRole("button", { name: "Start", exact: true }).click();
  await page.clock.fastForward("02:00");
  await shot(page, testInfo, "m4-intervals");
  await page.clock.fastForward("10:00");

  // Cool-down: tap through the stretches.
  await expect(page.getByText("Cool-down").first()).toBeVisible();
  for (let i = 0; i < 10; i++) {
    const next = page.getByRole("button", { name: /Next stretch|Finish/ });
    if (!(await next.count())) break;
    await next.first().click();
  }
  await expect(page.getByRole("heading", { name: "Day A complete" })).toBeVisible();
  await expect(page.getByText("24", { exact: true })).toBeVisible(); // sets
  await shot(page, testInfo, "m4-summary");
  await page.getByRole("button", { name: "Back to Today" }).click();
  await expect(page.getByText("Day A done. Nice work!")).toBeVisible();
  expect(errors).toEqual([]);
});

test("home: complete Workout A with waist circuit and walk", async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const errors = watchConsole(page);
  await installClock(page, "2026-11-02T07:00:00");
  await onboard(page, {
    program: "home",
    name: "Alex",
    startDate: "2026-11-02",
    startKg: "80",
    goalKg: "72",
  });
  await startWorkout(page);
  await ropeWarmup(page);
  await page.getByRole("button", { name: "Continue" }).click(); // mobility

  // Dumbbells prefill from the equipment weight.
  await expect(page.getByRole("textbox", { name: "Weight", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Done", exact: true }).click();
  await page.clock.fastForward(16_000);
  await expect(page.getByRole("textbox", { name: "Weight", exact: true })).toHaveValue("5");
  await page.getByRole("button", { name: "Done", exact: true }).click();
  await page.clock.fastForward(61_000);
  await powerSets(page, testInfo, 16, 60);

  // Waist circuit: 2 rounds of dead bug (reps), plank and side plank (holds).
  await expect(page.getByRole("heading", { name: /Waist/ })).toBeVisible();
  for (let round = 0; round < 2; round++) {
    await expect(page.getByText(`Round ${round + 1} of 2`)).toBeVisible();
    await doneButton(page).click(); // dead bug
    await page.getByRole("button", { name: "Start", exact: true }).click();
    if (round === 0) await shot(page, testInfo, "m4-hold");
    await page.clock.fastForward(46_000); // plank 30–45 s
    await page.getByRole("button", { name: "Start", exact: true }).click();
    await page.clock.fastForward(31_000); // side plank 20–30 s
    if (round === 0) {
      await expect(page.getByRole("button", { name: "Start next round" })).toBeVisible();
      await page.clock.fastForward(61_000); // rest between rounds
    }
  }

  // Walking pad.
  await expect(page.getByRole("heading", { name: "Walking pad" })).toBeVisible();
  await page.getByRole("button", { name: "Start", exact: true }).click();
  await page.clock.fastForward("25:30");
  await page.getByRole("button", { name: "Finish" }).click();
  await expect(page.getByRole("textbox", { name: "Minutes" })).toHaveValue("26");
  await page.getByRole("button", { name: "Save walk" }).click();

  for (let i = 0; i < 10; i++) {
    const next = page.getByRole("button", { name: /Next stretch|Finish/ });
    if (!(await next.count())) break;
    await next.first().click();
  }
  await expect(page.getByRole("heading", { name: "Workout A complete" })).toBeVisible();
  expect(errors).toEqual([]);
});

test("resume a workout after a reload", async ({ page }, testInfo) => {
  await installClock(page, "2026-11-02T07:00:00");
  await onboard(page, {
    program: "gym",
    name: "Sam",
    startDate: "2026-11-02",
    startKg: "90",
    goalKg: "85",
  });
  await startWorkout(page);
  await ropeWarmup(page);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Start power sets" }).click();
  await page.getByRole("textbox", { name: "Weight", exact: true }).fill("60");
  await page.getByRole("textbox", { name: "Reps" }).fill("9");
  await doneButton(page).click();
  // Now in the 15 s switch to A2. Reload mid-timer.
  await expect(page.getByText("I'm ready")).toBeVisible();
  await page.reload();
  await page.clock.fastForward(16_000);
  await expect(page.getByRole("heading", { name: /A2\s*Lat Pulldown/ })).toBeVisible();

  // And from Today: the in-progress workout is offered.
  await page.goto("./#/");
  await expect(page.getByText("Workout in progress")).toBeVisible();
  await page.getByRole("link", { name: "Resume workout →" }).click();
  await expect(page.getByRole("heading", { name: /A2\s*Lat Pulldown/ })).toBeVisible();
  // The A1 set we logged shows in Logged sets.
  await page.getByRole("button", { name: "Logged sets" }).click();
  await expect(page.getByText("60 kg × 9")).toBeVisible();
  await shot(page, testInfo, "m4-logged-sets");
});
