import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { List, Map as MapIcon, Navigation, Search, Settings as Cog } from "lucide-react";
import { App as CapApp } from "@capacitor/app";
import { useStore } from "@/state/store";
import { useForecast } from "@/state/useWeather";
import { Sky } from "@/components/Sky";
import { skyTheme } from "@/weather/sky";
import { LocationPage } from "@/features/page/LocationPage";
import { Locations } from "@/features/locations/Locations";
import { Settings } from "@/features/settings/Settings";
import { getPosition, isNative, syncWidgets } from "@/platform/native";
import { reverseGeocode, placeId } from "@/data/geocode";
import { nearestStation } from "@/data/saws";
import { distanceKm } from "@/lib/geo";
import type { Place } from "@/data/types";
import { Spinner } from "@/components/ui";

const MapScreen = lazy(() => import("@/features/map/MapScreen"));

type Screen = "home" | "list" | "map" | "settings";

export function App() {
  const places = useStore((s) => s.places);
  const active = useStore((s) => s.active);
  const setActive = useStore((s) => s.setActive);
  const settings = useStore((s) => s.settings);
  const gpsOff = useStore((s) => s.gpsOff);
  const setCurrent = useStore((s) => s.setCurrent);
  const [screen, setScreen] = useState<Screen>("home");
  const [locating, setLocating] = useState(false);
  const [gpsError, setGpsError] = useState(false);
  const pager = useRef<HTMLDivElement>(null);
  const activePlace = places[active];
  const fc = useForecast(activePlace);
  const theme = useMemo(() => skyTheme(fc.data), [fc.data]);

  // ---- My Location ----
  const locate = useCallback(async () => {
    setLocating(true);
    try {
      const { lat, lon } = await getPosition();
      const existing = useStore.getState().places.find((p) => p.isCurrent);
      if (existing && distanceKm(existing.lat, existing.lon, lat, lon) < 0.8) return;
      let name = "My Location";
      let extra: Partial<Place> = {};
      try {
        extra = await reverseGeocode(lat, lon);
        name = extra.name || name;
      } catch {
        name = nearestStation(lat, lon)?.station.name ?? name;
      }
      setCurrent({ ...extra, id: placeId(lat, lon), name, lat, lon, isCurrent: true });
      setGpsError(false);
    } catch {
      setGpsError(true);
    } finally {
      setLocating(false);
    }
  }, [setCurrent]);

  useEffect(() => {
    const first = gpsOff ? undefined : setTimeout(() => void locate(), 0);
    const onVisible = () =>
      document.visibilityState === "visible" && !useStore.getState().gpsOff && void locate();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearTimeout(first);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [gpsOff, locate]);

  // ---- Keep the Android widgets in step with places and settings ----
  useEffect(() => {
    void syncWidgets(places, settings);
  }, [places, settings]);

  // ---- Pager: scroll to the active page; update active when swiping ----
  useEffect(() => {
    const el = pager.current;
    if (!el) return;
    const target = active * el.clientWidth;
    if (Math.abs(el.scrollLeft - target) > 4)
      el.scrollTo({ left: target, behavior: "instant" as ScrollBehavior });
  }, [active, places.length, screen]);

  const onPagerScroll = () => {
    const el = pager.current;
    if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth);
    if (i !== useStore.getState().active && i >= 0 && i < places.length) setActive(i);
  };

  // ---- Widgets and notifications open the app on a place: skyglass://place/<id> ----
  useEffect(() => {
    if (!isNative) return;
    const open = (url: string | undefined) => {
      const m = url && /^skyglass:\/\/place\/(.+)$/.exec(url);
      if (!m) return;
      const id = decodeURIComponent(m[1]);
      const i = useStore.getState().places.findIndex((p) => p.id === id);
      if (i >= 0) {
        setActive(i);
        setScreen("home");
      }
    };
    void CapApp.getLaunchUrl()
      .then((r) => open(r?.url))
      .catch(() => {});
    const sub = CapApp.addListener("appUrlOpen", (e) => open(e.url));
    return () => void sub.then((h) => h.remove());
  }, [setActive]);

  // ---- Android back button ----
  useEffect(() => {
    if (!isNative) return;
    const sub = CapApp.addListener("backButton", () => {
      const ev = new Event("sg:back", { cancelable: true });
      window.dispatchEvent(ev);
      if (ev.defaultPrevented) return;
      setScreen((s) => {
        if (s !== "home") return "home";
        void CapApp.minimizeApp();
        return s;
      });
    });
    return () => void sub.then((h) => h.remove());
  }, []);

  const openPlace = (i: number) => {
    setActive(i);
    setScreen("home");
  };

  return (
    <div className="relative h-full overflow-hidden">
      <Sky theme={theme} animate={settings.animations && screen === "home"} />

      {places.length === 0 ? (
        <Welcome
          locating={locating}
          gpsError={gpsError}
          onLocate={() => {
            useStore.getState().setGpsOff(false);
            void locate();
          }}
          onSearch={() => setScreen("list")}
        />
      ) : (
        <div ref={pager} className="pager h-full" onScroll={onPagerScroll} data-testid="pager">
          {places.map((p, i) => (
            <div key={p.id + (p.isCurrent ? ":gps" : "")} className="h-full">
              <LocationPage place={p} active={i === active} near={Math.abs(i - active) <= 1} />
            </div>
          ))}
        </div>
      )}

      {places.length > 0 && (
        <nav
          className="glass-strong fixed inset-x-0 bottom-0 z-20 flex items-center justify-between border-0 border-t border-white/10 px-5"
          style={{ paddingBottom: "calc(var(--safe-bottom) + 6px)", paddingTop: 8 }}
          aria-label="Main"
        >
          <button
            type="button"
            aria-label="Map and radar"
            onClick={() => setScreen("map")}
            className="grid h-10 w-10 place-items-center"
          >
            <MapIcon size={22} />
          </button>
          <div
            className="flex items-center gap-1.5"
            aria-label={`Page ${active + 1} of ${places.length}`}
          >
            {places.map((p, i) => (
              <button
                key={p.id + i}
                type="button"
                aria-label={p.name}
                onClick={() => setActive(i)}
                className="grid h-4 w-3 place-items-center"
              >
                {p.isCurrent ? (
                  <Navigation
                    size={9}
                    fill="currentColor"
                    className={i === active ? "" : "opacity-40"}
                  />
                ) : (
                  <span
                    className={`block h-[7px] w-[7px] rounded-full bg-white ${i === active ? "" : "opacity-40"}`}
                  />
                )}
              </button>
            ))}
          </div>
          <div className="flex">
            <button
              type="button"
              aria-label="Settings"
              onClick={() => setScreen("settings")}
              className="grid h-10 w-10 place-items-center"
            >
              <Cog size={21} />
            </button>
            <button
              type="button"
              aria-label="Locations"
              onClick={() => setScreen("list")}
              className="grid h-10 w-10 place-items-center"
            >
              <List size={23} />
            </button>
          </div>
        </nav>
      )}

      {screen === "list" && (
        <Locations
          onClose={() => setScreen("home")}
          onOpen={openPlace}
          locating={locating}
          onLocate={() => {
            useStore.getState().setGpsOff(false);
            void locate();
          }}
        />
      )}
      {screen === "map" && (
        <Suspense
          fallback={
            <div className="fixed inset-0 z-40 grid place-items-center bg-black/40">
              <Spinner size={28} />
            </div>
          }
        >
          <MapScreen place={activePlace} onClose={() => setScreen("home")} />
        </Suspense>
      )}
      {screen === "settings" && <Settings onClose={() => setScreen("home")} />}
    </div>
  );
}

