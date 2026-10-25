import type { ReactNode } from "react";

/** A tab screen: safe-area aware, padded above the tab bar. */
export function Screen({
  title,
  kicker,
  children,
}: {
  title: string;
  kicker?: string;
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
      <header className="mb-4">
        {kicker && (
          <p className="text-xs font-semibold tracking-wide text-accent-ink uppercase">{kicker}</p>
        )}
        <h1 className="text-2xl font-bold text-ink">{title}</h1>
      </header>
      {children}
    </main>
  );
}
