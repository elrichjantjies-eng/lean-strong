import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";

const variants: Record<Variant, string> = {
  primary: "bg-accent-strong text-on-accent shadow-sm active:brightness-95",
  secondary: "bg-surface text-ink ring-1 ring-line active:bg-surface-2",
  ghost: "text-accent-ink active:bg-surface-2",
  danger: "bg-[#b42318] text-white active:brightness-95",
};

export function Button({
  variant = "primary",
  size = "md",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: "md" | "lg" }) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-2 rounded-xl px-4 font-semibold transition disabled:opacity-50 ${
        size === "lg" ? "min-h-14 text-lg" : "min-h-12 text-base"
      } ${variants[variant]} ${className}`}
      {...props}
    />
  );
}

export function Card({
  children,
  className = "",
  style,
  flush = false,
}: {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  /** No padding (for cards with a full-bleed header). */
  flush?: boolean;
}) {
  return (
    <section
      className={`rounded-2xl bg-surface shadow-sm ring-1 ring-line ${flush ? "overflow-hidden" : "p-4"} ${className}`}
      style={style}
    >
      {children}
    </section>
  );
}

/** Soft tinted callout with a left accent bar, like the PDFs. */
export function Callout({
  tone = "info",
  title,
  children,
  color,
}: {
  tone?: "info" | "accent";
  title?: ReactNode;
  children: ReactNode;
  color?: string;
}) {
  const bar = color ?? (tone === "accent" ? "var(--app-accent)" : "var(--app-primary)");
  return (
    <div
      className={`rounded-xl border-l-4 p-4 ${tone === "accent" ? "bg-accent-soft" : "bg-info-soft"}`}
      style={{ borderLeftColor: bar }}
    >
      {title && (
        <p
          className={`mb-1 text-sm font-bold tracking-wide uppercase ${tone === "accent" ? "text-accent-ink" : "text-ink"}`}
        >
          {title}
        </p>
      )}
      <div className="space-y-1.5 text-[0.95rem] leading-relaxed text-ink">{children}</div>
    </div>
  );
}

export function ProgressBar({
  value,
  color = "var(--app-accent)",
  label,
}: {
  value: number;
  color?: string;
  label: string;
}) {
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuenow={pct}
      aria-valuemin={0}
      aria-valuemax={100}
      className="h-2.5 overflow-hidden rounded-full bg-surface-2"
    >
      <div
        className="h-full rounded-full transition-[width]"
        style={{ width: `${pct}%`, background: color }}
      />
    </div>
  );
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-semibold text-ink">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-sm text-muted">{hint}</span>}
    </label>
  );
}

export const inputClass =
  "block min-h-12 w-full rounded-xl bg-surface px-4 text-lg text-ink ring-1 ring-line focus:ring-2 focus:ring-[var(--app-accent)] focus:outline-none";
