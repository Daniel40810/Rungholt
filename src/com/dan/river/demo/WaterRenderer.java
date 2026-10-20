package com.dan.river.demo;

import com.dan.river.Drift;
import com.dan.river.FlowField;
import com.dan.river.RNoise;
import com.dan.river.RiverPath;
import com.dan.river.WaterOptics;
import com.dan.river.WaterSurface;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

/**
 * Kleiner Software-Renderer für die Vorschau: Boden und Wasser werden in einen G-Puffer gerastert
 * (Tiefe, Ort, Normale, Material), danach wird jedes Pixel beleuchtet. Wasser Pixel für Pixel: Strömung
 * und Wellen aus dem Paket, Spiegelung nach Fresnel (Himmel, Ufer, Sonne), Blick durchs Wasser auf den
 * Grund mit Kaustik, Farbe nach Weg und Trübe, Schaum obenauf. Zum Schluss Treibgut und Filmkurve.
 */
final class WaterRenderer {
    int W, H;
    private float[] iz, wx, wy, wz, nx, ny, nz;
    private byte[] mat;
    private int[] out;
    private BufferedImage img;
    // Kamera
    private double ex, ey, ez, fx, fy, fz, rx, ry, rz, ux, uy, uz, pf;
    /** Sonne (Richtung zur Sonne, normiert) und ihr Licht. */
    float sunX = 0.45f, sunY = 0.62f, sunZ = -0.64f;
    float sunR = 3.2f, sunG = 3.0f, sunB = 2.7f;
    boolean showFlow;
    final WaterSurface surface = new WaterSurface();
    final WaterOptics optics = new WaterOptics();
    private Valley v;
    // Geometrie
    private float[] gpos, gnrm;           // Boden: Ecken
    private int[] gtri;                   // Boden: Dreiecke
    private float[] wpos;                 // Wasser: Ecken (je Bild neu, Spiegel nach Abfluss)
    private int[] wtri;
    private int wcols;
    private float[] px, py, pz;           // projiziert
    private int triCount;
    private int[] binCount;
    private short[] triLo, triHi;

    void setValley(Valley val) {
        v = val;
        int n = val.nx * val.nz;
        gpos = new float[3 * n]; gnrm = new float[3 * n];
        for (int j = 0; j < val.nz; j++)
            for (int i = 0; i < val.nx; i++) {
                int p = j * val.nx + i;
                gpos[3 * p] = val.x0 + i * val.cell; gpos[3 * p + 1] = val.h[p]; gpos[3 * p + 2] = val.z0 + j * val.cell;
                float hl = val.h[j * val.nx + Math.max(0, i - 1)], hr = val.h[j * val.nx + Math.min(val.nx - 1, i + 1)];
                float hd = val.h[Math.max(0, j - 1) * val.nx + i], hu = val.h[Math.min(val.nz - 1, j + 1) * val.nx + i];
                float ax = -(hr - hl) / (2 * val.cell), az = -(hu - hd) / (2 * val.cell), l = (float) Math.sqrt(ax * ax + 1 + az * az);
                gnrm[3 * p] = ax / l; gnrm[3 * p + 1] = 1 / l; gnrm[3 * p + 2] = az / l;
            }
        gtri = new int[6 * (val.nx - 1) * (val.nz - 1)];
        int k = 0;
        for (int j = 0; j + 1 < val.nz; j++)
            for (int i = 0; i + 1 < val.nx; i++) {
                int a = j * val.nx + i, b = a + 1, c = a + val.nx, d = c + 1;
                gtri[k++] = a; gtri[k++] = c; gtri[k++] = b;
                gtri[k++] = b; gtri[k++] = c; gtri[k++] = d;
            }
        // Wasser: Streifen längs des Laufs, etwas über die Ufer hinaus (der Boden deckt den Rand)
        RiverPath p = val.path;
        wcols = 9;
        wpos = new float[3 * p.n * wcols];
        wtri = new int[6 * (p.n - 1) * (wcols - 1)];
        k = 0;
        for (int i = 0; i + 1 < p.n; i++)
            for (int c = 0; c + 1 < wcols; c++) {
                int a = i * wcols + c, b = a + 1, cc = a + wcols, d = cc + 1;
                wtri[k++] = a; wtri[k++] = cc; wtri[k++] = b;
                wtri[k++] = b; wtri[k++] = cc; wtri[k++] = d;
            }
    }

