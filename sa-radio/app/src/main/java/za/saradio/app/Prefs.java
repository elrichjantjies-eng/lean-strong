package za.saradio.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** All saved settings and lists. */
public final class Prefs {
    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("saradio", Context.MODE_PRIVATE);
    }

    // ---- Stations ---------------------------------------------------------------------------

    static List<Station> loadList(Context c, String key) {
        List<Station> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(get(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Station.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void saveList(Context c, String key, List<Station> list) {
        JSONArray a = new JSONArray();
        for (Station s : list) a.put(s.toJson());
        get(c).edit().putString(key, a.toString()).apply();
    }

    public static List<Station> favourites(Context c) {
        return loadList(c, "favs");
    }

    public static void saveFavourites(Context c, List<Station> l) {
        saveList(c, "favs", l);
    }

    public static List<Station> recents(Context c) {
        return loadList(c, "recents");
    }

    public static void addRecent(Context c, Station s) {
        List<Station> l = recents(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).key().equals(s.key())) l.remove(i);
        l.add(0, s);
        while (l.size() > 12) l.remove(l.size() - 1);
        saveList(c, "recents", l);
    }

    public static void saveLastStation(Context c, Station s) {
        get(c).edit().putString("last", s.toJson().toString()).apply();
    }

    public static Station lastStation(Context c) {
        try {
            String s = get(c).getString("last", null);
            return s == null ? null : Station.fromJson(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Data & quality ---------------------------------------------------------------------

    public static int dataMode(Context c) {
        return get(c).getInt("dataMode", DataMode.AUTO);
    }

    public static void setDataMode(Context c, int mode) {
        get(c).edit().putInt("dataMode", mode).apply();
    }

    private static String monthKey() {
        Calendar k = Calendar.getInstance();
        return String.format(Locale.ROOT, "%04d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1);
    }

    /** Radio data used this calendar month, in bytes. */
    public static long monthBytes(Context c) {
        SharedPreferences p = get(c);
        return monthKey().equals(p.getString("dataMonth", "")) ? p.getLong("dataMonthBytes", 0) : 0;
    }

    public static void addMonthBytes(Context c, long bytes) {
        if (bytes <= 0) return;
        SharedPreferences p = get(c);
        String m = monthKey();
        long cur = m.equals(p.getString("dataMonth", "")) ? p.getLong("dataMonthBytes", 0) : 0;
        p.edit().putString("dataMonth", m).putLong("dataMonthBytes", cur + bytes).apply();
    }

    // ---- Sound ------------------------------------------------------------------------------

    public static boolean eqEnabled(Context c) {
        return get(c).getBoolean("eqOn", true);
    }

    public static String eqPreset(Context c) {
        return get(c).getString("eqPreset", "normal");
    }

    /** Custom curve, one value in dB per device band; null when never edited. */
    public static float[] eqCustom(Context c, int bands) {
        try {
            JSONArray a = new JSONArray(get(c).getString("eqCustom", "[]"));
            if (a.length() != bands) return null;
            float[] out = new float[bands];
            for (int i = 0; i < bands; i++) out[i] = (float) a.getDouble(i);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static void setEqCustom(Context c, float[] db) {
        JSONArray a = new JSONArray();
        try {
            for (float v : db) a.put((double) v);
        } catch (Exception ignored) {
        }
        get(c).edit().putString("eqCustom", a.toString()).putString("eqPreset", "custom").apply();
    }

    /** Bass boost, surround and loudness, each 0..100. */
    public static int effect(Context c, String name) {
        return get(c).getInt("fx_" + name, 0);
    }

    public static String formatBytes(long b) {
        if (b < 1000L * 1000) return Math.max(0, Math.round(b / 1000.0)) + " KB";
        if (b < 1000L * 1000 * 1000) {
            double mb = b / 1e6;
            return (mb < 10 ? String.format(Locale.ROOT, "%.1f", mb) : String.valueOf(Math.round(mb))) + " MB";
        }
        return String.format(Locale.ROOT, "%.2f GB", b / 1e9);
    }
}
