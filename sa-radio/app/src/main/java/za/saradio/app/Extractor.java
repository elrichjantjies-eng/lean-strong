package za.saradio.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Turns a news page into a clean {@link Article}. Only the page's HTML is downloaded, never its
 * scripts, ads or trackers; Mozilla Readability then finds the story inside a hidden WebView that
 * has network access switched off.
 */
final class Extractor {

    interface Done {
        void onArticle(Article a);
    }

    private static final class Job {
        final String url;
        final String html;
        final int want;
        final Done done;

        Job(String url, String html, int want, Done done) {
            this.url = url;
            this.html = html;
            this.want = want;
            this.done = done;
        }
    }

    private static final Pattern META_CHARSET = Pattern.compile("<meta[^>]+charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final Activity act;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final LruCache<String, Article> cache = new LruCache<>(24);
    private WebView engine;
    private boolean ready;
    private Job running;
    private volatile String currentHtml = "";

    Extractor(Activity act) {
        this.act = act;
    }

    /** The JavaScript side pulls the page's HTML through this (strings this big don't belong in a script). */
    public final class Bridge {
        @JavascriptInterface
        public String html() {
            return currentHtml;
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void boot() {
        if (engine != null) return;
        engine = new WebView(act);
        WebSettings s = engine.getSettings();
        s.setJavaScriptEnabled(true);
        s.setBlockNetworkLoads(true); // the engine page can't fetch anything, ads included
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setLoadsImagesAutomatically(false);
        engine.addJavascriptInterface(new Bridge(), "SARBridge");
        engine.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                ready = true;
                next();
            }
        });
        // A WebView only runs reliably while attached; keep it tiny and invisible.
        engine.setAlpha(0f);
        ViewGroup root = (ViewGroup) act.findViewById(android.R.id.content);
        root.addView(engine, new FrameLayout.LayoutParams(1, 1));
        engine.loadUrl("file:///android_asset/reader/engine.html");
    }

    Article cached(String url) {
        return cache.get(url);
    }

    /** Fetches and extracts {@code url}; {@code done} is called on the main thread. */
    void extract(final String url, final int wantPx, final Done done) {
        Article hit = cache.get(url);
        if (hit != null) {
            main.post(() -> done.onArticle(hit));
            return;
        }
        boot();
        net.execute(() -> {
            final String[] page;
            try {
                page = fetch(url);
            } catch (Exception e) {
                main.post(() -> done.onArticle(Article.failed(url, "network: " + e.getMessage())));
                return;
            }
            main.post(() -> {
                queue.add(new Job(page[1], page[0], wantPx, a -> {
                    if (a.ok) cache.put(url, a);
                    a.url = url;
                    done.onArticle(a);
                }));
                next();
            });
        });
    }

    private void next() {
        if (!ready || running != null || queue.isEmpty()) return;
        final Job job = queue.poll();
        running = job;
        currentHtml = job.html;
        final Runnable timeout = () -> finish(job, Article.failed(job.url, "timeout"));
        main.postDelayed(timeout, 12000);
        String js = "SAR.extract(SARBridge.html(), " + JSONObject.quote(job.url) + ", " + job.want + ")";
        engine.evaluateJavascript(js, value -> {
            main.removeCallbacks(timeout);
            Article a;
            try {
                Object o = new JSONTokener(value).nextValue();
                a = o instanceof String ? Article.fromJson(job.url, (String) o) : Article.failed(job.url, "no-result");
            } catch (Exception e) {
                a = Article.failed(job.url, "bad-result");
            }
            finish(job, a);
        });
    }

    private void finish(Job job, Article a) {
        if (running != job) return;
        running = null;
        currentHtml = "";
        job.done.onArticle(a);
        next();
    }

    /** Downloads a page's HTML; returns {html, final url}. */
    static String[] fetch(String url) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            try {
                c.setConnectTimeout(9000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", NewsRepo.BROWSER_UA);
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
                c.setRequestProperty("Accept-Language", "en-ZA,en;q=0.9");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) throw new IOException("bad redirect");
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                byte[] body = StationRepo.readAll(c.getInputStream(), 6 << 20);
                return new String[] {new String(body, charset(c.getContentType(), body)), cur};
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    static Charset charset(String contentType, byte[] body) {
        String cs = null;
        if (contentType != null) {
            int i = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (i >= 0) cs = contentType.substring(i + 8).replace("\"", "").split(";")[0].trim();
        }
        if (cs == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher m = META_CHARSET.matcher(head);
            if (m.find()) cs = m.group(1);
        }
        try {
            if (cs != null) return Charset.forName(cs);
        } catch (Exception ignored) {
        }
        return StandardCharsets.UTF_8;
    }
}
