package com.dan.ground.demo;

import com.dan.ground.Batch;
import com.dan.ground.GNoise;
import com.dan.ground.Site;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

/**
 * Kleiner Software-Renderer der Vorschau: Boden (Höhenraster) und Bodendecke als Dreiecke mit Farbe je
 * Ecke, perspektivisch richtig verlaufend, mit Tiefenpuffer. Licht je Ecke: Sonne von beiden Seiten
 * (Halme sind dünn und scheinen durch), Himmel von oben; Dunst mit der Entfernung; Filmkurve.
 */
final class GroundRenderer {
    int W, H;
    private float[] iz, cr, cg, cb;
    private int[] out;
    private BufferedImage img;
    private double ex, ey, ez, fx, fy, fz, rx, ry, rz, ux, uy, uz, pf;
    float sunX = 0.45f, sunY = 0.6f, sunZ = -0.66f, sunR = 3.0f, sunG = 2.85f, sunB = 2.55f;
    float skyR = 0.32f, skyG = 0.42f, skyB = 0.62f;
    // Boden
    private final int gn;
    private final float g0, gc;
    private final float[] gpos, gcol;
    private final int[] gtri;
    // je Bild: projiziert und beleuchtet
    private float[] px = new float[0], py = new float[0], pz = new float[0], lr = new float[0], lg = new float[0], lb = new float[0], fd = new float[0];
    private int tris;
    double msRaster;

    /** Boden von −half bis +half mit Zellweite cell; Farbe aus der Bedeckung (Erde, Grasnarbe). */
    GroundRenderer(Site site, float half, float cell) {
        g0 = -half; gc = cell;
        gn = (int) (2 * half / cell) + 1;
        gpos = new float[3 * gn * gn];
        gcol = new float[3 * gn * gn];
        float[] cv = new float[5];
        for (int j = 0; j < gn; j++)
            for (int i = 0; i < gn; i++) {
                int p = j * gn + i;
                float x = g0 + i * cell, z = g0 + j * cell;
                gpos[3 * p] = x; gpos[3 * p + 1] = site.height(x, z); gpos[3 * p + 2] = z;
                site.cover(x, z, cv);
                float n = GNoise.value(x * 0.9f, z * 0.9f);
                // Grasnarbe (unten dunkel, bräunlich grün), offene Erde, Kies
                float r = 0.075f + 0.02f * n, g = 0.085f + 0.02f * n, b = 0.035f;
                float soil = Math.min(1, cv[3] * 1.5f), gravel = Math.min(1, cv[2] * 0.8f) * (1 - soil);
                r += (0.14f - r) * soil; g += (0.11f - g) * soil; b += (0.075f - b) * soil;
                r += (0.2f - r) * gravel; g += (0.19f - g) * gravel; b += (0.17f - b) * gravel;
                gcol[3 * p] = r; gcol[3 * p + 1] = g; gcol[3 * p + 2] = b;
            }
        gtri = new int[6 * (gn - 1) * (gn - 1)];
        int k = 0;
        for (int j = 0; j + 1 < gn; j++)
            for (int i = 0; i + 1 < gn; i++) {
                int a = j * gn + i, b = a + 1, c = a + gn, d = c + 1;
                gtri[k++] = a; gtri[k++] = c; gtri[k++] = b; gtri[k++] = b; gtri[k++] = c; gtri[k++] = d;
            }
    }

    private int carpetKey = -1;

    /** Bodenfarbe aus der Grasnarbe der Wiese, wenn sich die Jahreszeit geändert hat. */
    void carpet(com.dan.ground.Meadow m) {
        if (m.seasonKey() == carpetKey && carpetOf == m) return;
        carpetKey = m.seasonKey(); carpetOf = m;
        IntStream.range(0, gn).parallel().forEach(j -> {
            float[] c = new float[3];
            for (int i = 0; i < gn; i++) {
                int p = j * gn + i;
                m.carpet(gpos[3 * p], gpos[3 * p + 2], c);
                gcol[3 * p] = c[0]; gcol[3 * p + 1] = c[1]; gcol[3 * p + 2] = c[2];
            }
        });
    }

