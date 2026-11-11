package za.saradio.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FM frequencies, for tuning "by frequency" and for the dial. The station directory has no
 * frequency field, so the app reads one from the station's name or tags ("KFM 94.5", "Kaya 959",
 * "Hot 1027"), or, for a few big stations whose listings leave it out, from a short table of their
 * main frequency in their home city. Stations broadcast on different frequencies in different
 * areas, so this is a guide for ordering the dial, not a tuning instruction.
 */
final class Freq {
    private Freq() {}

    static final float MIN = 87.5f;
    static final float MAX = 108f;

    /** "94.5", "102,7", or 3–4 digits run together like "959" or "1027", not inside a longer number. */
    private static final Pattern DECIMAL = Pattern.compile("(?<![\\d.,])(\\d{2,3})[.,](\\d)\\d?(?![\\d.,])");
    private static final Pattern RUN = Pattern.compile("(?<![\\d.,])(\\d{3,4})(?![\\d.,])");

    /**
     * Main frequency in the home city, for big stations whose directory listing has no number in it.
     * Matched against the start of the station's lower-case name.
     */
    private static final String[][] KNOWN = {
        {"jacaranda", "94.2"}, {"jakaranda", "94.2"},
        {"metro fm", "96.4"},
        {"5fm", "98.0"},
        {"702", "92.7"}, {"talk radio 702", "92.7"},
        {"east coast radio", "94.0"}, {"ecr ", "94.0"}, {"ecr-", "94.0"},
        {"gagasi", "99.5"},
        {"heart fm", "104.9"},
        {"yfm", "99.2"},
        {"jozi fm", "105.8"},
        {"kaya", "95.9"},
        {"fine music radio", "101.3"},
        {"bush radio", "89.5"},
        {"uct radio", "104.5"},
        {"tuks fm", "107.2"},
    };

    /** The station's frequency in MHz, or 0 when the app doesn't know it. */
    static float of(Station s) {
        if (s == null) return 0;
        float f = parse(s.name);
        if (f > 0) return f;
        f = parse(s.tags);
        if (f > 0) return f;
        return known(s.name);
    }

    static float parse(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = DECIMAL.matcher(text);
        while (m.find()) {
            float f = Float.parseFloat(m.group(1) + "." + m.group(2));
            if (f >= MIN && f <= MAX) return f;
        }
        m = RUN.matcher(text);
        while (m.find()) {
            // "959" is 95.9 and "1027" is 102.7; "702" (70.2) and "786" (78.6) aren't FM frequencies.
            float f = Integer.parseInt(m.group(1)) / 10f;
            if (f >= MIN && f <= MAX) return f;
        }
        return 0;
    }

    static float known(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        for (String[] k : KNOWN) {
            if (n.startsWith(k[0]) || n.equals(k[0].trim())) return Float.parseFloat(k[1]);
        }
        return 0;
    }

    /** "94.2", "102.7" or "" when unknown. */
    static String label(float f) {
        if (f <= 0) return "";
        return String.format(Locale.ROOT, "%.1f", f);
    }

    static String label(Station s) {
        return label(of(s));
    }
}
