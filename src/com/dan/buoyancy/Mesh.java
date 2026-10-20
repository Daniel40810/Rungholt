package com.dan.buoyancy;

import java.util.Arrays;

/**
 * Netze der Körper, rein in Java gebaut (Paketvertrag: keine Dateien). Ecken: Ort (3), Normale (3), Farbe (3, linear),
 * Stoff (1): 0 Anstrich, 1 Fensterglas (leuchtet nachts schwach), 2 Lampe (Farbe und Stärke von außen), 3 Anzeige
 * auf der Brücke. Modellkoordinaten: x Steuerbord, y oben, z achteraus (also s = −z).
 */
public final class Mesh {
    public static final int FLOATS = 10;
    public static final float PAINT = 0, GLASS = 1, LAMP = 2, SCREEN = 3;
    private float[] v = new float[FLOATS * 1024];
    private int n;

    public float[] vertices() { return Arrays.copyOf(v, n * FLOATS); }
    public int count() { return n; }
    public int triangles() { return n / 3; }

    private void put(double[] p, double[] nn, double[] c, float m) {
        if ((n + 1) * FLOATS > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = n * FLOATS;
        v[o] = (float) p[0]; v[o + 1] = (float) p[1]; v[o + 2] = (float) p[2];
        v[o + 3] = (float) nn[0]; v[o + 4] = (float) nn[1]; v[o + 5] = (float) nn[2];
        v[o + 6] = (float) c[0]; v[o + 7] = (float) c[1]; v[o + 8] = (float) c[2];
        v[o + 9] = m;
        n++;
    }

    /** Dreieck gegen den Uhrzeigersinn von außen, flache Normale. */
    public void tri(double[] a, double[] b, double[] c, double[] col, float m) {
        double[] nn = normal(a, b, c);
        put(a, nn, col, m); put(b, nn, col, m); put(c, nn, col, m);
    }

    /** Dreieck mit Normalen je Ecke. */
    public void tri(double[] a, double[] na, double[] b, double[] nb, double[] c, double[] nc, double[] col, float m) {
        put(a, na, col, m); put(b, nb, col, m); put(c, nc, col, m);
    }

    public void quad(double[] a, double[] b, double[] c, double[] d, double[] col, float m) {
        tri(a, b, c, col, m);
        tri(a, c, d, col, m);
    }

    static double[] normal(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        double x = uy * wz - uz * wy, y = uz * wx - ux * wz, z = ux * wy - uy * wx, l = Math.sqrt(x * x + y * y + z * z);
        return l < 1e-12 ? new double[]{0, 1, 0} : new double[]{x / l, y / l, z / l};
    }

    static double[] p(double x, double y, double z) { return new double[]{x, y, z}; }

    /** Quader um (cx, cy, cz) mit halben Kanten. */
    public void box(double cx, double cy, double cz, double hx, double hy, double hz, double[] col, float m) {
        double x0 = cx - hx, x1 = cx + hx, y0 = cy - hy, y1 = cy + hy, z0 = cz - hz, z1 = cz + hz;
        quad(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1), col, m);   // +z
        quad(p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0), col, m);   // −z
        quad(p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), col, m);   // +x
        quad(p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0), col, m);   // −x
        quad(p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0), col, m);   // +y
        quad(p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1), col, m);   // −y
    }

    /** Kegelstumpf um die y-Achse durch (cx, cz), Halbmesser r0 unten und r1 oben, mit Deckeln; Farbe je Segment. */
    public void cylinder(double cx, double cz, double r0, double r1, double y0, double y1, int seg, double[][] cols, float m, boolean caps) {
        double slope = (r0 - r1) / Math.max(1e-6, y1 - y0);
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            double[] c = cols[i % cols.length];
            double s0 = Math.sin(a0), c0 = Math.cos(a0), s1 = Math.sin(a1), c1 = Math.cos(a1);
            double[] p00 = p(cx + s0 * r0, y0, cz + c0 * r0), p01 = p(cx + s1 * r0, y0, cz + c1 * r0), p11 = p(cx + s1 * r1, y1, cz + c1 * r1), p10 = p(cx + s0 * r1, y1, cz + c0 * r1);
            double l0 = Math.sqrt(1 + slope * slope);
            double[] n0 = {s0 / l0, slope / l0, c0 / l0}, n1 = {s1 / l0, slope / l0, c1 / l0};
            tri(p00, n0, p01, n1, p11, n1, c, m);
            tri(p00, n0, p11, n1, p10, n0, c, m);
            if (caps) {
                if (r1 > 1e-4) tri(p(cx, y1, cz), p10, p11, c, m);
                if (r0 > 1e-4) tri(p(cx, y0, cz), p01, p00, c, m);
            }
        }
    }

    /** Kugel um (cx, cy, cz). */
    public void sphere(double cx, double cy, double cz, double r, int seg, double[] col, float m) {
        int rings = Math.max(3, seg / 2);
        for (int j = 0; j < rings; j++) {
            double t0 = Math.PI * j / rings, t1 = Math.PI * (j + 1) / rings;
            for (int i = 0; i < seg; i++) {
                double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double[] n00 = sph(t0, a0), n01 = sph(t0, a1), n11 = sph(t1, a1), n10 = sph(t1, a0);
                double[] q00 = add(cx, cy, cz, n00, r), q01 = add(cx, cy, cz, n01, r), q11 = add(cx, cy, cz, n11, r), q10 = add(cx, cy, cz, n10, r);
                tri(q00, n00, q10, n10, q11, n11, col, m);
                tri(q00, n00, q11, n11, q01, n01, col, m);
            }
        }
    }

    static double[] sph(double t, double a) { return new double[]{Math.sin(t) * Math.sin(a), Math.cos(t), Math.sin(t) * Math.cos(a)}; }
    static double[] add(double x, double y, double z, double[] d, double r) { return new double[]{x + d[0] * r, y + d[1] * r, z + d[2] * r}; }

    /**
     * Seit Glacier Phase 10: ein Balken von a nach b (Mitte der Unterkante), w breit und h hoch, die Höhe nach oben (y),
     * die Breite waagerecht quer zum Balken; ein senkrechter Balken bekommt die Breite in x und Tiefe in z.
     */
    public void beam(double[] a, double[] b, double w, double h, double[] col, float m) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return;
        double[] t = {dx / l, dy / l, dz / l}, side, up;
        if (Math.abs(t[1]) > 0.95) { side = new double[]{w / 2, 0, 0}; up = new double[]{0, 0, h}; double[] o = {a[0], a[1], a[2] - h / 2}, e = {b[0], b[1], b[2] - h / 2}; a = o; b = e; }
        else {
            double sx = -t[2], sz = t[0], sl = Math.hypot(sx, sz);
            side = new double[]{sx / sl * w / 2, 0, sz / sl * w / 2};
            // Höhe: senkrecht zu Balken und Quere, nach oben
            double cx = side[1] * t[2] - side[2] * t[1], cy = side[2] * t[0] - side[0] * t[2], cz = side[0] * t[1] - side[1] * t[0], cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (cy < 0) { cx = -cx; cy = -cy; cz = -cz; }
            up = new double[]{cx / cl * h, cy / cl * h, cz / cl * h};
        }
        double[] a0 = sub(a, side), a1 = plus(a, side), b0 = sub(b, side), b1 = plus(b, side);
        double[] a2 = plus(a1, up), a3 = plus(a0, up), b2 = plus(b1, up), b3 = plus(b0, up);
        quad(a0, b0, b1, a1, col, m);   // unten
        quad(a3, a2, b2, b3, col, m);   // oben
        quad(a1, b1, b2, a2, col, m);
        quad(b0, a0, a3, b3, col, m);
        quad(a0, a1, a2, a3, col, m);
        quad(b1, b0, b3, b2, col, m);
    }

    static double[] plus(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    /**
     * Seit Glacier Phase 10: der Ausflugskatamaran ({@link Catamaran}) in Modellkoordinaten (Wasserlinie y = 0):
     * zwei Rümpfe (weiß, unter Wasser rotbraun), das Brückendeck, der Salon mit Fensterband, das Sonnendeck mit Geländer
     * und das Steuerhaus darauf, vorn das Netz zwischen den Rümpfen.
     */
    public static Mesh catamaran(Catamaran c) {
        Mesh m = new Mesh();
        double L = c.length, hs = c.hullSpacing / 2, hb = c.hullBeam / 2, T = c.draftDesign, D = c.deck;
        double[] white = WHITE, red = ANTIFOUL, stripe = {0.02, 0.10, 0.32}, glass = GLASSCOL, deck = {0.20, 0.20, 0.19}, rail = {0.55, 0.56, 0.56};
        int ns = 24;
        for (int side = -1; side <= 1; side += 2) {
            double q0 = side * hs;
            double[][] prev = null;
            for (int i = 0; i <= ns; i++) {
                double s = -L / 2 + L * i / ns, u = s / (L / 2);
                double w = hb * (u > 0.4 ? Math.sqrt(Math.max(0, 1 - Math.pow((u - 0.4) / 0.6, 2))) : u < -0.9 ? 0.9 : 1);
                double t = T * (u > 0.6 ? 1 - 0.5 * (u - 0.6) / 0.4 : 1), top = D + (u > 0.7 ? 0.5 * (u - 0.7) / 0.3 : 0);
                // Schnitt: oben bei +x, Wasserlinie, Kiel, Wasserlinie, oben bei −x
                double[][] sec = {{q0 + w, top, -s}, {q0 + w * 0.95, 0, -s}, {q0 + w * 0.25, -t, -s}, {q0 - w * 0.25, -t, -s}, {q0 - w * 0.95, 0, -s}, {q0 - w, top, -s}};
                if (prev != null)
                    for (int k = 0; k + 1 < sec.length; k++) {
                        double[] col = k == 0 || k == 4 ? white : red;
                        // der Schnitt läuft von +x nach −x; so zeigen die Flächen nach außen
                        m.quad(prev[k + 1], sec[k + 1], sec[k], prev[k], col, PAINT);
                    }
                if (prev != null) {
                    // Deck des Rumpfs
                    m.quad(prev[0], sec[0], sec[5], prev[5], deck, PAINT);
                }
                prev = sec;
            }
            // blauer Streifen außen und Heckspiegel
            m.beam(p(q0 + side * (hb + 0.01), D - 0.45, L / 2 - 0.2), p(q0 + side * (hb * 0.95 + 0.01), D - 0.45, -L * 0.2), 0.04, 0.25, stripe, PAINT);
            m.quad(p(q0 - hb * 0.22, -T, L / 2), p(q0 + hb * 0.22, -T, L / 2), p(q0 + hb * 0.9, D, L / 2), p(q0 - hb * 0.9, D, L / 2), white, PAINT);
        }
        // Brückendeck zwischen den Rümpfen
        // 4 cm über dem Deck der Rümpfe, sonst liegen zwei Flächen in einer Ebene und flackern (Befund Phase 10)
        m.box(0, D - 0.13, -(-L / 2 + 1.5 + (L * 0.75 - 1.5) / 2), c.beam / 2, 0.17, (L * 0.75 - 1.5) / 2, white, PAINT);
        // Netz vorn (dunkel, flach)
        m.box(0, D - 0.05, -(L * 0.375 + L * 0.06), hs - hb, 0.03, L * 0.06, DARK, PAINT);
        // Salon: Brüstung, Fensterband, Dach
        double sa = -L / 2 + 2.2, sb = L * 0.22, sm = -(sa + sb) / 2, sh = (sb - sa) / 2, bw = c.beam / 2 - 0.35;
        m.box(0, D + 0.45, sm, bw, 0.45, sh, white, PAINT);
        m.box(0, (D + 0.9 + c.sunDeck - 0.4) / 2, sm, bw - 0.05, (c.sunDeck - 0.4 - D - 0.9) / 2, sh - 0.05, glass, GLASS);   // bis unter das Dach
        m.box(0, c.sunDeck - 0.2, sm, c.beam / 2 - 0.15, 0.2, sh + 0.4, white, PAINT);
        // Steuerhaus vorn auf dem Sonnendeck
        double wa = sb - 4.2, wb = sb - 1.2;
        m.box(0, c.sunDeck + 0.5, -(wa + wb) / 2, 1.7, 0.5, (wb - wa) / 2, white, PAINT);
        m.box(0, c.sunDeck + 1.5, -(wa + wb) / 2, 1.65, 0.5, (wb - wa) / 2 - 0.05, glass, GLASS);
        m.box(0, c.sunDeck + 2.1, -(wa + wb) / 2, 1.85, 0.1, (wb - wa) / 2 + 0.3, white, PAINT);
        m.cylinder(0, -(wa + 0.6), 0.06, 0.04, c.sunDeck + 2.2, c.sunDeck + 4.4, 6, new double[][]{rail}, PAINT, false);
        // Geländer ums Sonnendeck: Pfosten alle 1,5 m, Handlauf und Mittelstab
        double x0 = -(c.beam / 2 - 0.25), x1 = c.beam / 2 - 0.25, z0 = -(sb + 0.3), z1 = -(sa - 0.3), y = c.sunDeck, yr = c.rail;
        double[][] ring = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}, {x0, z0}};
        for (int k = 0; k + 1 < ring.length; k++) {
            double ax = ring[k][0], az = ring[k][1], bx = ring[k + 1][0], bz = ring[k + 1][1], l = Math.hypot(bx - ax, bz - az);
            int posts = Math.max(1, (int) Math.round(l / 1.5));
            for (int j = 0; j < posts; j++) {
                double f = j / (double) posts, px = ax + (bx - ax) * f, pz = az + (bz - az) * f;
                m.beam(p(px, y, pz), p(px, yr, pz), 0.05, 0.05, rail, PAINT);
            }
            m.beam(p(ax, yr - 0.03, az), p(bx, yr - 0.03, bz), 0.05, 0.05, rail, PAINT);
            m.beam(p(ax, y + 0.5, az), p(bx, y + 0.5, bz), 0.025, 0.025, rail, PAINT);
        }
        // Treppe achtern (schräger Balken) und Geländer vorn am Brückendeck
        m.beam(p(x1 - 0.6, D, z1 + 1.4), p(x1 - 0.6, c.sunDeck, z1 - 0.4), 0.8, 0.12, deck, PAINT);
        for (int side = -1; side <= 1; side += 2) {
            double q = side * (hs + hb * 0.55);
            m.beam(p(q, D + 1.0, -sb), p(q, D + 1.0, -L * 0.36), 0.04, 0.04, rail, PAINT);
            for (double sp = sb; sp <= L * 0.36 + 0.01; sp += (L * 0.36 - sb) / 3) m.beam(p(q, D, -sp), p(q, D + 1.0, -sp), 0.04, 0.04, rail, PAINT);
        }
        return m;
    }

    /**
     * Seit Rungholt Phase 11: die Kogge. Geklinkerter Rumpf mit flachem Boden, Sprung zu den Steven, geraden Steven
     * (der vordere stark geneigt), Achterkastell, Mast mit Rah; das Segel aufgetucht an der Rah (sail = 0) oder gesetzt
     * (1, mit Bauch nach vorn, rot-weiß gestreift [G]); Ruder am Achtersteven. Wasserlinie bei y = 0.
     */
    public static Mesh cog(Cog c, double sail) {
        Mesh m = new Mesh();
        double L = c.length, B = c.beam, T = c.draftDesign, F = c.freeboard;
        double[] hull = {0.075, 0.048, 0.03}, tar = {0.03, 0.022, 0.016}, wood = {0.16, 0.11, 0.065}, deck = {0.20, 0.15, 0.10},
                cloth = {0.62, 0.56, 0.46}, red = {0.40, 0.06, 0.04}, rope = {0.10, 0.08, 0.05};
        int ns = 28;
        double[][] prev = null;
        double shift = 0;
        for (int i = 0; i <= ns; i++) {
            double s = -L / 2 + L * i / ns, u = s / (L / 2);
            double w = halfOuter(u, B), t = T * (1 - 0.35 * Math.pow(Math.abs(u), 3)), top = F + 1.1 * u * u * (u > 0 ? 1.1 : 0.9);
            double wb = w * 0.6;
            // Schnitt von Steuerbord oben über den flachen Boden nach Backbord oben (Modell: x Steuerbord, z achteraus)
            double[][] sec = {{w * 1.04, top, -s}, {w, 0.6 * top, -s}, {w * 0.97, 0, -s}, {w * 0.85, -t * 0.6, -s}, {wb, -t, -s}, {-wb, -t, -s},
                    {-w * 0.85, -t * 0.6, -s}, {-w * 0.97, 0, -s}, {-w, 0.6 * top, -s}, {-w * 1.04, top, -s}};
            if (prev != null)
                for (int k = 0; k + 1 < sec.length; k++) {
                    double[] col = k >= 2 && k <= 6 ? tar : ((k + i / 3) % 2 == 0 ? hull : wood);   // Plankengänge abwechselnd
                    m.quad(prev[k + 1], sec[k + 1], sec[k], prev[k], col, PAINT);
                }
            // Deck 0,9 m unter dem Schanzkleid, Schanzkleid innen
            if (prev != null) {
                double dy = -0.9;
                double[] a = {prev[0][0] * 0.94, prev[0][1] + dy, prev[0][2]}, b = {sec[0][0] * 0.94, sec[0][1] + dy, sec[0][2]},
                        cc = {sec[9][0] * 0.94, sec[9][1] + dy, sec[9][2]}, d = {prev[9][0] * 0.94, prev[9][1] + dy, prev[9][2]};
                m.quad(a, b, cc, d, deck, PAINT);
                double[] ai = {prev[0][0] * 0.94, prev[0][1], prev[0][2]}, bi = {sec[0][0] * 0.94, sec[0][1], sec[0][2]};
                m.quad(ai, bi, b, a, wood, PAINT);
                double[] ci = {sec[9][0] * 0.94, sec[9][1], sec[9][2]}, di = {prev[9][0] * 0.94, prev[9][1], prev[9][2]};
                m.quad(ci, di, d, cc, wood, PAINT);
                // Oberkante des Schanzkleids
                m.quad(prev[0], sec[0], bi, ai, wood, PAINT);
                m.quad(ci, sec[9], prev[9], di, wood, PAINT);
            }
            prev = sec;
            shift = top;
        }
        // Steven: vorn stark geneigt, achtern steil; Ruder am Achtersteven
        double bowTop = F + 1.2 * 1.1, sternTop = F + 1.0;
        m.beam(p(0, -T * 0.7, -L / 2 + 0.3), p(0, bowTop + 0.6, -L / 2 - 2.2), 0.35, 0.35, wood, PAINT);
        m.beam(p(0, -T * 0.7, L / 2 - 0.3), p(0, sternTop + 0.4, L / 2 + 0.6), 0.35, 0.35, wood, PAINT);
        m.box(0, (sternTop - T * 0.8) / 2, L / 2 + 0.9, 0.12, (sternTop + T * 0.8) / 2, 0.75, wood, PAINT);
        // Achterkastell: Plattform auf Pfosten mit Brüstung
        double cz0 = L / 2 - 7.0, cz1 = L / 2 - 0.4, cy = sternTop + 1.9, cw = halfOuter(-0.8, B) * 0.98;
        m.box(0, cy, (cz0 + cz1) / 2, cw, 0.12, (cz1 - cz0) / 2, deck, PAINT);
        m.box(cw - 0.05, cy + 0.55, (cz0 + cz1) / 2, 0.05, 0.45, (cz1 - cz0) / 2, wood, PAINT);
        m.box(-cw + 0.05, cy + 0.55, (cz0 + cz1) / 2, 0.05, 0.45, (cz1 - cz0) / 2, wood, PAINT);
        m.box(0, cy + 0.55, cz1 - 0.05, cw, 0.45, 0.05, wood, PAINT);
        m.box(0, cy + 0.55, cz0 + 0.05, cw, 0.45, 0.05, wood, PAINT);
        m.box(0, (cy + F - 0.9) / 2, cz0 + 0.3, cw * 0.9, (cy - F + 0.9) / 2, 0.08, wood, PAINT);   // Schott unter dem Kastell
        for (int k = -1; k <= 1; k += 2) m.beam(p(k * (cw - 0.3), F - 0.9, cz0 + 0.2), p(k * (cw - 0.3), cy, cz0 + 0.2), 0.25, 0.25, wood, PAINT);
        // Mast, Rah, Wanten
        double mz = -1.2, mh = c.mastHeight, yard = mh - 3.5;
        m.cylinder(0, mz, 0.32, 0.18, F - 1.0, mh, 8, new double[][]{wood}, PAINT, true);
        m.beam(p(-7.2, yard, mz - 0.35), p(7.2, yard, mz - 0.35), 0.22, 0.22, wood, PAINT);
        m.box(0, mh - 1.0, mz, 0.55, 0.45, 0.55, wood, PAINT);   // Mars (Ausguck)
        for (int k = -1; k <= 1; k += 2)
            for (int j = 0; j < 3; j++) {
                double sz = mz + 1.2 + j * 1.3;
                m.beam(p(k * halfOuter(-0.1, B) * 1.02, F + 0.1, sz), p(k * 0.25, mh - 2.0, mz), 0.04, 0.04, rope, PAINT);
            }
        m.beam(p(0, mh - 1.8, mz), p(0, bowTop + 0.5, -L / 2 - 2.0), 0.05, 0.05, rope, PAINT);   // Vorstag
        // Segel
        double sw = 13, sh = 11.5;
        if (sail < 0.05) {
            m.beam(p(-6.5, yard - 0.42, mz - 0.6), p(6.5, yard - 0.42, mz - 0.6), 0.55, 0.45, cloth, PAINT);
        } else {
            double hgt = sh * Math.min(1, sail), belly = 1.4 * sail;
            int nu = 10, nv = 8;
            for (int a = 0; a < nu; a++)
                for (int b = 0; b < nv; b++) {
                    double[] p00 = sailPoint(a, b, nu, nv, sw, hgt, yard, mz, belly), p10 = sailPoint(a + 1, b, nu, nv, sw, hgt, yard, mz, belly),
                            p11 = sailPoint(a + 1, b + 1, nu, nv, sw, hgt, yard, mz, belly), p01 = sailPoint(a, b + 1, nu, nv, sw, hgt, yard, mz, belly);
                    double[] col = (a / 2) % 2 == 0 ? cloth : red;
                    m.quad(p00, p10, p11, p01, col, PAINT);   // vorn
                    m.quad(p01, p11, p10, p00, col, PAINT);   // hinten
                }
        }
        return m;
    }

    /** Ein Punkt des gesetzten Segels: a quer, b von der Rah nach unten; Bauch nach vorn (−z). */
    private static double[] sailPoint(int a, int b, int nu, int nv, double sw, double hgt, double yard, double mz, double belly) {
        double fu = a / (double) nu, fv = b / (double) nv;
        double bulge = belly * Math.sin(Math.PI * fu) * Math.sin(Math.PI * Math.min(1, fv * 0.9 + 0.1));
        return p(-sw / 2 + sw * fu, yard - 0.4 - hgt * fv, mz - 0.6 - bulge);
    }

    /** Halbe Breite des Rumpfs außen an der Stelle u (−1 achtern … 1 vorn). */
    static double halfOuter(double u, double B) { return Cog.halfBeam(u, B) * 1.03; }

    // ------------------------------------------------------------------ Farben (linear)
    static final double[] RED = {0.42, 0.03, 0.02}, GREEN = {0.02, 0.22, 0.06}, WHITE = {0.72, 0.72, 0.7}, BLACK = {0.03, 0.03, 0.035},
            YELLOW = {0.65, 0.42, 0.02}, GREY = {0.18, 0.19, 0.2}, DARK = {0.06, 0.065, 0.07}, ANTIFOUL = {0.22, 0.045, 0.03},
            HULL = {0.025, 0.045, 0.11}, DECK = {0.11, 0.14, 0.11}, HATCH = {0.09, 0.2, 0.12}, HOUSE = {0.66, 0.66, 0.63}, GLASSCOL = {0.02, 0.025, 0.03},
            FUNNEL = {0.05, 0.12, 0.3}, LAMPCOL = {1, 1, 1};

    // ------------------------------------------------------------------ Tonnen

    /** Eine Tonne der Art k in Modellkoordinaten (Wasserlinie bei y = 0). */
    public static Mesh buoy(Buoy.Kind k) {
        Mesh m = new Mesh();
        double r = Buoy.D / 2;
        double[][] body = switch (k) {
            case BACKBORD -> new double[][]{RED};
            case STEUERBORD -> new double[][]{GREEN};
            case ANSTEUERUNG -> new double[][]{RED, RED, WHITE, WHITE};
            case NORD -> new double[][]{BLACK};
        };
        if (k == Buoy.Kind.NORD) {
            m.cylinder(0, 0, r, r, -Buoy.DRAFT, 0.0, 24, new double[][]{YELLOW}, PAINT, true);
            m.cylinder(0, 0, r, r, 0.0, Buoy.FREEBOARD, 24, new double[][]{BLACK}, PAINT, false);
        } else {
            m.cylinder(0, 0, r, r, -Buoy.DRAFT, Buoy.FREEBOARD, 24, body, PAINT, false);
        }
        m.cylinder(0, 0, r, r * 0.55, Buoy.FREEBOARD, Buoy.FREEBOARD + 0.3, 24, k == Buoy.Kind.NORD ? new double[][]{BLACK} : body, PAINT, true);
        // Gittergerüst: drei Beine, zwei Ringe
        double[] legCol = k == Buoy.Kind.ANSTEUERUNG ? RED : k == Buoy.Kind.NORD ? BLACK : body[0];
        double yb = Buoy.FREEBOARD + 0.3, yt = Buoy.LIGHT_Y - 0.3;
        for (int i = 0; i < 3; i++) {
            double a = 2 * Math.PI * i / 3 + 0.4;
            double xb = Math.sin(a) * 0.55, zb = Math.cos(a) * 0.55, xt = Math.sin(a) * 0.22, zt = Math.cos(a) * 0.22;
            leg(m, xb, yb, zb, xt, yt, zt, 0.05, legCol);
        }
        m.cylinder(0, 0, 0.42, 0.42, (yb + yt) / 2 - 0.04, (yb + yt) / 2 + 0.04, 12, new double[][]{legCol}, PAINT, true);
        m.cylinder(0, 0, 0.3, 0.3, yt, yt + 0.08, 12, new double[][]{legCol}, PAINT, true);
        // Laterne
        m.cylinder(0, 0, 0.14, 0.14, Buoy.LIGHT_Y - 0.22, Buoy.LIGHT_Y + 0.22, 12, new double[][]{LAMPCOL}, LAMP, false);
        m.cylinder(0, 0, 0.17, 0.05, Buoy.LIGHT_Y + 0.22, Buoy.LIGHT_Y + 0.32, 12, new double[][]{DARK}, PAINT, true);
        // Toppzeichen
        double ty = Buoy.LIGHT_Y + 0.45;
        switch (k) {
            case BACKBORD -> m.cylinder(0, 0, 0.28, 0.28, ty, ty + 0.6, 16, new double[][]{RED}, PAINT, true);
            case STEUERBORD -> m.cylinder(0, 0, 0.34, 0.0, ty, ty + 0.62, 16, new double[][]{GREEN}, PAINT, true);
            case ANSTEUERUNG -> m.sphere(0, ty + 0.32, 0, 0.3, 14, RED, PAINT);
            case NORD -> {
                m.cylinder(0, 0, 0.3, 0.0, ty, ty + 0.45, 16, new double[][]{BLACK}, PAINT, true);
                m.cylinder(0, 0, 0.3, 0.0, ty + 0.55, ty + 1.0, 16, new double[][]{BLACK}, PAINT, true);
            }
        }
        m.box(0, ty - 0.08, 0, 0.03, 0.12, 0.03, DARK, PAINT);
        return m;
    }

    /** Ein dünner Stab von a nach b (vierkantig). */
    static void leg(Mesh m, double ax, double ay, double az, double bx, double by, double bz, double w, double[] col) {
        double[] d = {bx - ax, by - ay, bz - az};
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double[] u = {d[0] / l, d[1] / l, d[2] / l};
        double[] s = norm(cross(u, Math.abs(u[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}));
        double[] t = cross(u, s);
        double[][] c = new double[8][];
        for (int i = 0; i < 4; i++) {
            double sx = (i == 0 || i == 3 ? -w : w), tx = (i < 2 ? -w : w);
            c[i] = p(ax + s[0] * sx + t[0] * tx, ay + s[1] * sx + t[1] * tx, az + s[2] * sx + t[2] * tx);
            c[i + 4] = p(bx + s[0] * sx + t[0] * tx, by + s[1] * sx + t[1] * tx, bz + s[2] * sx + t[2] * tx);
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            m.quad(c[i], c[j], c[j + 4], c[i + 4], col, PAINT);
        }
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    // ------------------------------------------------------------------ Schiff

    /** Rumpf mit Sprung, Wasserpass in drei Farben, Deck, Lukendeckel, Deckshaus mit Fenstern, Schornstein, Masten, Lampen. */
    public static Mesh ship(Ship sh) {
        Mesh m = new Mesh();
        double L = sh.length, F = sh.freeboard;
        int ns = 48;
        // Spantriss: Kiel, Kimm, Seite, Wasserpass (0 bis 0,3 m rot, bis 0,8 m schwarz), Deckskante
        double[] ky = {-1.0, -1.0, -0.82, -0.45, 0.0}, kw = {0.0, 0.72, 0.97, 1.0, 1.0};
        int np = 8;
        double[][][] pts = new double[ns + 1][np][];
        double[][][] nrm = new double[ns + 1][np][];
        for (int i = 0; i <= ns; i++) {
            double s = -L / 2 + L * i / ns;
            double hb = i == ns ? 0 : Ship.halfBeam(s, L, sh.beam), t = Ship.draftAt(s, L, sh.draftDesign), deck = deckAt(s, L, F);
            // vorn ist die Seite über dem Wasser ausgestellt (breiter als die Wasserlinie)
            double hd = i == ns ? 0 : Math.max(hb, s > 0.3 * L ? Ship.halfBeam(s - 3.5, L, sh.beam) * 0.97 : hb * 1.02);
            for (int j = 0; j < 5; j++) pts[i][j] = p(hb * kw[j], ky[j] * t, -s);
            pts[i][5] = p(hb + (hd - hb) * 0.3 / deck, 0.3, -s);
            pts[i][6] = p(hb + (hd - hb) * 0.8 / deck, 0.8, -s);
            pts[i][7] = p(hd, deck, -s);
        }
        // Normalen aus dem Gitter (glatt): längs × quer zeigt nach außen
        for (int i = 0; i <= ns; i++)
            for (int j = 0; j < np; j++) {
                double[] a = pts[Math.max(0, i - 1)][j], b = pts[Math.min(ns, i + 1)][j];
                int j0 = Math.max(0, j - 1), j1 = Math.min(np - 1, j + 1);
                while (j0 > 0 && same(pts[i][j0], pts[i][j])) j0--;
                while (j1 < np - 1 && same(pts[i][j1], pts[i][j])) j1++;
                double[] c = pts[i][j0], d = pts[i][j1];
                double[] tl = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, ts = {d[0] - c[0], d[1] - c[1], d[2] - c[2]};
                double[] nn = cross(tl, ts);
                double l = Math.sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2]);
                nrm[i][j] = l < 1e-9 ? new double[]{1, 0, 0} : new double[]{nn[0] / l, nn[1] / l, nn[2] / l};
            }
        for (int i = 0; i < ns; i++)
            for (int j = 0; j < np - 1; j++) {
                if (same(pts[i][j], pts[i][j + 1]) && same(pts[i + 1][j], pts[i + 1][j + 1])) continue;
                double ymid = (pts[i][j][1] + pts[i][j + 1][1]) / 2;
                double[] col = ymid < 0.3 ? ANTIFOUL : ymid < 0.8 ? BLACK : HULL;
                // Steuerbord (x > 0): von außen gegen den Uhrzeigersinn
                double[] a = pts[i][j], b = pts[i + 1][j], c = pts[i + 1][j + 1], d = pts[i][j + 1];
                m.tri(a, nrm[i][j], b, nrm[i + 1][j], c, nrm[i + 1][j + 1], col, PAINT);
                m.tri(a, nrm[i][j], c, nrm[i + 1][j + 1], d, nrm[i][j + 1], col, PAINT);
                // Backbord gespiegelt, Wicklung umgedreht
                m.tri(mir(a), mir(nrm[i][j]), mir(c), mir(nrm[i + 1][j + 1]), mir(b), mir(nrm[i + 1][j]), col, PAINT);
                m.tri(mir(a), mir(nrm[i][j]), mir(d), mir(nrm[i][j + 1]), mir(c), mir(nrm[i + 1][j + 1]), col, PAINT);
            }
        // Deck und Spiegelheck
        for (int i = 0; i < ns; i++) {
            double[] a = pts[i][np - 1], b = pts[i + 1][np - 1];
            m.quad(mir(a), a, b, mir(b), DECK, PAINT);
        }
        for (int j = 0; j < np - 1; j++) {
            double[] a = pts[0][j], b = pts[0][j + 1];
            if (same(a, b)) continue;
            double ymid = (a[1] + b[1]) / 2;
            double[] col = ymid < 0.3 ? ANTIFOUL : ymid < 0.8 ? BLACK : HULL;
            m.quad(mir(a), a, b, mir(b), col, PAINT);
        }
        // Lukendeckel
        double hw = sh.beam / 2 - 1.3;
        for (double[] h : new double[][]{{-0.19, 0.06}, {0.08, 0.33}}) {
            double s0 = h[0] * L, s1 = h[1] * L;
            m.box(0, F + 0.8, -(s0 + s1) / 2, hw, 0.8, (s1 - s0) / 2, HATCH, PAINT);
        }
        // Back (vorn erhöht)
        double fs = 0.42 * L;
        m.box(0, deckAt(fs, L, F) + 0.6, -(fs + L * 0.47) / 2, Ship.halfBeam(fs, L, sh.beam) - 0.4, 0.6, (L * 0.47 - fs) / 2, HULL, PAINT);
        // Deckshaus: drei Decks und das Steuerhaus
        double hf = sh.houseFront(), hbk = sh.houseBack(), fl = sh.bridgeFloor(), hwd = sh.beam / 2 - 0.9;
        m.box(0, (F + fl) / 2, -(hf + hbk) / 2, hwd, (fl - F) / 2, (hf - hbk) / 2, HOUSE, PAINT);
        m.box(0, fl + 1.35, -(hf + hbk + 1.5) / 2, hwd - 0.3, 1.35, (hf - hbk - 1.5) / 2, HOUSE, PAINT);
        // Brückennocken
        m.box(0, fl - 0.1, -(hf - 1.2), sh.beam / 2 + 1.0, 0.1, 1.2, HOUSE, PAINT);
        m.box(0, fl + 2.82, -(hf + hbk + 1.5) / 2, hwd, 0.12, (hf - hbk - 1.5) / 2 + 0.3, HOUSE, PAINT);
        // Fenster der Wohndecks (vorn und an den Seiten) und die Frontscheiben des Steuerhauses
        for (int d = 0; d < 2; d++) {
            double yy = F + 1.6 + d * 2.3;
            for (int k = -3; k <= 3; k++) m.box(k * 1.6, yy, -hf - 0.02, 0.45, 0.4, 0.03, GLASSCOL, GLASS);
            for (int k = 0; k < 4; k++) {
                double zz = -(hbk + 1.5 + k * 2.2);
                m.box(hwd + 0.02, yy, zz, 0.03, 0.4, 0.45, GLASSCOL, GLASS);
                m.box(-hwd - 0.02, yy, zz, 0.03, 0.4, 0.45, GLASSCOL, GLASS);
            }
        }
        // Scheiben des Steuerhauses nur als Außenhaut (von innen verworfen, man sieht hindurch)
        for (double[] w : sh.windows) {
            double x0 = w[1] - w[2] / 2 + 0.05, x1 = w[1] + w[2] / 2 - 0.05, zz = -w[0] - 0.03;
            m.quad(p(x1, w[3], zz), p(x0, w[3], zz), p(x0, w[4], zz), p(x1, w[4], zz), GLASSCOL, GLASS);
        }
        for (int k = 0; k < 3; k++) {
            double z0 = -(hf - 0.3 - k * 2.0), z1 = z0 + 1.4, xr = hwd - 0.27, y0 = fl + 1.1, y1 = fl + 2.3;
            m.quad(p(xr, y0, z1), p(xr, y0, z0), p(xr, y1, z0), p(xr, y1, z1), GLASSCOL, GLASS);
            m.quad(p(-xr, y0, z0), p(-xr, y0, z1), p(-xr, y1, z1), p(-xr, y1, z0), GLASSCOL, GLASS);
        }
        // Schornstein und Mast auf dem Steuerhaus
        m.box(0, fl + 3.4, -(hbk + 1.6), 1.0, 2.0, 1.3, FUNNEL, PAINT);
        m.box(0, fl + 5.1, -(hbk + 1.6), 1.02, 0.3, 1.32, BLACK, PAINT);
        Ship.Light aft = sh.lights.get(1), fore = sh.lights.get(0);
        m.box(0, (fl + 2.9 + aft.y()) / 2, -aft.s(), 0.08, (aft.y() - fl - 2.9) / 2, 0.08, WHITE, PAINT);
        m.box(0, aft.y() - 1.2, -aft.s(), 1.6, 0.05, 0.05, WHITE, PAINT);
        m.box(0, (F + fore.y()) / 2, -fore.s(), 0.12, (fore.y() - F) / 2, 0.12, YELLOW, PAINT);
        // Lampen (leuchten nachts, Farbe je Licht)
        for (Ship.Light l : sh.lights) m.box(l.q(), l.y(), -l.s(), 0.16, 0.14, 0.16, l.rgb(), LAMP);
        return m;
    }

    /** Deckshöhe bei s: Sprung zum Bug hin, achtern leicht. */
    public static double deckAt(double s, double L, double F) {
        double u = s / (L / 2);
        return F + 2.2 * Math.pow(Math.max(0, (u - 0.55) / 0.45), 2) + 0.4 * Math.pow(Math.max(0, (-u - 0.7) / 0.3), 2);
    }

    static boolean same(double[] a, double[] b) { return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) < 1e-6; }
    static double[] mir(double[] a) { return new double[]{-a[0], a[1], a[2]}; }

    /**
     * Die Brücke von innen: Pfosten zwischen den Frontscheiben, Fensterbank mit Pult und Anzeigen, Decke und Seiten.
     * Ohne Rückseitenverwerfung zeichnen (man steht drinnen).
     */
    public static Mesh bridgeInterior(Ship sh) {
        Mesh m = new Mesh();
        double hf = sh.houseFront(), fl = sh.bridgeFloor();
        double[][] w = sh.windows;
        double y0 = w[0][3], y1 = w[0][4], zf = -hf;
        double left = w[0][1] - w[0][2] / 2, right = w[2][1] + w[2][2] / 2;
        double[] frame = {0.55, 0.56, 0.55}, console = {0.05, 0.055, 0.06}, ceil = {0.42, 0.42, 0.4};
        // Pfosten zwischen und neben den Scheiben
        double[] posts = {left - 0.12, (w[0][1] + w[0][2] / 2 + w[1][1] - w[1][2] / 2) / 2, (w[1][1] + w[1][2] / 2 + w[2][1] - w[2][2] / 2) / 2, right + 0.12};
        for (double px : posts) m.box(px, (y0 + y1) / 2, zf + 0.08, 0.13, (y1 - y0) / 2 + 0.1, 0.08, frame, PAINT);
        // Unter- und Oberkante
        m.box((left + right) / 2, y0 - 0.06, zf + 0.12, (right - left) / 2 + 0.3, 0.06, 0.14, frame, PAINT);
        m.box((left + right) / 2, y1 + 0.15, zf + 0.1, (right - left) / 2 + 0.3, 0.15, 0.12, frame, PAINT);
        // Wand unter den Fenstern, Pult mit schräger Platte und Anzeigen
        m.box((left + right) / 2, (fl + y0) / 2, zf + 0.05, (right - left) / 2 + 0.3, (y0 - fl) / 2, 0.05, frame, PAINT);
        m.box((left + right) / 2, fl + 0.48, zf + 0.55, 2.3, 0.48, 0.45, console, PAINT);
        double ty = fl + 0.96;
        m.quad(p(-2.3, ty, zf + 1.0), p(2.3, ty, zf + 1.0), p(2.3, ty + 0.06, zf + 0.1), p(-2.3, ty + 0.06, zf + 0.1), console, PAINT);
        double[][] screens = {{-1.5, 0.25, 0.9}, {-0.45, 0.2, 0.75}, {0.45, 0.2, 0.75}, {1.5, 0.25, 0.9}};
        for (double[] s : screens) {
            double cx = s[0], hz = s[1], hw = s[2] / 2;
            m.quad(p(cx - hw, ty + 0.01, zf + 0.85), p(cx + hw, ty + 0.01, zf + 0.85), p(cx + hw, ty + 0.045, zf + 0.85 - 2 * hz), p(cx - hw, ty + 0.045, zf + 0.85 - 2 * hz),
                    new double[]{0.15, 0.55, 0.35}, SCREEN);
        }
        // Decke (Unterseite) und Seitenwände bis 1 m hinter das Auge
        double back = zf + 3.4;
        m.quad(p(left - 0.3, y1 + 0.3, back), p(right + 0.3, y1 + 0.3, back), p(right + 0.3, y1 + 0.3, zf), p(left - 0.3, y1 + 0.3, zf), ceil, PAINT);
        m.quad(p(left - 0.3, fl, zf), p(left - 0.3, fl, back), p(left - 0.3, y1 + 0.3, back), p(left - 0.3, y1 + 0.3, zf), frame, PAINT);
        m.quad(p(right + 0.3, fl, back), p(right + 0.3, fl, zf), p(right + 0.3, y1 + 0.3, zf), p(right + 0.3, y1 + 0.3, back), frame, PAINT);
        m.quad(p(left - 0.3, fl, back), p(right + 0.3, fl, back), p(right + 0.3, fl, zf), p(left - 0.3, fl, zf), new double[]{0.12, 0.1, 0.08}, PAINT);
        return m;
    }

    /**
     * Arm und Blatt des Wischers in Scheibenkoordinaten (u nach rechts, v nach oben, w nach außen), Arm nach oben
     * (Winkel 0); gedreht und an die Scheibe gesetzt wird er mit {@link Ship}-Lage und Winkel.
     */
    public static Mesh wiper(Wiper w) {
        Mesh m = new Mesh();
        double r0 = w.arm * w.bladeFrom, r1 = w.arm;
        m.box(0, w.arm * 0.5, 0.025, 0.012, w.arm * 0.5, 0.01, BLACK, PAINT);
        m.box(0, (r0 + r1) / 2, 0.012, 0.018, (r1 - r0) / 2, 0.012, DARK, PAINT);
        m.cylinder(0, 0.0, 0.03, 0.03, -0.02, 0.02, 8, new double[][]{BLACK}, PAINT, true);
        return m;
    }
}
