package com.dan.ice;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Eiskörper als Dreiecksnetz mit flachen Flächen (jede Fläche eigene Ecken, damit Kanten scharf bleiben). Raum
 * wie in der Szene: m, y oben. Neben dem Zeichnen braucht man das Netz für Volumen, Schwerpunkt und die Höhe, in der
 * ein Stück schwimmt (genau, über die Teilvolumen unter einer Ebene).
 */
public final class IceMesh {
    /** Ecken (x, y, z) und Normalen je Ecke, Dreiecke als Indizes. */
    public final float[] pos, nrm;
    public final int[] idx;
    /** Verdeckung je Ecke 0..1 (1 = offen; null = überall 1): Grund von Spalten, Kerbe an der Wasserlinie. */
    public final float[] ao;

    public IceMesh(float[] pos, float[] nrm, int[] idx) { this(pos, nrm, idx, null); }

    public IceMesh(float[] pos, float[] nrm, int[] idx, float[] ao) {
        this.pos = pos;
        this.nrm = nrm;
        this.idx = idx;
        this.ao = ao;
    }

    public int triangles() { return idx.length / 3; }

    /** Baut Netze aus Vielecken auf; flache Normalen, Umlauf nach außen (vom Bezugspunkt weg). */
    public static final class Builder {
        private final List<float[]> v = new ArrayList<>();
        private final double cx, cy, cz;

        /** Bezugspunkt im Inneren (für die Ausrichtung der Flächen nach außen). */
        public Builder(double cx, double cy, double cz) { this.cx = cx; this.cy = cy; this.cz = cz; }

        /** Ein ebenes, konvexes Vieleck (Ecken als x, y, z hintereinander), als Fächer in Dreiecke zerlegt. */
        public Builder poly(double... p) {
            int n = p.length / 3;
            if (n < 3) return this;
            double ax = 0, ay = 0, az = 0;
            for (int i = 0; i < n; i++) { ax += p[i * 3]; ay += p[i * 3 + 1]; az += p[i * 3 + 2]; }
            ax /= n; ay /= n; az /= n;
            double[] nn = normal(p, 0, 1, 2);
            boolean flip = nn[0] * (ax - cx) + nn[1] * (ay - cy) + nn[2] * (az - cz) < 0;
            for (int i = 1; i + 1 < n; i++) {
                int b = flip ? i + 1 : i, c = flip ? i : i + 1;
                tri(p[0], p[1], p[2], p[b * 3], p[b * 3 + 1], p[b * 3 + 2], p[c * 3], p[c * 3 + 1], p[c * 3 + 2]);
            }
            return this;
        }

        /** Ein Dreieck in der gegebenen Reihenfolge (gegen den Uhrzeigersinn von außen gesehen). */
        public Builder tri(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2) {
            double ux = x1 - x0, uy = y1 - y0, uz = z1 - z0, wx = x2 - x0, wy = y2 - y0, wz = z2 - z0;
            double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-12) return this;
            nx /= l; ny /= l; nz /= l;
            v.add(new float[]{(float) x0, (float) y0, (float) z0, (float) nx, (float) ny, (float) nz});
            v.add(new float[]{(float) x1, (float) y1, (float) z1, (float) nx, (float) ny, (float) nz});
            v.add(new float[]{(float) x2, (float) y2, (float) z2, (float) nx, (float) ny, (float) nz});
            return this;
        }

        public IceMesh build() {
            float[] p = new float[v.size() * 3], n = new float[v.size() * 3];
            int[] ix = new int[v.size()];
            for (int i = 0; i < v.size(); i++) {
                float[] e = v.get(i);
                System.arraycopy(e, 0, p, i * 3, 3);
                System.arraycopy(e, 3, n, i * 3, 3);
                ix[i] = i;
            }
            return new IceMesh(p, n, ix);
        }

