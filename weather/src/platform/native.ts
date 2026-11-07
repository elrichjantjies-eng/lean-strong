/**
 * Everything that differs between the Android app (Capacitor) and the web app lives here.
 */
import { Capacitor, registerPlugin } from "@capacitor/core";
import { Geolocation } from "@capacitor/geolocation";
import { Share } from "@capacitor/share";
import { Haptics, ImpactStyle } from "@capacitor/haptics";
import type { Place } from "@/data/types";
import type { Settings } from "@/state/settings";

export const isNative = Capacitor.isNativePlatform();
export const isAndroid = Capacitor.getPlatform() === "android";

export function isIos(): boolean {
  const ua = navigator.userAgent;
  return /iPad|iPhone|iPod/.test(ua) || (ua.includes("Macintosh") && navigator.maxTouchPoints > 1);
}

export function isStandalone(): boolean {
  return (
    (navigator as Navigator & { standalone?: boolean }).standalone === true ||
    window.matchMedia?.("(display-mode: standalone)").matches
  );
}

// ---- Location ----------------------------------------------------------------------------

export class LocationDenied extends Error {}

export async function getPosition(): Promise<{ lat: number; lon: number }> {
  if (isNative) {
    try {
      let perm = await Geolocation.checkPermissions();
      if (perm.location !== "granted" && perm.coarseLocation !== "granted") {
        perm = await Geolocation.requestPermissions({
          permissions: ["coarseLocation", "location"],
        });
      }
      if (perm.location !== "granted" && perm.coarseLocation !== "granted")
        throw new LocationDenied();
      const pos = await Geolocation.getCurrentPosition({
        enableHighAccuracy: false,
        timeout: 15000,
        maximumAge: 5 * 60_000,
      });
      return { lat: pos.coords.latitude, lon: pos.coords.longitude };
    } catch (e) {
      if (e instanceof LocationDenied) throw e;
      throw new LocationDenied(String((e as Error)?.message ?? e));
    }
  }
  return new Promise((resolve, reject) => {
    if (!("geolocation" in navigator)) return reject(new LocationDenied("No GPS"));
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lon: p.coords.longitude }),
      (err) => reject(new LocationDenied(err.message)),
      { enableHighAccuracy: false, timeout: 15000, maximumAge: 5 * 60_000 },
    );
  });
}

// ---- Share & haptics ---------------------------------------------------------------------------

export async function shareText(
  title: string,
  text: string,
): Promise<"shared" | "copied" | "failed"> {
  try {
    if (isNative) {
      await Share.share({ title, text, dialogTitle: title });
      return "shared";
    }
    if (navigator.share) {
      await navigator.share({ title, text });
      return "shared";
    }
    await navigator.clipboard.writeText(text);
    return "copied";
  } catch {
    return "failed";
  }
}

export function tap(): void {
  if (isNative) void Haptics.impact({ style: ImpactStyle.Light }).catch(() => {});
}

// ---- Home-screen widgets & notifications (Android) ---------------------------------------------

type WidgetPlace = { id: string; name: string; lat: number; lon: number; current: boolean };

export interface WidgetBridgePlugin {
  /** Saves the places and settings the widgets and background alerts use, then refreshes. */
  sync(options: {
    places: WidgetPlace[];
    settings: Record<string, string | number | boolean>;
  }): Promise<void>;
  /** Fetches fresh data for every widget now. */
  refresh(): Promise<void>;
  /** Asks for the notification permission (Android 13+). */
  requestNotifications(): Promise<{ granted: boolean }>;
  /** How many widgets are on the home screen, and whether notifications are allowed. */
  info(): Promise<{ widgets: number; notifications: boolean; canPin: boolean }>;
  /** Asks the launcher to add a widget ("small" | "wide" | "medium" | "large" | "clock"). */
  pin(options: { kind: string }): Promise<{ requested: boolean }>;
}

export const WidgetBridge = registerPlugin<WidgetBridgePlugin>("WidgetBridge");

export async function syncWidgets(places: Place[], settings: Settings): Promise<void> {
  if (!isAndroid) return;
  try {
    await WidgetBridge.sync({
      places: places.map((p) => ({
        id: p.id,
        name: p.name,
        lat: p.lat,
        lon: p.lon,
        current: !!p.isCurrent,
      })),
      settings: {
        temp: settings.temp,
        wind: settings.wind,
        h24: settings.h24,
        widgetStyle: settings.widgetStyle,
        widgetOpacity: settings.widgetOpacity,
        notifyWarnings: settings.notifyWarnings,
        notifyRain: settings.notifyRain,
        notifyDaily: settings.notifyDaily,
        dailyAt: settings.dailyAt,
      },
    });
  } catch {
    /* the widget bridge is optional */
  }
}