    private com.dan.ground.Meadow carpetOf;

    void setSize(int w, int h) {
        if (w == W && h == H) return;
        W = w; H = h;
        iz = new float[w * h]; cr = new float[w * h]; cg = new float[w * h]; cb = new float[w * h];
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    void camera(double cx, double cy, double cz, double tx, double ty, double tz, double fov) {
        ex = cx; ey = cy; ez = cz;
        double dx = tx - cx, dy = ty - cy, dz = tz - cz, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        fx = dx / l; fy = dy / l; fz = dz / l;
        double rl = Math.hypot(fz, fx);
        rx = -fz / rl; ry = 0; rz = fx / rl;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
        pf = (W / 2.0) / Math.tan(Math.toRadians(fov) / 2);
    }

    int triangles() { return tris; }

    /** Ein Bild: Boden, Bodendecke b und extra Dreiecke (etwa der Spaziergänger: je 9 Lagen, 3 Farben). */
    BufferedImage render(Batch b, float[] extra, float[] extraRgb, int extraN) {
        int ng = gn * gn, nb = b.nv, ne = 3 * extraN;
        int n = ng + nb + ne;
        if (px.length < n) { px = new float[n]; py = new float[n]; pz = new float[n]; lr = new float[n]; lg = new float[n]; lb = new float[n]; fd = new float[n]; }
        // Ecken projizieren und beleuchten
        IntStream.range(0, 64).parallel().forEach(s -> {
            int a = n * s / 64, e = n * (s + 1) / 64;
            for (int i = a; i < e; i++) {
                float x, y, z, nx2, ny2, nz2, r, g, bl;
                boolean thin;
                if (i < ng) {
                    x = gpos[3 * i]; y = gpos[3 * i + 1]; z = gpos[3 * i + 2];
                    int gi = i % gn, gj = i / gn;
                    float hl = gpos[3 * (gj * gn + Math.max(0, gi - 1)) + 1], hr = gpos[3 * (gj * gn + Math.min(gn - 1, gi + 1)) + 1];
                    float hd = gpos[3 * (Math.max(0, gj - 1) * gn + gi) + 1], hu = gpos[3 * (Math.min(gn - 1, gj + 1) * gn + gi) + 1];
                    nx2 = -(hr - hl) / (2 * gc); ny2 = 1; nz2 = -(hu - hd) / (2 * gc);
                    r = gcol[3 * i]; g = gcol[3 * i + 1]; bl = gcol[3 * i + 2];
                    thin = false;
                    fd[i] = 0;
                } else if (i < ng + nb) {
                    int k = i - ng;
                    x = b.xyz[3 * k]; y = b.xyz[3 * k + 1]; z = b.xyz[3 * k + 2];
                    nx2 = b.nrm[3 * k]; ny2 = b.nrm[3 * k + 1]; nz2 = b.nrm[3 * k + 2];
                    r = b.rgb[3 * k]; g = b.rgb[3 * k + 1]; bl = b.rgb[3 * k + 2];
                    thin = b.solid[k] == 0;
                    fd[i] = b.fade[k];
                } else {
                    int k = i - ng - nb;
                    x = extra[3 * k]; y = extra[3 * k + 1]; z = extra[3 * k + 2];
                    nx2 = 0; ny2 = 1; nz2 = 0;
                    r = extraRgb[3 * (k / 3)]; g = extraRgb[3 * (k / 3) + 1]; bl = extraRgb[3 * (k / 3) + 2];
                    thin = true;
                    fd[i] = 0;
                }
                float nl = (float) Math.sqrt(nx2 * nx2 + ny2 * ny2 + nz2 * nz2) + 1e-9f;
                float d = (nx2 * sunX + ny2 * sunY + nz2 * sunZ) / nl;
                float lam = thin ? 0.3f + 0.7f * Math.abs(d) : Math.max(0, d);
                float amb = 0.55f + 0.45f * ny2 / nl;
                float cR = r * (sunR * lam + skyR * amb), cG = g * (sunG * lam + skyG * amb), cB = bl * (sunB * lam + skyB * amb);
                double dx = x - ex, dy = y - ey, dz = z - ez;
                double vz = dx * fx + dy * fy + dz * fz;
                pz[i] = (float) vz;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float fa = 1 - (float) Math.exp(-dist * 0.0015f);
                cR += (0.62f - cR) * fa; cG += (0.70f - cG) * fa; cB += (0.80f - cB) * fa;
                lr[i] = cR; lg[i] = cG; lb[i] = cB;
                if (vz < 0.1) continue;
                px[i] = (float) (W / 2.0 + (dx * rx + dy * ry + dz * rz) / vz * pf);
                py[i] = (float) (H / 2.0 - (dx * ux + dy * uy + dz * uz) / vz * pf);
            }
        });
        // Himmel
        for (int y = 0; y < H; y++) {
            double bb = (H / 2.0 - y - 0.5) / pf;
            float dy = (float) (fy + uy * bb);
            float k = (float) Math.pow(1 - Math.max(0, Math.min(1, dy)), 3);
            float sr = 0.22f + 0.45f * k, sg = 0.36f + 0.40f * k, sb = 0.70f + 0.20f * k;
            for (int x = 0; x < W; x++) { int q = y * W + x; iz[q] = 0; cr[q] = sr; cg[q] = sg; cb[q] = sb; }
        }
        int ntG = gtri.length / 3, ntB = b.nt, ntE = extraN;
        int total = ntG + ntB + ntE;
        tris = total;
        long t0 = System.nanoTime();
        int strips = Math.max(1, Math.min(96, H / 6));
        int[] cnt = new int[strips];
        short[] lo = new short[total], hi = new short[total];
        for (int t = 0; t < total; t++) {
            int a = vi(t, 0, ntG, ntB, ng, b), c1 = vi(t, 1, ntG, ntB, ng, b), c2 = vi(t, 2, ntG, ntB, ng, b);
            lo[t] = -1;
            if (pz[a] < 0.1f || pz[c1] < 0.1f || pz[c2] < 0.1f) continue;
            float mnY = Math.min(py[a], Math.min(py[c1], py[c2])), mxY = Math.max(py[a], Math.max(py[c1], py[c2]));
            float mnX = Math.min(px[a], Math.min(px[c1], px[c2])), mxX = Math.max(px[a], Math.max(px[c1], px[c2]));
            if (mxY < 0 || mnY >= H || mxX < 0 || mnX >= W) continue;
            int s0 = Math.max(0, (int) (Math.max(0, mnY) * strips / H) - 1), s1 = Math.min(strips - 1, (int) (Math.min(H - 1, mxY) * strips / H) + 1);
            lo[t] = (short) s0; hi[t] = (short) s1;
            for (int s = s0; s <= s1; s++) cnt[s]++;
        }
        int[][] bins = new int[strips][];
        for (int s = 0; s < strips; s++) bins[s] = new int[cnt[s]];
        int[] fill = new int[strips];
        for (int t = 0; t < total; t++) { if (lo[t] < 0) continue; for (int s = lo[t]; s <= hi[t]; s++) bins[s][fill[s]++] = t; }
        IntStream.range(0, strips).parallel().forEach(s -> {
            int y0 = H * s / strips, y1 = H * (s + 1) / strips;
            for (int t : bins[s]) raster(vi(t, 0, ntG, ntB, ng, b), vi(t, 1, ntG, ntB, ng, b), vi(t, 2, ntG, ntB, ng, b), y0, y1);
        });
        msRaster = (System.nanoTime() - t0) / 1e6;
        IntStream.range(0, H).parallel().forEach(y -> { for (int x = 0; x < W; x++) { int q = y * W + x; out[q] = tone(cr[q], cg[q], cb[q]); } });
        return img;
    }

    private int vi(int t, int k, int ntG, int ntB, int ng, Batch b) {
        if (t < ntG) return gtri[3 * t + k];
        if (t < ntG + ntB) return b.tri[3 * (t - ntG) + k] + ng;
        return ng + b.nv + 3 * (t - ntG - ntB) + k;
    }

    private void raster(int a, int b, int c, int y0, int y1) {
        float xa = px[a], ya = py[a], xb = px[b], yb = py[b], xc = px[c], yc = py[c];
        float area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
        if (Math.abs(area) < 1e-7f) return;
        float minY = Math.min(ya, Math.min(yb, yc)), maxY = Math.max(ya, Math.max(yb, yc));
        float minX = Math.min(xa, Math.min(xb, xc)), maxX = Math.max(xa, Math.max(xb, xc));
        int ix0 = Math.max(0, (int) Math.floor(minX)), ix1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int iy0 = Math.max(y0, (int) Math.floor(minY)), iy1 = Math.min(y1 - 1, (int) Math.ceil(maxY));
        if (ix0 > ix1 || iy0 > iy1) return;
        float inv = 1 / area;
        float ia = 1 / pz[a], ib = 1 / pz[b], ic = 1 / pz[c];
        // sehr kleine Dreiecke (ferne Halme): wenigstens das Pixel der Mitte, halb gedeckt
        if (ix1 - ix0 <= 1 && iy1 - iy0 <= 1) {
            int x = (int) ((xa + xb + xc) / 3), y = (int) ((ya + yb + yc) / 3);
            if (x < 0 || y < y0 || x >= W || y >= y1) return;
            float z = (ia + ib + ic) / 3;
            int q = y * W + x;
            if (z <= iz[q]) return;
            float r = (lr[a] + lr[b] + lr[c]) / 3, g = (lg[a] + lg[b] + lg[c]) / 3, bl = (lb[a] + lb[b] + lb[c]) / 3;
            float cov = Math.min(1, Math.abs(area) * 0.5f + 0.25f) * (1 - (fd[a] + fd[b] + fd[c]) / 3);
            cr[q] += (r - cr[q]) * cov; cg[q] += (g - cg[q]) * cov; cb[q] += (bl - cb[q]) * cov;
            return;
        }
        for (int y = iy0; y <= iy1; y++) {
            float sy = y + 0.5f;
            for (int x = ix0; x <= ix1; x++) {
                float sx = x + 0.5f;
                float w0 = ((xb - sx) * (yc - sy) - (xc - sx) * (yb - sy)) * inv;
                float w1 = ((xc - sx) * (ya - sy) - (xa - sx) * (yc - sy)) * inv;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = w0 * ia + w1 * ib + w2 * ic;
                int q = y * W + x;
                if (z <= iz[q]) continue;
                float k0 = w0 * ia / z, k1 = w1 * ib / z, k2 = w2 * ic / z;
                float f = fd[a] * k0 + fd[b] * k1 + fd[c] * k2, o = 1 - f;
                if (f < 0.6f) iz[q] = z;
                cr[q] = (lr[a] * k0 + lr[b] * k1 + lr[c] * k2) * o + cr[q] * f;
                cg[q] = (lg[a] * k0 + lg[b] * k1 + lg[c] * k2) * o + cg[q] * f;
                cb[q] = (lb[a] * k0 + lb[b] * k1 + lb[c] * k2) * o + cb[q] * f;
            }
        }
    }

    private static final int[] LUT = new int[4096];
    static { for (int i = 0; i < LUT.length; i++) LUT[i] = (int) Math.round(255 * Math.pow(i / 4095.0, 1 / 2.2)); }

    private static int tone(float r, float g, float b) {
        r = Math.max(0, r) * 0.95f; g = Math.max(0, g) * 0.95f; b = Math.max(0, b) * 0.95f;
        r = r / (1 + r); g = g / (1 + g); b = b / (1 + b);
        return (LUT[(int) (Math.min(1, r) * 4095)] << 16) | (LUT[(int) (Math.min(1, g) * 4095)] << 8) | LUT[(int) (Math.min(1, b) * 4095)];
    }
}
