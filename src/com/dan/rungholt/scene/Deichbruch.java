package com.dan.rungholt.scene;

import com.dan.fracture.Fracture;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Shape;
import com.dan.fracture.Solid;

import java.util.Locale;

/**
 * Phase 12: der Bruch des Seedeichs mit {@code com.dan.fracture}. Ein Abschnitt des Deichs (Vorgabe 50 m) wird zum
 * Körper aus Klei ({@code Fracture.klei()}, rund 150 Zellen), am Fuß im Grund verankert. Über die Krone laufende
 * Wellen und überströmendes Wasser spülen die Binnenböschung aus (die Zellen auf der Landseite verschwinden von oben
 * nach unten, {@link Solid#melt}); auf die Seeseite drückt das Wasser (hydrostatisch, je Randfläche). Was nicht mehr
 * hält, bricht als Stück ab und kippt in die Bresche; die Strömung zieht landwärts ({@link Fracture#current}), Brocken
 * von einigen Kubikmetern Klei bleiben unter Wasser aber liegen (wie bei echten Brüchen, wo der Klei Korn für Korn geht).
 * <p>
 * Örtliche Achsen: t längs des Deichs, s zur See (waagerecht, Einheitsvektoren). Die Höhen kommen aus {@link Land1362}.
 */
public final class Deichbruch {
    /** Breite des Abschnitts längs des Deichs (m) und Sohle der Bresche nach dem Bruch (m NHN). */
    public static final double WIDTH = 50, BOTTOM = 0.2;

    public final Fracture world = Fracture.klei();
    public final double x, z;
    /** Längs des Deichs und zur See (x, z). */
    public final double[] t, s;
    public Solid solid;
    /** Ausgespülter Anteil der Binnenböschung 0..1 (vor dem Bruch aufsummiert), Wasser zur See und zum Land (m NHN). */
    public double eroded, seaLevel = Double.NaN, landLevel = Double.NaN;
    /** Zeit seit dem Bruch (s); NaN davor. */
    public double since = Double.NaN;
    private int cellsAtStart;
    private double soak;

    /** Eine Bruchstelle auf dem Seedeich bei x (m); z und Richtung aus der Linie des Deichs. */
    public Deichbruch(double atX) {
        double[][] d = Land1362.DIKE;
        int k = 1;
        while (k < d.length - 1 && d[k][0] < atX) k++;
        double tx = d[k][0] - d[k - 1][0], tz = d[k][1] - d[k - 1][1], l = Math.hypot(tx, tz);
        t = new double[]{tx / l, tz / l};
        double sx = -t[1], sz = t[0];
        // zur See zeigt die Normale, auf der dikeSide negativ ist
        double px = atX, pz = Stadt1362.dikeZ(atX);
        if (Land1362.dikeSide(px + sx * 20, pz + sz * 20) > 0) { sx = -sx; sz = -sz; }
        s = new double[]{sx, sz};
        x = px;
        z = pz;
        world.seed = 1362;
        world.sleepAfter = 20;
        world.dissolveBelow = 0.4;
        world.budget = 220;
    }

    /** Liegt (px, pz) im Abschnitt (mit Saum m)? */
    public boolean covers(double px, double pz, double margin) {
        double a = (px - x) * t[0] + (pz - z) * t[1], b = (px - x) * s[0] + (pz - z) * s[1];
        return Math.abs(a) < WIDTH / 2 + margin && b > -Land1362.CREST_HALF - (Land1362.CREST - BOTTOM) * Land1362.LAND_SLOPE - margin
                && b < Land1362.CREST_HALF + (Land1362.CREST - BOTTOM) * Land1362.SEA_SLOPE + margin;
    }

