package com.dan.fracture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Ein fester Körper, der noch hält: Zellen mit Flächen zwischen ihnen, Auflager und Lasten. Die Spannung kommt aus
 * {@link Stress}; wo eine Fläche über ihrer Festigkeit liegt, bricht sie. Zellen, die über keine heile Fläche mehr mit
 * einem Auflager verbunden sind, lösen sich als {@link Piece}. Gebaut von {@link Fracture#addSolid}.
 * <p>
 * Zwei Arten Auflager: <b>Anker</b> ({@link #anchorBelow}, {@link #anchor}) binden eine Randfläche an festen Grund
 * jenseits von ihr – mit derselben Feder und derselben Festigkeit wie eine Fläche zwischen zwei Zellen, sodass auch
 * der Fuß abreißen kann und ein gleichmäßiger Druck an der Auflagerfläche genau ankommt. <b>Gehaltene Zellen</b>
 * ({@link #support}) stehen ganz fest, etwa der Teil eines Gletschers weit hinter der Front.
 */
public final class Solid {
    public final int id;
    public final Material material;
    final Fracture world;
    // Zellen
    final int n;
    final double[] seed, cen, vol, mass;
    final Poly[] poly;
    final boolean[] support, attached;
    final double[] load, loadAt;      // äußere Last je Zelle (N) und ihr Angriffspunkt (NaN: Schwerpunkt)
    final double[] contactNow, contact, contactSolved;   // Kräfte und Momente (um die Saat) aufliegender Stücke, 6n
    final double[] rest;              // Kräfte schlafender Stücke, die aufliegen (am Schwerpunkt), 3n
    final double[] peak;              // größte Berührungslast je Zelle seit der letzten Rechnung, 6n
    // Flächen zwischen Zellen
    final int m;
    final int[] bi, bj;
    final double[] bc, bn, bd, ba, ft, fc, coh;
    final boolean[] broken;
    final double[] util;
    final double spacing;
    final List<int[]>[] adj;          // je Zelle: {Fläche, Nachbar}
    // Anker an festen Grund
    int na;
    int[] ai = new int[16];
    double[] ac = new double[48], an = new double[48], ad = new double[16], aa = new double[16], aft = new double[16], afc = new double[16], acoh = new double[16], autil = new double[16];
    boolean[] abroken = new boolean[16];
    final SplittableRandom rnd;
    // Zustand
    final Stress stress = new Stress();
    double timer;
    boolean dirty = true, cracking;
    double maxUtil;
    int worst = -1;
    final List<double[]> crackPath = new ArrayList<>();
    double crackEnergy;
    int brokenCount;
    long lastSolveNs;
    int lastIterations;
    /** Zählt jede Änderung der Gestalt (Bruch, Lösen, Schmelzen): wer ein Netz hält, baut neu, wenn sie sich ändert. */
    volatile int version;

    @SuppressWarnings("unchecked")
    Solid(Fracture world, int id, Voronoi.Result r, Material mat, Fracture.Strength strength, long seedValue) {
        this.world = world;
        this.id = id;
        this.material = mat;
        spacing = r.spacing;
        n = r.cells.length;
        seed = r.seeds;
        poly = r.cells;
        cen = new double[3 * n];
        vol = new double[n];
        mass = new double[n];
        support = new boolean[n];
        attached = new boolean[n];
        load = new double[3 * n];
        loadAt = new double[3 * n];
        Arrays.fill(loadAt, Double.NaN);
        contactNow = new double[6 * n];
        contact = new double[6 * n];
        contactSolved = new double[6 * n];
        rest = new double[3 * n];
        peak = new double[6 * n];
        for (int i = 0; i < n; i++) {
            double[] ms = poly[i].mass();
            vol[i] = ms[0];
            cen[i * 3] = ms[1]; cen[i * 3 + 1] = ms[2]; cen[i * 3 + 2] = ms[3];
            mass[i] = ms[0] * mat.density;
            attached[i] = true;
        }
        m = r.bi.length;
        bi = r.bi;
        bj = r.bj;
        bc = new double[3 * m];
        bn = new double[3 * m];
        bd = new double[m];
        ba = new double[m];
        ft = new double[m];
        fc = new double[m];
        coh = new double[m];
        broken = new boolean[m];
        util = new double[m];
        adj = new List[n];
        for (int i = 0; i < n; i++) adj[i] = new ArrayList<>(16);
        rnd = new SplittableRandom(seedValue * 31 + 7);
        this.strength = strength;
        for (int b = 0; b < m; b++) {
            int i = bi[b], j = bj[b];
            Poly.Face fi = null, fj = null;
            for (Poly.Face f : poly[i].faces) if (f.tag == j) { fi = f; break; }
            for (Poly.Face f : poly[j].faces) if (f.tag == i) { fj = f; break; }
            double aI = fi.area(), aJ = fj != null ? fj.area() : aI;
            ba[b] = Math.min(aI, aJ);
            double[] c = fi.centroid();
            System.arraycopy(c, 0, bc, b * 3, 3);
            double dx = seed[j * 3] - seed[i * 3], dy = seed[j * 3 + 1] - seed[i * 3 + 1], dz = seed[j * 3 + 2] - seed[i * 3 + 2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            bd[b] = d;
            bn[b * 3] = dx / d; bn[b * 3 + 1] = dy / d; bn[b * 3 + 2] = dz / d;
            double[] st = strengths(c);
            ft[b] = st[0]; fc[b] = st[1]; coh[b] = st[2];
            adj[i].add(new int[]{b, j});
            adj[j].add(new int[]{b, i});
        }
    }

    private final Fracture.Strength strength;

    private double[] strengths(double[] c) {
        double s = strength == null ? 1 : Math.max(0, strength.at(c[0], c[1], c[2]));
        double scat = 1 + material.scatter * (2 * rnd.nextDouble() - 1);
        return new double[]{material.tensile * s * scat, material.compressive * s * scat, material.cohesion() * s * scat};
    }

    // ------------------------------------------------------------------ Lesen
    public int cells() { return n; }
    /** Zählt Änderungen der Gestalt (siehe Feld). */
    public int version() { return version; }
    /** Der Stoff (gleich {@link #material}). */
    public Fracture world() { return world; }
    public int bonds() { return m; }
    public int anchors() { return na; }
    public double[] center(int i) { return new double[]{cen[i * 3], cen[i * 3 + 1], cen[i * 3 + 2]}; }
    public double[] seedOf(int i) { return new double[]{seed[i * 3], seed[i * 3 + 1], seed[i * 3 + 2]}; }
    public double volume(int i) { return vol[i]; }
    public Poly cell(int i) { return poly[i]; }
    public boolean attached(int i) { return attached[i]; }
    public boolean supported(int i) { return support[i]; }
    /** Fläche b gebrochen? b &lt; bonds(): zwischen Zellen; darüber: Anker b − bonds(). */
    public boolean broken(int b) { return b < m ? broken[b] : abroken[b - m]; }
    public int bondA(int b) { return b < m ? bi[b] : ai[b - m]; }
    /** Die andere Zelle (−1 beim Anker). */
    public int bondB(int b) { return b < m ? bj[b] : -1; }
    public double bondArea(int b) { return b < m ? ba[b] : aa[b - m]; }
    public double[] bondCenter(int b) {
        return b < m ? new double[]{bc[b * 3], bc[b * 3 + 1], bc[b * 3 + 2]} : new double[]{ac[(b - m) * 3], ac[(b - m) * 3 + 1], ac[(b - m) * 3 + 2]};
    }
    /** Auslastung der Fläche bei der letzten Rechnung (1 = bricht). */
    public double utilization(int b) { return b < m ? util[b] : autil[b - m]; }
    /** Normalspannung (Zug positiv), Schub, Biegerandspannung der Fläche (Pa) bei der letzten Rechnung. */
    public double[] stressOf(int b) {
        if (stress.sn == null || b >= stress.m) return new double[3];
        return new double[]{stress.sn[b], stress.tau[b], stress.sb[b]};
    }
    /** Kraft, die Zelle bondA über die Fläche auf bondB (oder den Grund) ausübt (N), bei der letzten Rechnung. */
    public double[] forceOf(int b) {
        if (stress.force == null || b >= stress.m) return new double[3];
        return new double[]{stress.force[b * 3], stress.force[b * 3 + 1], stress.force[b * 3 + 2]};
    }
    /** Federmoment, das Zelle bondA über die Fläche auf bondB ausübt (N·m), bei der letzten Rechnung. */
    public double[] momentOf(int b) {
        if (stress.moment == null || b >= stress.m) return new double[3];
        return new double[]{stress.moment[b * 3], stress.moment[b * 3 + 1], stress.moment[b * 3 + 2]};
    }
    /** Ist die Fläche zwischen den Zellen i und j gebrochen (oder gibt es keine)? */
    public boolean brokenBetween(int i, int j) {
        for (int[] e : adj[i]) if (e[1] == j) return broken[e[0]];
        return true;
    }

    public double maxUtilization() { return maxUtil; }
    /** Fläche mit der höchsten Auslastung (−1 ohne Rechnung); Nummern ab bonds() sind Anker. */
    public int worstBond() { return worst; }
    public boolean cracking() { return cracking; }
    public int brokenBonds() { return brokenCount; }
    /** Mittlerer Abstand der Saaten (m). */
    public double spacing() { return spacing; }
    public int attachedCount() { int k = 0; for (boolean a : attached) if (a) k++; return k; }
    public double solveMs() { return lastSolveNs / 1e6; }
    public int solveIterations() { return lastIterations; }

    // ------------------------------------------------------------------ Auflager, Lasten, Schwächen

    /** Zelle ganz festhalten. */
    public Solid support(int i, boolean on) { support[i] = on; dirty = true; return this; }

    /** Alle Zellen festhalten, deren Schwerpunkt die Bedingung erfüllt. */
    public Solid support(Predicate<double[]> where) {
        for (int i = 0; i < n; i++) if (where.test(center(i))) support[i] = true;
        dirty = true;
        return this;
    }

    /** Randflächen, die ganz auf oder unter der Höhe y liegen und nach unten zeigen, an den Grund ankern. */
    public Solid anchorBelow(double y) { return anchorBelow(y, 1); }

    /** Wie {@link #anchorBelow(double)}, die Anker k-mal so fest wie der Stoff (ein Fundament). */
    public Solid anchorBelow(double y, double k) {
        return anchor(f -> {
            if (f.ny > -0.2) return false;
            for (int q = 1; q < f.p.length; q += 3) if (f.p[q] > y + 1e-6) return false;
            return true;
        }, k);
    }

    /** Randflächen, die die Bedingung erfüllen, an festen Grund jenseits der Fläche ankern. */
    public Solid anchor(Predicate<Poly.Face> where) { return anchor(where, 1); }

    /** Wie {@link #anchor(Predicate)}, die Anker k-mal so fest wie der Stoff. */
    public Solid anchor(Predicate<Poly.Face> where, double k) {
        for (int i = 0; i < n; i++)
            for (Poly.Face f : poly[i].faces) {
                if (f.tag >= 0 || !where.test(f)) continue;
                double a = f.area();
                if (a < 1e-9) continue;
                double[] c = f.centroid();
                double dist = f.d - (f.nx * seed[i * 3] + f.ny * seed[i * 3 + 1] + f.nz * seed[i * 3 + 2]);
                if (dist <= 1e-9) continue;
                grow();
                ai[na] = i;
                System.arraycopy(c, 0, ac, na * 3, 3);
                an[na * 3] = f.nx; an[na * 3 + 1] = f.ny; an[na * 3 + 2] = f.nz;
                ad[na] = dist;
                aa[na] = a;
                double[] st = strengths(c);
                aft[na] = st[0] * k; afc[na] = st[1] * k; acoh[na] = st[2] * k;
                abroken[na] = false;
                na++;
            }
        dirty = true;
        return this;
    }

    private void grow() {
        if (na < ai.length) return;
        int c = ai.length * 2;
        ai = Arrays.copyOf(ai, c); ad = Arrays.copyOf(ad, c); aa = Arrays.copyOf(aa, c); aft = Arrays.copyOf(aft, c);
        afc = Arrays.copyOf(afc, c); acoh = Arrays.copyOf(acoh, c); autil = Arrays.copyOf(autil, c); abroken = Arrays.copyOf(abroken, c);
        ac = Arrays.copyOf(ac, 3 * c); an = Arrays.copyOf(an, 3 * c);
    }

    /** Äußere Kraft (N) auf Zelle i, am Schwerpunkt. */
    public Solid load(int i, double fx, double fy, double fz) {
        load[i * 3] = fx; load[i * 3 + 1] = fy; load[i * 3 + 2] = fz;
        loadAt[i * 3] = loadAt[i * 3 + 1] = loadAt[i * 3 + 2] = Double.NaN;
        dirty = true;
        return this;
    }

    /** Äußere Kraft (N) auf Zelle i im Punkt (x, y, z). */
    public Solid load(int i, double fx, double fy, double fz, double x, double y, double z) {
        load[i * 3] = fx; load[i * 3 + 1] = fy; load[i * 3 + 2] = fz;
        loadAt[i * 3] = x; loadAt[i * 3 + 1] = y; loadAt[i * 3 + 2] = z;
        dirty = true;
        return this;
    }

    /**
     * Ein Druck p (Pa) auf alle Randflächen, die die Bedingung erfüllen, je Zelle als Kraft in der Mitte ihrer Fläche
     * gegen die Außennormale (etwa eine Auflast oben auf einem Pfeiler). Ersetzt die Lasten dieser Zellen.
     */
    public Solid pressure(Predicate<Poly.Face> where, double p) {
        for (int i = 0; i < n; i++) {
            double fx = 0, fy = 0, fz = 0, mx = 0, my = 0, mz = 0, w = 0;
            for (Poly.Face f : poly[i].faces) {
                if (f.tag >= 0 || !where.test(f)) continue;
                double a = f.area();
                double[] c = f.centroid();
                fx -= p * a * f.nx; fy -= p * a * f.ny; fz -= p * a * f.nz;
                mx += a * c[0]; my += a * c[1]; mz += a * c[2]; w += a;
            }
            if (w > 0) load(i, fx, fy, fz, mx / w, my / w, mz / w);
        }
        return this;
    }

    public double[] loadOf(int i) { return new double[]{load[i * 3], load[i * 3 + 1], load[i * 3 + 2]}; }

    /** Alle Lasten auf null. */
    public Solid clearLoads() { Arrays.fill(load, 0); Arrays.fill(loadAt, Double.NaN); dirty = true; return this; }

    /** Festigkeit der Flächen im Umkreis r um (x, y, z) mit dem Faktor k (weich zum Rand) schwächen. */
    public Solid weaken(double x, double y, double z, double r, double k) {
        for (int b = 0; b < m + na; b++) {
            double[] c = bondCenter(b);
            double dx = c[0] - x, dy = c[1] - y, dz = c[2] - z;
            double q = Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
            if (q >= 1) continue;
            double w = 1 - (1 - k) * (1 - q * q);
            if (b < m) { ft[b] *= w; fc[b] *= w; coh[b] *= w; }
            else { aft[b - m] *= w; afc[b - m] *= w; acoh[b - m] *= w; }
        }
        dirty = true;
        return this;
    }

    /**
     * Zellen, deren Schwerpunkt die Bedingung erfüllt, verschwinden ohne Stück (sie schmelzen weg, werden ausgespült):
     * so frisst eine Schmelzkerbe sich in eine Front. Gibt die Zahl zurück.
     */
    public int melt(Predicate<double[]> where) {
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (!attached[i] || !where.test(center(i))) continue;
            attached[i] = false;
            k++;
        }
        if (k > 0) { dirty = true; version++; world.staticDirty = true; }
        return k;
    }

    /** Wie {@link #melt(Predicate)}, die Bedingung nach der Nummer der Zelle (etwa nach ihrer Gestalt). */
    public int meltCells(java.util.function.IntPredicate which) {
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (!attached[i] || !which.test(i)) continue;
            attached[i] = false;
            k++;
        }
        if (k > 0) { dirty = true; version++; world.staticDirty = true; }
        return k;
    }

    /** Fläche b brechen (von Hand); die Zellen lösen sich beim nächsten Schritt, wenn nichts mehr hält. */
    public void breakBond(int b) {
        if (broken(b)) return;
        if (b < m) broken[b] = true; else abroken[b - m] = true;
        brokenCount++;
        version++;
        dirty = true;
    }

    /** Gegenkraft eines Stücks auf Zelle i im Punkt (x, y, z) in diesem Schritt (aus {@link Contacts}). */
    void contactForce(int i, double fx, double fy, double fz, double x, double y, double z) {
        double px = x - seed[i * 3], py = y - seed[i * 3 + 1], pz = z - seed[i * 3 + 2];
        contactNow[i * 6] += fx; contactNow[i * 6 + 1] += fy; contactNow[i * 6 + 2] += fz;
        contactNow[i * 6 + 3] += py * fz - pz * fy;
        contactNow[i * 6 + 4] += pz * fx - px * fz;
        contactNow[i * 6 + 5] += px * fy - py * fx;
    }

    /**
     * Nach jedem Schritt: die Kräfte aufliegender Stücke über die Stoßdauer glätten (ein Aufschlag wirkt so lange, wie
     * er dauert, nicht nur einen Schritt). Ändern sie sich merklich gegen die letzte Rechnung, ist neu zu rechnen.
     */
    /** Die Last eines Stücks, das auf Zelle i einschläft (sign +1) oder wieder aufwacht (−1). */
    void restLoad(int i, double[] f, double sign) {
        rest[i * 3] += sign * f[0]; rest[i * 3 + 1] += sign * f[1]; rest[i * 3 + 2] += sign * f[2];
        dirty = true;
    }

    void settleContacts(double dt, double contactTime, double gravity) {
        double a = Math.min(1, dt / Math.max(dt, contactTime));
        boolean change = false;
        for (int i = 0; i < n; i++) {
            double lim = 0.1 * mass[i] * gravity + 1e3;
            for (int q = 0; q < 6; q++) {
                int k = i * 6 + q;
                contact[k] += a * (contactNow[k] - contact[k]);
                contactNow[k] = 0;
            }
            // Spitze seit der letzten Rechnung festhalten: ein Aufschlag zwischen zwei Rechnungen geht nicht verloren
            double cp = contact[i * 6] * contact[i * 6] + contact[i * 6 + 1] * contact[i * 6 + 1] + contact[i * 6 + 2] * contact[i * 6 + 2];
            double pp = peak[i * 6] * peak[i * 6] + peak[i * 6 + 1] * peak[i * 6 + 1] + peak[i * 6 + 2] * peak[i * 6 + 2];
            if (cp > pp) System.arraycopy(contact, i * 6, peak, i * 6, 6);
            if (!change && attached[i]) {
                double dx = contact[i * 6] - contactSolved[i * 6], dy = contact[i * 6 + 1] - contactSolved[i * 6 + 1], dz = contact[i * 6 + 2] - contactSolved[i * 6 + 2];
                if (dx * dx + dy * dy + dz * dz > lim * lim) change = true;
            }
        }
        if (change) dirty = true;
    }

    // ------------------------------------------------------------------ Rechnen

    /**
     * Rechnet die Spannung (Eigengewicht, Auftrieb, Lasten) und die Auslastung jeder Fläche; gibt die größte zurück.
     * Zusammenhänge ohne Auflager sollten vorher gelöst sein ({@link Fracture#step} tut das).
     */
    public double solve() {
        assemble();
        crunch();
        return finish();
    }

    /** Läuft gerade eine Rechnung in einem anderen Faden (siehe {@link Fracture#asyncSolve})? */
    java.util.concurrent.Future<?> job;
    private int solveIter;

    /**
     * Teil 1 der Rechnung (im Faden von {@link Fracture#step}): Lasten und Flächen in die Rechnung übernehmen. Danach
     * darf sich der Körper ändern; {@link #crunch} rechnet mit dem übernommenen Stand.
     */
    void assemble() {
        Fracture w = world;
        int[] idx = new int[n];
        long tSetup;
        int k = 0;
        for (int i = 0; i < n; i++) idx[i] = attached[i] ? k++ : -1;
        int mt = m + na;
        Stress s = stress;
        if (s.n != k || s.m != mt) s.init(k, mt);
        s.young = material.young;
        Arrays.fill(s.f, 0);
        for (int i = 0; i < n; i++) {
            int q = idx[i];
            if (q < 0) continue;
            System.arraycopy(seed, i * 3, s.x, q * 3, 3);
            s.fixed[q] = support[i];
            double sx = seed[i * 3], sy = seed[i * 3 + 1], sz = seed[i * 3 + 2];
            addForce(s.f, q, 0, -mass[i] * w.gravity, 0, cen[i * 3] - sx, cen[i * 3 + 1] - sy, cen[i * 3 + 2] - sz);
            double lx = load[i * 3], ly = load[i * 3 + 1], lz = load[i * 3 + 2];
            if (lx != 0 || ly != 0 || lz != 0) {
                double px = Double.isNaN(loadAt[i * 3]) ? cen[i * 3] : loadAt[i * 3];
                double py = Double.isNaN(loadAt[i * 3]) ? cen[i * 3 + 1] : loadAt[i * 3 + 1];
                double pz = Double.isNaN(loadAt[i * 3]) ? cen[i * 3 + 2] : loadAt[i * 3 + 2];
                addForce(s.f, q, lx, ly, lz, px - sx, py - sy, pz - sz);
            }
            for (int t = 0; t < 6; t++) s.f[q * 6 + t] += peak[i * 6 + t];
            if (rest[i * 3] != 0 || rest[i * 3 + 1] != 0 || rest[i * 3 + 2] != 0)
                addForce(s.f, q, rest[i * 3], rest[i * 3 + 1], rest[i * 3 + 2], cen[i * 3] - sx, cen[i * 3 + 1] - sy, cen[i * 3 + 2] - sz);
            // Auftrieb des Teils unter Wasser
            if (w.water != null) {
                double h = w.water.heightAt(cen[i * 3], cen[i * 3 + 2], w.time);
                if (!Double.isNaN(h)) {
                    double[] sub = poly[i].maxAbove(0, 1, 0, h) <= 0 ? new double[]{vol[i], cen[i * 3], cen[i * 3 + 1], cen[i * 3 + 2]} : submerged(poly[i], h);
                    if (sub[0] > 0) addForce(s.f, q, 0, w.waterDensity * w.gravity * sub[0], 0, sub[1] - sx, sub[2] - sy, sub[3] - sz);
                }
            }
        }
        for (int b = 0; b < m; b++) {
            s.bi[b] = Math.max(0, idx[bi[b]]);
            s.bj[b] = Math.max(0, idx[bj[b]]);
            s.active[b] = !broken[b] && attached[bi[b]] && attached[bj[b]];
            System.arraycopy(bc, b * 3, s.c, b * 3, 3);
            System.arraycopy(bn, b * 3, s.nrm, b * 3, 3);
            s.d[b] = bd[b];
            s.area[b] = ba[b];
        }
        for (int a = 0; a < na; a++) {
            int b = m + a;
            s.bi[b] = Math.max(0, idx[ai[a]]);
            s.bj[b] = -1;
            s.active[b] = !abroken[a] && attached[ai[a]];
            System.arraycopy(ac, a * 3, s.c, b * 3, 3);
            System.arraycopy(an, a * 3, s.nrm, b * 3, 3);
            s.d[b] = ad[a];
            s.area[b] = aa[a];
        }
        solveIter = Math.max(300, Math.min(8000, 40 * (int) Math.cbrt(Math.max(1, k)) * 10));
        System.arraycopy(contact, 0, contactSolved, 0, 6 * n);
        System.arraycopy(contact, 0, peak, 0, 6 * n);
        dirty = false;
    }

    /** Teil 2: das Gleichungssystem (darf in einem anderen Faden laufen; fasst nur die Rechnung an). */
    void crunch() {
        long t0 = System.nanoTime();
        stress.solve(2e-6, solveIter);
        lastIterations = stress.iterations;
        lastSolveNs = System.nanoTime() - t0;
    }

    /**
     * Teil 3 (wieder im Faden von {@link Fracture#step}): Auslastung je Fläche. Flächen, die seit Teil 1 gebrochen
     * oder gelöst sind, zählen nicht mehr.
     */
    double finish() {
        Stress s = stress;
        int mt = s.m;
        maxUtil = 0;
        worst = -1;
        double mu = material.mu();
        for (int b = 0; b < mt; b++) {
            double u = 0;
            boolean now = b < m ? b < this.m && !broken[b] && attached[bi[b]] && attached[bj[b]] : b - m < na && !abroken[b - m] && attached[ai[b - m]];
            if (s.active[b] && now) {
                u = b < m ? Stress.utilization(s.sn[b], s.tau[b], s.sb[b], ft[b], fc[b], coh[b], mu)
                        : Stress.utilization(s.sn[b], s.tau[b], s.sb[b], aft[b - m], afc[b - m], acoh[b - m], mu);
            }
            if (b < m) util[b] = u; else autil[b - m] = u;
            if (u > maxUtil) { maxUtil = u; worst = b; }
        }
        return maxUtil;
    }

    private static void addForce(double[] f, int q, double fx, double fy, double fz, double px, double py, double pz) {
        f[q * 6] += fx; f[q * 6 + 1] += fy; f[q * 6 + 2] += fz;
        f[q * 6 + 3] += py * fz - pz * fy;
        f[q * 6 + 4] += pz * fx - px * fz;
        f[q * 6 + 5] += px * fy - py * fx;
    }

    /** Volumen und Schwerpunkt des Teils unter der Höhe h {V, x, y, z}. */
    static double[] submerged(Poly p, double h) {
        if (p.maxAbove(0, 1, 0, h) <= 0) {
            double[] ms = p.mass();
            return new double[]{ms[0], ms[1], ms[2], ms[3]};
        }
        if (p.maxAbove(0, -1, 0, -h) <= 0) return new double[4];
        Poly c = p.copy();
        c.clip(0, 1, 0, h, -2);
        if (c.isEmpty()) return new double[4];
        double[] ms = c.mass();
        return new double[]{ms[0], ms[1], ms[2], ms[3]};
    }

    /**
     * Bricht, was über der Festigkeit liegt: die am stärksten ausgelasteten Flächen (alle über max(1, 0,85 · größte),
     * höchstens maxBreaks). Gibt die Zahl gebrochener Flächen zurück; energy[0] bekommt die freigesetzte Energie dazu.
     */
    int breakOverloaded(int maxBreaks, double[] energy) {
        if (maxUtil <= 1) return 0;
        double lim = Math.max(1, 0.85 * maxUtil);
        List<Integer> over = new ArrayList<>();
        for (int b = 0; b < m + na; b++) if (!broken(b) && utilization(b) > lim) over.add(b);
        over.sort((a, c) -> Double.compare(utilization(c), utilization(a)));
        int k = 0;
        for (int b : over) {
            if (k >= maxBreaks) break;
            breakBond(b);
            double sn = stress.sn[b], tau = stress.tau[b];
            // freigesetzte elastische Energie der Fläche: (σ² + τ²)/(2E) · A · d
            double u = Math.max(1, utilization(b));
            energy[0] += (sn * sn + tau * tau) / (2 * material.young) * stress.area[b] * stress.d[b] / (u * u);
            crackPath.add(bondCenter(b));
            k++;
        }
        return k;
    }

    /** Hält die Zelle selbst (ganz fest oder mit einem heilen Anker)? */
    private boolean holds(int i, boolean[] anchored) { return support[i] || anchored[i]; }

    /** Zusammenhänge ohne Auflager: je Gruppe die Zellnummern (die Zellen werden als gelöst markiert). */
    List<int[]> detach() {
        boolean[] anchored = new boolean[n];
        for (int a = 0; a < na; a++) if (!abroken[a]) anchored[ai[a]] = true;
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (attached[i] && holds(i, anchored)) { seen[i] = true; q.add(i); }
        while (!q.isEmpty()) {
            int i = q.poll();
            for (int[] e : adj[i]) {
                int j = e[1];
                if (seen[j] || !attached[j] || broken[e[0]]) continue;
                seen[j] = true;
                q.add(j);
            }
        }
        List<int[]> groups = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!attached[i] || seen[i]) continue;
            List<Integer> g = new ArrayList<>();
            seen[i] = true;
            q.add(i);
            while (!q.isEmpty()) {
                int a = q.poll();
                g.add(a);
                for (int[] e : adj[a]) {
                    int j = e[1];
                    if (seen[j] || !attached[j] || broken[e[0]]) continue;
                    seen[j] = true;
                    q.add(j);
                }
            }
            int[] arr = new int[g.size()];
            for (int t = 0; t < arr.length; t++) { arr[t] = g.get(t); attached[arr[t]] = false; }
            groups.add(arr);
        }
        if (!groups.isEmpty()) { dirty = true; version++; }
        return groups;
    }

    /** „Körper 1 (Eis): 412 Zellen, 2 870 Flächen, 64 Anker, 12 gebrochen, 400 halten · Auslastung 0,83 · 12 ms“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Körper %d (%s): %,d Zellen, %,d Flächen, %,d Anker, %,d gebrochen, %,d halten · Auslastung %.2f · %.1f ms, %d Schritte",
                id, material.name, n, m, na, brokenCount, attachedCount(), maxUtil, solveMs(), lastIterations);
    }
}
