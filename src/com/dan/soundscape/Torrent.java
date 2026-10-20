package com.dan.soundscape;

/**
 * Seit Glacier Phase 13: Wildwasser – ein Durchfluss, der tost (ein Dammbruch, die Ruptura, ein Wasserfall). Ort der
 * lautesten Stelle, Durchfluss (m³/s). Pegel in 1 m nach dem Durchfluss: 70 + 12 · lg(Q) dB [G] (8 000 m³/s: 117 dB,
 * in 150 m also rund 74 dB). Drei Teile: dumpfes Rollen (Braunrauschen unter 300 Hz), das Rauschen des Wassers
 * (Rosarauschen um 1 kHz), das Zischen der Gischt darüber; dazu ab und zu ein Schlag, wenn Eis oder Steine
 * aneinanderstoßen. Die App setzt Ort und Durchfluss je Bild.
 */
public final class Torrent {
    public final String name;
    public volatile double x, y, z, flow;

    public Torrent(String name, double x, double y, double z) { this.name = name; this.x = x; this.y = y; this.z = z; }

    /** Pegel in 1 m (dB) bei diesem Durchfluss. */
    public static double levelDb(double flow) { return flow <= 0.1 ? 0 : 70 + 12 * Math.log10(flow); }

    // Zustand (Faden des Mischers)
    final Soundscape.Pings knocks = new Soundscape.Pings();
    private Dsp.Brown roll;
    private Dsp.Pink rush, hiss;
    private final Dsp.Biquad rollLp = new Dsp.Biquad(), rushBp = new Dsp.Biquad(), hissHp = new Dsp.Biquad();
    private double swell;
    private Dsp.Rng rng;
    private int builtSr;
    private final float[] sL = new float[Soundscape.BLOCK], sR = new float[Soundscape.BLOCK];

    /** Addiert in l, r mit den Verstärkungen je Seite; d Abstand (m), Grenze der Luft; gibt die Energie zurück. */
    double render(float[] l, float[] r, int n, int sr, long seed, float gl, float gr, double d, double airHz) {
        if (builtSr != sr) {
            rng = new Dsp.Rng(seed * 6007 + name.hashCode());
            roll = new Dsp.Brown(seed * 7 + name.hashCode());
            rush = new Dsp.Pink(seed * 11 + name.hashCode());
            hiss = new Dsp.Pink(seed * 13 + name.hashCode());
            rollLp.lowpass(300, 0.7, sr);
            rushBp.bandpass(1000, 0.6, sr);
            hissHp.highpass(3500, 0.7, sr);
            knocks.clear();
            builtSr = sr;
        }
        double q = Math.max(0, flow), dt = n / (double) sr;
        if (q < 0.5) { knocks.render(l, r, 0, sr); return 0; }
        double lv = levelDb(q) - 20 * Math.log10(Math.max(1, d)) - 0.004 * d;
        swell += -swell * dt / 1.5 + 0.35 * Math.sqrt(2 * dt / 1.5) * rng.gauss();
        float aRoll = (float) (Dsp.amp(lv + 2) * Math.exp(0.3 * swell)), aRush = (float) (Dsp.amp(lv - 3) * 0.9);
        // die Gischt hört man nur nah (die Luft nimmt die Höhen)
        float aHiss = (float) (Dsp.amp(lv - 8) * Math.min(1, airHz / 6000));
        double knock = Dsp.amp(lv - 2) * 1.6;
        knocks.spawnPan(0.4 + q / 2500, dt, 120, 480, 0.03, 0.09, knock, 0.7071f, 0.7071f, rng, sr);
        java.util.Arrays.fill(sL, 0, n, 0);
        java.util.Arrays.fill(sR, 0, n, 0);
        double e = knocks.render(sL, sR, n, sr) * 0.5;
        for (int i = 0; i < n; i++) {
            float y = rollLp.process(roll.next()) * 1.6f * aRoll + rushBp.process(rush.next()) * aRush + hissHp.process(hiss.next()) * aHiss;
            float k = (sL[i] + sR[i]) * 0.7071f;
            l[i] += (y + k) * gl;
            r[i] += (y + k) * gr;
            e += y * y;
        }
        return e;
    }
}
