package com.dan.fracture;

import java.util.List;
import java.util.Locale;

/**
 * Ein freies Stück: ein Starrkörper aus einer oder mehreren konvexen Zellen. Masse und Trägheit kommen aus den Zellen;
 * das eigene Achsenkreuz liegt im Schwerpunkt auf den Hauptachsen, sodass der Trägheitstensor diagonal ist. Die Flächen
 * zwischen den Zellen bleiben erhalten – schlägt das Stück hart auf, kann es an ihnen weiter zerbrechen.
 */
public final class Piece {
    public final int id;
    public final Material material;
    /** Woher: Nummer des Körpers (−1 frei gesetzt) und Alter (s). */
    public final int source;
    /** Nummern der Zellen im Körper, aus dem das Stück stammt (für innere Flächen). */
    final int[] cid, cidSorted;
    int[] parentCid;
    int parent = -1;
    double age;
    // Zellen im eigenen Achsenkreuz
    final Poly[] cells;
    final double[][] pts;          // je Zelle: Ecken und Kantenmitten (lokal) für Berührungen
    final double[] ccen, crad, cvol, cseed;
    // Flächen zwischen den Zellen
    final int[] bi, bj;
    final double[] bc, bn, bd, ba, ft, fc, coh;
    // Masse
    final double mass, invMass, volume, radius;
    final double[] inertia = new double[3], invInertia = new double[3];
    // Lage und Bewegung
    final double[] p = new double[3], q = {1, 0, 0, 0}, v = new double[3], w = new double[3];
    final double[] pv = new double[3], pw = new double[3];   // Scheingeschwindigkeiten der Lagekorrektur
    final double[] R = new double[9];
    boolean asleep, handedOver;
    /** Last auf haltende Körper beim letzten Schritt {Solid, Zelle, {fx, fy, fz}}; bleibt liegen, solange das Stück schläft. */
    final List<Object[]> rest = new java.util.ArrayList<>();
    boolean restApplied;
    double quiet, wet, submerged, impactCool, sleptAt;
    // je Schritt
    final double[][] wpts;         // Ecken in Welt
    final double[] wcen;           // Zellmitten in Welt
    long stamp = -1;
    final double[] impulse = new double[3], impulsePrev = new double[3];
    int impulseCell = -1, hitCell = -1;
    double lastJ;
    double impulseMax;

