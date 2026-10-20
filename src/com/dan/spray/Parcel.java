package com.dan.spray;

/**
 * Ein Bündel Wasser in der Luft: ein Schwall Spritzwasser, eine Wolke Sprühnebel oder eine Gischtfahne. Der Kern führt
 * nur das Bündel; seine Tropfen sind eine Funktion von Bündel, Nummer und Alter ({@link #particle}), ohne eigenen Zustand.
 * Dieselbe Rechnung steht Zeile für Zeile in {@code gl/spray_common.glsl}: Die Grafikkarte zeichnet Tausende Tropfen, ohne
 * dass sie gespeichert oder zurückgelesen werden müssen.
 * <p>
 * Bewegung jedes Tropfens: Schwerkraft und eine Luftreibung, die ihn an den Wind angleicht (lineare Reibung mit Beiwert
 * k je Sekunde; kleine Tropfen haben ein größeres k). Mit der Grenzgeschwindigkeit u = Wind + Schwerkraft/k gilt genau
 * <pre>  v(t) = u + (v0 − u)·e^(−k t)      x(t) = x0 + u·t + (v0 − u)·(1 − e^(−k t))/k</pre>
 */
public record Parcel(int kind, double x, double y, double z, double t0, double vx, double vy, double vz, double life,
                     double k, double spreadPos, double spreadVel, double grav, int seed, double size, double waterY,
                     double mass, double tx, double tz, double nx, double nz, double wx, double wz, double burst) {

    public static final int SPLASH = 0, MIST = 1, DRIFT = 2, WASH = 3;
    public static final double G = 9.81;
    public static final String[] NAME = {"Spritzwasser", "Sprühnebel", "Gischtfahne", "Weißwasser"};

    /** Wolkenartig (Nebel, Weißwasser): schwebt, wächst und stirbt nicht an der Wasserlinie. */
    public static boolean cloudy(int kind) { return kind == MIST || kind == WASH; }

    /** Ganzzahl-Hash (32 Bit ohne Vorzeichen, wie im Shader). */
    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** Zufall 0..1 für Tropfen i, Wurf j. */
    public static double rnd(int seed, int i, int j) {
        return (hash(hash(i * 16 + j) ^ seed) >>> 8) * (1.0 / 16777216.0);
    }

    /** Alter des Bündels zur Zeit t. */
    public double age(double t) { return t - t0; }

    /** Mittelpunkt des Bündels (ein Tropfen ohne Streuung) zur Zeit t: {x, y, z}. */
    public double[] center(double t) { return move(x, y, z, vx, vy, vz, k, t - t0); }

    /** Bewegung nach der Formel oben. */
    double[] move(double px, double py, double pz, double ux0, double uy0, double uz0, double kk, double dt) {
        double ux = wx, uy = -G * grav / kk, uz = wz;
        double e = (1 - Math.exp(-kk * dt)) / kk;
        return new double[]{px + ux * dt + (ux0 - ux) * e, py + uy * dt + (uy0 - uy) * e, pz + uz * dt + (uz0 - uz) * e};
    }

    /**
     * Tropfen i zur Zeit t: {x, y, z, Größe, lebt (1/0), vx, vy, vz}. Genau wie {@code sprayParticle} im Shader.
     */
    public double[] particle(int i, double t) {
        double delay = rnd(seed, i, 0) * burst;
        double dt = t - t0 - delay;
        double r2 = rnd(seed, i, 1), r3 = rnd(seed, i, 2);
        double kk = kind == SPLASH ? k * (0.5 + 1.5 * r2 * r2) : cloudy(kind) ? k : k * (0.6 + 0.8 * r2);
        double f = kind == SPLASH ? 0.35 + 0.8 * Math.sqrt(r3) : 1.0;
        double a = (rnd(seed, i, 3) - 0.5) * 2 * spreadPos, b = rnd(seed, i, 4) * spreadPos * 0.3, c = (rnd(seed, i, 5) - 0.5) * spreadPos * 0.3;
        double px = x + tx * a + nx * b, py = y + c, pz = z + tz * a + nz * b;
        double jx = (rnd(seed, i, 6) - 0.5) * 2 * spreadVel, jy = (rnd(seed, i, 7) - 0.5) * 2 * spreadVel, jz = (rnd(seed, i, 8) - 0.5) * 2 * spreadVel;
        double ux0 = vx * f + jx, uy0 = vy * f + jy, uz0 = vz * f + jz;
        double tt = Math.max(dt, 0);
        double[] p = move(px, py, pz, ux0, uy0, uz0, kk, tt);
        double ex = Math.exp(-kk * tt), ux = wx, uy = -G * grav / kk, uz = wz;
        double sz = size * (0.6 + 0.8 * rnd(seed, i, 9)) * (kind == MIST ? 1 + 0.3 * tt : kind == WASH ? 1 + 0.35 * tt : 1);
        boolean alive = dt >= 0 && dt <= life && (cloudy(kind) || p[1] >= waterY);
        return new double[]{p[0], p[1], p[2], sz, alive ? 1 : 0, ux + (ux0 - ux) * ex, uy + (uy0 - uy) * ex, uz + (uz0 - uz) * ex};
    }

    /** Lebt das Bündel noch (einige Tropfen sind noch in der Luft)? */
    public boolean alive(double t) { return t - t0 <= life + burst; }
}
