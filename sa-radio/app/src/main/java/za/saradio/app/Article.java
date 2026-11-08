package za.saradio.app;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** A story pulled out of a news page by the reader (see assets/reader/extract.js). */
final class Article {

    static final class Block {
        /** p, h, quote, img, list or embed. */
        String type = "p";
        String html = "";
        String text = "";
        String src = "";
        String caption = "";
        String url = "";
        int width;
        int height;
        boolean ordered;
        boolean table;
        final List<String> items = new ArrayList<>();
    }

    boolean ok;
    String reason = "";
    String url = "";
    String title = "";
    String byline = "";
    String site = "";
    String excerpt = "";
    String leadSrc = "";
    String leadCaption = "";
    int words;
    boolean paywalled;
    final List<Block> blocks = new ArrayList<>();

    /** Minutes to read at a comfortable 220 words a minute. */
    int minutes() {
        return Math.max(1, Math.round(words / 220f));
    }

    /** A subscriber-only story where the page only gives away the opening. */
    boolean teaserOnly() {
        return paywalled && words < 350;
    }

    static Article failed(String url, String reason) {
        Article a = new Article();
        a.url = url;
        a.reason = reason;
        return a;
    }

    static Article fromJson(String url, String json) {
        Article a = new Article();
        a.url = url;
        try {
            JSONObject o = new JSONObject(json);
            a.ok = o.optBoolean("ok");
            a.reason = o.optString("reason");
            a.title = o.optString("title");
            a.byline = o.optString("byline");
            a.site = o.optString("site");
            a.excerpt = o.optString("excerpt");
            a.words = o.optInt("words");
            a.paywalled = o.optBoolean("paywalled");
            JSONObject lead = o.optJSONObject("lead");
            if (lead != null) {
                a.leadSrc = lead.optString("src");
                a.leadCaption = lead.optString("caption");
            }
            JSONArray bs = o.optJSONArray("blocks");
            for (int i = 0; bs != null && i < bs.length(); i++) {
                JSONObject j = bs.optJSONObject(i);
                if (j == null) continue;
                Block b = new Block();
                b.type = j.optString("t", "p");
                b.html = j.optString("html");
                b.text = j.optString("text");
                b.src = j.optString("src");
                b.caption = j.optString("caption");
                b.url = j.optString("url");
                b.width = j.optInt("w");
                b.height = j.optInt("h");
                b.ordered = j.optBoolean("ordered");
                b.table = j.optBoolean("table");
                JSONArray items = j.optJSONArray("items");
                for (int k = 0; items != null && k < items.length(); k++) {
                    JSONObject it = items.optJSONObject(k);
                    if (it != null) b.items.add(it.optString("html"));
                }
                a.blocks.add(b);
            }
        } catch (Exception e) {
            a.ok = false;
            a.reason = "bad-result";
        }
        return a;
    }
}
