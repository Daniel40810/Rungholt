package com.dan.timber;

import java.util.Arrays;

/**
 * Ein Netz aus Dreiecken mit einem festen Eckformat ({@link #STRIDE} Zahlen je Ecke): Lage (3), Normale (3),
 * Flächenkoordinate u, v in Metern (u läuft mit der Faser des Holzes, den Halmen des Reets, den Ruten des Flechtwerks),
 * Werkstoff ({@link Material#ordinal()}) und Verdeckung (1 = frei, kleiner = im Schatten von Traufe und Boden).
 * Die Bauhilfen legen Vierecke, Dreiecke, Balken und Pfosten an; alles ohne Grafikkarte, für Kollision, Prüfung und
 * jeden Renderer.
 */
public final class Mesh {
    /** Zahlen je Ecke: x, y, z, nx, ny, nz, u, v, Werkstoff, Verdeckung. */
    public static final int STRIDE = 10;

    float[] v = new float[STRIDE * 256];
    int[] idx = new int[384];
    int nv, ni;

    public int vertices() { return nv; }
    public int triangles() { return ni / 3; }
    /** Kopie der Ecken (nv · STRIDE Zahlen). */
    public float[] vertexData() { return Arrays.copyOf(v, nv * STRIDE); }
    /** Kopie der Indizes (3 je Dreieck). */
    public int[] indexData() { return Arrays.copyOf(idx, ni); }

