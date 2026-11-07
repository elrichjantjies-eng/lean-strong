import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  ChevronLeft,
  MapPin,
  Navigation,
  Search,
  Trash2,
  X,
} from "lucide-react";
import type { Place } from "@/data/types";
import { useStore } from "@/state/store";
import { useForecast } from "@/state/useWeather";
import { searchPlaces } from "@/data/geocode";
import { formatter } from "@/lib/units";
import { conditionLabel } from "@/weather/codes";
import { fmtTime, nowSec } from "@/lib/time";
import { today } from "@/weather/insights";
import { skyColors } from "@/components/Sky";
import { skyTheme } from "@/weather/sky";
import { Spinner } from "@/components/ui";
import { WeatherIcon } from "@/icons/WeatherIcon";
import { conditionIcon } from "@/weather/codes";

const SUGGESTIONS: Place[] = [
  {
    id: "-33.925,18.424",
    name: "Cape Town",
    admin: "Western Cape",
    countryCode: "ZA",
    lat: -33.9249,
    lon: 18.4241,
  },
  {
    id: "-26.204,28.047",
    name: "Johannesburg",
    admin: "Gauteng",
    countryCode: "ZA",
    lat: -26.2041,
    lon: 28.0473,
  },
  {
    id: "-29.858,31.022",
    name: "Durban",
    admin: "KwaZulu-Natal",
    countryCode: "ZA",
    lat: -29.8587,
    lon: 31.0218,
  },
  {
    id: "-25.747,28.229",
    name: "Pretoria",
    admin: "Gauteng",
    countryCode: "ZA",
    lat: -25.7479,
    lon: 28.2293,
  },
  {
    id: "-33.961,25.602",
    name: "Gqeberha",
    admin: "Eastern Cape",
    countryCode: "ZA",
    lat: -33.9608,
    lon: 25.6022,
  },
  {
    id: "-29.085,26.159",
    name: "Bloemfontein",
    admin: "Free State",
    countryCode: "ZA",
    lat: -29.0852,
    lon: 26.1596,
  },
];

