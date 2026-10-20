package com.dan.fracture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Berührungen und Stöße der Stücke: mit dem Boden, mit den Zellen der Körper, die noch halten, und untereinander.
 * Erkennung: Hüllkugeln, dann Ecken und Kantenmitten der einen Zelle in der anderen (beide Richtungen), mit einem
 * Saum von 2 cm, damit nichts durchschlüpft. Lösung: Impulsverfahren mit Reibung (Coulomb), Stoßzahl, Warmstart aus dem
 * letzten Schritt und einer Lagekorrektur, die Eindringen über 1 cm in 0,2 Schritten abbaut.
 */
final class Contacts {
    static final double MARGIN = 0.02, SLOP = 0.01, BETA = 0.2, MAX_BIAS = 3.0;
    static final int PER_PAIR = 6;
    /** Glacier Phase 16 (Dauerlauf): Vorrat an Berührungen, der nach einer Spitze stehen bleiben darf. */
    static final int POOL_KEEP = 8192;

    private final Fracture world;
    // die Zellen der haltenden Körper
    private final List<double[]> sPts = new ArrayList<>();
    private final List<Poly> sPoly = new ArrayList<>();
    private final List<Material> sMat = new ArrayList<>();
    private final List<Solid> sSolid = new ArrayList<>();
    private int[] sIdx = new int[0];
    private double[] sCen = new double[0], sRad = new double[0];
    private final Map<Long, int[]> sGrid = new HashMap<>();
    private double sCell = 4;
    private int[] sStamp = new int[0];
    private int stampNo;
    // Berührungen
    private final List<Contact> list = new ArrayList<>();
    private final List<Contact> pool = new ArrayList<>();
    private int used;
    /** Höchstens so viele Berührungen je Schritt (Fracture.maxContacts); was darüber geht, fällt in diesem Schritt weg. */
    int max = 40000;
    private int peak;
    private long capped;
    private final List<Contact> found = new ArrayList<>();
    private LongMap warm = new LongMap(), next = new LongMap();
    private final double[] t3 = new double[3], s3 = new double[3];

    /** Stöße vom letzten Schritt je Berührung (offene Adressierung, ohne Kästchen für long). */
    static final class LongMap {
        long[] keys = new long[1024];
        double[] vals = new double[3 * 1024];
        boolean[] full = new boolean[1024];
        int size;

        void clear() { java.util.Arrays.fill(full, false); size = 0; }

        /** Nach einer Spitze wieder klein (die Tabelle wächst nur, sonst bliebe sie so groß wie die größte Spitze). */
        void shrinkTo(int need) {
            int cap = 1024;
            while (cap < need * 4) cap *= 2;
            if (keys.length <= cap * 4) return;
            keys = new long[cap];
            vals = new double[3 * cap];
            full = new boolean[cap];
            size = 0;
        }

        void put(long k, double a, double b, double c) {
            if (size * 2 >= keys.length) grow();
            int i = slot(k);
            if (!full[i]) { full[i] = true; keys[i] = k; size++; }
            vals[i * 3] = a; vals[i * 3 + 1] = b; vals[i * 3 + 2] = c;
        }

        /** Index oder −1. */
        int find(long k) {
            int i = slot(k);
            return full[i] ? i : -1;
        }

        private int slot(long k) {
            int mask = keys.length - 1;
            int i = (int) (k ^ (k >>> 29) ^ (k >>> 47)) & mask;
            while (full[i] && keys[i] != k) i = (i + 1) & mask;
            return i;
        }

        private void grow() {
            long[] ok = keys;
            double[] ov = vals;
            boolean[] of = full;
            keys = new long[ok.length * 2];
            vals = new double[ov.length * 2];
            full = new boolean[ok.length * 2];
            size = 0;
            for (int i = 0; i < ok.length; i++) if (of[i]) put(ok[i], ov[i * 3], ov[i * 3 + 1], ov[i * 3 + 2]);
        }
    }

    private Contact obtain() {
        if (used == pool.size()) pool.add(new Contact());
        Contact c = pool.get(used++);
        c.b = null; c.bDyn = false; c.stat = -1; c.ln = c.lt1 = c.lt2 = c.lp = 0;
        return c;
    }
    private double maxDepth;
    private Piece[] sweep = new Piece[0];
    private int sweepSet;
    private final double[] rv = new double[3];

