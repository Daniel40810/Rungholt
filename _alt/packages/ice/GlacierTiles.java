package com.dan.ice;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Oberfläche eines {@link Glacier} als Kacheln von {@link #TILE} m mit fünf Stufen (2, 4, 8, 16, 32 m Punktabstand).
 * Welche Stufe eine Kachel bekommt, entscheidet der Abstand zum Auge ({@link #level}); Risse zwischen Kacheln
 * verschiedener Stufe deckt eine Schürze am Rand. Am Rand des Eises enden die Kacheln sauber:
 * <ul>
 * <li>zum Wasser hin rücken die Punkte auf die Frontlinie (dort setzt die Wand an, {@link GlacierWall});</li>
 * <li>zum Fels hin tauchen sie 3 m unter das Gelände (der Gletscher verschwindet unter der Seitenmoräne).</li>
 * </ul>
 * Reiner Prozessor, ohne Grafikkarte: {@link #build} liefert ein {@link IceMesh} mit Verdeckung je Ecke.
 */
public final class GlacierTiles {
    public static final double TILE = 256;
    public static final double[] SPACING = {2, 4, 8, 16, 32};
    /** Abstand (m), bis zu dem eine Stufe gilt, für Stufe 0 bis 3 (Stufe 4 dahinter); Stufe „Mittel“. */
    public static final double[] RANGE_MEDIUM = {420, 1100, 2400, 5000};

    public final Glacier glacier;
    /** Die Kacheln mit Eis (nach {@link #rescan} eine neue Liste; wer sie durchläuft, hält die alte). */
    public volatile List<Tile> tiles = new ArrayList<>();
    public final double x0, z0;
    public final int nx, nz;

    /** Eine Kachel: Lage, Höhenbereich (für das Ausblenden außerhalb des Blickfelds). */
    public static final class Tile {
        public final int ix, iz;
        public final double x0, z0;
        public double minY, maxY;
        Tile(int ix, int iz, double x0, double z0) { this.ix = ix; this.iz = iz; this.x0 = x0; this.z0 = z0; }
        public long key() { return ((long) ix << 32) ^ (iz & 0xFFFFFFFFL); }
    }

    public GlacierTiles(Glacier g) {
        glacier = g;
        double[] b = g.bounds();
        x0 = Math.floor(b[0] / TILE) * TILE;
        z0 = Math.floor(b[1] / TILE) * TILE;
        nx = (int) Math.ceil((b[2] - x0) / TILE);
        nz = (int) Math.ceil((b[3] - z0) / TILE);
        rescan();
    }

    /** Welche Kacheln Eis tragen (nach einem Vorstoß neu), mit ihrem Höhenbereich. */
    public synchronized void rescan() {
        List<Tile> list = new ArrayList<>();
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double tx = x0 + i * TILE, tz = z0 + j * TILE;
                boolean any = false;
                double lo = 1e9, hi = -1e9;
                for (int b = 0; b <= 16; b++)
                    for (int a = 0; a <= 16; a++) {
                        double x = tx + a * TILE / 16, z = tz + b * TILE / 16;
                        if (glacier.covers(x, z)) {
                            any = true;
                            double h = glacier.base(x, z);
                            lo = Math.min(lo, h);
                            hi = Math.max(hi, h);
                        }
                    }
                if (!any) continue;
                Tile t = new Tile(i, j, tx, tz);
                t.minY = lo - 45;
                t.maxY = hi + 8;
                list.add(t);
            }
        tiles = list;
    }

    /** Stufe für eine Kachel aus dem Abstand des Auges zur Kachel (nächster Punkt, mit Höhe). */
    public static int level(Tile t, double[] eye, double[] range) {
        double dx = Math.max(0, Math.max(t.x0 - eye[0], eye[0] - (t.x0 + TILE)));
        double dz = Math.max(0, Math.max(t.z0 - eye[2], eye[2] - (t.z0 + TILE)));
        double dy = Math.max(0, Math.max(t.minY - eye[1], eye[1] - t.maxY));
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (int l = 0; l < range.length; l++) if (d < range[l]) return l;
        return SPACING.length - 1;
    }

    /** Netz einer Kachel in der Stufe level (Punktabstand {@link #SPACING}[level]). */
    public IceMesh build(Tile t, int level) {
        double sp = SPACING[level];
        int n = (int) Math.round(TILE / sp) + 1;
        int nv = n * n;
        float[] pos = new float[(nv + 4 * n) * 3], ao = new float[nv + 4 * n];
        boolean[] in = new boolean[nv];
        double[] a = new double[1];
        Glacier g = glacier;
        double reach = 3 * sp + 4;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                double x = t.x0 + i * sp, z = t.z0 + j * sp, y;
                float o;
                if (g.covers(x, z)) {
                    y = g.surface(x, z, sp, a);
                    o = (float) a[0];
                    in[k] = true;
                } else {
                    double[] q = g.nearestFront(x, z, reach);
                    if (q != null && (x - q[0]) * q[2] + (z - q[1]) * q[3] > -0.5) {
                        // auf die Frontlinie, Höhe knapp innen (wie die Oberkante der Wand)
                        x = q[0];
                        z = q[1];
                        y = g.surface(x - q[2] * 0.5, z - q[3] * 0.5, sp, a);
                        o = (float) a[0];
                    } else {
                        y = Math.min(g.ground(x, z), g.base(x, z)) - 3;
                        o = 0.6f;
                    }
                }
                pos[k * 3] = (float) x;
                pos[k * 3 + 1] = (float) y;
                pos[k * 3 + 2] = (float) z;
                ao[k] = o;
            }
        List<int[]> tri = new ArrayList<>();
        boolean[] used = new boolean[nv];
        for (int j = 0; j < n - 1; j++)
            for (int i = 0; i < n - 1; i++) {
                int p = j * n + i, q = p + 1, r = p + n, s = r + 1;
                // Diagonale abwechselnd, damit keine Richtung bevorzugt ist
                boolean flip = ((i + j) & 1) == 0;
                int[][] two = flip ? new int[][]{{p, r, q}, {q, r, s}} : new int[][]{{p, r, s}, {p, s, q}};
                for (int[] tr : two)
                    if (in[tr[0]] || in[tr[1]] || in[tr[2]]) {
                        tri.add(tr);
                        used[tr[0]] = used[tr[1]] = used[tr[2]] = true;
                    }
            }
        // Normalen aus den Flächen
        float[] nrm = new float[pos.length];
        for (int[] tr : tri) {
            double ux = pos[tr[1] * 3] - pos[tr[0] * 3], uy = pos[tr[1] * 3 + 1] - pos[tr[0] * 3 + 1], uz = pos[tr[1] * 3 + 2] - pos[tr[0] * 3 + 2];
            double wx = pos[tr[2] * 3] - pos[tr[0] * 3], wy = pos[tr[2] * 3 + 1] - pos[tr[0] * 3 + 1], wz = pos[tr[2] * 3 + 2] - pos[tr[0] * 3 + 2];
            double cx = uy * wz - uz * wy, cy = uz * wx - ux * wz, cz = ux * wy - uy * wx;
            if (cy < 0) { cx = -cx; cy = -cy; cz = -cz; tr[0] ^= tr[2]; tr[2] ^= tr[0]; tr[0] ^= tr[2]; }
            for (int v : tr) { nrm[v * 3] += cx; nrm[v * 3 + 1] += cy; nrm[v * 3 + 2] += cz; }
        }
        for (int v = 0; v < nv; v++) {
            double l = Math.sqrt(nrm[v * 3] * nrm[v * 3] + nrm[v * 3 + 1] * nrm[v * 3 + 1] + nrm[v * 3 + 2] * nrm[v * 3 + 2]);
            if (l < 1e-12) { nrm[v * 3 + 1] = 1; continue; }
            nrm[v * 3] /= l; nrm[v * 3 + 1] /= l; nrm[v * 3 + 2] /= l;
        }
        // Normalen am Rand aus der Fläche selbst (über die Kachel hinaus), sonst sieht man die Kanten der Kacheln
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                if (i != 0 && j != 0 && i != n - 1 && j != n - 1) continue;
                int v = j * n + i;
                if (!in[v]) continue;
                double x = pos[v * 3], z = pos[v * 3 + 2];
                double hx = g.surface(x + sp, z, sp, null) - g.surface(x - sp, z, sp, null), hz = g.surface(x, z + sp, sp, null) - g.surface(x, z - sp, sp, null);
                double nx = -hx / (2 * sp), nz = -hz / (2 * sp), l = Math.sqrt(nx * nx + 1 + nz * nz);
                nrm[v * 3] = (float) (nx / l);
                nrm[v * 3 + 1] = (float) (1 / l);
                nrm[v * 3 + 2] = (float) (nz / l);
            }
        // Schürze an den vier Rändern: Kante nach unten verdoppelt
        double skirt = 6 + 2.5 * sp;
        int nextV = nv;
        int[][] edges = new int[4][n];
        for (int e = 0; e < 4; e++)
            for (int c = 0; c < n; c++) edges[e][c] = switch (e) { case 0 -> c; case 1 -> (n - 1) * n + c; case 2 -> c * n; default -> c * n + n - 1; };
        for (int e = 0; e < 4; e++) {
            int base = nextV;
            for (int c = 0; c < n; c++) {
                int v = edges[e][c], w = nextV++;
                pos[w * 3] = pos[v * 3];
                pos[w * 3 + 1] = (float) (pos[v * 3 + 1] - skirt);
                pos[w * 3 + 2] = pos[v * 3 + 2];
                nrm[w * 3] = nrm[v * 3]; nrm[w * 3 + 1] = nrm[v * 3 + 1]; nrm[w * 3 + 2] = nrm[v * 3 + 2];
                ao[w] = ao[v] * 0.5f;
            }
            for (int c = 0; c < n - 1; c++) {
                int v0 = edges[e][c], v1 = edges[e][c + 1];
                if (!used[v0] || !used[v1] || !(in[v0] || in[v1])) continue;
                int w0 = base + c, w1 = base + c + 1;
                tri.add(new int[]{v0, w0, v1});
                tri.add(new int[]{v1, w0, w1});
                tri.add(new int[]{v0, v1, w0});
                tri.add(new int[]{v1, w1, w0});
            }
        }
        int[] idx = new int[tri.size() * 3];
        int k = 0;
        for (int[] tr : tri) { idx[k++] = tr[0]; idx[k++] = tr[1]; idx[k++] = tr[2]; }
        return new IceMesh(pos, nrm, idx, ao);
    }

    /** Ob die Kachel im Blickfeld liegt (Spaltenmatrix viewProj, Quader der Kachel). */
    public static boolean visible(Tile t, float[] vp) {
        int outL = 0, outR = 0, outB = 0, outT = 0, outN = 0;
        for (int c = 0; c < 8; c++) {
            double x = t.x0 + ((c & 1) != 0 ? TILE : 0), y = (c & 2) != 0 ? t.maxY : t.minY, z = t.z0 + ((c & 4) != 0 ? TILE : 0);
            double cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12];
            double cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13];
            double cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15];
            if (cx < -cw) outL++;
            if (cx > cw) outR++;
            if (cy < -cw) outB++;
            if (cy > cw) outT++;
            if (cw < 0) outN++;
        }
        return outL < 8 && outR < 8 && outB < 8 && outT < 8 && outN < 8;
    }
}
