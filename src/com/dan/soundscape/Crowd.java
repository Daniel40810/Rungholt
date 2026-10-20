package com.dan.soundscape;

/**
 * Stimmengewirr einer Menge (seit Haithabu Phase 14): ein Markt, ein Fest, Leute am Hafen. Acht gedachte Sprecher
 * reden in Silben (Grundton 95 bis 230 Hz, Vokale aus den Formanten von a, e, i, o, u), mit Pausen zwischen den
 * Sätzen; dazu ein sprachförmiges Rauschen, das die übrigen vertritt. Einzelne Wörter versteht man nie: Die Silben
 * sind ohne Sinn, und schon in 20 m verschwimmt alles. Pegel: ein Sprecher 60 dB in 1 m, die Menge
 * 60 + 10 · lg(0,35 · Leute) dB am Rand ({@link #radius}).
 */
public final class Crowd {
    public final String name;
    public volatile double x, y, z;
    /** Wie viele Leute (0 = still) und wie weit sie verteilt sind (m). */
    public volatile double people, radius = 15;

    public Crowd(String name, double x, double y, double z, double people) {
        this.name = name; this.x = x; this.y = y; this.z = z; this.people = people;
    }

    // ------------------------------------------------------------------ Zustand (Faden des Mischers)
    private static final double[][] VOWELS = {{730, 1090}, {530, 1840}, {390, 2300}, {570, 840}, {440, 1020}, {660, 1700}};
    private static final int TALKERS = 8;
    private final double[] f0 = new double[TALKERS], base = new double[TALKERS], sylLen = new double[TALKERS], ph = new double[TALKERS], syl = new double[TALKERS], talk = new double[TALKERS], env = new double[TALKERS];
    private final boolean[] speaking = new boolean[TALKERS];
    private final Dsp.Biquad[] fa = new Dsp.Biquad[TALKERS], fb = new Dsp.Biquad[TALKERS];
    private final Dsp.Biquad murmur = new Dsp.Biquad(), murmur2 = new Dsp.Biquad(), air = new Dsp.Biquad();
    private float[] table;
    private Dsp.Rng rng;
    private Dsp.Pink pink;
    private double murmurAm;
    private int builtSr;

    private void build(int sr, long seed) {
        rng = new Dsp.Rng(seed * 6007 + name.hashCode());
        pink = new Dsp.Pink(seed * 13 + 5);
        table = Voice.Horn.harmonics(24, 1.1);
        for (int k = 0; k < TALKERS; k++) {
            base[k] = k < 4 ? 95 + 50 * rng.uniform() : 170 + 60 * rng.uniform();
            f0[k] = base[k];
            sylLen[k] = 0.2;
            fa[k] = new Dsp.Biquad();
            fb[k] = new Dsp.Biquad();
            syl[k] = rng.uniform() * 0.2;
            talk[k] = rng.uniform() * 2;
            speaking[k] = rng.uniform() < 0.5;
        }
        murmur.bandpass(500, 0.7, sr);
        murmur2.bandpass(1600, 1.0, sr);
        builtSr = sr;
    }

    /** Addiert das Gewirr in l, r (Verstärkung je Seite gl, gr, Pegel als Amplitude, Grenze der Luft in Hz); gibt die Energie. */
    double render(float[] l, float[] r, int n, int sr, long seed, float gl, float gr, double amp, double airHz) {
        if (builtSr != sr) build(sr, seed);
        air.lowpass(airHz, 0.6, sr);
        double dt = 1.0 / sr, e = 0;
        double many = Math.min(1, people / 40.0);
        // pro Stück: Silben weiter, Formanten der neuen Silbe
        for (int k = 0; k < TALKERS; k++) {
            syl[k] -= n * dt;
            talk[k] -= n * dt;
            if (talk[k] <= 0) { speaking[k] = !speaking[k]; talk[k] = speaking[k] ? 1 + 3 * rng.uniform() : 0.5 + 2.5 * rng.uniform(); }
            if (syl[k] <= 0) {
                double[] v = VOWELS[(int) (rng.uniform() * VOWELS.length)];
                fa[k].bandpass(v[0] * (0.92 + 0.16 * rng.uniform()) * (k < 4 ? 1 : 1.15), 4, sr);
                fb[k].bandpass(v[1] * (0.92 + 0.16 * rng.uniform()) * (k < 4 ? 1 : 1.15), 6, sr);
                syl[k] = sylLen[k] = 0.12 + 0.16 * rng.uniform();
                // Satzmelodie: jede Silbe auf eigener Höhe um den Grundton des Sprechers, innerhalb der Silbe fallend
                f0[k] = Math.max(80, Math.min(300, base[k] * (0.82 + 0.4 * rng.uniform())));
            }
        }
        murmurAm += -murmurAm * n * dt / 0.4 + 0.3 * Math.sqrt(2 * n * dt / 0.4) * rng.gauss();
        float gm = (float) (many * 1.6 * Math.exp(0.4 * murmurAm));
        float gt = (float) (1 / Math.sqrt(TALKERS) * 5.1);
        for (int i = 0; i < n; i++) {
            float sum = 0;
            for (int k = 0; k < TALKERS; k++) {
                double u = Math.min(1, Math.max(0, 1 - syl[k] / sylLen[k]));
                double want = speaking[k] ? Math.pow(Math.sin(Math.PI * u), 0.8) : 0;
                env[k] += (want - env[k]) * 0.01;
                if (env[k] < 1e-4) continue;
                ph[k] += f0[k] * (1.06 - 0.12 * u) * dt;
                ph[k] -= Math.floor(ph[k]);
                float src = table[(int) (ph[k] * table.length) & 2047] + 0.15f * rng.white();
                sum += (fa[k].process(src) * 1.6f + fb[k].process(src) * 0.9f) * (float) env[k];
            }
            float p = pink.next();
            float m = (murmur.process(p) * 1.5f + murmur2.process(p) * 0.7f) * gm;
            float y = air.process(sum * gt + m) * (float) amp;
            l[i] += y * gl;
            r[i] += y * gr;
            e += y * y * 0.5 * (gl * gl + gr * gr);
        }
        return e;
    }
}
