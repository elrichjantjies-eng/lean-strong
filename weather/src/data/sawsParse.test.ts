import { describe, expect, it } from "vitest";
import {
  clock,
  fireDanger,
  num,
  parseDays,
  parseStorms,
  parseWarnings,
  sastToUnix,
  warningColor,
} from "./sawsParse";

const row = (over: Record<string, unknown> = {}) => ({
  synopNo: "68819",
  forecastDay: "1",
  dateT: "2026-11-07T00:00:00",
  minimumTemp: "13",
  maximumTemp: "20",
  dailyWeatherDescription: "partly cloudy",
  precipitationPropability: "30",
  precipitationAmount: "3",
  dailyWindDirectionShort: "SSE",
  dailyWindSpeed: "20.0",
  dailyWindDescription: "fresh South South Easterly",
  minimumHumidity: "55",
  maximumHumidity: "75",
  ultraVioletIndex: "Low",
  fireDangerIndex: "#ff8000",
  sunrise: "1900-01-01T05:39:00",
  sunset: "1900-01-01T19:20:00",
  moonrise: "1900-01-01T04:16:00",
  moonset: "1900-01-01T17:49:00",
  moonPhase: "Waning Crescent",
  highTideAM: "02:19",
  highTideAM_Height: "1.83181812763214",
  lowTideAM: "08:18",
  lowTideAM_Height: "0.4",
  highTidePM: "n/a",
  lowTidePM: "n/a",
  forecastedSwellHeight: "n/a",
  ...over,
});

describe("SAWS parsing", () => {
  it("reads numbers and SAWS's placeholders", () => {
    expect(num("20.0")).toBe(20);
    expect(num("30%")).toBe(30);
    expect(num("n/a")).toBeNull();
    expect(num(null)).toBeNull();
    expect(clock("1900-01-01T05:39:00")).toBe("05:39");
  });

  it("maps the fire danger colours", () => {
    expect(fireDanger("#0066ff")).toBe("blue");
    expect(fireDanger("#00FF00")).toBe("green");
    expect(fireDanger("#ff0000")).toBe("red");
    expect(fireDanger("n/a")).toBeNull();
  });

  it("turns a station's rows into one entry per day", () => {
    const days = parseDays([
      row({ dateT: "2026-11-08T00:00:00", maximumTemp: "28" }),
      row(),
      row({ forecastedTime: "14:00-20:00" }), // a second period for the same day
      row({ dateT: "2026-11-15T00:00:00", minimumTemp: null, maximumTemp: null }), // placeholder
    ]);
    expect(days.map((d) => d.date)).toEqual(["2026-11-07", "2026-11-08"]);
    const d = days[0];
    expect(d).toMatchObject({
      min: 13,
      max: 20,
      description: "Partly cloudy",
      pop: 30,
      windKmh: 20,
      fire: "orange",
      sunrise: "05:39",
    });
    expect(d.tides).toEqual([
      { label: "High", time: "02:19", height: 1.83181812763214 },
      { label: "Low", time: "08:18", height: 0.4 },
    ]);
    expect(d.swell).toBeNull();
  });

  it("colours warnings the way SAWS's impact matrix does", () => {
    expect(warningColor("Warning Level 2")).toEqual({ level: 2, color: "yellow" });
    expect(warningColor("Warning Level 7")).toEqual({ level: 7, color: "yellow" });
    expect(warningColor("Warning Level 6")).toEqual({ level: 6, color: "orange" });
    expect(warningColor("Warning Level 10")).toEqual({ level: 10, color: "red" });
    expect(warningColor("Warning Advisory")).toEqual({ level: null, color: "advisory" });
  });

  it("parses warnings with their polygon and SAST times", () => {
    const [w] = parseWarnings([
      {
        eevent: "Damaging Winds",
        value: "Warning Level 4",
        areaDesc: "City of Cape Town",
        headline: "Yellow level 4 warning",
        startTime: "2026-11-07T08:00:00",
        expires: "2026-11-07T20:00:00",
        polygon: "-33.8,18.3 -33.8,18.6 -34.1,18.6 -34.1,18.3",
      },
    ]);
    expect(w.color).toBe("yellow");
    expect(w.level).toBe(4);
    expect(w.polygons[0]).toHaveLength(4);
    expect(w.polygons[0][0]).toEqual([-33.8, 18.3]);
    expect(w.start).toBe(Date.parse("2026-11-07T06:00:00Z") / 1000);
    expect(w.id).toBeTruthy();
  });

  it("treats a plain-text answer as no warnings", () => {
    expect(parseWarnings("No warnings found")).toEqual([]);
  });

  it("parses storm cells", () => {
    const cells = parseStorms([
      {
        stormID: "2081",
        points: [
          ["-24.0", "29.5"],
          ["-24.1", "29.5"],
          ["-24.1", "29.6"],
        ],
        instance: "forecast30",
        instanceTime: "2026-11-07T13:12:00",
      },
      {
        stormID: "1",
        points: [["-24.0", "29.5"]],
        instance: "current",
        instanceTime: "2026-11-07T13:12:00",
      },
    ]);
    expect(cells).toHaveLength(1);
    expect(cells[0]).toMatchObject({
      id: "2081",
      instance: "forecast30",
      time: sastToUnix("2026-11-07T13:12:00"),
    });
  });
});