function Welcome({
  locating,
  gpsError,
  onLocate,
  onSearch,
}: {
  locating: boolean;
  gpsError: boolean;
  onLocate: () => void;
  onSearch: () => void;
}) {
  return (
    <div
      className="flex h-full flex-col items-center justify-center px-8 text-center"
      style={{ paddingTop: "var(--safe-top)" }}
    >
      <img
        src={`${import.meta.env.BASE_URL}icons/icon-192.png`}
        alt=""
        className="h-24 w-24 rounded-[28px] shadow-2xl"
      />
      <h1 className="mt-6 text-3xl font-semibold">Skyglass</h1>
      <p className="text-dim mt-2 max-w-xs">
        Forecasts, official SAWS warnings, radar and air quality, beautifully simple.
      </p>
      <div className="mt-8 w-full max-w-xs space-y-3">
        <button
          type="button"
          onClick={onLocate}
          className="flex w-full items-center justify-center gap-2 rounded-2xl bg-white py-3.5 font-semibold text-slate-900"
        >
          {locating ? <Spinner /> : <Navigation size={18} />} Use my location
        </button>
        <button
          type="button"
          onClick={onSearch}
          className="glass-pill flex w-full items-center justify-center gap-2 rounded-2xl py-3.5 font-semibold"
        >
          <Search size={18} /> Search for a city
        </button>
        {gpsError && (
          <p className="text-sm text-amber-200">
            Location is off or not allowed. You can search for a city instead.
          </p>
        )}
      </div>
    </div>
  );
}
