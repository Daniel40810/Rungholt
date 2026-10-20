package com.dan.soundscape;

/**
 * Ein Ereignis, das klingt und wieder vergeht: Brecher, Nebelhorn, Typhon, Möwenruf, Donner. Jede Stimme erzeugt ein
 * Monosignal mit dem Pegel in 1 m Abstand (Effektivwert 1 = {@link #level1m} dB); Abstand, Laufzeit, Luftdämpfung und
 * Richtung rechnet der Mischer. Nur Java 21.
 */
abstract class Voice {
    final String name;
    final double x, y, z;
    /** Pegel in 1 m (dB SPL) oder, wenn {@link #atEar}, schon am Ohr. */
    final double level1m;
    final boolean atEar;
    /** Abtastwert, an dem der Schall das Ohr erreicht. */
    long start;
    int age;
    /** Gegenwärtige Verstärkung je Kanal (geglättet), Luftdämpfung, Verzögerung des abgewandten Ohrs. */
    float gl = -1, gr = -1;
    final Dsp.Biquad air = new Dsp.Biquad();
    final float[] hist = new float[64];
    double distance;
    /** Wartezeit bis zum Ohr für Klänge mit Pegel am Ohr (Donner, dessen Laufzeit noch aussteht), s. */
    double delaySeconds;
    /** Seit Haithabu Phase 14: Versatz im Stück (s), zu dem der Klang an der Quelle beginnt (Takt der Werkstätten). */
    double lead;

