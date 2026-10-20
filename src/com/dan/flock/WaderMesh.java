package com.dan.flock;

import java.util.Arrays;

/**
 * Seit Rungholt Phase 8: das Netz einer Art des Watts aus ihrem Datensatz ({@link WaderSpecies}), gebaut wie
 * {@link BirdMesh} (Ecken: Ort, Normale, Farbe, Teil; x rechts, y oben, z nach hinten, Schnabel bei −z, wahre Größe in
 * m). Teile: 0 Rumpf, 1 Innenflügel, 2 Außenflügel, 3 Beine, neu 4 Hals, Kopf und Schnabel: {@code wader.vert} neigt
 * ihn um {@link #neck} zum Stochern. Zwei Spielarten (0 Sommer, 1 Winter: Kappe der Lachmöwe, Bauchfleck des
 * Alpenstrandläufers) und zwei Feinheiten (nah, fern).
 */
public final class WaderMesh {
    public static final int FLOATS = GullMesh.FLOATS;
    private float[] v = new float[FLOATS * 1024];
    private int n;
    /** Rechte Schulter (x, y, z), x des Handgelenks, Drehpunkt des Halses (x, y, z), Höhe der Füße unter der Mitte (negativ). */
    public final double[] shoulder = new double[3], neck = new double[3];
    public double wrist, footY;
    public final WaderSpecies species;
    public final int variant;
    public final boolean far;

    private WaderMesh(WaderSpecies s, int variant, boolean far) { species = s; this.variant = variant; this.far = far; }

    public float[] vertices() { return Arrays.copyOf(v, n * FLOATS); }
    public int count() { return n; }

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