    Piece(int id, int source, Material mat, List<Poly> worldCells, List<double[]> worldSeeds, int[][] bonds, double[][] bondData, int[] cid) {
        this.id = id;
        this.cid = cid;
        int[] sorted = cid.clone();
        java.util.Arrays.sort(sorted);
        this.cidSorted = sorted;
        this.source = source;
        this.material = mat;
        int n = worldCells.size();
        // Masse und Trägheit in Welt um den gemeinsamen Schwerpunkt
        double M = 0, mx = 0, my = 0, mz = 0;
        double[][] ms = new double[n][];
        for (int i = 0; i < n; i++) {
            ms[i] = worldCells.get(i).mass();
            double mi = ms[i][0] * mat.density;
            M += mi;
            mx += mi * ms[i][1]; my += mi * ms[i][2]; mz += mi * ms[i][3];
        }
        double cx = mx / M, cy = my / M, cz = mz / M;
        double[] I = new double[9];
        for (int i = 0; i < n; i++) {
            double rho = mat.density;
            double rx = ms[i][1] - cx, ry = ms[i][2] - cy, rz = ms[i][3] - cz, mi = ms[i][0] * rho;
            double r2 = rx * rx + ry * ry + rz * rz;
            I[0] += ms[i][4] * rho + mi * (r2 - rx * rx);
            I[4] += ms[i][5] * rho + mi * (r2 - ry * ry);
            I[8] += ms[i][6] * rho + mi * (r2 - rz * rz);
            I[1] += ms[i][7] * rho - mi * rx * ry;
            I[2] += ms[i][8] * rho - mi * rx * rz;
            I[5] += ms[i][9] * rho - mi * ry * rz;
        }
        I[3] = I[1]; I[6] = I[2]; I[7] = I[5];
        double[] E = new double[9], ev = new double[3];
        Mat.eigen(I, E, ev);
        mass = M;
        invMass = 1 / M;
        for (int k = 0; k < 3; k++) {
            inertia[k] = Math.max(ev[k], 1e-9 * M);
            invInertia[k] = 1 / inertia[k];
        }
        // R = E (Spalten sind die Hauptachsen); lokal = Eᵀ (x − c)
        System.arraycopy(E, 0, R, 0, 9);
        Mat.toQuat(R, q);
        Mat.fromQuat(q, R);
        p[0] = cx; p[1] = cy; p[2] = cz;
        double[] Rt = Mat.transpose(R);
        double[] o = {cx, cy, cz}, zero = new double[3];
        cells = new Poly[n];
        pts = new double[n][];
        ccen = new double[3 * n];
        crad = new double[n];
        cvol = new double[n];
        cseed = new double[3 * n];
        double vol = 0, rad = 0;
        for (int i = 0; i < n; i++) {
            Poly c = worldCells.get(i).transform(Rt, o, zero);
            cells[i] = c;
            double[] mm = c.mass();
            cvol[i] = mm[0];
            vol += mm[0];
            ccen[i * 3] = mm[1]; ccen[i * 3 + 1] = mm[2]; ccen[i * 3 + 2] = mm[3];
            double[] vt = c.vertices(), em = c.edgeMids();
            double[] all = new double[vt.length + em.length];
            System.arraycopy(vt, 0, all, 0, vt.length);
            System.arraycopy(em, 0, all, vt.length, em.length);
            pts[i] = all;
            double r = 0;
            for (int k = 0; k < vt.length; k += 3) {
                double dx = vt[k] - mm[1], dy = vt[k + 1] - mm[2], dz = vt[k + 2] - mm[3];
                r = Math.max(r, dx * dx + dy * dy + dz * dz);
                rad = Math.max(rad, vt[k] * vt[k] + vt[k + 1] * vt[k + 1] + vt[k + 2] * vt[k + 2]);
            }
            crad[i] = Math.sqrt(r);
            double[] s = worldSeeds.get(i);
            double sx = s[0] - cx, sy = s[1] - cy, sz = s[2] - cz;
            cseed[i * 3] = Rt[0] * sx + Rt[1] * sy + Rt[2] * sz;
            cseed[i * 3 + 1] = Rt[3] * sx + Rt[4] * sy + Rt[5] * sz;
            cseed[i * 3 + 2] = Rt[6] * sx + Rt[7] * sy + Rt[8] * sz;
        }
        volume = vol;
        radius = Math.sqrt(rad);
        int m = bonds == null ? 0 : bonds.length;
        bi = new int[m]; bj = new int[m];
        bc = new double[3 * m]; bn = new double[3 * m];
        bd = new double[m]; ba = new double[m]; ft = new double[m]; fc = new double[m]; coh = new double[m];
        for (int b = 0; b < m; b++) {
            bi[b] = bonds[b][0];
            bj[b] = bonds[b][1];
            double[] dd = bondData[b];   // cx, cy, cz, nx, ny, nz, d, a, ft, fc, coh (Welt)
            double sx = dd[0] - cx, sy = dd[1] - cy, sz = dd[2] - cz;
            bc[b * 3] = Rt[0] * sx + Rt[1] * sy + Rt[2] * sz;
            bc[b * 3 + 1] = Rt[3] * sx + Rt[4] * sy + Rt[5] * sz;
            bc[b * 3 + 2] = Rt[6] * sx + Rt[7] * sy + Rt[8] * sz;
            bn[b * 3] = Rt[0] * dd[3] + Rt[1] * dd[4] + Rt[2] * dd[5];
            bn[b * 3 + 1] = Rt[3] * dd[3] + Rt[4] * dd[4] + Rt[5] * dd[5];
            bn[b * 3 + 2] = Rt[6] * dd[3] + Rt[7] * dd[4] + Rt[8] * dd[5];
            bd[b] = dd[6]; ba[b] = dd[7]; ft[b] = dd[8]; fc[b] = dd[9]; coh[b] = dd[10];
        }
        wpts = new double[n][];
        for (int i = 0; i < n; i++) wpts[i] = new double[pts[i].length];
        wcen = new double[3 * n];
        // Zellen an der Oberfläche: nur sie können etwas berühren (Phase 7: Berührungen großer Stücke)
        surf = new boolean[n];
        for (int i = 0; i < n; i++)
            for (Poly.Face f : cells[i].faces) if (outer(f)) { surf[i] = true; break; }
    }

    /** Liegt Zelle i an der Oberfläche des Stücks (hat sie eine äußere Fläche)? */
    final boolean[] surf;

    // ------------------------------------------------------------------ Lesen
    public int cellCount() { return cells.length; }
    public int bondCount() { return bi.length; }
    public double mass() { return mass; }
    public double volume() { return volume; }
    /** Halbmesser der Hüllkugel um den Schwerpunkt (m). */
    public double radius() { return radius; }
    public double age() { return age; }
    public boolean asleep() { return asleep; }
    /** Wie lange das Stück schon im Wasser liegt (s). */
    public double wetTime() { return wet; }
    /** Ob das Stück nach {@link Fracture#sleepAfter} auf dem Wasser an einen anderen Löser übergeben ist. */
    public boolean handedOver() { return handedOver; }
    /** Anteil unter Wasser (0..1) beim letzten Schritt. */
    public double submergedShare() { return volume > 0 ? submerged / volume : 0; }
    public double[] position() { return p.clone(); }
    public double[] velocity() { return v.clone(); }
    public double[] spin() { return w.clone(); }
    /** Drehung als Quaternion {w, x, y, z}. */
    public double[] orientation() { return q.clone(); }
    /** Drehung als Matrix zeilenweise (Welt = R · lokal + Lage). */
    public double[] rotation() { return R.clone(); }
    /** Das Stück, aus dem dieses beim Aufschlag gebrochen ist (−1: direkt aus einem Körper). */
    public int parent() { return parent; }