export function Locations({
  onClose,
  onOpen,
  onLocate,
  locating,
}: {
  onClose: () => void;
  onOpen: (i: number) => void;
  onLocate: () => void;
  locating: boolean;
}) {
  const places = useStore((s) => s.places);
  const addPlace = useStore((s) => s.addPlace);
  const removePlace = useStore((s) => s.removePlace);
  const movePlace = useStore((s) => s.movePlace);
  const setCurrent = useStore((s) => s.setCurrent);
  const setGpsOff = useStore((s) => s.setGpsOff);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Place[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState(false);
  const [editing, setEditing] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onBack = (e: Event) => {
      e.preventDefault();
      if (query) setQuery("");
      else onClose();
    };
    window.addEventListener("sg:back", onBack);
    return () => window.removeEventListener("sg:back", onBack);
  }, [query, onClose]);

  useEffect(() => {
    if (query.trim().length < 2) return;
    const t = setTimeout(() => {
      setSearching(true);
      searchPlaces(query)
        .then((r) => {
          setResults(r);
          setError(false);
        })
        .catch(() => setError(true))
        .finally(() => setSearching(false));
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const add = (p: Place) => {
    addPlace(p);
    setQuery("");
    onOpen(useStore.getState().active);
  };
  const hasGps = places.some((p) => p.isCurrent);
  const shown = query.trim().length >= 2 ? results : null;

  return (
    <div
      className="fixed inset-0 z-30 flex flex-col bg-[#0b1730]/80 backdrop-blur-2xl"
      style={{ animation: "backdrop-in .2s ease both" }}
    >
      <div
        className="mx-auto w-full max-w-xl px-4"
        style={{ paddingTop: "calc(var(--safe-top) + 12px)" }}
      >
        <div className="flex items-center justify-between">
          {places.length > 0 ? (
            <button
              type="button"
              onClick={onClose}
              className="flex items-center gap-1 py-2 text-[15px]"
              aria-label="Back to forecast"
            >
              <ChevronLeft size={20} /> Forecast
            </button>
          ) : (
            <span />
          )}
          {places.length > 0 && (
            <button
              type="button"
              onClick={() => setEditing((e) => !e)}
              className="py-2 text-[15px] font-semibold"
            >
              {editing ? "Done" : "Edit"}
            </button>
          )}
        </div>
        <h1 className="mt-1 text-[32px] font-bold">Weather</h1>
        <div className="glass-pill mt-3 flex items-center gap-2 rounded-xl px-3 py-2">
          <Search size={17} className="text-white/60" />
          <input
            ref={input}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search for a city or suburb"
            aria-label="Search for a city"
            className="min-w-0 flex-1 bg-transparent text-[16px] outline-none placeholder:text-white/50"
            style={{ userSelect: "text" }}
          />
          {searching && <Spinner size={14} />}
          {query && (
            <button type="button" aria-label="Clear search" onClick={() => setQuery("")}>
              <X size={16} />
            </button>
          )}
        </div>
      </div>

      <div
        className="no-scrollbar mx-auto w-full max-w-xl flex-1 overflow-y-auto px-4 pt-3"
        style={{ paddingBottom: "calc(var(--safe-bottom) + 24px)" }}
      >
        {shown ? (
          <ul className="space-y-1" aria-label="Search results">
            {error && <li className="text-dim py-3 text-sm">Search is unavailable offline.</li>}
            {shown.length === 0 && !error && (
              <li className="text-dim py-3 text-sm">No places found.</li>
            )}
            {shown.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => add(p)}
                  className="flex w-full items-center gap-3 rounded-xl px-2 py-2.5 text-left hover:bg-white/10"
                >
                  <MapPin size={18} className="text-white/60" />
                  <span>
                    <span className="block text-[16px]">{p.name}</span>
                    <span className="text-faint block text-xs">
                      {[p.admin, p.country].filter(Boolean).join(", ")}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <>
            <ul className="space-y-2.5">
              {places.map((p, i) => (
                <li key={p.id + (p.isCurrent ? ":gps" : "")} className="flex items-center gap-2">
                  {editing && !p.isCurrent && (
                    <button
                      type="button"
                      aria-label={`Remove ${p.name}`}
                      onClick={() => removePlace(p.id)}
                      className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-red-500/80"
                    >
                      <Trash2 size={16} />
                    </button>
                  )}
                  {editing && p.isCurrent && (
                    <button
                      type="button"
                      aria-label="Turn off My Location"
                      onClick={() => {
                        setGpsOff(true);
                        setCurrent(null);
                      }}
                      className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-red-500/80"
                    >
                      <Trash2 size={16} />
                    </button>
                  )}
                  <PlaceCard place={p} onClick={() => !editing && onOpen(i)} />
                  {editing && !p.isCurrent && (
                    <div className="flex flex-col">
                      <button
                        type="button"
                        aria-label={`Move ${p.name} up`}
                        disabled={i === 0 || places[i - 1]?.isCurrent}
                        onClick={() => movePlace(i, i - 1)}
                        className="p-1.5 disabled:opacity-25"
                      >
                        <ArrowUp size={18} />
                      </button>
                      <button
                        type="button"
                        aria-label={`Move ${p.name} down`}
                        disabled={i === places.length - 1}
                        onClick={() => movePlace(i, i + 1)}
                        className="p-1.5 disabled:opacity-25"
                      >
                        <ArrowDown size={18} />
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>

            {!hasGps && (
              <button
                type="button"
                onClick={onLocate}
                className="glass-pill mt-4 flex w-full items-center justify-center gap-2 rounded-2xl py-3 text-[15px]"
              >
                {locating ? <Spinner size={16} /> : <Navigation size={16} />} Use my location
              </button>
            )}

            <div className="card-title mt-6 mb-2">Popular in South Africa</div>
            <div className="flex flex-wrap gap-2">
              {SUGGESTIONS.filter((s) => !places.some((p) => p.id === s.id)).map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => add(s)}
                  className="glass-pill rounded-full px-3.5 py-1.5 text-sm"
                >
                  + {s.name}
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/** A location card with its own sky gradient, like the iPhone's list. */
function PlaceCard({ place, onClick }: { place: Place; onClick: () => void }) {
  const settings = useStore((s) => s.settings);
  const fmt = useMemo(() => formatter(settings), [settings]);
  const fc = useForecast(place);
  const f = fc.data;
  const [top, bottom] = skyColors(skyTheme(f));
  const now = nowSec();
  const d = f ? today(f, now) : null;
  return (
    <button
      type="button"
      onClick={onClick}
      className="relative min-w-0 flex-1 overflow-hidden rounded-[22px] border border-white/15 p-4 text-left shadow-lg"
      style={{ background: `linear-gradient(135deg, ${top}, ${bottom})` }}
      aria-label={`${place.name}${f ? `, ${fmt.temp(f.current.temp)}` : ""}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-[21px] leading-tight font-bold">
            {place.isCurrent ? "My Location" : place.name}
          </div>
          <div className="truncate text-[13px] font-medium text-white/85">
            {place.isCurrent
              ? place.name
              : f
                ? fmtTime(now, f.utcOffset, settings.h24)
                : place.admin}
          </div>
        </div>
        <div className="text-[46px] leading-none font-light">
          {f ? fmt.temp(f.current.temp) : "–"}
        </div>
      </div>
      <div className="mt-5 flex items-end justify-between text-[13px] font-medium">
        <span className="flex items-center gap-1.5">
          {f && <WeatherIcon icon={conditionIcon(f.current.code, f.current.isDay)} size={20} />}
          {f ? conditionLabel(f.current.code, f.current.isDay) : fc.loading ? "Loading…" : ""}
        </span>
        {d && (
          <span>
            H:{fmt.temp(d.max)} L:{fmt.temp(d.min)}
          </span>
        )}
      </div>
    </button>
  );
}
