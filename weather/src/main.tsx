import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/inter/wght.css";
import "./index.css";
import { App } from "./App";
import { isNative } from "./platform/native";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Offline support for the web app (never inside the Android app, which bundles everything).
if (!isNative && "serviceWorker" in navigator && import.meta.env.PROD) {
  void import("virtual:pwa-register").then(({ registerSW }) => registerSW({ immediate: true }));
}

if (isNative) {
  void import("@capacitor/status-bar").then(({ StatusBar, Style }) => {
    void StatusBar.setStyle({ style: Style.Dark }).catch(() => {});
  });
}
