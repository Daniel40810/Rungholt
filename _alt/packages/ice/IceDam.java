package com.dan.ice;

import java.util.Locale;

/**
 * Ein Eisdamm (seit Glacier Phase 13): die Zunge eines Gletschers, die an ein Ufer stößt und einen Seearm abriegelt.
 * Am Perito Moreno ist das die Spitze der Front an der Halbinsel Magallanes; hinter ihr staut sich der Brazo Rico, bis
 * das Wasser einen Tunnel unter dem Eis durchfrisst, der Tunnel zur Rinne wird und die Brücke über ihr einstürzt.
 * <p>
 * Der Damm ist ein Körper für {@code fracture} wie ein Abschnitt der Front ({@link FrontBlock}): ein Quader längs der
 * Rinne, in dem Eis ist, wo der Grund unter {@link #shoreMax} liegt und kein Gletscher steht (an den liegt der Damm an),
 * oben die Krone mit Spalten, unten der Grund. Dazu die Rinne als Linie von Mündung zu Mündung, an der das Wasser den
 * Tunnel frisst, und die Brücke, an der das Eis dicker ist [G].
 * <p>
 * Reines Java ohne Abhängigkeit (Paketvertrag Punkt 1): Gelände und Gletscher kommen als Funktionen herein.
 * Örtliche Achsen: s längs der Rinne (von Mündung A zur Mündung B, m), q quer (nach rechts, m), y oben.
 */
public final class IceDam {
    /** Höhe des Grunds an (x, z) (m). */
    @FunctionalInterface public interface Ground { double height(double x, double z); }

    /** Steht dort schon Gletschereis (an das der Damm anliegt)? */
    @FunctionalInterface public interface Cover { boolean ice(double x, double z); }

    private final Ground ground;
    private final Cover cover;
    /** Die Rinne {x0, z0, x1, z1, …} von Mündung A (die Seite mit dem hohen Wasser) zur Mündung B. */
    public final double[] line;
    /** Bogenlänge je Punkt der Rinne (m) und ihr Grund (m, y). */
    public final double[] arc, floor;
    /** Länge der Rinne (m). */
    public final double length;
    /** Achse des Quaders: Ursprung (Mündung A), Richtung längs (Einheit), Rand vor den Mündungen, Grenzen quer (m). */
    public final double ox, oz, ux, uz, out, qMin, qMax;
    /** Krone über dem Seespiegel (m) [G], Rauigkeit der Krone (m), Grund darüber ist Ufer (m), tiefster Fuß (m). */
    public final double crown, roughness, shoreMax, yBottom;
    /** Wasserspiegel des Sees (m, y) für die Dichte der Zellen. */
    public final double waterLevel;
    /** Die Brücke: Mitte längs der Rinne (m), halbe Länge (m), um so viel ist das Eis dort dicker (m) [G]. */
    public final double bridgeS, bridgeHalf, bridgeCrown;
    public final long seed;
    /** Rand quer, in dem kein Damm steht (m): Platz für Platzhalter-Zellen neben dem Damm (Glacier Phase 14). */
    public static final double MARGIN = 12;

    /**
     * Ein Damm über der Rinne {@code line} (mindestens zwei Punkte, von der Seite des hohen Wassers aus). Quer reicht
     * der Quader von {@code qMin} bis {@code qMax} (m, rechts positiv), vor den Mündungen {@code out} m hinaus.
     */
    public IceDam(Ground ground, Cover cover, double[] line, double qMin, double qMax, double out, double crown, double roughness,
                  double shoreMax, double yBottom, double waterLevel, double bridgeS, double bridgeHalf, double bridgeCrown, long seed) {
        if (line.length < 4) throw new IllegalArgumentException("Eisdamm: die Rinne braucht zwei Punkte");
        this.ground = ground;
        this.cover = cover;
        this.line = line.clone();
        int n = line.length / 2;
        arc = new double[n];
        floor = new double[n];
        for (int k = 1; k < n; k++) arc[k] = arc[k - 1] + Math.hypot(line[k * 2] - line[k * 2 - 2], line[k * 2 + 1] - line[k * 2 - 1]);
        length = arc[n - 1];
        // Grund der Rinne: das Tiefste quer über ±8 m (die Rinne liegt in der Senke)
        for (int k = 0; k < n; k++) {
            double[] t = tangent(k);
            double lo = Double.MAX_VALUE;
            for (int j = -2; j <= 2; j++) lo = Math.min(lo, ground.height(line[k * 2] - t[1] * 4 * j, line[k * 2 + 1] + t[0] * 4 * j));
            floor[k] = lo;
        }
        ox = line[0];
        oz = line[1];
        double dx = line[2 * n - 2] - ox, dz = line[2 * n - 1] - oz, l = Math.hypot(dx, dz);
        ux = dx / l;
        uz = dz / l;
        this.qMin = qMin;
        this.qMax = qMax;
        this.out = out;
        this.crown = crown;
        this.roughness = roughness;
        this.shoreMax = shoreMax;
        this.yBottom = yBottom;
        this.waterLevel = waterLevel;
        this.bridgeS = bridgeS;
        this.bridgeHalf = bridgeHalf;
        this.bridgeCrown = bridgeCrown;
        this.seed = seed;
    }

