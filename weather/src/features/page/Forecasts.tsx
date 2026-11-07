import { CalendarDays, Clock, Sunrise, Sunset, Droplet } from "lucide-react";
import type { Forecast } from "@/data/types";
import { Card, RangeBar } from "@/components/ui";
import { WeatherIcon } from "@/icons/WeatherIcon";
import { conditionIcon, conditionLabel } from "@/weather/codes";
import { fmtHour, fmtTime, weekday } from "@/lib/time";
import { headline, todayIndex, upcomingHours } from "@/weather/insights";
import type { Fmt } from "@/lib/units";

type Item =
  | {
      kind: "hour";
      time: number;
      label: string;
      code: number;
      isDay: boolean;
      temp: number;
      pop: number;
    }
  | { kind: "sun"; time: number; label: string; rise: boolean };

export function HourlyCard({
  f,
  fmt,
  now,
  onOpen,
}: {
  f: Forecast;
  fmt: Fmt;
  now: number;
  onOpen: () => void;
}) {
  const hours = upcomingHours(f, now, 25);
  const off = f.utcOffset;
  const items: Item[] = hours.map((h, i) => ({
    kind: "hour",
    time: h.time,
    label: i === 0 ? "Now" : fmtHour(h.time, off, fmt.s.h24),
    code: i === 0 ? f.current.code : h.code,
    isDay: i === 0 ? f.current.isDay : h.isDay,
    temp: i === 0 ? f.current.temp : h.temp,
    pop: h.pop,
  }));
  const end = hours[hours.length - 1]?.time ?? now;
  for (const d of f.daily) {
    for (const [t, rise] of [
      [d.sunrise, true],
      [d.sunset, false],
    ] as const) {
      if (t > now && t < end)
        items.push({ kind: "sun", time: t, label: fmtTime(t, off, fmt.s.h24), rise });
    }
  }
  items.sort((a, b) => a.time - b.time);

  return (
    <section className="glass fade-up overflow-hidden" aria-label="Hourly forecast">
      <button type="button" onClick={onOpen} className="block w-full px-4 pt-3.5 pb-2 text-left">
        <p className="text-[15px] leading-snug">{headline(f, now, fmt)}</p>
      </button>
      <div className="mx-4 border-t hairline" />
      <div className="card-title px-4 pt-2.5">
        <Clock size={13} /> <span>Hourly forecast</span>
      </div>
      <div className="no-scrollbar flex gap-1 overflow-x-auto px-2 pt-2 pb-3.5">
        {items.map((it) =>
          it.kind === "hour" ? (
            <button
              type="button"
              onClick={onOpen}
              key={`h${it.time}`}
              className="flex w-[54px] shrink-0 flex-col items-center gap-1.5"
            >
              <span className={`text-[13px] ${it.label === "Now" ? "font-semibold" : "text-dim"}`}>
                {it.label}
              </span>
              <span className="flex h-[42px] flex-col items-center justify-center">
                <WeatherIcon
                  icon={conditionIcon(it.code, it.isDay)}
                  size={30}
                  title={conditionLabel(it.code, it.isDay)}
                />
                {it.pop >= 20 && (
                  <span className="text-[10.5px] font-semibold text-sky-300">{it.pop}%</span>
                )}
              </span>
              <span className="text-[17px] font-medium">{fmt.temp(it.temp)}</span>
            </button>
          ) : (
            <div
              key={`s${it.time}`}
              className="flex w-[54px] shrink-0 flex-col items-center gap-1.5"
            >
              <span className="text-dim text-[13px]">{it.label}</span>
              <span className="flex h-[42px] items-center">
                {it.rise ? (
                  <Sunrise size={24} className="text-amber-300" />
                ) : (
                  <Sunset size={24} className="text-orange-300" />
                )}
              </span>
              <span className="text-[13px] font-medium">{it.rise ? "Sunrise" : "Sunset"}</span>
            </div>
          ),
        )}
      </div>
    </section>
  );
}

export function DailyCard({
  f,
  fmt,
  now,
  onOpenDay,
}: {
  f: Forecast;
  fmt: Fmt;
  now: number;
  onOpenDay: (index: number) => void;
}) {
  const ti = todayIndex(f, now);
  const days = f.daily.slice(ti);
  const lo = Math.min(...days.map((d) => d.min));
  const hi = Math.max(...days.map((d) => d.max));
  const off = f.utcOffset;
  return (
    <Card
      title={`${days.length}-day forecast`}
      icon={<CalendarDays size={13} />}
      label="Daily forecast"
    >
      <ul>
        {days.map((d, i) => (
          <li key={d.time} className={i ? "border-t hairline" : ""}>
            <button
              type="button"
              onClick={() => onOpenDay(ti + i)}
              className="flex w-full items-center gap-2 py-2.5 text-left"
              aria-label={`${i === 0 ? "Today" : weekday(d.time, off)}: ${conditionLabel(d.code)}, high ${fmt.temp(d.max)}, low ${fmt.temp(d.min)}`}
            >
              <span className="w-[52px] text-[16px] font-medium">
                {i === 0 ? "Today" : weekday(d.time, off, true)}
              </span>
              <span className="flex w-[44px] flex-col items-center">
                <WeatherIcon icon={conditionIcon(d.code)} size={28} />
                {d.pop >= 20 && (
                  <span className="flex items-center text-[10.5px] font-semibold text-sky-300">
                    <Droplet size={8} className="mr-0.5" fill="currentColor" />
                    {d.pop}%
                  </span>
                )}
              </span>
              <span className="text-dim w-[38px] text-right text-[16px]">{fmt.temp(d.min)}</span>
              <RangeBar
                lo={lo}
                hi={hi}
                min={d.min}
                max={d.max}
                dot={i === 0 ? f.current.temp : undefined}
              />
              <span className="w-[38px] text-right text-[16px] font-medium">{fmt.temp(d.max)}</span>
            </button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
