import { useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

/** A frosted-glass card with an optional small uppercase title, like the iPhone weather tiles. */
export function Card({
  title,
  icon,
  children,
  className = "",
  onClick,
  label,
  style,
}: {
  title?: ReactNode;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
  onClick?: () => void;
  label?: string;
  style?: React.CSSProperties;
}) {
  const body = (
    <>
      {title && (
        <div className="card-title mb-2">
          {icon}
          <span>{title}</span>
        </div>
      )}
      {children}
    </>
  );
  if (onClick) {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-label={label}
        style={style}
        className={`glass fade-up block w-full p-4 text-left transition active:scale-[0.985] ${className}`}
      >
        {body}
      </button>
    );
  }
  return (
    <section style={style} aria-label={label} className={`glass fade-up p-4 ${className}`}>
      {body}
    </section>
  );
}

/** A bottom sheet over a blurred backdrop. Closes on backdrop tap, Escape or the close button. */
export function Sheet({
  title,
  onClose,
  children,
  tall = false,
}: {
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  tall?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    const onBack = (e: Event) => {
      e.preventDefault();
      onClose();
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener("sg:back", onBack);
    ref.current?.focus();
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("sg:back", onBack);
    };
  }, [onClose]);

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex flex-col justify-end"
      role="dialog"
      aria-modal="true"
      aria-label={typeof title === "string" ? title : undefined}
    >
      <button
        type="button"
        aria-label="Close"
        className="absolute inset-0 bg-black/35"
        style={{ animation: "backdrop-in .25s ease both", backdropFilter: "blur(2px)" }}
        onClick={onClose}
      />
      <div
        ref={ref}
        tabIndex={-1}
        className={`glass-strong relative mx-auto flex w-full max-w-xl flex-col rounded-t-[28px] outline-none ${tall ? "h-[92vh]" : "max-h-[88vh]"}`}
        style={{
          animation: "sheet-in .35s cubic-bezier(.2,.8,.2,1) both",
          paddingBottom: "var(--safe-bottom)",
        }}
      >
        <div className="mx-auto mt-2 h-1.5 w-10 rounded-full bg-white/30" />
        <div className="flex items-center justify-between px-5 pt-3 pb-2">
          <h2 className="text-lg font-semibold">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="glass-pill grid h-8 w-8 place-items-center rounded-full"
          >
            <X size={16} />
          </button>
        </div>
        <div className="no-scrollbar flex-1 overflow-y-auto px-5 pb-6">{children}</div>
      </div>
    </div>,
    document.body,
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex rounded-xl bg-white/10 p-1">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={`flex-1 rounded-lg px-2 py-1.5 text-sm font-medium transition ${value === o.value ? "bg-white/25 shadow" : "text-white/70"}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
  hint,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  hint?: string;
}) {
  return (
    <label className="flex items-center justify-between gap-4 py-2.5">
      <span>
        <span className="block text-[15px]">{label}</span>
        {hint && <span className="text-faint block text-xs">{hint}</span>}
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={`relative h-7 w-12 shrink-0 rounded-full transition ${checked ? "bg-emerald-400/90" : "bg-white/20"}`}
      >
        <span
          className={`absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition-all ${checked ? "left-[22px]" : "left-0.5"}`}
        />
      </button>
    </label>
  );
}

/** A thin horizontal range bar (min–max) inside a track spanning lo–hi, like the 10-day list. */
export function RangeBar({
  lo,
  hi,
  min,
  max,
  dot,
}: {
  lo: number;
  hi: number;
  min: number;
  max: number;
  dot?: number;
}) {
  const span = Math.max(1, hi - lo);
  const left = ((min - lo) / span) * 100;
  const width = Math.max(4, ((max - min) / span) * 100);
  return (
    <div className="relative h-[5px] flex-1 rounded-full bg-black/20">
      <div
        className="absolute inset-y-0 rounded-full"
        style={{
          left: `${left}%`,
          width: `${width}%`,
          background: tempGradient(min, max),
        }}
      />
      {dot != null && (
        <div
          className="absolute top-1/2 h-[9px] w-[9px] -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-black/30 bg-white"
          style={{ left: `${((dot - lo) / span) * 100}%` }}
        />
      )}
    </div>
  );
}

/** Colour for a temperature in °C (blue → green → yellow → orange → red). */
export function tempColor(c: number): string {
  const stops: [number, [number, number, number]][] = [
    [-5, [120, 160, 255]],
    [5, [90, 200, 250]],
    [14, [110, 220, 170]],
    [20, [190, 230, 90]],
    [26, [255, 205, 70]],
    [32, [255, 140, 60]],
    [40, [240, 70, 70]],
  ];
  if (c <= stops[0][0]) return `rgb(${stops[0][1].join(",")})`;
  for (let i = 1; i < stops.length; i++) {
    const [t1, c1] = stops[i];
    const [t0, c0] = stops[i - 1];
    if (c <= t1) {
      const k = (c - t0) / (t1 - t0);
      const mix = c0.map((v, j) => Math.round(v + (c1[j] - v) * k));
      return `rgb(${mix.join(",")})`;
    }
  }
  return `rgb(${stops[stops.length - 1][1].join(",")})`;
}

export function tempGradient(min: number, max: number): string {
  return `linear-gradient(90deg, ${tempColor(min)}, ${tempColor((min + max) / 2)}, ${tempColor(max)})`;
}

export function Spinner({ size = 18 }: { size?: number }) {
  return (
    <span
      aria-hidden
      className="inline-block rounded-full border-2 border-white/30 border-t-white"
      style={{ width: size, height: size, animation: "spin 0.8s linear infinite" }}
    />
  );
}
