package com.skyglass.weather.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * South African Weather Service warnings (https://www.weathersa.co.za/api/WarningAlerts).
 * Mirrors parseWarnings() in src/data/sawsParse.ts.
 */
public final class Saws {

    private Saws() {}

    public static final class Warning {
        public String id;
        public String event;
        public String headline;
        public String color;
        public int level;
        public long end;
        public final List<double[]> ring = new ArrayList<>();
    }

    public static boolean inSouthAfrica(double lat, double lon) {
        return lat < -21.5 && lat > -35.5 && lon > 16 && lon < 33.5;
    }

    /** Current warnings for the country; empty when there are none (SAWS answers 400 then). */
    public static List<Warning> fetchWarnings() throws java.io.IOException {
        List<Warning> out = new ArrayList<>();
        String body;
        try {
            body = Wx.httpGet("https://www.weathersa.co.za/api/WarningAlerts");
        } catch (Wx.HttpStatus e) {
            if (e.status == 400 || e.status == 404) return out;
            throw e;
        }
        try {
            JSONArray a = new JSONArray(body);
            for (int i = 0; i < a.length(); i++) {
                JSONObject r = a.getJSONObject(i);
                Warning w = new Warning();
                String value = r.optString("value", r.optString("level", ""));
                w.event = nonEmpty(r.optString("eevent"), r.optString("event"), "Weather warning");
                w.headline = nonEmpty(r.optString("headline"), w.event, w.event);
                Matcher m = Pattern.compile("(\\d+)").matcher(value);
                w.level = m.find() ? Integer.parseInt(m.group(1)) : 0;
                if (value.toLowerCase(Locale.US).contains("advisory")) w.color = "advisory";
                else if (w.level >= 10) w.color = "red";
                else if (w.level == 5 || w.level == 6 || w.level == 8 || w.level == 9) w.color = "orange";
                else w.color = "yellow";
                w.end = parseSast(r.optString("expires", r.optString("endTime", "")));
                for (String pt : r.optString("polygon", "").trim().split("\\s+")) {
                    String[] ll = pt.split(",");
                    if (ll.length < 2) continue;
                    try {
                        w.ring.add(new double[] {Double.parseDouble(ll[0]), Double.parseDouble(ll[1])});
                    } catch (NumberFormatException ignored) {
                        // Skip malformed points.
                    }
                }
                String start = r.optString("startTime", "");
                w.id = nonEmpty(r.optString("identifier"), r.optString("id"),
                    Integer.toHexString((w.event + "|" + w.headline + "|" + r.optString("areaDesc") + "|" + start).hashCode()));
                out.add(w);
            }
        } catch (org.json.JSONException ignored) {
            // "No warnings found" or another non-JSON answer.
        }
        return out;
    }

    /** Warnings whose area contains the point and that haven't expired. */
    public static List<Warning> covering(List<Warning> all, double lat, double lon) {
        long now = System.currentTimeMillis() / 1000;
        List<Warning> out = new ArrayList<>();
        for (Warning w : all) {
            if (w.end > 0 && w.end < now) continue;
            if (w.ring.size() >= 3 && contains(w.ring, lat, lon)) out.add(w);
        }
        out.sort((a, b) -> b.level - a.level);
        return out;
    }

    static boolean contains(List<double[]> ring, double lat, double lon) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            double yi = ring.get(i)[0], xi = ring.get(i)[1];
            double yj = ring.get(j)[0], xj = ring.get(j)[1];
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }

    /** SAWS times have no zone; they're South African Standard Time (UTC+2). */
    static long parseSast(String s) {
        Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})").matcher(s);
        if (!m.find()) return 0;
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+02:00"));
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
            Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
        return c.getTimeInMillis() / 1000;
    }

    private static String nonEmpty(String a, String b, String fallback) {
        if (a != null && !a.isEmpty() && !"null".equals(a)) return a;
        if (b != null && !b.isEmpty() && !"null".equals(b)) return b;
        return fallback;
    }

    public static String colorName(String color) {
        switch (color) {
            case "red": return "Red";
            case "orange": return "Orange";
            case "advisory": return "Advisory";
            default: return "Yellow";
        }
    }
}
