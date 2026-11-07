import { useState } from "react";
import { CloudLightning, CloudRain, ExternalLink, TriangleAlert } from "lucide-react";
import type { Forecast, SawsWarning } from "@/data/types";
import { Card, Sheet } from "@/components/ui";
import { compassName } from "@/lib/geo";
import { fmtTime } from "@/lib/time";
import { nowcast } from "@/weather/insights";
import type { StormNearby } from "@/state/useWeather";
import type { Fmt } from "@/lib/units";
import { SAWS_SITE } from "@/data/saws";

const COLORS: Record<SawsWarning["color"], { bg: string; dot: string; name: string }> = {
  yellow: { bg: "rgba(250, 204, 21, 0.22)", dot: "#facc15", name: "Yellow" },
  orange: { bg: "rgba(251, 146, 60, 0.26)", dot: "#fb923c", name: "Orange" },
  red: { bg: "rgba(239, 68, 68, 0.3)", dot: "#ef4444", name: "Red" },
  advisory: { bg: "rgba(168, 85, 247, 0.24)", dot: "#c084fc", name: "Advisory" },
};

const fmtWhen = (t: number) =>
  t
    ? new Date(t * 1000).toLocaleString("en-ZA", {
        weekday: "short",
        hour: "2-digit",
        minute: "2-digit",
        timeZone: "Africa/Johannesburg",
      })
    : "–";

export function WarningsCard({ warnings }: { warnings: SawsWarning[] }) {
  const [open, setOpen] = useState<SawsWarning | null>(null);
  if (warnings.length === 0) return null;
  const worst = [...warnings].sort((a, b) => (b.level ?? 0) - (a.level ?? 0))[0];
  const c = COLORS[worst.color];
  return (
    <>
      <Card
        onClick={() => setOpen(worst)}
        label="Weather warnings"
        className="border-0"
        style={{ background: `linear-gradient(135deg, ${c.bg}, rgba(255,255,255,0.06))` }}
      >
        <div className="card-title mb-1.5" style={{ color: c.dot }}>
          <TriangleAlert size={13} />
          <span>
            SAWS {c.name} warning{worst.level ? ` · level ${worst.level}` : ""}
          </span>
        </div>
        <div className="text-[16px] font-semibold">{worst.event}</div>
        <p className="text-dim mt-0.5 line-clamp-2 text-sm">{worst.headline}</p>
        {warnings.length > 1 && (
          <p className="text-faint mt-1 text-xs">+{warnings.length - 1} more · tap for details</p>
        )}
      </Card>
      {open && (
        <Sheet title="Weather warnings" onClose={() => setOpen(null)}>
          <div className="space-y-3">
            {warnings.map((w) => (
              <div key={w.id} className="glass p-4" style={{ background: COLORS[w.color].bg }}>
                <div className="flex items-center gap-2">
                  <span
                    className="h-2.5 w-2.5 rounded-full"
                    style={{ background: COLORS[w.color].dot }}
                  />
                  <span className="font-semibold">
                    {w.event}
                    {w.level ? ` · level ${w.level}` : ""}
                  </span>
                </div>
                <p className="mt-2 text-[15px]">{w.headline}</p>
                {w.description && (
                  <p className="text-dim mt-2 text-sm whitespace-pre-line">{w.description}</p>
                )}
                {w.instruction && (
                  <p className="mt-2 text-sm">
                    <span className="font-semibold">What to do: </span>
                    {w.instruction}
                  </p>
                )}
                <p className="text-faint mt-2 text-xs">
                  {w.area && `${w.area} · `}
                  {fmtWhen(w.start)} → {fmtWhen(w.end)}
                </p>
              </div>
            ))}
            <a
              href={`${SAWS_SITE}/Warnings`}
              target="_blank"
              rel="noreferrer"
              className="glass-pill flex items-center justify-center gap-2 rounded-2xl py-3 text-sm"
            >
              All warnings on weathersa.co.za <ExternalLink size={14} />
            </a>
          </div>
        </Sheet>
      )}
    </>
  );
}

export function StormCard({ storm, fmt }: { storm: StormNearby; fmt: Fmt }) {
  const near = storm.distanceKm < 1;
  return (
    <Card
      label="Thunderstorm nearby"
      style={{ background: "linear-gradient(135deg, rgba(250,204,21,.2), rgba(255,255,255,.06))" }}
    >
      <div className="flex items-center gap-3">
        <span className="relative grid h-10 w-10 place-items-center">
          <span
            className="absolute inset-0 rounded-full bg-amber-300/40"
            style={{ animation: "pulse-ring 1.8s ease-out infinite" }}
          />
          <CloudLightning size={22} className="relative text-amber-200" />
        </span>
        <div>
          <div className="font-semibold">
            {near ? "Thunderstorm overhead" : "Thunderstorm nearby"}
          </div>
          <div className="text-dim text-sm">
            {near
              ? "SAWS radar shows a storm cell over you."
              : `${storm.instance === "forecast30" ? "Expected within 30 min: " : ""}${fmt.distance(storm.distanceKm)} to the ${compassName(storm.bearing)}`}
          </div>
        </div>
      </div>
    </Card>
  );
}

export function NowcastCard({ f, fmt, now }: { f: Forecast; fmt: Fmt; now: number }) {
  const n = nowcast(f.minutely, now, f.utcOffset, fmt.s.h24);
  if (!n) return null;
  const slots = f.minutely.filter((m) => m.time + 900 > now).slice(0, 12);
  const max = Math.max(1, ...slots.map((m) => m.precip));
  return (
    <Card title="Next 3 hours" icon={<CloudRain size={13} />} label="Rain nowcast">
      <div className="text-[17px] font-semibold">{n.text}</div>
      <div className="mt-3 flex h-14 items-end gap-1">
        {slots.map((m) => (
          <div key={m.time} className="flex flex-1 flex-col items-center justify-end">
            <div
              className="w-full rounded-t-md bg-sky-300/80"
              style={{
                height: `${Math.max(3, (m.precip / max) * 100)}%`,
                opacity: m.precip > 0 ? 1 : 0.25,
              }}
            />
          </div>
        ))}
      </div>
      <div className="text-faint mt-1 flex justify-between text-[11px]">
        <span>Now</span>
        <span>{slots[4] ? fmtTime(slots[4].time, f.utcOffset, fmt.s.h24) : ""}</span>
        <span>{slots[8] ? fmtTime(slots[8].time, f.utcOffset, fmt.s.h24) : ""}</span>
        <span>{slots[11] ? fmtTime(slots[11].time + 900, f.utcOffset, fmt.s.h24) : ""}</span>
      </div>
    </Card>
  );
}
