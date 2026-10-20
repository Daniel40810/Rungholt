package com.dan.fracture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Zerlegt einen {@link Shape} in Zellen (3D-Voronoi): Saaten nach einer Dichte verteilt (dichter, wo
 * {@link Fracture.Density} es will, mit Mindestabstand gegen Splitter), je Saat ein Quader, zugeschnitten auf den Umriss
 * und dann auf die Mittelsenkrechte jeder näheren Saat, bis keine Saat mehr nah genug ist (doppelter Radius der Zelle).
 * Gleicher Seed, gleiche Zellen.
 */
final class Voronoi {
    /** Ergebnis: Saaten, Zellen und Nachbarflächen (i &lt; j). */
    static final class Result {
        double[] seeds;
        Poly[] cells;
        int[] bi, bj;
        double spacing;
    }

    private Voronoi() { }

    static Result build(Shape shape, int count, Fracture.Density density, long seed) {
        double[] b = shape.bounds();
        SplittableRandom rnd = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
        // Volumen und mittlere Dichte durch Stichproben
        int in = 0, samples = 4000;
        double rhoSum = 0, rhoMax = 1e-6;
        for (int k = 0; k < samples; k++) {
            double x = b[0] + (b[3] - b[0]) * rnd.nextDouble(), y = b[1] + (b[4] - b[1]) * rnd.nextDouble(), z = b[2] + (b[5] - b[2]) * rnd.nextDouble();
            if (!shape.inside(x, y, z)) continue;
            in++;
            double r = rho(density, x, y, z);
            rhoSum += r;
            rhoMax = Math.max(rhoMax, r);
        }
        double vol = (b[3] - b[0]) * (b[4] - b[1]) * (b[5] - b[2]) * Math.max(1, in) / samples;
        double rhoMean = in > 0 ? rhoSum / in : 1;
        double s0 = Math.cbrt(vol / Math.max(1, count));
        // Saaten: Ausdünnen nach der Dichte, Mindestabstand nach der örtlichen Weite
        double minCell = 0.5 * s0 * Math.cbrt(rhoMean / rhoMax);
        Grid g = new Grid(b, Math.max(minCell, 1e-6));
        List<double[]> seeds = new ArrayList<>();
        int tries = 0, maxTries = 80 * count + 1000;
        while (seeds.size() < count && tries++ < maxTries) {
            double x = b[0] + (b[3] - b[0]) * rnd.nextDouble(), y = b[1] + (b[4] - b[1]) * rnd.nextDouble(), z = b[2] + (b[5] - b[2]) * rnd.nextDouble();
            if (!shape.inside(x, y, z)) continue;
            double r = rho(density, x, y, z);
            if (rnd.nextDouble() * rhoMax > r) continue;
            double minDist = 0.55 * s0 * Math.cbrt(rhoMean / r);
            if (g.near(seeds, x, y, z, minDist)) continue;
            g.add(seeds.size(), x, y, z);
            seeds.add(new double[]{x, y, z});
        }
        int n = seeds.size();
        double[] sx = new double[n * 3];
        for (int i = 0; i < n; i++) System.arraycopy(seeds.get(i), 0, sx, i * 3, 3);
        // Zellen
        Grid ng = new Grid(b, s0);
        for (int i = 0; i < n; i++) ng.add(i, sx[i * 3], sx[i * 3 + 1], sx[i * 3 + 2]);
        double pad = s0 * 0.01;
        Poly[] cells = new Poly[n];
        for (int i = 0; i < n; i++) {
            double x = sx[i * 3], y = sx[i * 3 + 1], z = sx[i * 3 + 2];
            Poly c = Poly.box(b[0] - pad, b[1] - pad, b[2] - pad, b[3] + pad, b[4] + pad, b[5] + pad, -1);
            shape.clip(c, x, y, z);
            int gi = ng.ix(x), gj = ng.iy(y), gk = ng.iz(z);
            int maxRing = Math.max(ng.nx, Math.max(ng.ny, ng.nz));
            for (int ring = 0; ring <= maxRing && !c.isEmpty(); ring++) {
                List<int[]> cand = new ArrayList<>();
                for (int a = gi - ring; a <= gi + ring; a++)
                    for (int bb = gj - ring; bb <= gj + ring; bb++)
                        for (int cc = gk - ring; cc <= gk + ring; cc++) {
                            if (Math.max(Math.abs(a - gi), Math.max(Math.abs(bb - gj), Math.abs(cc - gk))) != ring) continue;
                            int[] list = ng.get(a, bb, cc);
                            if (list == null) continue;
                            for (int q = 1; q <= list[0]; q++) if (list[q] != i) cand.add(new int[]{list[q]});
                        }
                final int ii = i;
                cand.sort((p, q) -> Double.compare(d2(sx, ii, p[0]), d2(sx, ii, q[0])));
                for (int[] cj : cand) {
                    int j = cj[0];
                    double nx = sx[j * 3] - x, ny = sx[j * 3 + 1] - y, nz = sx[j * 3 + 2] - z;
                    double mx = (sx[j * 3] + x) / 2, my = (sx[j * 3 + 1] + y) / 2, mz = (sx[j * 3 + 2] + z) / 2;
                    c.clip(nx, ny, nz, nx * mx + ny * my + nz * mz, j);
                    if (c.isEmpty()) break;
                }
                double maxR = 0;
                for (Poly.Face f : c.faces)
                    for (int k = 0; k < f.p.length; k += 3) {
                        double dx = f.p[k] - x, dy = f.p[k + 1] - y, dz = f.p[k + 2] - z;
                        maxR = Math.max(maxR, dx * dx + dy * dy + dz * dz);
                    }
                maxR = Math.sqrt(maxR);
                // jede Saat im nächsten Ring liegt mindestens ring · Weite entfernt
                if (ring * ng.cell >= 2 * maxR) break;
            }
            cells[i] = c;
        }
        // leere Zellen und solche außerhalb des Umrisses fallen weg
        int[] map = new int[n];
        Arrays.fill(map, -1);
        int m = 0;
        double minVol = 1e-6 * s0 * s0 * s0;
        double[][] mass = new double[n][];
        for (int i = 0; i < n; i++) {
            if (cells[i].isEmpty()) continue;
            mass[i] = cells[i].mass();
            if (mass[i][0] < minVol) continue;
            if (!shape.inside(mass[i][1], mass[i][2], mass[i][3]) && !shape.inside(sx[i * 3], sx[i * 3 + 1], sx[i * 3 + 2])) continue;
            map[i] = m++;
        }
        Result r = new Result();
        r.spacing = s0;
        r.seeds = new double[m * 3];
        r.cells = new Poly[m];
        for (int i = 0; i < n; i++) {
            if (map[i] < 0) continue;
            System.arraycopy(sx, i * 3, r.seeds, map[i] * 3, 3);
            Poly c = cells[i];
            for (Poly.Face f : c.faces) f.tag = f.tag >= 0 ? map[f.tag] : -1;
            r.cells[map[i]] = c;
        }
        // Nachbarflächen: beide Seiten müssen einander nennen
        List<int[]> bonds = new ArrayList<>();
        for (int i = 0; i < m; i++)
            for (Poly.Face f : r.cells[i].faces) {
                int j = f.tag;
                if (j <= i) continue;
                boolean back = false;
                for (Poly.Face g2 : r.cells[j].faces) if (g2.tag == i) { back = true; break; }
                if (back) bonds.add(new int[]{i, j});
                else f.tag = -1;
            }
        // einseitige Marken der Gegenseite löschen
        for (int j = 0; j < m; j++)
            for (Poly.Face f : r.cells[j].faces) {
                int i = f.tag;
                if (i < 0 || i >= j) continue;
                boolean ok = false;
                for (Poly.Face g2 : r.cells[i].faces) if (g2.tag == j) { ok = true; break; }
                if (!ok) f.tag = -1;
            }
        r.bi = new int[bonds.size()];
        r.bj = new int[bonds.size()];
        for (int k = 0; k < bonds.size(); k++) { r.bi[k] = bonds.get(k)[0]; r.bj[k] = bonds.get(k)[1]; }
        return r;
    }

