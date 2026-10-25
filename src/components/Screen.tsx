import type { ReactNode } from "react";
import { Link } from "react-router";
import { Icon } from "./Icon";

/** A tab screen: safe-area aware, padded above the tab bar, with an optional back link. */
export function Screen({
  title,
  kicker,
  back,
  actions,
  hideTitle = false,
  children,
}: {
  title: string;
  kicker?: string;
  back?: string;
  actions?: ReactNode;
  /** Keep the heading for screen readers only (when the page shows its own banner). */
  hideTitle?: boolean;
  children: ReactNode;
}) {
  return (
    <main
      className="mx-auto max-w-xl px-4 pb-28"
      style={{
        paddingTop: "calc(var(--safe-top) + 1rem)",
        paddingLeft: "calc(var(--safe-left) + 1rem)",
        paddingRight: "calc(var(--safe-right) + 1rem)",
      }}
    >
      {back && (
        <Link
          to={back}
          aria-label="Back"
          className="-mt-2 -ml-3 mb-1 flex size-12 items-center justify-center rounded-full text-ink active:bg-surface-2"
        >
          <Icon name="back" />
        </Link>
      )}
      <header className={hideTitle ? "sr-only" : "mb-4 flex items-end justify-between gap-3"}>
        <div>
          {kicker && (
            <p className="text-xs font-semibold tracking-wide text-accent-ink uppercase">
              {kicker}
            </p>
          )}
          <h1 className="text-2xl font-bold text-ink">{title}</h1>
        </div>
        {actions}
      </header>
      {children}
    </main>
  );
}
