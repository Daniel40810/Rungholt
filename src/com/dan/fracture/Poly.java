package com.dan.fracture;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein konvexes Polyeder: Flächen als Vielecke mit Ebene (Normale nach außen) und Marke. Die Marke sagt, woher die
 * Fläche kommt: −1 vom Rand des Körpers, sonst die Nummer der Nachbarzelle, deren Mittelsenkrechte sie ist. Grundlage
 * der Zellen ({@link Voronoi}), der Stücke ({@link Piece}) und des Auftriebs (der Teil unter Wasser ist ein Schnitt).
 * Reines Java, keine Abhängigkeit.
 */
public final class Poly {
    /** Eine Fläche: Punkte {x, y, z, …} gegen den Uhrzeigersinn von außen gesehen, Ebene n·x = d, Marke. */
    public static final class Face {
        public double[] p;
        public double nx, ny, nz, d;
        public int tag;

        Face(double[] p, double nx, double ny, double nz, double d, int tag) {
            this.p = p; this.nx = nx; this.ny = ny; this.nz = nz; this.d = d; this.tag = tag;
        }

        public int count() { return p.length / 3; }

        /** Fläche (m²). */
        public double area() {
            double sx = 0, sy = 0, sz = 0;
            int n = count();
            for (int k = 1; k + 1 < n; k++) {
                double ax = p[k * 3] - p[0], ay = p[k * 3 + 1] - p[1], az = p[k * 3 + 2] - p[2];
                double bx = p[k * 3 + 3] - p[0], by = p[k * 3 + 4] - p[1], bz = p[k * 3 + 5] - p[2];
                sx += ay * bz - az * by;
                sy += az * bx - ax * bz;
                sz += ax * by - ay * bx;
            }
            return 0.5 * Math.sqrt(sx * sx + sy * sy + sz * sz);
        }

        /** Flächenschwerpunkt (m). */
        public double[] centroid() {
            double cx = 0, cy = 0, cz = 0, w = 0;
            int n = count();
            for (int k = 1; k + 1 < n; k++) {
                double ax = p[k * 3] - p[0], ay = p[k * 3 + 1] - p[1], az = p[k * 3 + 2] - p[2];
                double bx = p[k * 3 + 3] - p[0], by = p[k * 3 + 4] - p[1], bz = p[k * 3 + 5] - p[2];
                double sx = ay * bz - az * by, sy = az * bx - ax * bz, sz = ax * by - ay * bx;
                double a = 0.5 * Math.sqrt(sx * sx + sy * sy + sz * sz);
                cx += a * (p[0] + p[k * 3] + p[k * 3 + 3]) / 3;
                cy += a * (p[1] + p[k * 3 + 1] + p[k * 3 + 4]) / 3;
                cz += a * (p[2] + p[k * 3 + 2] + p[k * 3 + 5]) / 3;
                w += a;
            }
            if (w <= 0) return new double[]{p[0], p[1], p[2]};
            return new double[]{cx / w, cy / w, cz / w};
        }
    }

    public final List<Face> faces = new ArrayList<>();

