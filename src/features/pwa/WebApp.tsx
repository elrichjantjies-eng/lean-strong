import { useState } from "react";
import { useRegisterSW } from "virtual:pwa-register/react";
import { isIosBrowser, isNative, isStandaloneWeb } from "@/platform";
import { Button } from "@/components/ui";

/** "Update available → Reload" (prompt-style updates; web only). */
export function UpdatePrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({ immediate: true });
  if (!needRefresh) return null;
  return (
    <div
      role="status"
      className="fixed inset-x-3 z-40 mx-auto flex max-w-xl items-center justify-between gap-3 rounded-2xl bg-primary p-3 pl-4 text-on-primary shadow-lg"
      style={{ top: "calc(var(--safe-top) + 0.5rem)" }}
    >
      <span className="font-semibold">Update available</span>
      <span className="flex gap-2">
        <button
          type="button"
          className="min-h-11 px-3 text-sm font-semibold opacity-80"
          onClick={() => setNeedRefresh(false)}
        >
          Later
        </button>
        <button
          type="button"
          className="min-h-11 rounded-xl bg-accent-strong px-4 font-semibold text-on-accent"
          onClick={() => void updateServiceWorker(true)}
        >
          Reload
        </button>
      </span>
    </div>
  );
}

const DISMISS_KEY = "ls:install-banner-dismissed";

function dismissedRecently(): boolean {
  try {
    const at = Number(localStorage.getItem(DISMISS_KEY));
    return !!at && Date.now() - at < 3 * 86_400_000;
  } catch {
    return false;
  }
}

function ShareGlyph() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      className="size-6 text-[#0062cc] dark:text-[#6cb4ff]"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 3v12M8 7l4-4 4 4M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
    </svg>
  );
}

function AddGlyph() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="size-6"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <rect x="4" y="4" width="16" height="16" rx="4" />
      <path d="M12 8v8M8 12h8" />
    </svg>
  );
}

/** On iPhone Safari (not yet installed): how to add the app to the Home Screen. */
export function InstallBanner() {
  const [hidden, setHidden] = useState(
    () => isNative || !isIosBrowser() || isStandaloneWeb() || dismissedRecently(),
  );
  if (hidden) return null;
  const steps = [
    {
      icon: <ShareGlyph />,
      text: (
        <>
          Tap the <strong>Share</strong> button in Safari's toolbar
        </>
      ),
    },
    {
      icon: <AddGlyph />,
      text: (
        <>
          Scroll down and tap <strong>Add to Home Screen</strong>
        </>
      ),
    },
    {
      icon: <span className="text-lg font-bold text-[#0062cc] dark:text-[#6cb4ff]">Add</span>,
      text: (
        <>
          Tap <strong>Add</strong>, then open the app from its new icon
        </>
      ),
    },
  ];
  return (
    <aside
      aria-label="Install the app"
      className="mx-auto max-w-xl px-4 pt-3"
      style={{ paddingTop: "calc(var(--safe-top) + 0.75rem)" }}
    >
      <div className="rounded-2xl bg-surface p-4 shadow-sm ring-2 ring-[var(--app-accent)]">
        <p className="font-bold text-ink">Add Lean & Strong to your Home Screen</p>
        <p className="mt-1 text-sm text-muted">
          It then opens full-screen and works offline. Your data is only kept reliably once the app
          is on your Home Screen.
        </p>
        <ol className="mt-3 space-y-2">
          {steps.map((s, i) => (
            <li key={i} className="flex items-center gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink">
                {s.icon}
              </span>
              <span className="text-sm text-ink">
                {i + 1}. {s.text}
              </span>
            </li>
          ))}
        </ol>
        <Button
          variant="ghost"
          className="mt-2 w-full"
          onClick={() => {
            try {
              localStorage.setItem(DISMISS_KEY, String(Date.now()));
            } catch {
              /* private mode */
            }
            setHidden(true);
          }}
        >
          Not now
        </Button>
      </div>
    </aside>
  );
}
