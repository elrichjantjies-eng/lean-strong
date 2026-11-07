import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.skyglass.weather",
  appName: "Skyglass",
  webDir: "dist",
  android: {
    allowMixedContent: false,
  },
  plugins: {
    SystemBars: {
      // Inject --safe-area-inset-* CSS variables so the layout clears the status and nav bars.
      insetsHandling: "css",
      initialViewportFitValueHint: "cover",
    },
    SplashScreen: {
      launchShowDuration: 500,
      launchAutoHide: true,
      backgroundColor: "#0b1730",
      showSpinner: false,
    },
  },
};

export default config;
