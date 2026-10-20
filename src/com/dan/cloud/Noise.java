package com.dan.cloud;

import java.util.stream.IntStream;

/**
 * Kachelbares Rauschen für die Wolken, auf dem Prozessor gerechnet und als Bytes abgelegt (dieselben Bytes gehen auf
 * die Grafikkarte; so rechnen {@link Clouds#densityAt} und der Shader mit denselben Zahlen).
 * <ul>
 * <li>Grundform 128³ RGBA: R Perlin-Worley (weiche Haufen mit Zellstruktur), G/B/A Worley-fbm mit 4, 8 und 16 Zellen
 * (Schneider, „The Real-Time Volumetric Cloudscapes of Horizon Zero Dawn“, 2015).</li>
 * <li>Feinheit 32³ RGBA: Worley-fbm mit 2, 4, 8 Zellen (frisst die Ränder an: Fransen unten, Blumenkohl oben).</li>
 * <li>Wetterkarte 512² RGBA: R Zellen (Haufen, in Gruppen mit Lücken), G große Flecken (Schichtwolken), B Feinverteilung (Regen),
 * A die hohe Schicht.</li>
 * </ul>
 * Alles ist in allen Richtungen periodisch und mit festem Startwert reproduzierbar.
 */
public final class Noise {
    public static final int BASE = 128, DETAIL = 32, WEATHER = 512;
    private static volatile byte[] base, detail, weather;

    private Noise() { }

    /** Grundform BASE³ × RGBA (x schnell, dann y, dann z). */
    public static byte[] base() {
        byte[] b = base;
        if (b == null) synchronized (Noise.class) { if (base == null) base = buildBase(); b = base; }
        return b;
    }

    /** Feinheit DETAIL³ × RGBA. */
    public static byte[] detail() {
        byte[] b = detail;
        if (b == null) synchronized (Noise.class) { if (detail == null) detail = buildDetail(); b = detail; }
        return b;
    }

    /** Wetterkarte WEATHER² × RGBA (x schnell, dann z). */
    public static byte[] weather() {
        byte[] b = weather;
        if (b == null) synchronized (Noise.class) { if (weather == null) weather = buildWeather(); b = weather; }
        return b;
    }

    // ------------------------------------------------------------------ Bausteine

    static int hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    static double rnd(int x, int y, int z, int seed) { return (hash(x, y, z, seed) & 0xFFFFFF) / (double) 0x1000000; }

    static final int[][] GRAD = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

