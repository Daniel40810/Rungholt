package com.dan.figure;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Netz eines Menschen von 1,78 m [G] aus Teilen, die sich um Gelenke drehen, und die Haltung als Zahlen. Körper:
 * x rechts, y oben, z vorn, Fußsohle bei y = 0. Je Ecke: Ort, Normale, Fläche der Kleidung ({@link Outfit}) und Teil.
 * <p>
 * Teile: 0 Becken, 1 Rumpf, 2 Kopf mit Hals, 3/4 Oberschenkel links/rechts, 5/6 Unterschenkel mit Fuß, 7/8 Oberarm,
 * 9/10 Unterarm mit Hand, 11 Rucksack, 12 Mütze, 13/14 Fuß. Gelenke: Hüfte {@link #HIP}, Knie {@link #KNEE}, Knöchel
 * {@link #ANKLE} (der Fuß bleibt waagerecht), Schulter {@link #SHOULDER}, Ellbogen {@link #ELBOW}, Hals {@link #NECK}.
 * <p>
 * Die Haltung ({@link #pose}) rechnet aus Schrittphase, Gang, Bücken und Zeigen die Winkel der Gelenke; der
 * Scheitelshader {@code gl/figure.vert} rechnet genau so (dieselben Formeln), und {@link #transform} gibt den Ort einer
 * Ecke im Körper, damit die Prüfung ohne Grafikkarte nachmessen kann (Füße auf dem Boden, Schrittlänge).
 */
public final class FigureMesh {
    public static final int FLOATS = 8;
    public static final double HIP = 0.93, KNEE = 0.48, ANKLE = 0.08, SHOULDER = 1.43, ELBOW = 1.13, NECK = 1.52, THIGH = HIP - KNEE, SHIN = KNEE - ANKLE;
    /** Ausschlag der Hüfte beim Gehen (rad, bei Gang 1) und größte Beugung des Knies im Schwung. */
    public static final double HIP_SWING = 0.42, KNEE_SWING = 1.05;

    private final float[] v;
    private final int count;

    private FigureMesh(float[] v) { this.v = v; count = v.length / FLOATS; }

    public float[] vertices() { return v; }
    public int count() { return count; }

    // ------------------------------------------------------------------ Haltung

    /**
     * Winkel der Gelenke (rad): {Hüfte L, Hüfte R, Knie L, Knie R, Schulter L, Schulter R, Ellbogen L, Ellbogen R,
     * Rumpf, Senkung}. Hüfte und Schulter positiv = nach vorn, Knie und Ellbogen positiv = gebeugt, Rumpf positiv =
     * vorgebeugt; Senkung (m) hält den tieferen Fuß auf dem Boden. phase 0..1 je Doppelschritt, gait 0 Stehen … 1 Gehen
     * (über 1 schneller), bend 0..1 Bücken, point 0..1 rechter Arm zeigt nach vorn.
     */
    public static double[] pose(double phase, double gait, double bend, double point) {
        double a = 2 * Math.PI * phase, g = Math.min(1.4, Math.max(0, gait));
        double hl = HIP_SWING * g * Math.sin(a), hr = -hl;
        double kl = KNEE_SWING * g * Math.pow(Math.max(0, Math.cos(a)), 1.5) + 0.08 * g;
        double kr = KNEE_SWING * g * Math.pow(Math.max(0, -Math.cos(a)), 1.5) + 0.08 * g;
        // Bücken: Oberschenkel vor, Unterschenkel zurück, der Fuß bleibt unter der Hüfte; der Rumpf neigt sich
        hl += 0.5 * bend; hr += 0.5 * bend; kl += 1.0 * bend; kr += 1.0 * bend;
        double al = -0.65 * HIP_SWING * g * Math.sin(a), ar = -al;
        double el = 0.25 + 0.25 * g, er = el;
        al += 0.35 * bend; ar += 0.35 * bend;
        ar = ar * (1 - point) + 1.45 * point;
        er = er * (1 - point) + 0.08 * point;
        double torso = 1.0 * bend + 0.05 * g;
        double drop = Math.min(foot(hl, kl), foot(hr, kr));
        return new double[]{hl, hr, kl, kr, al, ar, el, er, torso, drop};
    }

    /** Höhe der Fußsohle über dem Boden bei Hüfte h und Knie k (ohne Senkung). */
    public static double foot(double h, double k) { return HIP - (THIGH * Math.cos(h) + SHIN * Math.cos(h - k)) - ANKLE; }

    /** Vorlage des Fußes vor der Hüfte (m). */
    public static double footForward(double h, double k) { return THIGH * Math.sin(h) + SHIN * Math.sin(h - k); }

    /**
     * Schrittlänge (m, ein Schritt) bei Gang g und Größe s: der Fuß wandert in der Stützphase unter der Hüfte von vorn
     * nach hinten; ein Doppelschritt ist zweimal so lang.
     */
    public static double stride(double gait, double scale) {
        double g = Math.min(1.4, Math.max(0.05, gait));
        return 2 * (THIGH + SHIN) * Math.sin(HIP_SWING * g) * scale;
    }

    /** Dreht (y, z) um die x-Achse durch py: positiv schwingt ein hängendes Glied nach vorn. */
    static double[] swing(double y, double z, double py, double a) {
        double c = Math.cos(a), s = Math.sin(a), yy = y - py;
        return new double[]{py + yy * c + z * s, -yy * s + z * c};
    }

    /** Ort einer Ecke (Körper) des Teils part in der Haltung p ({@link #pose}); wie {@code figure.vert}. */
    public static double[] transform(double x, double y, double z, int part, double[] p) {
        double[] r;
        switch (part) {
            case 3, 4 -> { r = swing(y, z, HIP, p[part - 3]); y = r[0]; z = r[1]; }
            case 5, 6, 13, 14 -> {
                int s = part >= 13 ? part - 13 : part - 5;
                if (part >= 13) { r = swing(y, z, ANKLE, -(p[s] - p[2 + s])); y = r[0]; z = r[1]; }   // Fuß waagerecht
                r = swing(y, z, KNEE, -p[2 + s]); y = r[0]; z = r[1];
                r = swing(y, z, HIP, p[s]); y = r[0]; z = r[1];
            }
            case 1, 2, 7, 8, 9, 10, 11, 12 -> {
                if (part == 9 || part == 10) { r = swing(y, z, ELBOW, p[6 + part - 9]); y = r[0]; z = r[1]; }
                if (part >= 7 && part <= 10) { r = swing(y, z, SHOULDER, p[4 + (part - 7) % 2]); y = r[0]; z = r[1]; }
                r = swing(y, z, HIP, -p[8]); y = r[0]; z = r[1];   // der Rumpf neigt sich nach vorn: das Obere geht vor
            }
            default -> { }
        }
        return new double[]{x, y - p[9], z};
    }

    // ------------------------------------------------------------------ Netz

    /** Das Netz mit seg Ecken je Ring (12 nah, 6 fern). */
    public static FigureMesh build(int seg) {
        List<float[]> o = new ArrayList<>();
        // Becken (Hose)
        tube(o, seg, 0, 0, new double[][]{{0.80, 0.15, 0.10}, {0.88, 0.165, 0.11}, {0.97, 0.16, 0.105}}, Outfit.TROUSERS, 0);
        // Rumpf (Jacke)
        tube(o, seg, 0, 0.005, new double[][]{{0.93, 0.158, 0.105}, {1.08, 0.152, 0.10}, {1.26, 0.172, 0.118}, {1.40, 0.19, 0.11}, {1.47, 0.13, 0.085}, {1.51, 0.06, 0.05}}, Outfit.JACKET, 1);
        // Hals und Kopf, Haar hinten und oben, Mütze
        tube(o, seg, 0, 0.0, new double[][]{{1.47, 0.052, 0.05}, {1.57, 0.05, 0.048}}, Outfit.SKIN, 2);
        head(o, seg, 0, 1.645, 0.012, 0.092, 0.115, 0.103, 2);
        cap(o, seg, 0, 1.655, 0.0, 0.103, 0.125, 0.115, 12);
        for (int s = -1; s <= 1; s += 2) {
            int L = s < 0 ? 0 : 1;
            double x = 0.095 * s;
            // Oberschenkel: Hose, am Knie die Fläche, die bei hochgekrempelter Hose Haut wird
            tube(o, seg, x, 0.0, new double[][]{{0.97, 0.085, 0.085}, {0.72, 0.07, 0.072}, {0.56, 0.058, 0.06}}, Outfit.TROUSERS, 3 + L);
            tube(o, seg, x, 0.0, new double[][]{{0.56, 0.058, 0.06}, {0.47, 0.052, 0.055}}, Outfit.KNEE, 3 + L);
            // Unterschenkel und Fuß
            tube(o, seg, x, 0.0, new double[][]{{0.50, 0.052, 0.055}, {0.40, 0.05, 0.055}}, Outfit.KNEE, 5 + L);
            tube(o, seg, x, 0.0, new double[][]{{0.40, 0.05, 0.055}, {0.25, 0.046, 0.05}, {0.09, 0.036, 0.04}}, Outfit.SHIN, 5 + L);
            box(o, x, 0.0, 0.075, 0.042, 0.045, 0.12, Outfit.FOOT, 13 + L);   // Fuß: von der Ferse bis zur Spitze
            double ax = 0.215 * s;
            tube(o, seg, ax, 0.0, new double[][]{{1.45, 0.058, 0.058}, {1.30, 0.05, 0.05}, {1.12, 0.044, 0.044}}, Outfit.JACKET, 7 + L);
            tube(o, seg, ax, 0.0, new double[][]{{1.14, 0.043, 0.043}, {0.99, 0.038, 0.036}, {0.875, 0.032, 0.03}}, Outfit.FOREARM, 9 + L);
            tube(o, seg, ax, 0.0, new double[][]{{0.88, 0.03, 0.022}, {0.80, 0.03, 0.02}, {0.77, 0.018, 0.014}}, Outfit.SKIN, 9 + L);
        }
        // Rucksack
        box(o, 0, 1.24, -0.20, 0.15, 0.20, 0.08, Outfit.PACK, 11);
        float[] r = new float[o.size() * FLOATS];
        for (int i = 0; i < o.size(); i++) System.arraycopy(o.get(i), 0, r, i * FLOATS, FLOATS);
        return new FigureMesh(r);
    }

    /** Ringe {y, rx, rz} um (x, z): Seiten und Deckel. */
    static void tube(List<float[]> o, int seg, double x, double z, double[][] rings, int slot, int part) {
        for (int k = 0; k + 1 < rings.length; k++) {
            double[] a = rings[k], b = rings[k + 1];
            for (int i = 0; i < seg; i++) {
                double t0 = 2 * Math.PI * i / seg, t1 = 2 * Math.PI * (i + 1) / seg;
                double[] p00 = ring(x, z, a, t0), p01 = ring(x, z, a, t1), p10 = ring(x, z, b, t0), p11 = ring(x, z, b, t1);
                double[] n0 = {Math.cos(t0), 0, Math.sin(t0)}, n1 = {Math.cos(t1), 0, Math.sin(t1)};
                tri(o, p00, n0, p01, n1, p11, n1, slot, part);
                tri(o, p00, n0, p11, n1, p10, n0, slot, part);
            }
        }
        for (int end = 0; end < 2; end++) {
            double[] r = end == 0 ? rings[0] : rings[rings.length - 1];
            double[] c = {x, r[0], z}, n = {0, end == 0 ? -1 : 1, 0};
            for (int i = 0; i < seg; i++) {
                double t0 = 2 * Math.PI * i / seg, t1 = 2 * Math.PI * (i + 1) / seg;
                tri(o, c, n, ring(x, z, r, t0), n, ring(x, z, r, t1), n, slot, part);
            }
        }
    }

    static double[] ring(double x, double z, double[] r, double t) { return new double[]{x + Math.cos(t) * r[1], r[0], z + Math.sin(t) * r[2]}; }

    /** Kopf: Ellipsoid; hinten und oben Haar, vorn Gesicht mit einer Nase. */
    static void head(List<float[]> o, int seg, double x, double y, double z, double rx, double ry, double rz, int part) {
        int nv = Math.max(4, seg / 2 + 1);
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < seg; i++) {
                double[][] q = new double[4][], n = new double[4][];
                int[][] c = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                double cy = 0, cz = 0;
                for (int k = 0; k < 4; k++) {
                    double th = Math.PI * c[k][1] / nv, ph = 2 * Math.PI * c[k][0] / seg;
                    double ux = Math.sin(th) * Math.cos(ph), uy = Math.cos(th), uz = Math.sin(th) * Math.sin(ph);
                    q[k] = new double[]{x + ux * rx, y + uy * ry, z + uz * rz};
                    n[k] = new double[]{ux, uy, uz};
                    cy += uy / 4; cz += uz / 4;
                }
                int slot = cy > 0.45 || (cz < 0.15 && cy > -0.45) ? Outfit.HAIR : Outfit.SKIN;
                tri(o, q[0], n[0], q[1], n[1], q[2], n[2], slot, part);
                tri(o, q[0], n[0], q[2], n[2], q[3], n[3], slot, part);
            }
        // Nase
        double[] tip = {x, y - 0.01, z + rz + 0.022}, a = {x - 0.016, y - 0.035, z + rz - 0.01}, b = {x + 0.016, y - 0.035, z + rz - 0.01}, t = {x, y + 0.025, z + rz - 0.004};
        tri(o, t, new double[]{-0.7, 0.2, 0.7}, a, new double[]{-0.7, 0.2, 0.7}, tip, new double[]{-0.7, 0.2, 0.7}, Outfit.SKIN, part);
        tri(o, t, new double[]{0.7, 0.2, 0.7}, tip, new double[]{0.7, 0.2, 0.7}, b, new double[]{0.7, 0.2, 0.7}, Outfit.SKIN, part);
        tri(o, a, new double[]{0, -1, 0.3}, b, new double[]{0, -1, 0.3}, tip, new double[]{0, -1, 0.3}, Outfit.SKIN, part);
    }

    /** Mütze: obere Halbschale etwas größer als der Kopf, mit Krempe vorn. */
    static void cap(List<float[]> o, int seg, double x, double y, double z, double rx, double ry, double rz, int part) {
        int nv = Math.max(3, seg / 4 + 1);
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < seg; i++) {
                double[][] q = new double[4][], n = new double[4][];
                int[][] c = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int k = 0; k < 4; k++) {
                    double th = 0.5 * Math.PI * c[k][1] / nv, ph = 2 * Math.PI * c[k][0] / seg;
                    double ux = Math.sin(th) * Math.cos(ph), uy = Math.cos(th), uz = Math.sin(th) * Math.sin(ph);
                    q[k] = new double[]{x + ux * rx, y + uy * ry * 0.75, z + uz * rz};
                    n[k] = new double[]{ux, uy, uz};
                }
                tri(o, q[0], n[0], q[1], n[1], q[2], n[2], Outfit.HAT, part);
                tri(o, q[0], n[0], q[2], n[2], q[3], n[3], Outfit.HAT, part);
            }
        // Krempe
        double[] up = {0, 1, 0};
        for (int i = 0; i < seg / 2; i++) {
            double t0 = Math.PI * i / (seg / 2), t1 = Math.PI * (i + 1) / (seg / 2);
            double[] a = {x + Math.cos(t0) * rx, y, z + Math.sin(t0) * rz}, b = {x + Math.cos(t1) * rx, y, z + Math.sin(t1) * rz};
            double[] a2 = {x + Math.cos(t0) * rx * 1.25, y - 0.01, z + Math.sin(t0) * rz * 1.55}, b2 = {x + Math.cos(t1) * rx * 1.25, y - 0.01, z + Math.sin(t1) * rz * 1.55};
            tri(o, a, up, b, up, b2, up, Outfit.HAT, part);
            tri(o, a, up, b2, up, a2, up, Outfit.HAT, part);
        }
    }

    /** Quader um (x, y0 + hy, z): halbe Breite hx, halbe Höhe hy, halbe Tiefe hz. */
    static void box(List<float[]> o, double x, double y0, double z, double hx, double hy, double hz, int slot, int part) {
        double yc = y0 + hy;
        double[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (double[] f : n) {
            double[] u = f[0] != 0 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
            double[] w = {f[1] * u[2] - f[2] * u[1], f[2] * u[0] - f[0] * u[2], f[0] * u[1] - f[1] * u[0]};
            double[][] c = new double[4][];
            double[][] s = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int k = 0; k < 4; k++) {
                double px = f[0] + u[0] * s[k][0] + w[0] * s[k][1], py = f[1] + u[1] * s[k][0] + w[1] * s[k][1], pz = f[2] + u[2] * s[k][0] + w[2] * s[k][1];
                c[k] = new double[]{x + px * hx, yc + py * hy, z + pz * hz};
            }
            tri(o, c[0], f, c[1], f, c[2], f, slot, part);
            tri(o, c[0], f, c[2], f, c[3], f, slot, part);
        }
    }

    static void tri(List<float[]> o, double[] a, double[] na, double[] b, double[] nb, double[] c, double[] nc, int slot, int part) {
        o.add(vert(a, na, slot, part));
        o.add(vert(b, nb, slot, part));
        o.add(vert(c, nc, slot, part));
    }

    static float[] vert(double[] p, double[] n, int slot, int part) {
        return new float[]{(float) p[0], (float) p[1], (float) p[2], (float) n[0], (float) n[1], (float) n[2], slot, part};
    }
}
