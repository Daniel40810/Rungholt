package com.dan.soundscape;

/**
 * Rufe von Tieren als Quelle und Filter (seit Haithabu Phase 14): Jeder Ruf ist eine Folge von Tönen; ein Ton hat
 * einen Grundton mit Verlauf (Anfang, Gipfel, Ende), Obertöne nach einer Tabelle, Rauschen, ein Zittern der
 * Lautstärke (Meckern, Rauheit) und ein Vibrato. Die Mischung läuft durch Formanten (parallele Bandpässe). Der Ruf
 * wird beim Auslösen einmal gerechnet (unter 1 ms) und dann wie ein fertiger Klang abgespielt.
 * <p>
 * <b>Pegel:</b> Der Effektivwert der lautesten 100 ms ist 1; der Pegel einer Stimme gilt also für die lautesten
 * 100 ms in 1 m (wie L<sub>AF,max</sub>). Grundtöne und Muster sind nach Gehör und Lautbeschreibungen gestaltet [G],
 * nicht aus Aufnahmen gemessen. Nur Java 21.
 */
final class Calls {
    private Calls() { }

    /**
     * Ein Ton: Beginn und Länge (s), Grundton am Anfang, am Gipfel und am Ende (Hz), wo der Gipfel liegt (0..1),
     * Anteil Rauschen (0..1), Zittern (Hz, Tiefe 0..1), Vibrato (Hz, Tiefe als Anteil), Ein- und Ausschwingen (s),
     * Lautstärke.
     */
    record Note(double t, double len, double f0, double f1, double f2, double peakAt, double noise,
                double amHz, double amDepth, double vibHz, double vibDepth, double attack, double release, double amp) {
        Note at(double nt) { return new Note(nt, len, f0, f1, f2, peakAt, noise, amHz, amDepth, vibHz, vibDepth, attack, release, amp); }
    }

    /** Ein Formant: Mitte (Hz), Güte, Verstärkung (linear). */
    record Formant(double f, double q, double gain) { }

    /**
     * Rechnet die Töne durch die Formanten: Obertöne bis knapp unter die halbe Abtastrate (Gewicht 1/kᵖ), dazu
     * Rauschen; Hochpass bei hp Hz. Ergebnis mit Effektivwert 1 in den lautesten 100 ms.
     */
    static float[] synth(Note[] notes, Formant[] form, double dry, double p, double hp, double sr, long seed) {
        double total = 0, fmax = 1;
        for (Note n : notes) { total = Math.max(total, n.t + n.len); fmax = Math.max(fmax, Math.max(n.f0, Math.max(n.f1, n.f2)) * (1 + n.vibDepth)); }
        int k = (int) Math.max(1, Math.min(24, Math.floor(0.45 * sr / fmax)));
        float[] table = Voice.Horn.harmonics(k, p);
        int len = (int) Math.ceil((total + 0.06) * sr);
        float[] out = new float[len];
        Dsp.Rng r = new Dsp.Rng(seed);
        Dsp.Biquad[] bp = new Dsp.Biquad[form.length];
        for (int j = 0; j < form.length; j++) bp[j] = new Dsp.Biquad().bandpass(Math.min(form[j].f, sr * 0.45), form[j].q, sr);
        // die Höhen über dem höchsten Formanten fallen ab (Schnabel, Maul und Luft), nur die Schwalbe bleibt hell
        double top = 0;
        for (Formant f : form) top = Math.max(top, f.f);
        Dsp.Biquad h = new Dsp.Biquad().highpass(hp, 0.7, sr), lp = new Dsp.Biquad().lowpass(Math.min(sr * 0.45, Math.max(4000, top * 2.2)), 0.6, sr);
        double ph = 0;
        int idx = 0;
        for (int i = 0; i < len; i++) {
            double t = i / sr;
            while (idx < notes.length - 1 && t >= notes[idx + 1].t) idx++;
            Note n = notes[idx];
            double u = (t - n.t) / n.len;
            float src = 0, tonal = 0;
            double env = 0;
            if (u >= 0 && u <= 1) {
                double f = u < n.peakAt ? n.f0 + (n.f1 - n.f0) * smooth(u / Math.max(1e-6, n.peakAt))
                        : n.f1 + (n.f2 - n.f1) * smooth((u - n.peakAt) / Math.max(1e-6, 1 - n.peakAt));
                f *= 1 + n.vibDepth * Math.sin(Dsp.TAU * n.vibHz * t);
                double tt = t - n.t, rest = n.t + n.len - t;
                env = Math.min(1, tt / Math.max(1e-4, n.attack)) * Math.min(1, rest / Math.max(1e-4, n.release));
                env *= 1 - n.amDepth * (0.5 + 0.5 * Math.sin(Dsp.TAU * n.amHz * t));
                env *= n.amp;
                ph += f / sr;
                ph -= Math.floor(ph);
                tonal = (float) (table[(int) (ph * table.length) & 2047] * (1 - n.noise));
                src = (float) (tonal + r.white() * 1.7 * n.noise);
            }
            // das Rauschen nur durch die Formanten, der trockene Anteil nur aus den Obertönen
            float y = (float) (tonal * dry);
            for (int j = 0; j < bp.length; j++) y += bp[j].process(src) * (float) form[j].gain;
            out[i] = (float) (lp.process(h.process(y)) * env);
        }
        normalize(out, sr);
        return out;
    }

