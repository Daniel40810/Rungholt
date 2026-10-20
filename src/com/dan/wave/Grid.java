package com.dan.wave;

/**
 * Ein Gitter der Flachwassergleichungen (Arakawa C): Wasserstand η in den Zellmitten, Durchfluss q = u·D (m²/s) auf
 * den Zellkanten. Gerechnet wird in Flussform, also bleibt das Volumen erhalten (bis auf die Dämpfung am Rand und die
 * Quellen):
 * <pre>
 *   ∂q/∂t = −g·D·∂η/∂x − g·n²·|q|·q / D^(7/3)        (Impuls, ohne Mitführung: lange Wellen)
 *   ∂η/∂t = −∂qx/∂x − ∂qz/∂z                          (Masse)
 * </pre>
 * D ist die Wassertiefe an der Kante: höchster Wasserstand der beiden Zellen minus höchster Grund (so fließt Wasser
 * nur über Land, wenn es höher steht als das Land: Auflaufen und Trockenfallen ohne Sonderfälle). Der Reibung wegen
 * halb implizit. Was aus einer Zelle mehr hinaus will, als drin ist, wird anteilig gekürzt, so bleibt D ≥ 0.
 */
final class Grid {
    final int nx, nz;
    final double dx, x0, z0;
    /** Ruhetiefe je Zelle (m, positiv: Wasser; negativ: Land so hoch über dem Ruhespiegel). */
    final float[] h;
    /** Wasserstand über dem Ruhespiegel (m); trocken: η = −h. */
    final float[] eta;
    /** Durchfluss auf den Kanten: qx[(nx+1)·j + i] zwischen Zelle i−1 und i, qz[nx·j + i] zwischen j−1 und j. */
    final float[] qx, qz;
    /** Höchster Wasserstand an Land (m über dem Ruhespiegel; NaN: nie nass) und Zeit des letzten Nasswerdens (s). */
    final float[] runup, wetAt;
    private final float[] fac;
    /** Rechenfenster: nur hier ist etwas los (Zellen, einschließlich). */
    int i0, i1, j0, j1;
    boolean active;
    /** Breite der Dämpfungszone am Rand (Zellen; 0: geschlossene Wand). */
    int sponge;
    double maxDepth;

    static final float DMIN = 0.01f;

    Grid(int nx, int nz, double dx, double x0, double z0) {
        this.nx = nx;
        this.nz = nz;
        this.dx = dx;
        this.x0 = x0;
        this.z0 = z0;
        int n = nx * nz;
        h = new float[n];
        eta = new float[n];
        qx = new float[(nx + 1) * nz];
        qz = new float[nx * (nz + 1)];
        runup = new float[n];
        wetAt = new float[n];
        fac = new float[n];
        java.util.Arrays.fill(runup, Float.NaN);
        java.util.Arrays.fill(wetAt, -1e9f);
    }

