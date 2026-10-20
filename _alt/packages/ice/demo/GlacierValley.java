package com.dan.ice.demo;

import com.dan.ice.Glacier;

/**
 * Ein ausgedachtes Tal für die Demo des Gletschers (ohne Höhendaten, ohne App): eine Zunge, die durch ein leicht
 * geschwungenes Tal nach +x fließt, mit einer Stufe (Eisfall) bei x = −3 km und einer Front bei x ≈ 0 in einen See.
 * Raster 10 m, 6,5 × 3,6 km. Wasserspiegel y = 0, die Front steht 60 m hoch.
 */
public final class GlacierValley {
    static final double X0 = -5200, Z0 = -1800, CELL = 10;
    static final int NX = 680, NZ = 360;

    private GlacierValley() { }

    public static double center(double x) { return 260 * Math.sin(x / 1400) + 120 * Math.sin(x / 520 + 1); }
    static double halfWidth(double x) { return 620 + 0.05 * Math.max(0, -x) + 60 * Math.sin(x / 800); }

    /** Oberfläche des Eises über dem Wasser: steigt talauf, Eisfall bei −3 km. */
    static double iceSurface(double x) {
        return 55 + 0.025 * Math.max(0, -x) + 110 * smooth(-2800, -3250, x);
    }

    /** Lage der Front (x) bei z: leicht nach außen gewölbt. */
    static double frontX(double z) { return 120 - 0.00012 * z * z; }

    public static Glacier.Grid grid() {
        float[] h = new float[NX * NZ], bed = new float[NX * NZ];
        byte[] k = new byte[NX * NZ];
        for (int j = 0; j < NZ; j++)
            for (int i = 0; i < NX; i++) {
                double x = X0 + (i + 0.5) * CELL, z = Z0 + (j + 0.5) * CELL;
                double dz = Math.abs(z - center(x)), w = halfWidth(x);
                double out = dz - w;   // > 0: Talflanke
                double ridge = 40 + 0.9 * Math.max(0, out) + 300 * smooth(0, 900, out) + 80 * Math.sin(x / 300 + z / 410) * smooth(50, 400, out);
                int q = j * NX + i;
                double fx = frontX(z - center(x));
                if (out > 0) {
                    h[q] = (float) Math.max(ridge, iceSurface(Math.min(x, fx)) * 0.3 + 20 + out * 0.6);
                    k[q] = Glacier.ROCK;
                    bed[q] = h[q];
                } else if (x < fx) {
                    double s = iceSurface(x) - 18 * Math.pow(dz / w, 4);
                    h[q] = (float) s;
                    bed[q] = (float) (s - 160 - 120 * (1 - (dz / w) * (dz / w)));
                    k[q] = Glacier.ICE;
                } else {
                    double depth = 120 * (1 - Math.pow(dz / w, 2)) * smooth(fx - 50, fx + 400, x) + 15;
                    h[q] = (float) -depth;
                    bed[q] = h[q];
                    k[q] = Glacier.WATER;
                }
            }
        return new Glacier.Grid(NX, NZ, X0, Z0, CELL, h, bed, k);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
