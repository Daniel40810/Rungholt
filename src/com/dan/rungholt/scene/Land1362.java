package com.dan.rungholt.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.SplittableRandom;

/**
 * Phase 10: das Gelände von 1362 (F3 A), modelliert auf dem Grund von heute [G: alle Lagen und Höhen gestaltet; belegt
 * sind nur 54 Warften auf gut 10 km², eine Kette von 2 km, die Kirche von 40 × 15 m auf einer Warft nahe Südfall, ein
 * Seedeich mit Sielhafen und Entwässerungsgräben].
 * <ul>
 *   <li><b>Seedeich</b> ({@link #DIKE}) von West nach Ost, gut 500 m südlich der Kirche, zur Hever hin: Krone
 *       NHN +3,5 m, 4 m breit, Böschung 1:6 zur See und 1:3 zum Land; davor ein Vorland mit Salzwiese (+1,6 bis +1,9 m,
 *       50 bis 110 m), dahinter die Hever mit dem Watt von heute.</li>
 *   <li><b>Marsch</b> nördlich des Deichs: NHN +0,5 bis +1,0 m (Altmarsch), eine flache Aufhöhung entlang der
 *       Warftenkette; was heute Priel und Watt ist, ist dort Land. Pellworm und Nordstrand gehören dazu (Alt-Strand).</li>
 *   <li><b>Warften</b> ({@link #warften()}): die Kette von 2,3 km durch die Kirchwarft am Ursprung (26 Warften und die
 *       Kirchwarft, +2,4 bis +3,2 m, Kirchwarft +3,8 m mit 120 × 76 m Fuß) und 27 Einzelwarften der Höfe in der Marsch,
 *       zusammen 54.</li>
 *   <li><b>Sielzüge</b> ({@link #CANALS}): der Hauptzug von der Kirche zum Siel im Deich bei −3600/1300, zwei Nebenzüge;
 *       12 bis 16 m breit, Wasser bei NHN +0,1 m (Binnenwasser, die Tide kommt nicht hinein). Vor dem Siel das Sieltief
 *       durch das Vorland bis in die Hever (der Hafen; Phase 11).</li>
 * </ul>
 * Die Gräben in den Fennen und die Wege malt das Gelände im Shader (zu fein für das Raster von 8 m).
 */
public final class Land1362 {
    /** Der Seedeich von West nach Ost (x, z); das Land liegt nördlich (−z). */
    public static final double[][] DIKE = {{-10100, 1250}, {-7000, 1050}, {-5000, 1150}, {-3600, 1300}, {-2000, 1000}, {-800, 600}, {0, 520},
            {800, 600}, {2000, 900}, {4000, 1700}, {6500, 2400}};
    /** Krone des Seedeichs (m NHN), halbe Kronenbreite, Böschung zur See und zum Land (Breite je Meter Höhe). */
    public static final double CREST = 3.5, CREST_HALF = 2, SEA_SLOPE = 6, LAND_SLOPE = 3;
    /** Der Siel im Deich. */
    public static final double[] SIEL = {-3600, 1300};
    /** Sielzüge (x, z), der erste endet am Siel. */
    public static final double[][][] CANALS = {
            {{250, 120}, {-300, 330}, {-1200, 560}, {-2300, 900}, {-3100, 1150}, {-3600, 1290}},
            {{1400, -700}, {900, -350}, {250, 120}},
            {{-5200, -900}, {-4600, 100}, {-4000, 900}, {-3600, 1290}}};
    /** Die Warftenkette (Mittellinie), durch die Kirchwarft am Ursprung. */
    public static final double[][] CHAIN = {{-1150, -260}, {-700, -170}, {-300, -60}, {0, 0}, {350, 60}, {750, 160}, {1050, 250}};
    /** Wasserstand der Sielzüge und Gräben (m NHN). */
    public static final double INNER_WATER = 0.1;

    /** Eine Warft: Mitte, halbe Achsen der Krone entlang und quer (m), Richtung (Grad von Nord), Kronenhöhe, Name. */
    public record Warft(double x, double z, double a, double b, double headingDeg, double top, String name) {
        /** Höhe des Hügels bei (x, z) über der Marsch m (Krone flach, Flanke 1:4), NaN außerhalb. */
        public double height(double px, double pz, double marsh) {
            double h = Math.toRadians(headingDeg), ax = Math.sin(h), az = -Math.cos(h);
            double dx = px - x, dz = pz - z, u = dx * ax + dz * az, v = -dx * az + dz * ax;
            double r = Math.sqrt(u * u / (a * a) + v * v / (b * b));   // 1 am Rand der Krone
            double crownR = Math.min(a, b), out = Math.max(0, r - 1) * crownR;   // Meter außerhalb der Krone (genähert)
            double flank = (top - marsh) * 4;
            if (out > flank) return Double.NaN;
            double t = out / flank;
            return top - (top - marsh) * (t * t * (3 - 2 * t));
        }

