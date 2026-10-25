import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router";
import { App } from "@capacitor/app";
import { BACK_EVENT, isNative } from "./index";

/**
 * Android back button: closes an open sheet, lets the current screen handle it (onboarding
 * steps, the workout player asks first), otherwise goes back, or leaves the app from Today.
 */
export function NativeBridge() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!isNative) return;
    const handle = App.addListener("backButton", () => {
      if (document.querySelector('[role="dialog"]')) {
        window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
        return;
      }
      const event = new Event(BACK_EVENT, { cancelable: true });
      window.dispatchEvent(event);
      if (event.defaultPrevented) return;
      if (location.pathname !== "/") navigate(-1);
      else void App.minimizeApp();
    });
    return () => void handle.then((h) => h.remove());
  }, [location.pathname, navigate]);

  return null;
}
