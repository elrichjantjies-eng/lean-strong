package za.saradio.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tuning: stepping to the next or previous station. It isn't a sweep across the dial like a real
 * radio; it just switches channel. Which stations it steps through is the "band":
 * <ul>
 *   <li>A–Z: every station, in alphabetical order</li>
 *   <li>MHz: every station in order of frequency; stations whose frequency isn't known follow, A–Z</li>
 *   <li>Favs: your favourites, in the order you saved them</li>
 * </ul>
 * Playing a station from the Favourites tab switches to the Favs band, and playing one from the
 * Radio tab switches back to the A–Z or MHz band you used last.
 */
final class Tuner {
    private Tuner() {}

    static final int AZ = 0;
    static final int FREQ = 1;
    static final int FAVS = 2;
    static final String[] LABELS = {"A–Z", "MHz", "Favs"};
    private static final String[] NAMES = {"All stations, A to Z", "All stations, by frequency", "Your favourites"};

    private static List<Station> all;

    static int band(Context c) {
        int b = Prefs.get(c).getInt("tuneBand", AZ);
        return b >= AZ && b <= FAVS ? b : AZ;
    }

    static void setBand(Context c, int b) {
        android.content.SharedPreferences.Editor e = Prefs.get(c).edit().putInt("tuneBand", b);
        if (b != FAVS) e.putInt("tuneOrder", b);
        e.apply();
    }

    /** The A–Z or MHz band used last, for when you leave your favourites. */
    static int allBand(Context c) {
        int o = Prefs.get(c).getInt("tuneOrder", AZ);
        return o == FREQ ? FREQ : AZ;
    }

    /** "A–Z", "MHz" or "FAVS", for small labels (the unit keeps its proper case). */
    static String shortLabel(int band) {
        return band == FREQ ? "MHz" : LABELS[Math.max(0, Math.min(LABELS.length - 1, band))].toUpperCase(Locale.ROOT);
    }

    static String name(int band) {
        return NAMES[Math.max(0, Math.min(NAMES.length - 1, band))];
    }

    /** Keeps the tuner's copy of the station list fresh (the screen calls this after a refresh). */
    static synchronized void setStations(List<Station> l) {
        all = l == null ? null : new ArrayList<>(l);
    }

    static synchronized List<Station> stations(Context c) {
        if (all == null || all.isEmpty()) {
            String cache = StationRepo.loadCache(c);
            all = cache != null ? StationRepo.parse(cache) : new ArrayList<>();
        }
        return all;
    }

    /** The stations on a band, in tuning order. */
    static List<Station> dial(Context c, int band) {
        if (band == FAVS) return Prefs.favourites(c);
        return order(stations(c), band);
    }

    static List<Station> order(List<Station> in, int band) {
        List<Station> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // The directory lists some stations more than once ("Jacaranda" and "Jacaranda FM"); the
        // dial keeps the first (most popular) listing so tuning doesn't stop on the same station twice.
        for (Station s : in) if (s != null && seen.add(s.key()) && seen.add("name:" + dupKey(s))) out.add(s);
        if (band == FREQ) {
            Collections.sort(out, (a, b) -> {
                float fa = Freq.of(a), fb = Freq.of(b);
                if (fa > 0 && fb > 0 && fa != fb) return Float.compare(fa, fb);
                if (fa > 0 != fb > 0) return fa > 0 ? -1 : 1;
                return sortName(a).compareTo(sortName(b));
            });
        } else {
            Collections.sort(out, (a, b) -> sortName(a).compareTo(sortName(b)));
        }
        return out;
    }

    static String sortName(Station s) {
        String n = s.name == null ? "" : s.name.trim().toLowerCase(Locale.ROOT);
        if (n.startsWith("the ")) n = n.substring(4);
        return n.replaceAll("^[^\\p{L}\\p{N}]+", "");
    }

    /** One step along {@code dial} from {@code cur}; wraps round at the ends. */
    static Station step(List<Station> dial, Station cur, int dir) {
        if (dial == null || dial.isEmpty()) return null;
        int n = dial.size();
        int i = indexOf(dial, cur);
        if (i < 0) return dir >= 0 ? dial.get(0) : dial.get(n - 1);
        return dial.get(((i + dir) % n + n) % n);
    }

    static int indexOf(List<Station> dial, Station cur) {
        if (cur == null) return -1;
        for (int i = 0; i < dial.size(); i++) if (dial.get(i).key().equals(cur.key())) return i;
        String n = dupKey(cur);
        for (int i = 0; i < dial.size(); i++) if (dupKey(dial.get(i)).equals(n)) return i;
        return -1;
    }

    /** Same name (give or take "FM") and same frequency: the same station listed twice. */
    static String dupKey(Station s) {
        return looseName(s) + "|" + Freq.label(s);
    }

    /** A station's name without "FM", "Radio", frequencies and punctuation, for spotting duplicate listings. */
    static String looseName(Station s) {
        String n = (s.name == null ? "" : s.name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        StringBuilder sb = new StringBuilder();
        for (String w : n.trim().split(" ")) {
            if (w.isEmpty() || w.equals("fm") || w.equals("radio") || w.equals("the") || w.equals("sa") || w.equals("za")
                    || w.matches("\\d+")) continue;
            if (w.length() == 1 && sb.length() > 0) continue; // "S A"
            sb.append(w).append(' ');
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? StationRepo.normName(s.name == null ? "" : s.name) : out;
    }

    /** The station one step away on the current band (falls back to A–Z when there are no favourites). */
    static Station step(Context c, Station cur, int dir) {
        int band = band(c);
        List<Station> d = dial(c, band);
        if (d.isEmpty() && band == FAVS) d = dial(c, allBand(c));
        return step(d, cur, dir);
    }

    /** The previous and next stations, for the dial's labels. Either can be null. */
    static Station[] neighbours(Context c, Station cur) {
        int band = band(c);
        List<Station> d = dial(c, band);
        if (d.isEmpty() && band == FAVS) d = dial(c, allBand(c));
        if (d.size() < 2) return new Station[] {null, null};
        return new Station[] {step(d, cur, -1), step(d, cur, 1)};
    }
}
