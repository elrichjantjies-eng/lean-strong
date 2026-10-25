import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router";
import { App } from "@capacitor/app";
import { isNative, REQUEST_EXIT_EVENT } from "./index";

/** Android back button: ask in the workout player, otherwise go back, or leave from Today. */
export function NativeBridge() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!isNative) return;
    const handle = App.addListener("backButton", () => {
      const path = location.pathname;
      if (path.startsWith("/workout/")) {
        window.dispatchEvent(new Event(REQUEST_EXIT_EVENT));
      } else if (document.querySelector('[role="dialog"]')) {
        document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
        window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
      } else if (path !== "/") {
        navigate(-1);
      } else {
        void App.minimizeApp();
      }
    });
    return () => void handle.then((h) => h.remove());
  }, [location.pathname, navigate]);

  return null;
}
