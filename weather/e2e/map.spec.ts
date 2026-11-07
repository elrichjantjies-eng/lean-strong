import { expect, test } from "@playwright/test";
import { setup } from "./helpers";

test("draws SAWS warnings, storm cells and town forecasts on the map", async ({ page }) => {
  await setup(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Map and radar" }).click();
  // The warning area and the storm cell, plus the dot for the place.
  await expect(page.locator(".leaflet-overlay-pane path")).toHaveCount(3);
  // Today's highs for the main towns.
  await expect(page.locator(".leaflet-marker-icon").filter({ hasText: "Cape Town" })).toBeAttached();
  await page.locator(".leaflet-overlay-pane path[fill='#facc15']").dispatchEvent("click");
  await expect(page.getByText(/Damaging Winds \(level 2\)/)).toBeVisible();
});
