package com.dan.soundscape;

/**
 * Donner aus dem Kanal eines Blitzes: Jedes Stück des Kanals sendet eine N-Welle aus (Few 1969), die je nach Abstand
 * später ankommt. Ihre Summe rollt so lange, wie die Abstände auseinanderliegen. Fern wird die N-Welle länger (die
 * Stoßwelle läuft auseinander) und die Luft nimmt die Höhen (Tiefpass nach Abstand); nah bleibt ein Knall. Die Schläge
 * eines Blitzes wiederholen das im Abstand ihrer Zeiten, schwächer. Dazu zwei Echos von Wolke und See.
 */
public final class Thunder {
    private Thunder() { }

    /**
     * Rechnet den Donner als Monosignal. distances: Abstand jedes Kanalstücks zum Ohr (m), weights: Gewicht (Länge,
     * Hauptkanal stärker), strokes: Zeiten der Schläge ab dem ersten (s). Ergebnis: Effektivwert der lautesten
     * Zehntelsekunde = 1 (der Pegel am Ohr kommt vom Aufrufer).
     */
    public static float[] build(double sr, double[] distances, double[] weights, double[] strokes, double c, long seed) {
        int n = Math.min(distances.length, weights.length);
        double dmin = Double.MAX_VALUE, dmax = 0;
        for (int i = 0; i < n; i++) { dmin = Math.min(dmin, distances[i]); dmax = Math.max(dmax, distances[i]); }
        if (n == 0) { dmin = 1000; dmax = 1000; }
        double last = strokes == null || strokes.length == 0 ? 0 : strokes[strokes.length - 1];
        int len = (int) ((Math.min(25, (dmax - dmin) / c) + last + 3.5) * sr);
        float[] d = new float[len];
        Dsp.Rng r = new Dsp.Rng(seed);
        double[] st = strokes == null || strokes.length == 0 ? new double[]{0} : strokes;
        for (int s = 0; s < st.length; s++) {
            double gs = Math.pow(0.75, s);
            for (int i = 0; i < n; i++) {
                double dist = distances[i];
                double t = st[s] - st[0] + (dist - dmin) / c + 0.002 * r.gauss();
                double tau = 0.0025 * (1 + dist / 1200) * (0.7 + 0.6 * r.uniform());
                int i0 = (int) (t * sr), m = Math.max(2, (int) (tau * sr));
                if (i0 < 0 || i0 + m >= len) continue;
                double a = gs * weights[i] / Math.max(50, dist) * (r.uniform() < 0.5 ? 1 : -1);
                for (int k = 0; k < m; k++) d[i0 + k] += (float) (a * (1 - 2.0 * k / (m - 1)));
            }
        }
        // die Luft nimmt die Höhen: Tiefpass nach dem kürzesten Abstand, zweimal
        double fc = 5000 / (1 + dmin / 500);
        Dsp.Biquad a1 = new Dsp.Biquad().lowpass(Math.max(120, fc), 0.6, sr), a2 = new Dsp.Biquad().lowpass(Math.max(120, fc * 1.4), 0.6, sr);
        Dsp.Biquad lowBoost = new Dsp.Biquad().peaking(55, 0.9, 6, sr);
        for (int i = 0; i < len; i++) d[i] = lowBoost.process(a2.process(a1.process(d[i])));
        // Echos von der Wolkenuntergrenze und der See, weicher
        float[] e = d.clone();
        int e1 = (int) (0.33 * sr), e2 = (int) (0.95 * sr);
        Dsp.OnePole s1 = new Dsp.OnePole().cutoff(500, sr), s2 = new Dsp.OnePole().cutoff(250, sr);
        for (int i = 0; i < len; i++) {
            if (i >= e1) d[i] += 0.45f * s1.process(e[i - e1]);
            if (i >= e2) d[i] += 0.3f * s2.process(e[i - e2]);
        }
        // Ein- und Ausblenden, auf die lauteste Zehntelsekunde normieren
        int w = (int) (0.1 * sr);
        double best = 1e-12, acc = 0;
        for (int i = 0; i < len; i++) {
            acc += d[i] * d[i];
            if (i >= w) acc -= d[i - w] * d[i - w];
            best = Math.max(best, acc / w);
        }
        float g = (float) (1 / Math.sqrt(best));
        int fade = (int) (1.5 * sr);
        for (int i = 0; i < len; i++) d[i] *= g * (i > len - fade ? (float) (len - i) / fade : 1);
        return d;
    }
}
