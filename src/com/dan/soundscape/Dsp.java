package com.dan.soundscape;

/**
 * Bausteine der Klangerzeugung, alle in float und ohne Speicher je Abtastwert: Rauschen (weiß, rosa, braun),
 * Biquad-Filter nach dem Audio-EQ-Kochbuch von R. Bristow-Johnson, einpolige Glättung, Hall nach Schroeder und
 * Moorer, ein weicher Begrenzer. Nur Java 21.
 */
public final class Dsp {
    private Dsp() { }

    static final double TAU = 2 * Math.PI;

    /** Pegel in dB SPL zu Amplitude (Effektivwert) bei {@link Soundscape#REF_DB} = 1. */
    public static double amp(double db) { return Math.pow(10, (db - Soundscape.REF_DB) / 20); }

    /** Amplitude (Effektivwert) zu dB SPL. */
    public static double db(double rms) { return Soundscape.REF_DB + 20 * Math.log10(Math.max(1e-9, rms)); }

    // ------------------------------------------------------------------ Rauschen

    /** Zufall: xorshift64*, schnell und mit eigenem Startwert (gleicher Startwert, gleiches Rauschen). */
    public static final class Rng {
        private long s;
        public Rng(long seed) { s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L; if (s == 0) s = 1; next(); }
        public long next() {
            s ^= s >>> 12; s ^= s << 25; s ^= s >>> 27;
            return s * 0x2545F4914F6CDD1DL;
        }
        /** Gleichverteilt in [0, 1). */
        public double uniform() { return (next() >>> 11) * 0x1.0p-53; }
        /** Gleichverteilt in [−1, 1). */
        public float white() { return (float) (2 * uniform() - 1); }
        /** Normalverteilt (Box-Muller). */
        public double gauss() {
            double u = Math.max(1e-12, uniform()), v = uniform();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(TAU * v);
        }
        /** Wartezeit eines Poisson-Vorgangs mit Rate r (je s). */
        public double exp(double r) { return r <= 0 ? Double.POSITIVE_INFINITY : -Math.log(Math.max(1e-12, uniform())) / r; }
    }

    /** Rosa Rauschen (−3 dB je Oktave) nach P. Kellet, Effektivwert ≈ 1. */
    public static final class Pink {
        private final Rng r;
        private float b0, b1, b2, b3, b4, b5, b6;
        public Pink(long seed) { r = new Rng(seed); }
        public float next() {
            float w = r.white();
            b0 = 0.99886f * b0 + w * 0.0555179f;
            b1 = 0.99332f * b1 + w * 0.0750759f;
            b2 = 0.96900f * b2 + w * 0.1538520f;
            b3 = 0.86650f * b3 + w * 0.3104856f;
            b4 = 0.55000f * b4 + w * 0.5329522f;
            b5 = -0.7616f * b5 - w * 0.0168980f;
            float p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f;
            b6 = w * 0.115926f;
            return p * 0.19f;
        }
    }

    /** Braunes Rauschen (−6 dB je Oktave), leckender Integrator, Effektivwert ≈ 1. */
    public static final class Brown {
        private final Rng r;
        private float y;
        public Brown(long seed) { r = new Rng(seed); }
        public float next() {
            y = 0.996f * y + 0.02f * r.white();
            return y * 6.5f;
        }
    }

    // ------------------------------------------------------------------ Filter

    /** Biquad (direkte Form I, transponiert). Koeffizienten nach dem Audio-EQ-Kochbuch. */
    public static final class Biquad {
        float b0 = 1, b1, b2, a1, a2, z1, z2;

        public float process(float x) {
            float y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        public void reset() { z1 = z2 = 0; }

        private void set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
            b0 = (float) (nb0 / na0); b1 = (float) (nb1 / na0); b2 = (float) (nb2 / na0);
            a1 = (float) (na1 / na0); a2 = (float) (na2 / na0);
        }

        static double w0(double f, double sr) { return TAU * Math.max(1, Math.min(f, sr * 0.49)) / sr; }

