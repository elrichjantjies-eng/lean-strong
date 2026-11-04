package za.saradio.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plays Shoutcast/Icecast MP3 and AAC streams: one thread downloads into a ring buffer (pulling
 * out ICY song titles on the way), another splits frames, decodes them and plays the sound.
 * HLS and other formats are reported as unsupported so the service can fall back to MediaPlayer.
 */
public class StreamPlayer implements Spectrum.Clock {

    public interface Callback {
        void onPlaying(StreamPlayer p);

        void onBuffering(StreamPlayer p);

        void onError(StreamPlayer p, Exception e, boolean unsupported);

        void onTitle(StreamPlayer p, String title);
    }

    static class Unsupported extends IOException {
        Unsupported(String why) {
            super(why);
        }
    }

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    static final int RING_BYTES = 1 << 20;

    private final String url;
    private final int bitrateKbps;
    private final int sessionId;
    private final float startSeconds;
    private final float refillSeconds;
    private final Callback cb;
    private final Ring ring = new Ring(RING_BYTES);
    private final AtomicLong bytesIn = new AtomicLong();

    private volatile HttpURLConnection conn;
    private volatile boolean stopped;
    private volatile boolean reportedError;
    private volatile AudioTrack track;
    private volatile float volume = 1f;
    private volatile int measuredKbps;
    private Thread netThread;
    private Thread decThread;

    public StreamPlayer(String url, int bitrateKbps, int sessionId, float startSeconds, float refillSeconds, Callback cb) {
        this.url = url;
        this.bitrateKbps = bitrateKbps;
        this.sessionId = sessionId;
        this.startSeconds = startSeconds;
        this.refillSeconds = refillSeconds;
        this.cb = cb;
    }

    public void start() {
        netThread = new Thread(this::netLoop, "radio-net");
        netThread.start();
    }

    public void stop() {
        stopped = true;
        ring.close(null);
        AudioTrack t = track;
        if (t != null) {
            try {
                t.pause();
                t.flush();
            } catch (Exception ignored) {
            }
        }
        final HttpURLConnection c = conn;
        if (c != null) {
            new Thread(() -> {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }, "radio-close").start();
        }
        if (netThread != null) netThread.interrupt();
        if (decThread != null) decThread.interrupt();
    }

    public void setVolume(float v) {
        volume = v;
        AudioTrack t = track;
        if (t != null) {
            try {
                t.setVolume(v);
            } catch (Exception ignored) {
            }
        }
    }

    /** Bytes downloaded so far, including song-title metadata. */
    public long bytesRead() {
        return bytesIn.get();
    }

    /** Audio bitrate worked out from the frames themselves; 0 until known. */
    public int measuredKbps() {
        return measuredKbps;
    }

    @Override
    public long playedFrames() {
        AudioTrack t = track;
        return t == null ? -1 : (t.getPlaybackHeadPosition() & 0xFFFFFFFFL);
    }

    private void fail(Exception e) {
        if (stopped || reportedError) return;
        reportedError = true;
        cb.onError(this, e, e instanceof Unsupported);
    }

    // ---- Network ----------------------------------------------------------------------------

