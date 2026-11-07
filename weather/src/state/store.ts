import { create } from "zustand";
import type { Place } from "@/data/types";
import { DEFAULT_SETTINGS, type Settings } from "./settings";
import { load, save } from "@/lib/storage";

const KEY = "skyglass:state:v1";

type Persisted = {
  places: Place[];
  active: number;
  settings: Settings;
  /** The user turned "My Location" off. */
  gpsOff: boolean;
};

type State = Persisted & {
  setActive(i: number): void;
  addPlace(p: Place): void;
  removePlace(id: string): void;
  movePlace(from: number, to: number): void;
  /** Creates or updates the "My Location" entry (always first). */
  setCurrent(p: Place | null): void;
  setGpsOff(off: boolean): void;
  updateSettings(s: Partial<Settings>): void;
};

const initial = load<Persisted>(KEY, {
  places: [],
  active: 0,
  settings: DEFAULT_SETTINGS,
  gpsOff: false,
});

export const useStore = create<State>((set, get) => {
  const commit = (patch: Partial<Persisted>) => {
    set(patch);
    const { places, active, settings, gpsOff } = get();
    save(KEY, { places, active, settings, gpsOff });
  };
  return {
    places: initial.places,
    active: Math.min(initial.active, Math.max(0, initial.places.length - 1)),
    settings: { ...DEFAULT_SETTINGS, ...initial.settings },
    gpsOff: initial.gpsOff,
    setActive: (i) => commit({ active: Math.max(0, Math.min(i, get().places.length - 1)) }),
    addPlace: (p) => {
      const places = get().places;
      const existing = places.findIndex((q) => q.id === p.id && !q.isCurrent);
      if (existing >= 0) return commit({ active: existing });
      commit({ places: [...places, { ...p, isCurrent: false }], active: places.length });
    },
    removePlace: (id) => {
      const places = get().places.filter((p) => p.id !== id || p.isCurrent);
      commit({ places, active: Math.min(get().active, Math.max(0, places.length - 1)) });
    },
    movePlace: (from, to) => {
      const places = [...get().places];
      const [p] = places.splice(from, 1);
      places.splice(to, 0, p);
      // "My Location" always stays first.
      places.sort((a, b) => Number(!!b.isCurrent) - Number(!!a.isCurrent));
      commit({ places });
    },
    setCurrent: (p) => {
      const rest = get().places.filter((q) => !q.isCurrent);
      const hadCurrent = rest.length !== get().places.length;
      if (!p)
        return commit({ places: rest, active: Math.max(0, get().active - (hadCurrent ? 1 : 0)) });
      commit({
        places: [{ ...p, isCurrent: true }, ...rest],
        active: hadCurrent ? get().active : get().active + (get().places.length ? 1 : 0),
      });
    },
    setGpsOff: (gpsOff) => commit({ gpsOff }),
    updateSettings: (s) => commit({ settings: { ...get().settings, ...s } }),
  };
});
