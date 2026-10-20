package com.dan.road.demo;

import com.dan.road.Batch;
import com.dan.road.Ground;
import com.dan.road.Network;
import com.dan.road.Roads;
import com.dan.road.WNoise;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

/**
 * Software-Renderer der Vorschau: Gelände (Höhenraster), Bach und die Dreiecke der Wege mit Farbe je
 * Ecke, perspektivisch richtig verlaufend, mit Tiefenpuffer. Licht je Ecke: Sonne, Himmel, nachts die
 * Scheinwerfer; glänzende Flächen (nasse Fahrbahn, Pfützen, Glas) spiegeln Himmel, Sonne und
 * Scheinwerfer nach Fresnel. Leuchten strahlen selbst, Lichthöfe werden addiert. Dunst, Regen,
 * Filmkurve.
 */
final class RoadRenderer {
    int W, H;
    private float[] iz, cr, cg, cb;
    private int[] out;
    private BufferedImage img;
    private double ex, ey, ez, fx, fy, fz, rx, ry, rz, ux, uy, uz, pf;
    // Licht
    float sunX = 0.5f, sunY = 0.55f, sunZ = -0.67f;
    float dark, rain, exposure = 1;
    // Gelände
    private final int gn;
    private final float g0, gc;
    private final float[] gpos, gnrm, gcol;
    private final boolean[] gwater;
    private final int[] gtri;
    private int ntG;
    private float[] px = new float[0], py = new float[0], pz = new float[0], lr = new float[0], lg = new float[0], lb = new float[0], fd = new float[0];
    private byte[] kd = new byte[0];
    private int tris;
    double msRaster;
    Roads roads;

    RoadRenderer(Ground ground, Network net, float half, float cell) {
        g0 = -half; gc = cell;
        gn = (int) (2 * half / cell) + 1;
        gpos = new float[3 * gn * gn];
        gnrm = new float[3 * gn * gn];
        gcol = new float[3 * gn * gn];
        gwater = new boolean[gn * gn];
        IntStream.range(0, gn).parallel().forEach(j -> {
            for (int i = 0; i < gn; i++) {
                int p = j * gn + i;
                float x = g0 + i * cell, z = g0 + j * cell;
                float h = ground.height(x, z);
                float wl = ground.water(x, z);
                gpos[3 * p] = x; gpos[3 * p + 1] = h; gpos[3 * p + 2] = z;
                float n = WNoise.value(x * 0.05f, z * 0.05f), n2 = WNoise.fbm(x * 0.006f + 2, z * 0.006f + 9, 3);
                // Felder in Streifen, Wiese, Wald am Hang
                float field = WNoise.smooth(0.55f, 0.6f, WNoise.value((float) Math.floor(x / 90) * 0.37f, (float) Math.floor(z / 60) * 0.51f));
                float r = 0.075f + 0.03f * n, g = 0.10f + 0.03f * n, b = 0.035f;
                float fr = 0.22f + 0.05f * n2, fg = 0.18f + 0.04f * n2, fb = 0.07f;
                r += (fr - r) * field; g += (fg - g) * field; b += (fb - b) * field;
                float cov = net.cover(x, z);
                r += (0.13f - r) * cov * 0.6f; g += (0.12f - g) * cov * 0.6f; b += (0.10f - b) * cov * 0.6f;
                if (wl == wl && wl > h) { gwater[p] = true; gpos[3 * p + 1] = wl; r = 0.02f; g = 0.03f; b = 0.03f; }
                gcol[3 * p] = r; gcol[3 * p + 1] = g; gcol[3 * p + 2] = b;
            }
        });
        for (int j = 0; j < gn; j++)
            for (int i = 0; i < gn; i++) {
                int p = j * gn + i;
                float hl = gpos[3 * (j * gn + Math.max(0, i - 1)) + 1], hr = gpos[3 * (j * gn + Math.min(gn - 1, i + 1)) + 1];
                float hd = gpos[3 * (Math.max(0, j - 1) * gn + i) + 1], hu = gpos[3 * (Math.min(gn - 1, j + 1) * gn + i) + 1];
                float nx = -(hr - hl) / (2 * gc), nz = -(hu - hd) / (2 * gc), l = (float) Math.sqrt(nx * nx + 1 + nz * nz);
                gnrm[3 * p] = nx / l; gnrm[3 * p + 1] = 1 / l; gnrm[3 * p + 2] = nz / l;
                if (gwater[p]) { gnrm[3 * p] = 0; gnrm[3 * p + 1] = 1; gnrm[3 * p + 2] = 0; }
            }
        gtri = new int[6 * (gn - 1) * (gn - 1)];
        int k = 0;
        for (int j = 0; j + 1 < gn; j++)
            for (int i = 0; i + 1 < gn; i++) {
                int a = j * gn + i, b = a + 1, c = a + gn, d = c + 1;
                gtri[k++] = a; gtri[k++] = c; gtri[k++] = b; gtri[k++] = b; gtri[k++] = c; gtri[k++] = d;
            }
        ntG = gtri.length / 3;
    }

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

