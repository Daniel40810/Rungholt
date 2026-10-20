package com.dan.rungholt.scene;

import com.dan.figure.Hike;

/**
 * Phase 9: die geführte Wattwanderung von Nordstrand nach Südfall. Start am Deichfuß westlich von Fuhlehörn
 * (4692/−1404, wo Ansicht 1 steht), über die hohen Watten (im Mittel NHN +0,8 m, nirgends unter −0,3 m nach den
 * BAW-Höhen) an der Kirche von Rungholt vorbei, auf das Vorland von Südfall und bis an den Fuß der Warft: 5,8 km [G:
 * der Weg der Führer ist nicht veröffentlicht; dieser hält sich an die höchsten Watten]. Halte: Wattwurm, Herzmuscheln
 * und Schlick, Rungholt (an der Stelle, die dem Grundriss der Kirche am nächsten liegt), Salzwiese von Südfall.
 * Losgehen etwa 1,5 h vor Niedrigwasser, an der Warft rund 2 h später.
 */
public final class Wattwanderung {
    /** Der Weg (x, z in m). */
    public static final double[][] ROUTE = {{4692, -1404}, {4300, -1330}, {3500, -1150}, {2600, -1000}, {1700, -900}, {900, -700},
            {300, -480}, {-380, -200}, {-700, -80}, {-956, -12}};
    /** Wie viele Personen außer dem Führer [G]. */
    public static final int WALKERS = 15;
    /** Losgehen so viele Stunden vor Niedrigwasser [G]. */
    public static final double START_BEFORE_LW = 1.5;

    private Wattwanderung() { }

    /** Eine Gruppe für den Monat; Halte am Weg. */
    public static Hike create(int month, long seed) {
        Hike h = new Hike(ROUTE, WALKERS, month, seed);
        h.stops.add(new Hike.Stop(600, 240, "Wattwurm"));
        h.stops.add(new Hike.Stop(2400, 200, "Herzmuscheln und Schlick"));
        h.stops.add(new Hike.Stop(nearest(h, 0, 0), 360, "Rungholt: hier stand die Kirche"));
        h.stops.add(new Hike.Stop(h.length() - 380, 180, "Salzwiese von Südfall"));
        return h;
    }

    /** Strecke des Wegs, die dem Punkt (x, z) am nächsten liegt. */
    public static double nearest(Hike h, double x, double z) {
        double best = 0, bd = Double.MAX_VALUE;
        for (double s = 0; s <= h.length(); s += 5) {
            double[] p = h.at(s, 0);
            double d = Math.hypot(p[0] - x, p[1] - z);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }
}
