import { expect, test } from "@playwright/test";
import { shot, watchConsole } from "./helpers";

test("app shell loads both programs and every tab", async ({ page }, testInfo) => {
  const errors = watchConsole(page);
  await page.goto("./");
  await expect(page.getByRole("heading", { name: "Today" })).toBeVisible();
  await expect(page.getByText("12-week fat-loss strength program")).toBeVisible();
  await expect(page.getByText("9 weeks · home")).toBeVisible();
  await shot(page, testInfo, "m1-today");

  for (const tab of ["Plan", "Exercises", "Progress", "More"]) {
    await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: tab }).click();
    await expect(page.getByRole("heading", { name: tab })).toBeVisible();
  }
  expect(errors).toEqual([]);
});