    // ------------------------------------------------------------ Himmel und Licht

    /** Himmelsfarbe in Richtung (dx, dy, dz), linear. */
    void sky(float dx, float dy, float dz, float[] o) {
        float k = (float) Math.pow(1 - Math.max(0, Math.min(1, dy)), 3);
        float day = daylight(), grey = Math.min(1, rain * 1.2f);
        float r = 0.22f + 0.45f * k, g = 0.36f + 0.40f * k, b = 0.70f + 0.20f * k;
        r += (0.5f - r) * grey; g += (0.52f - g) * grey; b += (0.56f - b) * grey;
        float nr = 0.004f + 0.01f * k, ng = 0.006f + 0.012f * k, nb = 0.014f + 0.016f * k;
        // Dämmerung: warmer Horizont
        float dusk = 4 * dark * (1 - dark) * k * (1 - grey);
        o[0] = r * day + nr * (1 - day) + 0.5f * dusk; o[1] = g * day + ng * (1 - day) + 0.2f * dusk; o[2] = b * day + nb * (1 - day) + 0.05f * dusk;
    }

    private float sunR, sunG, sunB, skyR, skyG, skyB;

    /** Tageslicht 0..1 aus der Dunkelheit: in der Dämmerung fällt es schnell ab. */
    float daylight() { return (1 - dark) * (1 - dark); }

    /** Belichtung passend zum Tageslicht: nachts länger, damit Mondlicht und Lampen sichtbar werden. */
    float autoExposure() { return 1 / (daylight() + 0.09f); }

    private void lightSetup() {
        float day = daylight(), cloud = 1 - 0.75f * Math.min(1, rain * 1.5f);
        float warm = dark * 0.8f;
        sunR = 3.0f * day * cloud; sunG = (2.85f - 0.8f * warm) * day * cloud; sunB = (2.55f - 1.4f * warm) * day * cloud;
        float amb = day + 0.035f * (1 - day);
        skyR = (0.32f + 0.15f * rain) * amb; skyG = (0.42f + 0.1f * rain) * amb; skyB = (0.62f - 0.05f * rain) * amb;
    }

    // ------------------------------------------------------------ Bild

