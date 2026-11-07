import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import {
  CloudLightning,
  Layers,
  LocateFixed,
  Pause,
  Play,
  TriangleAlert,
  Umbrella,
  X,
  Thermometer,
} from "lucide-react";
import type { Place, SawsCity, SawsWarning } from "@/data/types";
import { useCached } from "@/data/cache";
import { fetchSawsCities, inSouthAfrica } from "@/data/saws";
import { useSawsBundle } from "@/state/useWeather";
import { useStore } from "@/state/store";
import { formatter } from "@/lib/units";
import { getJson } from "@/data/http";
import { useNow } from "@/lib/useNow";

type RadarFrames = { host: string; frames: { time: number; path: string }[] };

async function fetchRadar(): Promise<RadarFrames> {
  const r = await getJson<{
    host: string;
    radar: { past: { time: number; path: string }[]; nowcast?: { time: number; path: string }[] };
  }>("https://api.rainviewer.com/public/weather-maps.json");
  return { host: r.host, frames: [...r.radar.past, ...(r.radar.nowcast ?? [])] };
}

const WARN_COLOR: Record<SawsWarning["color"], string> = {
  yellow: "#facc15",
  orange: "#fb923c",
  red: "#ef4444",
  advisory: "#c084fc",
};

const STORM_COLOR = { current: "#ef4444", forecast30: "#fb923c", forecast60: "#facc15" } as const;

