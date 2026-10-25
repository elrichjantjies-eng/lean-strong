import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { onboard, setToday, shot } from "./helpers";

const found: string[] = [];

async function audit(page: Page, name: string) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const serious = results.violations.filter(
    (v) => v.impact === "serious" || v.impact === "critical",
  );
  const summary = serious.map(
    (v) =>
      `${name}: ${v.id} (${v.impact}) ${v.nodes
        .slice(0, 3)
        .map((n) => n.target.join(" "))
        .join(" | ")}`,
  );
  found.push(...summary);
}

const screens: [string, string][] = [
  ["today", "./#/"],
  ["plan", "./#/plan"],
  ["session", "./#/plan/session/A"],
  ["exercises", "./#/exercises"],
  ["exercise", "./#/exercises/kb_goblet_squat"],
  ["progress", "./#/progress?tab=weight"],
  ["checkin", "./#/progress?tab=checkin"],
  ["guide", "./#/more/guide/how-it-works"],
  ["more", "./#/more"],
  ["settings", "./#/more/settings"],
  ["backup", "./#/more/backup"],
  ["workout", "./#/workout/A"],
];

for (const scheme of ["light", "dark"] as const) {
  test(`accessibility (${scheme} mode): no serious WCAG AA issues`, async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    await page.emulateMedia({ colorScheme: scheme, reducedMotion: "reduce" });
    await setToday(page, "2026-11-04T09:00:00");
    await page.goto("./");
    await audit(page, "onboarding");
    await onboard(page, {
      program: "home",
      name: "Alex",
      startDate: "2026-11-02",
      startKg: "80",
      goalKg: "72",
    });
    for (const [name, url] of screens) {
      await page.goto(url);
      await page.waitForLoadState("networkidle");
      await page.waitForTimeout(150);
      await audit(page, name);
      if (scheme === "dark") await shot(page, testInfo, `m10-dark-${name}`, true);
    }
    // The set screen inside the workout player.
    await page.goto("./#/workout/A");
    await page.getByRole("button", { name: "Start", exact: true }).click();
    await page.getByRole("button", { name: "Skip" }).click();
    await page.getByRole("button", { name: "Continue" }).click();
    await expect(page.getByRole("button", { name: "Done", exact: true })).toBeVisible();
    await audit(page, "set");
    if (scheme === "dark") await shot(page, testInfo, "m10-dark-set");
    expect(found).toEqual([]);
    found.length = 0;
  });
}