    BufferedImage render(Batch b, float t) {
        lightSetup();
        int ng = gn * gn, nb = b.nv;
        int n = ng + nb;
        if (px.length < n) {
            int c = n + n / 4;
            px = new float[c]; py = new float[c]; pz = new float[c]; lr = new float[c]; lg = new float[c]; lb = new float[c]; fd = new float[c]; kd = new byte[c];
        }
        final float hzR, hzG, hzB;
        float[] hz = new float[3];
        sky((float) fx, 0.05f, (float) fz, hz);
        hzR = hz[0]; hzG = hz[1]; hzB = hz[2];
        final float air = 0.00022f + 0.003f * rain;
        final boolean night = roads != null && roads.lightCount > 0;
        IntStream.range(0, 128).parallel().forEach(s -> {
            int a = n * s / 128, e = n * (s + 1) / 128;
            float[] sk = new float[3], il = new float[3];
            for (int i = a; i < e; i++) {
                float x, y, z, nx, ny, nz, r, g, bl, gl;
                byte kind;
                if (i < ng) {
                    x = gpos[3 * i]; y = gpos[3 * i + 1]; z = gpos[3 * i + 2];
                    nx = gnrm[3 * i]; ny = gnrm[3 * i + 1]; nz = gnrm[3 * i + 2];
                    r = gcol[3 * i]; g = gcol[3 * i + 1]; bl = gcol[3 * i + 2];
                    gl = gwater[i] ? 1 : 0;
                    kind = Batch.SOLID;
                    fd[i] = 0;
                    // Schnee auf dem Gelände nach dem Wetter der Wege
                    if (roads != null && roads.weather.snow > 0.01f && !gwater[i]) {
                        float c = WNoise.smooth(0, 0.4f, roads.weather.snow * 1.5f + (WNoise.value(x * 0.1f, z * 0.1f) - 0.5f) * 0.3f) * WNoise.smooth(0.7f, 0.9f, ny);
                        r += (0.75f - r) * c; g += (0.77f - g) * c; bl += (0.82f - bl) * c;
                    } else if (roads != null && !gwater[i]) {
                        float k = 1 - 0.3f * roads.weather.wet;
                        r *= k; g *= k; bl *= k;
                    }
                } else {
                    int k = i - ng;
                    x = b.xyz[3 * k]; y = b.xyz[3 * k + 1]; z = b.xyz[3 * k + 2];
                    nx = b.nrm[3 * k]; ny = b.nrm[3 * k + 1]; nz = b.nrm[3 * k + 2];
                    r = b.rgb[3 * k]; g = b.rgb[3 * k + 1]; bl = b.rgb[3 * k + 2];
                    gl = b.gloss[k];
                    kind = b.kind[k];
                    fd[i] = b.fade[k];
                }
                kd[i] = kind;
                double dx = x - ex, dy = y - ey, dz = z - ez;
                double vz = dx * fx + dy * fy + dz * fz;
                pz[i] = (float) vz;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float cR, cG, cB;
                if (kind == Batch.LAMP || kind == Batch.GLOW) { cR = r; cG = g; cB = bl; }
                else {
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
                    nx /= nl; ny /= nl; nz /= nl;
                    float d = nx * sunX + ny * sunY + nz * sunZ;
                    boolean thin = kind == Batch.THIN;
                    float lam = thin ? 0.3f + 0.7f * Math.abs(d) : Math.max(0, d);
                    float amb = 0.55f + 0.45f * Math.abs(ny);
                    float eR = sunR * lam + skyR * amb, eG = sunG * lam + skyG * amb, eB = sunB * lam + skyB * amb;
                    if (night && dist < 260) {
                        il[0] = il[1] = il[2] = 0;
                        roads.illuminate(x, y, z, nx, ny, nz, il);
                        if (thin && il[0] == 0) roads.illuminate(x, y, z, -nx, -ny, -nz, il);
                        eR += il[0]; eG += il[1]; eB += il[2];
                    }
                    float dif = 1 - 0.6f * gl;
                    cR = r * eR * dif; cG = g * eG * dif; cB = bl * eB * dif;
                    if (gl > 0.01f) {
                        // Spiegelung: Himmel nach Fresnel, Sonnenglanz, Scheinwerfer
                        float vx = (float) (dx / dist), vy = (float) (dy / dist), vzz = (float) (dz / dist);
                        float vn = vx * nx + vy * ny + vzz * nz;
                        float rxv = vx - 2 * vn * nx, ryv = vy - 2 * vn * ny, rzv = vzz - 2 * vn * nz;
                        float cos = Math.max(0, -vn), f5 = (1 - cos) * (1 - cos);
                        f5 = f5 * f5 * (1 - cos);
                        float F = (0.03f + 0.97f * f5) * gl;
                        sky(rxv, Math.max(0.02f, ryv), rzv, sk);
                        float sp = rxv * sunX + ryv * sunY + rzv * sunZ;
                        float glint = sp > 0.9f ? (float) Math.pow(sp, 300 * gl + 20) * 40 * gl : 0;
                        cR += (sk[0] + sunR * glint) * F * 1.2f; cG += (sk[1] + sunG * glint) * F * 1.2f; cB += (sk[2] + sunB * glint) * F * 1.2f;
                        if (night && dist < 400) {
                            float s2 = spec(x, y, z, rxv, ryv, rzv) * gl;
                            cR += s2; cG += s2 * 0.92f; cB += s2 * 0.78f;
                        }
                    }
                }
                float fa = 1 - (float) Math.exp(-dist * air);
                if (kind == Batch.GLOW) { cR *= 1 - fa; cG *= 1 - fa; cB *= 1 - fa; }
                else { cR += (hzR - cR) * fa; cG += (hzG - cG) * fa; cB += (hzB - cB) * fa; }
                lr[i] = cR; lg[i] = cG; lb[i] = cB;
                if (vz < 0.1) continue;
                px[i] = (float) (W / 2.0 + (dx * rx + dy * ry + dz * rz) / vz * pf);
                py[i] = (float) (H / 2.0 - (dx * ux + dy * uy + dz * uz) / vz * pf);
            }
        });
        // Himmel
        IntStream.range(0, H).parallel().forEach(y -> {
            float[] sk = new float[3];
            for (int x = 0; x < W; x++) {
                double aa = (x + 0.5 - W / 2.0) / pf, bb = (H / 2.0 - y - 0.5) / pf;
                float dx = (float) (fx + rx * aa + ux * bb), dy = (float) (fy + ry * aa + uy * bb), dz = (float) (fz + rz * aa + uz * bb);
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                sky(dx / l, dy / l, dz / l, sk);
                int q = y * W + x;
                iz[q] = 0; cr[q] = sk[0]; cg[q] = sk[1]; cb[q] = sk[2];
            }
        });
        int ntB = b.nt;
        int total = ntG + ntB;
        tris = total;
        long t0 = System.nanoTime();
        int strips = Math.max(1, Math.min(128, H / 6));
        int[] cnt = new int[strips];
        short[] lo = new short[total], hi = new short[total];
        IntStream.range(0, 64).parallel().forEach(q -> {
            for (int tt = total * q / 64; tt < total * (q + 1) / 64; tt++) {
                int a = vi(tt, 0, b, ng), c1 = vi(tt, 1, b, ng), c2 = vi(tt, 2, b, ng);
                lo[tt] = -1;
                if (pz[a] < 0.1f || pz[c1] < 0.1f || pz[c2] < 0.1f) continue;
                float mnY = Math.min(py[a], Math.min(py[c1], py[c2])), mxY = Math.max(py[a], Math.max(py[c1], py[c2]));
                float mnX = Math.min(px[a], Math.min(px[c1], px[c2])), mxX = Math.max(px[a], Math.max(px[c1], px[c2]));
                if (mxY < 0 || mnY >= H || mxX < 0 || mnX >= W) continue;
                lo[tt] = (short) Math.max(0, (int) (Math.max(0, mnY) * strips / H) - 1);
                hi[tt] = (short) Math.min(strips - 1, (int) (Math.min(H - 1, mxY) * strips / H) + 1);
            }
        });
        for (int tt = 0; tt < total; tt++) if (lo[tt] >= 0) for (int s = lo[tt]; s <= hi[tt]; s++) cnt[s]++;
        int[][] bins = new int[strips][];
        for (int s = 0; s < strips; s++) bins[s] = new int[cnt[s]];
        int[] fill = new int[strips];
        for (int tt = 0; tt < total; tt++) { if (lo[tt] < 0) continue; for (int s = lo[tt]; s <= hi[tt]; s++) bins[s][fill[s]++] = tt; }
        IntStream.range(0, strips).parallel().forEach(s -> {
            int y0 = H * s / strips, y1 = H * (s + 1) / strips;
            // erst alles Feste, dann Lichthöfe (addiert, ohne Tiefe zu schreiben)
            for (int tt : bins[s]) { int a = vi(tt, 0, b, ng); if (kd[a] != Batch.GLOW) raster(a, vi(tt, 1, b, ng), vi(tt, 2, b, ng), y0, y1, false); }
            for (int tt : bins[s]) { int a = vi(tt, 0, b, ng); if (kd[a] == Batch.GLOW) raster(a, vi(tt, 1, b, ng), vi(tt, 2, b, ng), y0, y1, true); }
        });
        msRaster = (System.nanoTime() - t0) / 1e6;
        // Regen: feine Striche
        if (rain > 0.02f) rainStreaks(t);
        final float ex2 = exposure;
        IntStream.range(0, H).parallel().forEach(y -> { for (int x = 0; x < W; x++) { int q = y * W + x; out[q] = tone(cr[q] * ex2, cg[q] * ex2, cb[q] * ex2); } });
        return img;
    }

