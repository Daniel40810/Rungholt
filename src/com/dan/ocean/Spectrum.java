package com.dan.ocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Das Spektrum der Windsee: JONSWAP in der Frequenz, Richtungsverteilung nach Donelan-Banner, verteilt auf mehrere
 * Kacheln (Kaskaden) verschiedener Größe. Jede Kaskade hält die Startamplituden h₀(k) ihres Wellenzahlbands; die
 * Grafikkarte und der Prozessor rechnen aus denselben Zahlen, darum passt {@code heightAt} zum Bild.
 * <p>
 * Die Gesamtvarianz wird so skaliert, dass die signifikante Wellenhöhe H<sub>s</sub> = 4σ der Vorgabe folgt.
 * Die Spitzenperiode kommt aus Wind und Streichlänge (JONSWAP), höchstens so lang wie bei ausgereifter See
 * (Pierson-Moskowitz).
 */
public final class Spectrum {
    public static final double G = 9.81;
    /** Wiederholzeit: alle Kreisfrequenzen sind Vielfache von 2π/T, so läuft die See ohne Sprung in Schleife. */
    public static final double REPEAT = 1000;
    static final double OMEGA0 = 2 * Math.PI / REPEAT;
    /** Größenverhältnis benachbarter Kaskaden und das Band: die feinere Kaskade beginnt bei ihrer 6. Oberwelle. */
    public static final double RATIO = 6.85, BAND_START = 6;

    /** Eine Kachel: Kantenlänge L in m, Auflösung n, Band [kMin, kMax), Startamplituden, Schaumschwelle. */
    public static final class Cascade {
        public final int index, n;
        public final double length, kMin, kMax;
        /**
         * Je Texel vier Werte: h₀(k) re, im und conj(h₀(−k)) re, im; Index (z·n + x)·4. Texel (x, z) gehört zur
         * Wellenzahl k = 2π/L · (x', z') mit x' = x &lt; n/2 ? x : x − n.
         */
        public final float[] h0;
        /** Erwartete Höhenvarianz dieser Kaskade in m². */
        public final double variance;
        /** Jacobi-Schwelle: darunter entsteht Schaum (aus der gewünschten Bedeckung bestimmt). */
        public double foamThreshold = -10;

        Cascade(int index, int n, double length, double kMin, double kMax, float[] h0, double variance) {
            this.index = index;
            this.n = n;
            this.length = length;
            this.kMin = kMin;
            this.kMax = kMax;
            this.h0 = h0;
            this.variance = variance;
        }

        public int wave(int i) { return i < n / 2 ? i : i - n; }
    }

    /** Kreisfrequenz nach der Dispersionsbeziehung ω² = g·k·tanh(k·d). */
    public static double omega(double k, double depth) {
        double kd = k * depth;
        return Math.sqrt(G * k * (kd > 20 ? 1 : Math.tanh(kd)));
    }

    /** Auf Vielfache von 2π/T gerundete Kreisfrequenz (dieselbe Rundung im Shader). */
    public static double omegaQ(double k, double depth) {
        return Math.floor(omega(k, depth) / OMEGA0) * OMEGA0;
    }

    /** Gruppengeschwindigkeit dω/dk. */
    static double dOmegaDk(double k, double depth) {
        double kd = Math.min(20, k * depth), th = Math.tanh(kd), w = omega(k, depth);
        if (w < 1e-9) return 0;
        double sech2 = 1 - th * th;
        return G * (th + kd * sech2) / (2 * w);
    }

    /** Spitzenkreisfrequenz aus Wind (m/s) und Streichlänge (m), nicht länger als bei ausgereifter See, Periode ≥ 1,2 s. */
    public static double peakOmega(double wind, double fetch) {
        wind = Math.max(0.3, wind);
        double jonswap = 22 * Math.cbrt(G * G / (wind * fetch));
        double pm = 0.855 * G / wind;
        return Math.min(2 * Math.PI / 1.2, Math.max(jonswap, pm));
    }

    /** JONSWAP ohne Vorfaktor (der wird über H_s skaliert), γ = 3,3. */
    static double jonswap(double w, double wp) {
        if (w <= 1e-6) return 0;
        double sigma = w <= wp ? 0.07 : 0.09;
        double r = Math.exp(-(w - wp) * (w - wp) / (2 * sigma * sigma * wp * wp));
        return Math.pow(w, -5) * Math.exp(-1.25 * Math.pow(wp / w, 4)) * Math.pow(3.3, r);
    }

    /** Richtungsverteilung nach Donelan-Banner, θ relativ zur Windrichtung, Integral über −π..π = 1. */
    static double spreading(double theta, double w, double wp) {
        double r = w / wp, beta;
        if (r < 0.95) beta = 2.61 * Math.pow(Math.max(r, 0.56), 1.3);
        else if (r < 1.6) beta = 2.28 * Math.pow(r, -1.3);
        else beta = Math.pow(10, -0.4 + 0.8393 * Math.exp(-0.567 * Math.log(r * r)));
        double c = Math.cosh(beta * theta);
        return beta / (2 * Math.tanh(beta * Math.PI)) / (c * c);
    }

