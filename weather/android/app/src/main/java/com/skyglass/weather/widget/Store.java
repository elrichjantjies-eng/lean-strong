package com.skyglass.weather.widget;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What the app shares with the widgets and background work: the saved places, the user's
 * settings, the last forecast per place and which place each widget shows.
 */
public final class Store {

    private static final String FILE = "skyglass_widgets";

    public static final class Place {
        public String id;
        public String name;
        public double lat;
        public double lon;
        public boolean current;
    }

    public static final class Settings {
        public boolean fahrenheit;
        public boolean h24 = true;
        public String style = "glass";
        public int opacity = 60;
        public boolean notifyWarnings = true;
        public boolean notifyRain = true;
        public boolean notifyDaily;
        public String dailyAt = "06:30";
    }

    private final SharedPreferences prefs;

    public Store(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- places ----

    public void savePlaces(JSONArray places) {
        prefs.edit().putString("places", places.toString()).apply();
    }

    public List<Place> places() {
        List<Place> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("places", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Place p = new Place();
                p.id = o.getString("id");
                p.name = o.optString("name", "");
                p.lat = o.getDouble("lat");
                p.lon = o.getDouble("lon");
                p.current = o.optBoolean("current");
                out.add(p);
            }
        } catch (JSONException ignored) {
            // Corrupt or empty: no places yet.
        }
        return out;
    }

    public Place place(String id) {
        List<Place> all = places();
        if (id != null) for (Place p : all) if (p.id.equals(id)) return p;
        // Unknown or unset: the first place (My Location when it's on).
        return all.isEmpty() ? null : all.get(0);
    }

    // ---- settings ----

    public void saveSettings(JSONObject s) {
        prefs.edit().putString("settings", s.toString()).apply();
    }

    public Settings settings() {
        Settings s = new Settings();
        try {
            JSONObject o = new JSONObject(prefs.getString("settings", "{}"));
            s.fahrenheit = "f".equals(o.optString("temp", "c"));
            s.h24 = o.optBoolean("h24", true);
            s.style = o.optString("widgetStyle", "glass");
            s.opacity = Math.max(20, Math.min(100, o.optInt("widgetOpacity", 60)));
            s.notifyWarnings = o.optBoolean("notifyWarnings", true);
            s.notifyRain = o.optBoolean("notifyRain", true);
            s.notifyDaily = o.optBoolean("notifyDaily", false);
            s.dailyAt = o.optString("dailyAt", "06:30");
        } catch (JSONException ignored) {
            // Defaults.
        }
        return s;
    }

    // ---- forecasts ----

    public void saveData(Wx.Data d) {
        try {
            prefs.edit().putString("data:" + d.placeId, d.toJson().toString()).apply();
        } catch (JSONException ignored) {
            // Never happens for our own data.
        }
    }

    public Wx.Data data(String placeId) {
        if (placeId == null) return null;
        String raw = prefs.getString("data:" + placeId, null);
        if (raw == null) return null;
        try {
            return Wx.Data.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return null;
        }
    }

    // ---- widget → place ----

    public void setWidgetPlace(int widgetId, String placeId) {
        if (placeId == null) prefs.edit().remove("widget:" + widgetId).apply();
        else prefs.edit().putString("widget:" + widgetId, placeId).apply();
    }

    /** The place id chosen for a widget, or null to follow the first place. */
    public String widgetPlace(int widgetId) {
        return prefs.getString("widget:" + widgetId, null);
    }

    public void forgetWidget(int widgetId) {
        prefs.edit().remove("widget:" + widgetId).apply();
    }

    // ---- notification bookkeeping ----

    public Set<String> notifiedWarnings() {
        return new HashSet<>(prefs.getStringSet("notified", new HashSet<>()));
    }

    public void setNotifiedWarnings(Set<String> ids) {
        prefs.edit().putStringSet("notified", ids).apply();
    }

    public long lastRainAlert() {
        return prefs.getLong("lastRain", 0);
    }

    public void setLastRainAlert(long t) {
        prefs.edit().putLong("lastRain", t).apply();
    }

    public String lastDaily() {
        return prefs.getString("lastDaily", "");
    }

    public void setLastDaily(String day) {
        prefs.edit().putString("lastDaily", day).apply();
    }
}
