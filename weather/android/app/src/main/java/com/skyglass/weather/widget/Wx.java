package com.skyglass.weather.widget;

import com.skyglass.weather.R;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The forecast the widgets and notifications show, fetched straight from Open-Meteo so the
 * widgets stay fresh without opening the app. Mirrors src/weather/codes.ts in the web app.
 */
public final class Wx {

    private Wx() {}

    public static final class Hour {
        public long time;
        public double temp;
        public int code;
        public boolean day;
        public int pop;
    }

    public static final class Day {
        public long time;
        public int code;
        public double max;
        public double min;
        public int pop;
        public long sunrise;
        public long sunset;
        public double uv;
    }

    public static final class Data {
        public String placeId;
        public String name;
        public boolean current;
        public double lat;
        public double lon;
        public long fetchedAt;
        public int utcOffset;
        public double temp;
        public double feels;
        public int code;
        public boolean isDay;
        public int humidity;
        public double wind;
        public double gust;
        public int windDir;
        public double uv;
        public final List<Hour> hours = new ArrayList<>();
        public final List<Day> days = new ArrayList<>();
        /** 15-minute precipitation (mm) starting at minutelyStart. */
        public long minutelyStart;
        public final List<Double> minutely = new ArrayList<>();
        /** The most severe SAWS warning covering this place, if any. */
        public String alertText;
        public String alertColor;

        /** Today's entry in `days`. */
        public Day today() {
            long now = System.currentTimeMillis() / 1000;
            Day best = days.isEmpty() ? null : days.get(0);
            for (Day d : days) if (d.time <= now) best = d;
            return best;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("placeId", placeId).put("name", name).put("current", current);
            o.put("lat", lat).put("lon", lon).put("fetchedAt", fetchedAt).put("utcOffset", utcOffset);
            o.put("temp", temp).put("feels", feels).put("code", code).put("isDay", isDay);
            o.put("humidity", humidity).put("wind", wind).put("gust", gust).put("windDir", windDir).put("uv", uv);
            JSONArray h = new JSONArray();
            for (Hour x : hours) {
                h.put(new JSONArray().put(x.time).put(x.temp).put(x.code).put(x.day ? 1 : 0).put(x.pop));
            }
            o.put("hours", h);
            JSONArray d = new JSONArray();
            for (Day x : days) {
                d.put(new JSONArray().put(x.time).put(x.code).put(x.max).put(x.min).put(x.pop).put(x.sunrise).put(x.sunset).put(x.uv));
            }
            o.put("days", d);
            o.put("minutelyStart", minutelyStart);
            JSONArray m = new JSONArray();
            for (Double v : minutely) m.put(v);
            o.put("minutely", m);
            if (alertText != null) o.put("alertText", alertText).put("alertColor", alertColor);
            return o;
        }

        public static Data fromJson(JSONObject o) throws JSONException {
            Data x = new Data();
            x.placeId = o.optString("placeId");
            x.name = o.optString("name");
            x.current = o.optBoolean("current");
            x.lat = o.optDouble("lat");
            x.lon = o.optDouble("lon");
            x.fetchedAt = o.optLong("fetchedAt");
            x.utcOffset = o.optInt("utcOffset");
            x.temp = o.optDouble("temp");
            x.feels = o.optDouble("feels");
            x.code = o.optInt("code");
            x.isDay = o.optBoolean("isDay", true);
            x.humidity = o.optInt("humidity");
            x.wind = o.optDouble("wind");
            x.gust = o.optDouble("gust");
            x.windDir = o.optInt("windDir");
            x.uv = o.optDouble("uv");
            JSONArray h = o.optJSONArray("hours");
            if (h != null) {
                for (int i = 0; i < h.length(); i++) {
                    JSONArray a = h.getJSONArray(i);
                    Hour y = new Hour();
                    y.time = a.getLong(0);
                    y.temp = a.getDouble(1);
                    y.code = a.getInt(2);
                    y.day = a.getInt(3) == 1;
                    y.pop = a.getInt(4);
                    x.hours.add(y);
                }
            }
            JSONArray d = o.optJSONArray("days");
            if (d != null) {
                for (int i = 0; i < d.length(); i++) {
                    JSONArray a = d.getJSONArray(i);
                    Day y = new Day();
                    y.time = a.getLong(0);
                    y.code = a.getInt(1);
                    y.max = a.getDouble(2);
                    y.min = a.getDouble(3);
                    y.pop = a.getInt(4);
                    y.sunrise = a.getLong(5);
                    y.sunset = a.getLong(6);
                    y.uv = a.optDouble(7, 0);
                    x.days.add(y);
                }
            }
            x.minutelyStart = o.optLong("minutelyStart");
            JSONArray m = o.optJSONArray("minutely");
            if (m != null) for (int i = 0; i < m.length(); i++) x.minutely.add(m.getDouble(i));
            if (o.has("alertText")) {
                x.alertText = o.optString("alertText");
                x.alertColor = o.optString("alertColor", "yellow");
            }
            return x;
        }
    }

