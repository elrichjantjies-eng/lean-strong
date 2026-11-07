import type { ReactNode } from "react";
import {
  Droplets,
  Eye,
  Gauge,
  Leaf,
  Moon,
  Sun,
  Sunset,
  Sunrise,
  Thermometer,
  Umbrella,
  Waves,
  Wind,
  ArrowUp,
  ArrowDown,
  Minus,
  Flame,
  Sparkles,
  Backpack,
} from "lucide-react";
import type { AirQuality, Forecast, Marine, Place, SawsForecast } from "@/data/types";
import { Card } from "@/components/ui";
import { fmtDuration, fmtTime, weekday, fmtDate } from "@/lib/time";
import { compass } from "@/lib/geo";
import { moonInfo, moonTimes, nextPhase } from "@/lib/astro";
import { beaufortName, type Fmt } from "@/lib/units";
import {
  aqiCategory,
  daylightChange,
  dewNote,
  feelsLikeNote,
  precipTotals,
  pressureTrend,
  sunProgress,
  today,
  todayIndex,
  uvAdvice,
  uvCategory,
  visibilityNote,
  whatToTake,
} from "@/weather/insights";
import { lifeIndices } from "@/weather/lifeIndex";
import type { Metric } from "@/features/detail/DetailSheet";
import { FIRE } from "./SawsCard";

type Open = (m: Metric) => void;

function Tile({
  title,
  icon,
  children,
  onClick,
  wide = false,
}: {
  title: string;
  icon: ReactNode;
  children: ReactNode;
  onClick?: () => void;
  wide?: boolean;
}) {
  return (
    <Card
      title={title}
      icon={icon}
      onClick={onClick}
      label={title}
      className={`flex min-h-[164px] flex-col ${wide ? "col-span-2" : "aspect-square"}`}
    >
      <div className="flex flex-1 flex-col">{children}</div>
    </Card>
  );
}

function Scale({ value, max, colors }: { value: number; max: number; colors: string[] }) {
  return (
    <div
      className="relative mt-2 h-[5px] rounded-full"
      style={{ background: `linear-gradient(90deg, ${colors.join(",")})` }}
    >
      <div
        className="absolute top-1/2 h-[9px] w-[9px] -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-black/40 bg-white"
        style={{ left: `${Math.max(0, Math.min(100, (value / max) * 100))}%` }}
      />
    </div>
  );
}

export function AirTile({ air, fmt, onOpen }: { air: AirQuality; fmt: Fmt; onOpen: Open }) {
  const v = fmt.s.aqi === "us" ? air.usAqi : air.euAqi;
  if (v == null) return null;
  const cat = aqiCategory(v, fmt.s.aqi);
  const pollutants: [string, number | null][] = [
    ["PM2.5", air.pm25],
    ["PM10", air.pm10],
    ["O₃", air.o3],
    ["NO₂", air.no2],
    ["SO₂", air.so2],
  ];
  return (
    <Tile title="Air quality" icon={<Leaf size={13} />} onClick={() => onOpen("aqi")} wide>
      <div className="flex items-baseline gap-2">
        <span className="text-3xl font-semibold">{Math.round(v)}</span>
        <span className="text-lg font-medium">{cat.label}</span>
      </div>
      <Scale
        value={v}
        max={fmt.s.aqi === "us" ? 300 : 100}
        colors={["#4ade80", "#facc15", "#fb923c", "#ef4444", "#a855f7"]}
      />
      <p className="text-dim mt-2.5 text-sm">{cat.advice}</p>
      <div className="mt-auto flex justify-between pt-2.5 text-xs">
        {pollutants.map(([k, val]) => (
          <span key={k} className="text-center">
            <span className="text-faint block">{k}</span>
            <span className="font-medium">{val == null ? "–" : Math.round(val)}</span>
          </span>
        ))}
      </div>
    </Tile>
  );
}

export function UvTile({
  f,
  fmt,
  now,
  onOpen,
}: {
  f: Forecast;
  fmt: Fmt;
  now: number;
  onOpen: Open;
}) {
  const uv = f.current.uv;
  const cat = uvCategory(uv);
  return (
    <Tile title="UV index" icon={<Sun size={13} />} onClick={() => onOpen("uv")}>
      <div className="text-3xl font-semibold">{Math.round(uv)}</div>
      <div className="text-lg font-medium">{cat.label}</div>
      <Scale value={uv} max={12} colors={["#4ade80", "#facc15", "#fb923c", "#ef4444", "#a855f7"]} />
      <p className="mt-auto pt-2 text-[13px] leading-snug">{uvAdvice(f, now, fmt.s.h24)}</p>
    </Tile>
  );
}

