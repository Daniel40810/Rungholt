package com.dan.soundscape;

/**
 * Klänge der Gewerke und der Schiffe (seit Haithabu Phase 14), beim Auslösen einmal gerechnet wie {@link Calls}:
 * Hammer auf heißem Eisen und auf dem Amboss (abklingende Eigentöne eines kleinen Blockambosses), das leichte
 * Klopfen des Bronzegießers, Beil und Dechsel im Holz, Sägestriche (grob und fein), der Ruderschlag eines Schiffs
 * (Eintauchen, Spritzen, Knarren in der Riemenpforte oder Dolle, Tropfen) und einstürzende brennende Balken.
 * <p>
 * Pegel wie bei den Rufen: lauteste 100 ms in 1 m [G]. Hammer 112 dB, Klopfen 92, Beil 100, Säge 84, feine Säge 70,
 * Ruderschlag eines ganzen Schiffs 86 + 10 · lg(Riemen / 10), Einsturz 116. Nur Java 21.
 */
final class Crafts {
    private Crafts() { }

    /** Abklingende Eigentöne {f, Abklingzeit τ (s), Gewicht} mit einem Anschlag aus Rauschen. */
    private static void modes(float[] out, int at, double[][] m, double strike, double sr, Dsp.Rng r) {
        for (double[] q : m) {
            double rad = Math.exp(-1 / (q[1] * sr)), w = Dsp.TAU * Math.min(q[0], sr * 0.45) / sr;
            double c1 = 2 * rad * Math.cos(w), c2 = -rad * rad, y1 = q[2] * Math.sin(w) * strike, y2 = 0;
            int n = Math.min(out.length - at, (int) (q[1] * 9 * sr));
            for (int i = 0; i < n; i++) {
                double y = c1 * y1 + c2 * y2;
                y2 = y1;
                y1 = y;
                out[at + i] += (float) y;
            }
        }
        // der Anschlag: 2 ms helles Rauschen
        Dsp.Biquad hp = new Dsp.Biquad().highpass(1200, 0.7, sr);
        int n = Math.min(out.length - at, (int) (0.002 * sr));
        for (int i = 0; i < n; i++) out[at + i] += hp.process(r.white()) * (float) (strike * 0.6 * (1 - i / (double) n));
    }

    /** Rauschen durch einen Filter mit Hüllkurve: Einsatz (s), Abklingzeit τ (s), Dauer (s). */
    private static void noise(float[] out, int at, Dsp.Biquad f, double amp, double attack, double tau, double dur, double sr, Dsp.Rng r) {
        int n = Math.min(out.length - at, (int) (dur * sr));
        for (int i = 0; i < n; i++) {
            double t = i / sr;
            out[at + i] += (float) (f.process(r.white()) * amp * Math.min(1, t / Math.max(1e-4, attack)) * Math.exp(-t / tau));
        }
    }

