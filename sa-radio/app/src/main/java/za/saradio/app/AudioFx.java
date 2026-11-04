package za.saradio.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

/**
 * The real sound effects: equalizer, bass boost, surround and loudness. They are attached to one
 * audio session that every player in the app uses, so they survive reconnects and stream changes.
 */
public final class AudioFx {
    private AudioFx() {}

    private static int session;
    private static Equalizer eq;
    private static BassBoost bass;
    private static Virtualizer virt;
    private static LoudnessEnhancer loud;
    private static int[] bandsHz;
    private static int minMb = -1500;
    private static int maxMb = 1500;

    /** The shared audio session id. */
    public static synchronized int session(Context c) {
        if (session <= 0) {
            try {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                session = am.generateAudioSessionId();
            } catch (Throwable t) {
                session = 0;
            }
        }
        return session;
    }

    /** Creates the effects if they don't exist yet. Each one is optional; phones differ. */
    static synchronized void ensure(Context c) {
        if (eq != null) return;
        int s = session(c);
        if (s <= 0) return;
        try {
            eq = new Equalizer(0, s);
            short n = eq.getNumberOfBands();
            int[] hz = new int[n];
            for (short i = 0; i < n; i++) hz[i] = eq.getCenterFreq(i) / 1000;
            short[] r = eq.getBandLevelRange();
            if (!sane(hz, r)) {
                // Some phones report 0 Hz bands or a zero range; such an equalizer can't do anything useful.
                eq.release();
                eq = null;
            } else {
                bandsHz = hz;
                minMb = r[0];
                maxMb = r[1];
            }
        } catch (Throwable t) {
            eq = null;
        }
        if (bass == null) {
            try {
                bass = new BassBoost(0, s);
                if (!bass.getStrengthSupported()) {
                    bass.release();
                    bass = null;
                }
            } catch (Throwable t) {
                bass = null;
            }
        }
        if (virt == null) {
            try {
                virt = new Virtualizer(0, s);
                if (!virt.getStrengthSupported()) {
                    virt.release();
                    virt = null;
                }
            } catch (Throwable t) {
                virt = null;
            }
        }
        if (loud == null) {
            try {
                loud = new LoudnessEnhancer(s);
            } catch (Throwable t) {
                loud = null;
            }
        }
    }

    static boolean sane(int[] hz, short[] range) {
        if (hz == null || hz.length < 2 || range == null || range.length < 2) return false;
        if (range[1] < 300 || range[0] > -300) return false; // less than ±3 dB
        for (int i = 0; i < hz.length; i++) {
            if (hz[i] < 10 || hz[i] > 24000) return false;
            if (i > 0 && hz[i] <= hz[i - 1]) return false;
        }
        return true;
    }

    /** Applies everything from the saved settings. */
    public static synchronized void apply(Context c) {
        ensure(c);
        boolean on = Prefs.eqEnabled(c);
        if (eq != null) {
            float[] db = curve(c);
            try {
                for (int i = 0; i < db.length && i < bandsHz.length; i++) eq.setBandLevel((short) i, toMb(db[i]));
                eq.setEnabled(on);
            } catch (Throwable ignored) {
            }
        }
        int b = Prefs.effect(c, "bass");
        if (bass != null) {
            try {
                bass.setStrength((short) (b * 10));
                bass.setEnabled(on && b > 0);
            } catch (Throwable ignored) {
            }
        }
        int v = Prefs.effect(c, "surround");
        if (virt != null) {
            try {
                virt.setStrength((short) (v * 10));
                virt.setEnabled(on && v > 0);
            } catch (Throwable ignored) {
            }
        }
        int l = Prefs.effect(c, "loudness");
        if (loud != null) {
            try {
                loud.setTargetGain(l * 8); // up to +8 dB
                loud.setEnabled(on && l > 0);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Fast path while a finger drags a band. */
    static synchronized void setBand(int band, float db) {
        if (eq == null || bandsHz == null || band >= bandsHz.length) return;
        try {
            eq.setBandLevel((short) band, toMb(db));
        } catch (Throwable ignored) {
        }
    }

    private static short toMb(float db) {
        return (short) Math.max(minMb, Math.min(maxMb, Math.round(db * 100)));
    }

    /** The curve currently selected, one dB value per band. */
    static float[] curve(Context c) {
        int[] b = bands();
        String id = Prefs.eqPreset(c);
        if ("custom".equals(id)) {
            float[] cu = Prefs.eqCustom(c, b.length);
            if (cu != null) return cu;
        }
        EqPresets.Preset p = EqPresets.find(id);
        return EqPresets.forBands(p == null ? EqPresets.ALL[0] : p, b);
    }

    static synchronized int[] bands() {
        return bandsHz != null ? bandsHz : EqPresets.DEFAULT_BANDS_HZ;
    }

    /** Largest boost/cut the UI offers, in dB. */
    static synchronized float rangeDb() {
        return Math.max(6, Math.min(12, Math.min(-minMb, maxMb) / 100f));
    }

    static synchronized boolean hasEqualizer() {
        return eq != null || preview;
    }

    private static boolean preview;

    /** Tests only: pretend the phone has a typical five-band equalizer (the test runtime has none). */
    static synchronized void previewBandsForTest(int[] hz, int min, int max) {
        preview = true;
        bandsHz = hz;
        minMb = min;
        maxMb = max;
    }

    static synchronized boolean hasBass() {
        return bass != null;
    }

    static synchronized boolean hasSurround() {
        return virt != null;
    }

    static synchronized boolean hasLoudness() {
        return loud != null;
    }

    /** Frees the effects (the session id is kept, so they can be recreated later). */
    static synchronized void release() {
        try {
            if (eq != null) eq.release();
            if (bass != null) bass.release();
            if (virt != null) virt.release();
            if (loud != null) loud.release();
        } catch (Throwable ignored) {
        }
        eq = null;
        bass = null;
        virt = null;
        loud = null;
    }
}