        public double radius() { return Math.max(a, b) + (top - 0.8) * 4; }
    }

    private static List<Warft> warften;

    /** Die 54 Warften (fest, Startwert 1362). */
    public static synchronized List<Warft> warften() {
        if (warften != null) return warften;
        List<Warft> l = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(1362);
        l.add(new Warft(0, 0, 60, 38, 100, 3.8, "Kirchwarft"));
        // Kette: alle 75 bis 95 m eine Warft, nicht auf der Kirchwarft
        double len = 0;
        double[] cum = new double[CHAIN.length];
        for (int i = 1; i < CHAIN.length; i++) cum[i] = len += Math.hypot(CHAIN[i][0] - CHAIN[i - 1][0], CHAIN[i][1] - CHAIN[i - 1][1]);
        double s = 30;
        int k = 1;
        while (s < len - 20 && l.size() < 27) {
            double[] p = along(CHAIN, cum, s);
            if (Math.hypot(p[0], p[1]) > 95) {
                double side = (r.nextDouble() - 0.5) * 30;
                double hd = Math.toDegrees(Math.atan2(p[2], -p[3]));
                l.add(new Warft(p[0] - p[3] * side, p[1] + p[2] * side, 22 + 12 * r.nextDouble(), 16 + 8 * r.nextDouble(), hd + (r.nextDouble() - 0.5) * 20,
                        2.4 + 0.8 * r.nextDouble(), "Warft " + k++));
            }
            s += 75 + 20 * r.nextDouble();
        }
        // Einzelwarften der Höfe: in 3,2 km um die Kirche, auf Land, mit Abstand
        int tries = 0;
        while (l.size() < 54 && tries++ < 20000) {
            double ang = r.nextDouble() * 2 * Math.PI, rad = 400 + 2800 * Math.sqrt(r.nextDouble());
            double x = Math.cos(ang) * rad, z = Math.sin(ang) * rad;
            if (dikeSide(x, z) < 250) continue;
            if (canalDist(x, z) < 60 || chainDist(x, z) < 180) continue;
            boolean ok = true;
            for (Warft w : l) if (Math.hypot(w.x - x, w.z - z) < 220) { ok = false; break; }
            if (!ok) continue;
            l.add(new Warft(x, z, 18 + 10 * r.nextDouble(), 14 + 8 * r.nextDouble(), r.nextDouble() * 180, 2.0 + 0.8 * r.nextDouble(), "Hofwarft " + (l.size() - 26)));
        }
        warften = List.copyOf(l);
        return warften;
    }

    /** Punkt auf einer Linie bei Strecke s: {x, z, Richtung x, Richtung z}. */
    static double[] along(double[][] line, double[] cum, double s) {
        int i = 1;
        while (i < line.length - 1 && cum[i] < s) i++;
        double seg = cum[i] - cum[i - 1], t = Math.max(0, Math.min(1, (s - cum[i - 1]) / seg));
        double dx = (line[i][0] - line[i - 1][0]) / seg, dz = (line[i][1] - line[i - 1][1]) / seg;
        return new double[]{line[i - 1][0] + (line[i][0] - line[i - 1][0]) * t, line[i - 1][1] + (line[i][1] - line[i - 1][1]) * t, dx, dz};
    }