    static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    /** Perlin-Rauschen mit Periode p in jeder Achse, Ergebnis etwa −1..1; x, y, z in Gitterzellen. */
    static double perlin(double x, double y, double z, int p, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        double u = fade(fx), v = fade(fy), w = fade(fz);
        double r = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            int[] g = GRAD[Math.floorMod(hash(Math.floorMod(x0 + dx, p), Math.floorMod(y0 + dy, p), Math.floorMod(z0 + dz, p), seed), 12)];
            double d = g[0] * (fx - dx) + g[1] * (fy - dy) + g[2] * (fz - dz);
            r += d * (dx == 1 ? u : 1 - u) * (dy == 1 ? v : 1 - v) * (dz == 1 ? w : 1 - w);
        }
        return r;
    }

    /** Perlin-fbm 0..1 mit Grundperiode p (in Einheiten des Würfels 0..1). */
    static double perlinFbm(double x, double y, double z, int p, int octaves, int seed) {
        double s = 0, a = 1, n = 0;
        int f = p;
        for (int o = 0; o < octaves; o++) {
            s += a * perlin(x * f, y * f, z * f, f, seed + o * 31);
            n += a;
            a *= 0.5;
            f *= 2;
        }
        return Math.max(0, Math.min(1, s / n * 0.95 + 0.5));
    }

    private static final java.util.Map<Long, double[]> POINTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Je Zelle ein Merkpunkt (x, y, z im Bereich 0..1 der Zelle), einmal gerechnet. */
    static double[] points(int c, int seed) {
        return POINTS.computeIfAbsent(((long) c << 32) | (seed & 0xffffffffL), k -> {
            double[] p = new double[c * c * c * 3];
            for (int z = 0; z < c; z++)
                for (int y = 0; y < c; y++)
                    for (int x = 0; x < c; x++) {
                        int o = ((z * c + y) * c + x) * 3;
                        p[o] = rnd(x, y, z, seed);
                        p[o + 1] = rnd(x, y, z, seed + 1);
                        p[o + 2] = rnd(x, y, z, seed + 2);
                    }
            return p;
        });
    }

    /** Worley (1 − Abstand zum nächsten Punkt), c Zellen je Achse, periodisch; x, y, z im Würfel 0..1. */
    static double worley(double x, double y, double z, int c, int seed) {
        double[] pts = points(c, seed);
        double px = x * c, py = y * c, pz = z * c;
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
        double best = 9;
        for (int k = -1; k <= 1; k++)
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) {
                    int gx = cx + i, gy = cy + j, gz = cz + k;
                    int o = ((Math.floorMod(gz, c) * c + Math.floorMod(gy, c)) * c + Math.floorMod(gx, c)) * 3;
                    double fx = gx + pts[o], fy = gy + pts[o + 1], fz = gz + pts[o + 2];
                    double d = (fx - px) * (fx - px) + (fy - py) * (fy - py) + (fz - pz) * (fz - pz);
                    if (d < best) best = d;
                }
        return Math.max(0, Math.min(1, 1 - Math.sqrt(best) * 1.15));
    }

    static double worleyFbm(double x, double y, double z, int c, int seed) {
        return worley(x, y, z, c, seed) * 0.625 + worley(x, y, z, c * 2, seed + 7) * 0.25 + worley(x, y, z, c * 4, seed + 13) * 0.125;
    }

    static byte b(double v) { return (byte) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    // ------------------------------------------------------------------ Texturen

    static byte[] buildBase() {
        int n = BASE;
        byte[] out = new byte[n * n * n * 4];
        IntStream.range(0, n).parallel().forEach(z -> {
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double u = (x + 0.5) / n, v = (y + 0.5) / n, w = (z + 0.5) / n;
                    double pf = perlinFbm(u, v, w, 4, 4, 11);
                    double wf = worleyFbm(u, v, w, 4, 101);
                    double pw = wf + pf * (1 - wf);           // Perlin-Worley: remap(perlin, 0, 1, worley, 1)
                    pw = Math.max(0, Math.min(1, (pw - 0.35) / 0.6));
                    int o = ((z * n + y) * n + x) * 4;
                    out[o] = b(pw);
                    out[o + 1] = b(wf);
                    out[o + 2] = b(worleyFbm(u, v, w, 8, 202));
                    out[o + 3] = b(worleyFbm(u, v, w, 16, 303));
                }
        });
        return out;
    }

    static byte[] buildDetail() {
        int n = DETAIL;
        byte[] out = new byte[n * n * n * 4];
        IntStream.range(0, n).parallel().forEach(z -> {
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double u = (x + 0.5) / n, v = (y + 0.5) / n, w = (z + 0.5) / n;
                    int o = ((z * n + y) * n + x) * 4;
                    out[o] = b(worleyFbm(u, v, w, 2, 404));
                    out[o + 1] = b(worleyFbm(u, v, w, 4, 505));
                    out[o + 2] = b(worleyFbm(u, v, w, 8, 606));
                    out[o + 3] = (byte) 255;
                }
        });
        return out;
    }

    static byte[] buildWeather() {
        int n = WEATHER;
        byte[] out = new byte[n * n * 4];
        IntStream.range(0, n).parallel().forEach(z -> {
            for (int x = 0; x < n; x++) {
                double u = (x + 0.5) / n, v = (z + 0.5) / n;
                // Haufen: Zellen (16 je Kachel) mit weichem Perlin darüber, damit sie nicht wie Waben aussehen
                double cluster = perlinFbm(u, 0.83, v, 2, 3, 713);
                double cells = Math.max(0, Math.min(1, (worley(u, 0.37, v, 16, 707) * 0.45 + perlinFbm(u, 0.37, v, 8, 4, 708) * 0.55) * (0.55 + 0.7 * cluster)));
                double patches = perlinFbm(u, 0.11, v, 4, 4, 709) * 0.75 + worley(u, 0.11, v, 4, 710) * 0.25;
                double fine = perlinFbm(u, 0.71, v, 12, 3, 711);
                double high = perlinFbm(u * 1.0, 0.53, v, 6, 4, 712);
                int o = (z * n + x) * 4;
                out[o] = b(cells);
                out[o + 1] = b(patches);
                out[o + 2] = b(fine);
                out[o + 3] = b(high);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ Abtasten wie die Grafikkarte (linear, wiederholt)

    /** Trilinear in einer RGBA-Byte-Textur der Kantenlänge n, Koordinaten in Texturraum (1 = eine Kachel). */
    static void sample3(byte[] t, int n, double u, double v, double w, double[] out) {
        double x = u * n - 0.5, y = v * n - 0.5, z = w * n - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        out[0] = out[1] = out[2] = out[3] = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double wgt = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            int o = ((Math.floorMod(z0 + dz, n) * n + Math.floorMod(y0 + dy, n)) * n + Math.floorMod(x0 + dx, n)) * 4;
            for (int k = 0; k < 4; k++) out[k] += wgt * (t[o + k] & 255) / 255.0;
        }
    }

    /** Bilinear in einer RGBA-Byte-Textur n², wiederholt. */
    static void sample2(byte[] t, int n, double u, double v, double[] out) {
        double x = u * n - 0.5, y = v * n - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        out[0] = out[1] = out[2] = out[3] = 0;
        for (int c = 0; c < 4; c++) {
            int dx = c & 1, dy = (c >> 1) & 1;
            double wgt = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy);
            int o = (Math.floorMod(y0 + dy, n) * n + Math.floorMod(x0 + dx, n)) * 4;
            for (int k = 0; k < 4; k++) out[k] += wgt * (t[o + k] & 255) / 255.0;
        }
    }
}