    void setSize(int w, int h) {
        if (w == W && h == H) return;
        W = w; H = h;
        int n = w * h;
        iz = new float[n]; wx = new float[n]; wy = new float[n]; wz = new float[n];
        nx = new float[n]; ny = new float[n]; nz = new float[n]; mat = new byte[n];
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    void camera(double cx, double cy, double cz, double tx, double ty, double tz, double fovDeg) {
        ex = cx; ey = cy; ez = cz;
        double dx = tx - cx, dy = ty - cy, dz = tz - cz, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        fx = dx / l; fy = dy / l; fz = dz / l;
        // rechts = Blick × oben, oben = rechts × Blick (rechtshändig, y nach oben)
        double rl = Math.hypot(fz, fx);
        rx = -fz / rl; ry = 0; rz = fx / rl;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
        pf = (W / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2);
    }

    int triangles() { return triCount; }

    /** Zeiten des letzten Bildes (ms): Rastern, Licht. */
    volatile double msRaster, msShade;

    /** Ein Bild zur Zeit t. */
    BufferedImage render(float t, Drift drift, int driftN, float[] dq, float[] drgb) {
        FlowField flow = v.flow;
        RiverPath p = v.path;
        // Wasserspiegel nach Abfluss
        float[] c = new float[6];
        for (int i = 0; i < p.n; i++) {
            float rise = flow.rise(p.half[i]);
            for (int k = 0; k < wcols; k++) {
                float u = -1.35f + 2.7f * k / (wcols - 1);
                int o = 3 * (i * wcols + k);
                wpos[o] = p.x[i] + -p.tz[i] * u * p.half[i]; wpos[o + 1] = p.level[i] + rise; wpos[o + 2] = p.z[i] + p.tx[i] * u * p.half[i];
            }
        }
        java.util.Arrays.fill(iz, 0);
        java.util.Arrays.fill(mat, (byte) 0);
        int ng = gpos.length / 3, nw = wpos.length / 3;
        if (px == null || px.length < ng + nw) { px = new float[ng + nw]; py = new float[ng + nw]; pz = new float[ng + nw]; }
        IntStream.range(0, 64).parallel().forEach(s -> {
            int a = (ng + nw) * s / 64, b = (ng + nw) * (s + 1) / 64;
            for (int i = a; i < b; i++) {
                float[] src = i < ng ? gpos : wpos;
                int o = 3 * (i < ng ? i : i - ng);
                project(src[o], src[o + 1], src[o + 2], i);
            }
        });
        long tA = System.nanoTime();
        // Rastern in Streifen
        int strips = Math.max(1, Math.min(64, H / 8));
        triCount = 0;
        final int nGt = gtri.length / 3, nWt = wtri.length / 3;
        // Dreiecke einmal in die Streifen einsortieren (nur sichtbare), dann je Streifen rastern
        int total = nGt + nWt;
        if (binCount == null || binCount.length != strips) binCount = new int[strips];
        java.util.Arrays.fill(binCount, 0);
        if (triLo == null || triLo.length < total) { triLo = new short[total]; triHi = new short[total]; }
        for (int t3 = 0; t3 < total; t3++) {
            int a0, b0, c0;
            if (t3 < nGt) { a0 = gtri[3 * t3]; b0 = gtri[3 * t3 + 1]; c0 = gtri[3 * t3 + 2]; }
            else { int k = t3 - nGt; a0 = wtri[3 * k] + ng; b0 = wtri[3 * k + 1] + ng; c0 = wtri[3 * k + 2] + ng; }
            triLo[t3] = -1;
            if (pz[a0] < 0.25f || pz[b0] < 0.25f || pz[c0] < 0.25f) continue;
            float mnY = Math.min(py[a0], Math.min(py[b0], py[c0])), mxY = Math.max(py[a0], Math.max(py[b0], py[c0]));
            float mnX = Math.min(px[a0], Math.min(px[b0], px[c0])), mxX = Math.max(px[a0], Math.max(px[b0], px[c0]));
            if (mxY < 0 || mnY >= H || mxX < 0 || mnX >= W) continue;
            // ein Streifen Reserve je Seite: die Streifengrenzen sind ganzzahlig gerundet
            int s0 = Math.max(0, (int) (Math.max(0, mnY) * strips / H) - 1), s1 = Math.min(strips - 1, (int) (Math.min(H - 1, mxY) * strips / H) + 1);
            triLo[t3] = (short) s0; triHi[t3] = (short) s1;
            for (int s = s0; s <= s1; s++) binCount[s]++;
        }
        int[][] bins = new int[strips][];
        for (int s = 0; s < strips; s++) bins[s] = new int[binCount[s]];
        int[] fill = new int[strips];
        for (int t3 = 0; t3 < total; t3++) {
            if (triLo[t3] < 0) continue;
            for (int s = triLo[t3]; s <= triHi[t3]; s++) bins[s][fill[s]++] = t3;
        }
        IntStream.range(0, strips).parallel().forEach(s -> {
            int y0 = H * s / strips, y1 = H * (s + 1) / strips;
            for (int t3 : bins[s]) {
                if (t3 < nGt) raster(gtri[3 * t3], gtri[3 * t3 + 1], gtri[3 * t3 + 2], 0, 1, y0, y1);
                else { int k = t3 - nGt; raster(wtri[3 * k] + ng, wtri[3 * k + 1] + ng, wtri[3 * k + 2] + ng, ng, 3, y0, y1); }
            }
        });
        triCount = nGt + nWt;
        long tB = System.nanoTime();
        // Licht
        IntStream.range(0, strips).parallel().forEach(s -> {
            int y0 = H * s / strips, y1 = H * (s + 1) / strips;
            shade(y0, y1, t);
        });
        long tC = System.nanoTime();
        msRaster = (tB - tA) / 1e6; msShade = (tC - tB) / 1e6;
        // Treibgut
        for (int i = 0; i < driftN; i++) quad(dq, drgb, i);
        return img;
    }

    private void project(float x, float y, float z, int i) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double vz = dx * fx + dy * fy + dz * fz;
        pz[i] = (float) vz;
        if (vz < 0.25) return;
        px[i] = (float) (W / 2.0 + (dx * rx + dy * ry + dz * rz) / vz * pf);
        py[i] = (float) (H / 2.0 - (dx * ux + dy * uy + dz * uz) / vz * pf);
    }