    private void netLoop() {
        try {
            HttpURLConnection c = null;
            String cur = url;
            for (int hop = 0; hop < 8 && c == null; hop++) {
                if (stopped) return;
                HttpURLConnection h = (HttpURLConnection) new URL(cur).openConnection();
                conn = h;
                h.setInstanceFollowRedirects(false);
                h.setConnectTimeout(8000);
                h.setReadTimeout(15000);
                h.setRequestProperty("User-Agent", StationRepo.USER_AGENT);
                h.setRequestProperty("Icy-MetaData", "1");
                h.setRequestProperty("Accept", "*/*");
                int code = h.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = h.getHeaderField("Location");
                    h.disconnect();
                    if (loc == null) throw new IOException("Bad redirect");
                    cur = new URL(new URL(cur), loc).toString();
                } else if (code == 200) {
                    String type = lower(h.getContentType());
                    String path = lower(new URL(cur).getPath());
                    boolean playlist = type.contains("mpegurl") || type.contains("scpls") || path.endsWith(".m3u")
                            || path.endsWith(".m3u8") || path.endsWith(".pls") || path.endsWith(".asx");
                    if (playlist) {
                        String body = new String(StationRepo.readAll(h.getInputStream(), 256 * 1024), StandardCharsets.UTF_8);
                        h.disconnect();
                        if (body.contains("#EXT-X-")) throw new Unsupported("HLS");
                        String next = firstUrl(body);
                        if (next == null) throw new IOException("Empty playlist");
                        cur = next;
                    } else if (type.contains("ogg") || type.contains("opus") || type.contains("flac") || type.contains("mp2t")
                            || type.contains("video") || type.contains("text/html")) {
                        throw new Unsupported(type);
                    } else {
                        c = h;
                    }
                } else {
                    throw new IOException("HTTP " + code);
                }
            }
            if (c == null) throw new IOException("Too many redirects");
            int metaInt = 0;
            try {
                metaInt = Integer.parseInt(c.getHeaderField("icy-metaint").trim());
            } catch (Exception ignored) {
            }
            decThread = new Thread(this::decodeLoop, "radio-decode");
            decThread.start();
            InputStream in = new BufferedInputStream(c.getInputStream(), 32768);
            byte[] buf = new byte[16384];
            if (metaInt > 0) {
                byte[] meta = new byte[4080];
                while (!stopped) {
                    int left = metaInt;
                    while (left > 0 && !stopped) {
                        int n = in.read(buf, 0, Math.min(left, buf.length));
                        if (n < 0) throw new IOException("Stream ended");
                        bytesIn.addAndGet(n);
                        ring.write(buf, 0, n);
                        left -= n;
                    }
                    int lenByte = in.read();
                    if (lenByte < 0) throw new IOException("Stream ended");
                    int len = lenByte * 16;
                    int got = 0;
                    while (got < len) {
                        int n = in.read(meta, got, len - got);
                        if (n < 0) throw new IOException("Stream ended");
                        got += n;
                    }
                    bytesIn.addAndGet(1 + len);
                    if (len > 0) {
                        String title = parseTitle(meta, len);
                        if (title != null && !stopped) cb.onTitle(this, title);
                    }
                }
            } else {
                while (!stopped) {
                    int n = in.read(buf);
                    if (n < 0) throw new IOException("Stream ended");
                    bytesIn.addAndGet(n);
                    ring.write(buf, 0, n);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            if (!stopped) {
                ring.close(e instanceof IOException ? (IOException) e : new IOException(e.getMessage()));
                if (decThread == null) fail(e);
            }
        } finally {
            HttpURLConnection c = conn;
            if (c != null && stopped) {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    static String firstUrl(String playlist) {
        for (String line : playlist.split("[\\r\\n]+")) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (t.toLowerCase(Locale.ROOT).startsWith("file") && eq > 0) t = t.substring(eq + 1).trim();
            int href = t.toLowerCase(Locale.ROOT).indexOf("href=\"");
            if (href >= 0) {
                int end = t.indexOf('"', href + 6);
                if (end > 0) t = t.substring(href + 6, end);
            }
            if (t.startsWith("http://") || t.startsWith("https://")) return t;
        }
        return null;
    }

    static String parseTitle(byte[] b, int len) {
        while (len > 0 && b[len - 1] == 0) len--;
        String s = new String(b, 0, len, StandardCharsets.UTF_8);
        if (s.indexOf('�') >= 0) s = new String(b, 0, len, LATIN1);
        int i = s.indexOf("StreamTitle='");
        if (i < 0) return null;
        int from = i + 13;
        int to = s.indexOf("';", from);
        if (to < 0) to = s.lastIndexOf('\'');
        if (to < from) return null;
        String t = s.substring(from, to).trim();
        return t.equals("-") ? "" : t;
    }

    // ---- Decoding ---------------------------------------------------------------------------

    private boolean started;
    private boolean starving;

    private void decodeLoop() {
        MediaCodec codec = null;
        AudioTrack t = null;
        try {
            int bytesPerSec = (bitrateKbps > 0 ? bitrateKbps : 128) * 125;
            int startBytes = clamp((int) (bytesPerSec * startSeconds), 6000, RING_BYTES / 4);
            final int refillBytes = clamp((int) (bytesPerSec * refillSeconds), 12000, RING_BYTES / 4);
            ring.await(startBytes, 15000);
            if (stopped) return;

            Frames fr = new Frames(ring);
            fr.sync(0);
            // Only when every buffered byte is used up do we pause and wait for a proper refill,
            // rather than stuttering through tiny gaps.
            fr.onDry = () -> {
                if (started && !starving && !stopped) {
                    starving = true;
                    cb.onBuffering(this);
                    ring.await(refillBytes, 20000);
                }
            };

            MediaFormat fmt;
            if (fr.aac) {
                fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, fr.sampleRate, fr.channels);
                int asc = ((fr.aacProfile + 1) << 11) | (fr.sfIndex << 7) | (fr.channels << 3);
                fmt.setByteBuffer("csd-0", ByteBuffer.wrap(new byte[] {(byte) (asc >> 8), (byte) asc}));
            } else {
                fmt = MediaFormat.createAudioFormat(fr.layer == 2 ? "audio/mpeg-L2" : MediaFormat.MIMETYPE_AUDIO_MPEG,
                        fr.sampleRate, fr.channels);
            }
            try {
                codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
                codec.configure(fmt, null, null, 0);
                codec.start();
            } catch (Exception e) {
                throw new Unsupported("No decoder: " + e.getMessage());
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            Pcm pcm = null;
            long pts = 0;
            byte[] pending = null;
            while (!stopped) {
                if (pending == null) {
                    pending = fr.next();
                    if (fr.kbps > 0) measuredKbps = fr.kbps;
                }
                int in = codec.dequeueInputBuffer(10000);
                if (in >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(in);
                    ib.clear();
                    int n = Math.min(pending.length, ib.capacity());
                    ib.put(pending, 0, n);
                    codec.queueInputBuffer(in, 0, n, pts, 0);
                    pts += fr.frameUs;
                    pending = null;
                }
                while (!stopped) {
                    int out = codec.dequeueOutputBuffer(info, 0);
                    if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (t != null) release(t);
                        pcm = new Pcm(codec.getOutputFormat());
                        t = makeTrack(pcm);
                        continue;
                    }
                    if (out < 0) break;
                    ByteBuffer ob = codec.getOutputBuffer(out);
                    if (t == null) {
                        pcm = new Pcm(codec.getOutputFormat());
                        t = makeTrack(pcm);
                    }
                    if (info.size > 0 && ob != null) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        pcm.analyse(ob);
                        t.write(ob, info.size, AudioTrack.WRITE_BLOCKING);
                        pcm.written += info.size / pcm.frameBytes;
                        if (!started) {
                            started = true;
                            cb.onPlaying(this);
                        } else if (starving) {
                            starving = false;
                            cb.onPlaying(this);
                        }
                    }
                    codec.releaseOutputBuffer(out, false);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            fail(e);
        } finally {
            Spectrum.detach(this);
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                try {
                    codec.release();
                } catch (Exception ignored) {
                }
            }
            if (t != null) release(t);
            track = null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void release(AudioTrack t) {
        try {
            t.pause();
            t.flush();
        } catch (Exception ignored) {
        }
        try {
            t.release();
        } catch (Exception ignored) {
        }
    }

    /** Decoded PCM layout, plus the visualiser analysis. */
    private final class Pcm {
        final int rate;
        final int channels;
        final boolean isFloat;
        final int frameBytes;
        final Spectrum.Analyzer analyzer;
        long written;

        Pcm(MediaFormat f) {
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
            frameBytes = channels * (isFloat ? 4 : 2);
            analyzer = new Spectrum.Analyzer(rate);
        }

        void analyse(ByteBuffer src) {
            if (!BarsView.anyoneWatching()) return;
            ByteBuffer b = src.duplicate().order(ByteOrder.nativeOrder());
            int frames = b.remaining() / frameBytes;
            int base = b.position();
            for (int f = 0; f < frames; f++) {
                float sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    sum += isFloat ? b.getFloat(base + f * frameBytes + ch * 4)
                            : b.getShort(base + f * frameBytes + ch * 2) / 32768f;
                }
                float[] levels = analyzer.add(sum / channels);
                if (levels != null) Spectrum.push(written + f, levels);
            }
        }
    }

    private AudioTrack makeTrack(Pcm p) throws IOException {
        int mask;
        if (p.channels == 1) mask = AudioFormat.CHANNEL_OUT_MONO;
        else if (p.channels == 2) mask = AudioFormat.CHANNEL_OUT_STEREO;
        else if (p.channels == 6) mask = AudioFormat.CHANNEL_OUT_5POINT1;
        else throw new Unsupported("channels " + p.channels);
        int enc = p.isFloat ? AudioFormat.ENCODING_PCM_FLOAT : AudioFormat.ENCODING_PCM_16BIT;
        int size = Math.max(AudioTrack.getMinBufferSize(p.rate, mask, enc) * 4, p.frameBytes * p.rate / 2);
        AudioTrack.Builder b = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(p.rate).setChannelMask(mask).setEncoding(enc).build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM);
        if (sessionId > 0) b.setSessionId(sessionId);
        AudioTrack t = b.build();
        t.setVolume(volume);
        t.play();
        track = t;
        Spectrum.attach(this);
        return t;
    }

    /** Finds and splits MP3 (MPEG-1/2 layer II/III) and ADTS AAC frames. Pure Java, so it can be unit-tested. */
    static class Frames {
        boolean aac;
        int aacProfile;
        int channels;
        int sampleRate;
        int sfIndex;
        int layer;
        long frameUs;
        /** Audio bitrate measured from the frames (kbps). */
        int kbps;
        /** Called when every buffered byte has been used and more must be waited for. */
        Dry onDry;

        interface Dry {
            void run() throws InterruptedException;
        }

        final byte[] buf = new byte[65536];
        int len;
        int pos;
        final Ring ring;
        private long frameBytes;
        private long frameCount;
        private int hLen, hHeader, hRate, hCh, hSfi, hProfile, hLayer, hSamples;

        private static final int[] AAC_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
        private static final int[][] MP3_RATES = {{11025, 12000, 8000}, {0, 0, 0}, {22050, 24000, 16000}, {44100, 48000, 32000}};
        private static final int[] BR_V1_L2 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384};
        private static final int[] BR_V1_L3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
        private static final int[] BR_V2_L23 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};

        Frames(Ring ring) {
            this.ring = ring;
        }

        private void ensure(int need) throws InterruptedException, IOException {
            if (len - pos >= need) return;
            if (pos > 0) {
                System.arraycopy(buf, pos, buf, 0, len - pos);
                len -= pos;
                pos = 0;
            }
            while (len < need) {
                if (onDry != null && ring.available() == 0) onDry.run();
                int n = ring.read(buf, len, buf.length - len);
                if (n < 0) throw new IOException("Stream ended");
                len += n;
            }
        }

        /** 0 = any, 1 = MP3 only, 2 = AAC only. */
        private boolean header(int off, int want) {
            int p = pos + off;
            int b0 = buf[p] & 255, b1 = buf[p + 1] & 255, b2 = buf[p + 2] & 255, b3 = buf[p + 3] & 255;
            if (b0 != 255 || (b1 & 0xE0) != 0xE0) return false;
            int layerBits = (b1 >> 1) & 3;
            if (layerBits == 0) { // ADTS (AAC)
                if (want == 1 || (b1 & 0xF0) != 0xF0) return false;
                int profile = (b2 >> 6) & 3;
                int sfi = (b2 >> 2) & 15;
                int ch = ((b2 & 1) << 2) | ((b3 >> 6) & 3);
                int flen = ((b3 & 3) << 11) | ((buf[p + 4] & 255) << 3) | (((buf[p + 5] & 255) >> 5) & 7);
                int hdr = (b1 & 1) == 1 ? 7 : 9;
                if (sfi > 12 || ch == 0 || profile == 3 || flen <= hdr || flen > 8192) return false;
                hLen = flen;
                hHeader = hdr;
                hRate = AAC_RATES[sfi];
                hCh = ch;
                hSfi = sfi;
                hProfile = profile;
                hLayer = 0;
                hSamples = 1024;
                return true;
            }
            if (want == 2) return false;
            int version = (b1 >> 3) & 3;
            if (version == 1) return false;
            int lay = 4 - layerBits;
            int brIdx = (b2 >> 4) & 15;
            int srIdx = (b2 >> 2) & 3;
            int padding = (b2 >> 1) & 1;
            if (brIdx == 0 || brIdx == 15 || srIdx == 3 || lay == 1) return false;
            int rate = MP3_RATES[version][srIdx];
            int br = version == 3 ? (lay == 2 ? BR_V1_L2[brIdx] : BR_V1_L3[brIdx]) : BR_V2_L23[brIdx];
            int flen, samples;
            if (lay == 2 || version == 3) {
                flen = 144000 * br / rate + padding;
                samples = 1152;
            } else {
                flen = 72000 * br / rate + padding;
                samples = 576;
            }
            if (flen < 24 || flen > 8192) return false;
            hLen = flen;
            hHeader = 0;
            hRate = rate;
            hCh = ((b3 >> 6) & 3) == 3 ? 1 : 2;
            hSfi = srIdx;
            hProfile = 0;
            hLayer = lay;
            hSamples = samples;
            return true;
        }

        /** Locks onto the format: two consecutive matching headers. */
        void sync(int want) throws InterruptedException, IOException {
            for (int tries = 0; tries <= 98304; tries++) {
                ensure(9);
                if (header(0, want)) {
                    boolean isAac = hLayer == 0;
                    int l = hLen, r = hRate, c = hCh, s = hSfi, pr = hProfile, ly = hLayer, sm = hSamples;
                    ensure(l + 9);
                    if (header(l, isAac ? 2 : 1) && hRate == r && (hLayer == 0) == isAac) {
                        aac = isAac;
                        sampleRate = r;
                        channels = c;
                        sfIndex = s;
                        aacProfile = pr;
                        layer = ly;
                        frameUs = sm * 1_000_000L / r;
                        return;
                    }
                }
                pos++;
            }
            throw new Unsupported("Unknown stream format");
        }

        /** Next frame's payload (without the ADTS header). */
        byte[] next() throws InterruptedException, IOException {
            for (int skipped = 0; skipped <= 262144; skipped++) {
                ensure(9);
                if (header(0, aac ? 2 : 1) && hRate == sampleRate) {
                    ensure(hLen);
                    int n = hLen - hHeader;
                    byte[] out = new byte[n];
                    System.arraycopy(buf, pos + hHeader, out, 0, n);
                    pos += hLen;
                    frameBytes += hLen;
                    frameCount++;
                    if (frameCount % 64 == 0 && frameUs > 0) {
                        kbps = (int) Math.round(frameBytes * 8.0 / (frameCount * frameUs / 1e6) / 1000.0);
                    }
                    return out;
                }
                pos++;
            }
            throw new IOException("Lost sync");
        }
    }

