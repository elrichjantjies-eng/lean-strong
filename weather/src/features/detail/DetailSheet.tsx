import { useMemo, useState } from "react";
import type { AirQuality, Forecast } from "@/data/types";
import { Chart, type ChartPoint } from "@/components/Chart";
import { Sheet, tempColor } from "@/components/ui";
import { WeatherIcon } from "@/icons/WeatherIcon";
import { conditionIcon, conditionLabel } from "@/weather/codes";
import { fmtTime, weekday, local } from "@/lib/time";
import { aqiCategory, todayIndex, uvCategory } from "@/weather/insights";
import { compass } from "@/lib/geo";
import {
  beaufortName,
  temp as tempIn,
  wind as windIn,
  precip as precipIn,
  type Fmt,
} from "@/lib/units";

export type Metric =
  | "conditions"
  | "feels"
  | "uv"
  | "wind"
  | "precip"
  | "humidity"
  | "visibility"
  | "pressure"
  | "aqi";

const METRICS: { id: Metric; label: string }[] = [
  { id: "conditions", label: "Conditions" },
  { id: "precip", label: "Rain" },
  { id: "wind", label: "Wind" },
  { id: "uv", label: "UV" },
  { id: "feels", label: "Feels like" },
  { id: "humidity", label: "Humidity" },
  { id: "aqi", label: "Air quality" },
  { id: "visibility", label: "Visibility" },
  { id: "pressure", label: "Pressure" },
];

