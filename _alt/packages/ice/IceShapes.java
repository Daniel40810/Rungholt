package com.dan.ice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formen aus Eis ohne Modellierwerkzeug: ein Block mit gebrochenen Kanten (für die Demo) und Eisberge aus einer
 * Kugel, die von Bruchflächen beschnitten und leicht verbeult wird – so sehen frisch gekalbte Stücke aus: ebene
 * Bruchflächen, an denen Zellen ausgebrochen sind, dazwischen gerundete, angeschmolzene Teile [G]. Gleicher seed,
 * gleicher Eisberg.
 */
public final class IceShapes {
    private IceShapes() { }

    /** Würfel der Kante {@code size} (Mitte im Ursprung) mit gebrochenen Kanten der Breite {@code bevel}. */
    public static IceMesh block(double size, double bevel) {
        double h = size / 2, b = Math.max(0.001, Math.min(bevel, h * 0.9)), i = h - b;
        IceMesh.Builder m = new IceMesh.Builder(0, 0, 0);
        int[] s = {-1, 1};
        // sechs Hauptflächen
        for (int ax = 0; ax < 3; ax++)
            for (int sg : s) {
                double[] p = new double[12];
                int k = 0;
                double[][] uv = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (double[] q : uv) {
                    double[] v = new double[3];
                    v[ax] = sg * h;
                    v[(ax + 1) % 3] = q[0] * i;
                    v[(ax + 2) % 3] = q[1] * i;
                    p[k++] = v[0]; p[k++] = v[1]; p[k++] = v[2];
                }
                m.poly(p);
            }
        // zwölf Kantenfasen
        for (int ax = 0; ax < 3; ax++) {
            int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
            for (int s1 : s)
                for (int s2 : s) {
                    double[] p = new double[12];
                    int k = 0;
                    for (int[] e : new int[][]{{-1, 1}, {1, 1}, {1, 2}, {-1, 2}}) {
                        double[] v = new double[3];
                        v[ax] = e[0] * i;
                        if (e[1] == 1) { v[a1] = s1 * h; v[a2] = s2 * i; } else { v[a1] = s1 * i; v[a2] = s2 * h; }
                        p[k++] = v[0]; p[k++] = v[1]; p[k++] = v[2];
                    }
                    m.poly(p);
                }
        }
        // acht Eckdreiecke
        for (int sx : s)
            for (int sy : s)
                for (int sz : s)
                    m.poly(sx * h, sy * i, sz * i, sx * i, sy * h, sz * i, sx * i, sy * i, sz * h);
        return m.build();
    }

    /**
     * Ein Eisberg mit dem Halbmesser {@code radius} (m): eine Ikosaederkugel (Stufe {@code detail}), beschnitten von
     * 7 bis 11 Bruchebenen und verbeult mit Rauschen. {@code flat} streckt (über 1) oder staucht (unter 1) die
     * Höhe: Tafeleisberge sind flach, gekippte Türme hoch.
     */
    public static IceMesh iceberg(long seed, double radius, double flat, int detail) {
        List<double[]> v = new ArrayList<>();
        List<int[]> f = new ArrayList<>();
        icosphere(Math.max(1, Math.min(4, detail)), v, f);
        java.util.Random rnd = new java.util.Random(seed);
        int planes = 7 + rnd.nextInt(5);
        double[][] pl = new double[planes][4];
        for (int k = 0; k < planes; k++) {
            double[] n = randomDir(rnd);
            pl[k] = new double[]{n[0], n[1], n[2], 0.62 + 0.3 * rnd.nextDouble()};
        }
        double ph = rnd.nextDouble() * 100;
        double[][] p = new double[v.size()][];
        for (int i = 0; i < v.size(); i++) {
            double[] u = v.get(i);
            double r = 1 + 0.18 * (noise(u[0] * 1.7 + ph, u[1] * 1.7, u[2] * 1.7) - 0.5) + 0.07 * (noise(u[0] * 5 + ph, u[1] * 5, u[2] * 5) - 0.5);
            for (double[] q : pl) {
                double c = u[0] * q[0] + u[1] * q[1] + u[2] * q[2];
                if (c > 1e-3) r = Math.min(r, q[3] / c);
            }
            p[i] = new double[]{u[0] * r * radius, u[1] * r * radius * flat, u[2] * r * radius};
        }
        IceMesh.Builder b = new IceMesh.Builder(0, 0, 0);
        for (int[] t : f) b.tri(p[t[0]][0], p[t[0]][1], p[t[0]][2], p[t[1]][0], p[t[1]][1], p[t[1]][2], p[t[2]][0], p[t[2]][1], p[t[2]][2]);
        return b.build();
    }

    /** Das Netz um dy in der Höhe verschieben (etwa auf die Wasserlinie). */
    public static IceMesh shifted(IceMesh m, double dx, double dy, double dz) {
        float[] p = m.pos.clone();
        for (int i = 0; i < p.length; i += 3) { p[i] += (float) dx; p[i + 1] += (float) dy; p[i + 2] += (float) dz; }
        return new IceMesh(p, m.nrm, m.idx, m.ao);
    }

    static double[] randomDir(java.util.Random r) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new double[]{s * Math.cos(a), z, s * Math.sin(a)};
    }

    /** Glattes Wertrauschen 0..1 im Raum. */
    static double noise(double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double r = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            r += w * IceNoise.rnd(ix + dx, iy + dy, iz + dz, 7, 3);
        }
        return r;
    }

    /** Ikosaeder, n-mal unterteilt, auf die Einheitskugel; Dreiecke gegen den Uhrzeigersinn von außen. */
    static void icosphere(int n, List<double[]> v, List<int[]> f) {
        double t = (1 + Math.sqrt(5)) / 2;
        double[][] base = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (double[] b : base) v.add(norm(b));
        int[][] fs = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        for (int[] x : fs) f.add(x);
        for (int s = 0; s < n; s++) {
            Map<Long, Integer> mid = new HashMap<>();
            List<int[]> nf = new ArrayList<>();
            for (int[] x : f) {
                int a = mid(x[0], x[1], v, mid), b = mid(x[1], x[2], v, mid), c = mid(x[2], x[0], v, mid);
                nf.add(new int[]{x[0], a, c});
                nf.add(new int[]{x[1], b, a});
                nf.add(new int[]{x[2], c, b});
                nf.add(new int[]{a, b, c});
            }
            f.clear();
            f.addAll(nf);
        }
    }

    private static int mid(int a, int b, List<double[]> v, Map<Long, Integer> m) {
        long k = Math.min(a, b) * 1_000_003L + Math.max(a, b);
        Integer i = m.get(k);
        if (i != null) return i;
        double[] p = v.get(a), q = v.get(b);
        v.add(norm(new double[]{(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2}));
        m.put(k, v.size() - 1);
        return v.size() - 1;
    }

    private static double[] norm(double[] p) {
        double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new double[]{p[0] / l, p[1] / l, p[2] / l};
    }
}
