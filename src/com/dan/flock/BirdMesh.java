package com.dan.flock;

import java.util.Arrays;

/**
 * Seit Haithabu Phase 13: das Netz einer Vogelart in Körperkoordinaten (x rechts, y oben, z nach hinten; Schnabel bei
 * −z), gebaut wie {@link GullMesh} (gleiche Ecken: Ort, Normale, Farbe, Teil 0 Rumpf, 1 Innenflügel, 2 Außenflügel,
 * 3 Beine), aber nach den Maßen der Art gestreckt und mit ihren Eigenheiten: der lange Hals von Schwan und Kormoran,
 * der Gabelschwanz der Schwalbe, Erpel und Weibchen der Stockente. Farben linear [G nach Bestimmungsbüchern].
 * <p>
 * Schulter und Handgelenk liegen bei {@link #shoulder} und {@link #wrist}; der Shader biegt die Flügel darum wie bei der
 * Möwe ({@link GullMesh#wing}).
 */
public final class BirdMesh {
    public static final int FLOATS = GullMesh.FLOATS;
    private float[] v = new float[FLOATS * 1024];
    private int n;
    /** Rechte Schulter (x, y, z) und x des rechten Handgelenks in m. */
    public final double[] shoulder = new double[3];
    public double wrist;
    public final Species species;
    public final int variant;

    private BirdMesh(Species sp, int variant) { this.species = sp; this.variant = variant; }

    public float[] vertices() { return Arrays.copyOf(v, n * FLOATS); }
    public int count() { return n; }

    /** Zahl der Spielarten je Art (Stockente: Erpel und Weibchen). */
    public static int variants(Species sp) { return sp == Species.ENTE || sp == Species.SCHWAN || sp == Species.KORMORAN || sp == Species.MOEWE ? 2 : 1; }

    /** Spielart eines Vogels jetzt: Erpel oder Weibchen; Schwan und Kormoran fliegen mit gestrecktem Hals (Spielart 1). */
    public static int variantOf(Flock.Gull g) {
        if (g.species == Species.ENTE) return g.id % 2;
        if (g.species == Species.MOEWE) return g.kelp ? 1 : 0;
        if (g.species == Species.SCHWAN || g.species == Species.KORMORAN) {
            Flock.State s = g.state;
            return s == Flock.State.FLY || s == Flock.State.TAKEOFF || s == Flock.State.LAND || s == Flock.State.LEAVE ? 1 : 0;
        }
        return 0;
    }

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