    /** Ellipsoid; obere Hälfte top, untere bottom; band != null färbt einen Streifen vorn unten (Brustband, Bauchfleck). */
    private void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double[] top, double[] bottom, int seg, int part, double[] band, double bandZ0, double bandZ1) {
        int rings = Math.max(2, seg / 2);
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < seg; i++) {
                double t0 = Math.PI * j / rings, t1 = Math.PI * (j + 1) / rings, a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double[][] q = {GullMesh.sp(t0, a0), GullMesh.sp(t1, a0), GullMesh.sp(t1, a1), GullMesh.sp(t0, a1)};
                double[] col = j < rings / 2 ? top : bottom;
                double zc = (q[0][2] + q[2][2]) * 0.5 * rz, yc = (q[0][1] + q[2][1]) * 0.5;
                if (band != null && zc >= bandZ0 && zc <= bandZ1 && yc < 0.35) col = band;
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
            tri(GullMesh.up(a, 0.0015), GullMesh.up(c, 0.0015), GullMesh.up(b, 0.0015), top, part);
            tri(GullMesh.up(a, -0.0015), GullMesh.up(b, -0.0015), GullMesh.up(c, -0.0015), bottom, part);
        }
    }

    /** Ein dünner Kegel von a nach b (Bein, Schnabel). */
    private void stick(double[] a, double[] b, double r0, double r1, double[] col, int part, int sides) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double[] d = {dx / l, dy / l, dz / l};
        double[] t = Math.abs(d[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = GullMesh.norm(cross(d, t)), w = cross(d, u);
        for (int k = 0; k < sides; k++) {
            double a0 = 2 * Math.PI * k / sides, a1 = 2 * Math.PI * (k + 1) / sides;
            double[] o0 = {u[0] * Math.cos(a0) + w[0] * Math.sin(a0), u[1] * Math.cos(a0) + w[1] * Math.sin(a0), u[2] * Math.cos(a0) + w[2] * Math.sin(a0)};
            double[] o1 = {u[0] * Math.cos(a1) + w[0] * Math.sin(a1), u[1] * Math.cos(a1) + w[1] * Math.sin(a1), u[2] * Math.cos(a1) + w[2] * Math.sin(a1)};
            double[] p00 = add(a, o0, r0), p01 = add(a, o1, r0), p10 = add(b, o0, r1), p11 = add(b, o1, r1);
            put(p00[0], p00[1], p00[2], o0, col, part); put(p10[0], p10[1], p10[2], o0, col, part); put(p11[0], p11[1], p11[2], o1, col, part);
            put(p00[0], p00[1], p00[2], o0, col, part); put(p11[0], p11[1], p11[2], o1, col, part); put(p01[0], p01[1], p01[2], o1, col, part);
        }
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] add(double[] a, double[] d, double s) { return new double[]{a[0] + d[0] * s, a[1] + d[1] * s, a[2] + d[2] * s}; }

    /** Das Netz der Art (Spielart 0 Sommer, 1 Winter), nah oder fern. */
    public static WaderMesh build(WaderSpecies s, int variant, boolean far) {
        WaderMesh m = new WaderMesh(s, variant, far);
        double L = s.length(), sl = L / 0.60, sx = s.span() / 1.40;
        int seg = far ? 6 : 12;
        boolean goose = s == WaderSpecies.BRANDGANS || s == WaderSpecies.RINGELGANS;
        double bw = goose ? 1.25 : 1.0, bh = goose ? 1.05 : 0.95;
        double rx = 0.075 * sl * bw, ry = 0.075 * sl * bh, rz = 0.22 * sl;
        double[] band = s == WaderSpecies.BRANDGANS ? s.breast() : (s.summerPatch(variant == 0 ? 6 : 12) ? s.breast() : null);
        double bz0 = s == WaderSpecies.BRANDGANS ? -0.22 * sl : -0.05 * sl, bz1 = s == WaderSpecies.BRANDGANS ? -0.12 * sl : 0.08 * sl;
        m.ellipsoid(0, 0, 0.02 * sl, rx, ry, rz, s.back(), s.belly(), seg + 2, 0, band, bz0, bz1);
        // Ringelgans: weißer Steiß; Säbelschnäbler und Austernfischer: schwarzer Rücken bzw. schwarze Bänder (über den Rücken)
        if (s == WaderSpecies.RINGELGANS) m.ellipsoid(0, -0.01 * sl, 0.20 * sl, 0.05 * sl, 0.045 * sl, 0.05 * sl, WaderSpecies.c(0.8, 0.8, 0.78), WaderSpecies.c(0.8, 0.8, 0.78), seg, 0, null, 0, 0);
        // Hals und Kopf (Teil 4), Drehpunkt am Halsansatz
        m.neck[0] = 0; m.neck[1] = 0.03 * sl; m.neck[2] = -0.17 * sl;
        double[] headC = {0, 0.05 * sl + s.neck() * 0.7, -0.22 * sl - s.neck() * 0.6};
        double[] headCol = s.headIn(variant == 0 ? 6 : 12);
        if (s.neck() > 0.02) {
            int k = far ? 2 : 5;
            for (int i = 0; i <= k; i++) {
                double t = i / (double) k;
                double r = (0.042 - 0.012 * t) * sl * (goose ? 1.05 : 0.9);
                m.ellipsoid(m.neck[0], m.neck[1] + (headC[1] - m.neck[1]) * t, m.neck[2] + (headC[2] - m.neck[2]) * t, r, r, r, headCol, headCol, seg - 2, 4, null, 0, 0);
            }
        }
        double hr = 0.048 * sl * (s == WaderSpecies.ALPENSTRANDLAEUFER ? 1.15 : 1.0);
        double[] headTop = s == WaderSpecies.SAEBELSCHNAEBLER ? s.wingTip() : headCol;   // Säbelschnäbler: schwarze Kappe
        m.ellipsoid(headC[0], headC[1], headC[2], hr, hr, hr * 1.15, headTop, s == WaderSpecies.RINGELGANS || s == WaderSpecies.LACHMOEWE ? headCol : s.head(), seg, 4, null, 0, 0);
        if (s == WaderSpecies.RINGELGANS)   // weißer Halsfleck
            for (int sd = -1; sd <= 1; sd += 2) m.ellipsoid(sd * 0.03 * sl, m.neck[1] + (headC[1] - m.neck[1]) * 0.55, m.neck[2] + (headC[2] - m.neck[2]) * 0.55, 0.014 * sl, 0.01 * sl, 0.02 * sl, WaderSpecies.c(0.8, 0.8, 0.78), WaderSpecies.c(0.8, 0.8, 0.78), 6, 4, null, 0, 0);
        if (s == WaderSpecies.LACHMOEWE && variant == 1)   // Winter: dunkler Fleck hinter dem Auge
            for (int sd = -1; sd <= 1; sd += 2) m.ellipsoid(sd * 0.042 * sl, headC[1] + 0.01 * sl, headC[2] + 0.02 * sl, 0.01 * sl, 0.01 * sl, 0.01 * sl, WaderSpecies.c(0.05, 0.05, 0.05), WaderSpecies.c(0.05, 0.05, 0.05), 6, 4, null, 0, 0);
        // Augen
        if (!far) for (int sd = -1; sd <= 1; sd += 2) m.ellipsoid(sd * 0.04 * sl, headC[1] + 0.012 * sl, headC[2] - 0.02 * sl, 0.007 * sl, 0.007 * sl, 0.007 * sl, GullMesh.EYE, GullMesh.EYE, 6, 4, null, 0, 0);
        // Schnabel als Kegel, mit Krümmung in zwei Gliedern
        double[] b0 = {0, headC[1] - 0.005 * sl, headC[2] - hr * 0.95};
        double bl = s.bill(), cv = s.billCurve();
        double[] bm = {0, b0[1] + 0.0, b0[2] - bl * 0.5}, bt = {0, b0[1] + cv * bl * 0.45 - 0.02 * bl, b0[2] - bl};
        double br = Math.max(0.0035, (goose ? 0.012 : 0.006) * sl);
        m.stick(b0, bm, br, br * 0.8, s.billColor(), 4, far ? 3 : 6);
        m.stick(bm, bt, br * 0.8, br * 0.25, s.billColor(), 4, far ? 3 : 6);
        // Schwanz
        m.plate(new double[][]{{-0.055 * sl * bw, 0.01 * sl, 0.18 * sl}, {0.055 * sl * bw, 0.01 * sl, 0.18 * sl}, {0.06 * sl * bw, 0.0, 0.30 * sl}, {-0.06 * sl * bw, 0.0, 0.30 * sl}},
                s == WaderSpecies.RINGELGANS ? s.head() : (s == WaderSpecies.AUSTERNFISCHER ? s.wingTip() : s.back()), s.belly(), 0);
        // Mantel
        m.plate(new double[][]{{-0.068 * sl * bw, 0.068 * sl * bh, -0.08 * sl}, {0.068 * sl * bw, 0.068 * sl * bh, -0.08 * sl}, {0.058 * sl * bw, 0.068 * sl * bh, 0.15 * sl},
                {-0.058 * sl * bw, 0.068 * sl * bh, 0.15 * sl}}, s == WaderSpecies.SAEBELSCHNAEBLER ? s.wingTip() : s.back(), s.belly(), 0);
        // Flügel: innen Rückenfarbe, außen Handschwingen; Säbelschnäbler mit schwarzem Band innen, Austernfischer mit weißem Flügelstreif
        double sy = 0.03 * sl * bh, x0 = 0.06 * sl * bw, xw = Math.max(x0 + 0.02, 0.44 * sx), xt = Math.max(xw + 0.02, 0.70 * sx);
        double chord = s.murmur() ? 0.9 : goose ? 1.05 : 1.0;
        m.shoulder[0] = x0; m.shoulder[1] = sy; m.shoulder[2] = -0.04 * sl;
        m.wrist = xw;
        for (int sd = -1; sd <= 1; sd += 2) {
            double[][] inner = {{sd * x0, sy, -0.09 * sl}, {sd * xw, sy, -0.07 * sl}, {sd * xw, sy, -0.07 * sl + 0.20 * sl * chord}, {sd * x0, sy, -0.09 * sl + 0.24 * sl * chord}};
            double[][] outer = {{sd * xw, sy, -0.07 * sl}, {sd * (xw + 0.55 * (xt - xw)), sy, -0.03 * sl}, {sd * xt, sy, 0.05 * sl}, {sd * (xw + 0.5 * (xt - xw)), sy, 0.10 * sl * chord}, {sd * xw, sy, 0.13 * sl * chord}};
            if (sd < 0) { inner = GullMesh.rev(inner); outer = GullMesh.rev(outer); }
            m.plate(inner, s == WaderSpecies.SAEBELSCHNAEBLER ? s.wingTip() : s.back(), s.belly(), 1);
            m.plate(outer, s.wingTip(), s == WaderSpecies.RINGELGANS ? s.back() : s.belly(), 2);
            if (!far && (s == WaderSpecies.AUSTERNFISCHER || s == WaderSpecies.ALPENSTRANDLAEUFER)) {   // weißer Flügelstreif oben
                double[] white = WaderSpecies.c(0.78, 0.78, 0.76);
                double zf = 0.03 * sl;
                m.tri(new double[]{sd * x0, sy + 0.003, zf + 0.02 * sl}, new double[]{sd * xt * 0.95, sy + 0.003, 0.05 * sl}, new double[]{sd * x0, sy + 0.003, zf + 0.05 * sl}, white, sd < 0 ? 2 : 2);
                m.tri(new double[]{sd * x0, sy + 0.003, zf + 0.02 * sl}, new double[]{sd * x0, sy + 0.003, zf + 0.05 * sl}, new double[]{sd * xt * 0.95, sy + 0.003, 0.05 * sl}, white, 2);
            }
            // Beine: vom Bauch nach unten, Zehen
            double lx = sd * 0.03 * sl * bw, top = -ry * 0.6, foot = -ry - s.legLength();
            m.stick(new double[]{lx, top, 0.02 * sl}, new double[]{lx, foot, 0.01 * sl}, Math.max(0.003, 0.008 * sl), Math.max(0.0025, 0.005 * sl), s.legColor(), 3, far ? 3 : 5);
            double tl = Math.max(0.015, 0.05 * sl);
            m.tri(new double[]{lx - tl * 0.6, foot, -tl}, new double[]{lx + tl * 0.6, foot, -tl}, new double[]{lx, foot, tl * 0.3}, s.legColor(), 3);
            m.tri(new double[]{lx + tl * 0.6, foot, -tl}, new double[]{lx - tl * 0.6, foot, -tl}, new double[]{lx, foot, tl * 0.3}, s.legColor(), 3);
            m.footY = foot;
        }
        return m;
    }
}
