package com.dan.road.demo;

import com.dan.road.Ground;
import com.dan.road.Network;
import com.dan.road.WNoise;
import com.dan.road.Way;
import com.dan.road.WayType;

/**
 * Die Landschaft der Vorschau: Hügel, ein Bach von Norden nach Süden, ein Hügel in der Mitte. Darauf
 * vier Rundkurse: außen die Autobahn, innen die Landstraße, daneben ein Feldweg über die Felder und ein
 * Pfad über den Hügel. Autobahn und Straße überqueren den Bach auf Brücken.
 */
final class Landscape {
    static final float HALF = 900;

    /** Der Bach: Mitte bei z, halbe Breite 5 m. */
    static float creekX(double z) { return (float) (-30 + 40 * Math.sin(z / 170.0) + 12 * Math.sin(z / 53.0)); }

    static final Ground BASE = new Ground() {
        @Override public float height(double x, double z) {
            float fx = (float) x, fz = (float) z;
            float h = 28 * WNoise.fbm(fx * 0.0022f + 3, fz * 0.0022f + 7, 4) - 14;
            h += 22 * (float) Math.exp(-((x + 150) * (x + 150) + z * z) / (2 * 95.0 * 95.0));
            // Bachtal
            float d = (float) Math.abs(x - creekX(z));
            float valley = 1 - WNoise.smooth(8, 90, d);
            float lvl = level(z);
            h += (lvl + 1.2f - h) * valley * 0.85f;
            if (d < 7) h = Math.min(h, lvl - 1.2f * (1 - d / 7));
            return h;
        }

        @Override public float water(double x, double z) {
            return Math.abs(x - creekX(z)) < 6 ? level(z) : Float.NaN;
        }
    };

    /** Wasserspiegel des Bachs: fällt nach Süden. */
    static float level(double z) { return (float) (-6 - z * 0.004); }

    static Network network() {
        Network n = new Network();
        n.add(new Way(WayType.motorway(), true, ellipse(0, 0, 650, 480, 16, 0, 0)));
        n.add(new Way(WayType.road(), true, ellipse(-150, 0, 330, 262, 14, 0.12, 18)));
        n.add(new Way(WayType.track(), true, ellipse(390, 0, 150, 250, 10, 0.2, 14)));
        n.add(new Way(WayType.path(), true, ellipse(-150, 10, 118, 88, 18, 0.28, 9)));
        return n;
    }

    /** Punkte auf einer Ellipse, mit Wellen der Stärke wob. */
    private static double[] ellipse(double cx, double cz, double a, double b, int n, double wob, double amp) {
        double[] p = new double[2 * n];
        for (int i = 0; i < n; i++) {
            double t = i * 2 * Math.PI / n;
            double k = 1 + wob * Math.sin(3 * t + 1.3) + amp * 0.001 * Math.sin(7 * t);
            p[2 * i] = cx + a * Math.cos(t) * k;
            p[2 * i + 1] = cz + b * Math.sin(t) * k;
        }
        return p;
    }
}