    private void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double[] top, double[] bottom, int seg, int part) {
        int rings = seg / 2;
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < seg; i++) {
                double t0 = Math.PI * j / rings, t1 = Math.PI * (j + 1) / rings, a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double[][] q = {GullMesh.sp(t0, a0), GullMesh.sp(t1, a0), GullMesh.sp(t1, a1), GullMesh.sp(t0, a1)};
                double[] col = j < rings / 2 ? top : bottom;
                int[][] tris = {{0, 1, 2}, {0, 2, 3}};
                for (int[] tr : tris)
                    for (int k : tr) {
                        double[] d = q[k];
                        double[] nn = GullMesh.norm(new double[]{d[0] / rx, d[1] / ry, d[2] / rz});
                        put(cx + d[0] * rx, cy + d[1] * ry, cz + d[2] * rz, nn, col, part);
                    }
            }
    }

    private void plate(double[][] poly, double[] top, double[] bottom, int part) {
        for (int i = 1; i + 1 < poly.length; i++) {
            double[] a = poly[0], b = poly[i], c = poly[i + 1];
            tri(GullMesh.up(a, 0.002), GullMesh.up(c, 0.002), GullMesh.up(b, 0.002), top, part);
            tri(GullMesh.up(a, -0.002), GullMesh.up(b, -0.002), GullMesh.up(c, -0.002), bottom, part);
        }
    }

    /** Farben einer Art: Rücken, Bauch, Kopf, Mantel, Flügel oben innen, außen, Flügel unten, Schnabel, Beine, Schwanz. */
    record Colors(double[] back, double[] belly, double[] head, double[] mantle, double[] wingIn, double[] wingOut, double[] under, double[] beak,
                  double[] leg, double[] tail) { }

    static double[] c(double r, double g, double b) { return new double[]{r, g, b}; }

    static Colors colors(Species sp, int variant) {
        return switch (sp) {
            case MOEWE -> variant == 1
                    // Dominikanermöwe (seit Glacier Phase 10): Mantel und Flügel oben schwarzbraun
                    ? new Colors(GullMesh.WHITE, GullMesh.WHITE, GullMesh.WHITE, c(0.05, 0.05, 0.06), c(0.05, 0.05, 0.06), GullMesh.BLACK, GullMesh.WHITE, GullMesh.BEAK, GullMesh.LEG, GullMesh.WHITE)
                    : new Colors(GullMesh.WHITE, GullMesh.WHITE, GullMesh.WHITE, GullMesh.MANTLE, GullMesh.MANTLE, GullMesh.BLACK, GullMesh.WHITE, GullMesh.BEAK, GullMesh.LEG, GullMesh.WHITE);
            case KRAEHE -> new Colors(c(0.30, 0.30, 0.31), c(0.26, 0.26, 0.27), c(0.02, 0.02, 0.025), c(0.30, 0.30, 0.31), c(0.025, 0.025, 0.03),
                    c(0.02, 0.02, 0.025), c(0.03, 0.03, 0.035), c(0.02, 0.02, 0.02), c(0.03, 0.03, 0.03), c(0.02, 0.02, 0.025));
            case SCHWALBE -> new Colors(c(0.02, 0.03, 0.07), c(0.55, 0.48, 0.38), c(0.02, 0.03, 0.07), c(0.02, 0.03, 0.07), c(0.02, 0.025, 0.05),
                    c(0.015, 0.02, 0.04), c(0.30, 0.27, 0.22), c(0.02, 0.02, 0.02), c(0.05, 0.04, 0.04), c(0.02, 0.025, 0.05));
            case ENTE -> variant == 0
                    // Erpel: grüner Kopf, weißer Ring, braune Brust, grauer Rumpf
                    ? new Colors(c(0.36, 0.36, 0.33), c(0.50, 0.50, 0.47), c(0.02, 0.12, 0.05), c(0.22, 0.18, 0.13), c(0.30, 0.29, 0.27), c(0.25, 0.24, 0.22),
                    c(0.55, 0.55, 0.53), c(0.55, 0.50, 0.08), c(0.70, 0.35, 0.08), c(0.03, 0.03, 0.03))
                    // Weibchen: braun gefleckt
                    : new Colors(c(0.25, 0.17, 0.09), c(0.33, 0.24, 0.14), c(0.28, 0.20, 0.11), c(0.22, 0.15, 0.08), c(0.25, 0.18, 0.10), c(0.20, 0.14, 0.08),
                    c(0.45, 0.40, 0.32), c(0.45, 0.28, 0.06), c(0.65, 0.33, 0.08), c(0.22, 0.15, 0.08));
            case SCHWAN -> new Colors(c(0.80, 0.80, 0.78), c(0.74, 0.74, 0.72), c(0.80, 0.80, 0.78), c(0.80, 0.80, 0.78), c(0.80, 0.80, 0.78), c(0.78, 0.78, 0.76),
                    c(0.74, 0.74, 0.72), c(0.80, 0.30, 0.04), c(0.04, 0.04, 0.04), c(0.78, 0.78, 0.76));
            // Kondor: schwarz, weiße Halskrause, weißes Feld auf den Armschwingen oben, nackter grauer Kopf
            case KONDOR -> new Colors(c(0.03, 0.03, 0.035), c(0.035, 0.035, 0.04), c(0.30, 0.22, 0.20), c(0.04, 0.04, 0.045), c(0.62, 0.62, 0.60),
                    c(0.025, 0.025, 0.03), c(0.04, 0.04, 0.045), c(0.55, 0.50, 0.42), c(0.25, 0.24, 0.24), c(0.03, 0.03, 0.035));
            case KORMORAN -> new Colors(c(0.035, 0.04, 0.035), c(0.03, 0.03, 0.03), c(0.04, 0.045, 0.04), c(0.09, 0.075, 0.05), c(0.07, 0.06, 0.045),
                    c(0.03, 0.03, 0.03), c(0.03, 0.03, 0.03), c(0.30, 0.28, 0.25), c(0.02, 0.02, 0.02), c(0.03, 0.03, 0.03));
        };
    }

    /** Das Netz der Art (Spielart variant, 0 oder 1 bei der Stockente). */
    public static BirdMesh build(Species sp, int variant) {
        BirdMesh m = new BirdMesh(sp, variant);
        Colors k = colors(sp, variant);
        double sl = sp.length / 0.60, sx = sp.span / 1.40;
        // Rumpf: Enten und Schwäne breiter und flacher (Schwimmer), Kormoran schlank, Schwalbe zierlich
        double bw = switch (sp) { case ENTE -> 1.35; case SCHWAN -> 1.3; case KORMORAN -> 0.85; case SCHWALBE -> 1.1; default -> 1.0; };
        double bh = switch (sp) { case ENTE -> 1.1; case SCHWAN -> 1.05; default -> 1.0; };
        m.ellipsoid(0, 0, 0.02 * sl, 0.075 * sl * bw, 0.075 * sl * bh, 0.24 * sl, k.back(), k.belly(), 14, 0);
        // Hals und Kopf
        double[] head;
        if (sp == Species.SCHWAN || sp == Species.KORMORAN) {
            // Hals als Kette von Ellipsoiden auf einer Kurve vom Rumpf nach oben vorn (Schwan als S)
            boolean flight = variant == 1;
            double[] p0 = {0, 0.03 * sl, -0.20 * sl}, p1, p2;
            if (flight) { p1 = new double[]{0, 0.03 * sl, -0.36 * sl}; p2 = new double[]{0, 0.035 * sl, -0.50 * sl}; }   // im Flug gestreckt
            else if (sp == Species.SCHWAN) { p1 = new double[]{0, 0.32 * sl, -0.18 * sl}; p2 = new double[]{0, 0.30 * sl, -0.32 * sl}; }
            else { p1 = new double[]{0, 0.11 * sl, -0.28 * sl}; p2 = new double[]{0, 0.15 * sl, -0.33 * sl}; }
            // Hals als Röhre aus vielen dicht liegenden Kugeln (glatt, keine Perlen)
            int segs = 16;
            double r0 = (sp == Species.SCHWAN ? 0.040 : 0.032) * sl;
            for (int i = 0; i <= segs; i++) {
                double t = i / (double) segs, a = (1 - t) * (1 - t), b = 2 * t * (1 - t), cc = t * t;
                double x = a * p0[0] + b * p1[0] + cc * p2[0], y = a * p0[1] + b * p1[1] + cc * p2[1], z = a * p0[2] + b * p1[2] + cc * p2[2];
                double r = r0 * (1.2 - 0.35 * t);
                m.ellipsoid(x, y, z, r, r, r, k.head(), k.head(), 8, 0);
            }
            head = p2;
            m.ellipsoid(head[0], head[1], head[2] - 0.02 * sl, 0.035 * sl, 0.033 * sl, 0.05 * sl, k.head(), k.head(), 10, 0);
            if (sp == Species.KORMORAN) m.ellipsoid(0.02 * sl, head[1] - 0.005 * sl, head[2] - 0.03 * sl, 0.015 * sl, 0.015 * sl, 0.02 * sl, c(0.6, 0.58, 0.52), c(0.6, 0.58, 0.52), 6, 0);
        } else {
            double hy = sp == Species.ENTE ? 0.08 : 0.05, hz = sp == Species.ENTE ? -0.25 : -0.24;
            head = new double[]{0, hy * sl, hz * sl};
            if (sp == Species.ENTE) m.ellipsoid(0, 0.05 * sl, -0.21 * sl, 0.04 * sl, 0.05 * sl, 0.04 * sl, variant == 0 ? c(0.6, 0.6, 0.58) : k.head(), k.belly(), 8, 0);
            m.ellipsoid(head[0], head[1], head[2], 0.05 * sl, 0.05 * sl, 0.06 * sl, k.head(), k.head(), 10, 0);
        }
        // Schnabel
        double bl = switch (sp) { case ENTE -> 0.09; case SCHWAN -> 0.07; case KORMORAN -> 0.09; case KRAEHE -> 0.08; case SCHWALBE -> 0.02; default -> 0.07; } * sl;
        double bwid = (sp == Species.ENTE || sp == Species.SCHWAN ? 0.022 : 0.012) * sl;
        double hz0 = head[2] - 0.05 * sl, hy0 = head[1];
        double[] b0 = {0, hy0 + 0.005 * sl, hz0}, bL = {-bwid, hy0 - 0.005 * sl, hz0}, bR = {bwid, hy0 - 0.005 * sl, hz0}, tip = {0, hy0 - 0.012 * sl, hz0 - bl}, bd = {0, hy0 - 0.018 * sl, hz0};
        m.tri(b0, bL, tip, k.beak(), 0); m.tri(b0, tip, bR, k.beak(), 0); m.tri(bL, bd, tip, k.beak(), 0); m.tri(bd, bR, tip, k.beak(), 0);
        if (sp == Species.SCHWAN) m.ellipsoid(0, hy0 + 0.012 * sl, hz0 - 0.005 * sl, 0.012 * sl, 0.012 * sl, 0.012 * sl, c(0.03, 0.03, 0.03), c(0.03, 0.03, 0.03), 6, 0);
        // Augen
        for (int s = -1; s <= 1; s += 2) m.ellipsoid(s * 0.04 * sl, head[1] + 0.015 * sl, head[2] - 0.02 * sl, 0.008 * sl, 0.008 * sl, 0.008 * sl, GullMesh.EYE, GullMesh.EYE, 6, 0);
        // Schwanz: die Schwalbe mit langen Spießen, die Ente mit hochgebogenem Ende
        if (sp == Species.SCHWALBE) {
            m.plate(new double[][]{{-0.05 * sl, 0, 0.18 * sl}, {0.05 * sl, 0, 0.18 * sl}, {0.10 * sl, 0, 0.55 * sl}, {0.0, 0, 0.30 * sl}, {-0.10 * sl, 0, 0.55 * sl}}, k.tail(), k.tail(), 0);
        } else {
            double tl = sp == Species.KORMORAN ? 0.36 : sp == Species.ENTE ? 0.30 : 0.34;
            m.plate(new double[][]{{-0.06 * sl, 0.01 * sl, 0.2 * sl}, {0.06 * sl, 0.01 * sl, 0.2 * sl}, {0.07 * sl, (sp == Species.ENTE ? 0.04 : 0) * sl, tl * sl},
                    {-0.07 * sl, (sp == Species.ENTE ? 0.04 : 0) * sl, tl * sl}}, k.tail(), k.tail(), 0);
        }
        // Mantel
        m.plate(new double[][]{{-0.07 * sl * bw, 0.07 * sl * bh, -0.08 * sl}, {0.07 * sl * bw, 0.07 * sl * bh, -0.08 * sl}, {0.06 * sl * bw, 0.07 * sl * bh, 0.16 * sl},
                {-0.06 * sl * bw, 0.07 * sl * bh, 0.16 * sl}}, k.mantle(), k.belly(), 0);
        // Flügel
        double sy = 0.03 * sl * bh, x0 = 0.06 * sl * bw, xw = Math.max(x0 + 0.02, 0.44 * sx), xt = Math.max(xw + 0.02, 0.70 * sx);
        double cf = -0.09 * sl, cb = 0.15 * sl, chord = sp == Species.SCHWALBE ? 0.7 : sp == Species.ENTE ? 0.75 : sp == Species.KRAEHE ? 1.15 : sp == Species.KONDOR ? 1.3 : 1.0;
        m.shoulder[0] = x0; m.shoulder[1] = sy; m.shoulder[2] = -0.04 * sl;
        m.wrist = xw;
        for (int s = -1; s <= 1; s += 2) {
            double[][] inner = {{s * x0, sy, cf}, {s * xw, sy, -0.07 * sl}, {s * xw, sy, -0.07 * sl + 0.20 * sl * chord}, {s * x0, sy, cf + 0.24 * sl * chord}};
            double[][] outer;
            if (sp == Species.KRAEHE || sp == Species.KONDOR)       // gefingerte Handschwingen: breit und stumpf
                outer = new double[][]{{s * xw, sy, -0.07 * sl}, {s * (xw + 0.6 * (xt - xw)), sy, -0.04 * sl}, {s * xt, sy, 0.05 * sl}, {s * (xw + 0.7 * (xt - xw)), sy, 0.14 * sl}, {s * xw, sy, 0.15 * sl}};
            else if (sp == Species.SCHWALBE)  // sichelförmig, spitz nach hinten
                outer = new double[][]{{s * xw, sy, -0.07 * sl}, {s * (xw + 0.5 * (xt - xw)), sy, -0.03 * sl}, {s * xt, sy, 0.12 * sl}, {s * xw, sy, 0.07 * sl}};
            else
                outer = new double[][]{{s * xw, sy, -0.07 * sl}, {s * (xw + 0.55 * (xt - xw)), sy, -0.02 * sl}, {s * xt, sy, 0.06 * sl}, {s * (xw + 0.55 * (xt - xw)), sy, 0.11 * sl * chord}, {s * xw, sy, 0.13 * sl * chord}};
            if (s < 0) { inner = GullMesh.rev(inner); outer = GullMesh.rev(outer); }
            m.plate(inner, k.wingIn(), k.under(), 1);
            m.plate(outer, k.wingOut(), sp == Species.MOEWE ? GullMesh.WHITE : k.under(), 2);
            if (sp == Species.ENTE) {
                // Spiegel: blaues Feld am Hinterrand des Innenflügels
                double[] mirror = c(0.04, 0.08, 0.28);
                m.tri(new double[]{s * (x0 + 0.3 * (xw - x0)), sy + 0.003, 0.06 * sl}, new double[]{s * (x0 + 0.9 * (xw - x0)), sy + 0.003, 0.05 * sl},
                        new double[]{s * (x0 + 0.6 * (xw - x0)), sy + 0.003, 0.12 * sl}, mirror, 1);
            }
            // Beine
            double lx = s * 0.035 * sl * bw, ll = 0.16 * sl * (sp == Species.SCHWAN ? 0.8 : 1);
            double[] lc = k.leg();
            m.tri(new double[]{lx - 0.008 * sl, -0.05 * sl, 0.03 * sl}, new double[]{lx + 0.008 * sl, -0.05 * sl, 0.03 * sl}, new double[]{lx, -ll, 0.0}, lc, 3);
            m.tri(new double[]{lx + 0.008 * sl, -0.05 * sl, 0.03 * sl}, new double[]{lx - 0.008 * sl, -0.05 * sl, 0.03 * sl}, new double[]{lx, -ll, 0.0}, lc, 3);
            m.tri(new double[]{lx - 0.03 * sl, -ll, -0.04 * sl}, new double[]{lx + 0.03 * sl, -ll, -0.04 * sl}, new double[]{lx, -ll, 0.02 * sl}, lc, 3);
            m.tri(new double[]{lx + 0.03 * sl, -ll, -0.04 * sl}, new double[]{lx - 0.03 * sl, -ll, -0.04 * sl}, new double[]{lx, -ll, 0.02 * sl}, lc, 3);
        }
        return m;
    }

    /**
     * Flügelstellung eines Vogels: wie {@link GullMesh#wing}, dazu der Kormoran beim Trocknen (Flügel halb geöffnet und
     * hängend) und die Schwalbe mit flachen, schnellen Schlägen.
     */
    public static double[] wing(Flock.Gull g) {
        double[] w = GullMesh.wing(g);
        if (g.spread > 0) {
            double s = g.spread;
            double[] dry = {0.25, -0.55, 0.35, 0.0};
            for (int i = 0; i < 4; i++) w[i] = w[i] * (1 - s) + dry[i] * s;
        }
        if (g.species == Species.SCHWALBE && g.fold < 0.5) { w[0] *= 0.7; w[2] += 0.25; }
        return w;
    }
}
