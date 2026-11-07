import { useEffect, useRef, useState } from "react";
import { Navigation, RefreshCw, Share2, WifiOff } from "lucide-react";
import type { Place } from "@/data/types";
import {
  useAirQuality,
  useForecast,
  useLocalAlerts,
  useMarine,
  useSawsForecast,
} from "@/state/useWeather";
import { useStore } from "@/state/store";
import { formatter } from "@/lib/units";
import { conditionLabel } from "@/weather/codes";
import { today, vsYesterday } from "@/weather/insights";
import { ago } from "@/lib/time";
import { useNow } from "@/lib/useNow";
import { Spinner } from "@/components/ui";
import { HourlyCard, DailyCard } from "./Forecasts";
import { NowcastCard, StormCard, WarningsCard } from "./Alerts";
import { SawsCard } from "./SawsCard";
import {
  AirTile,
  FeelsTile,
  FireTile,
  HumidityTile,
  LifeCard,
  MarineTile,
  MoonTile,
  PrecipTile,
  PressureTile,
  SunTile,
  TakeCard,
  UvTile,
  VisibilityTile,
  WindTile,
  DaylightTile,
} from "./Tiles";
import { DetailSheet, type Metric } from "@/features/detail/DetailSheet";
import { shareText } from "@/platform/native";