    // ---- Fetching ----------------------------------------------------------------------

    public static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Skyglass-Android");
        try {
            int status = c.getResponseCode();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                status >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            if (status >= 400) throw new HttpStatus(status, sb.toString());
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    public static final class HttpStatus extends IOException {
        public final int status;
        public final String body;

        HttpStatus(int status, String body) {
            super("HTTP " + status);
            this.status = status;
            this.body = body;
        }
    }

    public static Data fetch(String placeId, String name, boolean current, double lat, double lon)
        throws IOException, JSONException {
        String url = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,is_day,weather_code,relative_humidity_2m,"
                + "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"
                + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                + "sunrise,sunset,uv_index_max"
                + "&minutely_15=precipitation&forecast_minutely_15=8&forecast_hours=26"
                + "&forecast_days=7&timezone=auto&timeformat=unixtime",
            lat, lon);
        return parse(placeId, name, current, lat, lon, new JSONObject(httpGet(url)));
    }

    /** Builds the widget data from an Open-Meteo forecast response. */
    public static Data parse(String placeId, String name, boolean current, double lat, double lon, JSONObject r)
        throws JSONException {
        Data d = new Data();
        d.placeId = placeId;
        d.name = name;
        d.current = current;
        d.lat = lat;
        d.lon = lon;
        d.fetchedAt = System.currentTimeMillis();
        d.utcOffset = r.optInt("utc_offset_seconds");
        JSONObject c = r.getJSONObject("current");
        d.temp = c.optDouble("temperature_2m");
        d.feels = c.optDouble("apparent_temperature", d.temp);
        d.code = c.optInt("weather_code");
        d.isDay = c.optInt("is_day", 1) == 1;
        d.humidity = c.optInt("relative_humidity_2m");
        d.wind = c.optDouble("wind_speed_10m");
        d.gust = c.optDouble("wind_gusts_10m");
        d.windDir = c.optInt("wind_direction_10m");
        d.uv = c.optDouble("uv_index", 0);

        JSONObject h = r.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time");
        for (int i = 0; i < ht.length(); i++) {
            Hour y = new Hour();
            y.time = ht.getLong(i);
            y.temp = h.getJSONArray("temperature_2m").optDouble(i);
            y.code = h.getJSONArray("weather_code").optInt(i);
            y.day = h.getJSONArray("is_day").optInt(i, 1) == 1;
            y.pop = h.getJSONArray("precipitation_probability").optInt(i, 0);
            d.hours.add(y);
        }
        JSONObject dy = r.getJSONObject("daily");
        JSONArray dt = dy.getJSONArray("time");
        for (int i = 0; i < dt.length(); i++) {
            Day y = new Day();
            y.time = dt.getLong(i);
            y.code = dy.getJSONArray("weather_code").optInt(i);
            y.max = dy.getJSONArray("temperature_2m_max").optDouble(i);
            y.min = dy.getJSONArray("temperature_2m_min").optDouble(i);
            y.pop = dy.getJSONArray("precipitation_probability_max").optInt(i, 0);
            y.sunrise = dy.getJSONArray("sunrise").optLong(i);
            y.sunset = dy.getJSONArray("sunset").optLong(i);
            y.uv = dy.getJSONArray("uv_index_max").optDouble(i, 0);
            d.days.add(y);
        }
        JSONObject m = r.optJSONObject("minutely_15");
        if (m != null) {
            JSONArray mt = m.getJSONArray("time");
            JSONArray mp = m.getJSONArray("precipitation");
            if (mt.length() > 0) d.minutelyStart = mt.getLong(0);
            for (int i = 0; i < mp.length(); i++) d.minutely.add(mp.optDouble(i, 0));
        }
        return d;
    }

