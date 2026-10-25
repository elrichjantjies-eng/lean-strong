/**
 * Everything that differs between the Android app (Capacitor) and the web app lives here, so
 * the rest of the app doesn't care where it runs.
 */
import { Capacitor } from "@capacitor/core";
import { Haptics, ImpactStyle, NotificationType } from "@capacitor/haptics";
import { KeepAwake } from "@capacitor-community/keep-awake";
import { LocalNotifications } from "@capacitor/local-notifications";

export const isNative = Capacitor.isNativePlatform();

/** Fired when Android's back button is pressed during a workout; the player asks before leaving. */
export const REQUEST_EXIT_EVENT = "ls:request-exit";
export const platform = Capacitor.getPlatform() as "web" | "android" | "ios";

export function isIosBrowser(): boolean {
  const ua = navigator.userAgent;
  return /iPad|iPhone|iPod/.test(ua) || (ua.includes("Macintosh") && navigator.maxTouchPoints > 1);
}

/** True when running as an installed Home Screen web app (iOS or other). */
export function isStandaloneWeb(): boolean {
  return (
    (navigator as Navigator & { standalone?: boolean }).standalone === true ||
    window.matchMedia("(display-mode: standalone)").matches
  );
}

// ---------- Haptics ----------

export async function haptic(kind: "tick" | "done"): Promise<void> {
  try {
    if (isNative) {
      if (kind === "done") await Haptics.notification({ type: NotificationType.Success });
      else await Haptics.impact({ style: ImpactStyle.Medium });
    } else {
      // Android Chrome supports vibrate; iOS Safari has no vibration, so this is a no-op there.
      navigator.vibrate?.(kind === "done" ? [80, 60, 160] : 40);
    }
  } catch {
    /* haptics are a nice-to-have */
  }
}

// ---------- Keep the screen awake ----------

let wakeLock: WakeLockSentinel | null = null;
let wantAwake = false;

async function requestWebLock(): Promise<boolean> {
  if (!("wakeLock" in navigator)) return false;
  try {
    wakeLock = await navigator.wakeLock.request("screen");
    return true;
  } catch {
    return false;
  }
}

function onVisibility() {
  // The browser drops the wake lock when the page is hidden; take it back on return.
  if (wantAwake && document.visibilityState === "visible") void requestWebLock();
}

/** Returns false when the screen can't be kept awake (show the Auto-Lock tip then). */
export async function keepAwake(on: boolean): Promise<boolean> {
  wantAwake = on;
  if (isNative) {
    try {
      if (on) await KeepAwake.keepAwake();
      else await KeepAwake.allowSleep();
      return true;
    } catch {
      return false;
    }
  }
  if (on) {
    document.addEventListener("visibilitychange", onVisibility);
    return requestWebLock();
  }
  document.removeEventListener("visibilitychange", onVisibility);
  await wakeLock?.release().catch(() => undefined);
  wakeLock = null;
  return true;
}

export const wakeLockSupported = () => isNative || "wakeLock" in navigator;

// ---------- Timer-end notification (Android, while the app is in the background) ----------

const TIMER_NOTIFICATION_ID = 9001;

export async function scheduleTimerAlert(at: number, title: string, body: string): Promise<void> {
  if (!isNative || at <= Date.now()) return;
  try {
    const perm = await LocalNotifications.checkPermissions();
    if (perm.display !== "granted") return;
    await LocalNotifications.schedule({
      notifications: [
        {
          id: TIMER_NOTIFICATION_ID,
          title,
          body,
          schedule: { at: new Date(at), allowWhileIdle: true },
        },
      ],
    });
  } catch {
    /* best effort */
  }
}

export async function cancelTimerAlert(): Promise<void> {
  if (!isNative) return;
  try {
    await LocalNotifications.cancel({ notifications: [{ id: TIMER_NOTIFICATION_ID }] });
  } catch {
    /* nothing scheduled */
  }
}
