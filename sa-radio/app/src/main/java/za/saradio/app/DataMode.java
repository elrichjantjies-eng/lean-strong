package za.saradio.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "Data &amp; quality" setting. Radio streams have a fixed bitrate, so the honest levers are:
 * which stream to pick when a station offers several, how deep the playback buffer is, and how
 * many pictures the app downloads around the audio.
 */
public final class DataMode {
    private DataMode() {}

    public static final int AUTO = 0;
    public static final int SAVER = 1;
    public static final int BALANCED = 2;
    public static final int BEST = 3;

    public static final String[] SHORT = {"Auto", "Saver", "Balanced", "Best"};

    public static String title(int mode) {
        switch (mode) {
            case SAVER: return "Data saver";
            case BALANCED: return "Balanced";
            case BEST: return "Best quality";
            default: return "Auto";
        }
    }

    public static String describe(int mode) {
        switch (mode) {
            case SAVER:
                return "Picks the lightest stream a station offers, loads smaller pictures and skips "
                        + "pictures in articles. Typically 15–30 MB an hour.";
            case BALANCED:
                return "Good sound with sensible data use. Typically 30–60 MB an hour.";
            case BEST:
                return "Picks the highest-quality stream and keeps a deeper buffer for fewer drop-outs. "
                        + "Can use 60–150 MB an hour.";
            default:
                return "Best quality on Wi-Fi, Balanced on mobile data. Switches by itself when you "
                        + "change networks.";
        }
    }

    /** True when the active network is metered (mobile data or a metered hotspot). */
    public static boolean onMeteredNetwork(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps == null) return cm.isActiveNetworkMetered();
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } catch (Exception e) {
            return true;
        }
    }

    /** The mode actually in force: Auto resolves to Best on Wi-Fi and Balanced on mobile data. */
    public static int effective(Context c) {
        int m = Prefs.dataMode(c);
        if (m != AUTO) return m;
        return onMeteredNetwork(c) ? BALANCED : BEST;
    }

    /** Seconds of audio to collect before playback starts. */
    static float startBufferSeconds(int eff) {
        return eff == SAVER ? 1.2f : eff == BEST ? 2.5f : 1.6f;
    }

    /** Seconds of audio to refill after the buffer runs dry. */
    static float rebufferSeconds(int eff) {
        return eff == SAVER ? 3f : eff == BEST ? 6f : 4f;
    }

    /** Pixel size for station logos in the grid. */
    static int logoPx(int eff) {
        return eff == SAVER ? 144 : 256;
    }

    /** Pixel size for the big Now Playing artwork. */
    static int artPx(int eff) {
        return eff == SAVER ? 256 : 640;
    }

    /** Whether to open article pages just to find a preview picture (costs ~100 KB each). */
    static boolean fetchArticlePreviews(int eff) {
        return eff != SAVER;
    }

    /**
     * Streams of the station, ordered by preference for this mode. The first is played; the rest
     * are fallbacks if it fails.
     */
    public static List<Station.Stream> rank(Station s, final int eff) {
        List<Station.Stream> all = s.streams();
        if (all.size() < 2) return all;
        final Station.Stream primary = all.get(0);
        List<Station.Stream> out = new ArrayList<>(all);
        Collections.sort(out, (a, b) -> Float.compare(cost(a, eff, primary), cost(b, eff, primary)));
        return out;
    }

    /** Lower is better. Streams with an unknown bitrate sit in the middle of the field. */
    private static float cost(Station.Stream st, int eff, Station.Stream primary) {
        float kbps = st.bitrate > 0 ? st.bitrate : 96;
        // A small bonus for the most-listened stream so ties go to the one people actually use.
        float tie = st == primary ? -0.5f : 0f;
        switch (eff) {
            case SAVER: return kbps + tie;
            case BEST: return -kbps + tie;
            default:
                // Aim for ~96 kbps without going over 128.
                return Math.abs(kbps - 96) + (kbps > 128 ? 64 : 0) + tie;
        }
    }

    /** Rough megabytes per hour for a bitrate in kbps. */
    public static int mbPerHour(int kbps) {
        return Math.round(kbps * 3600f / 8f / 1000f);
    }
}
