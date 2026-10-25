import { useState } from "react";
import { Icon } from "./Icon";

/**
 * Big −/+ stepper with an editable number in the middle. Built for sweaty thumbs: the buttons
 * are 56 px and the value can also be typed.
 */
export function Stepper({
  label,
  value,
  onChange,
  step = 1,
  min = 0,
  max = 9999,
  decimals = 0,
  unit,
  placeholder = "–",
}: {
  label: string;
  value: number | undefined;
  onChange: (v: number | undefined) => void;
  step?: number;
  min?: number;
  max?: number;
  decimals?: number;
  unit?: string;
  placeholder?: string;
}) {
  const format = (v: number | undefined) =>
    v === undefined ? "" : String(Number(v.toFixed(decimals)));
  // While typing, show exactly what was typed; otherwise show the formatted value.
  const [draft, setDraft] = useState<string | null>(null);
  const text = draft ?? format(value);

  const clamp = (v: number) => Math.min(max, Math.max(min, Number(v.toFixed(decimals))));
  const bump = (dir: 1 | -1) => onChange(clamp((value ?? (dir > 0 ? min : step)) + dir * step));

  return (
    <div>
      <span className="mb-1 block text-center text-sm font-semibold text-muted">{label}</span>
      <div className="flex items-stretch gap-2">
        <button
          type="button"
          aria-label={`Decrease ${label}`}
          onClick={() => bump(-1)}
          className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink active:brightness-95"
        >
          <Icon name="minus" className="size-7" />
        </button>
        <div className="relative flex-1">
          <input
            aria-label={label}
            inputMode={decimals > 0 ? "decimal" : "numeric"}
            value={text}
            placeholder={placeholder}
            onFocus={(e) => {
              setDraft(format(value));
              e.currentTarget.select();
            }}
            onChange={(e) => {
              setDraft(e.target.value);
              const v = parseFloat(e.target.value.replace(",", "."));
              onChange(Number.isFinite(v) ? clamp(v) : undefined);
            }}
            onBlur={() => setDraft(null)}
            className="h-14 w-full rounded-xl bg-surface text-center text-2xl font-bold text-ink tabular-nums ring-1 ring-line focus:ring-2 focus:ring-[var(--app-accent)] focus:outline-none"
          />
          {unit && (
            <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm text-muted">
              {unit}
            </span>
          )}
        </div>
        <button
          type="button"
          aria-label={`Increase ${label}`}
          onClick={() => bump(1)}
          className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink active:brightness-95"
        >
          <Icon name="plus" className="size-7" />
        </button>
      </div>
    </div>
  );
}