    /**
     * Höhe des Geländes nach dem Bruch: im Abschnitt die Sohle der Bresche, zu den Enden auf 8 m weich in den Deich
     * (Kolk und Abbruchkanten); null ändert nichts außerhalb.
     */
    public double carve(double px, double pz, double h) {
        double a = Math.abs((px - x) * t[0] + (pz - z) * t[1]), b = (px - x) * s[0] + (pz - z) * s[1];
        double reachLand = Land1362.CREST_HALF + (Land1362.CREST - BOTTOM) * Land1362.LAND_SLOPE + 6;
        double reachSea = Land1362.CREST_HALF + (Land1362.CREST - BOTTOM) * Land1362.SEA_SLOPE + 4;
        if (b < -reachLand || b > reachSea || a > WIDTH / 2 + 8) return h;
        double u = a <= WIDTH / 2 ? 0 : (a - WIDTH / 2) / 8;
        u = u * u * (3 - 2 * u);
        double bottom = BOTTOM - 0.8 * Math.exp(-(b * b) / 60) * (1 - u);   // Kolk mittig unter der alten Krone
        return Math.min(h, bottom + (h - bottom) * u);
    }

    /** Den Körper anlegen (Zellen), mit der Binnenböschung so weit ausgespült, wie {@link #eroded} sagt. */
    public void build(Fracture.Ground ground) {
        double c = Land1362.CREST, ch = Land1362.CREST_HALF, ss = Land1362.SEA_SLOPE, ls = Land1362.LAND_SLOPE, yb = BOTTOM;
        double sd = s[0] * x + s[1] * z, td = t[0] * x + t[1] * z;
        // Ebenen n·p ≤ d (n nach außen): Krone, Sohle, Böschungen, Enden
        double[] sea = norm(s[0] / ss, 1, s[1] / ss), land = norm(-s[0] / ls, 1, -s[1] / ls);
        double dSea = (c + ch / ss + sd / ss) / len(s[0] / ss, 1, s[1] / ss), dLand = (c + ch / ls - sd / ls) / len(s[0] / ls, 1, s[1] / ls);
        double reachSea = ch + (c - yb) * ss, reachLand = ch + (c - yb) * ls;
        double[][] corners = new double[8][];
        int k = 0;
        for (int e = -1; e <= 1; e += 2)
            for (int q = 0; q < 2; q++) {
                double along = e * WIDTH / 2, across = q == 0 ? reachSea : -reachLand;
                corners[k++] = new double[]{x + t[0] * along + s[0] * across, z + t[1] * along + s[1] * across};
            }
        double x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
        for (int i = 0; i < 4; i++) { x0 = Math.min(x0, corners[i][0]); x1 = Math.max(x1, corners[i][0]); z0 = Math.min(z0, corners[i][1]); z1 = Math.max(z1, corners[i][1]); }
        Shape shape = Shape.convex(new double[]{x0, yb, z0, x1, c, z1},
                0, 1, 0, c,
                0, -1, 0, -yb,
                sea[0], sea[1], sea[2], dSea,
                land[0], land[1], land[2], dLand,
                t[0], 0, t[1], td + WIDTH / 2,
                -t[0], 0, -t[1], -td + WIDTH / 2);
        world.ground = ground;
        // mehr Zellen an der Krone und zur Mitte (dort bricht es), weniger am Fuß
        solid = world.addSolid(shape, 150, (px, py, pz) -> 0.6 + 0.8 * Math.exp(-Math.pow(((px - x) * t[0] + (pz - z) * t[1]) / 15, 2)) * (0.5 + py / c), null);
        solid.anchorBelow(yb + 0.05, 1.5);
        // die Enden halten am Deich daneben
        solid.anchor(f -> Math.abs(f.nx * t[0] + f.nz * t[1]) > 0.9, 1.0);
        cellsAtStart = solid.cells();
        erode(eroded);
        since = 0;
    }

