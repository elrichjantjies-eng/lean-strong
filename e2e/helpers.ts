import { expect, type Page, type TestInfo } from "@playwright/test";

/** Save a review screenshot to e2e/screenshots/<project>/<name>.png. */
export async function shot(page: Page, testInfo: TestInfo, name: string, fullPage = false) {
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: `e2e/screenshots/${testInfo.project.name}/${name}.png`,
    fullPage,
  });
}

/** Collect console errors and uncaught page errors; assert it's empty at the end. */
export function watchConsole(page: Page): string[] {
  const errors: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errors.push(msg.text());
  });
  page.on("pageerror", (err) => errors.push(err.message));
  return errors;
}

/** Freeze the browser clock at a local date and time, e.g. "2026-11-04T09:00:00". */
export async function setToday(page: Page, isoLocal: string) {
  await page.clock.setFixedTime(new Date(isoLocal));
}

export interface OnboardOptions {
  program: "gym" | "home";
  name: string;
  startDate: string;
  startKg: string;
  goalKg: string;
  screenshots?: TestInfo;
}

/** Walk through onboarding. Leaves the app on Today. */
export async function onboard(page: Page, o: OnboardOptions) {
  const snap = async (name: string) => {
    if (o.screenshots) await shot(page, o.screenshots, `onboarding-${o.program}-${name}`);
  };
  await page.goto("./");
  await expect(page.getByRole("heading", { name: "Lean & Strong" })).toBeVisible();
  await snap("1-welcome");
  await page.getByRole("button", { name: "Get started" }).click();

  const programName = o.program === "gym" ? /12-week fat-loss/ : /9-week home program/;
  await page.getByRole("button", { name: programName }).click();
  await snap("2-program");
  await page.getByRole("button", { name: "Continue" }).click();

  await page.getByLabel("Your name").fill(o.name);
  await page.getByRole("button", { name: "Continue" }).click();

  await page.getByLabel("Start date").fill(o.startDate);
  await page.getByRole("button", { name: "Continue" }).click();

  await page.getByRole("textbox", { name: "Current weight" }).fill(o.startKg);
  await page.getByRole("textbox", { name: "Goal weight" }).fill(o.goalKg);
  await snap("3-weight");
  await page.getByRole("button", { name: "Continue" }).click();

  await page.getByRole("button", { name: "Skip" }).click(); // height
  if (o.program === "home") {
    await expect(page.getByRole("heading", { name: "Your weights at home" })).toBeVisible();
    await page.getByRole("button", { name: "Continue" }).click();
  }
  await page.getByRole("button", { name: "Skip" }).click(); // calories
  await expect(page.getByRole("heading", { name: "Reminders" })).toBeVisible();
  await snap("4-reminders");
  await page.getByRole("button", { name: "Continue" }).click();
  await snap("5-done");
  await page.getByRole("button", { name: "Let's go" }).click();
  await expect(page.getByRole("heading", { name: `Hi ${o.name}` })).toBeVisible();
}
