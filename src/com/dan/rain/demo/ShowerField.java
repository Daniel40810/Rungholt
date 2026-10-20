package com.dan.rain.demo;

import com.dan.rain.Rain;

import java.util.Random;

/**
 * Schauer für die Demo, ohne Wolken-Package: einige runde Regenzellen (1 bis 3 km), die mit dem Wind ziehen. Mit
 * {@link #singleAt} nur eine Zelle, die auf einer Linie durch den Ursprung zieht (für Standbilder eines Durchzugs).
 */
final class ShowerField implements Rain.Curtain {
    private final double[] cx, cz, r;
    volatile double windX = 10, windZ = 0, time;

    ShowerField(int seed, int n, double area) {
        Random rnd = new Random(seed);
        cx = new double[n];
        cz = new double[n];
        r = new double[n];
        for (int i = 0; i < n; i++) {
            cx[i] = (rnd.nextDouble() - 0.5) * area;
            cz[i] = (rnd.nextDouble() - 0.5) * area;
            r[i] = 1000 + 2000 * rnd.nextDouble();
        }
    }

    /** Eine Zelle mit Halbmesser radius, die zur Zeit t bei (x, z) steht und mit (wx, wz) m/s zieht. */
    static ShowerField singleAt(double x, double z, double radius, double wx, double wz, double t) {
        ShowerField f = new ShowerField(new double[]{x - wx * t}, new double[]{z - wz * t}, new double[]{radius});
        f.windX = wx;
        f.windZ = wz;
        f.time = t;
        return f;
    }

    private ShowerField(double[] x, double[] z, double[] rr) { cx = x; cz = z; r = rr; }

    @Override
    public double density(double x, double z) {
        double sum = 0;
        for (int i = 0; i < cx.length; i++) {
            double dx = x - cx[i] - windX * time, dz = z - cz[i] - windZ * time;
            double q = (dx * dx + dz * dz) / (r[i] * r[i]);
            if (q < 4) sum += Math.exp(-q * 2.2);
        }
        // weicher Rand, dichter Kern
        return Math.min(1, sum * 1.3);
    }
}
