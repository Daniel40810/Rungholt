package com.dan.river.demo;

import com.dan.river.FlowField;
import com.dan.river.RNoise;
import com.dan.river.RiverPath;
import com.dan.river.Rock;

/**
 * Das Tal der Vorschau: ein Bach von Norden nach Süden (z wächst), 340 m lang. Oben ein ruhiger Lauf,
 * dann eine weite Kurve mit tiefem Kolk außen und Kiesbank innen, eine Schnelle mit Steinen und am Ende
 * wieder ruhiges Wasser. Der Boden ist ein Höhenraster; Flussbett und Steine sind hineingeformt.
 */
final class Valley {
    static final double[][] NODES = {
            {-10, -175, 3.3, 7}, {14, -125, 3.1, 7.5}, {30, -78, 2.95, 8.5}, {8, -36, 2.85, 9.5}, {-24, 0, 2.75, 7.5},
            {-22, 28, 2.35, 5.5}, {-8, 58, 1.45, 5.0}, {12, 90, 0.9, 6.5}, {22, 128, 0.7, 8}, {2, 180, 0.5, 8}};

    final RiverPath path;
    final FlowField flow;
    /** Raster: Ursprung, Zellweite, Größe; Höhen und Art (0 Wiese, 1 Kies, 2 Fels). */
    final float x0 = -110, z0 = -170, cell = 0.8f;
    final int nx = 276, nz = 426;
    final float[] h;
    /** Anteil Kies und Fels je Rasterpunkt 0..1 (bilinear gelesen, damit die Grenzen weich sind). */
    final float[] gravel, rock;

    Valley() {
        // zwei Läufe aus denselben Punkten: einer sucht nur nahe am Wasser (schnell, für die Strömung),
        // der andere bis weit ins Tal (nur zum Formen des Bodens)
        path = new RiverPath(NODES, 2.5, 12);
        RiverPath wide = new RiverPath(NODES, 2.5, 160);
        flow = new FlowField(path);
        // Steine: die Schnelle (viele, teils knapp unter Wasser), ein großer Block im Kolk, einzelne am Ufer
        java.util.Random r = new java.util.Random(7);
        float[] c = new float[6];
        for (int i = 0; i < 26; i++) {
            double sArc = 205 + r.nextDouble() * 60;
            path.at(sArc, c);
            double u = (r.nextDouble() * 2 - 1) * 0.8;
            double px = c[0] + -c[5] * u * c[3], pz = c[1] + c[4] * u * c[3];
            double rad = 0.35 + r.nextDouble() * 0.8;
            flow.add(new Rock(px, pz, rad, r.nextDouble() < 0.3 ? -0.08 : 0.15 + r.nextDouble() * 0.5));
        }
        path.at(172, c);
        flow.add(new Rock(c[0] + -c[5] * -0.3 * c[3], c[1] + c[4] * -0.3 * c[3], 1.6, 0.7));
        for (int i = 0; i < 6; i++) {
            double sArc = 20 + r.nextDouble() * 150;
            path.at(sArc, c);
            double u = (r.nextBoolean() ? 1 : -1) * (0.75 + 0.15 * r.nextDouble());
            flow.add(new Rock(c[0] + -c[5] * u * c[3], c[1] + c[4] * u * c[3], 0.5 + r.nextDouble() * 0.6, 0.3 + 0.3 * r.nextDouble()));
        }
        h = new float[nx * nz];
        gravel = new float[nx * nz];
        rock = new float[nx * nz];
        java.util.stream.IntStream.range(0, nz).parallel().forEach(j -> {
            RiverPath.Loc L = new RiverPath.Loc();
            for (int i = 0; i < nx; i++) {
                float x = x0 + i * cell, z = z0 + j * cell;
                ground(wide, x, z, L, j * nx + i);
            }
        });
    }

    private void ground(RiverPath wide, float x, float z, RiverPath.Loc L, int idx) {
        float noise = RNoise.fbm(x * 0.05f, z * 0.05f, 4);
        if (!wide.locate(x, z, L)) { h[idx] = 8 + noise * 3; return; }
        float e = Math.abs(L.n) - L.half;                 // Abstand zum Ufer (innen negativ)
        float y, gr, rk = 0;
        if (e < 0) {
            // Flussbett: Tiefe aus der Strömung beim mittleren Abfluss, Kies mit Buckeln
            float u = L.n / L.half;
            float dmax = flow.depth * (float) Math.sqrt(L.half / 10f);
            float bend = Math.max(-0.8f, Math.min(0.8f, -L.curv * L.half * u * 2.2f));
            float d = dmax * (float) Math.pow(Math.max(0, 1 - u * u), 0.7) * (1 + 0.6f * bend);
            y = L.level - Math.max(0.05f, d) + (RNoise.value(x * 0.9f, z * 0.9f) - 0.5f) * 0.12f;
            gr = 1;
        } else {
            // Ufer: Kiesstreifen, Böschung, Aue, dann Talhang
            float bank = 0.7f * smooth(0.5f, 4f, e);
            float plain = 0.04f * Math.max(0, e - 4);
            float wall = 0.22f * Math.max(0, e - 32) * (0.7f + 0.6f * noise);
            y = L.level - 0.25f + 0.25f * smooth(0, 1.5f, e) + bank + plain + wall + (noise - 0.5f) * 0.8f * smooth(4, 20, e);
            gr = 1 - smooth(1.5f + noise * 2, 3.5f + noise * 2, e);
        }
        // Steine
        for (Rock r : flow.rocks()) {
            float dx = x - r.x, dz = z - r.z, d2 = dx * dx + dz * dz, R = r.r * 1.15f;
            if (d2 < R * R) {
                float top = L.level + r.top + 0.1f;
                float dome = (float) Math.sqrt(1 - d2 / (R * R));
                float ry = (L.level - 0.6f) + (top - (L.level - 0.6f)) * (float) Math.pow(dome, 0.6) + (RNoise.value(x * 3, z * 3) - 0.5f) * 0.12f;
                if (ry > y) { y = ry; rk = Math.max(rk, smooth(0, 0.35f, dome)); }
            }
        }
        h[idx] = y;
        gravel[idx] = gr;
        rock[idx] = rk;
    }

    /** Bodenhöhe bilinear. */
    float height(float x, float z) { return bil(h, x, z); }

    /** Anteil Kies und Fels an (x, z): out[0], out[1]. */
    void cover(float x, float z, float[] out) { out[0] = bil(gravel, x, z); out[1] = bil(rock, x, z); }

    private float bil(float[] h, float x, float z) {
        float u = (x - x0) / cell, v = (z - z0) / cell;
        int i = Math.max(0, Math.min(nx - 2, (int) u)), j = Math.max(0, Math.min(nz - 2, (int) v));
        float fu = Math.max(0, Math.min(1, u - i)), fv = Math.max(0, Math.min(1, v - j));
        int p = j * nx + i;
        float a = h[p] + (h[p + 1] - h[p]) * fu, b = h[p + nx] + (h[p + nx + 1] - h[p + nx]) * fu;
        return a + (b - a) * fv;
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
