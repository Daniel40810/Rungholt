package com.dan.seal;

import java.util.Arrays;

/**
 * Der Körper eines Seehunds (Phase 7): ein Netz in Körperkoordinaten und die Biegung, die es in eine Haltung bringt.
 * Java und {@code gl/seal.vert} rechnen dieselbe Biegung; Java braucht sie für die Demo, für den tiefsten Punkt (das
 * Tier liegt auf dem Grund) und für den Kopf über dem Wasser.
 * <p>
 * <b>Körperkoordinaten</b> (Länge 1, das Tier wird mit seiner Länge skaliert): x rechts, y oben, z vorn; s läuft
 * entlang der Wirbelsäule von der Nase (0) bis zum Schwanzansatz (0,93), die Hinterflossen reichen darüber hinaus. Der
 * Körper ist gestreckt rund, am Bauch flacher, der Kopf rund mit kurzer Schnauze, kaum Hals, die Vorderflossen klein
 * an den Seiten, die Hinterflossen als zwei Fächer [G, nach Fotos und Skelettmaßen von Phoca vitulina].
 * <p>
 * <b>Biegung</b>: Die Wirbelsäule hat an jeder Stelle s einen Neigungswinkel (Kopf heben, Schwanz heben, Buckel) und
 * einen Gierwinkel (Kopf drehen, Schwanz schlagen); die Lage eines Punkts ist das Integral der Richtung vom Anker
 * ({@link #ANCHOR}, der Schwerpunkt) bis s in {@link #STEPS} Schritten. Querschnitt und Flossen hängen im Rahmen ihrer
 * Stelle. Danach dreht die Rolle (auf der Seite liegen) den ganzen Körper um seine Achse.
 */
public final class SealBody {
    /** Die Haltung als Zahlen (Bogenmaß); dieselbe Reihenfolge im Shader (iC, iD). */
    public static final int HEAD_LIFT = 0, HEAD_YAW = 1, TAIL_LIFT = 2, ARCH = 3, ROLL = 4, TAIL_YAW = 5, FORE_SWING = 6, HIND_SPREAD = 7, PITCH = 8, N = 9;

    /** Anker der Biegung (Schwerpunkt), Schritte der Integration, Ende des Rumpfs. */
    public static final double ANCHOR = 0.45, END = 0.93;
    public static final int STEPS = 10;
    /** Belegung eines Eckpunkts: Versatz x, y, z im Rahmen, Normale, s, Teil (0 Rumpf, 1 Vorderflosse, 2 Hinterflosse), Winkel um die Achse. */
    public static final int FLOATS = 9;

    /** Querschnitt entlang s: s, halbe Breite, Höhe oben, Höhe unten, Mitte (Anteile der Länge) [G]. */
    static final double[][] PROFILE = {
            {0.000, 0.006, 0.006, 0.006, -0.004},
            {0.018, 0.028, 0.024, 0.022, -0.002},
            {0.045, 0.044, 0.041, 0.034, 0.004},
            {0.080, 0.061, 0.058, 0.044, 0.008},
            {0.125, 0.060, 0.057, 0.049, 0.006},
            {0.175, 0.068, 0.064, 0.060, 0.000},
            {0.255, 0.088, 0.078, 0.074, -0.004},
            {0.355, 0.112, 0.092, 0.090, -0.006},
            {0.470, 0.118, 0.092, 0.092, -0.006},
            {0.590, 0.102, 0.082, 0.080, -0.004},
            {0.710, 0.074, 0.064, 0.060, -0.002},
            {0.810, 0.048, 0.044, 0.040, 0.000},
            {0.880, 0.031, 0.028, 0.025, 0.000},
            {0.930, 0.016, 0.015, 0.013, 0.000}};

    private SealBody() { }