    /** Spiegelung der Scheinwerfer in einer glänzenden Fläche (Blickstrahl nach der Spiegelung r). */
    private float spec(float x, float y, float z, float rxv, float ryv, float rzv) {
        float s = 0;
        float[] L = roads.lights;
        for (int i = 0; i < roads.lightCount; i++) {
            int o = 7 * i;
            float dx = L[o] - x, dy = L[o + 1] - y, dz = L[o + 2] - z;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 90000) continue;
            float d = (float) Math.sqrt(d2);
            float c = (rxv * dx + ryv * dy + rzv * dz) / d;
            if (c < 0.9f) continue;
            // der Scheinwerfer muss zur Fläche hin leuchten
            float face = -(dx * L[o + 3] + dy * L[o + 4] + dz * L[o + 5]) / d;
            if (face < 0.3f) continue;
            float k = (c - 0.9f) / 0.1f;
            s += L[o + 6] * 0.08f * k * k * k * k * face / (d2 * 0.02f + 1);
        }
        return s;
    }

    private void rainStreaks(float t) {
        int count = (int) (W * H / 900f * rain);
        java.util.Random r = new java.util.Random((long) (t * 30));
        float len = H * 0.035f;
        for (int k = 0; k < count; k++) {
            int x0 = r.nextInt(W), y0 = r.nextInt(H);
            float a = 0.12f + 0.1f * r.nextFloat();
            for (int j = 0; j < len; j++) {
                int x = x0 + j / 6, y = y0 + j;
                if (x >= W || y >= H) break;
                int q = y * W + x;
                float c = 0.25f * (1 - dark * 0.85f) + 0.02f;
                cr[q] += (c - cr[q]) * a; cg[q] += (c - cg[q]) * a; cb[q] += (c * 1.05f - cb[q]) * a;
            }
        }
    }

    private int vi(int t, int k, Batch b, int ng) {
        if (t < ntG) return gtri[3 * t + k];
        return b.tri[3 * (t - ntG) + k] + ng;
    }

    private void raster(int a, int b, int c, int y0, int y1, boolean add) {
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
        if (ix1 - ix0 <= 1 && iy1 - iy0 <= 1) {
            int x = (int) ((xa + xb + xc) / 3), y = (int) ((ya + yb + yc) / 3);
            if (x < 0 || y < y0 || x >= W || y >= y1) return;
            float z = (ia + ib + ic) / 3;
            int q = y * W + x;
            if (z <= iz[q]) return;
            float r = (lr[a] + lr[b] + lr[c]) / 3, g = (lg[a] + lg[b] + lg[c]) / 3, bl = (lb[a] + lb[b] + lb[c]) / 3;
            float cov = Math.min(1, Math.abs(area) * 0.5f + 0.25f) * (1 - (fd[a] + fd[b] + fd[c]) / 3);
            if (add) { cr[q] += r * cov; cg[q] += g * cov; cb[q] += bl * cov; return; }
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
                float r = lr[a] * k0 + lr[b] * k1 + lr[c] * k2, g = lg[a] * k0 + lg[b] * k1 + lg[c] * k2, bl = lb[a] * k0 + lb[b] * k1 + lb[c] * k2;
                if (add) { cr[q] += r; cg[q] += g; cb[q] += bl; continue; }
                float f = fd[a] * k0 + fd[b] * k1 + fd[c] * k2, o = 1 - f;
                if (f < 0.6f) iz[q] = z;
                cr[q] = r * o + cr[q] * f; cg[q] = g * o + cg[q] * f; cb[q] = bl * o + cb[q] * f;
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
