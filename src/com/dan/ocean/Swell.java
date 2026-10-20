package com.dan.ocean;

/**
 * Die Dünung: lange, regelmäßige Wellen von einem fernen Sturm, als Gerstner-Wellen (vier Züge, leicht gefächert).
 * Sie ist vom Wind getrennt regelbar und läuft auch bei Windstille. Gerstner-Wellen bewegen die Wasserteilchen auf
 * Kreisbahnen: Kämme werden spitz, Täler breit. Prozessor und Shader rechnen dieselbe Formel.
 */
public final class Swell {
    /** Je Zug: Richtung x, z (wohin er läuft), Wellenzahl k, Kreisfrequenz ω, Amplitude A, Steilheit Q, Phase φ₀. */
    public static final int STRIDE = 7, COUNT = 4;
    static final double[] DIR_OFF = {0, -9, 7, 15}, PERIOD = {1.0, 0.93, 1.06, 0.86}, WEIGHT = {1.0, 0.65, 0.5, 0.3}, PHASE = {0.0, 2.1, 4.4, 1.3};

    private Swell() { }

    /**
     * Die vier Züge: Höhe Hs (signifikant), Periode T, Richtung, aus der sie kommt (Grad), Wassertiefe.
     * Die Summe der Amplituden ist so gewählt, dass 4σ = Hs; die Steilheit bleibt unter dem Überschlag.
     */
    public static double[] components(double hs, double period, double fromDeg, double depth) {
        double[] c = new double[COUNT * STRIDE];
        if (!(hs > 0) || !(period > 0)) return c;
        double sw = 0;
        for (double w : WEIGHT) sw += w * w;
        double sigma = hs / 4, steepTotal = 0;
        for (int i = 0; i < COUNT; i++) {
            double to = Math.toRadians(fromDeg + 180 + DIR_OFF[i]);
            double w = 2 * Math.PI / (period * PERIOD[i]);
            double k = kFromOmega(w, depth);
            double a = WEIGHT[i] * sigma * Math.sqrt(2 / sw);
            int o = i * STRIDE;
            c[o] = Math.sin(to);
            c[o + 1] = -Math.cos(to);
            c[o + 2] = k;
            c[o + 3] = Spectrum.omegaQ(k, depth);
            c[o + 4] = a;
            c[o + 6] = PHASE[i];
            steepTotal += k * a;
        }
        double q = steepTotal > 0 ? Math.min(1.0, 0.55 / steepTotal) : 0;
        for (int i = 0; i < COUNT; i++) c[i * STRIDE + 5] = q;
        return c;
    }

    /** Wellenzahl zu ω (Newton auf ω² = g·k·tanh(k·d)). */
    static double kFromOmega(double w, double depth) {
        double k = w * w / Spectrum.G;
        for (int i = 0; i < 30; i++) {
            double f = Spectrum.omega(k, depth) - w, d = Spectrum.dOmegaDk(k, depth);
            if (d <= 0) break;
            double nk = k - f / d;
            if (Math.abs(nk - k) < 1e-12) { k = nk; break; }
            k = Math.max(1e-6, nk);
        }
        return k;
    }

    /**
     * Auslenkung am Ruhepunkt (x0, z0) zur Zeit t: out[0..2] = dx, dy, dz, out[3..5] = Geschwindigkeit,
     * out[6..7] = Steigungen dy/dx0, dy/dz0, out[8] = Jacobi-Anteil (∂dx/∂x0 + ∂dz/∂z0). Addiert auf out.
     */
    public static void add(double[] c, double x0, double z0, double t, double[] out) {
        for (int i = 0; i < COUNT; i++) {
            int o = i * STRIDE;
            double a = c[o + 4];
            if (a == 0) continue;
            double dx = c[o], dz = c[o + 1], k = c[o + 2], w = c[o + 3], q = c[o + 5];
            double ph = k * (dx * x0 + dz * z0) - w * t + c[o + 6];
            double s = Math.sin(ph), co = Math.cos(ph);
            out[0] += -q * a * dx * s;
            out[1] += a * co;
            out[2] += -q * a * dz * s;
            out[3] += q * a * dx * w * co;
            out[4] += a * w * s;
            out[5] += q * a * dz * w * co;
            out[6] += -a * k * dx * s;
            out[7] += -a * k * dz * s;
            out[8] += -q * a * k * co;
        }
    }
}
