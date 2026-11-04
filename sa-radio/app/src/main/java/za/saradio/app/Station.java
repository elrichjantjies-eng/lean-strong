package za.saradio.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** A radio station. A station can broadcast more than one stream (for example 48 kbps and 128 kbps). */
public class Station {

    /** One playable stream of a station. */
    public static final class Stream {
        public final String url;
        public final int bitrate;
        public final String codec;

        public Stream(String url, int bitrate, String codec) {
            this.url = url == null ? "" : url;
            this.bitrate = Math.max(0, bitrate);
            this.codec = codec == null ? "" : codec;
        }

        /** Codecs the player can't decode (FLV is a video container used by a few listings). */
        boolean playable() {
            String c = codec.toUpperCase(Locale.ROOT);
            return url.startsWith("http") && !c.equals("FLV") && !c.contains("VIDEO");
        }

        /** "128 kbps MP3", "AAC+", or "" when nothing is known. */
        public String label() {
            String c = codec.equalsIgnoreCase("UNKNOWN") ? "" : codec.toUpperCase(Locale.ROOT);
            if (bitrate > 0 && c.length() > 0) return bitrate + " kbps " + c;
            if (bitrate > 0) return bitrate + " kbps";
            return c;
        }
    }

    public int bitrate;
    public int clicks;
    public boolean custom;
    public String uuid = "";
    public String name = "";
    public String url = "";
    public String favicon = "";
    public String state = "";
    public String province = "";
    public String tags = "";
    public String codec = "";
    /** Other streams of the same station, besides {@link #url}. */
    public final List<Stream> alts = new ArrayList<>();

    public String key() {
        return uuid.length() > 0 ? uuid : url;
    }

    /** Every playable stream: the main one first, then the alternates. */
    public List<Stream> streams() {
        List<Stream> out = new ArrayList<>();
        Stream main = new Stream(url, bitrate, codec);
        if (main.playable() || alts.isEmpty()) out.add(main);
        for (Stream s : alts) {
            if (!s.playable()) continue;
            boolean dup = false;
            for (Stream o : out) if (o.url.equals(s.url)) dup = true;
            if (!dup) out.add(s);
        }
        return out;
    }

    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (custom) sb.append("Your stream");
        String where = province.length() > 0 ? province : cleanState();
        if (where.length() > 0) append(sb, where);
        String t = firstTags(2);
        if (t.length() > 0) append(sb, t);
        if (sb.length() == 0) sb.append("Live radio");
        return sb.toString();
    }

    private String cleanState() {
        String s = state == null ? "" : state.trim();
        return s.equalsIgnoreCase("south africa") ? "" : s;
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(s);
    }

    String firstTags(int max) {
        if (tags == null || tags.trim().length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String raw : tags.split(",")) {
            String t = raw.trim();
            if (t.length() == 0 || t.length() > 18) continue;
            String l = t.toLowerCase(Locale.ROOT);
            if (l.equals("south africa") || l.equals("za") || l.equals("rsa") || l.equals("south african")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
            if (++n >= max) break;
        }
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("stationuuid", uuid);
            o.put("name", name);
            o.put("url_resolved", url);
            o.put("favicon", favicon);
            o.put("state", state);
            o.put("tags", tags);
            o.put("codec", codec);
            o.put("bitrate", bitrate);
            o.put("clickcount", clicks);
            o.put("custom", custom);
            if (!alts.isEmpty()) {
                JSONArray a = new JSONArray();
                for (Stream s : alts) {
                    JSONObject j = new JSONObject();
                    j.put("u", s.url);
                    j.put("b", s.bitrate);
                    j.put("c", s.codec);
                    a.put(j);
                }
                o.put("alts", a);
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Station fromJson(JSONObject o) {
        Station s = new Station();
        s.uuid = o.optString("stationuuid", "");
        s.name = o.optString("name", "").trim();
        s.url = o.optString("url_resolved", "").trim();
        if (s.url.length() == 0) s.url = o.optString("url", "").trim();
        s.favicon = o.optString("favicon", "").trim();
        s.state = o.optString("state", "").trim();
        s.tags = o.optString("tags", "");
        s.codec = o.optString("codec", "");
        s.bitrate = o.optInt("bitrate", 0);
        s.clicks = o.optInt("clickcount", 0);
        s.custom = o.optBoolean("custom", false);
        JSONArray a = o.optJSONArray("alts");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.optJSONObject(i);
                if (j != null) s.alts.add(new Stream(j.optString("u"), j.optInt("b"), j.optString("c")));
            }
        }
        s.province = StationRepo.province(s.state, s.name, s.tags);
        return s;
    }
}