        public Biquad lowpass(double f, double q, double sr) {
            double w = w0(f, sr), c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
            return this;
        }

        public Biquad highpass(double f, double q, double sr) {
            double w = w0(f, sr), c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
            return this;
        }

        /** Bandpass mit 0 dB Spitzenverstärkung. */
        public Biquad bandpass(double f, double q, double sr) {
            double w = w0(f, sr), c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set(al, 0, -al, 1 + al, -2 * c, 1 - al);
            return this;
        }

        public Biquad peaking(double f, double q, double gainDb, double sr) {
            double w = w0(f, sr), c = Math.cos(w), al = Math.sin(w) / (2 * q), A = Math.pow(10, gainDb / 40);
            set(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A);
            return this;
        }

        public Biquad highshelf(double f, double gainDb, double sr) {
            double w = w0(f, sr), c = Math.cos(w), A = Math.pow(10, gainDb / 40), al = Math.sin(w) / 2 * Math.sqrt(2);
            double sq = 2 * Math.sqrt(A) * al;
            set(A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq),
                    (A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq);
            return this;
        }

        /** Betrag des Frequenzgangs bei f (für Prüfungen). */
        public double response(double f, double sr) {
            double w = TAU * f / sr;
            double nr = b0 + b1 * Math.cos(w) + b2 * Math.cos(2 * w), ni = -b1 * Math.sin(w) - b2 * Math.sin(2 * w);
            double dr = 1 + a1 * Math.cos(w) + a2 * Math.cos(2 * w), di = -a1 * Math.sin(w) - a2 * Math.sin(2 * w);
            return Math.hypot(nr, ni) / Math.hypot(dr, di);
        }
    }

    /** Einpoliger Tiefpass (Luftdämpfung, Glättung von Pegeln). */
    public static final class OnePole {
        float a = 1, y;
        public OnePole cutoff(double f, double sr) { a = (float) (1 - Math.exp(-TAU * Math.max(1, f) / sr)); return this; }
        public OnePole time(double seconds, double sr) { a = (float) (1 - Math.exp(-1 / Math.max(1e-6, seconds * sr))); return this; }
        public float process(float x) { y += a * (x - y); return y; }
    }

    // ------------------------------------------------------------------ Hall

    /**
     * Hall nach Schroeder und Moorer: vier parallele Kammfilter mit Tiefpass in der Rückkopplung (Luft und Wände
     * schlucken die Höhen), dann zwei Allpässe, je Kanal leicht versetzte Längen. Nachhallzeit T60 in s.
     */
    public static final class Reverb {
        private static final double[] COMB = {0.0297, 0.0371, 0.0411, 0.0437}, ALL = {0.005, 0.0017};
        private final float[][] cb = new float[8][], ab = new float[4][];
        private final int[] ci = new int[8], ai = new int[4];
        private final float[] cf = new float[8], lp = new float[8];
        private float damp = 0.3f;
        private double rt = -1;
        private final double sr;

        public Reverb(double sampleRate, double scale) {
            sr = sampleRate;
            for (int k = 0; k < 8; k++) cb[k] = new float[(int) Math.max(8, COMB[k % 4] * scale * (k < 4 ? 1 : 1.037) * sr)];
            for (int k = 0; k < 4; k++) ab[k] = new float[(int) Math.max(8, ALL[k % 2] * (k < 2 ? 1 : 1.07) * sr)];
        }

        /** Nachhallzeit (s) und Dämpfung der Höhen 0..1. */
        public void set(double t60, double highDamp) {
            if (Math.abs(t60 - rt) < 1e-3 && Math.abs(highDamp - damp) < 1e-3) return;
            rt = t60;
            damp = (float) highDamp;
            // der Tiefpass in der Rückkopplung kürzt den Nachhall um gut ein Viertel; das gleicht der Faktor aus
            for (int k = 0; k < 8; k++) cf[k] = (float) Math.pow(10, -3.0 * cb[k].length / sr / Math.max(0.05, t60 * (1 + 0.95 * highDamp)));
        }

