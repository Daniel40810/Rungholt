package com.dan.flock;

import java.util.Arrays;

/**
 * Das Netz einer Silbermöwe in Körperkoordinaten (x rechts, y oben, z nach hinten; Schnabel bei −z), rein in Java.
 * Ecken: Ort (3), Normale (3), Farbe (3, linear), Teil (1): 0 Rumpf, 1 Innenflügel, 2 Außenflügel, 3 Beine. Die
 * Flügel liegen ausgebreitet; der Vertex-Shader dreht Innen- und Außenflügel um Schulter und Handgelenk.
 * Maße: Rumpf 0,6 m, Spannweite 1,4 m. Farben: weiß, Mantel silbergrau, Flügelspitzen schwarz, Schnabel gelb, Beine fleischfarben.
 */
public final class GullMesh {
    public static final int FLOATS = 10;
    /** Schulter und Handgelenk (rechts; links gespiegelt). */
    public static final double SHOULDER_X = 0.06, SHOULDER_Y = 0.03, SHOULDER_Z = -0.04, WRIST_X = 0.44;
    private float[] v = new float[FLOATS * 512];
    private int n;

    public float[] vertices() { return Arrays.copyOf(v, n * FLOATS); }
    public int count() { return n; }

    static final double[] WHITE = {0.78, 0.78, 0.76}, MANTLE = {0.33, 0.36, 0.4}, BLACK = {0.025, 0.025, 0.03}, BEAK = {0.75, 0.55, 0.05},
            LEG = {0.6, 0.38, 0.3}, EYE = {0.02, 0.02, 0.02};

    private void put(double x, double y, double z, double[] nn, double[] c, int part) {
        if ((n + 1) * FLOATS > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = n * FLOATS;
        v[o] = (float) x; v[o + 1] = (float) y; v[o + 2] = (float) z;
        v[o + 3] = (float) nn[0]; v[o + 4] = (float) nn[1]; v[o + 5] = (float) nn[2];
        v[o + 6] = (float) c[0]; v[o + 7] = (float) c[1]; v[o + 8] = (float) c[2];
        v[o + 9] = part;
        n++;
    }

    private void tri(double[] a, double[] b, double[] c, double[] col, int part) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        double x = uy * wz - uz * wy, y = uz * wx - ux * wz, z = ux * wy - uy * wx, l = Math.max(1e-12, Math.sqrt(x * x + y * y + z * z));
        double[] nn = {x / l, y / l, z / l};
        put(a[0], a[1], a[2], nn, col, part); put(b[0], b[1], b[2], nn, col, part); put(c[0], c[1], c[2], nn, col, part);
    }

