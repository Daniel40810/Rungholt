package com.dan.soundscape;

/**
 * Seit Rungholt Phase 14: Klänge des Wattenmeers und einer Marsch im Mittelalter, beim Auslösen einmal gerechnet wie
 * {@link Calls} und {@link Glacial}. Alles erzeugt, nach Gehör und Lautbeschreibungen gebaut [G], keine Aufnahmen.
 * <ul>
 * <li><b>Austernfischer</b>: lautes, schrilles „kliep“ (2,6 bis 3,3 kHz), einzeln oder in Reihen; ab Stärke 0,8 das
 * Trillern im Flug, eine schnelle Kette, die sich beschleunigt. 98 dB in 1 m.</li>
 * <li><b>Alpenstrandläufer</b>: ein gepresstes, schnarrendes „trrrii“ um 4,5 kHz; tone = Zahl der Vögel, die zugleich
 * rufen (ein Schwarm zwitschert durcheinander). 80 + 10 · lg(Vögel) dB, höchstens 100.</li>
 * <li><b>Säbelschnäbler</b>: weiches, flötendes „klüüt“ um 2,2 kHz, ein- bis dreimal. 90 dB.</li>
 * <li><b>Brandgans</b>: das Weibchen ein raues, nasales „ak-ak-ak“ um 500 Hz, der Erpel ein Pfeifen um 1,6 kHz. 88 dB.</li>
 * <li><b>Lachmöwe</b>: rau kreischendes „kriäh“ (1,3 → 1,7 → 1,1 kHz). 92 dB.</li>
 * <li><b>Ringelgans</b>: tiefes, kehliges Grummeln „rrott“ um 500 Hz in Reihen; tone = Zahl der Gänse (Schwatzen einer
 * Schar). 85 + 8 · lg(Gänse) dB, höchstens 104.</li>
 * <li><b>Schwarm</b>: das Rauschen von Hunderten Flügeln, wenn ein Schwarm wendet; tone = Zahl der Vögel.
 * 70 + 10 · lg(Vögel / 100) dB.</li>
 * <li><b>Seehund</b>: Schnauben beim Auftauchen oder ein tiefes Knurren; <b>Heuler</b>: das Rufen des Jungen nach der
 * Mutter, ein klagendes „maa“ (350 → 550 → 400 Hz). 80 und 85 dB.</li>
 * <li><b>Rind</b> (Muhen, 1,2 bis 2 s, 110 → 160 Hz) 100 dB, <b>Schaf</b> (Blöken mit Zittern) 85 dB.</li>
 * <li><b>Glocke</b>: ein Schlag einer Bronzeglocke aus ihren Teiltönen (Unterton 0,5, Prim 1, kleine Terz 1,2, Quinte
 * 1,5, Oktave 2 und höhere), jeder als Paar leicht verstimmter Töne (Schweben) mit eigener Abklingzeit; der Anschlag
 * als kurzes Klirren. tone 0 die große Glocke (Prim 311 Hz), 1 die kleine (415 Hz). 118 dB in 1 m.</li>
 * <li><b>Glocke im Watt</b> (die Sage): derselbe Schlag, wie unter Wasser: dumpf (Tiefpass), die Tonhöhe wogt leicht,
 * zwei verschwommene Echos. 130 dB in 1 m [G: die Sage, kein Messwert].</li>
 * </ul>
 * Nur Java 21.
 */
final class Wadden {
    private Wadden() { }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Calls.Note n(double t, double len, double f0, double f1, double f2, double peak, double noise, double amHz, double amD, double vibHz, double vibD, double att, double rel, double amp) {
        return new Calls.Note(t, len, f0, f1, f2, peak, noise, amHz, amD, vibHz, vibD, att, rel, amp);
    }

    /** Mehrere Rufe derselben Art durcheinander (versetzt, verschieden laut). */
    static float[] chorus(float[][] parts, double[] at, double sr) {
        int len = 0;
        for (int i = 0; i < parts.length; i++) len = Math.max(len, (int) (at[i] * sr) + parts[i].length);
        float[] o = new float[len];
        for (int i = 0; i < parts.length; i++) {
            int off = (int) (at[i] * sr);
            float g = (float) (i == 0 ? 1 : 0.35 + 0.5 * Math.abs(Math.sin(i * 2.399)));
            for (int j = 0; j < parts[i].length; j++) o[off + j] += parts[i][j] * g;
        }
        Calls.normalize(o, sr);
        return o;
    }

