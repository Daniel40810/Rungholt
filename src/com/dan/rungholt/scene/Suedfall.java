package com.dan.rungholt.scene;

import com.dan.timber.House;
import com.dan.timber.HouseType;

import java.util.List;

/**
 * Phase 9: die Hallig Südfall im Detail. Auf der Warft (Kuppe NHN +5,5 m bei −1008/0 aus den BAW-Höhen) das
 * Friesenhaus aus Backstein mit Reetdach, First von West nach Ost, und ein kleinerer Stall östlich davon [G]. In der
 * Senke der Warft östlich des Hauses (Grund NHN +3,0 m bei −980/−8) der Fething, der Teich für das Regenwasser; auf
 * der Salzwiese Pütten, aus denen einst Klei für die Warft gestochen wurde [G: Lage gewählt, wo die Wiese eben ist].
 */
public final class Suedfall {
    /** Die Häuser auf der Warft. */
    public static final List<House> HOUSES = List.of(
            new House(HouseType.FRIESENHAUS, -1011, 5.25, 5, 92, 1, 1, 0.55, 1911),
            new House(HouseType.FRIESENHAUS, -1031, 4.55, 14, 92, 0.8, 0.85, 0.7, 1912));

    /**
     * Die Häuser auf den Grund gestellt: die Höhe ist die Mitte zwischen der tiefsten und der höchsten Ecke (die Wände
     * reichen 0,4 m in den Boden), damit auf der Kuppe der Warft keine Seite schwebt.
     */
    public static List<House> placed(java.util.function.DoubleBinaryOperator ground) {
        List<House> out = new java.util.ArrayList<>();
        for (House h : HOUSES) {
            double lo = 1e9, hi = -1e9;
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sz = -1; sz <= 1; sz += 2) {
                    double[] p = h.toWorld(sx * h.type().length / 2, 0, sz * h.type().width / 2);
                    double g = ground.applyAsDouble(p[0], p[2]);
                    lo = Math.min(lo, g); hi = Math.max(hi, g);
                }
            out.add(new House(h.type(), h.x(), Math.min(lo + 0.35, (lo + hi) / 2), h.z(), h.headingDeg(), h.lengthScale(), h.widthScale(), h.state(), h.seed()));
        }
        return out;
    }

    /** Ein Teich: Mitte, halbe Länge, halbe Breite (m), Richtung (Grad von Nord), Spiegel (m NHN). */
    public record Pond(String name, double x, double z, double hx, double hz, double headingDeg, double level) { }

    /** Fething auf der Warft und drei Pütten auf der Salzwiese. */
    public static final List<Pond> PONDS = List.of(
            new Pond("Fething", -980, -7, 6.5, 5.5, 10, 3.55),
            new Pond("Pütte", -860, -120, 11, 5, 75, 2.05),
            new Pond("Pütte", -640, -40, 10, 4.5, 80, 2.20),
            new Pond("Pütte", -880, 92, 9, 4, 60, 2.30));

    private Suedfall() { }
}