    /** Querschnitt an der Stelle s: {halbe Breite, oben, unten, Mitte}. */
    public static double[] section(double s) {
        double[][] p = PROFILE;
        if (s <= p[0][0]) return new double[]{p[0][1], p[0][2], p[0][3], p[0][4]};
        for (int i = 1; i < p.length; i++)
            if (s <= p[i][0]) {
                double t = (s - p[i - 1][0]) / (p[i][0] - p[i - 1][0]);
                t = t * t * (3 - 2 * t);
                double[] r = new double[4];
                for (int k = 0; k < 4; k++) r[k] = p[i - 1][k + 1] + (p[i][k + 1] - p[i - 1][k + 1]) * t;
                return r;
            }
        double[] l = p[p.length - 1];
        return new double[]{l[1], l[2], l[3], l[4]};
    }

    // ------------------------------------------------------------------ Biegung (wie seal.vert)

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Neigung der Wirbelsäule an der Stelle s (rad, + steigt nach vorn). */
    public static double pitchAt(double s, float[] p) {
        double head = 1 - smooth(0.10, 0.36, s), tail = smooth(0.62, 0.92, s);
        return p[PITCH] + p[HEAD_LIFT] * head - p[TAIL_LIFT] * tail + p[ARCH] * Math.max(-1, Math.min(1, (s - ANCHOR) * 2.4));
    }

    /** Gierung der Wirbelsäule an der Stelle s (rad, + nach links, von oben gegen den Uhrzeigersinn). */
    public static double yawAt(double s, float[] p) {
        return p[HEAD_YAW] * (1 - smooth(0.08, 0.40, s)) + p[TAIL_YAW] * smooth(0.50, 1.0, s);
    }

    /** Punkt der Wirbelsäule an der Stelle s (Länge 1, Anker im Ursprung, vor der Rolle). */
    public static double[] spine(double s, float[] p) {
        double ds = (ANCHOR - s) / STEPS, x = 0, y = 0, z = 0;
        for (int k = 0; k < STEPS; k++) {
            double m = s + (k + 0.5) * ds, ph = pitchAt(m, p), yw = yawAt(m, p);
            x += Math.sin(yw) * Math.cos(ph) * ds;
            y += Math.sin(ph) * ds;
            z += Math.cos(yw) * Math.cos(ph) * ds;
        }
        return new double[]{x, y, z};
    }

    /**
     * Ein Eckpunkt in seine Haltung: Versatz (x, y, z im Rahmen der Stelle s), Teil, dazu die Normale; Ergebnis {x, y, z,
     * nx, ny, nz} in Körperkoordinaten (Länge 1), nach der Rolle.
     */
    public static double[] deform(double ox, double oy, double oz, double nx, double ny, double nz, double s, int part, float[] p) {
        // Flossen drehen um ihren Ansatz im Rahmen
        if (part == 1) {
            double a = p[FORE_SWING], c = Math.cos(a), si = Math.sin(a);
            double by = oy, bz = oz;   // um die x-Achse durch den Ansatz (0, -0.03, 0)
            double ry = by + 0.03, rz = bz;
            oy = -0.03 + ry * c - rz * si;
            oz = ry * si + rz * c;
            double ny2 = ny * c - nz * si, nz2 = ny * si + nz * c;
            ny = ny2; nz = nz2;
        } else if (part == 2) {
            double side = ox < 0 ? -1 : 1, a = side * p[HIND_SPREAD], c = Math.cos(a), si = Math.sin(a);
            // auffächern um die Hochachse, dann mit dem Schwanz heben (um x)
            double x1 = ox * c - oz * si, z1 = ox * si + oz * c, nx1 = nx * c - nz * si, nz1 = nx * si + nz * c;
            double b = -0.5 * p[TAIL_LIFT], cb = Math.cos(b), sb = Math.sin(b);
            double y2 = oy * cb - z1 * sb, z2 = oy * sb + z1 * cb, ny2 = ny * cb - nz1 * sb, nz2 = ny * sb + nz1 * cb;
            ox = x1; oy = y2; oz = z2; nx = nx1; ny = ny2; nz = nz2;
        }
        double[] c = spine(s, p);
        double ph = pitchAt(s, p), yw = yawAt(s, p);
        double[] f = {Math.sin(yw) * Math.cos(ph), Math.sin(ph), Math.cos(yw) * Math.cos(ph)};
        double[] r = {Math.cos(yw), 0, -Math.sin(yw)};
        double[] u = {f[1] * r[2] - f[2] * r[1], f[2] * r[0] - f[0] * r[2], f[0] * r[1] - f[1] * r[0]};
        double px = c[0] + r[0] * ox + u[0] * oy + f[0] * oz, py = c[1] + r[1] * ox + u[1] * oy + f[1] * oz, pz = c[2] + r[2] * ox + u[2] * oy + f[2] * oz;
        double qx = r[0] * nx + u[0] * ny + f[0] * nz, qy = r[1] * nx + u[1] * ny + f[1] * nz, qz = r[2] * nx + u[2] * ny + f[2] * nz;
        // Rolle um die Längsachse (z)
        double cr = Math.cos(p[ROLL]), sr = Math.sin(p[ROLL]);
        return new double[]{px * cr - py * sr, px * sr + py * cr, pz, qx * cr - qy * sr, qx * sr + qy * cr, qz};
    }