    static final class Contact {
        Piece a, b;          // b: anderes Stück (auch schlafend) oder null (Boden, Körper)
        boolean bDyn;
        int cellA;
        long key;
        double px, py, pz, nx, ny, nz, depth;
        double t1x, t1y, t1z, t2x, t2y, t2z;
        double[] ra = new double[3], rb = new double[3], ts = new double[3];
        double mn, mt1, mt2, bias, mu, target;
        double ln, lt1, lt2, lp;
        double vn0;
        int stat = -1;       // Zelle eines haltenden Körpers (Index in der Liste), sonst −1
    }

    Contacts(Fracture w) { this.world = w; }

    int count() { return list.size(); }
    double maxDepth() { return maxDepth; }

    void rebuildStatic() {
        sPts.clear(); sPoly.clear(); sMat.clear(); sGrid.clear(); sSolid.clear();
        List<double[]> cen = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        double spacing = 0;
        int ns = 0;
        for (Solid s : world.solids) {
            spacing += s.spacing;
            ns++;
            for (int i = 0; i < s.n; i++) {
                if (!s.attached[i]) continue;
                Poly p = s.poly[i];
                // nur Zellen an der Oberfläche des Körpers (eine Fläche nach außen oder zu einer gelösten Zelle)
                boolean surface = false;
                for (Poly.Face f : p.faces) if (f.tag < 0 || f.tag >= s.n || !s.attached[f.tag]) { surface = true; break; }
                if (!surface) continue;
                double[] v = p.vertices(), e = p.edgeMids();
                double[] all = Arrays.copyOf(v, v.length + e.length);
                System.arraycopy(e, 0, all, v.length, e.length);
                double r = 0;
                for (int k = 0; k < v.length; k += 3) {
                    double dx = v[k] - s.cen[i * 3], dy = v[k + 1] - s.cen[i * 3 + 1], dz = v[k + 2] - s.cen[i * 3 + 2];
                    r = Math.max(r, dx * dx + dy * dy + dz * dz);
                }
                sPts.add(all);
                sPoly.add(p);
                sMat.add(s.material);
                sSolid.add(s);
                idx.add(i);
                cen.add(new double[]{s.cen[i * 3], s.cen[i * 3 + 1], s.cen[i * 3 + 2], Math.sqrt(r)});
            }
        }
        int n = cen.size();
        sCen = new double[3 * n];
        sRad = new double[n];
        sStamp = new int[n];
        sIdx = new int[n];
        for (int i = 0; i < n; i++) sIdx[i] = idx.get(i);
        sCell = ns > 0 ? Math.max(0.5, 2 * spacing / ns) : 4;
        for (int i = 0; i < n; i++) {
            double[] c = cen.get(i);
            sCen[i * 3] = c[0]; sCen[i * 3 + 1] = c[1]; sCen[i * 3 + 2] = c[2];
            sRad[i] = c[3];
            int x0 = cell(c[0] - c[3]), x1 = cell(c[0] + c[3]), y0 = cell(c[1] - c[3]), y1 = cell(c[1] + c[3]), z0 = cell(c[2] - c[3]), z1 = cell(c[2] + c[3]);
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) {
                        long k = key(x, y, z);
                        int[] l = sGrid.get(k);
                        if (l == null) sGrid.put(k, l = new int[5]);
                        if (l[0] + 1 >= l.length) sGrid.put(k, l = Arrays.copyOf(l, l.length * 2));
                        l[++l[0]] = i;
                    }
        }
    }

    private int cell(double v) { return (int) Math.floor(v / sCell); }
    private static long key(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

    double detectMs, solveMs;

    void solve(List<Piece> awake, double dt) {
        long tStart = System.nanoTime();
        list.clear();
        used = 0;
        maxDepth = 0;
        long step = world.steps;
        for (Piece p : awake) p.refresh(step);
        // Boden und Körper
        for (Piece a : awake) {
            mg = margin(a, dt);
            for (int i = 0; i < a.cells.length; i++) {
                if (!a.surf[i]) continue;
                if (world.ground != null) groundContacts(a, i);
                if (!sPoly.isEmpty()) staticContacts(a, i);
            }
        }
        // Stücke untereinander: sortieren nach x und fegen
        List<Piece> all = world.pieces;
        int n = all.size();
        if (n > 1) {
            // die Reihenfolge vom letzten Schritt ist fast richtig: Einfügen sortiert in fast linearer Zeit
            if (sweep.length != n || sweepSet != all.hashCode()) {
                sweep = all.toArray(new Piece[0]);
                sweepSet = all.hashCode();
            }
            Piece[] arr = sweep;
            maxSpeed = 0;
            for (Piece p : arr) if (!p.asleep) maxSpeed = Math.max(maxSpeed, margin(p, 1) - MARGIN);
            for (int i = 1; i < n; i++) {
                Piece x = arr[i];
                double kx = x.p[0] - x.radius;
                int j = i - 1;
                while (j >= 0 && (arr[j].p[0] - arr[j].radius > kx || (arr[j].p[0] - arr[j].radius == kx && arr[j].id > x.id))) {
                    arr[j + 1] = arr[j];
                    j--;
                }
                arr[j + 1] = x;
            }
            // Werte am Stück in flache Felder, damit die innere Schleife nicht über Objekte springt
            if (sx0.length < n) { sx0 = new double[n]; sx1 = new double[n]; sy = new double[n]; sz = new double[n]; srad = new double[n]; ssl = new boolean[n]; }
            for (int i = 0; i < n; i++) {
                Piece a = arr[i];
                sx0[i] = a.p[0] - a.radius;
                sx1[i] = a.p[0] + a.radius + (a.asleep ? MARGIN : margin(a, dt));
                sy[i] = a.p[1]; sz[i] = a.p[2]; srad[i] = a.radius; ssl[i] = a.asleep;
            }
            double reach = maxSpeed * dt, extra = MARGIN + 2 * reach;
            for (int i = 0; i < n; i++) {
                double maxX = sx1[i], yi = sy[i], zi = sz[i], ri = srad[i];
                boolean si = ssl[i];
                for (int j = i + 1; j < n; j++) {
                    if (sx0[j] - reach > maxX) break;
                    if (si && ssl[j]) continue;
                    double bound = ri + srad[j] + extra, dy = yi - sy[j], dz = zi - sz[j];
                    if (dy > bound || dy < -bound || dz > bound || dz < -bound) continue;
                    Piece a = arr[i], b = arr[j];
                    double dx = a.p[0] - b.p[0], rr = a.radius + b.radius;
                    double rvx = a.v[0] - b.v[0], rvy = a.v[1] - b.v[1], rvz = a.v[2] - b.v[2];
                    double wa = Math.sqrt(a.w[0] * a.w[0] + a.w[1] * a.w[1] + a.w[2] * a.w[2]) * a.radius, wb = Math.sqrt(b.w[0] * b.w[0] + b.w[1] * b.w[1] + b.w[2] * b.w[2]) * b.radius;
                    mg = MARGIN + (Math.sqrt(rvx * rvx + rvy * rvy + rvz * rvz) + wa + wb) * dt;
                    if (dx * dx + dy * dy + dz * dz > (rr + mg) * (rr + mg)) continue;
                    Piece first = a, second = b;
                    if (a.asleep || (!b.asleep && b.id < a.id)) { first = b; second = a; }
                    first.refresh(step);
                    second.refresh(step);
                    pieceContacts(first, second);
                }
            }
        }
        long tDetect = System.nanoTime();
        detectMs = detectMs * 0.95 + (tDetect - tStart) / 1e6 * 0.05;
        // vorbereiten
        next.clear();
        double[] tmp = new double[3], cr = new double[3];
        for (Contact c : list) {
            Piece a = c.a, b = c.b;
            c.ra[0] = c.px - a.p[0]; c.ra[1] = c.py - a.p[1]; c.ra[2] = c.pz - a.p[2];
            if (b != null) { c.rb[0] = c.px - b.p[0]; c.rb[1] = c.py - b.p[1]; c.rb[2] = c.pz - b.p[2]; }
            // Tangente senkrecht zur Normale (wie Poly.perp, ohne neues Feld)
            {
                double ax = Math.abs(c.nx) < 0.7 ? 1 : 0, ay = ax == 1 ? 0 : 1;
                double ux = -c.nz * ay, uy = c.nz * ax, uz = c.nx * ay - c.ny * ax;
                double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
                c.t1x = ux / l; c.t1y = uy / l; c.t1z = uz / l;
            }
            c.t2x = c.ny * c.t1z - c.nz * c.t1y; c.t2y = c.nz * c.t1x - c.nx * c.t1z; c.t2z = c.nx * c.t1y - c.ny * c.t1x;
            c.mn = 1 / effMass(c, c.nx, c.ny, c.nz, tmp, cr);
            c.mt1 = 1 / effMass(c, c.t1x, c.t1y, c.t1z, tmp, cr);
            c.mt2 = 1 / effMass(c, c.t2x, c.t2y, c.t2z, tmp, cr);
            double[] rel = relVel(c);
            c.vn0 = rel[0] * c.nx + rel[1] * c.ny + rel[2] * c.nz;
            // Eindringen baut ein eigener Durchgang ab, der nur die Lage schiebt (split impulse), nicht die Geschwindigkeit:
            // so gewinnt kein Stück Energie, wenn es zwischen zwei anderen eingeklemmt ist
            c.bias = c.depth > SLOP ? Math.min(MAX_BIAS, BETA * (c.depth - SLOP) / dt) : 0;
            double gap = c.depth < 0 ? c.depth / dt : 0;   // Spalt: darf sich nur bis zur Berührung nähern
            double e = c.b != null ? 0.5 * (a.material.restitution + c.b.material.restitution) : a.material.restitution;
            // Stoßzahl nur bei echter Berührung; ein Spalt darf sich in diesem Schritt nur schließen
            c.target = c.depth >= 0 && c.vn0 < -1.0 ? -e * c.vn0 : gap;
            c.lp = 0;
            int wi = warm.find(c.key);
            if (wi >= 0 && c.depth >= 0) {
                c.ln = warm.vals[wi * 3] * 0.85; c.lt1 = warm.vals[wi * 3 + 1] * 0.85; c.lt2 = warm.vals[wi * 3 + 2] * 0.85;
                apply(c, c.ln * c.nx + c.lt1 * c.t1x + c.lt2 * c.t2x, c.ln * c.ny + c.lt1 * c.t1y + c.lt2 * c.t2y, c.ln * c.nz + c.lt1 * c.t1z + c.lt2 * c.t2z, tmp);
            }
            maxDepth = Math.max(maxDepth, c.depth);
        }
        // lösen
        int iters = world.solverIterations();
        for (int it = 0; it < iters; it++) {
            for (Contact c : list) {
                double[] rel = relVel(c);
                double vn = rel[0] * c.nx + rel[1] * c.ny + rel[2] * c.nz;
                double dl = c.mn * (c.target - vn);
                double ln = Math.max(0, c.ln + dl);
                dl = ln - c.ln;
                c.ln = ln;
                if (dl != 0) apply(c, dl * c.nx, dl * c.ny, dl * c.nz, tmp);
                // Reibung
                rel = relVel(c);
                double lim = c.mu * c.ln;
                double v1 = rel[0] * c.t1x + rel[1] * c.t1y + rel[2] * c.t1z;
                double l1 = Math.max(-lim, Math.min(lim, c.lt1 - c.mt1 * v1));
                double d1 = l1 - c.lt1;
                c.lt1 = l1;
                double v2 = rel[0] * c.t2x + rel[1] * c.t2y + rel[2] * c.t2z;
                double l2 = Math.max(-lim, Math.min(lim, c.lt2 - c.mt2 * v2));
                double d2 = l2 - c.lt2;
                c.lt2 = l2;
                if (d1 != 0 || d2 != 0) apply(c, d1 * c.t1x + d2 * c.t2x, d1 * c.t1y + d2 * c.t2y, d1 * c.t1z + d2 * c.t2z, tmp);
            }
        }
        // Lagekorrektur mit Scheingeschwindigkeiten
        for (int it = 0; it < iters; it++) {
            for (Contact c : list) {
                if (c.bias <= 0) continue;
                Piece a = c.a;
                double vx = a.pv[0] + a.pw[1] * c.ra[2] - a.pw[2] * c.ra[1];
                double vy = a.pv[1] + a.pw[2] * c.ra[0] - a.pw[0] * c.ra[2];
                double vz = a.pv[2] + a.pw[0] * c.ra[1] - a.pw[1] * c.ra[0];
                if (c.bDyn) {
                    Piece b = c.b;
                    vx -= b.pv[0] + b.pw[1] * c.rb[2] - b.pw[2] * c.rb[1];
                    vy -= b.pv[1] + b.pw[2] * c.rb[0] - b.pw[0] * c.rb[2];
                    vz -= b.pv[2] + b.pw[0] * c.rb[1] - b.pw[1] * c.rb[0];
                }
                double vn = vx * c.nx + vy * c.ny + vz * c.nz;
                double lp = Math.max(0, c.lp + c.mn * (c.bias - vn));
                double dl = lp - c.lp;
                c.lp = lp;
                if (dl == 0) continue;
                double jx = dl * c.nx, jy = dl * c.ny, jz = dl * c.nz;
                a.pv[0] += jx * a.invMass; a.pv[1] += jy * a.invMass; a.pv[2] += jz * a.invMass;
                t3[0] = c.ra[1] * jz - c.ra[2] * jy; t3[1] = c.ra[2] * jx - c.ra[0] * jz; t3[2] = c.ra[0] * jy - c.ra[1] * jx;
                a.invInertiaWorld(t3, tmp);
                a.pw[0] += tmp[0]; a.pw[1] += tmp[1]; a.pw[2] += tmp[2];
                if (c.bDyn) {
                    Piece b = c.b;
                    b.pv[0] -= jx * b.invMass; b.pv[1] -= jy * b.invMass; b.pv[2] -= jz * b.invMass;
                    t3[0] = c.rb[1] * jz - c.rb[2] * jy; t3[1] = c.rb[2] * jx - c.rb[0] * jz; t3[2] = c.rb[0] * jy - c.rb[1] * jx;
                    b.invInertiaWorld(t3, tmp);
                    b.pw[0] -= tmp[0]; b.pw[1] -= tmp[1]; b.pw[2] -= tmp[2];
                }
            }
        }
        // merken, Stoß je Stück, Aufschläge, Wecken
        for (Contact c : list) {
            next.put(c.key, c.ln, c.lt1, c.lt2);
            Piece a = c.a;
            double jx = c.ln * c.nx, jy = c.ln * c.ny, jz = c.ln * c.nz;
            a.impulse[0] += jx; a.impulse[1] += jy; a.impulse[2] += jz;
            if (c.ln > a.impulseMax) { a.impulseMax = c.ln; a.impulseCell = c.cellA; }
            if (c.b != null && c.b.asleep && (c.vn0 < -0.5 || c.depth > 0.05)) c.b.wake();
            // Gegenkraft auf den haltenden Körper (sie geht in dessen Spannung ein), je Stück gesammelt
            if (c.stat >= 0) {
                if (c.a != loadPiece) flushLoads();
                loadPiece = c.a;
                loadF[0] -= (c.ln * c.nx + c.lt1 * c.t1x + c.lt2 * c.t2x) / dt;
                loadF[1] -= (c.ln * c.ny + c.lt1 * c.t1y + c.lt2 * c.t2y) / dt;
                loadF[2] -= (c.ln * c.nz + c.lt1 * c.t1z + c.lt2 * c.t2z) / dt;
                loadCells.merge(c.stat, 1, Integer::sum);
            }
            if (c.vn0 < -world.impactSpeed && c.vn0 * dt <= c.depth && a.impactCool <= 0 && world.onImpact != null) {
                a.impactCool = 0.3;
                world.onImpact.impact(c.px, c.py, c.pz, a.volume, -c.vn0, false);
            }
        }
        flushLoads();
        LongMap sw = warm;
        warm = next;
        next = sw;
        // Phase 16 (Dauerlauf): nach einer Spitze (ein Damm stürzt ein) den Vorrat wieder freigeben
        peak = Math.max(peak, list.size());
        if (pool.size() > 2 * Math.max(used, POOL_KEEP)) {
            pool.subList(Math.max(used, POOL_KEEP), pool.size()).clear();
            if (pool instanceof ArrayList<Contact> al) al.trimToSize();
        }
        next.shrinkTo(list.size());
        solveMs = solveMs * 0.95 + (System.nanoTime() - tDetect) / 1e6 * 0.05;
    }

    // Last eines Stücks auf den Körper: die Summe seiner Berührungen, verteilt auf die berührten Zellen nach der Zahl
    // ihrer Berührungspunkte und in deren Schwerpunkt angesetzt. Die Aufteilung der Stöße auf die einzelnen Punkte ist
    // im Impulsverfahren beliebig; die Summe nicht.
    private Piece loadPiece;
    private final double[] loadF = new double[3];
    private final Map<Integer, Integer> loadCells = new java.util.TreeMap<>();

    private void flushLoads() {
        Piece p = loadPiece;
        if (p != null && !loadCells.isEmpty()) {
            int total = 0;
            for (int k : loadCells.values()) total += k;
            p.rest.clear();
            for (Map.Entry<Integer, Integer> e : loadCells.entrySet()) {
                int st = e.getKey();
                double w = e.getValue() / (double) total;
                Solid sol = sSolid.get(st);
                int i = sIdx[st];
                sol.contactForce(i, loadF[0] * w, loadF[1] * w, loadF[2] * w, sol.cen[i * 3], sol.cen[i * 3 + 1], sol.cen[i * 3 + 2]);
                p.rest.add(new Object[]{sol, i, new double[]{loadF[0] * w, loadF[1] * w, loadF[2] * w}});
            }
        }
        loadPiece = null;
        loadF[0] = loadF[1] = loadF[2] = 0;
        loadCells.clear();
    }

    /** Saum für Berührungen, die erst kommen: 2 cm plus der Weg eines Schritts (gegen Durchtunneln schneller Stücke). */
    private double margin(Piece a, double dt) {
        double v = Math.sqrt(a.v[0] * a.v[0] + a.v[1] * a.v[1] + a.v[2] * a.v[2]);
        double w = Math.sqrt(a.w[0] * a.w[0] + a.w[1] * a.w[1] + a.w[2] * a.w[2]) * a.radius;
        return MARGIN + (v + w) * dt;
    }

    private double mg, maxSpeed;
    private double[] sx0 = new double[0], sx1 = new double[0], sy = new double[0], sz = new double[0], srad = new double[0];
    private boolean[] ssl = new boolean[0];

    private void groundContacts(Piece a, int i) {
        double cx = a.wcen[i * 3], cy = a.wcen[i * 3 + 1], cz = a.wcen[i * 3 + 2], r = a.crad[i];
        double g = world.ground.heightAt(cx, cz);
        if (Double.isNaN(g) || cy - r > g + r + mg) return;
        double[] w = a.wpts[i];
        found.clear();
        for (int k = 0; k < w.length; k += 3) {
            double x = w[k], y = w[k + 1], z = w[k + 2];
            double gy = world.ground.heightAt(x, z);
            if (Double.isNaN(gy) || y - gy > mg) continue;
            double h = 0.25;
            double gx = (world.ground.heightAt(x + h, z) - world.ground.heightAt(x - h, z)) / (2 * h);
            double gz = (world.ground.heightAt(x, z + h) - world.ground.heightAt(x, z - h)) / (2 * h);
            if (Double.isNaN(gx)) gx = 0;
            if (Double.isNaN(gz)) gz = 0;
            double l = Math.sqrt(gx * gx + 1 + gz * gz);
            Contact c = obtain();
            c.a = a; c.cellA = i;
            c.nx = -gx / l; c.ny = 1 / l; c.nz = -gz / l;
            c.depth = (gy - y) * c.ny;
            c.px = x; c.py = y; c.pz = z;
            c.mu = a.material.friction;
            c.key = key(a.id, i, -1, 0, k / 3, 0);
            found.add(c);
        }
        keep(found);
    }

    private void staticContacts(Piece a, int i) {
        double cx = a.wcen[i * 3], cy = a.wcen[i * 3 + 1], cz = a.wcen[i * 3 + 2], r = a.crad[i] + mg;
        stampNo++;
        int x0 = cell(cx - r), x1 = cell(cx + r), y0 = cell(cy - r), y1 = cell(cy + r), z0 = cell(cz - r), z1 = cell(cz + r);
        int[] face = new int[1];
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    int[] l = sGrid.get(key(x, y, z));
                    if (l == null) continue;
                    for (int q = 1; q <= l[0]; q++) {
                        int s = l[q];
                        if (sStamp[s] == stampNo) continue;
                        sStamp[s] = stampNo;
                        double dx = sCen[s * 3] - cx, dy = sCen[s * 3 + 1] - cy, dz = sCen[s * 3 + 2] - cz, rr = sRad[s] + r;
                        if (dx * dx + dy * dy + dz * dz > rr * rr) continue;
                        Poly sp = sPoly.get(s);
                        double mu = Math.sqrt(a.material.friction * sMat.get(s).friction);
                        found.clear();
                        // Ecken des Stücks im Körper
                        double[] w = a.wpts[i];
                        for (int k = 0; k < w.length; k += 3) {
                            double d = sp.depth(w[k], w[k + 1], w[k + 2], face);
                            if (d < -mg) continue;
                            Poly.Face f = sp.faces.get(face[0]);
                            Contact c = obtain();
                            c.a = a; c.cellA = i;
                            c.nx = f.nx; c.ny = f.ny; c.nz = f.nz;
                            c.depth = d;
                            c.px = w[k]; c.py = w[k + 1]; c.pz = w[k + 2];
                            c.mu = mu;
                            c.key = key(a.id, i, -2, s, k / 3, 0);
                            c.stat = s;
                            found.add(c);
                        }
                        // Ecken des Körpers im Stück
                        double[] sv = sPts.get(s);
                        Poly ap = a.cells[i];
                        for (int k = 0; k < sv.length; k += 3) {
                            double ox = sv[k] - a.p[0], oy = sv[k + 1] - a.p[1], oz = sv[k + 2] - a.p[2];
                            double lx = a.R[0] * ox + a.R[3] * oy + a.R[6] * oz, ly = a.R[1] * ox + a.R[4] * oy + a.R[7] * oz, lz = a.R[2] * ox + a.R[5] * oy + a.R[8] * oz;
                            double d = ap.depth(lx, ly, lz, face);
                            if (d < -mg) continue;
                            Poly.Face f = ap.faces.get(face[0]);
                            Contact c = obtain();
                            c.a = a; c.cellA = i;
                            c.nx = -(a.R[0] * f.nx + a.R[1] * f.ny + a.R[2] * f.nz);
                            c.ny = -(a.R[3] * f.nx + a.R[4] * f.ny + a.R[5] * f.nz);
                            c.nz = -(a.R[6] * f.nx + a.R[7] * f.ny + a.R[8] * f.nz);
                            c.depth = d;
                            c.px = sv[k]; c.py = sv[k + 1]; c.pz = sv[k + 2];
                            c.mu = mu;
                            c.key = key(a.id, i, -2, s, k / 3, 1);
                            c.stat = s;
                            found.add(c);
                        }
                        keep(found);
                    }
                }
    }

    private void pieceContacts(Piece a, Piece b) {
        int[] face = new int[1];
        double mu = Math.sqrt(a.material.friction * b.material.friction);
        double bx = b.p[0], by = b.p[1], bz = b.p[2];
        for (int i = 0; i < a.cells.length; i++) {
            if (!a.surf[i]) continue;
            double ax = a.wcen[i * 3], ay = a.wcen[i * 3 + 1], az = a.wcen[i * 3 + 2];
            // nur Zellen, die an das andere Stück heranreichen
            double ex = ax - bx, ey = ay - by, ez = az - bz, er = b.radius + a.crad[i] + mg;
            if (ex * ex + ey * ey + ez * ez > er * er) continue;
            for (int j = 0; j < b.cells.length; j++) {
                if (!b.surf[j]) continue;
                double dx = b.wcen[j * 3] - ax, dy = b.wcen[j * 3 + 1] - ay, dz = b.wcen[j * 3 + 2] - az, rr = a.crad[i] + b.crad[j] + mg;
                if (dx * dx + dy * dy + dz * dz > rr * rr) continue;
                found.clear();
                pointsInto(a, i, b, j, false, mu, face, found);
                pointsInto(b, j, a, i, true, mu, face, found);
                keep(found);
            }
        }
    }

    /** Punkte der Zelle (p, i) in der Zelle (o, j); flip: die Berührung gehört o (Normale umgekehrt). */
    private void pointsInto(Piece p, int i, Piece o, int j, boolean flip, double mu, int[] face, List<Contact> found) {
        double[] w = p.wpts[i];
        Poly op = o.cells[j];
        double cx = o.wcen[j * 3], cy = o.wcen[j * 3 + 1], cz = o.wcen[j * 3 + 2], rr = o.crad[j] + mg;
        for (int k = 0; k < w.length; k += 3) {
            double ex = w[k] - cx, ey = w[k + 1] - cy, ez = w[k + 2] - cz;
            if (ex * ex + ey * ey + ez * ez > rr * rr) continue;
            double ox = w[k] - o.p[0], oy = w[k + 1] - o.p[1], oz = w[k + 2] - o.p[2];
            double lx = o.R[0] * ox + o.R[3] * oy + o.R[6] * oz, ly = o.R[1] * ox + o.R[4] * oy + o.R[7] * oz, lz = o.R[2] * ox + o.R[5] * oy + o.R[8] * oz;
            double d = op.depth(lx, ly, lz, face);
            if (d < -mg) continue;
            Poly.Face f = op.faces.get(face[0]);
            double nx = o.R[0] * f.nx + o.R[1] * f.ny + o.R[2] * f.nz, ny = o.R[3] * f.nx + o.R[4] * f.ny + o.R[5] * f.nz, nz = o.R[6] * f.nx + o.R[7] * f.ny + o.R[8] * f.nz;
            Contact c = obtain();
            // die Normale zeigt von b nach a: Punkte von a in b → Außennormale von b; Punkte von b in a → −Außennormale von a
            if (!flip) { c.a = p; c.b = o; c.cellA = i; c.nx = nx; c.ny = ny; c.nz = nz; }
            else { c.a = o; c.b = p; c.cellA = j; c.nx = -nx; c.ny = -ny; c.nz = -nz; }
            if (c.a.asleep) {
                // die Berührung gehört immer dem wachen Stück
                Piece t = c.a; c.a = c.b; c.b = t;
                c.cellA = flip ? i : j;
                c.nx = -c.nx; c.ny = -c.ny; c.nz = -c.nz;
            }
            c.bDyn = c.b != null && !c.b.asleep;
            c.depth = d;
            c.px = w[k]; c.py = w[k + 1]; c.pz = w[k + 2];
            c.mu = mu;
            c.key = key(c.a.id, c.cellA, c.b.id, flip ? i : j, k / 3, flip ? 3 : 2);
            found.add(c);
        }
    }

    /** Die tiefsten {@link #PER_PAIR} Berührungen eines Zellpaars behalten. */
    private void keep(List<Contact> found) {
        int n = found.size();
        if (n == 0) return;
        // die Berührungen in found sind die letzten n aus dem Vorrat (obtain); verworfene gehen zurück
        int start = used - n;
        if (list.size() >= max) { used = start; capped++; return; }
        if (n > PER_PAIR) {
            found.sort((x, y) -> Double.compare(y.depth, x.depth));
            for (int q = 0; q < n; q++) pool.set(start + q, found.get(q));
            used = start + PER_PAIR;
            list.addAll(found.subList(0, PER_PAIR));
        } else list.addAll(found);
    }

    /** Größte Zahl Berührungen in einem Schritt, Zellpaare, die wegen {@link #max} wegfielen, Größe des Vorrats. */
    int peak() { return peak; }
    long capped() { return capped; }
    int poolSize() { return pool.size(); }

    private static long key(int a, int ca, int b, int cb, int pt, int dir) {
        long h = a * 0x9E3779B97F4A7C15L;
        h = (h ^ ca) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (b + 7)) * 0x94D049BB133111EBL;
        h = (h ^ cb) * 0x9E3779B97F4A7C15L;
        h = (h ^ pt) * 0xBF58476D1CE4E5B9L;
        return h ^ dir;
    }

    private static double effMass(Contact c, double nx, double ny, double nz, double[] tmp, double[] cr) {
        Piece a = c.a, b = c.b;
        double k = a.invMass;
        cross(c.ra, nx, ny, nz, cr);
        a.invInertiaWorld(cr, tmp);
        k += nx * (tmp[1] * c.ra[2] - tmp[2] * c.ra[1]) + ny * (tmp[2] * c.ra[0] - tmp[0] * c.ra[2]) + nz * (tmp[0] * c.ra[1] - tmp[1] * c.ra[0]);
        if (c.bDyn) {
            k += b.invMass;
            cross(c.rb, nx, ny, nz, cr);
            b.invInertiaWorld(cr, tmp);
            k += nx * (tmp[1] * c.rb[2] - tmp[2] * c.rb[1]) + ny * (tmp[2] * c.rb[0] - tmp[0] * c.rb[2]) + nz * (tmp[0] * c.rb[1] - tmp[1] * c.rb[0]);
        }
        return k;
    }

    private static void cross(double[] r, double x, double y, double z, double[] out) {
        out[0] = r[1] * z - r[2] * y;
        out[1] = r[2] * x - r[0] * z;
        out[2] = r[0] * y - r[1] * x;
    }

    private double[] relVel(Contact c) {
        Piece a = c.a;
        double vx = a.v[0] + a.w[1] * c.ra[2] - a.w[2] * c.ra[1];
        double vy = a.v[1] + a.w[2] * c.ra[0] - a.w[0] * c.ra[2];
        double vz = a.v[2] + a.w[0] * c.ra[1] - a.w[1] * c.ra[0];
        if (c.bDyn) {
            Piece b = c.b;
            vx -= b.v[0] + b.w[1] * c.rb[2] - b.w[2] * c.rb[1];
            vy -= b.v[1] + b.w[2] * c.rb[0] - b.w[0] * c.rb[2];
            vz -= b.v[2] + b.w[0] * c.rb[1] - b.w[1] * c.rb[0];
        }
        rv[0] = vx; rv[1] = vy; rv[2] = vz;
        return rv;
    }

    /** Stoß J (Welt) auf a im Punkt, −J auf b. */
    private static void apply(Contact c, double jx, double jy, double jz, double[] tmp) {
        Piece a = c.a;
        a.v[0] += jx * a.invMass; a.v[1] += jy * a.invMass; a.v[2] += jz * a.invMass;
        double[] t = c.ts;
        t[0] = c.ra[1] * jz - c.ra[2] * jy; t[1] = c.ra[2] * jx - c.ra[0] * jz; t[2] = c.ra[0] * jy - c.ra[1] * jx;
        a.invInertiaWorld(t, tmp);
        a.w[0] += tmp[0]; a.w[1] += tmp[1]; a.w[2] += tmp[2];
        if (c.bDyn) {
            Piece b = c.b;
            b.v[0] -= jx * b.invMass; b.v[1] -= jy * b.invMass; b.v[2] -= jz * b.invMass;
            double[] s = c.ts;
            s[0] = c.rb[1] * jz - c.rb[2] * jy; s[1] = c.rb[2] * jx - c.rb[0] * jz; s[2] = c.rb[0] * jy - c.rb[1] * jx;
            b.invInertiaWorld(s, tmp);
            b.w[0] -= tmp[0]; b.w[1] -= tmp[1]; b.w[2] -= tmp[2];
        }
    }
}