export function SunTile({ f, fmt, now }: { f: Forecast; fmt: Fmt; now: number }) {
  const ti = todayIndex(f, now);
  const d = f.daily[ti];
  const p = sunProgress(d, now);
  const next =
    now < d.sunrise
      ? ["Sunrise", d.sunrise]
      : now < d.sunset
        ? ["Sunset", d.sunset]
        : ["Sunrise", f.daily[ti + 1]?.sunrise ?? d.sunrise];
  const other =
    next[0] === "Sunset"
      ? `Sunrise: ${fmtTime(d.sunrise, f.utcOffset, fmt.s.h24)}`
      : `Sunset: ${fmtTime(d.sunset, f.utcOffset, fmt.s.h24)}`;
  // A sine-shaped arc for the sun's path, with the sun marked on it.
  const W = 140;
  const H = 46;
  const px = (q: number) => 6 + q * (W - 12);
  const py = (q: number) => H - 6 - Math.sin(q * Math.PI) * (H - 14);
  const arc = Array.from(
    { length: 41 },
    (_, i) => `${i ? "L" : "M"}${px(i / 40).toFixed(1)},${py(i / 40).toFixed(1)}`,
  ).join(" ");
  return (
    <Tile
      title={next[0] as string}
      icon={next[0] === "Sunset" ? <Sunset size={13} /> : <Sunrise size={13} />}
    >
      <div className="text-3xl font-semibold">
        {fmtTime(next[1] as number, f.utcOffset, fmt.s.h24)}
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} className="mt-1 w-full" aria-hidden>
        <line x1="0" x2={W} y1={H - 6} y2={H - 6} stroke="white" strokeOpacity=".3" />
        <path
          d={arc}
          fill="none"
          stroke="white"
          strokeOpacity=".45"
          strokeWidth="2"
          strokeDasharray={p == null ? "3 4" : undefined}
        />
        {p != null && (
          <circle cx={px(p)} cy={py(p)} r="5.5" fill="#ffd54f" stroke="white" strokeWidth="1.5" />
        )}
      </svg>
      <div className="mt-auto text-[13px]">{other}</div>
      <div className="text-faint text-[11px]">{fmtDuration(d.daylight)} of daylight</div>
    </Tile>
  );
}