    /**
     * Baut die Kaskaden. windTo: Richtung, in die der Wind weht (Bogenmaß von Nord im Uhrzeigersinn).
     * targetHs: gewünschte signifikante Höhe der Windsee in m.
     */
    public static List<Cascade> build(double wind, double windTo, double fetch, double depth, double targetHs,
                                      int n, int count, long seed) {
        double wp = peakOmega(wind, fetch);
        double kp = wp * wp / G;
        double lp = 2 * Math.PI / kp;
        double l0 = Math.max(24, Math.min(1200, 5 * lp));
        // Windrichtung als Vektor der Szene: x Ost, z Süd, Nord = −z
        double wx = Math.sin(windTo), wz = -Math.cos(windTo);
        List<double[]> s = new ArrayList<>();
        double[] lengths = new double[count], kLo = new double[count], kHi = new double[count];
        for (int c = 0; c < count; c++) lengths[c] = l0 / Math.pow(RATIO, c);
        for (int c = 0; c < count; c++) {
            kLo[c] = c == 0 ? 0 : BAND_START * 2 * Math.PI / lengths[c];
            kHi[c] = c == count - 1 ? Math.PI * n / lengths[c] * 0.95 : BAND_START * 2 * Math.PI / lengths[c + 1];
        }
        // Rohspektrum je Kaskade sammeln, Gesamtvarianz für die Skalierung
        double total = 0;
        for (int c = 0; c < count; c++) {
            double dk = 2 * Math.PI / lengths[c];
            double[] dens = new double[n * n];
            for (int iz = 0; iz < n; iz++)
                for (int ix = 0; ix < n; ix++) {
                    int mx = ix < n / 2 ? ix : ix - n, mz = iz < n / 2 ? iz : iz - n;
                    if (mx == -n / 2 || mz == -n / 2) continue;
                    double kx = mx * dk, kz = mz * dk, k = Math.hypot(kx, kz);
                    if (k < 1e-9 || k < kLo[c] || k >= kHi[c]) continue;
                    double w = omega(k, depth);
                    double th = Math.atan2(kx * wz - kz * wx, kx * wx + kz * wz);
                    // Dichte in der k-Ebene: S(ω)·dω/dk / k · D(θ)
                    double d = jonswap(w, wp) * dOmegaDk(k, depth) / k * spreading(th, w, wp) * dk * dk;
                    dens[iz * n + ix] = d;
                    total += d;
                }
            s.add(dens);
        }
        double scale = total > 0 && targetHs > 0 ? (targetHs / 4) * (targetHs / 4) / total : 0;
        List<Cascade> out = new ArrayList<>();
        for (int c = 0; c < count; c++) {
            Random rnd = new Random(seed * 1_000_003L + c * 7919L);
            double[] dens = s.get(c);
            double[] re = new double[n * n], im = new double[n * n];
            double var = 0;
            for (int i = 0; i < n * n; i++) {
                // h₀ = a·(ξr + i·ξi)/√2 mit a² = S·Δk²/2: E|h₀|² = a², und die Höhe hat Varianz Σ 2·a² = Σ S·Δk²
                double a = Math.sqrt(dens[i] * scale / 2);
                double gr = rnd.nextGaussian(), gi = rnd.nextGaussian();
                re[i] = a * gr / Math.sqrt(2);
                im[i] = a * gi / Math.sqrt(2);
                var += dens[i] * scale;
            }
            float[] h0 = new float[n * n * 4];
            for (int iz = 0; iz < n; iz++)
                for (int ix = 0; ix < n; ix++) {
                    int i = iz * n + ix, j = ((n - iz) % n) * n + (n - ix) % n;
                    h0[i * 4] = (float) re[i];
                    h0[i * 4 + 1] = (float) im[i];
                    h0[i * 4 + 2] = (float) re[j];
                    h0[i * 4 + 3] = (float) -im[j];
                }
            out.add(new Cascade(c, n, lengths[c], kLo[c], kHi[c], h0, var));
        }
        // Diese eine Ziehung genau auf H_s bringen: die räumliche Varianz ist Σ 2·|h₀|² (Parseval, im Zeitmittel)
        double realized = 0;
        for (Cascade c : out) for (int i = 0; i < c.h0.length; i += 4) realized += 2 * ((double) c.h0[i] * c.h0[i] + (double) c.h0[i + 1] * c.h0[i + 1]);
        if (realized > 0 && targetHs > 0) {
            float f = (float) Math.sqrt((targetHs / 4) * (targetHs / 4) / realized);
            for (Cascade c : out) for (int i = 0; i < c.h0.length; i++) c.h0[i] *= f;
        }
        return out;
    }
}
