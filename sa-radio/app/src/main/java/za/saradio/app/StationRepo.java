package za.saradio.app;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Loads South African stations from the community radio-browser.info directory. */
public final class StationRepo {
    private StationRepo() {}

    private static final String QUERY =
            "/json/stations/search?countrycode=ZA&hidebroken=true&order=clickcount&reverse=true&limit=1500";
    public static final String USER_AGENT = "SARadio/3.0 (Android)";
    private static final String[] HOSTS = {
        "all.api.radio-browser.info",
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "fi1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "at1.api.radio-browser.info"
    };
    public static final String[] PROVINCES = {
        "Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape", "Free State",
        "Limpopo", "Mpumalanga", "North West", "Northern Cape"
    };
    private static volatile String workingHost;

    public static String httpGet(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return new String(readAll(c.getInputStream(), 20 << 20), StandardCharsets.UTF_8);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static byte[] readAll(InputStream in, int max) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) throw new IOException("too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static String fetchRaw() throws IOException {
        List<String> hosts = new ArrayList<>();
        if (workingHost != null) hosts.add(workingHost);
        for (String h : HOSTS) if (!hosts.contains(h)) hosts.add(h);
        IOException last = null;
        for (String h : hosts) {
            try {
                String body = httpGet("https://" + h + QUERY, 15000);
                if (body.trim().startsWith("[")) {
                    workingHost = h;
                    return body;
                }
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("No server responded");
    }

    /** Tells radio-browser a station was played (their popularity ranking depends on it). */
    public static void countClick(final String uuid) {
        if (uuid == null || uuid.length() == 0) return;
        new Thread(() -> {
            try {
                String h = workingHost != null ? workingHost : HOSTS[0];
                httpGet("https://" + h + "/json/url/" + uuid, 8000);
            } catch (Exception ignored) {
            }
        }, "radio-click").start();
    }

    static String normName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Parses the directory. Listings that share a name are one station with several streams, so
     * the most popular listing becomes the station and the others become its alternate streams.
     * (The old app threw the duplicates away, which also threw away the low-data streams.)
     */
    public static List<Station> parse(String json) {
        List<Station> out = new ArrayList<>();
        Map<String, Station> byName = new HashMap<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Station s = Station.fromJson(o);
                if (s.name.length() == 0 || !s.url.startsWith("http")) continue;
                String k = normName(s.name);
                if (k.length() == 0) continue;
                Station first = byName.get(k);
                if (first == null) {
                    byName.put(k, s);
                    out.add(s);
                } else {
                    boolean known = first.url.equals(s.url);
                    for (Station.Stream a : first.alts) if (a.url.equals(s.url)) known = true;
                    if (!known) first.alts.add(new Station.Stream(s.url, s.bitrate, s.codec));
                    if (first.favicon.length() == 0) first.favicon = s.favicon;
                    if (first.province.length() == 0) first.province = s.province;
                }
            }
        } catch (Exception ignored) {
        }
        // The most popular listing may be a format we can't play (e.g. FLV); promote a playable one.
        for (Station s : out) {
            Station.Stream main = new Station.Stream(s.url, s.bitrate, s.codec);
            if (main.playable() || s.alts.isEmpty()) continue;
            for (int i = 0; i < s.alts.size(); i++) {
                Station.Stream a = s.alts.get(i);
                if (a.playable()) {
                    s.alts.set(i, main);
                    s.url = a.url;
                    s.bitrate = a.bitrate;
                    s.codec = a.codec;
                    break;
                }
            }
        }
        return out;
    }

    public static void saveCache(Context c, String raw) {
        File f = new File(c.getFilesDir(), "stations.json");
        File tmp = new File(c.getFilesDir(), "stations.json.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        if (tmp.renameTo(f)) Prefs.get(c).edit().putLong("stationsAt", System.currentTimeMillis()).apply();
    }

    public static String loadCache(Context c) {
        File f = new File(c.getFilesDir(), "stations.json");
        if (!f.exists()) return null;
        try {
            return new String(readAll(new FileInputStream(f), 20 << 20), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Province from the listing's state, or failing that from city names in its name and tags. */
    public static String province(String state, String name, String tags) {
        String p = province(state);
        if (p.length() > 0) return p;
        return province((name == null ? "" : name) + " " + (tags == null ? "" : tags));
    }

    public static String province(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
        if (s.length() == 0) return "";
        if (has(s, "gauteng", "johannesburg", "joburg", "jozi", "pretoria", "tshwane", "soweto", "ekurhuleni",
                "midrand", "sandton", "centurion", "benoni", "vereeniging", "krugersdorp", "alexandra", "tembisa"))
            return "Gauteng";
        if (has(s, "western cape", "cape town", "kaapstad", "stellenbosch", "paarl", "george", "worcester", "mossel bay",
                "knysna", "westkaap", "wes kaap", "mitchells plain", "khayelitsha"))
            return "Western Cape";
        if (has(s, "kwazulu", "kwa zulu", "kzn", "natal", "durban", "pietermaritzburg", "richards bay", "newcastle",
                "umlazi", "zululand"))
            return "KwaZulu-Natal";
        if (has(s, "eastern cape", "port elizabeth", "gqeberha", "east london", "mthatha", "makhanda",
                "grahamstown", "oos kaap", "ooskaap", "nelson mandela bay"))
            return "Eastern Cape";
        if (has(s, "free state", "freestate", "bloemfontein", "welkom", "vrystaat")) return "Free State";
        if (has(s, "limpopo", "polokwane", "thohoyandou", "tzaneen", "venda")) return "Limpopo";
        if (has(s, "mpumalanga", "nelspruit", "mbombela", "witbank", "emalahleni", "secunda", "middelburg"))
            return "Mpumalanga";
        if (has(s, "north west", "northwest", "noordwes", "rustenburg", "mahikeng", "mafikeng", "potchefstroom",
                "klerksdorp"))
            return "North West";
        if (has(s, "northern cape", "kimberley", "upington", "noord kaap", "noordkaap")) return "Northern Cape";
        return "";
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    public static String abbreviate(String p) {
        switch (p) {
            case "KwaZulu-Natal": return "KZN";
            case "Western Cape": return "WC";
            case "Eastern Cape": return "EC";
            case "Northern Cape": return "NC";
            case "North West": return "NW";
            case "Free State": return "FS";
            case "Gauteng": return "GP";
            case "Limpopo": return "LP";
            case "Mpumalanga": return "MP";
            default: return p;
        }
    }
}
