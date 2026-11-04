package za.saradio.app;

/**
 * Live sound levels for the visualiser. The decoder analyses the actual audio (no microphone or
 * extra permission needed), and the levels are released in step with what the speaker is
 * playing rather than what the decoder has just produced, which can be half a second ahead.
 */
public final class Spectrum {
    private Spectrum() {}

    public static final int BANDS = 28;
    private static final int FFT = 1024;
    private static final int SLOTS = 96;

    /** Reports how many audio frames the speaker has played so far. */
    interface Clock {
        long playedFrames();
    }

    private static final long[] slotFrame = new long[SLOTS];
    private static final float[][] slotLevels = new float[SLOTS][BANDS];
    private static int head;
    private static int count;
    private static volatile Clock clock;

    static synchronized void attach(Clock c) {
        clock = c;
        head = 0;
        count = 0;
    }

    static synchronized void detach(Clock c) {
        if (clock == c) {
            clock = null;
            count = 0;
        }
    }

    static synchronized void push(long frame, float[] levels) {
        slotFrame[head] = frame;
        System.arraycopy(levels, 0, slotLevels[head], 0, BANDS);
        head = (head + 1) % SLOTS;
        if (count < SLOTS) count++;
    }

    /** Copies the levels for what's audible now into {@code out}; false if there's no live analysis. */
    static boolean read(float[] out) {
        Clock c = clock;
        if (c == null) return false;
        long played;
        try {
            played = c.playedFrames();
        } catch (Throwable t) {
            return false;
        }
        synchronized (Spectrum.class) {
            if (count == 0) return false;
            int best = -1;
            for (int i = 0; i < count; i++) {
                int idx = (head - 1 - i + SLOTS) % SLOTS;
                if (slotFrame[idx] <= played) {
                    best = idx;
                    break;
                }
            }
            if (best < 0) {
                java.util.Arrays.fill(out, 0f);
                return true;
            }
            System.arraycopy(slotLevels[best], 0, out, 0, Math.min(out.length, BANDS));
            return true;
        }
    }

    /** Analyses mono PCM blocks and turns them into band levels between 0 and 1. */
    static final class Analyzer {
        private final float[] window = new float[FFT];
        private final float[] re = new float[FFT];
        private final float[] im = new float[FFT];
        private final float[] hist = new float[FFT];
        private final float[] levels = new float[BANDS];
        private final int[] bandLo = new int[BANDS];
        private final int[] bandHi = new int[BANDS];
        private final float[] tilt = new float[BANDS];
        private final int hop;
        private int filled;
        private int sinceLast;
        private int pos;

        Analyzer(int sampleRate) {
            hop = Math.max(256, sampleRate / 30);
            for (int i = 0; i < FFT; i++) window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT - 1)));
            double lo = Math.log(45), hi = Math.log(Math.min(16000, sampleRate / 2.2));
            for (int b = 0; b < BANDS; b++) {
                double f0 = Math.exp(lo + (hi - lo) * b / BANDS);
                double f1 = Math.exp(lo + (hi - lo) * (b + 1) / BANDS);
                bandLo[b] = Math.max(1, (int) Math.floor(f0 * FFT / sampleRate));
                bandHi[b] = Math.max(bandLo[b], (int) Math.ceil(f1 * FFT / sampleRate));
                double mid = Math.sqrt(f0 * f1);
                tilt[b] = (float) (3.0 * Math.log(mid / 300.0) / Math.log(2)); // +3 dB/octave
            }
        }

        /** Feeds one mono sample (-1..1). Returns the levels when a new analysis is ready. */
        float[] add(float sample) {
            hist[pos] = sample;
            pos = (pos + 1) & (FFT - 1);
            if (filled < FFT) filled++;
            if (++sinceLast < hop || filled < FFT) return null;
            sinceLast = 0;
            for (int i = 0; i < FFT; i++) {
                re[i] = hist[(pos + i) & (FFT - 1)] * window[i]; // oldest sample first
                im[i] = 0;
            }
            fft(re, im);
            for (int b = 0; b < BANDS; b++) {
                float peak = 0;
                for (int k = bandLo[b]; k <= bandHi[b] && k < FFT / 2; k++) {
                    float m = re[k] * re[k] + im[k] * im[k];
                    if (m > peak) peak = m;
                }
                double amp = Math.sqrt(peak) / (FFT / 4.0);
                double db = 20 * Math.log10(amp + 1e-9) + tilt[b];
                float v = (float) ((db + 58) / 52);
                levels[b] = Math.max(0, Math.min(1, v));
            }
            return levels;
        }

        private static void fft(float[] re, float[] im) {
            int n = re.length;
            for (int i = 1, j = 0; i < n; i++) {
                int bit = n >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) {
                    float t = re[i]; re[i] = re[j]; re[j] = t;
                    t = im[i]; im[i] = im[j]; im[j] = t;
                }
            }
            for (int len = 2; len <= n; len <<= 1) {
                double ang = -2 * Math.PI / len;
                float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
                for (int i = 0; i < n; i += len) {
                    float cr = 1, ci = 0;
                    for (int k = 0; k < len / 2; k++) {
                        int a = i + k, b = i + k + len / 2;
                        float xr = re[b] * cr - im[b] * ci;
                        float xi = re[b] * ci + im[b] * cr;
                        re[b] = re[a] - xr;
                        im[b] = im[a] - xi;
                        re[a] += xr;
                        im[a] += xi;
                        float nr = cr * wr - ci * wi;
                        ci = cr * wi + ci * wr;
                        cr = nr;
                    }
                }
            }
        }
    }
}
