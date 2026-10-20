package com.dan.soundscape;

/**
 * Seit Rungholt Phase 14: fließendes Wasser in einem Priel oder Bach – ein Rauschen mit Glucksen an der Kante, das mit
 * der Strömung lauter und heller wird. Ort (die nächste Stelle der Kante), Geschwindigkeit (m/s), Breite (m). Pegel
 * in 1 m: 52 + 22 · lg(v / 0,5) + 5 · lg(Breite / 10) dB [G] (ein Priel von 30 m bei 1 m/s: rund 61 dB an der Kante).
 * Die App setzt Ort, Strömung und Breite je Bild ({@code soundscape.streams}).
 */
public final class Stream {
    public final String name;
    public volatile double x, y, z, speed, width = 10;

    public Stream(String name) { this.name = name; }

    public static double levelDb(double speed, double width) {
        if (speed < 0.03) return 0;
        return 52 + 22 * Math.log10(speed / 0.5) + 5 * Math.log10(Math.max(1, width) / 10);
    }

    final Soundscape.Pings gurgle = new Soundscape.Pings();
    private Dsp.Pink rush;
    private Dsp.Brown low;
    private final Dsp.Biquad bp = new Dsp.Biquad(), lp = new Dsp.Biquad();
    private Dsp.Rng rng;
    private int builtSr;
    private double swell;
    private final float[] sL = new float[Soundscape.BLOCK], sR = new float[Soundscape.BLOCK];

    double render(float[] l, float[] r, int n, int sr, long seed, float gl, float gr, double d, double airHz) {
        if (builtSr != sr) {
            rng = new Dsp.Rng(seed * 4099 + name.hashCode());
            rush = new Dsp.Pink(seed * 17 + name.hashCode());
            low = new Dsp.Brown(seed * 19 + name.hashCode());
            lp.lowpass(250, 0.7, sr);
            gurgle.clear();
            builtSr = sr;
        }
        double v = Math.max(0, speed), dt = n / (double) sr;
        double lv = levelDb(v, width);
        if (lv <= 0) { gurgle.render(l, r, 0, sr); return 0; }
        lv -= 20 * Math.log10(Math.max(1, d)) + 0.004 * d;
        swell += -swell * dt / 0.8 + 0.4 * Math.sqrt(2 * dt / 0.8) * rng.gauss();
        bp.bandpass(Math.min(airHz, 500 + 900 * Math.min(2, v)), 0.6, sr);
        float a = (float) (Dsp.amp(lv) * 1.4 * Math.exp(0.25 * swell)), al = (float) (Dsp.amp(lv - 4) * 0.8);
        // Glucksen: Blasen und Wirbel an der Kante, umso mehr, je schneller
        java.util.Arrays.fill(sL, 0, n, 0);
        java.util.Arrays.fill(sR, 0, n, 0);
        gurgle.spawnPan(4 + 18 * Math.min(2, v), dt, 280, Math.min(airHz, 1400), 0.01, 0.035, Dsp.amp(lv + 2) * 2.2, 0.7071f, 0.7071f, rng, sr);
        double e = gurgle.render(sL, sR, n, sr) * 0.5;
        for (int i = 0; i < n; i++) {
            float y = bp.process(rush.next()) * a + lp.process(low.next()) * al + (sL[i] + sR[i]) * 0.7071f;
            l[i] += y * gl;
            r[i] += y * gr;
            e += y * y;
        }
        return e;
    }
}