    /** Eine Ecke anlegen; gibt ihre Nummer. */
    public int vertex(double x, double y, double z, double nx, double ny, double nz, double u, double w, Material m, double ao) {
        if ((nv + 1) * STRIDE > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = nv * STRIDE;
        v[o] = (float) x; v[o + 1] = (float) y; v[o + 2] = (float) z;
        v[o + 3] = (float) nx; v[o + 4] = (float) ny; v[o + 5] = (float) nz;
        v[o + 6] = (float) u; v[o + 7] = (float) w;
        v[o + 8] = m.ordinal();
        v[o + 9] = (float) ao;
        return nv++;
    }

    public void tri(int a, int b, int c) {
        if (ni + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
        idx[ni++] = a; idx[ni++] = b; idx[ni++] = c;
    }

    /**
     * Viereck p0 → p1 → p2 → p3 (gegen den Uhrzeigersinn von außen gesehen), flach schattiert. u läuft entlang
     * p0 → p1 (Meter), v entlang p0 → p3; uv0 verschiebt den Ursprung. ao je Ecke (4 Werte) oder einer für alle.
     */
    public void quad(double[] p0, double[] p1, double[] p2, double[] p3, Material m, double u0, double v0, double... ao) {
        double[] n = normal(p0, p1, p3);
        double lu = dist(p0, p1), lv = dist(p0, p3);
        double a0 = ao.length > 0 ? ao[0] : 1, a1 = ao.length > 1 ? ao[1] : a0, a2 = ao.length > 2 ? ao[2] : a0, a3 = ao.length > 3 ? ao[3] : a0;
        int i0 = vertex(p0[0], p0[1], p0[2], n[0], n[1], n[2], u0, v0, m, a0);
        int i1 = vertex(p1[0], p1[1], p1[2], n[0], n[1], n[2], u0 + lu, v0, m, a1);
        int i2 = vertex(p2[0], p2[1], p2[2], n[0], n[1], n[2], u0 + lu, v0 + lv, m, a2);
        int i3 = vertex(p3[0], p3[1], p3[2], n[0], n[1], n[2], u0, v0 + lv, m, a3);
        tri(i0, i1, i2);
        tri(i0, i2, i3);
    }

    /** Viereck mit vertauschten Achsen: u läuft entlang p0 → p3 (für Faser quer zur ersten Kante). */
    public void quadUV(double[] p0, double[] p1, double[] p2, double[] p3, Material m, double... ao) {
        double[] n = normal(p0, p1, p3);
        double lu = dist(p0, p3), lv = dist(p0, p1);
        double a0 = ao.length > 0 ? ao[0] : 1, a1 = ao.length > 1 ? ao[1] : a0, a2 = ao.length > 2 ? ao[2] : a0, a3 = ao.length > 3 ? ao[3] : a0;
        int i0 = vertex(p0[0], p0[1], p0[2], n[0], n[1], n[2], 0, 0, m, a0);
        int i1 = vertex(p1[0], p1[1], p1[2], n[0], n[1], n[2], 0, lv, m, a1);
        int i2 = vertex(p2[0], p2[1], p2[2], n[0], n[1], n[2], lu, lv, m, a2);
        int i3 = vertex(p3[0], p3[1], p3[2], n[0], n[1], n[2], lu, 0, m, a3);
        tri(i0, i1, i2);
        tri(i0, i2, i3);
    }

    /** Dreieck (gegen den Uhrzeigersinn von außen), u entlang a → b. */
    public void triangle(double[] a, double[] b, double[] c, Material m, double ao) {
        double[] n = normal(a, b, c);
        double[] e = sub(b, a);
        double le = Math.max(1e-9, len(e));
        double[] eu = {e[0] / le, e[1] / le, e[2] / le};
        double[] ac = sub(c, a);
        double cu = dot(ac, eu);
        double[] perp = {ac[0] - eu[0] * cu, ac[1] - eu[1] * cu, ac[2] - eu[2] * cu};
        int i0 = vertex(a[0], a[1], a[2], n[0], n[1], n[2], 0, 0, m, ao);
        int i1 = vertex(b[0], b[1], b[2], n[0], n[1], n[2], le, 0, m, ao);
        int i2 = vertex(c[0], c[1], c[2], n[0], n[1], n[2], cu, len(perp), m, ao);
        tri(i0, i1, i2);
    }

    /**
     * Quader in Achsenrichtung (Mitte, halbe Ausdehnung). Die Faser läuft entlang der längsten Achse, außer grainY.
     * aoBottom dunkelt die unteren Ecken.
     */
    public void box(double cx, double cy, double cz, double hx, double hy, double hz, Material m, boolean grainY, double aoBottom) {
        double x0 = cx - hx, x1 = cx + hx, y0 = cy - hy, y1 = cy + hy, z0 = cz - hz, z1 = cz + hz;
        double[][] p = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        double ab = aoBottom;
        // Seiten: -z, +z, -x, +x, oben, unten
        face(p[1], p[0], p[3], p[2], m, grainY, ab);
        face(p[4], p[5], p[6], p[7], m, grainY, ab);
        face(p[0], p[4], p[7], p[3], m, grainY, ab);
        face(p[5], p[1], p[2], p[6], m, grainY, ab);
        quad(p[3], p[7], p[6], p[2], m, 0, 0, 1);
        quad(p[0], p[1], p[5], p[4], m, 0, 0, ab * 0.8);
    }

    /** Seitenfläche eines Quaders (unten p0, p1; oben p2, p3); Faser senkrecht (grainY) oder waagerecht. */
    private void face(double[] p0, double[] p1, double[] p2, double[] p3, Material m, boolean grainY, double ab) {
        if (grainY) quadUV(p0, p1, p2, p3, m, ab, ab, 1, 1);
        else quad(p0, p1, p2, p3, m, 0, 0, ab, ab, 1, 1);
    }

    /**
     * Balken oder Pfosten von a nach b als Prisma mit sides Seiten (4 = kantig behauen, 6 bis 8 = rund) und Radius r.
     * Die Faser läuft entlang des Balkens; Enden zu, wenn caps.
     */
    public void beam(double[] a, double[] b, double r, int sides, Material m, boolean caps, double aoA, double aoB) {
        double[] d = sub(b, a);
        double l = len(d);
        if (l < 1e-9) return;
        double[] w = {d[0] / l, d[1] / l, d[2] / l};
        double[] up = Math.abs(w[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] e1 = norm(cross(w, up)), e2 = cross(e1, w);
        double rot = sides == 4 ? Math.PI / 4 : 0;
        int base = nv;
        for (int i = 0; i <= sides; i++) {
            double t = rot + 2 * Math.PI * i / sides, c = Math.cos(t), s = Math.sin(t);
            double[] n = {e1[0] * c + e2[0] * s, e1[1] * c + e2[1] * s, e1[2] * c + e2[2] * s};
            double u = r * 2 * Math.PI * i / sides;
            vertex(a[0] + n[0] * r, a[1] + n[1] * r, a[2] + n[2] * r, n[0], n[1], n[2], 0, u, m, aoA);
            vertex(b[0] + n[0] * r, b[1] + n[1] * r, b[2] + n[2] * r, n[0], n[1], n[2], l, u, m, aoB);
        }
        for (int i = 0; i < sides; i++) {
            int k = base + i * 2;
            tri(k, k + 3, k + 2);
            tri(k, k + 1, k + 3);
        }
        if (caps) {
            for (int end = 0; end < 2; end++) {
                double[] c0 = end == 0 ? a : b;
                double sg = end == 0 ? -1 : 1;
                int ci = vertex(c0[0], c0[1], c0[2], w[0] * sg, w[1] * sg, w[2] * sg, 0, 0, m, end == 0 ? aoA : aoB);
                int first = nv;
                for (int i = 0; i < sides; i++) {
                    double t = rot + 2 * Math.PI * i / sides, c = Math.cos(t), s = Math.sin(t);
                    double[] n = {e1[0] * c + e2[0] * s, e1[1] * c + e2[1] * s, e1[2] * c + e2[2] * s};
                    vertex(c0[0] + n[0] * r, c0[1] + n[1] * r, c0[2] + n[2] * r, w[0] * sg, w[1] * sg, w[2] * sg, n[0] * r, n[2] * r, m, end == 0 ? aoA : aoB);
                }
                for (int i = 0; i < sides; i++) {
                    int i0 = first + i, i1 = first + (i + 1) % sides;
                    if (end == 0) tri(ci, i0, i1); else tri(ci, i1, i0);
                }
            }
        }
    }

    /** Hängt ein anderes Netz an. */
    public void append(Mesh o) {
        int base = nv;
        if ((nv + o.nv) * STRIDE > v.length) v = Arrays.copyOf(v, Math.max(v.length * 2, (nv + o.nv) * STRIDE));
        System.arraycopy(o.v, 0, v, nv * STRIDE, o.nv * STRIDE);
        nv += o.nv;
        if (ni + o.ni > idx.length) idx = Arrays.copyOf(idx, Math.max(idx.length * 2, ni + o.ni));
        for (int i = 0; i < o.ni; i++) idx[ni + i] = o.idx[i] + base;
        ni += o.ni;
    }

    /** Achsenparalleler Umriss {minX, minY, minZ, maxX, maxY, maxZ}. */
    public double[] bounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < nv; i++)
            for (int k = 0; k < 3; k++) {
                double c = v[i * STRIDE + k];
                b[k] = Math.min(b[k], c);
                b[k + 3] = Math.max(b[k + 3], c);
            }
        return b;
    }

    /** Fläche aller Dreiecke eines Werkstoffs (m²). */
    public double area(Material m) {
        double s = 0;
        for (int t = 0; t < ni; t += 3) {
            int a = idx[t], b = idx[t + 1], c = idx[t + 2];
            if ((int) v[a * STRIDE + 8] != m.ordinal()) continue;
            double[] pa = pos(a), pb = pos(b), pc = pos(c);
            s += 0.5 * len(cross(sub(pb, pa), sub(pc, pa)));
        }
        return s;
    }

    public Material material(int i) { return Material.values()[(int) v[i * STRIDE + 8]]; }

    public double[] pos(int i) { return new double[]{v[i * STRIDE], v[i * STRIDE + 1], v[i * STRIDE + 2]}; }

    // ------------------------------------------------------------------ Vektoren
    public static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    public static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    public static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double dist(double[] a, double[] b) { return len(sub(a, b)); }
    static double[] norm(double[] a) { double l = Math.max(1e-12, len(a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] normal(double[] a, double[] b, double[] c) { return norm(cross(sub(b, a), sub(c, a))); }
}