    /** Auf Effektivwert 1 in den lautesten 100 ms bringen. */
    static void normalize(float[] x, double sr) {
        int w = Math.max(1, (int) (0.1 * sr));
        double s = 0, best = 0;
        for (int i = 0; i < x.length; i++) {
            s += x[i] * x[i];
            if (i >= w) s -= x[i - w] * x[i - w];
            best = Math.max(best, s);
        }
        double rms = Math.sqrt(Math.max(1e-20, best / w));
        float g = (float) (1 / rms);
        for (int i = 0; i < x.length; i++) x[i] *= g;
    }

    static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    private static Note n(double t, double len, double f0, double f1, double f2, double peak, double noise, double amHz, double amD, double attack, double release, double amp) {
        return new Note(t, len, f0, f1, f2, peak, noise, amHz, amD, 0, 0, attack, release, amp);
    }

    // ------------------------------------------------------------------ Arten

    /** Was ein Ruf ist: Name, Pegel in 1 m (dB, lauteste 100 ms), Klang. */
    record Made(String name, double level1m, float[] data) { }

    /**
     * Ein Ruf der Art zu Stärke 0..1. Pegel in 1 m [G]: Hahn und Hund 100 dB, Ziege 88, Krähe 90, Ente 85 (Erpel 72),
     * Huhn 80, Schwein 78 (Quieken 104), Schwalbe 75, Kormoran 78, Flügel des Schwans 84.
     */
    static Made make(Soundscape.Kind k, double strength, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = Math.max(0, Math.min(1, strength));
        double lv = 6 * Math.log10(Math.max(0.05, s));
        switch (k) {
            case KRAEHE -> {
                // Nebelkrähe: zwei bis vier raue „kräh“, Grundton um 550 Hz, schnell zitternd (Rauheit), hell
                int m = 2 + (int) (r.uniform() * 3);
                Note[] ns = new Note[m];
                double t = 0, f = 480 + 140 * r.uniform();
                for (int i = 0; i < m; i++) {
                    double len = 0.24 + 0.12 * r.uniform();
                    ns[i] = n(t, len, f * 0.92, f, f * 0.78, 0.25, 0.42, 70 + 20 * r.uniform(), 0.55, 0.02, 0.08, 1 - 0.1 * i);
                    t += len + 0.18 + 0.15 * r.uniform();
                }
                return new Made("Krähe", 90 + lv, synth(ns, new Formant[]{new Formant(1300, 3, 1.4), new Formant(2600, 4, 0.9)}, 0.25, 0.7, 250, sr, seed));
            }
            case ENTE -> {
                // Stockente, Weibchen: absteigende Reihe „quaak quaak quak quak“ (vier bis acht), näselnd
                int m = 4 + (int) (r.uniform() * 5);
                Note[] ns = new Note[m];
                double t = 0, f = 270 + 60 * r.uniform();
                for (int i = 0; i < m; i++) {
                    double len = Math.max(0.09, 0.2 - 0.014 * i);
                    ns[i] = n(t, len, f * 1.05, f * 1.12, f * 0.9, 0.3, 0.18, 0, 0, 0.012, 0.05, Math.pow(0.82, i));
                    t += len + 0.1 + 0.04 * r.uniform();
                }
                return new Made("Ente", 85 + lv, synth(ns, new Formant[]{new Formant(1100, 3.5, 1.6), new Formant(2300, 4, 1.0)}, 0.15, 0.55, 200, sr, seed));
            }
            case ERPEL -> {
                // Erpel: leises, raues „räb“, ein- oder zweimal
                int m = 1 + (int) (r.uniform() * 2);
                Note[] ns = new Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) { ns[i] = n(t, 0.14 + 0.05 * r.uniform(), 170, 185, 150, 0.4, 0.55, 45, 0.5, 0.01, 0.04, 1); t += 0.3; }
                return new Made("Erpel", 72 + lv, synth(ns, new Formant[]{new Formant(900, 3, 1.4), new Formant(1900, 4, 0.6)}, 0.2, 0.7, 120, sr, seed));
            }
            case SCHWALBE -> {
                // Rauchschwalbe: Zwitschern aus vielen kurzen Gleittönen zwischen 3 und 7 kHz, am Ende oft ein „zerr“
                int m = 10 + (int) (r.uniform() * 14);
                Note[] ns = new Note[m + 1];
                double t = 0;
                for (int i = 0; i < m; i++) {
                    double a = 3000 + 2500 * r.uniform(), b = 3200 + 3600 * r.uniform(), c = 2800 + 2600 * r.uniform(), len = 0.03 + 0.05 * r.uniform();
                    ns[i] = n(t, len, a, b, c, 0.2 + 0.6 * r.uniform(), 0.03, 0, 0, 0.004, 0.01, 0.6 + 0.4 * r.uniform());
                    t += len + 0.02 + 0.06 * r.uniform();
                }
                ns[m] = n(t + 0.05, 0.22, 4200, 4300, 4100, 0.5, 0.5, 120, 0.7, 0.01, 0.04, 0.7);
                return new Made("Schwalbe", 75 + lv, synth(ns, new Formant[]{new Formant(4500, 1.2, 0.8)}, 0.6, 1.2, 1500, sr, seed));
            }
            case KORMORAN -> {
                Note[] ns = {n(0, 0.28 + 0.1 * r.uniform(), 110, 125, 95, 0.3, 0.5, 35, 0.5, 0.02, 0.08, 1)};
                return new Made("Kormoran", 78 + lv, synth(ns, new Formant[]{new Formant(500, 3, 1.5), new Formant(1200, 4, 0.7)}, 0.2, 0.8, 70, sr, seed));
            }
            case SCHWAN -> {
                // Höckerschwan im Flug: das Singen der Schwingen, ein Wummern im Takt der Flügelschläge (2,6 je s)
                double len = 3.0;
                Note[] ns = {new Note(0, len, 900, 950, 880, 0.5, 0.7, 2.6, 0.9, 0, 0, 0.4, 0.6, 1)};
                return new Made("Schwan", 84 + lv, synth(ns, new Formant[]{new Formant(1200, 1.5, 1.5), new Formant(2600, 2, 0.5)}, 0.2, 1.0, 300, sr, seed));
            }
            case HUHN -> {
                if (s > 0.9) {
                    // Warnruf beim Weglaufen: schnelles, lautes „gak gak gak gaaak“
                    int m = 4 + (int) (r.uniform() * 4);
                    Note[] ns = new Note[m];
                    double t = 0;
                    for (int i = 0; i < m; i++) { double len = i == m - 1 ? 0.3 : 0.07; ns[i] = n(t, len, 480, 560, 470, 0.3, 0.25, 0, 0, 0.006, 0.03, 1); t += len + 0.06; }
                    return new Made("Huhn", 88, synth(ns, new Formant[]{new Formant(900, 3, 1.3), new Formant(2000, 3, 0.9)}, 0.25, 0.8, 200, sr, seed));
                }
                // Gackern: „gok gok gok“ und oft ein langes „gooaak“ am Ende
                int m = 3 + (int) (r.uniform() * 5);
                boolean tail = r.uniform() < 0.5;
                Note[] ns = new Note[m + (tail ? 1 : 0)];
                double t = 0, f = 320 + 70 * r.uniform();
                for (int i = 0; i < m; i++) { double len = 0.07 + 0.05 * r.uniform(); ns[i] = n(t, len, f, f * 1.08, f * 0.9, 0.3, 0.22, 0, 0, 0.006, 0.03, 0.8 + 0.2 * r.uniform()); t += len + 0.14 + 0.16 * r.uniform(); }
                if (tail) ns[m] = n(t, 0.42, f * 1.3, f * 1.75, f * 1.45, 0.4, 0.2, 0, 0, 0.02, 0.1, 1);
                return new Made("Huhn", 80 + lv, synth(ns, new Formant[]{new Formant(850, 3, 1.3), new Formant(1900, 3.5, 0.8)}, 0.25, 0.8, 180, sr, seed));
            }
            case HAHN -> {
                // „Kikeriki“: drei kurze Töne, dann ein langer mit Vibrato, rau, gut 2 s
                double f = 500 + 60 * r.uniform();
                Note[] ns = {
                        n(0, 0.17, f, f * 1.05, f, 0.4, 0.22, 0, 0, 0.01, 0.03, 0.8),
                        n(0.24, 0.16, f * 1.12, f * 1.2, f * 1.1, 0.4, 0.22, 0, 0, 0.01, 0.03, 0.85),
                        n(0.46, 0.22, f * 1.28, f * 1.35, f * 1.25, 0.4, 0.22, 0, 0, 0.01, 0.03, 0.9),
                        new Note(0.76, 1.05 + 0.3 * r.uniform(), f * 1.4, f * 1.5, f * 1.1, 0.25, 0.24, 0, 0, 6.5, 0.02, 0.03, 0.25, 1)};
                return new Made("Hahn", 100 + lv, synth(ns, new Formant[]{new Formant(1200, 2.5, 1.5), new Formant(2600, 3, 1.0)}, 0.25, 0.65, 250, sr, seed));
            }
            case ZIEGE -> {
                // Meckern „määäh“: ein oder zwei Töne mit starkem Zittern der Lautstärke (7 bis 10 Hz)
                int m = r.uniform() < 0.3 ? 2 : 1;
                Note[] ns = new Note[m];
                double t = 0, f = 260 + 120 * r.uniform();
                for (int i = 0; i < m; i++) { double len = 0.55 + 0.4 * r.uniform(); ns[i] = n(t, len, f * 0.95, f * 1.08, f * 0.85, 0.25, 0.14, 7 + 3 * r.uniform(), 0.75, 0.04, 0.15, 1); t += len + 0.35; }
                return new Made("Ziege", (s > 0.9 ? 92 : 88) + lv, synth(ns, new Formant[]{new Formant(750, 3, 1.5), new Formant(1800, 3, 0.9)}, 0.2, 0.75, 150, sr, seed));
            }
            case SCHWEIN -> {
                // Grunzen: ein bis drei kurze, tiefe, raue Stöße
                int m = 1 + (int) (r.uniform() * 3);
                Note[] ns = new Note[m];
                double t = 0, f = 95 + 35 * r.uniform();
                for (int i = 0; i < m; i++) { double len = 0.12 + 0.12 * r.uniform(); ns[i] = n(t, len, f, f * 1.1, f * 0.85, 0.3, 0.42, 28, 0.55, 0.012, 0.05, 1); t += len + 0.12 + 0.2 * r.uniform(); }
                return new Made("Schwein", 78 + lv, synth(ns, new Formant[]{new Formant(320, 2.5, 1.6), new Formant(950, 3, 0.9)}, 0.3, 0.9, 60, sr, seed));
            }
            case QUIEKEN -> {
                Note[] ns = {n(0, 0.55 + 0.4 * r.uniform(), 850, 1250, 800, 0.35, 0.3, 0, 0, 0.03, 0.1, 1)};
                return new Made("Schwein", 104 + lv, synth(ns, new Formant[]{new Formant(2000, 2, 1.4), new Formant(3600, 3, 0.6)}, 0.3, 0.7, 300, sr, seed));
            }
            case HUND -> {
                // Bellen eines spitzartigen Hundes: ein bis vier kurze „wau“, harter Einsatz
                int m = 1 + (int) (r.uniform() * (s > 0.7 ? 4 : 2));
                Note[] ns = new Note[m];
                double t = 0, f = 430 + 120 * r.uniform();
                for (int i = 0; i < m; i++) { double len = 0.12 + 0.06 * r.uniform(); ns[i] = n(t, len, f * 0.95, f * 1.3, f * 0.75, 0.25, 0.3, 0, 0, 0.004, 0.05, 1 - 0.08 * i); t += len + 0.22 + 0.2 * r.uniform(); }
                return new Made("Hund", 100 + lv, synth(ns, new Formant[]{new Formant(950, 2.5, 1.5), new Formant(2300, 3, 0.8)}, 0.25, 0.8, 150, sr, seed));
            }
            default -> throw new IllegalArgumentException(k.name());
        }
    }
}
