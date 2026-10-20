package com.dan.spray.demo;

import com.dan.spray.Sea;

/**
 * Eine schlichte See für Demo und Prüfung: zwölf lineare Wellenzüge um die Windrichtung, Amplituden so, dass die
 * signifikante Höhe stimmt (H_s = 4·σ), Periode nach Pierson-Moskowitz aus dem Wind. Höhe und Geschwindigkeit an der
 * Oberfläche nach der linearen Theorie. Das echte Meer kommt in der App aus {@code com.dan.ocean}.
 */
public final class SineSea implements Sea {
    static final int N = 12;
    final double[] a = new double[N], kx = new double[N], kz = new double[N], w = new double[N], ph = new double[N];
    public double t, level;

    public SineSea(double hs, double windSpeed, double windFromDeg, int seed) {
        java.util.Random r = new java.util.Random(seed);
        double tp = Math.max(2.5, Math.min(13, 0.73 * Math.max(3, windSpeed)));
        double wp = 2 * Math.PI / tp, sum = 0;
        double[] amp = new double[N];
        double to = Math.toRadians(windFromDeg + 180);
        for (int i = 0; i < N; i++) {
            double wi = wp * (0.75 + 0.9 * i / (N - 1));
            double s = Math.exp(-1.25 * Math.pow(wp / wi, 4)) / Math.pow(wi, 5);
            amp[i] = Math.sqrt(s);
            sum += amp[i] * amp[i];
            double k = wi * wi / 9.81, dir = to + (r.nextDouble() - 0.5) * 1.2;
            kx[i] = k * Math.sin(dir);
            kz[i] = -k * Math.cos(dir);
            w[i] = wi;
            ph[i] = r.nextDouble() * 2 * Math.PI;
        }
        double sigma = hs / 4;
        for (int i = 0; i < N; i++) a[i] = amp[i] / Math.sqrt(sum / 2) * sigma / Math.sqrt(2) * Math.sqrt(2);
        // Σ a²/2 = σ²
        double check = 0;
        for (int i = 0; i < N; i++) check += a[i] * a[i] / 2;
        double f = check > 0 ? sigma / Math.sqrt(check) : 0;
        for (int i = 0; i < N; i++) a[i] *= f;
    }

    @Override public double height(double x, double z) {
        double h = level;
        for (int i = 0; i < N; i++) h += a[i] * Math.cos(kx[i] * x + kz[i] * z - w[i] * t + ph[i]);
        return h;
    }

    @Override public double[] velocity(double x, double z) {
        double vx = 0, vy = 0, vz = 0;
        for (int i = 0; i < N; i++) {
            double th = kx[i] * x + kz[i] * z - w[i] * t + ph[i], k = Math.hypot(kx[i], kz[i]);
            double c = a[i] * w[i] * Math.cos(th);
            vx += c * kx[i] / k;
            vz += c * kz[i] / k;
            vy += a[i] * w[i] * Math.sin(th);
        }
        return new double[]{vx, vy, vz};
    }

    @Override public double level() { return level; }

    /** Signifikante Höhe aus den Amplituden (4·σ). */
    public double hs() {
        double s = 0;
        for (double v : a) s += v * v / 2;
        return 4 * Math.sqrt(s);
    }
}
