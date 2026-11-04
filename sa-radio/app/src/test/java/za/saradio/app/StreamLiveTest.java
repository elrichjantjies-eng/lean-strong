package za.saradio.app;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaCodec;

/**
 * Connects to real South African stations and runs the real download, playlist, song-title and
 * frame-sync code. Only the audio decoder is a stand-in (the test runtime has none), so this
 * checks everything up to the point where sound would come out. Run with -Plive=true.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StreamLiveTest {

    private static final class Result {
        String name;
        String outcome = "timeout";
        String title = "";
        int kbps;
    }

    @Test
    public void topStationsConnectAndSync() throws Exception {
        Assume.assumeTrue("needs -Plive=true", Boolean.getBoolean("live"));
        ShadowMediaCodec.CodecConfig.Codec fake = (in, out) -> {
            in.position(in.limit());
            byte[] pcm = new byte[Math.min(out.remaining(), 4096)];
            out.put(pcm);
        };
        ShadowMediaCodec.addDecoder("audio/mp4a-latm", new ShadowMediaCodec.CodecConfig(16384, 16384, fake));
        ShadowMediaCodec.addDecoder("audio/mpeg", new ShadowMediaCodec.CodecConfig(16384, 16384, fake));

        List<Station> all = StationRepo.parse(StationRepo.fetchRaw());
        assertTrue("directory returned " + all.size(), all.size() > 100);
        List<Station> pick = new ArrayList<>();
        for (Station s : all) {
            if (pick.size() >= 14) break;
            if (s.url.toLowerCase(Locale.ROOT).contains(".m3u8")) continue; // HLS goes to the system player
            pick.add(s);
        }

        List<Result> results = new ArrayList<>();
        for (Station s : pick) {
            final Result r = new Result();
            r.name = s.name;
            final CountDownLatch done = new CountDownLatch(1);
            Station.Stream st = DataMode.rank(s, DataMode.BALANCED).get(0);
            StreamPlayer p = new StreamPlayer(st.url, st.bitrate, 0, 1f, 3f, new StreamPlayer.Callback() {
                @Override
                public void onPlaying(StreamPlayer pl) {
                    r.outcome = "playing";
                    done.countDown();
                }

                @Override
                public void onBuffering(StreamPlayer pl) {}

                @Override
                public void onError(StreamPlayer pl, Exception e, boolean unsupported) {
                    r.outcome = (unsupported ? "fallback(" : "error(") + e.getMessage() + ")";
                    done.countDown();
                }

                @Override
                public void onTitle(StreamPlayer pl, String t) {
                    r.title = t;
                }
            });
            p.start();
            done.await(15, TimeUnit.SECONDS);
            if (r.outcome.equals("playing")) Thread.sleep(2500); // let a few seconds of frames and titles arrive
            r.kbps = p.measuredKbps();
            p.stop();
            results.add(r);
        }

        int playing = 0;
        for (Result r : results) {
            if (r.outcome.equals("playing")) playing++;
            System.out.println(String.format(Locale.ROOT, "%-28s %-40s %4d kbps  %s", r.name, r.outcome, r.kbps, r.title));
        }
        System.out.println("playing via app decoder: " + playing + "/" + results.size());
        assertTrue("most top stations should play", playing >= results.size() / 2);
    }
}
