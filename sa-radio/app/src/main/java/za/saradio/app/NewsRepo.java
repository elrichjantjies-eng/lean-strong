package za.saradio.app;

import android.content.Context;
import android.text.Html;
import android.util.Xml;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/** Headlines from South African publishers' public RSS feeds. */
public final class NewsRepo {
    private NewsRepo() {}

    public static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/141.0 Mobile Safari/537.36 SARadio/3.0";

    /**
     * Checked November 2026. Four of the old app's feeds had moved or been switched off (TimesLIVE,
     * Sowetan, Mail &amp; Guardian, EWN), so those tabs were always empty.
     */
    public static final String[][] FEEDS = {
        {"News24", "https://feeds.news24.com/articles/news24/TopStories/rss"},
        {"Daily Maverick", "https://www.dailymaverick.co.za/dmrss/"},
        {"TimesLIVE", "https://www.timeslive.co.za/arc/outboundfeeds/rss/"},
        {"IOL", "https://www.iol.co.za/rss"},
        {"SABC News", "https://www.sabcnews.com/sabcnews/feed/"},
        {"eNCA", "https://www.enca.com/rss.xml"},
        {"The Citizen", "https://www.citizen.co.za/feed/"},
        {"Mail & Guardian", "https://mg.co.za/rss"},
        {"Sowetan", "https://www.sowetanlive.co.za/arc/outboundfeeds/rss/"},
        {"GroundUp", "https://www.groundup.org.za/sitenews/rss/"},
        {"BusinessLIVE", "https://www.businesslive.co.za/arc/outboundfeeds/rss/"},
        {"BusinessTech", "https://businesstech.co.za/news/feed/"},
        {"Moneyweb", "https://www.moneyweb.co.za/feed/"},
        {"MyBroadband", "https://mybroadband.co.za/news/feed"},
        {"TechCentral", "https://techcentral.co.za/feed/"},
        {"The South African", "https://www.thesouthafrican.com/feed/"},
    };

    private static final Pattern IMG = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    // Two-digit-year patterns come first: eNCA writes "Wed, 04 Nov 26 15:45:00 +0200", which the old
    // app read as the year 26 AD and then threw away as "too old".
    private static final String[] DATE_PATTERNS = {
        "EEE, dd MMM yy HH:mm:ss Z",
        "EEE, d MMM yy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss"
    };

