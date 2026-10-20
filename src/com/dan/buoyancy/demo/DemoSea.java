package com.dan.buoyancy.demo;

import com.dan.buoyancy.Sea;

/**
 * Eine schlichte See für Demo und Prüfung: N lineare Wellenzüge um die Windrichtung, so dass die signifikante Höhe
 * stimmt (H_s = 4·σ), Periode nach Pierson-Moskowitz aus dem Wind; oder ein einzelner regelmäßiger Wellenzug. Dieselben
 * Züge rechnet sea.vert auf der Grafikkarte. Das echte Meer kommt in der App aus {@code com.dan.ocean}.
 */
public final class DemoSea implements Sea {
    public static final int N = 12;
    final double[] a = new double[N], kx = new double[N], kz = new double[N], w = new double[N], ph = new double[N];
    public double t, level;

    /** Seegang aus H_s, Wind und Richtung. */
    public DemoSea(double hs, double windSpeed, double windFromDeg, int seed) {
        java.util.Random r = new java.util.Random(seed);
        double tp = Math.max(2.5, Math.min(13, 0.73 * Math.max(3, windSpeed)));
        double wp = 2 * Math.PI / tp, sum = 0;
        double[] amp = new double[N];
        double to = Math.toRadians(windFromDeg + 180);
        for (int i = 0; i < N; i++) {
            double wi = wp * (0.75 + 0.9 * i / (N - 1));
            amp[i] = Math.sqrt(Math.exp(-1.25 * Math.pow(wp / wi, 4)) / Math.pow(wi, 5));
            sum += amp[i] * amp[i] / 2;
            double k = wi * wi / 9.81, dir = to + (r.nextDouble() - 0.5) * 1.0;
            kx[i] = k * Math.sin(dir);
            kz[i] = -k * Math.cos(dir);
            w[i] = wi;
            ph[i] = r.nextDouble() * 2 * Math.PI;
        }
        double f = sum > 0 ? hs / 4 / Math.sqrt(sum) : 0;
        for (int i = 0; i < N; i++) a[i] = amp[i] * f;
    }

    /** Ein regelmäßiger Wellenzug: Höhe (Wellental bis -berg), Periode, laufend nach Richtung (Grad). */
    public static DemoSea regular(double height, double period, double towardDeg) {
        DemoSea s = new DemoSea(0, 5, 0, 1);
        java.util.Arrays.fill(s.a, 0);
        double wi = 2 * Math.PI / period, k = wi * wi / 9.81, d = Math.toRadians(towardDeg);
        s.a[0] = height / 2;
        s.kx[0] = k * Math.sin(d);
        s.kz[0] = -k * Math.cos(d);
        s.w[0] = wi;
        s.ph[0] = 0;
        return s;
    }

    @Override public double height(double x, double z) {
        double h = level;
        for (int i = 0; i < N; i++) if (a[i] != 0) h += a[i] * Math.cos(kx[i] * x + kz[i] * z - w[i] * t + ph[i]);
        return h;
    }

    @Override public double[] velocity(double x, double z) {
        double vx = 0, vy = 0, vz = 0;
        for (int i = 0; i < N; i++) {
            if (a[i] == 0) continue;
            double th = kx[i] * x + kz[i] * z - w[i] * t + ph[i], k = Math.hypot(kx[i], kz[i]);
            double c = a[i] * w[i] * Math.cos(th);
            vx += c * kx[i] / k;
            vz += c * kz[i] / k;
            vy += a[i] * w[i] * Math.sin(th);
        }
        return new double[]{vx, vy, vz};
    }

    @Override public double level() { return level; }

    /** Signifikante Höhe (4·σ). */
    public double hs() {
        double s = 0;
        for (double v : a) s += v * v / 2;
        return 4 * Math.sqrt(s);
    }

    /** Für den Shader: je Zug (Amplitude, kx, kz, ω) und (Phase). */
    public float[] waves() {
        float[] f = new float[N * 4];
        for (int i = 0; i < N; i++) { f[i * 4] = (float) a[i]; f[i * 4 + 1] = (float) kx[i]; f[i * 4 + 2] = (float) kz[i]; f[i * 4 + 3] = (float) w[i]; }
        return f;
    }

    public float[] phases() {
        float[] f = new float[N];
        for (int i = 0; i < N; i++) f[i] = (float) ph[i];
        return f;
    }
}