    // ------------------------------------------------------------------ Netz

    /** Ringe entlang des Rumpfs, Ecken je Ring. */
    public static final int RINGS = 36, AROUND = 20;

    /** Das Netz als Dreiecke (je Ecke {@link #FLOATS} Werte). */
    public static float[] mesh() {
        FloatList out = new FloatList();
        // Rumpf: Ringe, an der Nase und am Schwanz geschlossen
        double[][][] ring = new double[RINGS][AROUND][];
        double[] ss = new double[RINGS];
        // Ringe am Kopf dichter (Schnauze, Augen)
        for (int i = 0; i < RINGS; i++) {
            double t = i / (double) (RINGS - 1);
            ss[i] = END * (1 - Math.pow(1 - t, 1.35));
        }
        for (int i = 0; i < RINGS; i++) {
            double[] q = section(ss[i]);
            for (int k = 0; k < AROUND; k++) {
                double a = 2 * Math.PI * k / AROUND;
                double ca = Math.cos(a), sa = Math.sin(a);
                double y = ca >= 0 ? q[1] * Math.pow(ca, 0.95) : -q[2] * Math.pow(-ca, 0.75);
                ring[i][k] = new double[]{q[0] * sa, y + q[3], 0, a};
            }
        }
        // Normalen aus Nachbarn (Ring und Stelle)
        for (int i = 0; i < RINGS - 1; i++)
            for (int k = 0; k < AROUND; k++) {
                int k1 = (k + 1) % AROUND;
                emitQuad(out, ring, ss, i, k, i + 1, k1);
            }
        // Nasenkappe und Schwanzkappe
        double[] q0 = section(0);
        for (int k = 0; k < AROUND; k++) {
            int k1 = (k + 1) % AROUND;
            vert(out, 0, q0[3], 0.004, 0, 0, 1, 0, 0, 0);
            vert(out, ring[0][k1][0], ring[0][k1][1], 0, ring[0][k1][0] * 0.3, ring[0][k1][1] * 0.3, 1, ss[0], 0, ring[0][k1][3]);
            vert(out, ring[0][k][0], ring[0][k][1], 0, ring[0][k][0] * 0.3, ring[0][k][1] * 0.3, 1, ss[0], 0, ring[0][k][3]);
            int e = RINGS - 1;
            vert(out, 0, 0, -0.006, 0, 0, -1, END, 0, 0);
            vert(out, ring[e][k][0], ring[e][k][1], 0, ring[e][k][0], ring[e][k][1], -0.4, END, 0, ring[e][k][3]);
            vert(out, ring[e][k1][0], ring[e][k1][1], 0, ring[e][k1][0], ring[e][k1][1], -0.4, END, 0, ring[e][k1][3]);
        }
        // Vorderflossen (Teil 1): an der Stelle 0,30, seitlich unten, nach hinten und außen gelegt
        for (int side = -1; side <= 1; side += 2) flipper(out, side, 0.30, new double[]{0.084, -0.048, 0.01}, new double[]{0.106, -0.074, -0.105}, 0.050, 0.012, 1, false);
        // Hinterflossen (Teil 2): am Schwanzansatz nach hinten, aufrecht mit den Sohlen zueinander, als Fächer
        for (int side = -1; side <= 1; side += 2) flipper(out, side, END, new double[]{0.013, 0.002, -0.004}, new double[]{0.020, 0.004, -0.150}, 0.062, 0.010, 2, true);
        return out.toArray();
    }

