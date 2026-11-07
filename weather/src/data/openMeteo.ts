import type { AirQuality, Current, Day, Forecast, Hour, Marine, Minutely } from "./types";
import { getJson } from "./http";
import { distanceKm } from "@/lib/geo";

const CURRENT = [
  "temperature_2m",
  "relative_humidity_2m",
  "apparent_temperature",
  "is_day",
  "precipitation",
  "weather_code",
  "cloud_cover",
  "pressure_msl",
  "wind_speed_10m",
  "wind_direction_10m",
  "wind_gusts_10m",
  "visibility",
  "uv_index",
  "dew_point_2m",
];
const HOURLY = [
  "temperature_2m",
  "relative_humidity_2m",
  "dew_point_2m",
  "apparent_temperature",
  "precipitation_probability",
  "precipitation",
  "weather_code",
  "pressure_msl",
  "cloud_cover",
  "visibility",
  "wind_speed_10m",
  "wind_direction_10m",
  "wind_gusts_10m",
  "uv_index",
  "is_day",
];
const DAILY = [
  "weather_code",
  "temperature_2m_max",
  "temperature_2m_min",
  "apparent_temperature_max",
  "apparent_temperature_min",
  "sunrise",
  "sunset",
  "daylight_duration",
  "sunshine_duration",
  "uv_index_max",
  "precipitation_sum",
  "precipitation_hours",
  "precipitation_probability_max",
  "wind_speed_10m_max",
  "wind_gusts_10m_max",
  "wind_direction_10m_dominant",
];

type Series = Record<string, (number | null)[]>;
type OmResponse = {
  latitude: number;
  longitude: number;
  timezone: string;
  utc_offset_seconds: number;
  elevation: number;
  current: Record<string, number | null>;
  hourly: Series & { time: number[] };
  daily: Series & { time: number[] };
  minutely_15?: Series & { time: number[] };
};

const coord = (n: number) => n.toFixed(4);

export function forecastUrl(lat: number, lon: number): string {
  const q = new URLSearchParams({
    latitude: coord(lat),
    longitude: coord(lon),
    current: CURRENT.join(","),
    hourly: HOURLY.join(","),
    daily: DAILY.join(","),
    minutely_15: "precipitation",
    forecast_minutely_15: "12",
    timezone: "auto",
    forecast_days: "16",
    past_days: "1",
    timeformat: "unixtime",
  });
  return `https://api.open-meteo.com/v1/forecast?${q}`;
}

const n = (v: number | null | undefined, fallback = 0) =>
  v == null || Number.isNaN(v) ? fallback : v;

export function parseForecast(r: OmResponse, now = Date.now()): Forecast {
  const c = r.current;
  const current: Current = {
    time: n(c.time),
    temp: n(c.temperature_2m),
    feels: n(c.apparent_temperature, n(c.temperature_2m)),
    humidity: n(c.relative_humidity_2m),
    dew: n(c.dew_point_2m),
    code: n(c.weather_code),
    isDay: c.is_day === 1,
    precip: n(c.precipitation),
    cloud: n(c.cloud_cover),
    pressure: n(c.pressure_msl),
    wind: n(c.wind_speed_10m),
    windDir: n(c.wind_direction_10m),
    gust: n(c.wind_gusts_10m),
    visibility: n(c.visibility, 10000),
    uv: n(c.uv_index),
  };
  const h = r.hourly;
  const hourly: Hour[] = h.time.map((time, i) => ({
    time,
    temp: n(h.temperature_2m[i]),
    feels: n(h.apparent_temperature[i]),
    humidity: n(h.relative_humidity_2m[i]),
    dew: n(h.dew_point_2m[i]),
    pop: n(h.precipitation_probability[i]),
    precip: n(h.precipitation[i]),
    code: n(h.weather_code[i]),
    pressure: n(h.pressure_msl[i]),
    cloud: n(h.cloud_cover[i]),
    visibility: n(h.visibility[i], 10000),
    wind: n(h.wind_speed_10m[i]),
    windDir: n(h.wind_direction_10m[i]),
    gust: n(h.wind_gusts_10m[i]),
    uv: n(h.uv_index[i]),
    isDay: h.is_day[i] === 1,
  }));
  const d = r.daily;
  const daily: Day[] = d.time.map((time, i) => ({
    time,
    code: n(d.weather_code[i]),
    max: n(d.temperature_2m_max[i]),
    min: n(d.temperature_2m_min[i]),
    feelsMax: n(d.apparent_temperature_max[i]),
    feelsMin: n(d.apparent_temperature_min[i]),
    sunrise: n(d.sunrise[i]),
    sunset: n(d.sunset[i]),
    daylight: n(d.daylight_duration[i]),
    sunshine: n(d.sunshine_duration[i]),
    uvMax: n(d.uv_index_max[i]),
    precip: n(d.precipitation_sum[i]),
    precipHours: n(d.precipitation_hours[i]),
    pop: n(d.precipitation_probability_max[i]),
    windMax: n(d.wind_speed_10m_max[i]),
    gustMax: n(d.wind_gusts_10m_max[i]),
    windDir: n(d.wind_direction_10m_dominant[i]),
  }));
  const m = r.minutely_15;
  const minutely: Minutely[] = m
    ? m.time.map((time, i) => ({ time, precip: n(m.precipitation[i]) }))
    : [];
  return {
    fetchedAt: now,
    lat: r.latitude,
    lon: r.longitude,
    timezone: r.timezone,
    utcOffset: r.utc_offset_seconds,
    elevation: r.elevation,
    current,
    hourly,
    daily,
    minutely,
  };
}