    public static class Item {
        public long time;
        public String title = "";
        public String link = "";
        public String source = "";
        public String image = "";
        public String summary = "";

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("t", title);
                o.put("l", link);
                o.put("s", source);
                o.put("i", image);
                o.put("d", summary);
                o.put("w", time);
            } catch (Exception ignored) {
            }
            return o;
        }

        static Item fromJson(JSONObject o) {
            Item it = new Item();
            it.title = o.optString("t");
            it.link = o.optString("l");
            it.source = o.optString("s");
            it.image = o.optString("i");
            it.summary = o.optString("d");
            it.time = o.optLong("w");
            return it;
        }
    }

    public static List<Item> fetchAll() {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<List<Item>>> jobs = new ArrayList<>();
        for (final String[] f : FEEDS) {
            jobs.add(pool.submit(() -> {
                try {
                    return fetchFeed(f[0], f[1]);
                } catch (Throwable t) {
                    return new ArrayList<Item>();
                }
            }));
        }
        List<Item> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 25000;
        for (Future<List<Item>> j : jobs) {
            try {
                all.addAll(j.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS));
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        return tidy(all);
    }

    static List<Item> tidy(List<Item> items) {
        Set<String> seen = new HashSet<>();
        List<Item> out = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - 10L * 24 * 3600 * 1000;
        for (Item it : items) {
            if (it.title.length() == 0 || !it.link.startsWith("http")) continue;
            if (it.time > 0 && it.time < cutoff) continue;
            String k = it.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (seen.contains(k) || seen.contains(it.link)) continue;
            seen.add(k);
            seen.add(it.link);
            out.add(it);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out.size() > 400 ? new ArrayList<>(out.subList(0, 400)) : out;
    }

    static List<Item> fetchFeed(String source, String url) throws Exception {
        byte[] body = null;
        String cur = url;
        for (int hop = 0; hop < 5 && body == null; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            try {
                c.setConnectTimeout(9000);
                c.setReadTimeout(12000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", BROWSER_UA);
                c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) break;
                    cur = new URL(new URL(cur), loc).toString();
                } else if (code == 200) {
                    body = StationRepo.readAll(c.getInputStream(), 8 << 20);
                } else {
                    throw new IOException("HTTP " + code);
                }
            } finally {
                c.disconnect();
            }
        }
        return body == null ? new ArrayList<>() : parse(source, body);
    }

    static List<Item> parse(String source, byte[] xml) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        try {
            p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true);
        } catch (Exception ignored) {
        }
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(new ByteArrayInputStream(xml), null);
        List<Item> out = new ArrayList<>();
        Item it = null;
        String html = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName().toLowerCase(Locale.ROOT);
                if (tag.equals("item") || tag.equals("entry")) {
                    it = new Item();
                    it.source = source;
                    html = null;
                } else if (it != null) {
                    if (tag.equals("title")) {
                        it.title = clean(text(p));
                    } else if (tag.equals("link")) {
                        String href = p.getAttributeValue(null, "href");
                        String rel = p.getAttributeValue(null, "rel");
                        if (href != null) {
                            if (rel == null || rel.equals("alternate")) it.link = href.trim();
                        } else {
                            String t = text(p).trim();
                            if (t.length() > 0) it.link = t;
                        }
                    } else if (tag.equals("guid") && it.link.length() == 0) {
                        String t = text(p).trim();
                        if (t.startsWith("http")) it.link = t;
                    } else if (tag.equals("pubdate") || tag.equals("published") || tag.equals("dc:date")
                            || (tag.equals("updated") && it.time == 0)) {
                        long t = parseDate(text(p).trim());
                        if (t > 0) it.time = t;
                    } else if (tag.equals("description") || tag.equals("summary") || tag.equals("content:encoded")
                            || tag.equals("content")) {
                        String t = text(p);
                        if ((html == null || tag.equals("description") || tag.equals("summary")) && it.summary.length() == 0) {
                            it.summary = summary(t);
                        }
                        html = html == null ? t : html + t;
                    } else if (tag.equals("enclosure")) {
                        String type = p.getAttributeValue(null, "type");
                        String u = p.getAttributeValue(null, "url");
                        if (u != null && (type == null || type.startsWith("image")) && it.image.length() == 0) it.image = u;
                    } else if (tag.equals("media:content") || tag.equals("media:thumbnail")) {
                        String u = p.getAttributeValue(null, "url");
                        String medium = p.getAttributeValue(null, "medium");
                        String type = p.getAttributeValue(null, "type");
                        boolean img = "image".equals(medium) || (type != null && type.startsWith("image"))
                                || (u != null && u.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png|webp)(\\?.*)?$"));
                        if (u != null && img && (it.image.length() == 0 || tag.equals("media:content"))) it.image = u;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && it != null) {
                String tag = p.getName().toLowerCase(Locale.ROOT);
                if (tag.equals("item") || tag.equals("entry")) {
                    if (it.image.length() == 0 && html != null) {
                        Matcher m = IMG.matcher(html);
                        while (m.find()) {
                            String u = m.group(1);
                            if (!u.contains("pixel") && !u.contains("feedburner") && !u.endsWith(".gif")) {
                                it.image = u;
                                break;
                            }
                        }
                    }
                    it.image = it.image.replace("&amp;", "&").trim();
                    if (it.image.startsWith("//")) it.image = "https:" + it.image;
                    if (it.image.length() > 0 && !it.image.startsWith("http")) {
                        try {
                            it.image = new URL(new URL(it.link), it.image).toString();
                        } catch (Exception e) {
                            it.image = "";
                        }
                    }
                    out.add(it);
                    if (out.size() >= 40) break;
                    it = null;
                }
            }
        }
        return out;
    }

    private static String text(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.START_TAG) depth++;
            else if (ev == XmlPullParser.END_TAG) depth--;
            else if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) sb.append(p.getText());
            else if (ev == XmlPullParser.END_DOCUMENT) break;
        }
        return sb.toString();
    }

    private static String clean(String s) {
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
    }

    private static String summary(String s) {
        String t = Html.fromHtml(s.replaceAll("(?is)<(script|style|figure)[^>]*>.*?</\\1>", " "), Html.FROM_HTML_MODE_LEGACY)
                .toString().replace('￼', ' ').replaceAll("\\s+", " ").trim();
        if (t.length() <= 240) return t;
        int cut = t.lastIndexOf(' ', 230);
        if (cut <= 120) cut = 230;
        return t.substring(0, cut) + "…";
    }

    static long parseDate(String s) {
        if (s == null || s.length() == 0) return 0;
        long now = System.currentTimeMillis();
        for (String pat : DATE_PATTERNS) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pat, Locale.ENGLISH);
                if (pat.endsWith("'Z'")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                if (pat.equals("yyyy-MM-dd HH:mm:ss")) f.setTimeZone(TimeZone.getTimeZone("Africa/Johannesburg"));
                f.setLenient(true); // some feeds get the weekday wrong; don't reject the whole date

                Date d = f.parse(s);
                if (d == null) continue;
                Calendar k = Calendar.getInstance();
                k.setTime(d);
                if (k.get(Calendar.YEAR) < 2000) continue; // a 4-digit pattern swallowed a 2-digit year
                long t = d.getTime();
                return t > now + 3600_000L ? now : t;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    public static String ago(long t) {
        if (t <= 0) return "";
        long min = (System.currentTimeMillis() - t) / 60000;
        if (min < 1) return "Just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 24) return h + " h ago";
        long d = h / 24;
        if (d == 1) return "Yesterday";
        if (d < 7) return d + " days ago";
        return new SimpleDateFormat("d MMM", Locale.ENGLISH).format(new Date(t));
    }

    public static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        for (Item it : items) a.put(it.toJson());
        File tmp = new File(c.getFilesDir(), "news.json.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(c.getFilesDir(), "news.json"));
    }

    public static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            File f = new File(c.getFilesDir(), "news.json");
            if (f.exists()) {
                JSONArray a = new JSONArray(new String(StationRepo.readAll(new FileInputStream(f), 16 << 20), StandardCharsets.UTF_8));
                for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
