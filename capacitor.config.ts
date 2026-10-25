import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.leanstrong.app",
  appName: "Lean & Strong",
  webDir: "dist",
  android: {
    // Content is bundled; nothing loads from the network.
    allowMixedContent: false,
  },
  plugins: {
    SystemBars: {
      // Inject --safe-area-inset-* CSS variables so the layout clears the status and nav bars.
      insetsHandling: "css",
      initialViewportFitValueHint: "cover",
    },
    SplashScreen: {
      launchShowDuration: 600,
      launchAutoHide: true,
      backgroundColor: "#1c2a4a",
      showSpinner: false,
    },
    LocalNotifications: {
      smallIcon: "ic_stat_ls",
      iconColor: "#f0642d",
    },
  },
};

export default config;