export function WindTile({ f, fmt, onOpen }: { f: Forecast; fmt: Fmt; onOpen: Open }) {
  const c = f.current;
  return (
    <Tile title="Wind" icon={<Wind size={13} />} onClick={() => onOpen("wind")} wide>
      <div className="flex flex-1 items-center gap-4">
        <div className="flex-1 space-y-2">
          <Row label="Wind" value={fmt.wind(c.wind)} />
          <Row label="Gusts" value={fmt.wind(c.gust)} />
          <Row label="Direction" value={`${Math.round(c.windDir)}° ${compass(c.windDir, 16)}`} />
          <p className="text-faint text-xs">{beaufortName(c.wind)}</p>
        </div>
        <Compass deg={c.windDir} speed={fmt.wind(c.wind, false)} unit={fmt.windUnit} />
      </div>
    </Tile>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b pb-1.5 text-[15px] hairline">
      <span className="text-dim">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}

function Compass({ deg, speed, unit }: { deg: number; speed: string; unit: string }) {
  const ticks = Array.from({ length: 36 }, (_, i) => i * 10);
  return (
    <svg
      viewBox="0 0 120 120"
      className="h-[118px] w-[118px] shrink-0"
      aria-label={`Wind from ${compass(deg)}`}
    >
      {ticks.map((t) => (
        <line
          key={t}
          x1="60"
          y1="6"
          x2="60"
          y2={t % 90 === 0 ? 14 : 11}
          stroke="white"
          strokeOpacity={t % 90 === 0 ? 0.9 : 0.35}
          strokeWidth={t % 90 === 0 ? 2 : 1}
          transform={`rotate(${t} 60 60)`}
        />
      ))}
      {(["N", "E", "S", "W"] as const).map((l, i) => (
        <text
          key={l}
          x={60 + Math.sin((i * Math.PI) / 2) * 40}
          y={64 + -Math.cos((i * Math.PI) / 2) * 40}
          fontSize="10"
          fontWeight="600"
          fill="white"
          fillOpacity=".7"
          textAnchor="middle"
        >
          {l}
        </text>
      ))}
      {/* The arrow points where the wind blows to (it comes from `deg`). */}
      <g transform={`rotate(${deg + 180} 60 60)`}>
        <line
          x1="60"
          y1="98"
          x2="60"
          y2="24"
          stroke="white"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        <path d="M60 16 L66 28 L54 28 Z" fill="white" />
        <circle cx="60" cy="98" r="4" fill="none" stroke="white" strokeWidth="2" />
      </g>
      <circle cx="60" cy="60" r="20" fill="rgba(20,30,60,.55)" />
      <text x="60" y="61" fontSize="15" fontWeight="700" fill="white" textAnchor="middle">
        {speed}
      </text>
      <text x="60" y="72" fontSize="8" fill="white" fillOpacity=".7" textAnchor="middle">
        {unit}
      </text>
    </svg>
  );
}

export function PrecipTile({
  f,
  fmt,
  now,
  onOpen,
}: {
  f: Forecast;
  fmt: Fmt;
  now: number;
  onOpen: Open;
}) {
  const { past24, next24 } = precipTotals(f, now);
  return (
    <Tile title="Precipitation" icon={<Umbrella size={13} />} onClick={() => onOpen("precip")}>
      <div className="text-3xl font-semibold">{fmt.precip(past24)}</div>
      <div className="text-[15px] font-medium">in last 24 h</div>
      <p className="mt-auto text-[13px] leading-snug">
        {next24 >= 0.2
          ? `${fmt.precip(next24)} expected in the next 24 h.`
          : "No rain expected in the next 24 h."}
      </p>
    </Tile>
  );
}

export function FeelsTile({ f, fmt, onOpen }: { f: Forecast; fmt: Fmt; onOpen: Open }) {
  const c = f.current;
  return (
    <Tile title="Feels like" icon={<Thermometer size={13} />} onClick={() => onOpen("feels")}>
      <div className="text-3xl font-semibold">{fmt.temp(c.feels)}</div>
      <div className="text-dim text-sm">Actual: {fmt.temp(c.temp)}</div>
      <p className="mt-auto text-[13px] leading-snug">
        {feelsLikeNote(c.temp, c.feels, c.humidity, c.wind)}
      </p>
    </Tile>
  );
}

export function HumidityTile({ f, onOpen }: { f: Forecast; onOpen: Open }) {
  const c = f.current;
  return (
    <Tile title="Humidity" icon={<Droplets size={13} />} onClick={() => onOpen("humidity")}>
      <div className="text-3xl font-semibold">{Math.round(c.humidity)}%</div>
      <p className="mt-auto text-[13px] leading-snug">{dewNote(c.dew)}</p>
    </Tile>
  );
}

export function VisibilityTile({ f, fmt, onOpen }: { f: Forecast; fmt: Fmt; onOpen: Open }) {
  return (
    <Tile title="Visibility" icon={<Eye size={13} />} onClick={() => onOpen("visibility")}>
      <div className="text-3xl font-semibold">{fmt.visibility(f.current.visibility)}</div>
      <p className="mt-auto text-[13px] leading-snug">{visibilityNote(f.current.visibility)}</p>
    </Tile>
  );
}

export function PressureTile({
  f,
  fmt,
  now,
  onOpen,
}: {
  f: Forecast;
  fmt: Fmt;
  now: number;
  onOpen: Open;
}) {
  const p = f.current.pressure;
  const { trend } = pressureTrend(f, now);
  // Gauge from 960 to 1060 hPa over a 270° arc.
  const q = Math.max(0, Math.min(1, (p - 960) / 100));
  const angle = -135 + q * 270;
  const Icon = trend === "rising" ? ArrowUp : trend === "falling" ? ArrowDown : Minus;
  const r = 44;
  const arcPt = (a: number) => [
    60 + r * Math.sin((a * Math.PI) / 180),
    60 - r * Math.cos((a * Math.PI) / 180),
  ];
  const [sx, sy] = arcPt(-135);
  const [ex, ey] = arcPt(135);
  return (
    <Tile title="Pressure" icon={<Gauge size={13} />} onClick={() => onOpen("pressure")}>
      <svg viewBox="0 0 120 108" className="mx-auto -mt-1 w-[120px]" aria-hidden>
        <path
          d={`M${sx},${sy} A${r},${r} 0 1 1 ${ex},${ey}`}
          fill="none"
          stroke="white"
          strokeOpacity=".25"
          strokeWidth="6"
          strokeLinecap="round"
        />
        <g transform={`rotate(${angle} 60 60)`}>
          <line
            x1="60"
            y1="22"
            x2="60"
            y2="11"
            stroke="white"
            strokeWidth="4"
            strokeLinecap="round"
          />
        </g>
        <foreignObject x="44" y="36" width="32" height="20">
          <div className="flex justify-center">
            <Icon size={16} />
          </div>
        </foreignObject>
        <text x="60" y="76" fontSize="17" fontWeight="700" fill="white" textAnchor="middle">
          {fmt.pressure(p).split(" ")[0]}
        </text>
        <text x="60" y="90" fontSize="10" fill="white" fillOpacity=".7" textAnchor="middle">
          {fmt.pressure(p).split(" ")[1]}
        </text>
      </svg>
      <p className="mt-auto text-center text-[13px] capitalize">{trend}</p>
    </Tile>
  );
}

export function MoonTile({
  f,
  place,
  saws,
  fmt,
  now,
}: {
  f: Forecast;
  place: Place;
  saws?: SawsForecast | null;
  fmt: Fmt;
  now: number;
}) {
  const m = moonInfo(now);
  const d = today(f, now);
  const sawsToday = saws?.days.find((x) => x.date === new Date().toISOString().slice(0, 10));
  const computed = moonTimes(d.time, place.lat, place.lon);
  const rise =
    sawsToday?.moonrise || (computed.rise ? fmtTime(computed.rise, f.utcOffset, fmt.s.h24) : "–");
  const set =
    sawsToday?.moonset || (computed.set ? fmtTime(computed.set, f.utcOffset, fmt.s.h24) : "–");
  const full = nextPhase(now, 0.5);
  const daysToFull = Math.round((full - now) / 86400);
  return (
    <Tile title="Moon" icon={<Moon size={13} />} wide>
      <div className="flex flex-1 items-center gap-4">
        <div className="flex-1 space-y-2">
          <div className="text-lg font-semibold">{m.name}</div>
          <Row label="Illumination" value={`${Math.round(m.fraction * 100)}%`} />
          <Row label="Moonrise" value={rise} />
          <Row label="Moonset" value={set} />
          <p className="text-faint text-xs">
            {daysToFull <= 0
              ? "Full moon tonight"
              : `Next full moon: ${weekday(full, f.utcOffset, true)} ${fmtDate(full, f.utcOffset)} (${daysToFull} d)`}
          </p>
        </div>
        <MoonDisc phase={m.phase} southern={place.lat < 0} />
      </div>
    </Tile>
  );
}

/** The moon's lit shape for a phase (0 new, 0.5 full), mirrored for the southern hemisphere. */
export function MoonDisc({
  phase,
  southern,
  size = 104,
}: {
  phase: number;
  southern: boolean;
  size?: number;
}) {
  const R = 40;
  const k = (1 - Math.cos(phase * 2 * Math.PI)) / 2;
  const rx = Math.abs(1 - 2 * k) * R;
  const waxing = phase < 0.5;
  const sweep = k < 0.5 ? 0 : 1;
  const lit = `M50,${50 - R} A${R},${R} 0 0 1 50,${50 + R} A${rx},${R} 0 0 ${sweep} 50,${50 - R} Z`;
  const flip = waxing !== !southern; // lit side: right when waxing in the north
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} aria-hidden>
      <defs>
        <radialGradient id="moonlit" cx="40%" cy="35%" r="70%">
          <stop offset="0" stopColor="#fffbe6" />
          <stop offset="1" stopColor="#d8d2b0" />
        </radialGradient>
      </defs>
      <circle cx="50" cy="50" r={R + 6} fill="white" opacity=".06" />
      <circle cx="50" cy="50" r={R} fill="#1d2640" opacity=".85" />
      <path
        d={lit}
        fill="url(#moonlit)"
        transform={flip ? "translate(100 0) scale(-1 1)" : undefined}
      />
      <circle cx="38" cy="40" r="5" fill="#000" opacity=".06" />
      <circle cx="58" cy="62" r="7" fill="#000" opacity=".05" />
    </svg>
  );
}