export function DetailSheet({
  f,
  air,
  fmt,
  now,
  initialMetric = "conditions",
  initialDay,
  onClose,
}: {
  f: Forecast;
  air?: AirQuality | null;
  fmt: Fmt;
  now: number;
  initialMetric?: Metric;
  initialDay?: number;
  onClose: () => void;
}) {
  const ti = todayIndex(f, now);
  const [day, setDay] = useState(initialDay ?? ti);
  const [metric, setMetric] = useState<Metric>(initialMetric);
  const [scrub, setScrub] = useState<ChartPoint | null>(null);
  const d = f.daily[day];
  const hours = useMemo(
    () => f.hourly.filter((h) => h.time >= d.time && h.time <= d.time + 86400),
    [f, d],
  );
  const days = f.daily.slice(ti, ti + 10);
  const off = f.utcOffset;
  const s = fmt.s;

  const conv = {
    temp: (c: number) => tempIn(c, s.temp),
    wind: (k: number) => windIn(k, s.wind),
    precip: (mm: number) => precipIn(mm, s.precip),
  };

  let points: ChartPoint[] = [];
  let bars: ChartPoint[] | undefined;
  let format = (v: number) => String(Math.round(v));
  let gradient: string[] | undefined;
  let yMin: number | undefined;
  let yMax: number | undefined;
  let bands: [number, number, string][] | undefined;
  let big = "";
  let sub = "";
  let notes: string[] = [];

  const hv = (fn: (h: (typeof hours)[number]) => number) =>
    hours.map((h) => ({ t: h.time, v: fn(h) }));
  const at = (p: ChartPoint | null) => (p ? ` · ${fmtTime(p.t, off, s.h24)}` : "");

  switch (metric) {
    case "conditions":
    case "feels": {
      const feels = metric === "feels";
      points = hv((h) => conv.temp(feels ? h.feels : h.temp));
      bars = metric === "conditions" ? hv((h) => h.pop) : undefined;
      format = (v) => `${Math.round(v)}°`;
      const lo = Math.min(...hours.map((h) => (feels ? h.feels : h.temp)));
      const hi = Math.max(...hours.map((h) => (feels ? h.feels : h.temp)));
      gradient = [tempColor(lo), tempColor((lo + hi) / 2), tempColor(hi)];
      big = scrub
        ? format(scrub.v ?? 0)
        : `${fmt.temp(feels ? d.feelsMax : d.max)} / ${fmt.temp(feels ? d.feelsMin : d.min)}`;
      sub = scrub
        ? `${conditionLabel(hours.find((h) => h.time === scrub.t)?.code ?? d.code)}${at(scrub)}`
        : conditionLabel(d.code);
      notes = [
        `${weekday(d.time, off)}: ${conditionLabel(d.code).toLowerCase()}, a high of ${fmt.temp(d.max)} and a low of ${fmt.temp(d.min)}.`,
        d.pop > 0
          ? `Chance of rain up to ${d.pop}%${d.precip > 0 ? `, about ${fmt.precip(d.precip)} in total` : ""}.`
          : "No rain expected.",
        `Feels like ${fmt.temp(d.feelsMin)} to ${fmt.temp(d.feelsMax)}.`,
      ];
      break;
    }
    case "precip": {
      points = hv((h) => h.pop);
      bars = hv((h) => conv.precip(h.precip));
      yMin = 0;
      yMax = 100;
      format = (v) => `${Math.round(v)}%`;
      big = scrub ? `${scrub.v}%` : `${d.pop}%`;
      sub = scrub ? `Chance of rain${at(scrub)}` : `Chance of rain · ${fmt.precip(d.precip)} total`;
      notes = [
        d.precip > 0
          ? `${fmt.precip(d.precip)} expected over ${Math.round(d.precipHours)} hour${d.precipHours === 1 ? "" : "s"}.`
          : "No measurable rain expected.",
        "Bars show the amount per hour; the line is the chance of rain.",
      ];
      break;
    }
    case "wind": {
      points = hv((h) => conv.wind(h.wind));
      bars = undefined;
      const gusts = hv((h) => conv.wind(h.gust));
      yMin = 0;
      yMax = Math.max(...gusts.map((g) => g.v ?? 0)) * 1.1 + 1;
      format = (v) => `${Math.round(v)}`;
      const h = scrub ? hours.find((x) => x.time === scrub.t) : null;
      big = h ? fmt.wind(h.wind) : `${fmt.wind(d.windMax)}`;
      sub = h
        ? `${compass(h.windDir)} · gusts ${fmt.wind(h.gust)}${at(scrub)}`
        : `Max · gusts to ${fmt.wind(d.gustMax)} · from ${compass(d.windDir)}`;
      notes = [
        `${beaufortName(d.windMax)} at its strongest, mostly from the ${compass(d.windDir, 16)}.`,
        `Gusts up to ${fmt.wind(d.gustMax)}.`,
      ];
      break;
    }
    case "uv": {
      points = hv((h) => h.uv);
      yMin = 0;
      yMax = Math.max(11, ...hours.map((h) => h.uv + 1));
      bands = [
        [0, 3, "#4ade80"],
        [3, 6, "#facc15"],
        [6, 8, "#fb923c"],
        [8, 11, "#ef4444"],
        [11, 20, "#a855f7"],
      ];
      gradient = ["#4ade80", "#facc15", "#fb923c", "#ef4444", "#a855f7"];
      format = (v) => String(Math.round(v));
      big = scrub ? String(Math.round(scrub.v ?? 0)) : String(Math.round(d.uvMax));
      sub = `${uvCategory(scrub?.v ?? d.uvMax).label}${scrub ? at(scrub) : " · daily max"}`;
      notes = [
        d.uvMax >= 3
          ? "Use sun protection when the UV index is 3 or higher: SPF 30+, a hat and sunglasses."
          : "UV stays low all day.",
      ];
      break;
    }
    case "humidity": {
      points = hv((h) => h.humidity);
      yMin = 0;
      yMax = 100;
      format = (v) => `${Math.round(v)}%`;
      const h = scrub ? hours.find((x) => x.time === scrub.t) : null;
      const avg = Math.round(hours.reduce((a, x) => a + x.humidity, 0) / Math.max(1, hours.length));
      big = h ? `${h.humidity}%` : `${avg}%`;
      sub = h ? `Dew point ${fmt.temp(h.dew)}${at(scrub)}` : "Average";
      notes = [
        "The dew point is a better guide to how humid it feels: above 16° starts to feel sticky.",
      ];
      break;
    }
    case "visibility": {
      points = hv((h) => h.visibility / 1000);
      yMin = 0;
      format = (v) => fmt.distance(v).replace(/ (km|mi)$/, "");
      const h = scrub ? hours.find((x) => x.time === scrub.t) : null;
      big = h
        ? fmt.visibility(h.visibility)
        : fmt.visibility(Math.min(...hours.map((x) => x.visibility)));
      sub = h ? at(scrub).slice(3) : "Lowest";
      notes = ["Over 10 km is a clear view; under 1 km means fog."];
      break;
    }
    case "pressure": {
      points = hv((h) => h.pressure);
      format = (v) =>
        s.pressure === "hpa" ? String(Math.round(v)) : fmt.pressure(v).split(" ")[0];
      const h = scrub ? hours.find((x) => x.time === scrub.t) : null;
      big = fmt.pressure(h ? h.pressure : hours[0]?.pressure);
      sub = h ? at(scrub).slice(3) : "Sea level";
      notes = ["Falling pressure often brings cloud and rain; rising pressure, settled weather."];
      break;
    }
    case "aqi": {
      const list = (air?.hourly ?? []).filter((x) => x.time >= d.time && x.time <= d.time + 86400);
      points = list.map((x) => ({ t: x.time, v: s.aqi === "us" ? x.usAqi : x.euAqi }));
      yMin = 0;
      format = (v) => String(Math.round(v));
      gradient = ["#4ade80", "#facc15", "#fb923c", "#ef4444"];
      const v = scrub?.v ?? Math.max(0, ...points.map((p) => p.v ?? 0));
      big = points.length ? String(Math.round(v)) : "–";
      sub = points.length
        ? `${aqiCategory(v, s.aqi).label}${scrub ? at(scrub) : " · daily max"}`
        : "No air quality forecast for this day";
      notes = points.length ? [aqiCategory(v, s.aqi).advice] : [];
      break;
    }
  }

  const icons = metric === "conditions" ? hours.filter((_, i) => i % 3 === 0 && i < 24) : [];

  return (
    <Sheet title={METRICS.find((m) => m.id === metric)?.label ?? "Details"} onClose={onClose} tall>
      <div className="no-scrollbar -mx-5 flex gap-1 overflow-x-auto px-5 pb-3">
        {days.map((x, i) => {
          const idx = ti + i;
          const sel = idx === day;
          return (
            <button
              key={x.time}
              type="button"
              onClick={() => setDay(idx)}
              aria-pressed={sel}
              className={`flex w-11 shrink-0 flex-col items-center rounded-2xl py-1.5 ${sel ? "bg-white text-slate-900" : ""}`}
            >
              <span className={`text-[11px] ${sel ? "" : "text-dim"}`}>
                {i === 0 ? "Today" : weekday(x.time, off, true)}
              </span>
              <span className="text-[17px] font-semibold">{local(x.time, off).getUTCDate()}</span>
            </button>
          );
        })}
      </div>
      <div className="text-dim mb-3 text-center text-sm">
        {weekday(d.time, off)}, {local(d.time, off).getUTCDate()}{" "}
        {local(d.time, off).toLocaleString("en", { month: "long", timeZone: "UTC" })}
      </div>

      <div className="no-scrollbar -mx-5 mb-3 flex gap-2 overflow-x-auto px-5">
        {METRICS.filter((m) => m.id !== "aqi" || air).map((m) => (
          <button
            key={m.id}
            type="button"
            onClick={() => setMetric(m.id)}
            aria-pressed={metric === m.id}
            className={`shrink-0 rounded-full px-3.5 py-1.5 text-sm ${metric === m.id ? "bg-white text-slate-900" : "glass-pill"}`}
          >
            {m.label}
          </button>
        ))}
      </div>

      <div className="glass p-4">
        <div className="flex items-end justify-between">
          <div>
            <div className="text-3xl font-semibold">{big}</div>
            <div className="text-dim text-sm">{sub}</div>
          </div>
          {metric === "conditions" && <WeatherIcon icon={conditionIcon(d.code)} size={48} />}
        </div>
        {icons.length > 0 && (
          <div className="mt-3 flex justify-between px-1">
            {icons.map((h) => (
              <WeatherIcon key={h.time} icon={conditionIcon(h.code, h.isDay)} size={22} />
            ))}
          </div>
        )}
        {points.length > 1 ? (
          <Chart
            points={points}
            bars={bars}
            barMax={metric === "conditions" ? 100 : undefined}
            offset={off}
            now={now}
            format={format}
            gradient={gradient}
            yMin={yMin}
            yMax={yMax}
            bands={bands}
            onScrub={setScrub}
          />
        ) : (
          <div className="text-dim py-10 text-center text-sm">No hourly data for this day.</div>
        )}
      </div>

      <div className="mt-4 space-y-2">
        <div className="card-title">Daily summary</div>
        {notes.map((n, i) => (
          <p key={i} className="text-[15px] leading-relaxed text-white/90">
            {n}
          </p>
        ))}
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
        <Stat label="Sunrise" value={fmtTime(d.sunrise, off, s.h24)} />
        <Stat label="Sunset" value={fmtTime(d.sunset, off, s.h24)} />
        <Stat label="Rain" value={`${d.pop}% · ${fmt.precip(d.precip)}`} />
        <Stat label="UV max" value={`${Math.round(d.uvMax)} ${uvCategory(d.uvMax).label}`} />
        <Stat label="Wind" value={`${fmt.wind(d.windMax)} ${compass(d.windDir)}`} />
        <Stat label="Sunshine" value={`${Math.round(d.sunshine / 3600)} h`} />
      </div>
    </Sheet>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="glass-pill rounded-2xl px-3 py-2">
      <div className="text-faint text-xs">{label}</div>
      <div className="font-medium">{value}</div>
    </div>
  );
}