    private static double rho(Fracture.Density d, double x, double y, double z) {
        return d == null ? 1 : Math.max(1e-3, Math.min(1, d.at(x, y, z)));
    }

    private static double d2(double[] s, int i, int j) {
        double dx = s[i * 3] - s[j * 3], dy = s[i * 3 + 1] - s[j * 3 + 1], dz = s[i * 3 + 2] - s[j * 3 + 2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** Ein einfaches Raster für Nachbarn. */
    static final class Grid {
        final double x0, y0, z0, cell;
        final int nx, ny, nz;
        final int[][] slots;

        Grid(double[] b, double cell) {
            this.cell = cell;
            x0 = b[0]; y0 = b[1]; z0 = b[2];
            nx = Math.max(1, (int) Math.ceil((b[3] - b[0]) / cell));
            ny = Math.max(1, (int) Math.ceil((b[4] - b[1]) / cell));
            nz = Math.max(1, (int) Math.ceil((b[5] - b[2]) / cell));
            long total = (long) nx * ny * nz;
            if (total > 50_000_000L) throw new IllegalArgumentException("fracture: Raster zu fein (" + total + " Felder)");
            slots = new int[(int) total][];
        }

        int ix(double x) { return Math.max(0, Math.min(nx - 1, (int) ((x - x0) / cell))); }
        int iy(double y) { return Math.max(0, Math.min(ny - 1, (int) ((y - y0) / cell))); }
        int iz(double z) { return Math.max(0, Math.min(nz - 1, (int) ((z - z0) / cell))); }

        int[] get(int i, int j, int k) {
            if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return null;
            return slots[(k * ny + j) * nx + i];
        }

        void add(int id, double x, double y, double z) {
            int s = (iz(z) * ny + iy(y)) * nx + ix(x);
            int[] l = slots[s];
            if (l == null) l = slots[s] = new int[5];
            if (l[0] + 1 >= l.length) l = slots[s] = Arrays.copyOf(l, l.length * 2);
            l[++l[0]] = id;
        }

        boolean near(List<double[]> pts, double x, double y, double z, double r) {
            int rr = (int) Math.ceil(r / cell);
            int i0 = ix(x), j0 = iy(y), k0 = iz(z);
            for (int i = i0 - rr; i <= i0 + rr; i++)
                for (int j = j0 - rr; j <= j0 + rr; j++)
                    for (int k = k0 - rr; k <= k0 + rr; k++) {
                        int[] l = get(i, j, k);
                        if (l == null) continue;
                        for (int q = 1; q <= l[0]; q++) {
                            double[] p = pts.get(l[q]);
                            double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
                            if (dx * dx + dy * dy + dz * dz < r * r) return true;
                        }
                    }
            return false;
        }
    }
}