    /** Dreieck (a, b, c) im Zeilenbereich y0..y1; m = Material (1 Boden, 3 Wasser). */
    private void raster(int a, int b, int c, int ng, int m, int y0, int y1) {
        float za = pz[a], zb = pz[b], zc = pz[c];
        if (za < 0.25f || zb < 0.25f || zc < 0.25f) return;
        float xa = px[a], ya = py[a], xb = px[b], yb = py[b], xc = px[c], yc = py[c];
        float minY = Math.min(ya, Math.min(yb, yc)), maxY = Math.max(ya, Math.max(yb, yc));
        if (maxY < y0 || minY >= y1) return;
        float minX = Math.min(xa, Math.min(xb, xc)), maxX = Math.max(xa, Math.max(xb, xc));
        if (maxX < 0 || minX >= W) return;
        float area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
        if (Math.abs(area) < 1e-6f) return;
        int ix0 = Math.max(0, (int) Math.floor(minX)), ix1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int iy0 = Math.max(y0, (int) Math.floor(minY)), iy1 = Math.min(y1 - 1, (int) Math.ceil(maxY));
        float ia = 1 / za, ib = 1 / zb, ic = 1 / zc;
        float[] P = m == 3 ? wpos : gpos;
        int oa = 3 * (a - ng), ob = 3 * (b - ng), oc = 3 * (c - ng);
        float inv = 1 / area;
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
                iz[q] = z;
                float k0 = w0 * ia / z, k1 = w1 * ib / z, k2 = w2 * ic / z;
                wx[q] = P[oa] * k0 + P[ob] * k1 + P[oc] * k2;
                wy[q] = P[oa + 1] * k0 + P[ob + 1] * k1 + P[oc + 1] * k2;
                wz[q] = P[oa + 2] * k0 + P[ob + 2] * k1 + P[oc + 2] * k2;
                if (m == 1) {
                    nx[q] = gnrm[oa] * k0 + gnrm[ob] * k1 + gnrm[oc] * k2;
                    ny[q] = gnrm[oa + 1] * k0 + gnrm[ob + 1] * k1 + gnrm[oc + 1] * k2;
                    nz[q] = gnrm[oa + 2] * k0 + gnrm[ob + 2] * k1 + gnrm[oc + 2] * k2;
                }
                mat[q] = (byte) m;
            }
        }
    }

    // ------------------------------------------------------------ Licht

    private void sky(float dx, float dy, float dz, float[] o) {
        float h = Math.max(0, dy);
        float k = (float) Math.pow(1 - h, 3);
        o[0] = 0.20f + 0.55f * k; o[1] = 0.36f + 0.50f * k; o[2] = 0.72f + 0.28f * k;
        float s = dx * sunX + dy * sunY + dz * sunZ;
        if (s > 0.9995f) { o[0] += 40; o[1] += 38; o[2] += 34; }
        else if (s > 0.97f) { float g = (s - 0.97f) / 0.03f; g *= g; o[0] += g * 0.8f; o[1] += g * 0.7f; o[2] += g * 0.5f; }
    }

    /** Grundfarbe des Bodens an (x, z) mit Höhe y und Normale n: Wiese, Kies, Fels, weich gemischt. */
    private void albedo(float x, float y, float z, float nyv, float[] o) {
        float[] cv = COVER.get();
        v.cover(x, z, cv);
        float n1 = RNoise.value(x * 1.7f, z * 1.7f), n2 = RNoise.value(x * 7.1f, z * 7.1f);
        float g = 0.8f + 0.4f * n1;
        float r0 = 0.07f * g, g0 = 0.10f * g, b0 = 0.04f * g;
        if (nyv < 0.8f) { float k = Math.min(1, (0.8f - nyv) * 3); r0 += (0.16f - r0) * k; g0 += (0.13f - g0) * k; b0 += (0.10f - b0) * k; }
        float kg = 0.20f + 0.10f * n2 + 0.05f * n1;
        float gr = cv[0];
        r0 += (kg * 1.05f - r0) * gr; g0 += (kg * 0.95f - g0) * gr; b0 += (kg * 0.80f - b0) * gr;
        float kr = 0.15f + 0.10f * n1 + 0.05f * n2, rk = cv[1];
        r0 += (kr - r0) * rk; g0 += (kr * 0.97f - g0) * rk; b0 += (kr * 0.92f - b0) * rk;
        o[0] = r0; o[1] = g0; o[2] = b0;
    }

    private static final ThreadLocal<float[]> COVER = ThreadLocal.withInitial(() -> new float[2]);

    private void shade(int y0, int y1, float t) {
        float[] sk = new float[3], al = new float[3], tr = new float[3], sc = new float[3];
        FlowField.Flow f = new FlowField.Flow();
        WaterSurface.Surf su = new WaterSurface.Surf();
        FlowField flow = v.flow;
        float ambR = 0.35f, ambG = 0.45f, ambB = 0.65f;
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < W; x++) {
                int q = y * W + x;
                // Sehstrahl
                double a = (x + 0.5 - W / 2.0) / pf, b = (H / 2.0 - y - 0.5) / pf;
                float dx = (float) (fx + rx * a + ux * b), dy = (float) (fy + ry * a + uy * b), dz = (float) (fz + rz * a + uz * b);
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                float r, g, bl;
                if (mat[q] == 0) {
                    sky(dx, dy, dz, sk);
                    r = sk[0]; g = sk[1]; bl = sk[2];
                } else if (mat[q] == 1) {
                    float X = wx[q], Y = wy[q], Z = wz[q];
                    albedo(X, Y, Z, ny[q], al);
                    float lam = Math.max(0, nx[q] * sunX + ny[q] * sunY + nz[q] * sunZ);
                    // nasser Saum knapp über dem Wasser: dunkler
                    if (Y < v.path.level[0] + 3 && flow.sample(X, Z, f)) {
                        float above = Y - f.level;
                        if (above < 0.25f && above > -0.05f) { float wet = 1 - above / 0.25f; al[0] *= 1 - 0.45f * wet; al[1] *= 1 - 0.45f * wet; al[2] *= 1 - 0.4f * wet; }
                    }
                    float amb = 0.55f + 0.45f * ny[q];
                    r = al[0] * (sunR * lam + ambR * amb); g = al[1] * (sunG * lam + ambG * amb); bl = al[2] * (sunB * lam + ambB * amb);
                } else {
                    float X = wx[q], Z = wz[q];
                    float dist = 1 / iz[q];
                    if (!flow.sample(X, Z, f) || f.solid) { f.vx = f.vz = f.speed = 0; f.turb = 0; f.foam = 0; f.level = wy[q]; }
                    float ground = v.height(X, Z);
                    float depth = f.level - ground;
                    if (depth <= 0.005f) {
                        // hier ist Ufer: der Boden liegt über dem Spiegel, aber hinter dem Wasserband
                        albedo(X, ground, Z, 1, al);
                        r = al[0] * (sunR * 0.6f + ambR); g = al[1] * (sunG * 0.6f + ambG); bl = al[2] * (sunB * 0.6f + ambB);
                    } else {
                        float lod = Math.min(1, Math.max(0, (dist - 25) / 90));
                        surface.sample(X, Z, t, f, lod, su);
                        float snx = -su.dhdx, sny = 1, snz = -su.dhdz, sl = (float) Math.sqrt(snx * snx + 1 + snz * snz);
                        snx /= sl; sny /= sl; snz /= sl;
                        float cosV = Math.max(0.02f, -(dx * snx + dy * sny + dz * snz));
                        float F = WaterOptics.fresnel(cosV);
                        // Spiegelung: Himmel, tief am Horizont die Ufer
                        float rrx = dx + 2 * cosV * snx, rry = dy + 2 * cosV * sny, rrz = dz + 2 * cosV * snz;
                        sky(rrx, Math.max(0.01f, rry), rrz, sk);
                        float bankK = Math.max(0, 1 - rry / 0.22f) * 0.75f;
                        sk[0] += (0.05f - sk[0]) * bankK; sk[1] += (0.07f - sk[1]) * bankK; sk[2] += (0.04f - sk[2]) * bankK;
                        // Blick durchs Wasser: gebrochen, Weg bis zum Grund
                        float sinT = (float) Math.sqrt(Math.max(0, 1 - cosV * cosV)) / 1.33f;
                        float cosT = (float) Math.sqrt(1 - sinT * sinT);
                        float path = Math.min(depth / Math.max(0.2f, cosT), 20);
                        float bx = X + dx * path * 0.6f, bz = Z + dz * path * 0.6f;
                        float bg = v.height(bx, bz);
                        float bd = Math.max(0.02f, f.level - bg);
                        albedo(bx, bg, bz, 1, al);
                        float cau = WaterOptics.caustics(bx, bz, t, bd, f.vx, f.vz);
                        optics.transmit(bd / Math.max(0.3f, sunY), tr);
                        float lr = sunR * sunY * cau * tr[0] + ambR, lg = sunG * sunY * cau * tr[1] + ambG, lb = sunB * sunY * cau * tr[2] + ambB;
                        optics.transmit(path, tr);
                        optics.inscatter(path, sunY * 2.4f + 0.6f, sc);
                        float wr = al[0] * 0.7f * lr * tr[0] + sc[0], wg = al[1] * 0.7f * lg * tr[1] + sc[1], wb = al[2] * 0.7f * lb * tr[2] + sc[2];
                        r = wr * (1 - F) + sk[0] * F; g = wg * (1 - F) + sk[1] * F; bl = wb * (1 - F) + sk[2] * F;
                        // Glanz der Sonne
                        float spec = Math.max(0, rrx * sunX + rry * sunY + rrz * sunZ);
                        float sp = (float) Math.pow(spec, 600) * 60 + (float) Math.pow(spec, 60) * 0.6f;
                        r += sunR * sp * F * 4; g += sunG * sp * F * 4; bl += sunB * sp * F * 4;
                        // Schaum
                        float fo = su.foam;
                        if (fo > 0) {
                            float lit = sunR * Math.max(0.2f, sunY) + ambR;
                            float fr = 0.75f * lit, fg = 0.77f * (sunG * Math.max(0.2f, sunY) + ambG), fb = 0.78f * (sunB * Math.max(0.2f, sunY) + ambB);
                            r += (fr - r) * fo; g += (fg - g) * fo; bl += (fb - bl) * fo;
                        }
                        if (showFlow) {
                            // Strömung sichtbar: blau langsam, gelb schnell, violett Kehrwasser
                            float sp01 = Math.min(1, f.speed / 2.5f);
                            float cr = 0.1f + 0.9f * sp01, cg = 0.25f + 0.6f * sp01, cb = 0.9f - 0.8f * sp01;
                            if (f.eddy > 0.2f) { cr = 0.8f; cg = 0.2f; cb = 0.9f; }
                            // Strichmuster in Fließrichtung
                            float bs = (float) Math.hypot(f.bx, f.bz) + 1e-4f;
                            float along = (X * f.bx + Z * f.bz) / bs - t * bs;
                            float stripe = 0.5f + 0.5f * (float) Math.sin(along * 1.5f);
                            stripe *= 1 - Math.min(1, dist / 120f);
                            float k = 0.6f + 0.6f * stripe;
                            r = cr * k; g = cg * k; bl = cb * k;
                        }
                    }
                }
                // Dunst
                if (mat[q] != 0) {
                    float dist = 1 / iz[q];
                    float fa = 1 - (float) Math.exp(-dist * 0.0009f);
                    sky(dx, Math.max(0.02f, dy), dz, sk);
                    r += (sk[0] - r) * fa; g += (sk[1] - g) * fa; bl += (sk[2] - bl) * fa;
                }
                out[q] = tone(r, g, bl);
            }
        }
    }

    private static final int[] LUT = new int[4096];
    static {
        for (int i = 0; i < LUT.length; i++) {
            double v = i / (double) (LUT.length - 1);
            LUT[i] = (int) Math.round(255 * Math.pow(v, 1 / 2.2));
        }
    }

    private static int tone(float r, float g, float b) {
        float e = 0.9f;
        r = Math.max(0, r) * e; g = Math.max(0, g) * e; b = Math.max(0, b) * e;
        r = r / (1 + r); g = g / (1 + g); b = b / (1 + b);
        return (LUT[(int) (Math.min(1, r) * 4095)] << 16) | (LUT[(int) (Math.min(1, g) * 4095)] << 8) | LUT[(int) (Math.min(1, b) * 4095)];
    }

    /** Ein Stück Treibgut als Viereck mit Tiefenprüfung. */
    private void quad(float[] q, float[] rgb, int i) {
        float[] sx = new float[4], sy = new float[4];
        float zc = 0;
        for (int k = 0; k < 4; k++) {
            int o = 12 * i + 3 * k;
            double dx = q[o] - ex, dy = q[o + 1] - ey, dz = q[o + 2] - ez;
            double vz = dx * fx + dy * fy + dz * fz;
            if (vz < 0.3) return;
            sx[k] = (float) (W / 2.0 + (dx * rx + dy * ry + dz * rz) / vz * pf);
            sy[k] = (float) (H / 2.0 - (dx * ux + dy * uy + dz * uz) / vz * pf);
            zc += (float) vz / 4;
        }
        float lit = Math.max(0.2f, sunY);
        float cr = rgb[3 * i] * (sunR * lit + 0.35f), cg = rgb[3 * i + 1] * (sunG * lit + 0.45f), cb = rgb[3 * i + 2] * (sunB * lit + 0.65f);
        int col = tone(cr, cg, cb);
        float izc = 1 / (zc - 0.05f);
        int x0 = Math.max(0, (int) Math.floor(Math.min(Math.min(sx[0], sx[1]), Math.min(sx[2], sx[3])))), x1 = Math.min(W - 1, (int) Math.ceil(Math.max(Math.max(sx[0], sx[1]), Math.max(sx[2], sx[3]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(Math.min(sy[0], sy[1]), Math.min(sy[2], sy[3])))), y1 = Math.min(H - 1, (int) Math.ceil(Math.max(Math.max(sy[0], sy[1]), Math.max(sy[2], sy[3]))));
        if (x1 - x0 > 200 || y1 - y0 > 200) return;
        boolean any = false;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                if (!inside(sx, sy, x + 0.5f, y + 0.5f)) continue;
                int p = y * W + x;
                if (iz[p] > izc) continue;
                out[p] = col;
                any = true;
            }
        if (!any) {
            int cx = (int) ((sx[0] + sx[2]) / 2), cy = (int) ((sy[0] + sy[2]) / 2);
            if (cx >= 0 && cy >= 0 && cx < W && cy < H && iz[cy * W + cx] <= izc) out[cy * W + cx] = blend(out[cy * W + cx], col);
        }
    }

    private static int blend(int a, int b) {
        return ((((a >> 16) & 255) + ((b >> 16) & 255)) / 2 << 16) | ((((a >> 8) & 255) + ((b >> 8) & 255)) / 2 << 8) | (((a & 255) + (b & 255)) / 2);
    }

    private static boolean inside(float[] xs, float[] ys, float x, float y) {
        boolean in = false;
        for (int i = 0, j = 3; i < 4; j = i++)
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) in = !in;
        return in;
    }
}