export function LocationPage({
  place,
  active,
  near,
}: {
  place: Place;
  active: boolean;
  near: boolean;
}) {
  const settings = useStore((s) => s.settings);
  const fmt = formatter(settings);
  const fc = useForecast(near ? place : undefined);
  const air = useAirQuality(active ? place : undefined);
  const marine = useMarine(active ? place : undefined);
  const saws = useSawsForecast(active ? place : undefined);
  const alerts = useLocalAlerts(active ? place : undefined);
  const scroller = useRef<HTMLDivElement>(null);
  const [scroll, setScroll] = useState(0);
  const [detail, setDetail] = useState<{ metric: Metric; day?: number } | null>(null);
  const [pull, setPull] = useState(0);
  const [toast, setToast] = useState<string | null>(null);
  const now = useNow();
  const f = fc.data;

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const refresh = async () => {
    await Promise.all([
      fc.refresh(),
      air.refresh(),
      marine.refresh(),
      saws.refresh(),
      alerts.bundle.refresh(),
    ]);
  };

  // Pull to refresh.
  const startY = useRef<number | null>(null);
  const onTouchStart = (e: React.TouchEvent) => {
    startY.current =
      scroller.current && scroller.current.scrollTop <= 0 ? e.touches[0].clientY : null;
  };
  const onTouchMove = (e: React.TouchEvent) => {
    if (startY.current == null) return;
    const d = e.touches[0].clientY - startY.current;
    setPull(d > 0 ? Math.min(90, d * 0.5) : 0);
  };
  const onTouchEnd = () => {
    if (pull > 60) void refresh();
    setPull(0);
    startY.current = null;
  };

  if (!near) return <div className="h-full" />;

  if (!f) {
    return (
      <div className="grid h-full place-items-center px-8 text-center">
        <div>
          <Title place={place} />
          {fc.error ? (
            <div className="mt-6 space-y-3">
              <WifiOff className="mx-auto" />
              <p className="text-dim">Couldn't load the forecast. Check your connection.</p>
              <button
                type="button"
                onClick={() => void fc.refresh()}
                className="glass-pill rounded-full px-4 py-2 text-sm"
              >
                Try again
              </button>
            </div>
          ) : (
            <div className="mt-6">
              <Spinner size={26} />
            </div>
          )}
        </div>
      </div>
    );
  }

  const d = today(f, now);
  const c = f.current;
  const loading = fc.loading || saws.loading;
  const collapse = Math.min(
    1,
    scroll / Math.max(1, (place.isCurrent ? 278 : 262) - (place.isCurrent ? 104 : 88)),
  );
  const aqiValue = air.data ? (settings.aqi === "us" ? air.data.usAqi : air.data.euAqi) : null;

  const share = async () => {
    const text = `${place.name}: ${fmt.temp(c.temp)} ${conditionLabel(c.code, c.isDay).toLowerCase()}, H ${fmt.temp(d.max)} L ${fmt.temp(d.min)}, ${d.pop}% chance of rain. Feels like ${fmt.temp(c.feels)}, wind ${fmt.wind(c.wind)}. (Skyglass Weather)`;
    const r = await shareText(`Weather in ${place.name}`, text);
    if (r === "copied") setToast("Copied to clipboard");
  };

  // The header is an overlay that shrinks from the full view to one line over the first
  // HEADER_FULL - HEADER_MIN pixels of scrolling; cards slide under it and fade out.
  const HEADER_FULL = place.isCurrent ? 278 : 262;
  const HEADER_MIN = place.isCurrent ? 104 : 88;
  const collapseBy = HEADER_FULL - HEADER_MIN;
  const headerH = HEADER_FULL - Math.min(scroll, collapseBy);
  const fade = `linear-gradient(to bottom, transparent calc(var(--safe-top) + ${headerH - 14}px), black calc(var(--safe-top) + ${headerH + 6}px))`;

  return (
    <div className="relative h-full min-w-0">
      <header
        className="shadow-text pointer-events-none absolute inset-x-0 top-0 z-10 overflow-hidden px-4 text-center"
        style={{
          paddingTop: "calc(var(--safe-top) + 22px)",
          height: `calc(var(--safe-top) + ${headerH}px)`,
        }}
      >
        <Title place={place} />
        <div
          style={{
            opacity: Math.max(0, 1 - collapse * 1.6),
            transform: `translateY(${-collapse * 30}px)`,
          }}
        >
          <div
            className="text-[96px] leading-[1.02] font-extralight tracking-tight"
            data-testid="current-temp"
          >
            {fmt.temp(c.temp)}
          </div>
          <div className="text-[19px] font-medium">{conditionLabel(c.code, c.isDay)}</div>
          <div className="text-[17px] font-medium">
            H:{fmt.temp(d.max)}&nbsp;&nbsp;L:{fmt.temp(d.min)}
          </div>
          <div className="text-dim mt-1.5 text-[13px]">
            {vsYesterday(f, now, fmt)}{" "}
            <span className="text-faint">Feels like {fmt.temp(c.feels)}.</span>
          </div>
        </div>
        <div
          className="text-dim absolute inset-x-0 text-[16px] font-medium"
          style={{
            opacity: Math.max(0, collapse * 2 - 1),
            top: `calc(var(--safe-top) + ${HEADER_MIN - 34}px)`,
          }}
        >
          {fmt.temp(c.temp)} | {conditionLabel(c.code, c.isDay)}
        </div>
      </header>

      {(pull > 0 || loading) && (
        <div
          className="absolute inset-x-0 z-20 flex justify-center"
          style={{ top: "calc(var(--safe-top) + 4px)" }}
        >
          {loading ? (
            <Spinner size={16} />
          ) : (
            <RefreshCw
              size={16}
              style={{ transform: `rotate(${pull * 4}deg)`, opacity: pull / 60 }}
            />
          )}
        </div>
      )}

      <div
        ref={scroller}
        className="no-scrollbar h-full overflow-y-auto overscroll-contain"
        style={{ maskImage: fade, WebkitMaskImage: fade }}
        onScroll={(e) => setScroll(e.currentTarget.scrollTop)}
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        data-testid="location-page"
      >
        <div
          className="mx-auto max-w-xl px-4"
          style={{
            paddingTop: `calc(var(--safe-top) + ${HEADER_FULL + pull}px)`,
            paddingBottom: "calc(var(--safe-bottom) + 84px)",
            transition: pull ? undefined : "padding .25s",
          }}
        >
          <div className="space-y-3">
            <WarningsCard warnings={alerts.warnings} />
            {alerts.storm && <StormCard storm={alerts.storm} fmt={fmt} />}
            <NowcastCard f={f} fmt={fmt} now={now} />
            <HourlyCard
              f={f}
              fmt={fmt}
              now={now}
              onOpen={() => setDetail({ metric: "conditions" })}
            />
            <DailyCard
              f={f}
              fmt={fmt}
              now={now}
              onOpenDay={(day) => setDetail({ metric: "conditions", day })}
            />
            {saws.data && <SawsCard saws={saws.data} fmt={fmt} />}
            <TakeCard f={f} now={now} />
            <div className="grid grid-cols-2 gap-3">
              {air.data && (
                <AirTile air={air.data} fmt={fmt} onOpen={(m) => setDetail({ metric: m })} />
              )}
              <UvTile f={f} fmt={fmt} now={now} onOpen={(m) => setDetail({ metric: m })} />
              <SunTile f={f} fmt={fmt} now={now} />
              <WindTile f={f} fmt={fmt} onOpen={(m) => setDetail({ metric: m })} />
              <PrecipTile f={f} fmt={fmt} now={now} onOpen={(m) => setDetail({ metric: m })} />
              <FeelsTile f={f} fmt={fmt} onOpen={(m) => setDetail({ metric: m })} />
              <HumidityTile f={f} onOpen={(m) => setDetail({ metric: m })} />
              <VisibilityTile f={f} fmt={fmt} onOpen={(m) => setDetail({ metric: m })} />
              {saws.data && <FireTile saws={saws.data} />}
              <PressureTile f={f} fmt={fmt} now={now} onOpen={(m) => setDetail({ metric: m })} />
              {!saws.data && <DaylightTile f={f} now={now} />}
              {marine.data && <MarineTile marine={marine.data} fmt={fmt} offset={f.utcOffset} />}
              <MoonTile f={f} place={place} saws={saws.data} fmt={fmt} now={now} />
            </div>
            <LifeCard f={f} now={now} aqi={aqiValue} />
          </div>

          <footer className="text-faint mt-6 space-y-1.5 text-center text-[11px]">
            <div>
              Updated {ago(f.fetchedAt)}
              {fc.error && " · offline, showing last forecast"}
            </div>
            <div>
              Forecast: Open-Meteo · Official forecasts &amp; warnings:{" "}
              <a
                className="underline"
                href="https://www.weathersa.co.za"
                target="_blank"
                rel="noreferrer"
              >
                SAWS
              </a>
            </div>
            <div className="flex justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => void refresh()}
                className="glass-pill flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-white"
              >
                <RefreshCw size={12} /> Refresh
              </button>
              <button
                type="button"
                onClick={() => void share()}
                className="glass-pill flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-white"
              >
                <Share2 size={12} /> Share
              </button>
            </div>
          </footer>
        </div>
      </div>

      {detail && (
        <DetailSheet
          f={f}
          air={air.data}
          fmt={fmt}
          now={now}
          initialMetric={detail.metric}
          initialDay={detail.day}
          onClose={() => setDetail(null)}
        />
      )}
      {toast && (
        <div className="glass-strong fixed bottom-24 left-1/2 z-50 -translate-x-1/2 rounded-full px-4 py-2 text-sm">
          {toast}
        </div>
      )}
    </div>
  );
}

function Title({ place }: { place: Place }) {
  return (
    <div>
      {place.isCurrent && (
        <div className="text-dim flex items-center justify-center gap-1 text-[11px] font-semibold tracking-wider uppercase">
          <Navigation size={10} fill="currentColor" /> My location
        </div>
      )}
      <h1 className="text-[30px] leading-tight font-normal" data-testid="place-name">
        {place.name}
      </h1>
    </div>
  );
}
