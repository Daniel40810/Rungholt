package com.dan.ocean.gl;

/**
 * Das Gitter der Wasseroberfläche: Ringe um die Kamera, innen dicht (Zentimeter), nach außen immer weiter bis zur
 * Kimm und darüber hinaus (60 km). Ecken: Lage relativ zum Mittelpunkt (x, z) und der Eckenabstand an dieser Stelle,
 * nach dem die Ecke die Wellen filtert. Dreiecke von oben gesehen gegen den Uhrzeigersinn.
 */
public final class RingMesh {
    public final float[] verts;
    public final int[] idx;
    public final int rings, segments;
    public final double inner, outer;

    public RingMesh(int rings, int segments, double inner, double outer) {
        this.rings = rings;
        this.segments = segments;
        this.inner = inner;
        this.outer = outer;
        // Radien geometrisch: r_i = inner·(q^i − 1)/(q − 1), r_rings = outer
        double q = solveQ(rings, inner, outer);
        int vc = 1 + rings * segments;
        verts = new float[vc * 3];
        verts[0] = 0; verts[1] = 0; verts[2] = (float) inner;
        int v = 3;
        for (int i = 1; i <= rings; i++) {
            double r = inner * (Math.pow(q, i) - 1) / (q - 1);
            double dr = inner * Math.pow(q, i - 1);
            double spacing = Math.max(dr, 2 * Math.PI * r / segments);
            for (int s = 0; s < segments; s++) {
                double a = 2 * Math.PI * s / segments;
                verts[v++] = (float) (r * Math.cos(a));
                verts[v++] = (float) (-r * Math.sin(a));
                verts[v++] = (float) spacing;
            }
        }
        idx = new int[segments * 3 + (rings - 1) * segments * 6];
        int k = 0;
        for (int s = 0; s < segments; s++) {
            idx[k++] = 0;
            idx[k++] = 1 + s;
            idx[k++] = 1 + (s + 1) % segments;
        }
        for (int i = 1; i < rings; i++) {
            int a = 1 + (i - 1) * segments, b = 1 + i * segments;
            for (int s = 0; s < segments; s++) {
                int s1 = (s + 1) % segments;
                idx[k++] = a + s; idx[k++] = b + s; idx[k++] = b + s1;
                idx[k++] = a + s; idx[k++] = b + s1; idx[k++] = a + s1;
            }
        }
    }

    static double solveQ(int n, double inner, double outer) {
        double lo = 1.000001, hi = 2;
        for (int i = 0; i < 200; i++) {
            double q = (lo + hi) / 2, r = inner * (Math.pow(q, n) - 1) / (q - 1);
            if (r < outer) lo = q; else hi = q;
        }
        return (lo + hi) / 2;
    }

    /** Dreiecke, deren Flächennormale nach unten zeigt (von oben gesehen im Uhrzeigersinn); 0 erwartet. */
    public int downwardTriangles() {
        int bad = 0;
        for (int t = 0; t < idx.length; t += 3) {
            float ax = verts[idx[t] * 3], az = verts[idx[t] * 3 + 1];
            float bx = verts[idx[t + 1] * 3], bz = verts[idx[t + 1] * 3 + 1];
            float cx = verts[idx[t + 2] * 3], cz = verts[idx[t + 2] * 3 + 1];
            // y-Komponente von (b−a)×(c−a) mit Punkten (x, 0, z)
            double ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az);
            if (ny <= 0) bad++;
        }
        return bad;
    }
}
