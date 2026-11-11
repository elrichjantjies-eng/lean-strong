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

    /** Region keys for world feeds, and their labels. */
    public static final String[] REGIONS = {"mideast", "africa", "asia", "europe", "americas", "global"};
    public static final String[] REGION_NAMES = {"Middle East", "Africa", "Asia", "Europe", "Americas", "Global"};

    /**
     * World outlets, chosen from different countries and owners so no one region or view
     * dominates; each entry is {name, url, region, country, who it is funded by}. The funding note
     * is shown to readers so they can judge it themselves. Checked November 2026; feeds that
     * refused automated readers (TRT World, Press TV, The New Arab, Arab News, Times of Israel,
     * teleSUR, Xinhua, The National) are left out, as is The New Humanitarian, whose feed only carries a
     * handful of stories a month.
     */
    public static final String[][] WORLD = {
        {"Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "mideast", "Qatar", "funded by the Qatari state"},
        {"Middle East Eye", "https://www.middleeasteye.net/rss", "mideast", "UK", "independent, Middle East focus"},
        {"Saudi Gazette", "https://saudigazette.com.sa/rssFeed/74", "mideast", "Saudi Arabia", "privately owned, Saudi"},
        {"Al-Monitor", "https://www.al-monitor.com/rss", "mideast", "US", "independent, reporters across the region"},
        {"Haaretz", "https://www.haaretz.com/srv/haaretz-latest-headlines", "mideast", "Israel", "independent, left-leaning"},
        {"Jerusalem Post", "https://www.jpost.com/rss/rssfeedsfrontpage.aspx", "mideast", "Israel", "privately owned, right-leaning"},
        {"Anadolu Agency", "https://www.aa.com.tr/en/rss/default?cat=world", "mideast", "Turkey", "Turkish state news agency"},
        {"Daily Sabah", "https://www.dailysabah.com/rssFeed/world", "mideast", "Turkey", "privately owned, pro-government"},
        {"AllAfrica", "https://allafrica.com/tools/headlines/rdf/latest/headlines.rdf", "africa", "Pan-African", "gathers 100+ African newspapers"},
        {"Africanews", "https://www.africanews.com/feed/rss", "africa", "Congo", "owned by Euronews"},
        {"Premium Times", "https://www.premiumtimesng.com/feed", "africa", "Nigeria", "independent"},
        {"Nation", "https://nation.africa/kenya/rss.xml", "africa", "Kenya", "privately owned (Nation Media Group)"},
        {"The Hindu", "https://www.thehindu.com/news/international/feeder/default.rss", "asia", "India", "privately owned"},
        {"NDTV", "https://feeds.feedburner.com/ndtvnews-world-news", "asia", "India", "privately owned (Adani Group)"},
        {"Dawn", "https://www.dawn.com/feeds/home", "asia", "Pakistan", "privately owned"},
        {"South China Morning Post", "https://www.scmp.com/rss/91/feed", "asia", "Hong Kong", "owned by Alibaba"},
        {"CGTN", "https://www.cgtn.com/subscribe/rss/section/world.xml", "asia", "China", "Chinese state broadcaster"},
        {"CNA", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml&category=6311", "asia", "Singapore", "state-linked (Mediacorp)"},
        {"The Straits Times", "https://www.straitstimes.com/news/world/rss.xml", "asia", "Singapore", "state-linked (SPH Media)"},
        {"The Japan Times", "https://www.japantimes.co.jp/feed/", "asia", "Japan", "privately owned"},
        {"MercoPress", "https://en.mercopress.com/rss", "americas", "South Atlantic", "independent, South America"},
        {"Buenos Aires Times", "https://www.batimes.com.ar/feed", "americas", "Argentina", "privately owned (Perfil)"},
        {"CBC", "https://www.cbc.ca/webfeed/rss/rss-world", "americas", "Canada", "Canadian public broadcaster"},
        {"DW", "https://rss.dw.com/rdf/rss-en-all", "europe", "Germany", "German public broadcaster"},
        {"France 24", "https://www.france24.com/en/rss", "europe", "France", "French public broadcaster"},
        {"RFI", "https://www.rfi.fr/en/rss", "europe", "France", "French public broadcaster"},
        {"Euronews", "https://www.euronews.com/rss", "europe", "Europe", "privately owned (Alpac Capital)"},
        {"BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "europe", "UK", "UK public broadcaster"},
        {"TASS", "https://tass.com/rss/v2.xml", "europe", "Russia", "Russian state news agency"},
        {"UN News", "https://news.un.org/feed/subscribe/en/news/all/rss.xml", "global", "United Nations", "UN"},
        {"Global Voices", "https://globalvoices.org/feed/", "global", "Worldwide", "non-profit, citizen media"},
    };

    static String regionName(String key) {
        for (int i = 0; i < REGIONS.length; i++) if (REGIONS[i].equals(key)) return REGION_NAMES[i];
        return "";
    }

    /** The feed entry for a world outlet, or null for a South African one. */
    public static String[] worldFeed(String source) {
        for (String[] f : WORLD) if (f[0].equals(source)) return f;
        return null;
    }

    /** Just the South African stories (the Headlines list and the news widgets). */
    public static List<Item> local(List<Item> all) {
        List<Item> out = new ArrayList<>();
        for (Item it : all) if (it.region.isEmpty()) out.add(it);
        return out;
    }

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
        /** A {@link #REGIONS} key for world stories; empty for South African ones. */
        public String region = "";
        public String country = "";

        /** "Al Jazeera · Qatar" for world stories, just the name for South African ones. */
        public String byline() {
            return country.isEmpty() ? source : source + " · " + country;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("t", title);
                o.put("l", link);
                o.put("s", source);
                o.put("i", image);
                o.put("d", summary);
                o.put("w", time);
                if (!region.isEmpty()) o.put("r", region);
                if (!country.isEmpty()) o.put("c", country);
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
            it.region = o.optString("r");
            it.country = o.optString("c");
            return it;
        }
    }

    /** South African headlines first, then the world outlets; each set is tidied on its own. */
    public static List<Item> fetchAll() {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<List<Item>>> local = new ArrayList<>(), world = new ArrayList<>();
        for (final String[] f : FEEDS) local.add(pool.submit(() -> feedOrEmpty(f, "", "")));
        for (final String[] f : WORLD) world.add(pool.submit(() -> feedOrEmpty(f, f[2], f[3])));
        long deadline = System.currentTimeMillis() + 30000;
        List<Item> out = new ArrayList<>(tidy(gather(local, deadline), 400));
        // 32 outlets at up to 20 stories each; the cap only trims a backlog of undated items.
        // World magazines publish less often, so they keep three weeks instead of ten days.
        out.addAll(tidy(gather(world, deadline), 640, 21));
        pool.shutdownNow();
        return out;
    }

    private static List<Item> feedOrEmpty(String[] f, String region, String country) {
        try {
            List<Item> items = fetchFeed(f[0], f[1]);
            if (!region.isEmpty() && items.size() > 20) items = new ArrayList<>(items.subList(0, 20));
            for (Item it : items) {
                it.region = region;
                it.country = country;
            }
            return items;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static List<Item> gather(List<Future<List<Item>>> jobs, long deadline) {
        List<Item> all = new ArrayList<>();
        for (Future<List<Item>> j : jobs) {
            try {
                all.addAll(j.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS));
            } catch (Exception ignored) {
            }
        }
        return all;
    }

    static List<Item> tidy(List<Item> items) {
        return tidy(items, 400);
    }

    static List<Item> tidy(List<Item> items, int max) {
        return tidy(items, max, 10);
    }

    static List<Item> tidy(List<Item> items, int max, int days) {
        Set<String> seen = new HashSet<>();
        List<Item> out = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - days * 24L * 3600 * 1000;
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
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
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
                    // Some servers (Middle East Eye) gzip the feed even when it wasn't asked for.
                    String enc = c.getHeaderField("Content-Encoding");
                    boolean gz = (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip"))
                            || (body.length > 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B);
                    if (gz) {
                        try {
                            body = StationRepo.readAll(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(body)), 8 << 20);
                        } catch (IOException ignored) {
                        }
                    }
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
                    if (out.size() >= 30) break;
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
        Widgets.newsChanged(c);
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