    // ------------------------------------------------------------------ Achsen

    /** Örtlich {s, q} (längs der geraden Achse A→B, quer) aus Welt (x, z). */
    public double[] local(double x, double z) {
        double dx = x - ox, dz = z - oz;
        return new double[]{dx * ux + dz * uz, dx * -uz + dz * ux};
    }

    /** Welt (x, z) aus örtlich (s, q). */
    public double[] world(double s, double q) { return new double[]{ox + ux * s - uz * q, oz + uz * s + ux * q}; }

    private double[] tangent(int k) {
        int n = line.length / 2, a = Math.max(0, k - 1), b = Math.min(n - 1, k + 1);
        double tx = line[b * 2] - line[a * 2], tz = line[b * 2 + 1] - line[a * 2 + 1], l = Math.max(1e-9, Math.hypot(tx, tz));
        return new double[]{tx / l, tz / l};
    }

    /**
     * Der Ort (x, z) an der Rinne: {Bogenlänge s (m, 0 bei A), Abstand quer (m, rechts positiv), Grund der Rinne dort
     * (m), Richtung x, Richtung z}. Hinter den Mündungen läuft s über 0 und die Länge hinaus.
     */
    public double[] channel(double x, double z) {
        int n = line.length / 2;
        double best = Double.MAX_VALUE, bs = 0, bq = 0, bf = 0, btx = ux, btz = uz;
        for (int k = 0; k + 1 < n; k++) {
            double ax = line[k * 2], az = line[k * 2 + 1], bx = line[k * 2 + 2], bz = line[k * 2 + 3];
            double ex = bx - ax, ez = bz - az, l2 = ex * ex + ez * ez, l = Math.sqrt(l2);
            double t = ((x - ax) * ex + (z - az) * ez) / l2;
            double tc = Math.max(k == 0 ? Double.NEGATIVE_INFINITY : 0, Math.min(k == n - 2 ? Double.POSITIVE_INFINITY : 1, t));
            double px = ax + ex * tc, pz = az + ez * tc, d = Math.hypot(x - px, z - pz);
            double tq = Math.max(0, Math.min(1, t));
            double qx = ax + ex * tq, qz = az + ez * tq, dd = Math.hypot(x - qx, z - qz);
            if (dd < best) {
                best = dd;
                bs = arc[k] + tc * l;
                double cross = (ex * (z - az) - ez * (x - ax)) / l;
                bq = Math.signum(cross) * d;
                bf = floor[k] + (floor[k + 1] - floor[k]) * tq;
                btx = ex / l;
                btz = ez / l;
            }
        }
        return new double[]{bs, bq, bf, btx, btz};
    }

    /** Punkt der Rinne bei Bogenlänge s: {x, z, Richtung x, Richtung z, Grund}. */
    public double[] at(double s) {
        int n = line.length / 2;
        s = Math.max(0, Math.min(length, s));
        int k = 0;
        while (k < n - 2 && arc[k + 1] < s) k++;
        double l = Math.max(1e-9, arc[k + 1] - arc[k]), t = (s - arc[k]) / l;
        double ex = line[k * 2 + 2] - line[k * 2], ez = line[k * 2 + 3] - line[k * 2 + 1], el = Math.max(1e-9, Math.hypot(ex, ez));
        return new double[]{line[k * 2] + ex * t, line[k * 2 + 1] + ez * t, ex / el, ez / el, floor[k] + (floor[k + 1] - floor[k]) * t};
    }

    // ------------------------------------------------------------------ Gestalt (wie wiring.Cells)

    /** Im Quader (ohne Rücksicht auf Eis)? */
    public boolean inBox(double x, double z) {
        double[] l = local(x, z);
        return l[0] >= -out && l[0] <= straight() + out && l[1] >= qMin && l[1] <= qMax;
    }

    /** Länge der geraden Achse A→B (m). */
    public double straight() {
        int n = line.length / 2;
        return Math.hypot(line[2 * n - 2] - ox, line[2 * n - 1] - oz);
    }

    /** Hülle {x0, y0, z0, x1, y1, z1}. */
    public double[] bounds() {
        double x0 = 1e30, z0 = 1e30, x1 = -1e30, z1 = -1e30;
        double L = straight();
        for (int k = 0; k < 4; k++) {
            double[] w = world((k & 1) == 0 ? -out : L + out, (k & 2) == 0 ? qMin : qMax);
            x0 = Math.min(x0, w[0]); x1 = Math.max(x1, w[0]);
            z0 = Math.min(z0, w[1]); z1 = Math.max(z1, w[1]);
        }
        return new double[]{x0, yBottom, z0, x1, crownMax(), z1};
    }

