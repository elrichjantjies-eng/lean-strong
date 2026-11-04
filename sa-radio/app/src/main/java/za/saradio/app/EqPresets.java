package za.saradio.app;

/**
 * Sound presets. Each is a curve over ten reference frequencies; phones expose different
 * equalizer bands (usually five), so a curve is sampled at whatever bands the phone has.
 */
public final class EqPresets {
    private EqPresets() {}

    static final float[] REF_HZ = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    /** Shown when the phone hasn't told us its bands yet. Matches most Android equalizers. */
    static final int[] DEFAULT_BANDS_HZ = {60, 230, 910, 3600, 14000};

    public static final class Preset {
        public final String id;
        public final String name;
        public final String blurb;
        final float[] db;

        Preset(String id, String name, String blurb, float... db) {
            this.id = id;
            this.name = name;
            this.blurb = blurb;
            this.db = db;
        }
    }

    public static final Preset[] ALL = {
        new Preset("normal", "Normal", "As the station sends it", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        new Preset("talk", "Talk & News", "Clearer voices on talk shows", -8, -6, -3, -1, 0, 2, 4, 4, 2, 0),
        new Preset("bass", "Bass boost", "More low-end punch", 7, 6, 5, 3, 1, 0, 0, 0, 0, 0),
        new Preset("amapiano", "Amapiano", "Deep log drums, crisp shakers", 7, 7, 5, 2, -1, -1, 0, 2, 3, 4),
        new Preset("pop", "Pop & Hits", "Bright vocals, tight bass", -1, 1, 3, 3, 1, -1, 0, 1, 2, 2),
        new Preset("rock", "Rock", "Punchy drums and guitars", 5, 4, 3, 1, -1, -1, 1, 3, 4, 4),
        new Preset("jazz", "Jazz & Soul", "Warm and smooth", 3, 3, 2, 1, -1, -1, 0, 1, 2, 3),
        new Preset("gospel", "Gospel & Choir", "Full, rich voices", 1, 2, 3, 2, 2, 2, 2, 1, 0, 0),
        new Preset("classical", "Classical", "Natural and open", 2, 2, 1, 0, 0, 0, -1, 0, 2, 3),
        new Preset("phone", "Phone speaker", "Clearer on the built-in speaker", -10, -8, -4, 0, 2, 3, 4, 3, 1, 0),
        new Preset("car", "In the car", "Cuts through road noise", 4, 4, 3, 1, 0, 0, 1, 2, 3, 2),
        new Preset("night", "Late night", "Softer, easy on the ears", -4, -3, -1, 0, 0, 0, 0, -1, -2, -4),
    };

    public static Preset find(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return null;
    }

    /** Value of a ten-point curve at any frequency, interpolated on a log scale. */
    static float sample(float[] curve, float hz) {
        if (hz <= REF_HZ[0]) return curve[0];
        int last = REF_HZ.length - 1;
        if (hz >= REF_HZ[last]) return curve[last];
        double lx = Math.log(hz);
        for (int i = 0; i < last; i++) {
            if (hz <= REF_HZ[i + 1]) {
                double a = Math.log(REF_HZ[i]);
                double b = Math.log(REF_HZ[i + 1]);
                double t = (lx - a) / (b - a);
                return (float) (curve[i] + (curve[i + 1] - curve[i]) * t);
            }
        }
        return curve[last];
    }

    /** A preset's gain in dB for each of the phone's bands. */
    static float[] forBands(Preset p, int[] bandsHz) {
        float[] out = new float[bandsHz.length];
        for (int i = 0; i < bandsHz.length; i++) out[i] = Math.round(sample(p.db, bandsHz[i]) * 2) / 2f;
        return out;
    }

    static String hzLabel(int hz) {
        if (hz >= 1000) {
            float k = hz / 1000f;
            return (k >= 10 || k == Math.round(k) ? String.valueOf(Math.round(k)) : String.format(java.util.Locale.ROOT, "%.1f", k)) + "k";
        }
        return String.valueOf(hz);
    }
}