    /** Abstand zu einem Linienzug (m). */
    public static double lineDist(double[][] line, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < line.length; i++) {
            double ax = line[i - 1][0], az = line[i - 1][1], bx = line[i][0], bz = line[i][1];
            double vx = bx - ax, vz = bz - az, t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            best = Math.min(best, Math.hypot(x - ax - vx * t, z - az - vz * t));
        }
        return best;
    }

    /** Vorzeichenbehafteter Abstand zum Deich (m): positiv auf der Landseite (Norden). */
    public static double dikeSide(double x, double z) {
        double best = Double.MAX_VALUE, sign = 1;
        for (int i = 1; i < DIKE.length; i++) {
            double ax = DIKE[i - 1][0], az = DIKE[i - 1][1], bx = DIKE[i][0], bz = DIKE[i][1];
            double vx = bx - ax, vz = bz - az, t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            double d = Math.hypot(x - ax - vx * t, z - az - vz * t);
            if (d < best) { best = d; sign = (vx * (z - az) - vz * (x - ax)) < 0 ? 1 : -1; }
        }
        return best * sign;
    }

    /**
     * Phase 17: der Punkt der Deichachse, der (x, z) am nächsten liegt, um s Meter längs des Deichs verschoben: {x, z,
     * Richtung längs x, z, Normale zur See x, z}. Für die Gischt über der Krone.
     */
    public static double[] dikePoint(double x, double z, double s) {
        double best = Double.MAX_VALUE;
        int bi = 1;
        double bt = 0;
        for (int i = 1; i < DIKE.length; i++) {
            double ax = DIKE[i - 1][0], az = DIKE[i - 1][1], bx = DIKE[i][0], bz = DIKE[i][1];
            double vx = bx - ax, vz = bz - az, t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            double d = Math.hypot(x - ax - vx * t, z - az - vz * t);
            if (d < best) { best = d; bi = i; bt = t; }
        }
        double[] cum = new double[DIKE.length];
        for (int i = 1; i < DIKE.length; i++) cum[i] = cum[i - 1] + Math.hypot(DIKE[i][0] - DIKE[i - 1][0], DIKE[i][1] - DIKE[i - 1][1]);
        double at = cum[bi - 1] + bt * (cum[bi] - cum[bi - 1]) + s;
        double[] p = along(DIKE, cum, Math.max(0, Math.min(cum[cum.length - 1], at)));
        // die See liegt südlich (dikeSide < 0): Normale gegen die Landseite
        double nx = p[3], nz = -p[2];
        if (dikeSide(p[0] + nx * 5, p[1] + nz * 5) > 0) { nx = -nx; nz = -nz; }
        return new double[]{p[0], p[1], p[2], p[3], nx, nz};
    }

    public static double canalDist(double x, double z) {
        double d = Double.MAX_VALUE;
        for (double[][] c : CANALS) d = Math.min(d, lineDist(c, x, z));
        return d;
    }

    public static double chainDist(double x, double z) { return lineDist(CHAIN, x, z); }

    /** Länge der Warftenkette (m). */
    public static double chainLength() {
        double l = 0;
        for (int i = 1; i < CHAIN.length; i++) l += Math.hypot(CHAIN[i][0] - CHAIN[i - 1][0], CHAIN[i][1] - CHAIN[i - 1][1]);
        return l;
    }

    /** Die Marsch (m NHN) ohne Warften, Deich und Sielzüge: +0,5 bis +1,0, entlang der Kette etwas höher. */
    public static double marsh(double x, double z) {
        double n = fbm(x / 900, z / 900);
        double c = chainDist(x, z);
        return 0.55 + 0.4 * n + 0.25 * Math.exp(-c * c / (450.0 * 450.0));
    }

    /** Das Gelände von 1362 aus dem von heute bauen (gleiches Raster). */
    public static Terrain build(Terrain today) {
        int n = Terrain.N;
        short[] h = new short[n * n];
        byte[] art = new byte[n * n];
        List<Warft> ws = warften();
        double[][] tiefLine = sieltief(today);
        double[] hb = harbour(tiefLine);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int idx = j * n + i;
                double x = Terrain.cx(i), z = Terrain.cz(j);
                double h0 = Terrain.decode(today.h[idx]);
                byte a0 = today.art[idx];
                double side = dikeSide(x, z);
                double y;
                byte a;
                if (side > 0) {
                    double m = marsh(x, z);
                    y = m;
                    a = Terrain.MARSCH;
                    double cd = canalDist(x, z);
                    double half = cd < 1e9 ? 7 : 0;
                    if (cd < half + 6) {   // Sielzug mit Böschung
                        double t = Math.max(0, Math.min(1, (cd - half) / 6));
                        y = -0.7 * (1 - t) + m * t;
                        if (cd < half + 2) a = Terrain.SIELZUG;
                    }
                    for (Warft w : ws) {
                        if (Math.abs(x - w.x) > w.radius() || Math.abs(z - w.z) > w.radius()) continue;
                        double wh = w.height(x, z, m);
                        if (!Double.isNaN(wh) && wh > y) { y = wh; if (wh > m + 0.4) a = Terrain.WARFT; }
                    }
                } else {
                    double out = -side;
                    // vor dem Deich: Vorland mit Salzwiese, dann das Watt und die Hever von heute
                    double vor = 1.62 + 0.18 * fbm(x / 300, z / 300);
                    double t = smooth(50, 110, out);
                    if (a0 != Terrain.WATT) h0 = Math.min(h0, 0.2 + 0.3 * fbm(x / 600, z / 600));   // Köge, Deiche und Vorland von heute gab es 1362 nicht (sonst stünde dahinter ein See)
                    y = t >= 1 ? h0 : Math.max(h0, vor * (1 - t) + h0 * t);
                    a = y > 1.45 && out < 120 ? Terrain.VORLAND : Terrain.WATT;
                    double td = lineDist(tiefLine, x, z);
                    if (td < 18 && out < 1600) { double u = smooth(8, 18, td); y = Math.min(y, -2.3 * (1 - u) + y * u); if (td < 10) a = Terrain.WATT; }
                    // Phase 11: das Hafenbecken vor dem Siel (zum Wenden der Kogge), eine Ellipse längs des Sieltiefs
                    double bq = basin(hb, x, z);
                    if (bq < 1.25) { double u = smooth(1.0, 1.25, bq); y = Math.min(y, -2.3 * (1 - u) + y * u); if (bq < 1.05) a = Terrain.WATT; }
                }
                // der Seedeich über allem (Siel: der Deich bleibt geschlossen, das Tor ist zu)
                double ad = Math.abs(side), slope = side > 0 ? LAND_SLOPE : SEA_SLOPE;
                double dh = CREST - Math.max(0, ad - CREST_HALF) / slope;
                if (dh > y) { y = dh; a = Terrain.DEICH1362; }
                h[idx] = Terrain.encode(y);
                art[idx] = a;
            }
        Properties p = new Properties();
        p.putAll(today.props);
        p.setProperty("quelle", today.props.getProperty("quelle", "") + " · 1362 modelliert [G]");
        p.setProperty("aera", "1362");
        return new Terrain(h, art, p);
    }

    // ------------------------------------------------------------------ Phase 11: Sieltief und Sielhafen

    /** Halbachsen des Hafenbeckens längs und quer zum Sieltief (m) und Abstand seiner Mitte vom Siel. */
    public static final double BASIN_ALONG = 50, BASIN_ACROSS = 30, BASIN_FROM_SIEL = 85;

    /**
     * Das Sieltief: vom Siel nach Süden (10 m je Schritt, leicht nach Osten) durch Vorland und Watt, bis das Watt von heute
     * unter NHN −2,6 m liegt (dort ist die Hever tiefer als das Tief), mindestens 250 m; auf NHN −2,3 m ausgehoben.
     */
    public static double[][] sieltief(Terrain today) {
        List<double[]> tief = new ArrayList<>();
        double sx = SIEL[0], sz = SIEL[1];
        tief.add(new double[]{sx, sz});
        for (int k = 0; k < 300 && (k < 25 || today.height(sx, sz) > -2.6); k++) { sz += 10; sx += 1.5; tief.add(new double[]{sx, sz}); }   // mindestens 250 m
        return tief.toArray(new double[0][]);
    }

    /** Das Hafenbecken: {Mitte x, Mitte z, Richtung des Tiefs x, z (Einheitsvektor nach See)}. */
    public static double[] harbour(double[][] tief) {
        double[] d = {tief[tief.length - 1][0] - tief[0][0], tief[tief.length - 1][1] - tief[0][1]};
        double l = Math.max(1e-6, Math.hypot(d[0], d[1]));
        d[0] /= l; d[1] /= l;
        return new double[]{SIEL[0] + d[0] * BASIN_FROM_SIEL, SIEL[1] + d[1] * BASIN_FROM_SIEL, d[0], d[1]};
    }

    /** Elliptischer Abstand zum Hafenbecken (1 = Rand). */
    public static double basin(double[] hb, double x, double z) {
        double dx = x - hb[0], dz = z - hb[1];
        double al = dx * hb[2] + dz * hb[3], ac = -dx * hb[3] + dz * hb[2];
        return Math.hypot(al / BASIN_ALONG, ac / BASIN_ACROSS);
    }

    /**
     * Grobe Höhe von 1362 ohne Raster (für Ansichten, bevor das Gelände gebaut ist): Marsch und Warften hinter dem
     * Deich, der Deich, das Vorland davor.
     */
    public static double estimate(double x, double z) {
        double side = dikeSide(x, z), y;
        if (side > 0) {
            double m = marsh(x, z);
            y = m;
            for (Warft w : warften()) {
                if (Math.abs(x - w.x) > w.radius() || Math.abs(z - w.z) > w.radius()) continue;
                double wh = w.height(x, z, m);
                if (!Double.isNaN(wh)) y = Math.max(y, wh);
            }
        } else y = 1.7;
        double dh = CREST - Math.max(0, Math.abs(side) - CREST_HALF) / (side > 0 ? LAND_SLOPE : SEA_SLOPE);
        return Math.max(y, dh);
    }

    // ------------------------------------------------------------------ Rauschen (wie terrain.frag)

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    static double hash(double x, double z) { double s = Math.sin(x * 127.1 + z * 311.7) * 43758.5453; return s - Math.floor(s); }

    static double vnoise(double x, double z) {
        double ix = Math.floor(x), iz = Math.floor(z), fx = x - ix, fz = z - iz;
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uz;
    }

    static double fbm(double x, double z) {
        double s = 0, a = 0.5;
        for (int i = 0; i < 4; i++) { s += a * vnoise(x, z); x = x * 2.03 + 17.1; z = z * 2.03 + 17.1; a *= 0.5; }
        return s;
    }

    private Land1362() { }
}
