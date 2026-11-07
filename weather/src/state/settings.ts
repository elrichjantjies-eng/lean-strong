export type TempUnit = "c" | "f";
export type WindUnit = "kmh" | "ms" | "mph" | "kn" | "bft";
export type PrecipUnit = "mm" | "in";
export type PressureUnit = "hpa" | "inhg" | "mmhg";
export type DistanceUnit = "km" | "mi";
export type WidgetStyle = "glass" | "frosted" | "dark" | "tinted";

export type Settings = {
  temp: TempUnit;
  wind: WindUnit;
  precip: PrecipUnit;
  pressure: PressureUnit;
  distance: DistanceUnit;
  h24: boolean;
  aqi: "us" | "eu";
  /** Animated rain, snow, stars and clouds behind the forecast. */
  animations: boolean;
  /** Android home-screen widgets. */
  widgetStyle: WidgetStyle;
  /** 20–100: how see-through the widget glass is. */
  widgetOpacity: number;
  /** Android notifications. */
  notifyWarnings: boolean;
  notifyRain: boolean;
  notifyDaily: boolean;
  /** "HH:MM" local time for the daily forecast notification. */
  dailyAt: string;
};

export const DEFAULT_SETTINGS: Settings = {
  temp: "c",
  wind: "kmh",
  precip: "mm",
  pressure: "hpa",
  distance: "km",
  h24: true,
  aqi: "us",
  animations: true,
  widgetStyle: "glass",
  widgetOpacity: 60,
  notifyWarnings: true,
  notifyRain: true,
  notifyDaily: false,
  dailyAt: "06:30",
};