    // ---- Presentation helpers -----------------------------------------------------------

    public static String label(int code, boolean day) {
        switch (code) {
            case 0: return day ? "Sunny" : "Clear";
            case 1: return day ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: return "Fog";
            case 48: return "Freezing fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Heavy drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: return "Snow showers";
            case 86: return "Heavy snow showers";
            case 95: return "Thunderstorms";
            case 96: return "Thunderstorms with hail";
            case 99: return "Severe thunderstorms";
            default: return "Cloudy";
        }
    }

    public static int icon(int code, boolean day) {
        switch (code) {
            case 0: return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
            case 1: case 2: return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
            case 3: return R.drawable.wx_cloudy;
            case 45: case 48: return R.drawable.wx_fog;
            case 51: case 53: case 55: return R.drawable.wx_drizzle;
            case 56: case 57: case 66: case 67: return R.drawable.wx_sleet;
            case 61: case 63: return R.drawable.wx_rain;
            case 65: case 82: return R.drawable.wx_heavy_rain;
            case 71: case 73: case 75: case 77: case 85: case 86: return R.drawable.wx_snow;
            case 80: case 81: return day ? R.drawable.wx_showers_day : R.drawable.wx_showers_night;
            case 95: case 96: case 99: return R.drawable.wx_thunder;
            default: return R.drawable.wx_cloudy;
        }
    }

    /** The "Sky tint" widget background for the current conditions. */
    public static int tint(Data d) {
        int c = d.code;
        long now = System.currentTimeMillis() / 1000;
        Day t = d.today();
        boolean golden = t != null && (Math.abs(now - t.sunrise) < 2700 || Math.abs(now - t.sunset) < 2700);
        if (c >= 95) return R.drawable.tint_storm;
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return R.drawable.tint_snow;
        if (c >= 51) return d.isDay ? R.drawable.tint_rain : R.drawable.tint_storm;
        if (c == 45 || c == 48 || c == 3) return d.isDay ? R.drawable.tint_cloudy : R.drawable.tint_night;
        if (!d.isDay) return R.drawable.tint_night;
        if (golden) return R.drawable.tint_golden;
        return c == 2 ? R.drawable.tint_partly : R.drawable.tint_sunny;
    }

    public static String temp(double c, boolean fahrenheit) {
        if (Double.isNaN(c)) return "–";
        long v = Math.round(fahrenheit ? c * 9 / 5 + 32 : c);
        return v + "°";
    }

    public static String clock(long unix, int offset, boolean h24) {
        long t = unix + offset;
        int h = (int) ((t / 3600) % 24 + 24) % 24;
        int m = (int) ((t / 60) % 60 + 60) % 60;
        if (h24) return String.format(Locale.US, "%02d:%02d", h, m);
        return String.format(Locale.US, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    public static String hour(long unix, int offset, boolean h24) {
        long t = unix + offset;
        int h = (int) ((t / 3600) % 24 + 24) % 24;
        if (h24) return String.format(Locale.US, "%02d", h);
        return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? "am" : "pm");
    }

    private static final String[] WEEKDAYS = {"Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"};

    /** Short weekday in the place's local time (1 Jan 1970 was a Thursday). */
    public static String weekday(long unix, int offset) {
        long days = Math.floorDiv(unix + offset, 86400L);
        return WEEKDAYS[(int) Math.floorMod(days, 7L)];
    }
}
