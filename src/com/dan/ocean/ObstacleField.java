package com.dan.ocean;

/**
 * Seit Haithabu Phase 8: viele feste Körper auf einmal (Pfähle von Landebrücken, eine Pfahlreihe im Wasser) als Raster
 * des Abstands zum nächsten Körper (m, Zelle cell, Ecke x0, z0, Nord = −z wie die Szene). Um jeden legt sich derselbe
 * Brandungsring wie um ein {@link Obstacle}; anders als dort bleibt das Wasser zwischen den Pfählen stehen.
 */
public record ObstacleField(float[] dist, int w, int h, double x0, double z0, double cell) {
    public ObstacleField {
        if (dist.length != w * h) throw new IllegalArgumentException("Raster " + w + " × " + h + " passt nicht zu " + dist.length + " Werten");
    }

    /** Abstand an (x, z), bilinear; außerhalb des Rasters weit weg. */
    public double distance(double x, double z) {
        double u = (x - x0) / cell - 0.5, v = (z - z0) / cell - 0.5;
        if (u < 0 || v < 0 || u > w - 1 || v > h - 1) return 1e9;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        int i1 = Math.min(w - 1, i + 1), j1 = Math.min(h - 1, j + 1);
        double fx = u - i, fz = v - j;
        double a = dist[j * w + i], b = dist[j * w + i1], c = dist[j1 * w + i], d = dist[j1 * w + i1];
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    /**
     * Baut das Raster aus Kreisen {x, z, Halbmesser} über dem Rechteck (x0, z0, Breite, Tiefe), Abstand höchstens
     * maxDist (weiter weg zählt nicht für den Ring).
     */
    public static ObstacleField of(java.util.List<double[]> circles, double x0, double z0, double width, double depth, double cell, double maxDist) {
        int w = (int) Math.ceil(width / cell), h = (int) Math.ceil(depth / cell);
        float[] d = new float[w * h];
        java.util.Arrays.fill(d, (float) maxDist);
        for (double[] c : circles) {
            double r = c[2] + maxDist;
            int i0 = (int) Math.floor((c[0] - r - x0) / cell), i1 = (int) Math.ceil((c[0] + r - x0) / cell);
            int j0 = (int) Math.floor((c[1] - r - z0) / cell), j1 = (int) Math.ceil((c[1] + r - z0) / cell);
            for (int j = Math.max(0, j0); j <= Math.min(h - 1, j1); j++)
                for (int i = Math.max(0, i0); i <= Math.min(w - 1, i1); i++) {
                    double px = x0 + (i + 0.5) * cell, pz = z0 + (j + 0.5) * cell;
                    float v = (float) Math.max(0, Math.hypot(px - c[0], pz - c[1]) - c[2]);
                    if (v < d[j * w + i]) d[j * w + i] = v;
                }
        }
        return new ObstacleField(d, w, h, x0, z0, cell);
    }
}