    /** Ein Viereck zwischen zwei Ringen mit glatten Normalen (aus den Tangenten). */
    private static void emitQuad(FloatList out, double[][][] ring, double[] ss, int i0, int k0, int i1, int k1) {
        int[][] c = {{i0, k0}, {i1, k0}, {i1, k1}, {i0, k0}, {i1, k1}, {i0, k1}};
        for (int[] ik : c) {
            int i = ik[0], k = ik[1];
            double[] v = ring[i][k];
            double[] n = ringNormal(ring, ss, i, k);
            vert(out, v[0], v[1], 0, n[0], n[1], n[2], ss[i], 0, v[3]);
        }
    }

    private static double[] ringNormal(double[][][] ring, double[] ss, int i, int k) {
        int n = ring.length, m = ring[0].length;
        double[] a = ring[i][(k + 1) % m], b = ring[i][(k - 1 + m) % m];
        double tx = a[0] - b[0], ty = a[1] - b[1];
        // a läuft von oben nach rechts (im Uhrzeigersinn von vorn gesehen): außen ist (−ty, tx)
        double nx = -ty, ny = tx;
        // Neigung entlang der Achse: Radius ändert sich mit s
        int ip = Math.min(n - 1, i + 1), im = Math.max(0, i - 1);
        double rp = Math.hypot(ring[ip][k][0], ring[ip][k][1]), rm = Math.hypot(ring[im][k][0], ring[im][k][1]);
        double dz = -(ss[ip] - ss[im]);   // z läuft gegen s
        double nl = Math.hypot(nx, ny);
        nx /= Math.max(1e-9, nl);
        ny /= Math.max(1e-9, nl);
        double nz = Math.abs(dz) < 1e-9 ? 0 : -(rp - rm) / dz;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[]{nx / l, ny / l, nz / l};
    }

    /** Eine Flosse als flacher, sich verjüngender Körper von {@code base} nach {@code tip} (Seite gespiegelt). */
    private static void flipper(FloatList out, int side, double s, double[] base, double[] tip, double width, double thick, int part, boolean upright) {
        int segs = 4;
        double[][] top = new double[segs + 1][], bot = new double[segs + 1][];
        double[][] l = new double[segs + 1][], r = new double[segs + 1][];
        for (int j = 0; j <= segs; j++) {
            double t = j / (double) segs;
            double x = (base[0] + (tip[0] - base[0]) * t) * side, y = base[1] + (tip[1] - base[1]) * t, z = base[2] + (tip[2] - base[2]) * t;
            double w = width * (part == 2 ? 0.35 + 0.8 * t : 0.75 - 0.15 * t + 0.25 * Math.sin(Math.PI * t)) * 0.5, h = thick * (1 - 0.6 * t) * 0.5;
            // quer zur Flosse (in der Ebene x-z): senkrecht zur Richtung
            double dx = (tip[0] - base[0]) * side, dz = tip[2] - base[2], dl = Math.hypot(dx, dz);
            double qx = -dz / dl, qz = dx / dl;
            if (upright) {
                // Blatt aufrecht: Breite nach oben (Fächer), Dicke zur Seite
                l[j] = new double[]{x, y - w * 0.35, z};
                r[j] = new double[]{x, y + w * 0.65, z};
                top[j] = new double[]{x + qx * h * side, y + w * 0.15, z + qz * h};
                bot[j] = new double[]{x - qx * h * side, y + w * 0.15, z - qz * h};
            } else {
                l[j] = new double[]{x - qx * w, y, z - qz * w};
                r[j] = new double[]{x + qx * w, y, z + qz * w};
                top[j] = new double[]{x, y + h, z};
                bot[j] = new double[]{x, y - h, z};
            }
        }
        double ax = upright ? side : 0, ay = upright ? 0 : 1;
        for (int j = 0; j < segs; j++) {
            // eine Seite: l–top–r, die andere: l–bot–r
            tri(out, l[j], top[j], top[j + 1], ax, ay, 0, s, part);
            tri(out, l[j], top[j + 1], l[j + 1], ax, ay, 0, s, part);
            tri(out, top[j], r[j], r[j + 1], ax, ay, 0, s, part);
            tri(out, top[j], r[j + 1], top[j + 1], ax, ay, 0, s, part);
            tri(out, l[j], bot[j + 1], bot[j], -ax, -ay, 0, s, part);
            tri(out, l[j], l[j + 1], bot[j + 1], -ax, -ay, 0, s, part);
            tri(out, bot[j], r[j + 1], r[j], -ax, -ay, 0, s, part);
            tri(out, bot[j], bot[j + 1], r[j + 1], -ax, -ay, 0, s, part);
        }
    }

