package com.dan.forest.demo;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

/**
 * Kleiner Software-Renderer für die Vorschau des Wald-Pakets: Dreiecke mit Tiefenpuffer, Farbe je
 * Punkt (linear) mit Licht von Sonne und Himmel, Blätter zweiseitig und im Gegenlicht durchscheinend,
 * Dunst in der Ferne, Himmel als Verlauf. Gerechnet wird parallel in Bildstreifen. Er ist absichtlich
 * einfach und gehört nur zur Vorschau; ein eigenes Programm zeichnet die Bäume mit seinem Renderer.
 */
public final class SoftRenderer {
    public int W, H;
    private int[] rgb;
    private float[] zb, cr, cg, cb;
    private BufferedImage img;

    /** Kamera: Auge, Blickrichtung (normiert), rechts, oben; Brennweite in Pixeln. */
    private double ex, ey, ez, fx, fy, fz, rx, ry, rz, ux, uy, uz, focal;
    /** Sonne (Richtung zur Sonne, normiert) und Farben (linear). */
    public float sunX = 0.45f, sunY = 0.7f, sunZ = 0.35f;
    public float[] sun = {3.0f, 2.8f, 2.4f}, skyUp = {0.35f, 0.45f, 0.6f}, skyHorizon = {0.75f, 0.8f, 0.85f}, ground = {0.12f, 0.1f, 0.07f};
    public float fogDensity = 0.0025f, exposure = 0.9f;

