/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import { readFileSync } from "node:fs";

const pkg = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8")) as {
  version: string;
};

// BASE_PATH is "/" for local dev and the Android app, "/lean-strong/weather/" on GitHub Pages.
const base = process.env.BASE_PATH ?? "/";

export default defineConfig({
  base,
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      // Registered by the app itself (never inside the Android app).
      injectRegister: null,
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg", "icons/apple-touch-icon-180.png"],
      manifest: {
        id: base,
        name: "Skyglass Weather",
        short_name: "Skyglass",
        description: "Forecasts, SAWS warnings, radar, air quality and more, in a glass design.",
        lang: "en",
        start_url: base,
        scope: base,
        display: "standalone",
        orientation: "portrait",
        theme_color: "#0b1730",
        background_color: "#0b1730",
        icons: [
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          {
            src: "icons/maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,woff2,png,svg,ico,json}"],
        // The SAWS snapshot is refreshed on the server every couple of hours: never precache it.
        globIgnores: ["saws/**"],
        navigateFallback: "index.html",
        navigateFallbackDenylist: [/\/saws\//],
        clientsClaim: true,
        skipWaiting: true,
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            // Map tiles: keep recently viewed ones for offline use.
            urlPattern: /^https:\/\/server\.arcgisonline\.com\/ArcGIS\/rest\/services\/Canvas\//,
            handler: "CacheFirst",
            options: {
              cacheName: "map-tiles",
              expiration: { maxEntries: 400, maxAgeSeconds: 14 * 24 * 3600 },
            },
          },
          {
            urlPattern: ({ url }) => url.pathname.includes("/saws/"),
            handler: "NetworkFirst",
            options: { cacheName: "saws-snapshot", networkTimeoutSeconds: 6 },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "node",
  },
});