export default function MapScreen({ place, onClose }: { place?: Place; onClose: () => void }) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const radarLayers = useRef<L.TileLayer[]>([]);
  const overlay = useRef<L.LayerGroup | null>(null);
  const [frame, setFrame] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [layers, setLayers] = useState({ radar: true, warnings: true, storms: true, cities: true });
  const [showLayers, setShowLayers] = useState(false);
  const [info, setInfo] = useState<string | null>(null);
  const settings = useStore((s) => s.settings);
  const fmt = formatter(settings);
  const radar = useCached("radar", fetchRadar, 5 * 60_000, false);
  const bundle = useSawsBundle(true);
  const cities = useCached<SawsCity[]>("saws:cities", fetchSawsCities, 60 * 60_000);
  const frames = radar.data?.frames ?? [];
  const nowS = useNow(30_000);

  useEffect(() => {
    const onBack = (e: Event) => {
      e.preventDefault();
      onClose();
    };
    window.addEventListener("sg:back", onBack);
    return () => window.removeEventListener("sg:back", onBack);
  }, [onClose]);

  // Create the map once.
  useEffect(() => {
    if (!el.current || map.current) return;
    const sa = !place || inSouthAfrica(place.lat, place.lon);
    const m = L.map(el.current, {
      zoomControl: false,
      attributionControl: true,
      center: place ? [place.lat, place.lon] : [-29, 25],
      zoom: place ? (sa ? 7 : 8) : 5,
      minZoom: 3,
      maxZoom: 12,
    });
    L.tileLayer("https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png", {
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a> · Radar <a href="https://www.rainviewer.com/">RainViewer</a> · <a href="https://www.weathersa.co.za">SAWS</a>',
      subdomains: "abcd",
      maxZoom: 19,
    }).addTo(m);
    overlay.current = L.layerGroup().addTo(m);
    if (place) {
      L.circleMarker([place.lat, place.lon], {
        interactive: false,
        radius: 7,
        color: "#fff",
        weight: 3,
        fillColor: "#2f8cff",
        fillOpacity: 1,
      }).addTo(m);
    }
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
    };
  }, [place]);

  // Radar frames: one tile layer per frame, only the current one visible.
  useEffect(() => {
    const m = map.current;
    if (!m || !radar.data) return;
    radarLayers.current.forEach((l) => l.remove());
    radarLayers.current = radar.data.frames.map((f) =>
      L.tileLayer(`${radar.data!.host}${f.path}/256/{z}/{x}/{y}/2/1_1.png`, {
        opacity: 0,
        zIndex: 5,
        maxNativeZoom: 7,
        maxZoom: 12,
      }).addTo(m),
    );
    setFrame(radar.data.frames.length - 1);
    return () => radarLayers.current.forEach((l) => l.remove());
  }, [radar.data]);

  useEffect(() => {
    radarLayers.current.forEach((l, i) => l.setOpacity(layers.radar && i === frame ? 0.75 : 0));
  }, [frame, layers.radar, radar.data]);

  useEffect(() => {
    if (!playing || frames.length < 2) return;
    const t = setInterval(() => setFrame((f) => (f + 1) % frames.length), 700);
    return () => clearInterval(t);
  }, [playing, frames.length]);

  // SAWS overlays.
  useEffect(() => {
    const g = overlay.current;
    if (!g) return;
    g.clearLayers();
    const data = bundle.data;
    if (data && layers.warnings) {
      for (const w of data.warnings) {
        for (const ring of w.polygons) {
          L.polygon(ring, { color: WARN_COLOR[w.color], weight: 1.5, fillOpacity: 0.28 })
            .on("click", () =>
              setInfo(`${w.event}${w.level ? ` (level ${w.level})` : ""}: ${w.headline}`),
            )
            .addTo(g);
        }
      }
    }
    if (data && layers.storms) {
      for (const s of data.storms) {
        L.polygon(s.polygon, {
          color: STORM_COLOR[s.instance],
          weight: s.instance === "current" ? 2 : 1,
          dashArray: s.instance === "current" ? undefined : "4 4",
          fillOpacity: s.instance === "current" ? 0.35 : 0.12,
        })
          .on("click", () =>
            setInfo(
              s.instance === "current"
                ? "Thunderstorm cell (SAWS radar, now)"
                : `Where this storm cell is expected in ${s.instance === "forecast30" ? "30" : "60"} minutes`,
            ),
          )
          .addTo(g);
      }
    }
    if (cities.data && layers.cities) {
      for (const c of cities.data) {
        const html = `<div style="transform:translate(-50%,-50%);white-space:nowrap" class="glass-pill rounded-full px-2 py-0.5 text-[11px] font-semibold text-white">${fmt.temp(c.max)} <span style="opacity:.7;font-weight:500">${c.station.name.replace(/ (city|WO|AWS)$/i, "")}</span></div>`;
        L.marker([c.station.lat, c.station.lon], {
          icon: L.divIcon({ html, className: "", iconSize: [0, 0] }),
        })
          .on("click", () =>
            setInfo(
              `${c.station.name}: ${c.description}, ${fmt.temp(c.min)} to ${fmt.temp(c.max)} (SAWS)`,
            ),
          )
          .addTo(g);
      }
    }
  }, [bundle.data, cities.data, layers, fmt]);

  const time = frames[frame]?.time;
  const isFuture = time != null && time > nowS;
  const stormCount = bundle.data?.storms.filter((s) => s.instance === "current").length ?? 0;

  return (
    <div
      className="fixed inset-0 z-40 bg-[#0b1730]"
      style={{ animation: "backdrop-in .2s ease both" }}
    >
      <div ref={el} className="absolute inset-0" />

      <div
        className="pointer-events-none absolute inset-x-0 top-0 flex items-start justify-between p-3"
        style={{ paddingTop: "calc(var(--safe-top) + 10px)" }}
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="Close map"
          className="glass-strong pointer-events-auto grid h-10 w-10 place-items-center rounded-full"
        >
          <X size={20} />
        </button>
        <div className="pointer-events-auto flex flex-col gap-2">
          <button
            type="button"
            aria-label="Map layers"
            onClick={() => setShowLayers((s) => !s)}
            className="glass-strong grid h-10 w-10 place-items-center rounded-full"
          >
            <Layers size={19} />
          </button>
          {place && (
            <button
              type="button"
              aria-label="Centre on location"
              onClick={() => map.current?.setView([place.lat, place.lon], 8)}
              className="glass-strong grid h-10 w-10 place-items-center rounded-full"
            >
              <LocateFixed size={19} />
            </button>
          )}
        </div>
      </div>

      {showLayers && (
        <div
          className="glass-strong absolute right-3 z-[1000] w-56 rounded-2xl p-2"
          style={{ top: "calc(var(--safe-top) + 110px)" }}
        >
          {(
            [
              ["radar", "Rain radar", Umbrella],
              ["storms", "Thunderstorm cells (SAWS)", CloudLightning],
              ["warnings", "Warnings (SAWS)", TriangleAlert],
              ["cities", "Today's highs (SAWS)", Thermometer],
            ] as const
          ).map(([k, label, Icon]) => (
            <button
              key={k}
              type="button"
              aria-pressed={layers[k]}
              onClick={() => setLayers((l) => ({ ...l, [k]: !l[k] }))}
              className={`flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm ${layers[k] ? "bg-white/15" : "opacity-60"}`}
            >
              <Icon size={16} /> {label}
            </button>
          ))}
        </div>
      )}

      {info && (
        <button
          type="button"
          onClick={() => setInfo(null)}
          className="glass-strong absolute inset-x-3 z-[1000] rounded-2xl p-3 text-left text-sm"
          style={{ top: "calc(var(--safe-top) + 64px)" }}
        >
          {info}
        </button>
      )}

      <div
        className="absolute inset-x-3 z-[1000]"
        style={{ bottom: "calc(var(--safe-bottom) + 14px)" }}
      >
        <div className="glass-strong rounded-2xl p-3">
          <div className="mb-2 flex items-center justify-between text-xs">
            <span className="font-semibold">
              {layers.radar
                ? time
                  ? `${isFuture ? "Forecast" : "Radar"} ${new Date(time * 1000).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: !settings.h24 })}`
                  : radar.error
                    ? "Radar unavailable"
                    : "Loading radar…"
                : "Radar off"}
            </span>
            <span className="text-dim">
              {stormCount > 0
                ? `${stormCount} active storm cell${stormCount > 1 ? "s" : ""}`
                : bundle.data
                  ? "No active storm cells"
                  : ""}
            </span>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              aria-label={playing ? "Pause radar" : "Play radar"}
              onClick={() => setPlaying((p) => !p)}
              className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white text-slate-900"
            >
              {playing ? (
                <Pause size={16} fill="currentColor" />
              ) : (
                <Play size={16} fill="currentColor" />
              )}
            </button>
            <input
              type="range"
              min={0}
              max={Math.max(0, frames.length - 1)}
              value={frame}
              aria-label="Radar time"
              onChange={(e) => {
                setPlaying(false);
                setFrame(Number(e.target.value));
              }}
              className="flex-1"
            />
          </div>
          <div className="text-faint mt-2 flex items-center gap-3 text-[10px]">
            <Legend color="#ef4444" label="Storm now" />
            <Legend color="#fb923c" label="+30 min" dashed />
            <Legend color="#facc15" label="Warning" />
            <span className="ml-auto">Radar: light → heavy</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function Legend({ color, label, dashed }: { color: string; label: string; dashed?: boolean }) {
  return (
    <span className="flex items-center gap-1">
      <span
        className="inline-block h-2.5 w-2.5 rounded-sm"
        style={{
          background: `${color}55`,
          border: `1.5px ${dashed ? "dashed" : "solid"} ${color}`,
        }}
      />
      {label}
    </span>
  );
}
