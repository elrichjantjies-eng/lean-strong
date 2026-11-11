package za.saradio.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Station logos and news pictures: memory cache, disk cache, newest requests first. */
public final class ImageLoader {
    private ImageLoader() {}

    public interface Target {
        boolean wants(String url);

        void onBitmap(String url, Bitmap b);
    }

    private static final long DISK_MAX = 40L << 20;
    private static final long DISK_TRIM_TO = 25L << 20;

    private static File diskDir;
    private static final LruCache<String, Bitmap> MEM =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L << 20)) {
                @Override
                protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };
    private static final Map<String, String> LAST_KEY = new ConcurrentHashMap<>();
    /** URLs that failed; retried after {@link #clearFailures()} (e.g. when the network comes back). */
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> OG_CACHE = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(6, 6, 30, TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public boolean offer(Runnable r) {
                    return offerFirst(r); // what's on screen now beats what scrolled past
                }
            });

    private static final Pattern OG1 = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG2 = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image)[\"']",
            Pattern.CASE_INSENSITIVE);

    static {
        POOL.allowCoreThreadTimeOut(true);
    }

    public static void init(Context c) {
        if (diskDir != null) return;
        File d = new File(c.getCacheDir(), "img");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        diskDir = d;
        POOL.execute(ImageLoader::trimDisk);
    }

    public static void clearFailures() {
        FAILED.clear();
    }

    private static String key(String url, int px) {
        return px + "|" + url;
    }

    public static Bitmap cached(String url, int px) {
        if (url == null || url.length() == 0) return null;
        return MEM.get(key(url, px));
    }

    /** Any size of this picture that's already in memory. */
    /** The logo from the disk cache, decoded at about {@code px} (for widgets; call off the main thread when possible). */
    static Bitmap fromDisk(String url, int px) {
        if (url == null || url.isEmpty()) return null;
        try {
            return loadDisk(url, px);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap cachedAny(String url) {
        if (url == null || url.length() == 0) return null;
        String k = LAST_KEY.get(url);
        return k == null ? null : MEM.get(k);
    }

    public static void get(final String url, final int px, final Target t) {
        if (url == null || !url.startsWith("http") || FAILED.contains(url)) return;
        Bitmap hit = cached(url, px);
        if (hit != null) {
            t.onBitmap(url, hit);
            return;
        }
        POOL.execute(() -> {
            if (!t.wants(url)) return;
            Bitmap b = cached(url, px);
            if (b == null) b = loadDisk(url, px);
            if (b == null) {
                if (!t.wants(url)) return;
                byte[] data = download(url);
                if (data == null) {
                    FAILED.add(url);
                    return;
                }
                b = decode(data, px);
                if (b == null) {
                    FAILED.add(url);
                    return;
                }
                saveDisk(url, data);
            }
            MEM.put(key(url, px), b);
            LAST_KEY.put(url, key(url, px));
            final Bitmap done = b;
            MAIN.post(() -> {
                if (t.wants(url)) t.onBitmap(url, done);
            });
        });
    }

    public static void load(final ImageView v, String url, Drawable placeholder, int px) {
        v.setTag(url);
        v.animate().cancel();
        v.setAlpha(1f);
        Bitmap hit = cached(url, px);
        if (hit != null) {
            v.setImageBitmap(hit);
            return;
        }
        v.setImageDrawable(placeholder);
        get(url, px, new Target() {
            @Override
            public boolean wants(String u) {
                return u.equals(v.getTag());
            }

            @Override
            public void onBitmap(String u, Bitmap b) {
                v.setImageBitmap(b);
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(180).start();
            }
        });
    }

    /** News pictures. Without one in the feed, the article page is opened to find its preview picture. */
    public static void loadArticleImage(final ImageView v, final NewsRepo.Item item, final Drawable placeholder,
            final int px, boolean allowPageLookup) {
        if (item.image.length() > 0) {
            load(v, item.image, placeholder, px);
            return;
        }
        final String tag = "og:" + item.link;
        v.setTag(tag);
        v.setImageDrawable(placeholder);
        String known = OG_CACHE.get(item.link);
        if (known != null) {
            if (known.length() > 0) {
                item.image = known;
                load(v, known, placeholder, px);
            }
            return;
        }
        if (!allowPageLookup) return;
        POOL.execute(() -> {
            if (!tag.equals(v.getTag())) return;
            final String found = findOgImage(item.link);
            OG_CACHE.put(item.link, found == null ? "" : found);
            if (found == null) return;
            MAIN.post(() -> {
                item.image = found;
                if (tag.equals(v.getTag())) load(v, found, placeholder, px);
            });
        });
    }

    static String findOgImage(String pageUrl) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(pageUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", NewsRepo.BROWSER_UA);
            if (c.getResponseCode() != 200) {
                c.disconnect();
                return null;
            }
            byte[] buf = new byte[160 * 1024];
            int len = 0;
            InputStream in = c.getInputStream();
            int n;
            while (len < buf.length && (n = in.read(buf, len, buf.length - len)) > 0) {
                len += n;
                String head = new String(buf, Math.max(0, len - n - 8), Math.min(len, n + 8), StandardCharsets.ISO_8859_1);
                if (head.contains("</head>") || head.contains("</HEAD>")) break; // stop once the <head> is in
            }
            c.disconnect();
            String html = new String(buf, 0, len, StandardCharsets.UTF_8);
            Matcher m = OG1.matcher(html);
            String u = m.find() ? m.group(1) : null;
            if (u == null) {
                Matcher m2 = OG2.matcher(html);
                if (m2.find()) u = m2.group(1);
            }
            if (u == null) return null;
            return new URL(new URL(pageUrl), u.replace("&amp;", "&").trim()).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static File diskFile(String url) {
        if (diskDir == null) return null;
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return new File(diskDir, sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap loadDisk(String url, int px) {
        File f = diskFile(url);
        if (f == null || !f.exists()) return null;
        try {
            Bitmap b = decode(StationRepo.readAll(new FileInputStream(f), 4 << 20), px);
            if (b != null) {
                //noinspection ResultOfMethodCallIgnored
                f.setLastModified(System.currentTimeMillis());
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveDisk(String url, byte[] data) {
        File f = diskFile(url);
        if (f == null) return;
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        } catch (Exception ignored) {
        }
    }

    /** The old app never deleted pictures; keep the cache under ~40 MB, dropping the least recently used. */
    private static void trimDisk() {
        File d = diskDir;
        File[] files = d == null ? null : d.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= DISK_MAX) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= DISK_TRIM_TO) break;
            total -= f.length();
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    static byte[] download(String url) {
        String cur = url;
        for (int hop = 0; hop < 5; hop++) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(9000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", NewsRepo.BROWSER_UA);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return null;
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code != 200) {
                    c.disconnect();
                    return null;
                }
                byte[] all = StationRepo.readAll(c.getInputStream(), 4 << 20);
                c.disconnect();
                return all;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    static Bitmap decode(byte[] data, int px) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
            if (b == null) return null;
            int max = Math.max(b.getWidth(), b.getHeight());
            if (max <= px * 1.5f) return b;
            float s = px / (float) max;
            Bitmap scaled = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)),
                    Math.max(1, Math.round(b.getHeight() * s)), true);
            if (scaled != b) b.recycle();
            return scaled;
        } catch (Throwable t) {
            return null;
        }
    }
}