    private static void tri(FloatList out, double[] a, double[] b, double[] c, double nx, double ny, double nz, double s, int part) {
        // Normale aus der Fläche (Richtung nach ny ausgerichtet)
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double fx = uy * vz - uz * vy, fy = uz * vx - ux * vz, fz = ux * vy - uy * vx, l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l < 1e-12) return;
        fx /= l; fy /= l; fz /= l;
        if (fx * nx + fy * ny + fz * nz < 0) { fx = -fx; fy = -fy; fz = -fz; }
        for (double[] v : new double[][]{a, b, c}) vert(out, v[0], v[1], v[2], fx, fy, fz, s, part, 0);
    }

    private static void vert(FloatList o, double x, double y, double z, double nx, double ny, double nz, double s, int part, double ang) {
        o.add(x); o.add(y); o.add(z); o.add(nx); o.add(ny); o.add(nz); o.add(s); o.add(part); o.add(ang);
    }

    /** Wachsende Liste von float. */
    static final class FloatList {
        float[] a = new float[4096];
        int n;
        void add(double v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = (float) v; }
        float[] toArray() { return Arrays.copyOf(a, n); }
    }

    // ------------------------------------------------------------------ Abfragen für das Verhalten

    /** Ein grobes Abbild des Körpers (Unterseite der Ringe und Flossenspitzen) für {@link #lowest}. */
    private static final double[][] PROBES;
    static {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        for (int i = 0; i <= 14; i++) {
            double s = END * i / 14.0;
            double[] q = section(s);
            for (int k = 0; k < 8; k++) {
                double a = 2 * Math.PI * k / 8, ca = Math.cos(a), sa = Math.sin(a);
                double y = ca >= 0 ? q[1] * ca : -q[2] * Math.pow(-ca, 0.75);
                l.add(new double[]{q[0] * sa, y + q[3], 0, s, 0});
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            l.add(new double[]{0.106 * side, -0.074, -0.105, 0.30, 1});
            l.add(new double[]{0.020 * side, -0.010, -0.150, END, 2});
        }
        PROBES = l.toArray(new double[0][]);
    }

    /** Tiefster Punkt des Körpers in dieser Haltung (Länge 1, relativ zum Anker): so tief liegt der Bauch unter dem Anker. */
    public static double lowest(float[] p) {
        double m = Double.MAX_VALUE;
        for (double[] q : PROBES) m = Math.min(m, deform(q[0], q[1], q[2], 0, 1, 0, q[3], (int) q[4], p)[1]);
        return m;
    }

    /** Mitte des Kopfs (Länge 1, relativ zum Anker): für die Seehunde im Wasser, deren Kopf über der Fläche steht. */
    public static double[] head(float[] p) {
        double[] q = section(0.085);
        return deform(0, q[3] + q[1] * 0.4, 0, 0, 1, 0, 0.085, 0, p);
    }

    /** Länge der Wirbelsäule von der Nase bis zum Schwanzansatz in dieser Haltung (bleibt bei jeder Biegung 0,93). */
    public static double spineLength(float[] p) {
        double len = 0;
        double[] a = spine(0, p);
        for (int i = 1; i <= 60; i++) {
            double[] b = spine(END * i / 60.0, p);
            len += Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
            a = b;
        }
        return len;
    }

    /** Zahl der Dreiecke des Netzes. */
    public static int triangles() { return mesh().length / FLOATS / 3; }
}