    Voice(String name, double x, double y, double z, double level1m, boolean atEar) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.level1m = level1m;
        this.atEar = atEar;
    }

    /** n Werte ab {@link #age} nach out[off..off+n); false, wenn die Stimme verklungen ist. */
    abstract boolean render(float[] out, int off, int n, double sr);

    // ------------------------------------------------------------------ Brecher

    /** Ein Brecher schlägt an eine Wand: dumpfer Schlag, Krachen, dann das zurückfallende Wasser. */
    static final class Breaker extends Voice {
        private final Dsp.Rng r;
        private final Dsp.Biquad crash = new Dsp.Biquad(), hiss = new Dsp.Biquad(), body = new Dsp.Biquad();
        private final double strength, tail;
        private double ph;

        Breaker(double x, double y, double z, double strength, long seed, double sr) {
            super("Brecher", x, y, z, 104 + 16 * Math.max(0, Math.min(1, strength)), false);
            this.strength = strength;
            r = new Dsp.Rng(seed);
            crash.bandpass(700 + 500 * r.uniform(), 0.55, sr);
            hiss.highpass(2600, 0.7, sr);
            body.lowpass(420, 0.8, sr);
            tail = 1.4 + 1.2 * strength;
        }

        @Override boolean render(float[] out, int off, int n, double sr) {
            for (int i = off; i < n; i++) {
                double t = age++ / sr;
                double f = 34 + 26 * Math.exp(-t * 2.5);
                ph += Dsp.TAU * f / sr;
                float w = r.white();
                double thump = Math.sin(ph) * (1 - Math.exp(-t * 90)) * Math.exp(-t * 5.5) * 1.6;
                double cr = crash.process(w) * (1 - Math.exp(-t * 60)) * Math.exp(-t / (0.35 + 0.3 * strength)) * 3.2;
                double rumble = body.process(w) * Math.exp(-t / 0.7) * 1.8;
                double t2 = Math.max(0, t - 0.18);
                double hs = hiss.process(w) * (1 - Math.exp(-t2 * 8)) * Math.exp(-t2 / tail) * 1.1;
                out[i] = (float) (thump + cr + rumble + hs);
            }
            return age / sr < 0.2 + tail * 4;
        }
    }

    // ------------------------------------------------------------------ Hörner

    /**
     * Nebelhorn oder Typhon: ein Grundton mit Obertönen (Tabelle über eine Periode), Formant, Einschwingen und
     * Ausklingen; das Nebelhorn eines Diaphons „grunzt“ am Ende (der Ton fällt um ein Fünftel).
     */
    static final class Horn extends Voice {
        private final float[] table;
        private final double f0, seconds, attack, release;
        private final boolean grunt;
        private final Dsp.Biquad formant = new Dsp.Biquad(), lp = new Dsp.Biquad();
        private double ph;
        private final Dsp.Rng r;

        Horn(String name, double x, double y, double z, double level1m, double f0, double seconds, boolean typhon, long seed, double sr) {
            super(name, x, y, z, level1m, false);
            this.f0 = f0;
            this.seconds = Math.max(0.2, seconds);
            r = new Dsp.Rng(seed);
            grunt = !typhon;
            attack = typhon ? 0.05 : 0.14;
            release = typhon ? 0.18 : 0.4;
            table = harmonics(16, typhon ? 0.75 : 1.05);
            formant.peaking(typhon ? 900 : 640, 1.2, typhon ? 7 : 5, sr);
            lp.lowpass(typhon ? 4200 : 2800, 0.7, sr);
        }

        /** Eine Periode mit k Obertönen, Gewicht 1/kᵖ, auf Effektivwert 1 gebracht. */
        static float[] harmonics(int k, double p) {
            float[] t = new float[2048];
            double e = 0;
            for (int i = 0; i < t.length; i++) {
                double s = 0;
                for (int h = 1; h <= k; h++) s += Math.sin(Dsp.TAU * h * i / t.length) / Math.pow(h, p);
                t[i] = (float) s;
                e += s * s;
            }
            float g = (float) (1 / Math.sqrt(e / t.length));
            for (int i = 0; i < t.length; i++) t[i] *= g;
            return t;
        }

        @Override boolean render(float[] out, int off, int n, double sr) {
            for (int i = off; i < n; i++) {
                double t = age++ / sr;
                double env = Math.min(1, t / attack);
                double f = f0 * (1 + 0.004 * Math.sin(Dsp.TAU * 5.3 * t));
                if (t > seconds) env *= Math.exp(-(t - seconds) / (release / 3));
                if (grunt && t > seconds - 0.45) {
                    double u = Math.min(1, (t - (seconds - 0.45)) / 0.6);
                    f *= 1 - 0.2 * u * u;
                }
                ph += f / sr;
                ph -= Math.floor(ph);
                float s = table[(int) (ph * table.length) & 2047];
                s += 0.02f * r.white();
                out[i] = (float) (lp.process(formant.process(s)) * env);
            }
            return age / sr < seconds + release * 2;
        }
    }

    // ------------------------------------------------------------------ Möwe

    /**
     * Silbermöwe: kurzes „kjau“, Warnruf „kek-kek-kek“ oder ein langer Ruf aus sechs bis neun Tönen. Jeder Ton gleitet
     * hinauf und fällt ab, mit vielen Obertönen (näselnd), rau durch eine schnelle Schwankung, mit einem Formanten um
     * 2,2 kHz.
     */
    static final class Gull extends Voice {
        private final double[] noteT, noteLen, notePeak;
        private final float[] table = Horn.harmonics(9, 0.55);
        private final Dsp.Biquad formant = new Dsp.Biquad();
        private final Dsp.Rng r;
        private final double total;
        private double ph;
        final String variant;

        Gull(double x, double y, double z, double strength, long seed, double sr) {
            super("Möwe", x, y, z, 88 + 8 * Math.max(0, Math.min(1, strength)), false);
            r = new Dsp.Rng(seed);
            double u = r.uniform();
            int notes;
            double gap, len;
            if (u < 0.5) { variant = "kjau"; notes = 1; len = 0.26 + 0.12 * r.uniform(); gap = 0; }
            else if (u < 0.75) { variant = "kek"; notes = 3 + (int) (r.uniform() * 2); len = 0.07; gap = 0.09; }
            else { variant = "Ruf"; notes = 6 + (int) (r.uniform() * 4); len = 0.2; gap = 0.07; }
            noteT = new double[notes];
            noteLen = new double[notes];
            notePeak = new double[notes];
            double t = 0, base = 820 + 260 * r.uniform();
            for (int k = 0; k < notes; k++) {
                noteT[k] = t;
                noteLen[k] = variant.equals("Ruf") && k == 0 ? 0.42 : len * (0.85 + 0.3 * r.uniform());
                notePeak[k] = base * (variant.equals("kek") ? 1.25 : 1) * (variant.equals("Ruf") ? 1 - 0.03 * k : 1) * (0.96 + 0.08 * r.uniform());
                t += noteLen[k] + gap * (0.8 + 0.4 * r.uniform());
            }
            total = t;
            formant.peaking(2200 + 400 * r.uniform(), 1.3, 9, sr);
        }

        @Override boolean render(float[] out, int off, int n, double sr) {
            for (int i = off; i < n; i++) {
                double t = age++ / sr;
                int k = noteT.length - 1;
                while (k > 0 && noteT[k] > t) k--;
                double u = (t - noteT[k]) / noteLen[k];
                if (u < 0 || u > 1) { out[i] = formant.process(0); continue; }
                double rise = Math.min(1, u / 0.3), fall = u > 0.3 ? (u - 0.3) / 0.7 : 0;
                double f = notePeak[k] * (0.72 + 0.28 * rise) * (1 - 0.38 * fall * fall);
                double env = Math.sin(Math.PI * Math.min(1, u * 1.15)) * (u < 0.05 ? u / 0.05 : 1);
                double rough = 1 - 0.35 * (0.5 + 0.5 * Math.sin(Dsp.TAU * 47 * t));
                ph += f / sr;
                ph -= Math.floor(ph);
                float s = table[(int) (ph * table.length) & 2047] + 0.12f * r.white();
                out[i] = (float) (formant.process(s) * env * rough * 0.7);
            }
            return age / sr < total + 0.05;
        }

        double length() { return total; }
    }

    // ------------------------------------------------------------------ fertig gerechneter Klang

    /**
     * Ein vorher gerechneter Klang: Donner (Pegel schon am Ohr, atEar) oder seit Haithabu Phase 14 Rufe und Gewerke
     * (Pegel in 1 m).
     */
    static final class Buffered extends Voice {
        final float[] data;

        Buffered(String name, double x, double y, double z, double level, float[] data, boolean atEar) {
            super(name, x, y, z, level, atEar);
            this.data = data;
        }

        @Override boolean render(float[] out, int off, int n, double sr) {
            for (int i = off; i < n; i++) out[i] = age < data.length ? data[age++] : 0;
            return age < data.length;
        }
    }
}