        private static double[] normal(double[] p, int a, int b, int c) {
            double ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
            double wx = p[c * 3] - p[a * 3], wy = p[c * 3 + 1] - p[a * 3 + 1], wz = p[c * 3 + 2] - p[a * 3 + 2];
            return new double[]{uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx};
        }
    }

    // ------------------------------------------------------------------ Volumen

    /** Eingeschlossenes Volumen (m³), aus vorzeichenbehafteten Tetraedern zum Ursprung. */
    public double volume() { return volumeBelow(Double.POSITIVE_INFINITY); }

    /** Untere und obere Höhe (y) des Netzes. */
    public double[] yRange() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 1; i < pos.length; i += 3) { lo = Math.min(lo, pos[i]); hi = Math.max(hi, pos[i]); }
        return new double[]{lo, hi};
    }

    /**
     * Volumen unter der Ebene y = h (m³), genau: jedes Dreieck wird an der Ebene beschnitten, das Stück darunter gibt
     * mit einem Punkt auf der Ebene einen Tetraeder; der Deckel in der Ebene trägt nichts bei, weil er mit diesem Punkt
     * in einer Ebene liegt.
     */
    public double volumeBelow(double h) {
        double ox = 0, oy = Double.isInfinite(h) ? 0 : h, oz = 0, vol = 0;
        double[] in = new double[9], poly = new double[12];
        for (int t = 0; t < idx.length; t += 3) {
            for (int k = 0; k < 3; k++) {
                int v = idx[t + k] * 3;
                in[k * 3] = pos[v]; in[k * 3 + 1] = pos[v + 1]; in[k * 3 + 2] = pos[v + 2];
            }
            int n = clipBelow(in, h, poly);
            for (int i = 1; i + 1 < n; i++)
                vol += tet(ox, oy, oz, poly[0], poly[1], poly[2], poly[i * 3], poly[i * 3 + 1], poly[i * 3 + 2],
                        poly[(i + 1) * 3], poly[(i + 1) * 3 + 1], poly[(i + 1) * 3 + 2]);
        }
        return vol;
    }

    /** Dreieck (9 Werte) an y = h beschneiden, das Vieleck darunter nach out (bis 4 Ecken); Zahl der Ecken. */
    private static int clipBelow(double[] tri, double h, double[] out) {
        int n = 0;
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            double yi = tri[i * 3 + 1], yj = tri[j * 3 + 1];
            boolean ii = yi <= h, jj = yj <= h;
            if (ii) { out[n * 3] = tri[i * 3]; out[n * 3 + 1] = yi; out[n * 3 + 2] = tri[i * 3 + 2]; n++; }
            if (ii != jj) {
                double f = (h - yi) / (yj - yi);
                out[n * 3] = tri[i * 3] + (tri[j * 3] - tri[i * 3]) * f;
                out[n * 3 + 1] = h;
                out[n * 3 + 2] = tri[i * 3 + 2] + (tri[j * 3 + 2] - tri[i * 3 + 2]) * f;
                n++;
            }
        }
        return n;
    }

    private static double tet(double ox, double oy, double oz, double ax, double ay, double az, double bx, double by, double bz,
                              double cx, double cy, double cz) {
        ax -= ox; ay -= oy; az -= oz; bx -= ox; by -= oy; bz -= oz; cx -= ox; cy -= oy; cz -= oz;
        return (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6;
    }

    /**
     * Die Höhe der Wasserlinie im Netz (y), bei der der Anteil {@code fraction} des Volumens unter Wasser liegt
     * (Halbierung auf 1 mm). Für ein Stück Gletschereis im Süßwasser: {@code ice.submergedFraction(1000)}.
     */
    public double waterline(double fraction) {
        double[] r = yRange();
        double all = volume(), lo = r[0], hi = r[1];
        for (int i = 0; i < 60 && hi - lo > 1e-3; i++) {
            double m = (lo + hi) / 2;
            if (volumeBelow(m) < fraction * all) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    /** Schwerpunkt des Volumens (bei gleicher Dichte). */
    public double[] centroid() {
        double vol = 0, sx = 0, sy = 0, sz = 0;
        for (int t = 0; t < idx.length; t += 3) {
            int a = idx[t] * 3, b = idx[t + 1] * 3, c = idx[t + 2] * 3;
            double v = tet(0, 0, 0, pos[a], pos[a + 1], pos[a + 2], pos[b], pos[b + 1], pos[b + 2], pos[c], pos[c + 1], pos[c + 2]);
            vol += v;
            sx += v * (pos[a] + pos[b] + pos[c]) / 4;
            sy += v * (pos[a + 1] + pos[b + 1] + pos[c + 1]) / 4;
            sz += v * (pos[a + 2] + pos[b + 2] + pos[c + 2]) / 4;
        }
        return new double[]{sx / vol, sy / vol, sz / vol};
    }

    /** Größte Ausdehnung (m) der Hülle. */
    public double size() {
        double[] lo = {1e30, 1e30, 1e30}, hi = {-1e30, -1e30, -1e30};
        for (int i = 0; i < pos.length; i++) { lo[i % 3] = Math.min(lo[i % 3], pos[i]); hi[i % 3] = Math.max(hi[i % 3], pos[i]); }
        return Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
    }
}