export async function fetchForecast(lat: number, lon: number): Promise<Forecast> {
  return parseForecast(await getJson<OmResponse>(forecastUrl(lat, lon)));
}

// ---- Air quality ----------------------------------------------------------------------------

type AqResponse = {
  current: Record<string, number | null>;
  hourly: { time: number[]; us_aqi: (number | null)[]; european_aqi: (number | null)[] };
};

export async function fetchAirQuality(lat: number, lon: number): Promise<AirQuality> {
  const q = new URLSearchParams({
    latitude: coord(lat),
    longitude: coord(lon),
    current:
      "us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,dust",
    hourly: "us_aqi,european_aqi",
    forecast_days: "4",
    timezone: "auto",
    timeformat: "unixtime",
  });
  const r = await getJson<AqResponse>(`https://air-quality-api.open-meteo.com/v1/air-quality?${q}`);
  const c = r.current;
  return {
    fetchedAt: Date.now(),
    usAqi: c.us_aqi ?? null,
    euAqi: c.european_aqi ?? null,
    pm25: c.pm2_5 ?? null,
    pm10: c.pm10 ?? null,
    o3: c.ozone ?? null,
    no2: c.nitrogen_dioxide ?? null,
    so2: c.sulphur_dioxide ?? null,
    co: c.carbon_monoxide ?? null,
    dust: c.dust ?? null,
    hourly: r.hourly.time.map((time, i) => ({
      time,
      usAqi: r.hourly.us_aqi[i] ?? null,
      euAqi: r.hourly.european_aqi[i] ?? null,
    })),
  };
}

// ---- Marine -----------------------------------------------------------------------------------

type MarineResponse = {
  latitude: number;
  longitude: number;
  current: Record<string, number | null>;
  hourly: { time: number[]; wave_height: (number | null)[]; swell_wave_height: (number | null)[] };
  daily: { time: number[]; wave_height_max: (number | null)[] };
};

/** Sea conditions, or null for places more than ~25 km from the sea. */
export async function fetchMarine(lat: number, lon: number): Promise<Marine | null> {
  const q = new URLSearchParams({
    latitude: coord(lat),
    longitude: coord(lon),
    current:
      "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period,sea_surface_temperature",
    hourly: "wave_height,swell_wave_height",
    daily: "wave_height_max",
    forecast_days: "7",
    timezone: "auto",
    timeformat: "unixtime",
  });
  const r = await getJson<MarineResponse>(`https://marine-api.open-meteo.com/v1/marine?${q}`);
  const c = r.current;
  if (c.wave_height == null) return null;
  const dist = distanceKm(lat, lon, r.latitude, r.longitude);
  if (dist > 25) return null;
  return {
    fetchedAt: Date.now(),
    distanceKm: dist,
    waveHeight: c.wave_height,
    waveDir: n(c.wave_direction),
    wavePeriod: n(c.wave_period),
    swellHeight: c.swell_wave_height ?? null,
    swellDir: c.swell_wave_direction ?? null,
    swellPeriod: c.swell_wave_period ?? null,
    seaTemp: c.sea_surface_temperature ?? null,
    hourly: r.hourly.time.map((time, i) => ({
      time,
      waveHeight: r.hourly.wave_height[i] ?? null,
      swellHeight: r.hourly.swell_wave_height[i] ?? null,
    })),
    daily: r.daily.time.map((time, i) => ({ time, waveMax: r.daily.wave_height_max[i] ?? null })),
  };
}
