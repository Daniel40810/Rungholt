package com.dan.rungholt.data;

import com.dan.tide.Flood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die nasse Fläche einer Überflutung als Mehrfachfläche (F29 A): das Gitter der Überflutung auf {@link #RASTER} m
 * vergröbert (eine Zelle ist nass, wenn mindestens die Hälfte ihrer feinen Zellen Wasser hat), dann die Ränder als
 * Ringe nachgezogen: außen gegen den Uhrzeigersinn, Löcher (trockene Inseln) mit ihm, nur an den Ecken ein Punkt. Zellen,
 * die sich nur an einer Ecke berühren, werden verbunden ({@link #ohneEcken}), damit jeder Ring einfach bleibt und Oracle
 * die Fläche als gültig nimmt. Flächen und Löcher unter {@link #MIN_ZELLEN} Zellen fallen weg
 * (Pfützen und einzelne Sandköpfe).
 */
public final class Wasserlinie {
    private Wasserlinie() { }

    /** Raster der Wasserlinie (m). */
    public static final double RASTER = 64;
    /** Kleinste Fläche und kleinstes Loch (Zellen des Rasters). */
    public static final int MIN_ZELLEN = 4;

    /** Das vergröberte Gitter: nass je Zelle, dazu Ursprung und Zellgröße. */
    public record Gitter(boolean[] nass, int nx, int nz, double x0, double z0, double zelle) {
        public boolean at(int i, int j) { return i >= 0 && j >= 0 && i < nx && j < nz && nass[j * nx + i]; }
        public int zahl() { int n = 0; for (boolean b : nass) if (b) n++; return n; }
    }

    /** Aus der Überflutung: nass, wo das Wasser über {@link Flood#WET} steht (Mehrheit der feinen Zellen). */
    public static Gitter gitter(Flood f, double raster) {
        int k = Math.max(1, (int) Math.round(raster / f.cell));
        int nx = f.nx / k, nz = f.nz / k;
        boolean[] n = new boolean[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int w = 0;
                for (int b = 0; b < k; b++)
                    for (int a = 0; a < k; a++) {
                        int q = (j * k + b) * f.nx + i * k + a;
                        if (f.surface[q] - f.ground[q] > Flood.WET) w++;
                    }
                n[j * nx + i] = 2 * w >= k * k;
            }
        return new Gitter(n, nx, nz, f.x0, f.z0, f.cell * k);
    }

    /**
     * Zwei Zellen über Eck (nass–trocken–nass im Quadrat) gäben einen Ring, der sich an der Ecke selbst berührt, was
     * Oracle als ungültig meldet. Darum wird an jeder solchen Ecke eine der trockenen Zellen nass, bis keine mehr bleibt
     * (eine Zelle von 64 m mehr nasse Fläche je Ecke [G]).
     */
    static Gitter ohneEcken(Gitter g) {
        boolean[] n = g.nass().clone();
        int nx = g.nx(), nz = g.nz();
        for (int runde = 0; runde < 50; runde++) {
            int geaendert = 0;
            for (int j = 1; j < nz; j++)
                for (int i = 1; i < nx; i++) {
                    boolean a = n[(j - 1) * nx + i - 1], b = n[(j - 1) * nx + i], c = n[j * nx + i - 1], d = n[j * nx + i];
                    if (a && d && !b && !c) { n[(j - 1) * nx + i] = true; geaendert++; }
                    else if (b && c && !a && !d) { n[(j - 1) * nx + i - 1] = true; geaendert++; }
                }
            if (geaendert == 0) break;
        }
        return new Gitter(n, nx, nz, g.x0(), g.z0(), g.zelle());
    }

    /** Die nasse Fläche als Mehrfachfläche in Szenenmetern (oder null, wenn nichts nass ist). */
    public static Daten.Geom flaeche(Gitter g0) {
        Gitter g = ohneEcken(g0);
        List<int[][]> ringe = ringe(g);
        List<int[][]> aussen = new ArrayList<>(), loecher = new ArrayList<>();
        for (int[][] r : ringe) {
            long a = flaeche2(r);
            if (Math.abs(a) < 2L * MIN_ZELLEN) continue;
            if (a > 0) aussen.add(r); else loecher.add(r);
        }
        if (aussen.isEmpty()) return null;
        // jedes Loch zur kleinsten Außenfläche, die es enthält (Prüfpunkt: die Mitte einer trockenen Zelle am Rand)
        List<List<int[][]>> teile = new ArrayList<>();
        for (int[][] a : aussen) { List<int[][]> t = new ArrayList<>(); t.add(a); teile.add(t); }
        for (int[][] l : loecher) {
            double[] p = innen(l);
            int best = -1;
            long bestA = Long.MAX_VALUE;
            for (int i = 0; i < aussen.size(); i++) {
                long a = flaeche2(aussen.get(i));
                if (a < bestA && enthaelt(aussen.get(i), p[0], p[1])) { best = i; bestA = a; }
            }
            if (best >= 0) teile.get(best).add(l);
        }
        List<List<double[][]>> out = new ArrayList<>();
        for (List<int[][]> t : teile) {
            List<double[][]> rs = new ArrayList<>();
            for (int[][] r : t) {
                double[][] p = new double[r.length][];
                for (int i = 0; i < r.length; i++) p[i] = new double[]{g.x0() + r[i][0] * g.zelle(), g.z0() + r[i][1] * g.zelle()};
                rs.add(p);
            }
            out.add(rs);
        }
        return Daten.Geom.multiflaeche(out);
    }

    /** Doppelte Fläche eines Rings in Gitterecken (positiv: außen, nach der Richtung des Nachziehens). */
    static long flaeche2(int[][] r) {
        long a = 0;
        for (int i = 0; i < r.length; i++) { int[] u = r[i], v = r[(i + 1) % r.length]; a += (long) u[0] * v[1] - (long) v[0] * u[1]; }
        return -a;   // z nach Süden: so sind die Außenringe positiv
    }

    /** Ein Punkt sicher im Inneren des Lochs: die Mitte der trockenen Zelle rechts der ersten Kante. */
    static double[] innen(int[][] r) {
        int[] a = r[0], b = r[1];
        double dx = Integer.signum(b[0] - a[0]), dz = Integer.signum(b[1] - a[1]);
        double mx = a[0] + dx * 0.5, mz = a[1] + dz * 0.5;
        // nass links der Kante: rechts (im Gitter mit z nach unten) ist (−dz, dx) … die trockene Seite
        return new double[]{mx - dz * 0.5, mz + dx * 0.5};
    }

    static boolean enthaelt(int[][] r, double x, double z) {
        boolean in = false;
        for (int i = 0, j = r.length - 1; i < r.length; j = i++) {
            double xi = r[i][0], zi = r[i][1], xj = r[j][0], zj = r[j][1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    /**
     * Alle Ringe als Gitterecken. Kanten mit dem Nassen links: oben von rechts nach links, links nach unten, unten nach
     * rechts, rechts nach oben (z nach unten). An einer Ecke mit zwei Wegen (zwei nasse Zellen über Eck) geht es links
     * herum: so bleibt jede Zelle bei ihrem Ring.
     */
    static List<int[][]> ringe(Gitter g) {
        int nx = g.nx(), nz = g.nz(), w = nx + 1;
        // je Ecke bis zu zwei ausgehende Kanten: Richtung 0 rechts, 1 unten, 2 links, 3 oben
        Map<Integer, int[]> aus = new HashMap<>();
        int kanten = 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.at(i, j)) continue;
                if (!g.at(i, j - 1)) { add(aus, (i + 1) + j * w, 2); kanten++; }
                if (!g.at(i - 1, j)) { add(aus, i + j * w, 1); kanten++; }
                if (!g.at(i, j + 1)) { add(aus, i + (j + 1) * w, 0); kanten++; }
                if (!g.at(i + 1, j)) { add(aus, (i + 1) + (j + 1) * w, 3); kanten++; }
            }
        List<int[][]> out = new ArrayList<>();
        int[] di = {1, 0, -1, 0}, dj = {0, 1, 0, -1};
        while (!aus.isEmpty()) {
            // Anfang an einer Ecke mit nur einem Weg (an einer Ecke mit zweien könnte ein Ring zweimal vorbeikommen)
            int start = -1;
            for (Map.Entry<Integer, int[]> e : aus.entrySet()) { start = e.getKey(); if (e.getValue()[1] < 0) break; }
            int v = start, dir = -1;
            List<int[]> pts = new ArrayList<>();
            int guard = 0;
            while (guard++ <= kanten + 4) {
                int[] d = aus.get(v);
                if (d == null) break;
                int nd;
                if (d[1] < 0 || dir < 0) nd = d[0];
                else {
                    // links herum: von „rechts“ (0) ist links „oben“ (3), von „unten“ (1) ist links „rechts“ (0)
                    int links = (dir + 3) % 4;
                    nd = d[0] == links || d[1] == links ? links : d[0];
                }
                // Kante nd aus der Ecke v entfernen
                if (d[0] == nd) { d[0] = d[1]; d[1] = -1; } else d[1] = -1;
                if (d[0] < 0) aus.remove(v);
                if (dir != nd) pts.add(new int[]{v % w, v / w});
                dir = nd;
                v = (v % w + di[nd]) + (v / w + dj[nd]) * w;
                if (v == start) break;
            }
            // die erste Ecke ist auch eine, wenn die letzte Richtung eine andere war
            if (pts.size() >= 2 && pts.get(0)[0] == start % w && pts.get(0)[1] == start / w) {
                int[] p1 = pts.get(1), p0 = pts.get(0), pl = pts.get(pts.size() - 1);
                boolean gerade = (p1[0] - p0[0]) * (p0[1] - pl[1]) - (p1[1] - p0[1]) * (p0[0] - pl[0]) == 0;
                if (gerade) pts.remove(0);
            }
            if (pts.size() >= 4) out.add(pts.toArray(new int[0][]));
        }
        return out;
    }

    private static void add(Map<Integer, int[]> m, int v, int dir) {
        int[] d = m.computeIfAbsent(v, k -> new int[]{-1, -1});
        if (d[0] < 0) d[0] = dir; else d[1] = dir;
    }
}