    /** Ellipsoid um (cx, cy, cz) mit Halbachsen, glatt; Farbe oben und unten getrennt. */
    private void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double[] top, double[] bottom, int seg, int part) {
        int rings = seg / 2;
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < seg; i++) {
                double t0 = Math.PI * j / rings, t1 = Math.PI * (j + 1) / rings, a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double[][] q = {sp(t0, a0), sp(t1, a0), sp(t1, a1), sp(t0, a1)};
                double[] col = j < rings / 2 ? top : bottom;
                int[][] tris = {{0, 1, 2}, {0, 2, 3}};
                for (int[] tr : tris)
                    for (int k : tr) {
                        double[] d = q[k];
                        double[] nn = norm(new double[]{d[0] / rx, d[1] / ry, d[2] / rz});
                        put(cx + d[0] * rx, cy + d[1] * ry, cz + d[2] * rz, nn, col, part);
                    }
            }
    }

    static double[] sp(double t, double a) { return new double[]{Math.sin(t) * Math.sin(a), Math.cos(t), Math.sin(t) * Math.cos(a)}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    /** Eine Fläche als zwei Schichten: oben (Farbe top) und unten (Farbe bottom), 4 mm auseinander. */
    private void plate(double[][] poly, double[][] colTop, double[] bottom, int part) {
        for (int i = 1; i + 1 < poly.length; i++) {
            double[] a = poly[0], b = poly[i], c = poly[i + 1];
            double[] ct = colTop[Math.min(i - 1, colTop.length - 1)];
            tri(up(a, 0.002), up(c, 0.002), up(b, 0.002), ct, part);
            tri(up(a, -0.002), up(b, -0.002), up(c, -0.002), bottom, part);
        }
    }

    static double[] up(double[] p, double d) { return new double[]{p[0], p[1] + d, p[2]}; }

    /** Die ganze Möwe. */
    public static GullMesh build() {
        GullMesh m = new GullMesh();
        // Rumpf, Kopf, Schnabel, Auge, Schwanz
        m.ellipsoid(0, 0, 0.02, 0.075, 0.075, 0.24, WHITE, WHITE, 14, 0);
        m.ellipsoid(0, 0.05, -0.24, 0.05, 0.05, 0.06, WHITE, WHITE, 10, 0);
        double[] b0 = {0, 0.05, -0.29}, bl = {-0.012, 0.045, -0.29}, br = {0.012, 0.045, -0.29}, tip = {0, 0.035, -0.36}, bd = {0, 0.032, -0.29};
        m.tri(b0, bl, tip, BEAK, 0); m.tri(b0, tip, br, BEAK, 0); m.tri(bl, bd, tip, BEAK, 0); m.tri(bd, br, tip, BEAK, 0);
        m.ellipsoid(0.04, 0.065, -0.26, 0.008, 0.008, 0.008, EYE, EYE, 6, 0);
        m.ellipsoid(-0.04, 0.065, -0.26, 0.008, 0.008, 0.008, EYE, EYE, 6, 0);
        m.plate(new double[][]{{-0.06, 0.01, 0.2}, {0.06, 0.01, 0.2}, {0.07, 0.0, 0.34}, {-0.07, 0.0, 0.34}}, new double[][]{WHITE}, WHITE, 0);
        // Mantel (Rücken zwischen den Flügeln)
        m.plate(new double[][]{{-0.07, 0.07, -0.08}, {0.07, 0.07, -0.08}, {0.06, 0.07, 0.16}, {-0.06, 0.07, 0.16}}, new double[][]{MANTLE}, WHITE, 0);
        for (int s = -1; s <= 1; s += 2) {
            double sx = s, x0 = SHOULDER_X, xw = WRIST_X, xt = 0.70;
            // Innenflügel: Vorderkante leicht vor, Hinterkante mit weißem Saum
            double[][] inner = {{sx * x0, SHOULDER_Y, -0.09}, {sx * xw, SHOULDER_Y, -0.07}, {sx * xw, SHOULDER_Y, 0.13}, {sx * x0, SHOULDER_Y, 0.15}};
            double[][] outer = {{sx * xw, SHOULDER_Y, -0.07}, {sx * 0.6, SHOULDER_Y, -0.02}, {sx * xt, SHOULDER_Y, 0.06}, {sx * 0.6, SHOULDER_Y, 0.11}, {sx * xw, SHOULDER_Y, 0.13}};
            if (s < 0) { inner = rev(inner); outer = rev(outer); }
            m.plate(inner, new double[][]{MANTLE}, WHITE, 1);
            m.plate(outer, s < 0 ? new double[][]{BLACK, MANTLE, MANTLE} : new double[][]{MANTLE, MANTLE, BLACK}, s < 0 ? WHITE : WHITE, 2);
            // Flügelspitze unten auch schwarz
            m.tri(new double[]{sx * 0.6, SHOULDER_Y - 0.003, -0.02}, new double[]{sx * xt, SHOULDER_Y - 0.003, 0.06}, new double[]{sx * 0.6, SHOULDER_Y - 0.003, 0.11}, BLACK, 2);
            m.tri(new double[]{sx * 0.6, SHOULDER_Y - 0.003, -0.02}, new double[]{sx * 0.6, SHOULDER_Y - 0.003, 0.11}, new double[]{sx * xt, SHOULDER_Y - 0.003, 0.06}, BLACK, 2);
            // Beine (nur beim Sitzen sichtbar)
            double lx = sx * 0.035;
            m.tri(new double[]{lx - 0.008, -0.05, 0.03}, new double[]{lx + 0.008, -0.05, 0.03}, new double[]{lx, -0.16, 0.0}, LEG, 3);
            m.tri(new double[]{lx + 0.008, -0.05, 0.03}, new double[]{lx - 0.008, -0.05, 0.03}, new double[]{lx, -0.16, 0.0}, LEG, 3);
            m.tri(new double[]{lx - 0.03, -0.16, -0.04}, new double[]{lx + 0.03, -0.16, -0.04}, new double[]{lx, -0.16, 0.02}, LEG, 3);
            m.tri(new double[]{lx + 0.03, -0.16, -0.04}, new double[]{lx - 0.03, -0.16, -0.04}, new double[]{lx, -0.16, 0.02}, LEG, 3);
        }
        return m;
    }

    static double[][] rev(double[][] p) {
        double[][] r = new double[p.length][];
        for (int i = 0; i < p.length; i++) r[i] = p[p.length - 1 - i];
        return r;
    }

    /**
     * Flügelstellung einer Möwe für den Shader: {Anhebung innen, zusätzlich außen, Rückwärtsschwung, Faltung außen}
     * in rad. Schlagen (Phase, Stärke), Gleiten mit leichter V-Stellung, Stehen im Aufwind mit geknicktem Flügel
     * („Möwenflügel“), Sitzen mit angelegten Flügeln.
     */
    public static double[] wing(Flock.Gull g) {
        double s = Math.sin(g.wingPhase);
        double dih = 0.1 + 0.6 * g.flap * s, out = 0.3 * g.flap * Math.sin(g.wingPhase - 0.7) - 0.06, sweep = 0.05 + 0.1 * g.flap * Math.cos(g.wingPhase);
        if (g.state == Flock.State.SOAR) { dih = 0.2; out = -0.25; sweep = 0.15; }
        double f = g.fold;
        return new double[]{dih * (1 - f) - 0.08 * f, out * (1 - f) + 0.04 * f, sweep * (1 - f) + 1.45 * f, 0.25 * f};
    }
}