export function MarineTile({ marine, fmt, offset }: { marine: Marine; fmt: Fmt; offset: number }) {
  const max = Math.max(0.5, ...marine.daily.map((d) => d.waveMax ?? 0));
  return (
    <Tile title="Sea & surf" icon={<Waves size={13} />} wide>
      <div className="grid grid-cols-3 gap-2">
        <Big
          label="Waves"
          value={`${marine.waveHeight.toFixed(1)} m`}
          sub={`${Math.round(marine.wavePeriod)} s · ${compass(marine.waveDir)}`}
        />
        <Big
          label="Swell"
          value={marine.swellHeight != null ? `${marine.swellHeight.toFixed(1)} m` : "–"}
          sub={
            marine.swellPeriod != null
              ? `${Math.round(marine.swellPeriod)} s · ${compass(marine.swellDir ?? 0)}`
              : ""
          }
        />
        <Big
          label="Sea temp"
          value={marine.seaTemp != null ? fmt.temp(marine.seaTemp) : "–"}
          sub={
            marine.seaTemp != null
              ? marine.seaTemp < 16
                ? "Cold"
                : marine.seaTemp < 20
                  ? "Fresh"
                  : "Pleasant"
              : ""
          }
        />
      </div>
      <div className="mt-3 flex h-12 items-end gap-1.5">
        {marine.daily.map((d) => (
          <div key={d.time} className="flex flex-1 flex-col items-center gap-1">
            <div
              className="w-full rounded-t-md bg-cyan-300/70"
              style={{ height: `${((d.waveMax ?? 0) / max) * 32 + 2}px` }}
            />
            <span className="text-faint text-[10px]">
              {weekday(d.time, offset, true).slice(0, 2)}
            </span>
          </div>
        ))}
      </div>
    </Tile>
  );
}