    /** Grund setzen; der See steht still. */
    void bathymetry(Wave.Bed bed) {
        maxDepth = 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                double d = bed.depth(x0 + (i + 0.5) * dx, z0 + (j + 0.5) * dx);
                if (Double.isNaN(d)) d = -1000;
                h[c] = (float) d;
                eta[c] = d < 0 ? (float) -d : 0f;
                maxDepth = Math.max(maxDepth, d);
            }
        java.util.Arrays.fill(qx, 0);
        java.util.Arrays.fill(qz, 0);
        active = false;
    }

    boolean contains(double x, double z) { return x >= x0 && z >= z0 && x < x0 + nx * dx && z < z0 + nz * dx; }

    /** Größter stabiler Schritt (s), CFL 0,45 (in 2D gilt die halbe Grenze je Richtung). */
    double cfl(double g) { return 0.45 * dx / Math.sqrt(2 * g * Math.max(1, maxDepth + 5)); }

    /** Rechenfenster um eine Zelle erweitern (mit Rand). */
    void touch(int i, int j, int pad) {
        int a = Math.max(0, i - pad), b = Math.min(nx - 1, i + pad), c = Math.max(0, j - pad), d = Math.min(nz - 1, j + pad);
        if (!active) { i0 = a; i1 = b; j0 = c; j1 = d; active = true; return; }
        i0 = Math.min(i0, a); i1 = Math.max(i1, b); j0 = Math.min(j0, c); j1 = Math.max(j1, d);
    }

    /**
     * Einen Schritt dt: Impuls auf den Kanten im Fenster, Kürzung, Masse. {@code bx}/{@code bz}: Durchfluss auf den
     * Randkanten von außen (eingebettetes Gitter) oder null (Wand).
     */
    void step(double dt, double g, double manning, double edgeDamping, double vmax, Boundary bnd) {
        if (!active) return;
        // das Fenster wächst mit der Welle: höchstens eine Zelle je Schritt (CFL), zwei zur Sicherheit
        i0 = Math.max(0, i0 - 2); i1 = Math.min(nx - 1, i1 + 2); j0 = Math.max(0, j0 - 2); j1 = Math.min(nz - 1, j1 + 2);
        float gdt = (float) (g * dt), rdx = (float) (1 / dx), n2 = (float) (manning * manning), vm = (float) vmax;
        // Impuls x: Kanten i0..i1+1
        for (int j = j0; j <= j1; j++) {
            int row = j * nx, qrow = j * (nx + 1);
            for (int i = i0; i <= i1 + 1; i++) {
                int f = qrow + i;
                if (i == 0 || i == nx) {
                    qx[f] = bnd == null ? 0 : (float) bnd.qx(this, i, j);
                    continue;
                }
                qx[f] = momentum(qx[f], eta[row + i - 1], eta[row + i], h[row + i - 1], h[row + i], gdt, rdx, n2, (float) dt, vm);
            }
        }
        // Impuls z: Kanten j0..j1+1
        for (int j = j0; j <= j1 + 1; j++) {
            for (int i = i0; i <= i1; i++) {
                int f = j * nx + i;
                if (j == 0 || j == nz) {
                    qz[f] = bnd == null ? 0 : (float) bnd.qz(this, i, j);
                    continue;
                }
                int a = (j - 1) * nx + i, b = j * nx + i;
                qz[f] = momentum(qz[f], eta[a], eta[b], h[a], h[b], gdt, rdx, n2, (float) dt, vm);
            }
        }
        // Kürzung: was hinaus will, darf nicht mehr sein, als in der Zelle ist
        float k = (float) (dt / dx);
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int c = j * nx + i;
                float out = Math.max(0, qx[j * (nx + 1) + i + 1]) - Math.min(0, qx[j * (nx + 1) + i])
                        + Math.max(0, qz[(j + 1) * nx + i]) - Math.min(0, qz[j * nx + i]);
                float d = eta[c] + h[c];
                out *= k;
                fac[c] = out > d && out > 0 ? Math.max(0, d) / out : 1f;
            }
        for (int j = j0; j <= j1; j++) {
            int qrow = j * (nx + 1);
            for (int i = Math.max(1, i0); i <= Math.min(nx - 1, i1 + 1); i++) {
                float q = qx[qrow + i];
                if (q > 0 && i - 1 >= i0) q *= fac[j * nx + i - 1];
                else if (q < 0 && i <= i1) q *= fac[j * nx + i];
                qx[qrow + i] = q;
            }
        }
        for (int j = Math.max(1, j0); j <= Math.min(nz - 1, j1 + 1); j++)
            for (int i = i0; i <= i1; i++) {
                int f = j * nx + i;
                float q = qz[f];
                if (q > 0 && j - 1 >= j0) q *= fac[(j - 1) * nx + i];
                else if (q < 0 && j <= j1) q *= fac[j * nx + i];
                qz[f] = q;
            }
        // Masse
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int c = j * nx + i;
                float div = qx[j * (nx + 1) + i + 1] - qx[j * (nx + 1) + i] + qz[(j + 1) * nx + i] - qz[j * nx + i];
                float e = eta[c] - k * div;
                if (e < -h[c]) e = -h[c];
                eta[c] = e;
            }
        // Dämpfung am äußeren Rand: die Welle läuft hinaus, statt zurückzukommen
        if (sponge > 0 && edgeDamping > 0) damp((float) (edgeDamping * dt));
    }

    private static float momentum(float q, float eL, float eR, float hL, float hR, float gdt, float rdx, float n2, float dt, float vm) {
        float smax = Math.max(eL, eR), bed = Math.max(-hL, -hR);
        float d = smax - bed;
        if (d < DMIN) return 0f;
        q -= gdt * d * (eR - eL) * rdx;
        // Reibung am Grund: zählt nur im Flachen (bei 30 m Tiefe dämpft sie eine lange Welle um weniger als 1 ‰ je s)
        if (d < 30 && n2 > 0) {
            float fr = n2 * Math.abs(q) / (d * d * (float) Math.cbrt(d));
            q /= 1 + gdt * fr;
        }
        float lim = vm * d;
        return q > lim ? lim : q < -lim ? -lim : q;
    }

    private void damp(float k) {
        int s = sponge;
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int e = Math.min(Math.min(i, nx - 1 - i), Math.min(j, nz - 1 - j));
                if (e >= s) continue;
                float w = 1 - e / (float) s;
                float f = Math.max(0, 1 - k * w * w * 8);
                int c = j * nx + i;
                if (h[c] > 0) eta[c] *= f;
                qx[j * (nx + 1) + i] *= f;
                qz[j * nx + i] *= f;
            }
    }

    /** Land, das nass wird: höchster Stand und Zeit; gibt die Zahl der Zellen zurück, die gerade nass geworden sind. */
    int runup(double t, java.util.function.IntConsumer fresh) {
        int k = 0;
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int c = j * nx + i;
                if (h[c] >= 0) continue;
                float d = eta[c] + h[c];
                if (d <= DMIN) continue;
                if (Float.isNaN(runup[c]) || eta[c] > runup[c] + 0.05f) {
                    runup[c] = Float.isNaN(runup[c]) ? eta[c] : Math.max(runup[c], eta[c]);
                    if (fresh != null) fresh.accept(c);
                    k++;
                }
                wetAt[c] = (float) t;
            }
        return k;
    }

    /** Ist etwas los? Fenster auf das verkleinern, wo sich etwas bewegt (alle paar Schritte). */
    void shrink(float eps) {
        if (!active) return;
        int a = nx, b = -1, c = nz, d = -1;
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int q = j * nx + i;
                boolean move = h[q] > 0 && Math.abs(eta[q]) > eps || Math.abs(qx[j * (nx + 1) + i]) > eps || Math.abs(qz[q]) > eps
                        || h[q] <= 0 && eta[q] + h[q] > DMIN;
                if (!move) continue;
                a = Math.min(a, i); b = Math.max(b, i); c = Math.min(c, j); d = Math.max(d, j);
            }
        if (b < 0) { active = false; return; }
        i0 = Math.max(0, a - 3); i1 = Math.min(nx - 1, b + 3); j0 = Math.max(0, c - 3); j1 = Math.min(nz - 1, d + 3);
    }

    /** Wasserstand an (x, z), bilinear über nasse Zellen; NaN, wenn trocken oder außerhalb. */
    static double sample(float[] e, float[] h, int nx, int nz, double dx, double x0, double z0, double x, double z) {
        double u = (x - x0) / dx - 0.5, v = (z - z0) / dx - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < -1 || j < -1 || i >= nx || j >= nz) return Double.NaN;
        double fu = u - i, fv = v - j, sum = 0, w = 0;
        for (int b = 0; b <= 1; b++)
            for (int a = 0; a <= 1; a++) {
                int ii = Math.max(0, Math.min(nx - 1, i + a)), jj = Math.max(0, Math.min(nz - 1, j + b));
                int c = jj * nx + ii;
                if (e[c] + h[c] <= DMIN) continue;
                double ww = (a == 0 ? 1 - fu : fu) * (b == 0 ? 1 - fv : fv);
                sum += ww * e[c];
                w += ww;
            }
        return w < 1e-6 ? Double.NaN : sum / w;
    }

    /** Volumen über dem Ruhespiegel im Wasser (m³), für die Prüfung der Erhaltung. */
    double volume() {
        double v = 0;
        for (int c = 0; c < eta.length; c++) v += eta[c] + h[c] - Math.max(0, h[c]);
        return v * dx * dx;
    }

    /** Randbedingung eines eingebetteten Gitters. */
    interface Boundary {
        double qx(Grid g, int i, int j);
        double qz(Grid g, int i, int j);
    }
}