    // gesammelte Dreiecke des Bildes: Bildpunkte (x, y, z) und Farbe je Ecke
    private float[] tx = new float[3 * 65536], ty = new float[3 * 65536], tz = new float[3 * 65536];
    private float[] tr = new float[3 * 65536], tg = new float[3 * 65536], tbb = new float[3 * 65536];
    private final java.util.concurrent.atomic.AtomicInteger ntriA = new java.util.concurrent.atomic.AtomicInteger();
    private int ntri;
    private final int strips = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);

    public void setSize(int w, int h) {
        if (w == W && h == H && img != null) return;
        W = w; H = h;
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        rgb = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        zb = new float[w * h]; cr = new float[w * h]; cg = new float[w * h]; cb = new float[w * h];
    }

    /** Kamera von (x, y, z) auf (tx, ty, tz) mit senkrechtem Bildwinkel fovDeg. */
    public void camera(double x, double y, double z, double tx0, double ty0, double tz0, double fovDeg) {
        ex = x; ey = y; ez = z;
        fx = tx0 - x; fy = ty0 - y; fz = tz0 - z;
        double l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l; fy /= l; fz /= l;
        rx = -fz; ry = 0; rz = fx;
        l = Math.sqrt(rx * rx + rz * rz);
        rx /= l; rz /= l;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
        focal = (H / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2);
    }

    public void begin() { ntriA.set(0); ntri = 0; }

    /** Platz für mindestens n weitere Dreiecke schaffen (vor parallelem Zeichnen mit {@link #mesh}). */
    public void reserve(int n) {
        int need = 3 * (ntriA.get() + n);
        if (need > tx.length) { int c = tx.length; while (c < need) c *= 2; growTri(c); }
    }

    /**
     * Ein Netz zeichnen: Lagen, Normalen, Farben je Punkt (Baumkoordinaten), Dreiecke; gestellt an
     * (ox, oy, oz), um yaw gedreht und mit scale vergrößert. two[i]: Dreieck zweiseitig; skip: Dreiecke
     * mit Punkten, die zusammenfallen (abgefallene Blätter), werden übergangen.
     */
    public void mesh(float[] pos, float[] nrm, float[] col, int[] tri, boolean[] two, int nt, double ox, double oy, double oz, double yaw, double scale) {
        mesh(pos, nrm, col, tri, two, nt, pos.length / 3, ox, oy, oz, yaw, scale, Float.NaN, 0);
    }

    /**
     * Wie oben, mit Anzahl der Punkte nv und einer Hüllkugel (Mittelpunkt in Höhe cy über dem Fuß,
     * Radius r, beides vor der Größe scale): liegt sie ganz außerhalb des Bildes, wird nichts gerechnet.
     */
    public void mesh(float[] pos, float[] nrm, float[] col, int[] tri, boolean[] two, int nt, int nv, double ox, double oy, double oz, double yaw,
                     double scale, float cy, float r) {
        if (!Float.isNaN(cy) && !visible(ox, oy + cy * scale, oz, r * scale)) return;
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        Scratch sc = SCRATCH.get();
        sc.ensure(nv);
        // jeden Punkt einmal: in die Welt, ins Bild, Licht (für beide Seiten) und Dunst
        for (int i = 0; i < nv; i++) {
            float px = pos[3 * i], py = pos[3 * i + 1], pz = pos[3 * i + 2];
            double wx = ox + (px * cs - pz * sn) * scale, wy = oy + py * scale, wz = oz + (px * sn + pz * cs) * scale;
            double dx = wx - ex, dy = wy - ey, dz = wz - ez;
            double vz = dx * fx + dy * fy + dz * fz;
            sc.vz[i] = (float) vz;
            if (vz < 0.6) continue;
            sc.sx[i] = (float) (W / 2.0 + (dx * rx + dy * ry + dz * rz) / vz * focal);
            sc.sy[i] = (float) (H / 2.0 - (dx * ux + dy * uy + dz * uz) / vz * focal);
            float nx = nrm[3 * i] * cs - nrm[3 * i + 2] * sn, ny = nrm[3 * i + 1], nz = nrm[3 * i] * sn + nrm[3 * i + 2] * cs;
            float toward = (float) (nx * dx + ny * dy + nz * dz);
            float ndl = nx * sunX + ny * sunY + nz * sunZ;
            // Vorderseite (zur Kamera) und Rückseite getrennt: zweiseitige Flächen zeigen die zur Kamera gewandte
            float front = toward <= 0 ? ndl : -ndl;
            float hemi = 0.5f + 0.5f * (toward <= 0 ? ny : -ny);
            float dir = front > 0 ? front : -front * 0.35f;
            float fog = 1 - (float) Math.exp(-vz * fogDensity);
            float r0 = col[3 * i], g0 = col[3 * i + 1], b0 = col[3 * i + 2];
            float lr = sun[0] * dir + skyUp[0] * hemi + ground[0] * (1 - hemi);
            float lg = sun[1] * dir + skyUp[1] * hemi + ground[1] * (1 - hemi);
            float lb = sun[2] * dir + skyUp[2] * hemi + ground[2] * (1 - hemi);
            sc.cr[i] = r0 * lr + (skyHorizon[0] - r0 * lr) * fog;
            sc.cg[i] = g0 * lg + (skyHorizon[1] - g0 * lg) * fog;
            sc.cb[i] = b0 * lb + (skyHorizon[2] - b0 * lb) * fog;
            // einseitig: Licht ohne Durchscheinen
            float d1 = Math.max(0, ndl), h1 = 0.5f + 0.5f * ny;
            sc.or[i] = r0 * (sun[0] * d1 + skyUp[0] * h1 + ground[0] * (1 - h1)); sc.or[i] += (skyHorizon[0] - sc.or[i]) * fog;
            sc.og[i] = g0 * (sun[1] * d1 + skyUp[1] * h1 + ground[1] * (1 - h1)); sc.og[i] += (skyHorizon[1] - sc.og[i]) * fog;
            sc.ob[i] = b0 * (sun[2] * d1 + skyUp[2] * h1 + ground[2] * (1 - h1)); sc.ob[i] += (skyHorizon[2] - sc.ob[i]) * fog;
        }
        int[] loc = sc.loc;
        int nloc = 0;
        for (int t = 0; t < nt; t++) {
            int a = tri[3 * t], b = tri[3 * t + 1], c = tri[3 * t + 2];
            if (sc.vz[a] < 0.6 || sc.vz[b] < 0.6 || sc.vz[c] < 0.6) continue;
            float xa = sc.sx[a], ya = sc.sy[a], xb = sc.sx[b], yb = sc.sy[b], xc = sc.sx[c], yc = sc.sy[c];
            float area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
            if (area == 0) continue;                          // entartet (abgefallenes Blatt) oder zu klein
            boolean twoSided = two == null || two[t];
            if (!twoSided && area > 0) continue;              // Rückseite
            if (Math.max(xa, Math.max(xb, xc)) < 0 || Math.min(xa, Math.min(xb, xc)) >= W
                    || Math.max(ya, Math.max(yb, yc)) < 0 || Math.min(ya, Math.min(yb, yc)) >= H) continue;
            if (nloc + 1 > loc.length / 4) loc = sc.loc = java.util.Arrays.copyOf(loc, loc.length * 2);
            loc[4 * nloc] = a; loc[4 * nloc + 1] = b; loc[4 * nloc + 2] = c; loc[4 * nloc + 3] = twoSided ? 1 : 0;
            nloc++;
        }
        if (nloc == 0) return;
        reserve(nloc);
        int base = 3 * ntriA.getAndAdd(nloc);
        for (int k = 0; k < nloc; k++, base += 3) {
            boolean two2 = loc[4 * k + 3] != 0;
            for (int j = 0; j < 3; j++) {
                int v = loc[4 * k + j];
                tx[base + j] = sc.sx[v]; ty[base + j] = sc.sy[v]; tz[base + j] = sc.vz[v];
                tr[base + j] = two2 ? sc.cr[v] : sc.or[v]; tg[base + j] = two2 ? sc.cg[v] : sc.og[v]; tbb[base + j] = two2 ? sc.cb[v] : sc.ob[v];
            }
        }
    }

    /** Liegt die Kugel (x, y, z, r) wenigstens teilweise im Bild? */
    public boolean visible(double x, double y, double z, double r) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double vz = dx * fx + dy * fy + dz * fz;
        if (vz < -r) return false;
        double sx = dx * rx + dy * ry + dz * rz, sy = dx * ux + dy * uy + dz * uz;
        double hx = (W / 2.0) / focal, hy = (H / 2.0) / focal;
        // Abstand zu den Seitenebenen des Sichtkegels
        double kx = Math.sqrt(1 + hx * hx), ky = Math.sqrt(1 + hy * hy);
        if ((Math.abs(sx) - hx * vz) / kx > r) return false;
        if ((Math.abs(sy) - hy * vz) / ky > r) return false;
        return true;
    }

    /** Zwischenwerte je Faden: so darf {@link #mesh} aus mehreren Fäden zugleich gerufen werden (nach {@link #reserve}). */
    private static final class Scratch {
        float[] sx = new float[0], sy, vz, cr, cg, cb, or, og, ob;
        int[] loc = new int[4096];

        void ensure(int n) {
            if (sx.length >= n) return;
            int c = Math.max(n, sx.length * 2);
            sx = new float[c]; sy = new float[c]; vz = new float[c]; cr = new float[c]; cg = new float[c]; cb = new float[c];
            or = new float[c]; og = new float[c]; ob = new float[c];
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private synchronized void growTri(int c) {
        if (c <= tx.length) return;
        tx = java.util.Arrays.copyOf(tx, c); ty = java.util.Arrays.copyOf(ty, c); tz = java.util.Arrays.copyOf(tz, c);
        tr = java.util.Arrays.copyOf(tr, c); tg = java.util.Arrays.copyOf(tg, c); tbb = java.util.Arrays.copyOf(tbb, c);
    }

    /** Boden als Gitter um (cx, cz) mit Radius R, Höhe aus h (oder 0), Farbe grasig mit Flecken. */
    public void ground(double cx, double cz, double R, int n, java.util.function.DoubleBinaryOperator h, float[] grass) {
        float[] pos = new float[3 * (n + 1) * (n + 1)], nrm = new float[pos.length], col = new float[pos.length];
        for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) {
            int p = j * (n + 1) + i;
            double x = cx - R + 2 * R * i / n, z = cz - R + 2 * R * j / n;
            pos[3 * p] = (float) x; pos[3 * p + 1] = h == null ? 0 : (float) h.applyAsDouble(x, z); pos[3 * p + 2] = (float) z;
            nrm[3 * p + 1] = 1;
            float k = 0.75f + 0.5f * com.dan.forest.Noise2.fbm((float) x * 0.08f, (float) z * 0.08f, 3);
            col[3 * p] = grass[0] * k; col[3 * p + 1] = grass[1] * k; col[3 * p + 2] = grass[2] * k;
        }
        int[] tri = new int[6 * n * n];
        int t = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int a = j * (n + 1) + i, b = a + 1, c = a + n + 2, d = a + n + 1;
            tri[t++] = a; tri[t++] = c; tri[t++] = b; tri[t++] = a; tri[t++] = d; tri[t++] = c;
        }
        mesh(pos, nrm, col, tri, null, 2 * n * n, 0, 0, 0, 0, 1);
    }

    /** Fallende Blätter als Rhomben (je 4 Ecken in xyz, Farbe und Normale je Blatt), zweiseitig. */
    public void quads(float[] xyz, float[] rgb, float[] nrm, int n) {
        float[] pos = new float[12], nr = new float[12], col = new float[12];
        int[] tri = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < n; i++) {
            System.arraycopy(xyz, 12 * i, pos, 0, 12);
            for (int k = 0; k < 4; k++) {
                nr[3 * k] = nrm[3 * i]; nr[3 * k + 1] = nrm[3 * i + 1]; nr[3 * k + 2] = nrm[3 * i + 2];
                col[3 * k] = rgb[3 * i]; col[3 * k + 1] = rgb[3 * i + 1]; col[3 * k + 2] = rgb[3 * i + 2];
            }
            mesh(pos, nr, col, tri, null, 2, 0, 0, 0, 0, 1);
        }
    }

    /** Alle gesammelten Dreiecke rastern und das Bild liefern. */
    public BufferedImage end() {
        toneTable();
        ntri = ntriA.get();
        final int per = (H + strips - 1) / strips;
        // Dreiecke in die Streifen einsortieren, die sie berühren
        int[] cnt = new int[strips + 1];
        if (s0s.length < ntri) { s0s = new byte[ntri + ntri / 4]; s1s = new byte[s0s.length]; }
        final int n = ntri, chunk = 16384;
        IntStream.range(0, (n + chunk - 1) / chunk).parallel().forEach(c -> {
            for (int t = c * chunk; t < Math.min(n, (c + 1) * chunk); t++) {
                s0s[t] = (byte) strip(Math.min(ty[3 * t], Math.min(ty[3 * t + 1], ty[3 * t + 2])), per);
                s1s[t] = (byte) strip(Math.max(ty[3 * t], Math.max(ty[3 * t + 1], ty[3 * t + 2])), per);
            }
        });
        for (int t = 0; t < ntri; t++) for (int s = s0s[t]; s <= s1s[t]; s++) cnt[s + 1]++;
        for (int s = 0; s < strips; s++) cnt[s + 1] += cnt[s];
        if (bins.length < cnt[strips]) bins = new int[cnt[strips] + cnt[strips] / 4];
        int[] fill = java.util.Arrays.copyOf(cnt, strips);
        for (int t = 0; t < ntri; t++) for (int s = s0s[t]; s <= s1s[t]; s++) bins[fill[s]++] = t;
        final int[] start = cnt;
        IntStream.range(0, strips).parallel().forEach(s -> raster(s * per, Math.min(H, (s + 1) * per), start[s], start[s + 1]));
        return img;
    }

    private int[] bins = new int[0];
    private byte[] s0s = new byte[0], s1s = new byte[0];

    private int strip(float y, int per) { return Math.max(0, Math.min(strips - 1, (int) (y / per))); }

    private void raster(int y0, int y1, int from, int to) {
        // Himmel
        for (int y = y0; y < y1; y++) {
            double b = (H / 2.0 - y - 0.5) / focal;
            double dy = fy + uy * b;
            float t = (float) Math.max(0, Math.min(1, dy * 2.5 + 0.05));
            float r = skyHorizon[0] + (skyUp[0] * 0.9f - skyHorizon[0]) * t, g = skyHorizon[1] + (skyUp[1] * 0.9f - skyHorizon[1]) * t,
                    bl = skyHorizon[2] + (skyUp[2] * 1.1f - skyHorizon[2]) * t;
            for (int x = 0; x < W; x++) { int p = y * W + x; zb[p] = Float.MAX_VALUE; cr[p] = r; cg[p] = g; cb[p] = bl; }
        }
        for (int bi = from; bi < to; bi++) {
            int o = 3 * bins[bi];
            float ya = ty[o], yb = ty[o + 1], yc = ty[o + 2];
            float minY = Math.min(ya, Math.min(yb, yc)), maxY = Math.max(ya, Math.max(yb, yc));
            if (maxY < y0 || minY >= y1) continue;
            float xa = tx[o], xb = tx[o + 1], xc = tx[o + 2];
            float minX = Math.min(xa, Math.min(xb, xc)), maxX = Math.max(xa, Math.max(xb, xc));
            if (maxX < 0 || minX >= W) continue;
            float area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
            if (Math.abs(area) < 1e-6f) continue;
            float inv = 1 / area;
            int py0 = Math.max(y0, (int) Math.ceil(minY - 0.5f)), py1 = Math.min(y1 - 1, (int) Math.floor(maxY - 0.5f));
            int px0 = Math.max(0, (int) Math.ceil(minX - 0.5f)), px1 = Math.min(W - 1, (int) Math.floor(maxX - 0.5f));
            // Tiefe perspektivisch: 1/z interpolieren
            float iza = 1 / tz[o], izb = 1 / tz[o + 1], izc = 1 / tz[o + 2];
            for (int y = py0; y <= py1; y++) {
                float sy = y + 0.5f;
                for (int x = px0; x <= px1; x++) {
                    float sx = x + 0.5f;
                    float w0 = ((xb - sx) * (yc - sy) - (xc - sx) * (yb - sy)) * inv;
                    float w1 = ((xc - sx) * (ya - sy) - (xa - sx) * (yc - sy)) * inv;
                    float w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    float iz = w0 * iza + w1 * izb + w2 * izc;
                    float z = 1 / iz;
                    int p = y * W + x;
                    if (z >= zb[p]) continue;
                    zb[p] = z;
                    float a0 = w0 * iza * z, a1 = w1 * izb * z, a2 = w2 * izc * z;
                    cr[p] = a0 * tr[o] + a1 * tr[o + 1] + a2 * tr[o + 2];
                    cg[p] = a0 * tg[o] + a1 * tg[o + 1] + a2 * tg[o + 2];
                    cb[p] = a0 * tbb[o] + a1 * tbb[o + 1] + a2 * tbb[o + 2];
                }
            }
        }
        for (int y = y0; y < y1; y++) for (int x = 0; x < W; x++) {
            int p = y * W + x;
            rgb[p] = (tone(cr[p]) << 16) | (tone(cg[p]) << 8) | tone(cb[p]);
        }
    }

    /** Filmkurve und sRGB, als Tabelle über 0..8 (linear). */
    private static final int[] TONE = new int[4097];
    private float lutExposure = Float.NaN;

    private int tone(float v) {
        int i = (int) (v * 512);
        return TONE[i < 0 ? 0 : i > 4096 ? 4096 : i];
    }

    private void toneTable() {
        if (lutExposure == exposure) return;
        for (int i = 0; i <= 4096; i++) {
            float v = i / 512f * exposure;
            v = v / (1 + v * 0.6f) * 1.3f;
            double s = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
            TONE[i] = (int) Math.max(0, Math.min(255, s * 255 + 0.5));
        }
        lutExposure = exposure;
    }

    public int triangles() { return ntriA.get(); }
}