    static Calls.Made make(Soundscape.Kind k, double strength, double tone, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = clamp(strength, 0, 1), lv = 6 * Math.log10(Math.max(0.05, s));
        switch (k) {
            case AUSTERNFISCHER -> {
                if (s >= 0.8) {
                    // Trillern: schnelle Kette „kli-kli-kli…“, beschleunigt, dann ausklingend
                    int m = 14 + (int) (r.uniform() * 12);
                    Calls.Note[] ns = new Calls.Note[m];
                    double t = 0, gap = 0.11;
                    for (int i = 0; i < m; i++) {
                        double f = 2700 + 300 * Math.sin(i * 0.4) + 100 * r.uniform();
                        ns[i] = n(t, 0.05, f * 0.95, f * 1.05, f * 0.9, 0.35, 0.05, 0, 0, 0, 0, 0.004, 0.015, 0.8 + 0.2 * Math.sin(Math.PI * i / m));
                        t += 0.05 + gap;
                        gap = Math.max(0.025, gap * 0.9);
                    }
                    return new Calls.Made("Austernfischer (Trillern)", 100 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(3000, 2, 1.2)}, 0.6, 1.4, 1200, sr, seed));
                }
                int m = 1 + (int) (r.uniform() * 4);
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) {
                    double f = 2600 + 400 * r.uniform();
                    ns[i] = n(t, 0.12 + 0.05 * r.uniform(), f * 0.85, f * 1.12, f * 0.95, 0.3, 0.04, 0, 0, 0, 0, 0.006, 0.04, 1);
                    t += 0.25 + 0.15 * r.uniform();
                }
                return new Calls.Made("Austernfischer", 98 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(3100, 2, 1.2)}, 0.6, 1.4, 1200, sr, seed));
            }
            case ALPENSTRANDLAEUFER -> {
                int birds = (int) clamp(Math.round(tone), 1, 12);
                float[][] parts = new float[birds][];
                double[] at = new double[birds];
                for (int b = 0; b < birds; b++) {
                    double f = 4200 + 700 * r.uniform();
                    Calls.Note[] ns = {n(0, 0.18 + 0.12 * r.uniform(), f * 0.9, f, f * 0.97, 0.4, 0.15, 38 + 12 * r.uniform(), 0.7, 0, 0, 0.01, 0.05, 1)};
                    parts[b] = Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(4600, 1.5, 1.0)}, 0.5, 1.2, 2000, sr, seed + b);
                    at[b] = b == 0 ? 0 : r.uniform() * 0.9;
                }
                return new Calls.Made("Alpenstrandläufer", Math.min(100, 80 + 10 * Math.log10(birds)) + lv, chorus(parts, at, sr));
            }
            case SAEBELSCHNAEBLER -> {
                int m = 1 + (int) (r.uniform() * 3);
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) {
                    double f = 2050 + 300 * r.uniform();
                    ns[i] = n(t, 0.16 + 0.04 * r.uniform(), f * 0.88, f * 1.08, f, 0.45, 0.02, 0, 0, 0, 0, 0.015, 0.06, 1);
                    t += 0.3 + 0.1 * r.uniform();
                }
                return new Calls.Made("Säbelschnäbler", 90 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(2300, 2.5, 1.0)}, 0.8, 1.8, 900, sr, seed));
            }
            case BRANDGANS -> {
                if (r.uniform() < 0.35) {   // der Erpel pfeift
                    Calls.Note[] ns = {n(0, 0.35, 1500, 1750, 1550, 0.4, 0.05, 0, 0, 5, 0.01, 0.03, 0.1, 1)};
                    return new Calls.Made("Brandgans (Erpel)", 84 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(1700, 2, 1.0)}, 0.7, 1.6, 600, sr, seed));
                }
                int m = 3 + (int) (r.uniform() * 5);
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0, f = 460 + 80 * r.uniform();
                for (int i = 0; i < m; i++) { ns[i] = n(t, 0.09, f, f * 1.1, f * 0.9, 0.3, 0.3, 40, 0.4, 0, 0, 0.006, 0.03, 1); t += 0.17 + 0.03 * r.uniform(); }
                return new Calls.Made("Brandgans", 88 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(1000, 3, 1.4), new Calls.Formant(2200, 3, 0.8)}, 0.2, 0.8, 200, sr, seed));
            }
            case LACHMOEWE -> {
                int m = r.uniform() < 0.3 ? 2 : 1;
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) { ns[i] = n(t, 0.35 + 0.15 * r.uniform(), 1300, 1700, 1100, 0.3, 0.35, 60 + 20 * r.uniform(), 0.45, 0, 0, 0.02, 0.08, 1); t += 0.6; }
                return new Calls.Made("Lachmöwe", 92 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(1800, 2.5, 1.4), new Calls.Formant(3600, 3, 0.6)}, 0.3, 0.8, 500, sr, seed));
            }
            case RINGELGANS -> {
                int geese = (int) clamp(Math.round(tone), 1, 16);
                float[][] parts = new float[geese][];
                double[] at = new double[geese];
                for (int g = 0; g < geese; g++) {
                    int m = 2 + (int) (r.uniform() * 4);
                    Calls.Note[] ns = new Calls.Note[m];
                    double t = 0, f = 440 + 120 * r.uniform();
                    for (int i = 0; i < m; i++) { ns[i] = n(t, 0.14 + 0.06 * r.uniform(), f * 0.95, f, f * 0.85, 0.3, 0.35, 28 + 6 * r.uniform(), 0.6, 0, 0, 0.01, 0.05, 1); t += 0.26 + 0.12 * r.uniform(); }
                    parts[g] = Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(800, 3, 1.4), new Calls.Formant(1700, 3, 0.6)}, 0.25, 0.8, 150, sr, seed + g);
                    at[g] = g == 0 ? 0 : r.uniform() * 1.6;
                }
                return new Calls.Made("Ringelgans", Math.min(104, 85 + 8 * Math.log10(geese)) + lv, chorus(parts, at, sr));
            }
            case SCHWARM -> {
                double birds = Math.max(10, tone), len = 1.6 + 0.6 * r.uniform();
                float[] o = new float[(int) ((len + 0.2) * sr)];
                Dsp.Biquad bp = new Dsp.Biquad(), lo = new Dsp.Biquad().lowpass(700, 0.7, sr);
                for (int i = 0; i < o.length; i++) {
                    double t = i / sr, u = Math.min(1, t / len);
                    if (i % 64 == 0) bp.bandpass(900 + 1800 * Math.sin(Math.PI * u), 0.7, sr);
                    double env = Math.sin(Math.PI * u) * (0.75 + 0.25 * Math.sin(Dsp.TAU * 17 * t + 3 * Math.sin(Dsp.TAU * 0.7 * t)));
                    float w = r.white();
                    o[i] = (float) ((bp.process(w) + lo.process(w) * 0.6) * env);
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Schwarm", 70 + 10 * Math.log10(birds / 100), o);
            }
            case SEEHUND -> {
                if (r.uniform() < 0.6) {   // Schnauben beim Auftauchen
                    float[] o = new float[(int) (0.7 * sr)];
                    Dsp.Biquad bp = new Dsp.Biquad().bandpass(650 + 250 * r.uniform(), 1.1, sr), lo = new Dsp.Biquad().lowpass(300, 0.7, sr);
                    for (int i = 0; i < o.length; i++) {
                        double t = i / sr, env = (1 - Math.exp(-t / 0.01)) * Math.exp(-t / 0.13);
                        float w = r.white();
                        o[i] = (float) ((bp.process(w) * 1.2 + lo.process(w) * 0.6) * env);
                    }
                    Calls.normalize(o, sr);
                    return new Calls.Made("Seehund (schnaubt)", 78 + lv, o);
                }
                Calls.Note[] ns = {n(0, 0.5 + 0.4 * r.uniform(), 140, 170, 120, 0.3, 0.45, 22, 0.6, 0, 0, 0.04, 0.12, 1)};
                return new Calls.Made("Seehund (knurrt)", 80 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(500, 2.5, 1.4), new Calls.Formant(1100, 3, 0.6)}, 0.3, 0.9, 70, sr, seed));
            }
            case HEULER -> {
                double f = 340 + 60 * r.uniform();
                Calls.Note[] ns = {n(0, 0.6 + 0.4 * r.uniform(), f, f * 1.55, f * 1.15, 0.35, 0.12, 0, 0, 5.5, 0.025, 0.06, 0.2, 1)};
                return new Calls.Made("Heuler", 85 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(900, 3, 1.4), new Calls.Formant(2100, 3, 0.7)}, 0.3, 0.75, 200, sr, seed));
            }
            case RIND -> {
                double f = 105 + 30 * r.uniform(), len = 1.2 + 0.8 * r.uniform();
                Calls.Note[] ns = {n(0, len, f, f * 1.45, f * 1.05, 0.3, 0.12, 0, 0, 0, 0, 0.12, 0.35, 1)};
                return new Calls.Made("Rind", 100 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(480, 3, 1.6), new Calls.Formant(1150, 3, 0.9), new Calls.Formant(2400, 4, 0.3)}, 0.3, 0.8, 60, sr, seed));
            }
            case SCHAF -> {
                double f = 290 + 90 * r.uniform();
                Calls.Note[] ns = {n(0, 0.55 + 0.3 * r.uniform(), f * 0.95, f * 1.05, f * 0.85, 0.25, 0.15, 6 + 2 * r.uniform(), 0.55, 0, 0, 0.04, 0.15, 1)};
                return new Calls.Made("Schaf", 85 + lv, Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(800, 3, 1.5), new Calls.Formant(1900, 3, 0.8)}, 0.2, 0.75, 150, sr, seed));
            }
            case GLOCKE -> { return new Calls.Made(tone < 0.5 ? "Glocke (groß)" : "Glocke (klein)", 118 + lv, bell(tone < 0.5 ? 311 : 415, false, r, sr)); }
            case GLOCKE_WATT -> { return new Calls.Made("Glocke im Watt", 130 + lv, bell(tone < 0.5 ? 311 : 415, true, r, sr)); }
            default -> throw new IllegalArgumentException("kein Klang des Watts: " + k);
        }
    }

    /** Teiltöne einer Bronzeglocke: Verhältnis zum Prim, Stärke, Abklingzeit (s) [G, nach den Lehrbuchwerten]. */
    static final double[][] PARTIALS = {{0.5, 0.55, 3.8}, {1.0, 0.75, 2.4}, {1.2, 0.9, 1.9}, {1.5, 0.35, 1.3}, {2.0, 1.0, 1.1},
            {2.5, 0.45, 0.65}, {3.0, 0.32, 0.5}, {4.2, 0.22, 0.32}, {5.4, 0.12, 0.2}};

    /**
     * Ein Glockenschlag mit Prim f (Hz). Im Watt: Tiefpass 900 Hz, die Tonhöhe wogt (0,6 % mit 0,4 Hz), zwei verschwommene
     * Echos nach 0,11 und 0,27 s.
     */
    static float[] bell(double f, boolean sunken, Dsp.Rng r, double sr) {
        double len = sunken ? 7.5 : 6.5;
        float[] o = new float[(int) (len * sr)];
        double detune = 1 + 0.004 * (r.uniform() - 0.5);
        int np = PARTIALS.length;
        double[] ph = new double[2 * np];
        double wob = r.uniform() * Dsp.TAU;
        for (int i = 0; i < o.length; i++) {
            double t = i / sr;
            double fm = sunken ? 1 + 0.006 * Math.sin(Dsp.TAU * 0.4 * t + wob) : 1;
            double y = 0;
            for (int p = 0; p < np; p++) {
                double fp = f * PARTIALS[p][0] * detune * fm;
                if (fp > sr * 0.45) continue;
                double env = PARTIALS[p][1] * Math.exp(-t / PARTIALS[p][2]);
                for (int d = 0; d < 2; d++) {   // je Teilton ein Paar, 0,3 bis 0,8 Hz auseinander: das Schweben
                    int q = 2 * p + d;
                    ph[q] += (fp + (d == 0 ? -0.25 : 0.25) * (1 + p * 0.1)) / sr;
                    ph[q] -= Math.floor(ph[q]);
                    y += env * Math.sin(Dsp.TAU * ph[q]) * 0.5;
                }
            }
            o[i] = (float) y;
        }
        // der Anschlag: ein kurzes Klirren aus Rauschen
        Dsp.Biquad clank = new Dsp.Biquad().bandpass(2400 + 800 * r.uniform(), 1.2, sr);
        for (int i = 0; i < (int) (0.025 * sr); i++) o[i] += (float) (clank.process(r.white()) * 1.6 * Math.exp(-i / (0.004 * sr)));
        if (sunken) {
            Dsp.Biquad lp1 = new Dsp.Biquad().lowpass(900, 0.7, sr), lp2 = new Dsp.Biquad().lowpass(900, 0.7, sr);
            for (int i = 0; i < o.length; i++) o[i] = lp2.process(lp1.process(o[i]));
            float[] e = o.clone();
            int d1 = (int) (0.11 * sr), d2 = (int) (0.27 * sr);
            Dsp.Biquad lp3 = new Dsp.Biquad().lowpass(500, 0.6, sr);
            for (int i = 0; i < o.length; i++) {
                float a = i >= d1 ? e[i - d1] * 0.38f : 0, b = i >= d2 ? e[i - d2] * 0.22f : 0;
                o[i] += lp3.process(a + b);
            }
        }
        Calls.normalize(o, sr);
        return o;
    }
}