    /** Quader. Marken der sechs Seiten: tag. */
    public static Poly box(double x0, double y0, double z0, double x1, double y1, double z1, int tag) {
        Poly b = new Poly();
        b.faces.add(new Face(new double[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, -1, 0, 0, -x0, tag));
        b.faces.add(new Face(new double[]{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, 1, 0, 0, x1, tag));
        b.faces.add(new Face(new double[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, 0, -1, 0, -y0, tag));
        b.faces.add(new Face(new double[]{x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, 0, 1, 0, y1, tag));
        b.faces.add(new Face(new double[]{x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, 0, 0, -1, -z0, tag));
        b.faces.add(new Face(new double[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, 0, 0, 1, z1, tag));
        return b;
    }

    public Poly copy() {
        Poly c = new Poly();
        for (Face f : faces) c.faces.add(new Face(f.p.clone(), f.nx, f.ny, f.nz, f.d, f.tag));
        return c;
    }

    public boolean isEmpty() { return faces.size() < 4; }

    /** Größter Wert von n·x − d über alle Ecken (positiv: etwas liegt jenseits der Ebene). */
    public double maxAbove(double nx, double ny, double nz, double d) {
        double m = Double.NEGATIVE_INFINITY;
        for (Face f : faces) {
            double[] p = f.p;
            for (int k = 0; k < p.length; k += 3) m = Math.max(m, nx * p[k] + ny * p[k + 1] + nz * p[k + 2] - d);
        }
        return m;
    }

    /**
     * Schneidet ab, was jenseits der Ebene n·x = d liegt (n zeigt nach außen; n muss nicht Länge 1 haben). Die
     * Schnittfläche bekommt die Marke tag. Gibt false zurück, wenn nichts abgeschnitten wurde.
     */
    public boolean clip(double nx, double ny, double nz, double d, int tag) {
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len <= 0) return false;
        nx /= len; ny /= len; nz /= len; d /= len;
        double eps = 1e-9 * (1 + Math.abs(d));
        if (maxAbove(nx, ny, nz, d) <= eps) return false;
        List<double[]> cut = new ArrayList<>();
        List<Face> keep = new ArrayList<>();
        for (Face f : faces) {
            double[] p = f.p;
            int n = p.length / 3;
            double[] out = new double[(n + 2) * 3];
            int m = 0;
            for (int k = 0; k < n; k++) {
                int k2 = (k + 1) % n;
                double ax = p[k * 3], ay = p[k * 3 + 1], az = p[k * 3 + 2];
                double bx = p[k2 * 3], by = p[k2 * 3 + 1], bz = p[k2 * 3 + 2];
                double da = nx * ax + ny * ay + nz * az - d, db = nx * bx + ny * by + nz * bz - d;
                boolean ina = da <= eps, inb = db <= eps;
                if (ina) {
                    if (m * 3 + 3 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                    out[m * 3] = ax; out[m * 3 + 1] = ay; out[m * 3 + 2] = az;
                    m++;
                    if (da > -eps) cut.add(new double[]{ax, ay, az});
                }
                if (ina != inb && (da > eps || db > eps) && (da < -eps || db < -eps)) {
                    double t = da / (da - db);
                    double ix = ax + (bx - ax) * t, iy = ay + (by - ay) * t, iz = az + (bz - az) * t;
                    if (m * 3 + 3 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                    out[m * 3] = ix; out[m * 3 + 1] = iy; out[m * 3 + 2] = iz;
                    m++;
                    cut.add(new double[]{ix, iy, iz});
                }
            }
            if (m >= 3) {
                f.p = dedupe(java.util.Arrays.copyOf(out, m * 3));
                if (f.p.length >= 9) keep.add(f);
            }
        }
        faces.clear();
        faces.addAll(keep);
        // die neue Fläche aus den Schnittpunkten, im Kreis um ihre Mitte geordnet
        if (cut.size() >= 3) {
            double cx = 0, cy = 0, cz = 0;
            for (double[] q : cut) { cx += q[0]; cy += q[1]; cz += q[2]; }
            cx /= cut.size(); cy /= cut.size(); cz /= cut.size();
            double[] u = perp(nx, ny, nz);
            double vx = ny * u[2] - nz * u[1], vy = nz * u[0] - nx * u[2], vz = nx * u[1] - ny * u[0];
            final double fcx = cx, fcy = cy, fcz = cz;
            cut.sort((a, b) -> Double.compare(
                    Math.atan2((a[0] - fcx) * vx + (a[1] - fcy) * vy + (a[2] - fcz) * vz, (a[0] - fcx) * u[0] + (a[1] - fcy) * u[1] + (a[2] - fcz) * u[2]),
                    Math.atan2((b[0] - fcx) * vx + (b[1] - fcy) * vy + (b[2] - fcz) * vz, (b[0] - fcx) * u[0] + (b[1] - fcy) * u[1] + (b[2] - fcz) * u[2])));
            double[] p = new double[cut.size() * 3];
            for (int k = 0; k < cut.size(); k++) { p[k * 3] = cut.get(k)[0]; p[k * 3 + 1] = cut.get(k)[1]; p[k * 3 + 2] = cut.get(k)[2]; }
            p = dedupe(p);
            if (p.length >= 9) faces.add(new Face(p, nx, ny, nz, d, tag));
        }
        if (faces.size() < 4) faces.clear();
        return true;
    }

    /** Aufeinanderfolgende (fast) gleiche Punkte entfernen. */
    private static double[] dedupe(double[] p) {
        int n = p.length / 3, m = 0;
        double[] o = new double[p.length];
        for (int k = 0; k < n; k++) {
            if (m > 0) {
                double dx = p[k * 3] - o[(m - 1) * 3], dy = p[k * 3 + 1] - o[(m - 1) * 3 + 1], dz = p[k * 3 + 2] - o[(m - 1) * 3 + 2];
                if (dx * dx + dy * dy + dz * dz < 1e-14) continue;
            }
            o[m * 3] = p[k * 3]; o[m * 3 + 1] = p[k * 3 + 1]; o[m * 3 + 2] = p[k * 3 + 2];
            m++;
        }
        if (m > 1) {
            double dx = o[0] - o[(m - 1) * 3], dy = o[1] - o[(m - 1) * 3 + 1], dz = o[2] - o[(m - 1) * 3 + 2];
            if (dx * dx + dy * dy + dz * dz < 1e-14) m--;
        }
        return java.util.Arrays.copyOf(o, m * 3);
    }

    static double[] perp(double nx, double ny, double nz) {
        double ax = Math.abs(nx) < 0.7 ? 1 : 0, ay = ax == 1 ? 0 : 1;
        double ux = ny * 0 - nz * ay, uy = nz * ax - nx * 0, uz = nx * ay - ny * ax;
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        return new double[]{ux / l, uy / l, uz / l};
    }

    /**
     * Volumen, Schwerpunkt und zweite Momente um den Schwerpunkt (für Dichte 1):
     * {V, cx, cy, cz, Ixx, Iyy, Izz, Ixy, Ixz, Iyz} (Trägheitstensor, Nebenglieder mit Vorzeichen wie im Tensor).
     */
    public double[] mass() {
        if (faces.isEmpty()) return new double[10];
        double[] r = faces.get(0).p;
        double rx = r[0], ry = r[1], rz = r[2];
        double V = 0, mx = 0, my = 0, mz = 0, xx = 0, yy = 0, zz = 0, xy = 0, xz = 0, yz = 0;
        for (Face f : faces) {
            double[] p = f.p;
            int n = p.length / 3;
            for (int k = 1; k + 1 < n; k++) {
                double ax = p[0] - rx, ay = p[1] - ry, az = p[2] - rz;
                double bx = p[k * 3] - rx, by = p[k * 3 + 1] - ry, bz = p[k * 3 + 2] - rz;
                double cx = p[k * 3 + 3] - rx, cy = p[k * 3 + 4] - ry, cz = p[k * 3 + 5] - rz;
                double v = (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6;
                if (v == 0) continue;
                V += v;
                double sx = ax + bx + cx, sy = ay + by + cy, sz = az + bz + cz;
                mx += v * sx / 4; my += v * sy / 4; mz += v * sz / 4;
                // ∫ x_i x_j über das Tetraeder (0, a, b, c) = v/20 (Σ_k a_ki a_kj + s_i s_j)
                xx += v / 20 * (ax * ax + bx * bx + cx * cx + sx * sx);
                yy += v / 20 * (ay * ay + by * by + cy * cy + sy * sy);
                zz += v / 20 * (az * az + bz * bz + cz * cz + sz * sz);
                xy += v / 20 * (ax * ay + bx * by + cx * cy + sx * sy);
                xz += v / 20 * (ax * az + bx * bz + cx * cz + sx * sz);
                yz += v / 20 * (ay * az + by * bz + cy * cz + sy * sz);
            }
        }
        if (V <= 1e-15) return new double[10];
        double cx = mx / V, cy = my / V, cz = mz / V;
        xx -= V * cx * cx; yy -= V * cy * cy; zz -= V * cz * cz;
        xy -= V * cx * cy; xz -= V * cx * cz; yz -= V * cy * cz;
        return new double[]{V, cx + rx, cy + ry, cz + rz, yy + zz, xx + zz, xx + yy, -xy, -xz, -yz};
    }

    public double volume() { return mass()[0]; }

    /** Die Ecken ohne Doppelte {x, y, z, …}. */
    public double[] vertices() {
        List<double[]> out = new ArrayList<>();
        for (Face f : faces) {
            double[] p = f.p;
            outer:
            for (int k = 0; k < p.length; k += 3) {
                for (double[] q : out) {
                    double dx = q[0] - p[k], dy = q[1] - p[k + 1], dz = q[2] - p[k + 2];
                    if (dx * dx + dy * dy + dz * dz < 1e-10) continue outer;
                }
                out.add(new double[]{p[k], p[k + 1], p[k + 2]});
            }
        }
        double[] v = new double[out.size() * 3];
        for (int k = 0; k < out.size(); k++) { v[k * 3] = out.get(k)[0]; v[k * 3 + 1] = out.get(k)[1]; v[k * 3 + 2] = out.get(k)[2]; }
        return v;
    }

    /** Kantenmitten ohne Doppelte (für Berührungen Kante auf Kante). */
    public double[] edgeMids() {
        List<double[]> out = new ArrayList<>();
        for (Face f : faces) {
            double[] p = f.p;
            int n = p.length / 3;
            outer:
            for (int k = 0; k < n; k++) {
                int k2 = (k + 1) % n;
                double mx = (p[k * 3] + p[k2 * 3]) / 2, my = (p[k * 3 + 1] + p[k2 * 3 + 1]) / 2, mz = (p[k * 3 + 2] + p[k2 * 3 + 2]) / 2;
                for (double[] q : out) {
                    double dx = q[0] - mx, dy = q[1] - my, dz = q[2] - mz;
                    if (dx * dx + dy * dy + dz * dz < 1e-10) continue outer;
                }
                out.add(new double[]{mx, my, mz});
            }
        }
        double[] v = new double[out.size() * 3];
        for (int k = 0; k < out.size(); k++) { v[k * 3] = out.get(k)[0]; v[k * 3 + 1] = out.get(k)[1]; v[k * 3 + 2] = out.get(k)[2]; }
        return v;
    }

    /** Wendet x' = R (x − o) + t auf alle Punkte und Normalen an (R zeilenweise 3×3). */
    public Poly transform(double[] R, double[] o, double[] t) {
        Poly c = new Poly();
        for (Face f : faces) {
            double[] p = new double[f.p.length];
            for (int k = 0; k < p.length; k += 3) {
                double x = f.p[k] - o[0], y = f.p[k + 1] - o[1], z = f.p[k + 2] - o[2];
                p[k] = R[0] * x + R[1] * y + R[2] * z + t[0];
                p[k + 1] = R[3] * x + R[4] * y + R[5] * z + t[1];
                p[k + 2] = R[6] * x + R[7] * y + R[8] * z + t[2];
            }
            double nx = R[0] * f.nx + R[1] * f.ny + R[2] * f.nz, ny = R[3] * f.nx + R[4] * f.ny + R[5] * f.nz, nz = R[6] * f.nx + R[7] * f.ny + R[8] * f.nz;
            c.faces.add(new Face(p, nx, ny, nz, nx * p[0] + ny * p[1] + nz * p[2], f.tag));
        }
        return c;
    }

    /** Liegt der Punkt innen? Gibt die Tiefe (m, positiv innen) und die Fläche mit dem kleinsten Abstand zurück. */
    public double depth(double x, double y, double z, int[] face) {
        double best = Double.POSITIVE_INFINITY;
        int bi = -1;
        for (int k = 0; k < faces.size(); k++) {
            Face f = faces.get(k);
            double s = f.d - (f.nx * x + f.ny * y + f.nz * z);
            if (s < best) { best = s; bi = k; }
            if (best < 0) break;
        }
        if (face != null) face[0] = bi;
        return best;
    }
}
