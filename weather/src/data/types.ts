/** A saved or searched place. */
export type Place = {
  id: string;
  name: string;
  /** Province / state. */
  admin?: string;
  country?: string;
  countryCode?: string;
  lat: number;
  lon: number;
  /** True for the "My Location" entry that follows the device's GPS. */
  isCurrent?: boolean;
};

/** Times are Unix seconds; `utcOffset` (seconds) turns them into the place's local time. */
export type Current = {
  time: number;
  temp: number;
  feels: number;
  humidity: number;
  dew: number;
  code: number;
  isDay: boolean;
  precip: number;
  cloud: number;
  pressure: number;
  wind: number;
  windDir: number;
  gust: number;
  visibility: number;
  uv: number;
};

export type Hour = {
  time: number;
  temp: number;
  feels: number;
  humidity: number;
  dew: number;
  pop: number;
  precip: number;
  code: number;
  pressure: number;
  cloud: number;
  visibility: number;
  wind: number;
  windDir: number;
  gust: number;
  uv: number;
  isDay: boolean;
};

export type Day = {
  /** Local midnight. */
  time: number;
  code: number;
  max: number;
  min: number;
  feelsMax: number;
  feelsMin: number;
  sunrise: number;
  sunset: number;
  daylight: number;
  sunshine: number;
  uvMax: number;
  precip: number;
  precipHours: number;
  pop: number;
  windMax: number;
  gustMax: number;
  windDir: number;
};

export type Minutely = { time: number; precip: number };

export type Forecast = {
  fetchedAt: number;
  lat: number;
  lon: number;
  timezone: string;
  utcOffset: number;
  elevation: number;
  current: Current;
  /** From the start of yesterday, hourly, for 16 days. */
  hourly: Hour[];
  /** Index 0 is yesterday, index 1 is today. */
  daily: Day[];
  /** 15-minute precipitation for the next 3 hours. */
  minutely: Minutely[];
};

export type AirQuality = {
  fetchedAt: number;
  usAqi: number | null;
  euAqi: number | null;
  pm25: number | null;
  pm10: number | null;
  o3: number | null;
  no2: number | null;
  so2: number | null;
  co: number | null;
  dust: number | null;
  hourly: { time: number; usAqi: number | null; euAqi: number | null }[];
};

export type Marine = {
  fetchedAt: number;
  /** Distance from the place to the sea grid cell used. */
  distanceKm: number;
  waveHeight: number;
  waveDir: number;
  wavePeriod: number;
  swellHeight: number | null;
  swellDir: number | null;
  swellPeriod: number | null;
  seaTemp: number | null;
  hourly: { time: number; waveHeight: number | null; swellHeight: number | null }[];
  daily: { time: number; waveMax: number | null }[];
};

// ---- South African Weather Service --------------------------------------------------------

export type SawsStation = {
  synop: string;
  name: string;
  province: string;
  municipality: string;
  lat: number;
  lon: number;
};

export type FireDanger = "blue" | "green" | "yellow" | "orange" | "red";

export type SawsDay = {
  /** YYYY-MM-DD */
  date: string;
  min: number | null;
  max: number | null;
  description: string;
  pop: number | null;
  precipMm: number | null;
  windDir: string;
  windKmh: number | null;
  windText: string;
  humidityMin: number | null;
  humidityMax: number | null;
  uv: string;
  fire: FireDanger | null;
  sunrise: string;
  sunset: string;
  moonrise: string;
  moonset: string;
  moonPhase: string;
  tides: { label: string; time: string; height: number | null }[];
  swell: { height: number | null; period: number | null; dir: string } | null;
};

export type SawsForecast = {
  fetchedAt: number;
  station: SawsStation;
  distanceKm: number;
  days: SawsDay[];
  /** Where the data came from: live from SAWS, or the hourly snapshot on the web. */
  source: "live" | "snapshot";
};

export type WarningColor = "yellow" | "orange" | "red" | "advisory";

export type SawsWarning = {
  id: string;
  event: string;
  level: number | null;
  color: WarningColor;
  area: string;
  headline: string;
  description: string;
  instruction: string;
  start: number;
  end: number;
  /** Rings of [lat, lon]. */
  polygons: [number, number][][];
};

export type StormCell = {
  id: string;
  /** "current", or where it's expected in 30 / 60 minutes. */
  instance: "current" | "forecast30" | "forecast60";
  time: number;
  polygon: [number, number][];
};

export type SawsCity = {
  station: SawsStation;
  max: number | null;
  min: number | null;
  description: string;
};

export type SawsBundle = {
  fetchedAt: number;
  warnings: SawsWarning[];
  storms: StormCell[];
  source: "live" | "snapshot";
};