    /** Höchste Krone (m). */
    public double crownMax() { return crown + bridgeCrown + roughness + 2; }

    /**
     * Ebenen des Quaders {nx, ny, nz, d, …} (n·p ≤ d innen): 0 Anfang (Mündung A), 1 Ende (Mündung B), 2 quer
     * links (qMin), 3 quer rechts (qMax), 4 unten, 5 oben. Die Seiten 2, 3 und der Boden stecken in Gletscher, Ufer und
     * Grund; die Enden sind die Wände zum Wasser.
     */
    public double[] planes() {
        double ca = ux * ox + uz * oz, cq = -uz * ox + ux * oz, L = straight();
        return new double[]{
                -ux, 0, -uz, -ca + out,             // s ≥ −out
                ux, 0, uz, ca + L + out,            // s ≤ L + out
                uz, 0, -ux, -cq - qMin,             // q ≥ qMin
                -uz, 0, ux, cq + qMax,              // q ≤ qMax
                0, -1, 0, -yBottom,
                0, 1, 0, crownMax()};
    }

    /** Krone mit Spalten [G]: zum Ufer hin dünner (wo der Grund steigt), über der Brücke dicker. */
    public double top(double x, double z) {
        double g = ground.height(x, z);
        double r = roughness * (fbm(x / 22.0, z / 22.0) * 2 - 1) - 0.5 * roughness * ridge(x / 9.0 + 3.7, z / 9.0 - 1.3);
        double[] c = channel(x, z);
        double br = bridgeCrown * (1 - Glacier.smooth(bridgeHalf, bridgeHalf + 25, Math.abs(c[0] - bridgeS)));
        double t = crown + br + r - 0.25 * Math.max(0, g - 6);
        return Math.max(t, g + 0.5);
    }

    /** Fuß: der Grund, höchstens bis {@link #yBottom}. */
    public double bottom(double x, double z) { return Math.max(yBottom, ground.height(x, z)); }

    /** Steht an (x, z) Eis des Damms? */
    public boolean insideXZ(double x, double z) {
        if (!inBox(x, z) || cover.ice(x, z)) return false;
        // quer bleibt ein Rand frei: dort liegen die Platzhalter, damit keine Zelle bis an die Seite des Quaders reicht
        double q = local(x, z)[1];
        if (q < qMin + MARGIN || q > qMax - MARGIN) return false;
        double g = ground.height(x, z);
        return g < shoreMax && top(x, z) - Math.max(yBottom, g) > 1.5;
    }

    public boolean inside(double x, double y, double z) { return insideXZ(x, z) && y <= top(x, z) && y >= bottom(x, z); }

    /** Dichte der Zellen 0..1: fein an der Rinne (dort entsteht der Tunnel, fällt die Decke), grob am Rand. */
    public double density(double x, double y, double z) {
        double[] c = channel(x, z);
        double near = Math.exp(-Math.pow(c[1] / 34, 2));
        double brig = Math.exp(-Math.pow((c[0] - bridgeS) / (bridgeHalf + 30), 2));
        return Math.min(1, 0.07 + 0.75 * near + 0.25 * near * brig);
    }

    /**
     * Festigkeit als Faktor: die Spalten der Krone schwächen die oberen 12 m [G]; die Brücke ist fester (dichteres,
     * älteres Eis, sie steht am längsten).
     */
    public double strength(double x, double y, double z) {
        double t = top(x, z);
        double k = 1 - 0.45 * Glacier.smooth(t - 12, t - 2, y) * (0.5 + 0.5 * ridge(x / 9.0 + 3.7, z / 9.0 - 1.3));
        double[] c = channel(x, z);
        k *= 1 + 0.35 * (1 - Glacier.smooth(bridgeHalf, bridgeHalf + 15, Math.abs(c[0] - bridgeS)));
        return k;
    }

    /** Die Brücke in Welt: {x, z} ihrer Mitte über der Rinne. */
    public double[] bridge() { double[] p = at(bridgeS); return new double[]{p[0], p[1]}; }

    // ------------------------------------------------------------------ Rauschen

    private double hash(int i, int j) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + j * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    private double vnoise(double x, double z) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(i, j), b = hash(i + 1, j), c = hash(i, j + 1), d = hash(i + 1, j + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    private double fbm(double x, double z) { return 0.6 * vnoise(x, z) + 0.3 * vnoise(x * 2.1 + 5.2, z * 2.1 - 1.7) + 0.1 * vnoise(x * 4.3, z * 4.3); }

    /** Spalten: schmale Rinnen im Rauschen (1 in der Spalte). */
    private double ridge(double x, double z) { double v = vnoise(x, z); return Math.pow(1 - Math.min(1, Math.abs(v - 0.5) * 6), 2); }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "Eisdamm %.0f m längs der Rinne, quer %.0f bis %.0f m, Krone %.0f m (Brücke +%.0f m)", length, qMin, qMax, crown, bridgeCrown);
    }
}
