import { useState } from "react";
import { BadgeCheck, ExternalLink, Flame } from "lucide-react";
import type { FireDanger, SawsDay, SawsForecast } from "@/data/types";
import type { IconKey } from "@/icons/defs";
import { Card, Sheet } from "@/components/ui";
import { WeatherIcon } from "@/icons/WeatherIcon";
import { SAWS_SITE } from "@/data/saws";
import { ago } from "@/lib/time";
import type { Fmt } from "@/lib/units";

export function sawsIcon(description: string): IconKey {
  const d = description.toLowerCase();
  if (d.includes("thunder")) return "thunder";
  if (d.includes("snow")) return "snow";
  if (d.includes("shower")) return "showers-day";
  if (d.includes("drizzle")) return "drizzle";
  if (d.includes("heavy rain")) return "heavy-rain";
  if (d.includes("rain")) return "rain";
  if (d.includes("fog") || d.includes("mist")) return "fog";
  if (d.includes("overcast") || d === "cloudy") return "cloudy";
  if (d.includes("partly") || d.includes("high level")) return "partly-day";
  if (d.includes("mostly clear") || d.includes("fine")) return "partly-day";
  if (d.includes("clear") || d.includes("sunny")) return "clear-day";
  return "cloudy";
}

export const FIRE: Record<FireDanger, { color: string; label: string; advice: string }> = {
  blue: { color: "#3b82f6", label: "Low", advice: "Safe: fires unlikely to spread." },
  green: { color: "#22c55e", label: "Moderate", advice: "Fires can be controlled; take care." },
  yellow: {
    color: "#facc15",
    label: "Dangerous",
    advice: "Avoid open fires. Fires may spread quickly.",
  },
  orange: {
    color: "#fb923c",
    label: "Very dangerous",
    advice: "No open fires. Fires will spread rapidly.",
  },
  red: {
    color: "#ef4444",
    label: "Extremely dangerous",
    advice: "No fires at all. Be ready to evacuate.",
  },
};

const dayName = (date: string, i: number) =>
  i === 0
    ? "Today"
    : new Date(`${date}T12:00:00Z`).toLocaleDateString("en", { weekday: "short", timeZone: "UTC" });

export function SawsCard({ saws, fmt }: { saws: SawsForecast; fmt: Fmt }) {
  const [open, setOpen] = useState<SawsDay | null>(null);
  const today = new Date().toISOString().slice(0, 10);
  const days = saws.days.filter((d) => d.date >= today).slice(0, 7);
  if (days.length === 0) return null;
  return (
    <>
      <Card
        title={
          <span className="flex items-center gap-1.5">
            Official SAWS forecast <BadgeCheck size={13} className="text-emerald-300" />
          </span>
        }
        label="Official SAWS forecast"
      >
        <div className="text-dim -mt-1 mb-2 text-xs">
          {saws.station.name}
          {saws.distanceKm >= 2 ? ` · ${fmt.distance(saws.distanceKm)} away` : ""} ·{" "}
          {saws.source === "live" ? "live" : `updated ${ago(saws.fetchedAt)}`}
        </div>
        <ul>
          {days.map((d, i) => (
            <li key={d.date} className={i ? "border-t hairline" : ""}>
              <button
                type="button"
                onClick={() => setOpen(d)}
                className="flex w-full items-center gap-2 py-2 text-left"
              >
                <span className="w-[52px] text-[15px] font-medium">{dayName(d.date, i)}</span>
                <WeatherIcon icon={sawsIcon(d.description)} size={26} />
                <span className="min-w-0 flex-1 truncate text-sm">
                  {d.description}
                  {d.pop ? <span className="text-sky-300"> · {d.pop}%</span> : null}
                </span>
                {d.fire && (
                  <span
                    title={`Fire danger: ${FIRE[d.fire].label}`}
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ background: FIRE[d.fire].color }}
                  />
                )}
                <span className="text-dim w-[34px] text-right text-[15px]">{fmt.temp(d.min)}</span>
                <span className="w-[34px] text-right text-[15px] font-medium">
                  {fmt.temp(d.max)}
                </span>
              </button>
            </li>
          ))}
        </ul>
        <a
          href={SAWS_SITE}
          target="_blank"
          rel="noreferrer"
          className="text-dim mt-2 flex items-center gap-1.5 text-xs"
        >
          weathersa.co.za · South African Weather Service <ExternalLink size={11} />
        </a>
      </Card>
      {open && <SawsDaySheet day={open} saws={saws} fmt={fmt} onClose={() => setOpen(null)} />}
    </>
  );
}

function SawsDaySheet({
  day,
  saws,
  fmt,
  onClose,
}: {
  day: SawsDay;
  saws: SawsForecast;
  fmt: Fmt;
  onClose: () => void;
}) {
  const rows: [string, string][] = [
    ["Temperature", `${fmt.temp(day.min)} to ${fmt.temp(day.max)}`],
    [
      "Chance of rain",
      day.pop != null
        ? `${day.pop}%${day.precipMm ? ` · up to ${fmt.precip(day.precipMm)}` : ""}`
        : "–",
    ],
    [
      "Wind",
      day.windText ? `${day.windText}${day.windKmh ? ` · ${fmt.wind(day.windKmh)}` : ""}` : "–",
    ],
    ["Humidity", day.humidityMin != null ? `${day.humidityMin}–${day.humidityMax}%` : "–"],
    ["UV", day.uv || "–"],
    ["Sunrise / sunset", day.sunrise ? `${day.sunrise} / ${day.sunset}` : "–"],
    ["Moonrise / moonset", day.moonrise ? `${day.moonrise} / ${day.moonset}` : "–"],
    ["Moon", day.moonPhase || "–"],
  ];
  if (day.swell?.height != null)
    rows.push([
      "Swell",
      `${day.swell.height} m${day.swell.period ? ` · ${day.swell.period} s` : ""} ${day.swell.dir}`,
    ]);
  return (
    <Sheet
      title={`${saws.station.name}: ${dayName(day.date, -1)} ${day.date.slice(8)}`}
      onClose={onClose}
    >
      <div className="flex items-center gap-3">
        <WeatherIcon icon={sawsIcon(day.description)} size={56} />
        <div>
          <div className="text-xl font-semibold">{day.description}</div>
          <div className="text-dim text-sm">
            Official forecast from the South African Weather Service
          </div>
        </div>
      </div>
      {day.fire && (
        <div
          className="glass mt-4 flex items-center gap-3 p-3"
          style={{ background: `${FIRE[day.fire].color}33` }}
        >
          <Flame size={22} style={{ color: FIRE[day.fire].color }} />
          <div>
            <div className="font-semibold">Fire danger: {FIRE[day.fire].label}</div>
            <div className="text-dim text-sm">{FIRE[day.fire].advice}</div>
          </div>
        </div>
      )}
      <dl className="mt-4 divide-y divide-white/10">
        {rows.map(([k, v]) => (
          <div key={k} className="flex justify-between gap-4 py-2.5 text-[15px]">
            <dt className="text-dim">{k}</dt>
            <dd className="text-right">{v}</dd>
          </div>
        ))}
      </dl>
      {day.tides.length > 0 && (
        <>
          <div className="card-title mt-4 mb-2">Tides</div>
          <div className="grid grid-cols-2 gap-2">
            {day.tides.map((t, i) => (
              <div key={i} className="glass-pill rounded-2xl px-3 py-2 text-sm">
                <div className="text-faint text-xs">{t.label} tide</div>
                <div className="font-medium">
                  {t.time}
                  {t.height != null ? ` · ${t.height.toFixed(1)} m` : ""}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </Sheet>
  );
}
