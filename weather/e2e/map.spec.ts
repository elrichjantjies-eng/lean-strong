import { expect, test } from "@playwright/test";
import { setup } from "./helpers";

test("draws SAWS warnings, storm cells and town forecasts on the map", async ({ page }) => {
  await setup(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Map and radar" }).click();
  // In South Africa the rain map comes from the satellites (there's no radar coverage).
  await expect(page.getByText(/^Satellite \d{2}:\d{2}/)).toBeVisible();
  await expect(
    page.locator('img[src*="msg_fes%3Ah60b"], img[src*="msg_fes:h60b"]').first(),
  ).toBeAttached();
  // The warning area and the storm cell, plus the dot for the place.
  await expect(page.locator(".leaflet-overlay-pane path")).toHaveCount(3);
  // Today's highs for the main towns.
  await expect(
    page.locator(".leaflet-marker-icon").filter({ hasText: "Cape Town" }),
  ).toBeAttached();
  await page.locator(".leaflet-overlay-pane path[fill='#facc15']").dispatchEvent("click");
  await expect(page.getByText(/Damaging Winds \(level 2\)/)).toBeVisible();
});