    /** Die Binnenböschung bis zum Anteil e ausspülen (die Zellen, die am weitesten landwärts und oben liegen, zuerst). */
    public int erode(double e) {
        if (solid == null) return 0;
        double c = Land1362.CREST, reachSea = Land1362.CREST_HALF + (c - BOTTOM) * Land1362.SEA_SLOPE, reachLand = Land1362.CREST_HALF + (c - BOTTOM) * Land1362.LAND_SLOPE;
        // Rang einer Zelle: landwärts und oben zuerst, mittig stärker (das Wasser sammelt sich in der Mitte der Fehlstelle);
        // die Kante der Ausspülung wandert mit e von der Binnenböschung durch den Deich (Kopferosion)
        double cut = 1.0 - 1.1 * e;
        return solid.melt(p -> {
            double b = (p[0] - x) * s[0] + (p[2] - z) * s[1], a = Math.abs((p[0] - x) * t[0] + (p[2] - z) * t[1]);
            double rank = (reachSea - b) / (reachSea + reachLand) * 0.6 + (p[1] - BOTTOM) / (c - BOTTOM) * 0.4 - a / WIDTH * 0.6;
            return rank > cut;
        });
    }

    /**
     * Einen Schritt dt: Wasserdruck auf die Seeseite, Strömung in der Bresche landwärts, weiter ausspülen, solange das
     * Wasser überläuft.
     */
    public void step(double dt, double sea, double land) {
        if (solid == null) return;
        seaLevel = sea;
        landLevel = land;
        since += dt;
        double head = Math.max(0, sea - Math.max(land, BOTTOM));
        double v = Math.min(5, Math.sqrt(2 * 9.81 * head));
        world.water = (px, pz, tt) -> covers(px, pz, 60) ? Math.max(Math.max(land, BOTTOM), Math.min(sea, (px - x) * s[0] + (pz - z) * s[1] > 0 ? sea : land + (sea - land) * 0.6)) : Double.NaN;
        world.current = (px, pz, out) -> {
            if (!covers(px, pz, 40)) return false;
            out[0] = -s[0] * v * 0.7;
            out[1] = -s[1] * v * 0.7;
            return true;
        };
        // Druck auf die Seeseite (bis zur Krone)
        double rho = 1025, g = 9.81;
        for (int i = 0; i < solid.cells(); i++) {
            if (!solid.attached(i)) continue;
            double fx = 0, fz = 0;
            for (Poly.Face f : solid.cell(i).faces) {
                if (f.tag >= 0) continue;
                double out = f.nx * s[0] + f.nz * s[1];
                if (out < 0.1) continue;
                double[] cc = f.centroid();
                double p = rho * g * Math.max(0, sea - cc[1]);
                double a = f.area();
                fx -= p * a * f.nx;
                fz -= p * a * f.nz;
            }
            if (fx != 0 || fz != 0) solid.load(i, fx, 0, fz);
        }
        // weiter ausspülen, solange es überströmt (in rund 4 min durch den Deich); der durchnässte Klei verliert an Festigkeit
        if (sea > Land1362.CREST - 0.8) {
            eroded = Math.min(1.2, eroded + dt / 240 * (0.5 + Math.max(0, sea - Land1362.CREST + 0.8)));
            erode(eroded);
            soak += dt;
            if (soak >= 1) { soak -= 1; solid.weaken(x, (BOTTOM + Land1362.CREST) / 2, z, WIDTH * 0.7, 0.96); }
        }
        world.step(dt);
    }

    public int attached() { return solid == null ? 0 : solid.attachedCount(); }
    public int pieces() { return world.pieceCount(); }

    /** „Bresche: 64 von 152 Zellen stehen, 23 Stücke · Wasser 2,9 m über der Sohle“ */
    public String describe() {
        return solid == null ? "Deich hält" : String.format(Locale.GERMAN, "Bresche %.0f m: %d von %d Zellen stehen, %d Stücke treiben · seit %.0f s",
                WIDTH, attached(), cellsAtStart, pieces(), since);
    }

    static double len(double a, double b, double c) { return Math.sqrt(a * a + b * b + c * c); }
    static double[] norm(double a, double b, double c) { double l = len(a, b, c); return new double[]{a / l, b / l, c / l}; }
}
