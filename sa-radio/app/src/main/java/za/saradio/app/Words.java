package za.saradio.app;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The word list in assets/words.txt: five-letter words for the guessing game and themed lists for the word search. */
final class Words {
    private Words() {}

    static final class Theme {
        final String name;
        final List<String> words = new ArrayList<>();

        Theme(String name) {
            this.name = name;
        }
    }

    private static List<String> five;
    private static Set<String> fiveSet;
    private static List<Theme> themes;

    static synchronized void load(Context c) {
        if (five != null) return;
        five = new ArrayList<>();
        fiveSet = new HashSet<>();
        themes = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("words.txt"), "UTF-8"))) {
            Theme cur = null;
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("@")) {
                    cur = new Theme(line.substring(1).trim());
                    themes.add(cur);
                    continue;
                }
                for (String w : line.split("\\s+")) {
                    String u = w.toUpperCase(Locale.ROOT);
                    if (!u.matches("[A-Z]+")) continue;
                    if (cur != null) {
                        if (u.length() >= 4) cur.words.add(u);
                    } else if (u.length() == 5 && fiveSet.add(u)) {
                        five.add(u);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    static List<String> five(Context c) {
        load(c);
        return five;
    }

    static boolean isWord(Context c, String w) {
        load(c);
        return fiveSet.contains(w.toUpperCase(Locale.ROOT));
    }

    static List<Theme> themes(Context c) {
        load(c);
        return themes;
    }
}