    /** Nummer der Zelle i im Körper, aus dem das Stück stammt. */
    public int originCell(int i) { return cid[i]; }

    /**
     * Ist die Fläche einer Zelle dieses Stücks außen? Innen liegt sie, wenn ihre Marke eine andere Zelle desselben
     * Stücks nennt (für das Netz zum Zeichnen: Innenflächen weglassen).
     */
    public boolean outer(Poly.Face f) { return f.tag < 0 || java.util.Arrays.binarySearch(cidSorted, f.tag) < 0; }

    /** Zelle i im eigenen Achsenkreuz. */
    public Poly cell(int i) { return cells[i]; }
    /** Zelle i in Welt (neu berechnet). */
    public Poly worldCell(int i) { return cells[i].transform(R, new double[3], p); }

    /** Welt aus lokal. */
    public double[] toWorld(double x, double y, double z) {
        return new double[]{R[0] * x + R[1] * y + R[2] * z + p[0], R[3] * x + R[4] * y + R[5] * z + p[1], R[6] * x + R[7] * y + R[8] * z + p[2]};
    }

    /** Geschwindigkeit des Punkts (Welt) als Teil des Stücks. */
    public double[] velocityAt(double x, double y, double z) {
        double rx = x - p[0], ry = y - p[1], rz = z - p[2];
        return new double[]{v[0] + w[1] * rz - w[2] * ry, v[1] + w[2] * rx - w[0] * rz, v[2] + w[0] * ry - w[1] * rx};
    }

    /** Bewegungsenergie (J). */
    public double kinetic() {
        double[] wl = Mat.mulT(R, w);
        return 0.5 * mass * (v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                + 0.5 * (inertia[0] * wl[0] * wl[0] + inertia[1] * wl[1] * wl[1] + inertia[2] * wl[2] * wl[2]);
    }

    /** Setzt Lage und Bewegung (für Prüfungen und Demos). */
    public Piece place(double x, double y, double z) { p[0] = x; p[1] = y; p[2] = z; wake(); return this; }
    public Piece push(double vx, double vy, double vz) { v[0] = vx; v[1] = vy; v[2] = vz; wake(); return this; }
    public Piece twirl(double wx, double wy, double wz) { w[0] = wx; w[1] = wy; w[2] = wz; wake(); return this; }
    /** Dreht das Stück um die Welt-Achse (ax, ay, az) um den Winkel a (Bogenmaß). */
    public Piece turn(double ax, double ay, double az, double a) {
        double l = Math.sqrt(ax * ax + ay * ay + az * az), s = Math.sin(a / 2) / l;
        double[] r = {Math.cos(a / 2), ax * s, ay * s, az * s};
        double[] nq = Mat.qmul(r, q);
        System.arraycopy(nq, 0, q, 0, 4);
        Mat.normalize(q);
        Mat.fromQuat(q, R);
        stamp = -1;
        return this;
    }

    public void wake() { asleep = false; quiet = 0; stamp = -1; }

    /** Welt-Ecken und Zellmitten für diesen Schritt (einmal je Schritt). */
    void refresh(long step) {
        if (stamp == step) return;
        stamp = step;
        for (int i = 0; i < cells.length; i++) {
            double[] a = pts[i], o = wpts[i];
            for (int k = 0; k < a.length; k += 3) {
                double x = a[k], y = a[k + 1], z = a[k + 2];
                o[k] = R[0] * x + R[1] * y + R[2] * z + p[0];
                o[k + 1] = R[3] * x + R[4] * y + R[5] * z + p[1];
                o[k + 2] = R[6] * x + R[7] * y + R[8] * z + p[2];
            }
            double x = ccen[i * 3], y = ccen[i * 3 + 1], z = ccen[i * 3 + 2];
            wcen[i * 3] = R[0] * x + R[1] * y + R[2] * z + p[0];
            wcen[i * 3 + 1] = R[3] * x + R[4] * y + R[5] * z + p[1];
            wcen[i * 3 + 2] = R[6] * x + R[7] * y + R[8] * z + p[2];
        }
    }

    /** Inverse Trägheit in Welt mal Vektor. */
    void invInertiaWorld(double[] a, double[] out) {
        double lx = R[0] * a[0] + R[3] * a[1] + R[6] * a[2];
        double ly = R[1] * a[0] + R[4] * a[1] + R[7] * a[2];
        double lz = R[2] * a[0] + R[5] * a[1] + R[8] * a[2];
        lx *= invInertia[0]; ly *= invInertia[1]; lz *= invInertia[2];
        out[0] = R[0] * lx + R[1] * ly + R[2] * lz;
        out[1] = R[3] * lx + R[4] * ly + R[5] * lz;
        out[2] = R[6] * lx + R[7] * ly + R[8] * lz;
    }

    /** „Stück 7: 3 Zellen, 412 m³, 371 t, 2,4 m/s“ */
    @Override public String toString() {
        return String.format(Locale.GERMAN, "Stück %d: %d Zellen, %.1f m³, %.1f t, %.1f m/s%s", id, cells.length, volume, mass / 1000,
                Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), asleep ? ", ruht" : "");
    }
}