    /** A blocking byte ring buffer between the network and decoder threads. */
    static class Ring {
        private final byte[] b;
        private boolean closed;
        private int count;
        private IOException err;
        private int r;
        private int w;

        Ring(int size) {
            b = new byte[size];
        }

        synchronized void write(byte[] src, int off, int n) throws InterruptedException {
            while (n > 0) {
                while (count == b.length && !closed) wait();
                if (closed) return;
                int k = Math.min(n, Math.min(b.length - count, b.length - w));
                System.arraycopy(src, off, b, w, k);
                w = (w + k) % b.length;
                count += k;
                off += k;
                n -= k;
                notifyAll();
            }
        }

        synchronized int read(byte[] dst, int off, int n) throws InterruptedException, IOException {
            while (count == 0) {
                if (closed) {
                    if (err != null) throw err;
                    return -1;
                }
                wait();
            }
            int k = Math.min(n, Math.min(count, b.length - r));
            System.arraycopy(b, r, dst, off, k);
            r = (r + k) % b.length;
            count -= k;
            notifyAll();
            return k;
        }

        synchronized int available() {
            return count;
        }

        /** Waits until at least {@code n} bytes are buffered, the ring closes, or the timeout passes. */
        synchronized void await(int n, long timeoutMs) throws InterruptedException {
            long end = System.currentTimeMillis() + timeoutMs;
            while (count < n && !closed) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return;
                wait(left);
            }
        }

        synchronized void close(IOException e) {
            if (closed) return;
            closed = true;
            err = e;
            notifyAll();
        }
    }
}
