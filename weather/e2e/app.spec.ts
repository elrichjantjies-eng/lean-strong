import { expect, test } from "@playwright/test";
import { setup } from "./helpers";

test.beforeEach(async ({ page }) => {
  await setup(page);
});

test("shows the forecast for my location with SAWS data", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByTestId("place-name").first()).toHaveText("Cape Town");
  await expect(page.getByTestId("current-temp").first()).toHaveText("19°");
  await expect(page.getByText("My location").first()).toBeVisible();

  // Official SAWS forecast, the (test) warning and the storm cell nearby.
  await expect(page.getByText("Official SAWS forecast")).toBeVisible();
  await expect(page.getByText("Cape Town city")).toBeVisible();
  await expect(page.getByText("Damaging Winds").first()).toBeVisible();
  await expect(page.getByText("Thunderstorm nearby")).toBeVisible();

  // Forecast cards and tiles.
  await expect(page.getByRole("region", { name: "Hourly forecast" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Daily forecast" })).toContainText("Today");
  for (const tile of [
    "Air quality",
    "UV index",
    "Wind",
    "Humidity",
    "Visibility",
    "Pressure",
    "Fire danger",
    "Sea & surf",
    "Moon",
  ]) {
    await expect(
      page
        .getByRole("button", { name: tile, exact: true })
        .or(page.getByRole("region", { name: tile, exact: true }))
        .first(),
    ).toBeAttached();
  }
  await expect(page.getByText("Braai")).toBeAttached();
});

test("opens warning details", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Weather warnings" }).click();
  const sheet = page.getByRole("dialog", { name: "Weather warnings" });
  await expect(sheet).toContainText("Secure loose objects");
  await sheet.getByRole("button", { name: "Close" }).last().click();
  await expect(sheet).toBeHidden();
});

test("opens a day's details and switches metric", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("region", { name: "Daily forecast" }).getByRole("button").nth(1).click();
  const sheet = page.getByRole("dialog");
  await expect(sheet).toContainText("Daily summary");
  await sheet.getByRole("button", { name: "Wind", exact: true }).click();
  await expect(sheet.getByRole("heading")).toHaveText("Wind");
  await sheet.getByRole("button", { name: "UV", exact: true }).click();
  await expect(sheet).toContainText("UV max");
  await page.keyboard.press("Escape");
  await expect(sheet).toBeHidden();
});

test("searches, adds and removes a city", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByTestId("place-name").first()).toHaveText("Cape Town");
  await page.getByRole("button", { name: "Locations" }).click();
  await page.getByLabel("Search for a city").fill("Stellenbosch");
  await page.getByRole("list", { name: "Search results" }).getByRole("button").first().click();
  // Back on the forecast, now on the new page.
  await expect(
    page.getByRole("navigation", { name: "Main" }).getByRole("button", { name: "Stellenbosch" }),
  ).toBeAttached();
  await expect(page.getByTestId("place-name").filter({ hasText: "Stellenbosch" })).toBeVisible();

  await page.getByRole("button", { name: "Locations" }).click();
  await page.getByRole("button", { name: "Edit" }).click();
  await page.getByRole("button", { name: "Remove Stellenbosch" }).click();
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByRole("button", { name: /^Stellenbosch/ })).toHaveCount(0);
});

test("switches to Fahrenheit", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Settings" }).click();
  await page
    .getByRole("radiogroup", { name: "Temperature" })
    .getByRole("radio", { name: "°F" })
    .click();
  await page.keyboard.press("Escape");
  await expect(page.getByTestId("current-temp").first()).toHaveText("66°");
  // Remembered after a reload.
  await page.reload();
  await expect(page.getByTestId("current-temp").first()).toHaveText("66°");
});

test("opens the radar map", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Map and radar" }).click();
  await expect(page.getByText("active storm cell")).toBeVisible();
  await page.getByRole("button", { name: "Map layers" }).click();
  await expect(page.getByRole("button", { name: "Warnings (SAWS)" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await page.getByRole("button", { name: "Close map" }).click();
  await expect(page.getByTestId("place-name").first()).toBeVisible();
});

test.describe("without location access", () => {
  test.use({ permissions: [] });

  test("offers a search and popular cities", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("button", { name: "Use my location" })).toBeVisible();
    await page.getByRole("button", { name: "Search for a city" }).click();
    await page.getByRole("button", { name: "+ Durban" }).click();
    await expect(page.getByTestId("place-name").first()).toHaveText("Durban");
  });
});