        /** Mono hinein, Stereo heraus (addiert in l, r). */
        public void process(float x, float[] l, float[] r, int i, float mix) {
            float sl = 0, sr2 = 0;
            for (int k = 0; k < 8; k++) {
                float[] b = cb[k];
                float y = b[ci[k]];
                lp[k] = y + damp * (lp[k] - y);
                b[ci[k]] = x + cf[k] * lp[k];
                if (++ci[k] >= b.length) ci[k] = 0;
                if (k < 4) sl += y; else sr2 += y;
            }
            sl = allpass(0, allpass(1, sl * 0.25f));
            sr2 = allpass(2, allpass(3, sr2 * 0.25f));
            l[i] += sl * mix;
            r[i] += sr2 * mix;
        }

        private float allpass(int k, float x) {
            float[] b = ab[k];
            float d = b[ai[k]], y = -0.5f * x + d;
            b[ai[k]] = x + 0.5f * y;
            if (++ai[k] >= b.length) ai[k] = 0;
            return y;
        }
    }

    // ------------------------------------------------------------------ Begrenzer

    /**
     * Spitzenbegrenzer mit 5 ms Vorschau: Er sieht die Spitze kommen und senkt die Verstärkung vorher weich ab
     * (kein Knacken), erholt sich in 0,4 s; dahinter eine weiche Sättigung bis ±1. Die Ausgabe ist um die Vorschau
     * verzögert.
     */
    public static final class Limiter {
        private float gain = 1;
        private final float release, attack;
        public final float ceiling;
        private float minGain = 1;
        private final int la;
        private final float[] dl, dr;
        private final int[] dqI;
        private final float[] dqV;
        private int head, dqH, dqT;
        private long t;

        public Limiter(double sampleRate, double ceilingDb) {
            release = (float) (1 - Math.exp(-1 / (0.4 * sampleRate)));
            ceiling = (float) Math.pow(10, ceilingDb / 20);
            la = Math.max(8, (int) (0.005 * sampleRate));
            attack = (float) (1 - Math.exp(-3.0 / la));
            dl = new float[la + 1];
            dr = new float[la + 1];
            dqI = new int[la + 2];
            dqV = new float[la + 2];
        }

        public void process(float[] l, float[] r, int n) {
            int m = la + 1;
            for (int i = 0; i < n; i++) {
                float p = Math.max(Math.abs(l[i]), Math.abs(r[i]));
                float req = p > ceiling ? ceiling / p : 1;
                // gleitendes Minimum der benötigten Verstärkung über die Vorschau (monotone Schlange)
                while (dqT != dqH && dqV[(dqT - 1 + dqV.length) % dqV.length] >= req) dqT = (dqT - 1 + dqV.length) % dqV.length;
                dqV[dqT] = req;
                dqI[dqT] = (int) (t % Integer.MAX_VALUE);
                dqT = (dqT + 1) % dqV.length;
                while (dqT != dqH && (int) (t % Integer.MAX_VALUE) - dqI[dqH] > la) dqH = (dqH + 1) % dqV.length;
                float target = dqV[dqH];
                if (target < gain) gain += (target - gain) * attack;
                else gain += (target - gain) * release;
                if (gain < minGain) minGain = gain;
                dl[head] = l[i];
                dr[head] = r[i];
                int out = (head + 1) % m;
                l[i] = soft(dl[out] * gain);
                r[i] = soft(dr[out] * gain);
                head = out;
                t++;
            }
        }

        static float soft(float x) {
            float a = Math.abs(x);
            if (a < 0.8f) return x;
            float y = 0.8f + 0.2f * (float) Math.tanh((a - 0.8f) / 0.2f);
            return Math.copySign(y, x);
        }

        /** Stärkste Absenkung in dB seit dem letzten Abruf (0 = keine). */
        public double takeReductionDb() { double d = 20 * Math.log10(minGain); minGain = 1; return d; }
    }
}
