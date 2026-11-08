package za.saradio.app;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/**
 * Renders each screen to build/screenshots so the layout can be checked by eye. Needs the
 * network for logos and headlines, so it only runs with {@code ./gradlew test -Plive=true}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreensTest {
    private List<Station> stations;

    @Before
    public void seed() throws Exception {
        Assume.assumeTrue("screenshots need -Plive=true", Boolean.getBoolean("live"));
        Context c = RuntimeEnvironment.getApplication();
        String json = LogicTest.fixture("stations_fixture.json");
        try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), "stations.json"))) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
        }
        Prefs.get(c).edit().clear().putLong("stationsAt", System.currentTimeMillis()).apply();
        stations = StationRepo.parse(json);
        RadioService.current = null;
        RadioService.state = RadioService.IDLE;
        RadioService.songTitle = "";
    }

    private Station named(String n) {
        for (Station s : stations) if (s.name.equalsIgnoreCase(n)) return s;
        return stations.get(0);
    }

    private void playing(Context c) {
        Station jac = named("Jacaranda FM");
        RadioService.current = jac;
        RadioService.state = RadioService.PLAYING;
        RadioService.message = "Live";
        RadioService.songTitle = "Tyla – Water";
        RadioService.nowStream = DataMode.rank(jac, DataMode.BALANCED).get(0);
        RadioService.streamCount = jac.streams().size();
        RadioService.sessionBytes = 23_400_000;
        Prefs.addMonthBytes(c, 1_240_000_000L);
        for (int i = 6; i >= 0; i--) Prefs.addRecent(c, stations.get(i));
        Prefs.addRecent(c, jac);
        List<Station> favs = new ArrayList<>();
        favs.add(stations.get(1));
        favs.add(stations.get(3));
        favs.add(jac);
        Prefs.saveFavourites(c, favs);
    }

    private MainActivity launch() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        float d = a.getResources().getDisplayMetrics().density;
        WindowInsets in = new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, Math.round(32 * d), 0, Math.round(20 * d)))
                .build();
        root.dispatchApplyWindowInsets(in);
        settle(a, 1500);
        return a;
    }

    /** Lets background loads finish and animations end. */
    private static void settle(MainActivity a, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));
    }

    private static void shot(MainActivity a, String name) throws Exception {
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getWidth(), root.getHeight());
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File dir = new File(System.getProperty("screenshots.dir", "build/screenshots"));
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream o = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    @Test
    public void home() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        settle(a, 9000);
        shot(a, "01_home");
    }

    @Test
    public void favouritesEmpty() throws Exception {
        MainActivity a = launch();
        a.setTab(MainActivity.TAB_FAV);
        settle(a, 1000);
        shot(a, "03_favourites_empty");
    }

    @Test
    public void favourites() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        a.setTab(MainActivity.TAB_FAV);
        settle(a, 6000);
        shot(a, "02_favourites");
    }

    @Test
    public void news() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        List<NewsRepo.Item> items = NewsRepo.fetchAll();
        assertTrue("live feeds returned " + items.size(), items.size() > 50);
        NewsRepo.save(c, items);
        playing(c);
        MainActivity a = launch();
        a.setTab(MainActivity.TAB_NEWS);
        settle(a, 25000);
        shot(a, "04_news");
    }

    @Test
    public void nowPlaying() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        settle(a, 4000);
        a.openNowPlaying();
        settle(a, 4000);
        shot(a, "05_now_playing");
        Prefs.setDataMode(a, DataMode.SAVER);
        Prefs.get(a).edit().putString("eqPreset", "talk").apply();
        RadioService.nowStream = DataMode.rank(RadioService.current, DataMode.SAVER).get(0);
        a.refreshPlayer();
        a.nowPlaying.scrollToEnd();
        settle(a, 1500);
        shot(a, "05b_now_playing_lower_saver");
    }

    @Test
    public void sound() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        Prefs.get(RuntimeEnvironment.getApplication()).edit().putString("eqPreset", "amapiano").putInt("fx_bass", 40).apply();
        MainActivity a = launch();
        Panels.sound(a);
        settle(a, 1500);
        shot(a, "06b_sound_unsupported_phone");
        a.onBackPressed();
        settle(a, 800);
        AudioFx.previewBandsForTest(new int[] {60, 230, 910, 3600, 14000}, -1500, 1500);
        Panels.sound(a);
        settle(a, 1500);
        shot(a, "06_sound");
    }

    @Test
    public void settings() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        Panels.settings(a);
        settle(a, 1500);
        shot(a, "07_settings");
    }

    @Test
    public void sleepAndAdd() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        Panels.sleep(a);
        settle(a, 1500);
        shot(a, "08_sleep");
        a.onBackPressed();
        settle(a, 800);
        Panels.addStation(a);
        settle(a, 1500);
        shot(a, "09_add_station");
    }

    private static NewsRepo.Item item(org.json.JSONObject j) throws Exception {
        NewsRepo.Item it = new NewsRepo.Item();
        it.source = j.getString("source");
        it.link = j.getString("link");
        it.title = j.getString("title");
        it.time = System.currentTimeMillis() - 40 * 60_000L;
        return it;
    }

    private void reader(MainActivity a, String json, String name, boolean extras) throws Exception {
        org.json.JSONObject doc = new org.json.JSONObject(json);
        NewsRepo.Item it = item(doc.getJSONObject("item"));
        Article art = Article.fromJson(it.link, doc.getJSONObject("article").toString());
        a.setTab(MainActivity.TAB_NEWS);
        a.openArticleWith(it, art);
        settle(a, 6000);
        shot(a, name);
        if (!extras) return;
        a.articleView().scrollToFraction(0.45f);
        settle(a, 3000);
        shot(a, name + "_middle");
        a.articleView().scrollToFraction(1f);
        settle(a, 1500);
        shot(a, name + "_end");
    }

    @Test
    public void readerView() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        reader(a, LogicTest.fixture("reader_sample.json"), "12_reader_sample", false);
        // Real stories (kept out of the repo) when -PreaderFixtures=<dir> is given.
        File dir = new File(System.getProperty("reader.fixtures", ""));
        File[] real = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".json")) : null;
        if (real == null) return;
        java.util.Arrays.sort(real);
        for (File f : real) {
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            reader(a, json, "13_reader_" + f.getName().replace(".json", "").replace("reader_", ""), true);
        }
    }

    @Test
    public void readerStates() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        Context c = RuntimeEnvironment.getApplication();
        MainActivity a = launch();
        org.json.JSONObject doc = new org.json.JSONObject(LogicTest.fixture("reader_sample.json"));
        NewsRepo.Item it = item(doc.getJSONObject("item"));
        a.setTab(MainActivity.TAB_NEWS);
        Article teaser = Article.fromJson(it.link, doc.getJSONObject("article").toString());
        teaser.paywalled = true;
        while (teaser.blocks.size() > 2) teaser.blocks.remove(teaser.blocks.size() - 1);
        teaser.words = 70;
        it.source = "Business Day";
        a.openArticleWith(it, teaser);
        settle(a, 1500);
        a.articleView().scrollToFraction(1f);
        settle(a, 1500);
        shot(a, "14_reader_paywall");
        it.source = "SABC News";
        a.openArticleWith(it, Article.failed(it.link, "too-short"));
        settle(a, 1500);
        shot(a, "15_reader_not_an_article");
        Prefs.setDataMode(c, DataMode.SAVER);
        Prefs.get(c).edit().putInt("readerSize", 3).apply();
        it.source = "Sample News";
        a.openArticleWith(it, Article.fromJson(it.link, doc.getJSONObject("article").toString()));
        settle(a, 1500);
        a.articleView().scrollToFraction(0.5f);
        settle(a, 1500);
        shot(a, "16_reader_saver_xl");
        Prefs.get(c).edit().putInt("readerSize", 1).apply();
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    public void smallPhone() throws Exception {
        playing(RuntimeEnvironment.getApplication());
        MainActivity a = launch();
        settle(a, 8000);
        shot(a, "10_small_home");
        a.openNowPlaying();
        settle(a, 3000);
        shot(a, "11_small_now_playing");
    }
}
