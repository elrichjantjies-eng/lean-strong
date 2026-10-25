/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";

// BASE_PATH is "/" for local dev and Capacitor, "/lean-strong/" on GitHub Pages.
const base = process.env.BASE_PATH ?? "/";

export default defineConfig({
  base,
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      // We register the service worker ourselves (never inside the Android app).
      injectRegister: null,
      registerType: "prompt",
      includeAssets: ["favicon.svg", "favicon-32.png", "icons/apple-touch-icon-180.png"],
      manifest: {
        id: base,
        name: "Lean & Strong",
        short_name: "Lean&Strong",
        description: "Guided workouts, set logging and progress tracking. Works offline.",
        lang: "en",
        start_url: base,
        scope: base,
        display: "standalone",
        orientation: "portrait",
        theme_color: "#1c2a4a",
        background_color: "#f6f7fb",
        icons: [
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          {
            src: "icons/maskable-192.png",
            sizes: "192x192",
            type: "image/png",
            purpose: "maskable",
          },
          {
            src: "icons/maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        // Precache everything (app shell, fonts, every exercise picture and animation) so the
        // app works fully offline in the gym.
        globPatterns: ["**/*.{js,css,html,woff2,png,svg,webp,ico,json}"],
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
        navigateFallback: "index.html",
        // Take control on the very first visit so it's offline-ready straight away. Updates
        // still wait for the user to tap Reload (no skipWaiting).
        clientsClaim: true,
        cleanupOutdatedCaches: true,
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
