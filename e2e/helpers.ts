import type { Page, TestInfo } from "@playwright/test";

/** Save a review screenshot to e2e/screenshots/<project>/<name>.png. */
export async function shot(page: Page, testInfo: TestInfo, name: string, fullPage = false) {
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: `e2e/screenshots/${testInfo.project.name}/${name}.png`,
    fullPage,
  });
}

/** Fail the test on any console error or uncaught page error. */
export function watchConsole(page: Page): string[] {
  const errors: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errors.push(msg.text());
  });
  page.on("pageerror", (err) => errors.push(err.message));
  return errors;
}
