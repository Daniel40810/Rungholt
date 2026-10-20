package com.dan.buoyancy;

import java.util.Locale;

/**
 * Treibeis (Brash) als Feld (seit Glacier Phase 9): je Zelle der Anteil der Wasserfläche, den kleine Eisbrocken
 * bedecken (0..1). Ein Einschlag legt Brocken hinein, der Wind treibt sie mit wenigen Prozent seiner Geschwindigkeit,
 * dazu die Strömung; sie schmelzen in Stunden. Über Land gibt es keins. Zum Zeichnen reicht das Feld als Textur; die
 * einzelnen Brocken erfindet der Shader (Zellen aus Rauschen, deren Zahl der Anteil bestimmt).
 */
public final class BrashField {
    public final int nx, nz;
    public final double x0, z0, cell;
    private final float[] cover, tmp;
    private final byte[] water;   // 1 Wasser, 0 Land, −1 noch nicht gefragt
    /** Anteil der Windgeschwindigkeit, mit dem Brocken treiben, und Schmelze (Anteil der Bedeckung je Stunde). */
    public volatile double windDrift = 0.03, meltPerHour = 0.25;
    /** Dicke der Brocken (m): so viel Fläche bedeckt ein Kubikmeter. */
    public volatile double thickness = 0.7;
    private double acc;
    private volatile long version;
    private double total;

    public BrashField(int nx, int nz, double x0, double z0, double cell) {
        this.nx = nx;
        this.nz = nz;
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        cover = new float[nx * nz];
        tmp = new float[nx * nz];
        water = new byte[nx * nz];
        java.util.Arrays.fill(water, (byte) -1);
    }

    private boolean wet(int i, int j, Sea s) {
        int c = j * nx + i;
        if (water[c] < 0) {
            double x = x0 + (i + 0.5) * cell, z = z0 + (j + 0.5) * cell;
            water[c] = (byte) (s.bed(x, z) < s.level() - 0.3 ? 1 : 0);
        }
        return water[c] == 1;
    }

    /** Brocken mit zusammen {@code volume} m³ um (x, z) im Halbmesser r verteilen. */
    public synchronized void add(double x, double z, double volume, double r, Sea s) {
        double area = volume / thickness;
        int ci = (int) Math.floor((x - x0) / cell), cj = (int) Math.floor((z - z0) / cell), pad = (int) Math.ceil(2 * r / cell);
        double sum = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int j = Math.max(0, cj - pad); j <= Math.min(nz - 1, cj + pad); j++)
                for (int i = Math.max(0, ci - pad); i <= Math.min(nx - 1, ci + pad); i++) {
                    if (!wet(i, j, s)) continue;
                    double dx = x0 + (i + 0.5) * cell - x, dz = z0 + (j + 0.5) * cell - z;
                    double w = Math.exp(-(dx * dx + dz * dz) / (r * r));
                    if (pass == 0) sum += w;
                    else {
                        int c = j * nx + i;
                        cover[c] = (float) Math.min(0.92, cover[c] + area * w / (sum * cell * cell));
                    }
                }
            if (sum <= 0) return;
        }
        version++;
    }

    /** Treiben und Schmelzen; höchstens zehnmal je Sekunde. */
    synchronized void step(double dt, double sceneDt, double windX, double windZ, Sea s) {
        acc += dt;
        if (acc < 0.1) return;
        double h = acc;
        acc = 0;
        double ux = windX * windDrift, uz = windZ * windDrift;
        double decay = Math.max(0, 1 - meltPerHour * (sceneDt / Math.max(1e-9, dt)) * h / 3600);
        double sum = 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (water[c] == 0) { tmp[c] = 0; continue; }
                double x = x0 + (i + 0.5) * cell, z = z0 + (j + 0.5) * cell;
                double[] cur = s.velocity(x, z);
                double vx = ux + cur[0], vz = uz + (cur.length > 2 ? cur[2] : cur[1]);
                // rückwärts: woher kommt das Eis, das jetzt hier ist?
                double u = (x - vx * h - x0) / cell - 0.5, v = (z - vz * h - z0) / cell - 0.5;
                float val = sample(u, v);
                if (val > 0 && !wet(i, j, s)) val = 0;
                tmp[c] = (float) (val * decay);
                sum += tmp[c];
            }
        System.arraycopy(tmp, 0, cover, 0, cover.length);
        total = sum * cell * cell;
        version++;
    }

    private float sample(double u, double v) {
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i >= nx - 1 || j >= nz - 1) return 0;
        float fu = (float) (u - i), fv = (float) (v - j);
        float a = cover[j * nx + i], b = cover[j * nx + i + 1], c = cover[(j + 1) * nx + i], d = cover[(j + 1) * nx + i + 1];
        return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv;
    }

    /** Bedeckung am Ort (0..1). */
    public synchronized double at(double x, double z) { return sample((x - x0) / cell - 0.5, (z - z0) / cell - 0.5); }

    /** Kopie des Felds für die Grafik (0..1 je Zelle) und der Stand. */
    public synchronized float[] copy() { return cover.clone(); }
    public long version() { return version; }
    /** Bedeckte Fläche (m²). */
    public double area() { return total; }

    public String describe() { return String.format(Locale.GERMAN, "Treibeis %,.0f m² (%d² zu %.0f m)", total, nx, cell); }
}
