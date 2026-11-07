import { defineConfig, devices } from "@playwright/test";

const port = 4174;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  workers: process.env.CI ? 2 : 3,
  expect: { timeout: 10_000 },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: `http://localhost:${port}/`,
    trace: "off",
    // Requests made by the service worker would bypass the network mocks.
    serviceWorkers: "block",
    // Fixed position and clock so the mocked forecast always looks the same.
    geolocation: { latitude: -33.9249, longitude: 18.4241 },
    permissions: ["geolocation"],
    timezoneId: "Africa/Johannesburg",
    locale: "en-ZA",
    // Use a pre-installed Chromium when PW_CHROMIUM_PATH is set (local runs without downloads).
    launchOptions: process.env.PW_CHROMIUM_PATH
      ? { executablePath: process.env.PW_CHROMIUM_PATH }
      : {},
  },
  projects: [
    {
      name: "iphone",
      use: { ...devices["iPhone 14"], viewport: { width: 390, height: 844 } },
    },
    {
      name: "android",
      use: { ...devices["Pixel 7"], viewport: { width: 412, height: 915 } },
    },
  ],
  webServer: {
    command: `npx vite build && npx vite preview --port ${port} --strictPort`,
    url: `http://localhost:${port}/`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
