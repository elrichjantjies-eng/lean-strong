package za.saradio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LogicTest {

    static String fixture(String name) {
        InputStream in = LogicTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(name, in);
        return new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
    }

    // ---- Stations ---------------------------------------------------------------------------

    @Test
    public void duplicateListingsBecomeAlternateStreams() {
        List<Station> all = StationRepo.parse(fixture("stations_fixture.json"));
        Station jac = null;
        for (Station s : all) if (s.name.equals("Jacaranda FM")) jac = s;
        assertNotNull(jac);
        assertTrue("Jacaranda should keep its other streams", jac.streams().size() >= 2);
        int count = 0;
        for (Station s : all) if (StationRepo.normName(s.name).equals("jacarandafm")) count++;
        assertEquals("only one Jacaranda tile", 1, count);
    }

    @Test
    public void dataModePicksLightOrHeavyStream() {
        Station s = new Station();
        s.name = "Test FM";
        s.url = "http://a/48";
        s.bitrate = 48;
        s.codec = "AAC+";
        s.alts.add(new Station.Stream("http://a/128", 128, "MP3"));
        s.alts.add(new Station.Stream("http://a/320", 320, "MP3"));
        assertEquals(48, DataMode.rank(s, DataMode.SAVER).get(0).bitrate);
        assertEquals(320, DataMode.rank(s, DataMode.BEST).get(0).bitrate);
        assertEquals(128, DataMode.rank(s, DataMode.BALANCED).get(0).bitrate);
        // Every stream stays in the plan as a fallback.
        assertEquals(3, DataMode.rank(s, DataMode.SAVER).size());
    }

    @Test
    public void unplayableFlvListingIsNotChosen() {
        String json = "[{\"name\":\"5FM\",\"url_resolved\":\"http://x/flv\",\"codec\":\"FLV\",\"bitrate\":0,\"clickcount\":9},"
                + "{\"name\":\"5FM\",\"url_resolved\":\"http://x/aac\",\"codec\":\"AAC+\",\"bitrate\":48,\"clickcount\":5}]";
        List<Station> l = StationRepo.parse(json);
        assertEquals(1, l.size());
        assertEquals("http://x/aac", l.get(0).url);
        for (Station.Stream st : l.get(0).streams()) assertFalse(st.codec.equals("FLV"));
    }

    @Test
    public void provinceComesFromCityNamesWhenStateIsBlank() {
        assertEquals("Gauteng", StationRepo.province("", "Jozi FM", "community"));
        assertEquals("KwaZulu-Natal", StationRepo.province("KZN", "", ""));
        assertEquals("Western Cape", StationRepo.province("", "Radio Tygerberg", "cape town, talk"));
        assertEquals("", StationRepo.province("", "Smooth Jazz", "jazz"));
    }

    @Test
    public void stationSurvivesJsonRoundTrip() {
        Station s = new Station();
        s.name = "Test";
        s.url = "http://a";
        s.alts.add(new Station.Stream("http://b", 64, "MP3"));
        Station back = Station.fromJson(s.toJson());
        assertEquals(1, back.alts.size());
        assertEquals(64, back.alts.get(0).bitrate);
    }

    // ---- News -------------------------------------------------------------------------------

    @Test
    public void twoDigitYearDatesAreNotThrownAway() {
        // eNCA's format. The old app parsed this as the year 26 AD and dropped every story.
        long t = NewsRepo.parseDate("Wed, 04 Nov 26 15:45:00 +0200");
        Calendar k = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        k.setTimeInMillis(t);
        assertEquals(2026, k.get(Calendar.YEAR));
        assertEquals(13, k.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void commonDateFormatsParse() {
        assertTrue(NewsRepo.parseDate("Wed, 04 Nov 2026 13:49:50 GMT") > 0);
        assertTrue(NewsRepo.parseDate("Tue, 3 Nov 2026 08:24:46 +0000") > 0);
        assertTrue(NewsRepo.parseDate("2026-11-04T10:00:00+02:00") > 0);
        assertEquals(0, NewsRepo.parseDate("not a date"));
    }

    @Test
    public void rssItemsParseWithImages() throws Exception {
        String rss = "<rss><channel><item><title>Load-shedding &amp; you</title><link>https://ex.co.za/a</link>"
                + "<pubDate>Wed, 04 Nov 26 15:45:00 +0200</pubDate><description><![CDATA[<p>Hello <b>world</b></p>]]></description>"
                + "<media:content url=\"https://ex.co.za/a.jpg\" medium=\"image\"/></item></channel></rss>";
        List<NewsRepo.Item> items = NewsRepo.parse("eNCA", rss.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, items.size());
        assertEquals("Load-shedding & you", items.get(0).title);
        assertEquals("https://ex.co.za/a.jpg", items.get(0).image);
        assertEquals("Hello world", items.get(0).summary);
        assertTrue(items.get(0).time > 0);
        assertEquals(1, NewsRepo.tidy(items).size());
    }

    // ---- Equalizer --------------------------------------------------------------------------

    @Test
    public void presetsMapOntoPhoneBands() {
        EqPresets.Preset bass = EqPresets.find("bass");
        float[] five = EqPresets.forBands(bass, EqPresets.DEFAULT_BANDS_HZ);
        assertEquals(5, five.length);
        assertTrue("60 Hz is boosted", five[0] >= 5);
        assertEquals(0f, five[4], 0.01f);
        float[] ten = EqPresets.forBands(EqPresets.find("talk"), new int[] {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000});
        assertEquals(-8f, ten[0], 0.01f);
        assertEquals(4f, ten[6], 0.01f);
        for (EqPresets.Preset p : EqPresets.ALL) assertEquals(p.id, 10, p.db.length);
    }

    @Test
    public void junkEqualizersAreRejected() {
        assertTrue(AudioFx.sane(new int[] {60, 230, 910, 3600, 14000}, new short[] {-1500, 1500}));
        assertFalse(AudioFx.sane(new int[] {0, 0, 0, 0, 0}, new short[] {-1500, 1500}));
        assertFalse(AudioFx.sane(new int[] {60, 230, 910, 3600, 14000}, new short[] {0, 0}));
        assertFalse(AudioFx.sane(new int[] {60}, new short[] {-1500, 1500}));
    }

    @Test
    public void interpolationIsLogarithmic() {
        float[] curve = {0, 0, 0, 0, 0, 0, 10, 10, 10, 10};
        // Halfway between 1 kHz and 2 kHz on a log scale is ~1414 Hz.
        assertEquals(5f, EqPresets.sample(curve, 1414), 0.1f);
    }

    @Test
    public void analyzerFindsATone() {
        int rate = 44100;
        Spectrum.Analyzer an = new Spectrum.Analyzer(rate);
        float[] levels = null;
        for (int i = 0; i < rate / 2; i++) {
            float[] l = an.add((float) (0.5 * Math.sin(2 * Math.PI * 1000 * i / rate)));
            if (l != null) levels = l.clone();
        }
        assertNotNull(levels);
        int loudest = 0;
        for (int b = 1; b < levels.length; b++) if (levels[b] > levels[loudest]) loudest = b;
        // 1 kHz sits a little past the middle of the 45 Hz..16 kHz log range.
        assertTrue("band " + loudest, loudest > Spectrum.BANDS / 2 - 3 && loudest < Spectrum.BANDS / 2 + 5);
        assertTrue(levels[loudest] > 0.6f);
        assertTrue(levels[0] < 0.2f);
    }

    // ---- Buffering --------------------------------------------------------------------------

    /** A valid MPEG-1 Layer III, 128 kbps, 44.1 kHz frame (417 bytes). */
    private static byte[] mp3Frame() {
        byte[] f = new byte[417];
        f[0] = (byte) 0xFF;
        f[1] = (byte) 0xFB;
        f[2] = (byte) 0x90;
        f[3] = (byte) 0x00;
        return f;
    }

    /**
     * Streams 128 kbps audio in real time (sped up 4x) and plays it back at the same pace. The old
     * app paused for a 2-second refill whenever the network buffer was empty at a frame boundary,
     * even with seconds of audio still waiting in the decoder. This counts how often each rule
     * would stall over 12 seconds of audio.
     */
    @Test
    public void bufferingOnlyStallsWhenAudioRunsOut() throws Exception {
        final int speed = 4;
        final StreamPlayer.Ring ring = new StreamPlayer.Ring(1 << 20);
        final byte[] frame = mp3Frame();
        final int bytesPerSec = 16000;
        Thread net = new Thread(() -> {
            try {
                // Burst a second of audio on connect, then real time in TCP-sized chunks.
                byte[] chunk = new byte[1460];
                long pos = 0;
                byte[] stream = new byte[frame.length * 600];
                for (int i = 0; i < 600; i++) System.arraycopy(frame, 0, stream, i * frame.length, frame.length);
                ring.write(stream, 0, bytesPerSec);
                pos = bytesPerSec;
                while (pos + chunk.length < stream.length) {
                    ring.write(stream, (int) pos, chunk.length);
                    pos += chunk.length;
                    Thread.sleep(1000L * chunk.length / bytesPerSec / speed);
                }
                ring.close(null);
            } catch (InterruptedException ignored) {
            }
        });
        net.start();
        ring.await(bytesPerSec, 5000);
        StreamPlayer.Frames fr = new StreamPlayer.Frames(ring);
        final int[] newRuleStalls = {0};
        final boolean[] started = {false};
        fr.onDry = () -> {
            if (started[0]) newRuleStalls[0]++;
        };
        fr.sync(1);
        assertEquals(44100, fr.sampleRate);
        int oldRuleStalls = 0;
        int frames = 0;
        long frameNs = fr.frameUs * 1000 / speed;
        long next = System.nanoTime();
        while (frames < 460) { // ~12 s of audio
            if (started[0] && ring.available() == 0) oldRuleStalls++;
            fr.next();
            started[0] = true;
            frames++;
            next += frameNs;
            long wait = next - System.nanoTime();
            if (wait > 0) Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
        }
        net.interrupt();
        assertEquals(128, fr.kbps);
        System.out.println("old rule stalls: " + oldRuleStalls + ", new rule stalls: " + newRuleStalls[0]);
        assertTrue("old rule should stall repeatedly, got " + oldRuleStalls, oldRuleStalls >= 5);
        assertEquals("new rule never stalls while audio is buffered", 0, newRuleStalls[0]);
    }
}