function Big({ label, value, sub }: { label: string; value: string; sub: string }) {
  return (
    <div>
      <div className="text-faint text-xs">{label}</div>
      <div className="text-xl font-semibold">{value}</div>
      <div className="text-dim text-xs">{sub}</div>
    </div>
  );
}

export function FireTile({ saws }: { saws: SawsForecast }) {
  const d = saws.days.find((x) => x.date === new Date().toISOString().slice(0, 10)) ?? saws.days[0];
  if (!d?.fire) return null;
  const fd = FIRE[d.fire];
  return (
    <Tile title="Fire danger" icon={<Flame size={13} />}>
      <div className="text-2xl leading-tight font-semibold" style={{ color: fd.color }}>
        {fd.label}
      </div>
      <Scale
        value={["blue", "green", "yellow", "orange", "red"].indexOf(d.fire) + 0.5}
        max={5}
        colors={["#3b82f6", "#22c55e", "#facc15", "#fb923c", "#ef4444"]}
      />
      <p className="mt-auto pt-2 text-[13px] leading-snug">{fd.advice}</p>
    </Tile>
  );
}

export function LifeCard({ f, now, aqi }: { f: Forecast; now: number; aqi: number | null }) {
  const list = lifeIndices(f, now, aqi);
  const color = (s: number) =>
    s >= 8 ? "#4ade80" : s >= 6 ? "#a3e635" : s >= 4 ? "#facc15" : "#fb7185";
  return (
    <Card title="Today's outlook" icon={<Sparkles size={13} />} label="Activities">
      <div className="grid grid-cols-2 gap-2">
        {list.map((a) => (
          <div key={a.id} className="glass-pill flex items-center gap-2.5 rounded-2xl px-3 py-2.5">
            <span className="text-2xl" aria-hidden>
              {a.emoji}
            </span>
            <span className="min-w-0">
              <span className="block text-[14px] font-medium">{a.name}</span>
              <span
                className="my-0.5 inline-block rounded-full px-1.5 text-[11px] font-semibold text-slate-900"
                style={{ background: color(a.score) }}
              >
                {a.verdict}
              </span>
              <span className="text-faint line-clamp-2 block text-[11px] leading-snug">
                {a.reason}
              </span>
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}

export function TakeCard({ f, now }: { f: Forecast; now: number }) {
  const items = whatToTake(f, now);
  if (items.length === 0) return null;
  return (
    <Card title="Before you head out" icon={<Backpack size={13} />} label="What to take">
      <div className="flex flex-wrap gap-2">
        {items.map((i) => (
          <span key={i} className="glass-pill rounded-full px-3 py-1.5 text-sm">
            {i}
          </span>
        ))}
      </div>
    </Card>
  );
}

export function DaylightTile({ f, now }: { f: Forecast; now: number }) {
  const d = today(f, now);
  const change = daylightChange(f, now);
  return (
    <Tile title="Daylight" icon={<Sun size={13} />}>
      <div className="text-3xl font-semibold">{fmtDuration(d.daylight)}</div>
      <div className="text-dim text-sm">{Math.round(d.sunshine / 3600)} h of sunshine</div>
      {change && <p className="mt-auto text-[13px] leading-snug">{change}.</p>}
    </Tile>
  );
}