    /** Was ein Klang ist: Name, Pegel in 1 m, Daten. */
    static Calls.Made make(Soundscape.Kind k, double strength, double tone, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = Math.max(0, Math.min(1, strength));
        switch (k) {
            case HAMMER -> {
                // Schlag auf das heiße Eisen (dumpf) mit dem Amboss darunter; ab und zu ein Prellschlag auf die Bahn (hell)
                boolean ring = r.uniform() < 0.22;
                float[] o = new float[(int) ((ring ? 3.2 : 1.2) * sr)];
                double base = 2050 * (0.96 + 0.08 * tone);
                double[][] anvil = {{base, 0.65, 1}, {base * 1.58, 0.42, 0.7}, {base * 2.37, 0.28, 0.5}, {base * 3.12, 0.18, 0.35}};
                double[][] iron = {{380, 0.018, 1}, {910, 0.012, 0.7}, {1700, 0.008, 0.4}};
                if (ring) modes(o, 0, anvil, 1, sr, r);
                // das Eisen auf der Bahn dämpft den Amboss: kurz und leise
                else { modes(o, 0, iron, 1.4, sr, r); modes(o, 0, damp(scale(anvil, 0.35), 0.18), 1, sr, r); }
                fade(o, sr);
                Calls.normalize(o, sr);
                return new Calls.Made(ring ? "Amboss" : "Hammer", (ring ? 108 : 112) + 6 * Math.log10(Math.max(0.05, s)), o);
            }
            case KLOPFEN -> {
                float[] o = new float[(int) (0.5 * sr)];
                double b = 3100 * (0.9 + 0.2 * r.uniform());
                modes(o, 0, new double[][]{{b, 0.12, 1}, {b * 1.63, 0.08, 0.6}, {b * 2.31, 0.05, 0.4}, {600, 0.01, 0.6}}, 1, sr, r);
                fade(o, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Klopfen", 92 + 6 * Math.log10(Math.max(0.05, s)), o);
            }
            case BEIL -> {
                // Beil oder Dechsel: dumpfer Schlag ins Holz, Holz klingt kurz, dazu ein heller Riss
                float[] o = new float[(int) (0.45 * sr)];
                noise(o, 0, new Dsp.Biquad().lowpass(1100, 0.7, sr), 1.0, 0.001, 0.025, 0.2, sr, r);
                double w = 0.9 + 0.2 * r.uniform();
                modes(o, 0, new double[][]{{180 * w, 0.04, 1.2}, {430 * w, 0.025, 0.9}, {760 * w, 0.015, 0.6}}, 1, sr, r);
                noise(o, (int) (0.004 * sr), new Dsp.Biquad().highpass(3500, 0.7, sr), 0.4, 0.0005, 0.004, 0.03, sr, r);
                fade(o, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Beil", 100 + 6 * Math.log10(Math.max(0.05, s)), o);
            }
            case SAEGE, FEINSAEGE -> {
                // ein Strich: Rauschen der Zähne im Takt der Zähne (grob 140 Hz, fein 320 Hz), Stoß lauter als Zug
                boolean fine = k == Soundscape.Kind.FEINSAEGE;
                double dur = fine ? 0.28 + 0.06 * r.uniform() : 0.45 + 0.12 * r.uniform();
                float[] o = new float[(int) ((dur + 0.05) * sr)];
                Dsp.Biquad bp = new Dsp.Biquad().bandpass(fine ? 6500 : 3800, 0.9, sr), body = new Dsp.Biquad().bandpass(fine ? 1800 : 420, 3, sr);
                double tooth = (fine ? 320 : 140) * (0.9 + 0.2 * r.uniform()), ph = 0;
                boolean push = tone > 0.5;
                int n = (int) (dur * sr);
                for (int i = 0; i < n; i++) {
                    double t = i / sr, u = t / dur;
                    double env = Math.pow(Math.sin(Math.PI * u), 0.7) * (push ? 1 : 0.6);
                    ph += tooth * (0.8 + 0.4 * Math.sin(Math.PI * u)) / sr;
                    double am = 0.45 + 0.55 * Math.pow(0.5 + 0.5 * Math.sin(Dsp.TAU * ph), 3);
                    float w0 = r.white();
                    o[i] = (float) ((bp.process(w0) * 1.4 * am + body.process(w0) * 0.35 * am) * env);
                }
                Calls.normalize(o, sr);
                return new Calls.Made(fine ? "feine Säge" : "Säge", (fine ? 70 : 84) + 6 * Math.log10(Math.max(0.05, s)), o);
            }
            case RUDER -> {
                // alle Blätter fast zugleich: Eintauchen (Plopp), Spritzen; dann das Knarren in Pforte oder Dolle, Tropfen
                int blades = (int) Math.max(2, Math.min(12, Math.round(tone)));
                float[] o = new float[(int) (1.2 * sr)];
                for (int b = 0; b < blades; b++) {
                    int at = (int) ((0.06 * r.uniform()) * sr);
                    double f0 = 300 + 150 * r.uniform(), ph = 0;
                    int n = (int) (0.06 * sr);
                    for (int i = 0; i < n; i++) {
                        double t = i / sr;
                        ph += (f0 * Math.exp(-t / 0.02) + 120) / sr;
                        o[at + i] += (float) (Math.sin(Dsp.TAU * ph) * Math.exp(-t / 0.018) * 0.9);
                    }
                    noise(o, at, new Dsp.Biquad().bandpass(1500 + 1000 * r.uniform(), 1.0, sr), 0.45, 0.003, 0.045, 0.25, sr, r);
                    if (r.uniform() < 0.4) {
                        // Knarren: Haften und Gleiten, ein unruhiger Pulszug durch die Resonanz des Holzes
                        int c0 = at + (int) ((0.15 + 0.2 * r.uniform()) * sr), cn = (int) ((0.18 + 0.1 * r.uniform()) * sr);
                        double per = sr / (180 + 140 * r.uniform()), next = 0;
                        Dsp.Biquad wood = new Dsp.Biquad().bandpass(800 + 500 * r.uniform(), 2.5, sr);
                        for (int i = 0; i < cn && c0 + i < o.length; i++) {
                            float pulse = 0;
                            if (i >= next) { pulse = 1; next = i + per * (0.85 + 0.3 * r.uniform()); }
                            double env = Math.sin(Math.PI * i / cn);
                            o[c0 + i] += (float) (wood.process(pulse) * 2 * env);
                        }
                    }
                    // Tropfen vom Blatt in der Rückholung
                    for (int d = 0; d < 2; d++) {
                        int dt = at + (int) ((0.55 + 0.4 * r.uniform()) * sr);
                        if (dt < o.length - 1) modes(o, dt, new double[][]{{1300 + 2200 * r.uniform(), 0.006, 0.15}}, 1, sr, r);
                    }
                }
                fade(o, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Ruderschlag", 86 + 10 * Math.log10(blades / 10.0) + 6 * Math.log10(Math.max(0.05, s)), o);
            }
            case EINSTURZ -> {
                // brennende Balken stürzen: dumpfer Schlag, Krachen, Prasseln, Grollen
                float[] o = new float[(int) (4.5 * sr)];
                double ph = 0;
                for (int i = 0; i < (int) (0.6 * sr); i++) {
                    double t = i / sr;
                    ph += (32 + 28 * Math.exp(-t * 4)) / sr;
                    o[i] += (float) (Math.sin(Dsp.TAU * ph) * (1 - Math.exp(-t * 80)) * Math.exp(-t * 4) * 1.5);
                }
                noise(o, 0, new Dsp.Biquad().bandpass(700, 0.6, sr), 2.5, 0.004, 0.5, 3, sr, r);
                noise(o, 0, new Dsp.Biquad().lowpass(300, 0.7, sr), 1.6, 0.05, 1.4, 4.4, sr, r);
                for (int c = 0; c < 60; c++) {
                    int at = (int) (Math.pow(r.uniform(), 1.6) * 4 * sr);
                    if (at < o.length - 10) modes(o, at, new double[][]{{900 + 4000 * r.uniform(), 0.002 + 0.004 * r.uniform(), 0.6}}, 1, sr, r);
                }
                fade(o, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Einsturz", 116 + 6 * Math.log10(Math.max(0.05, s)), o);
            }
            default -> throw new IllegalArgumentException(k.name());
        }
    }

    /** Abklingzeiten mal f. */
    private static double[][] damp(double[][] m, double f) {
        double[][] o = new double[m.length][];
        for (int i = 0; i < m.length; i++) o[i] = new double[]{m[i][0], m[i][1] * f, m[i][2]};
        return o;
    }

    /** Die letzten 8 % weich auf null (kein Knacken am Ende des Puffers). */
    static void fade(float[] o, double sr) {
        int n = Math.max(1, o.length / 12);
        for (int i = 0; i < n; i++) o[o.length - 1 - i] *= (float) (i / (double) n);
    }

    private static double[][] scale(double[][] m, double g) {
        double[][] o = new double[m.length][];
        for (int i = 0; i < m.length; i++) o[i] = new double[]{m[i][0], m[i][1], m[i][2] * g};
        return o;
    }
}
