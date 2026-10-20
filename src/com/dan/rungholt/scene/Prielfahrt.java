package com.dan.rungholt.scene;

import com.dan.tide.Flood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Phase 15: der Weg der Fahrt „Prielfahrt“ (F20 A): mit dem Boot bei Flut einen Priel hinauf. Der Weg folgt der Rinne
 * im Gitter der Überflutung (BAW-Höhen, 16 m): der kürzeste Weg nur über Zellen tiefer als {@link #RINNE}, je tiefer,
 * desto billiger, damit das Boot in der Mitte der Rinne bleibt; vom tiefsten Punkt nahe dem Anfang zum tiefsten nahe dem
 * Ende, geglättet, alle 8 m ein Punkt.
 * <p>
 * Gestaltet [G]: Anfang und Ende, 12 m/s (gut 23 Knoten), das Auge 1,6 m über dem Wasser, wie es beim Start steht.
 */
public final class Prielfahrt {
    /** Tiefer als das ist Rinne (m NHN). */
    public static final double RINNE = -0.8;
    /**
     * Anfang und Ende [G]: der Priel südlich der hohen Watten, der bei der Ansicht 4 „Am Priel“ vorbeiläuft, von seiner
     * Mündung in die Norderhever hinauf nach Nordosten zum Deich von Nordstrand. Geschwindigkeit (m/s), Auge über dem
     * Wasser (m).
     */
    public static final double START_X = 2700, START_Z = 1950, END_X = 4350, END_Z = 980, TEMPO = 12, AUGE = 1.6;

    private Prielfahrt() { }

    /** Der Weg von (sx, sz) nach (ex, ez) als Punkte {x, z}, oder null, wenn keine Rinne beide verbindet. */
    public static double[][] weg(Flood f, double sx, double sz, double ex, double ez) {
        int nx = f.nx, nz = f.nz;
        int best = deepest(f, sx, sz), goal = deepest(f, ex, ez);
        if (best < 0 || goal < 0) return null;
        double c = f.cell;
        // Dijkstra über die Rinne: Kosten = Strecke · (1 + 1,5 · Höhe über −3 m), damit das Boot in der Mitte bleibt
        double[] dist = new double[nx * nz];
        int[] from = new int[nx * nz];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(from, -1);
        dist[best] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        q.add(new double[]{0, best});
        int[] di = {1, -1, 0, 0, 1, 1, -1, -1}, dj = {0, 0, 1, -1, 1, -1, 1, -1};
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int k = (int) e[1];
            if (e[0] > dist[k]) continue;
            if (k == goal) break;
            int i = k % nx, j = k / nx;
            for (int d = 0; d < 8; d++) {
                int ii = i + di[d], jj = j + dj[d];
                if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                int kk = jj * nx + ii;
                if (!(f.ground[kk] < RINNE)) continue;
                double step = c * (d < 4 ? 1 : Math.sqrt(2));
                double nd = dist[k] + step * (1 + 1.5 * Math.max(0, f.ground[kk] + 3));
                if (nd < dist[kk]) { dist[kk] = nd; from[kk] = k; q.add(new double[]{nd, kk}); }
            }
        }
        if (Double.isInfinite(dist[goal])) return null;
        int end = goal;
        List<double[]> raw = new ArrayList<>();
        for (int k = end; k >= 0; k = from[k]) raw.add(0, new double[]{f.x0 + (k % nx + 0.5) * c, f.z0 + (k / nx + 0.5) * c});
        // glätten (drei Durchgänge, Fenster 7), Enden fest
        double[][] p = raw.toArray(new double[0][]);
        for (int pass = 0; pass < 3; pass++) {
            double[][] o = new double[p.length][];
            for (int i = 0; i < p.length; i++) {
                double x = 0, z = 0;
                int n = 0;
                for (int k = Math.max(0, i - 3); k <= Math.min(p.length - 1, i + 3); k++) { x += p[k][0]; z += p[k][1]; n++; }
                o[i] = i == 0 || i == p.length - 1 ? p[i] : new double[]{x / n, z / n};
            }
            p = o;
        }
        // alle 8 m ein Punkt
        List<double[]> out = new ArrayList<>();
        out.add(p[0]);
        double carry = 0;
        for (int i = 1; i < p.length; i++) {
            double l = Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            double s = 8 - carry;
            while (s <= l) {
                double t = s / l;
                out.add(new double[]{p[i - 1][0] + (p[i][0] - p[i - 1][0]) * t, p[i - 1][1] + (p[i][1] - p[i - 1][1]) * t});
                s += 8;
            }
            carry = l - (s - 8);
        }
        out.add(p[p.length - 1]);
        return out.toArray(new double[0][]);
    }

    /** Die tiefste Zelle der Rinne in 200 m um (x, z), oder −1. */
    static int deepest(Flood f, double x, double z) {
        int best = -1, si = (int) Math.floor((x - f.x0) / f.cell), sj = (int) Math.floor((z - f.z0) / f.cell), r = (int) Math.ceil(200 / f.cell);
        for (int j = Math.max(0, sj - r); j <= Math.min(f.nz - 1, sj + r); j++)
            for (int i = Math.max(0, si - r); i <= Math.min(f.nx - 1, si + r); i++) {
                int k = j * f.nx + i;
                if (f.ground[k] < RINNE && (best < 0 || f.ground[k] < f.ground[best])) best = k;
            }
        return best;
    }

    /** Länge eines Wegs {x, z} (m). */
    public static double laenge(double[][] w) {
        double l = 0;
        for (int i = 1; i < w.length; i++) l += Math.hypot(w[i][0] - w[i - 1][0], w[i][1] - w[i - 1][1]);
        return l;
    }
}
