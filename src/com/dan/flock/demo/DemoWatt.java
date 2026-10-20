package com.dan.flock.demo;

import com.dan.flock.Waders;

/**
 * Ein Stück Watt für Demo und Prüfung der Wattvögel: eine schiefe Ebene von NHN −2,4 m im Westen bis +0,9 m im Osten
 * (1,4 km), darin eine Mulde mit Schlick, im Osten eine Salzwiese auf +2 m als Rastplatz und Weide; der Wasserspiegel
 * folgt einer Tide mit 12 h 25 min (MThw +1,6 m, MTnw −1,9 m), ab t = 0 Hochwasser.
 */
public final class DemoWatt implements Waders.World {
    public double t;
    public static final double PERIOD = 12.42 * 3600;

    @Override public double ground(double x, double z) {
        if (x > 700) return 2.0 + 0.02 * Math.sin(z * 0.05);
        return -2.4 + (x + 700) / 1400 * 3.3 + 0.05 * Math.sin(x * 0.03) * Math.cos(z * 0.021) - 0.4 * Math.exp(-((x + 100) * (x + 100) + (z - 250) * (z - 250)) / 30000.0);
    }

    public double level() { return -0.15 + 1.75 * Math.cos(2 * Math.PI * t / PERIOD); }

    @Override public double depth(double x, double z) { return Math.max(0, level() - ground(x, z)); }

    @Override public double mud(double x, double z) { return Math.exp(-((x + 100) * (x + 100) + (z - 250) * (z - 250)) / 30000.0); }

    @Override public boolean marsh(double x, double z) { return x > 700; }

    /** Hochwasser bei t = 0, PERIOD, 2·PERIOD … */
    @Override public double highWaterHours() { return (t - Math.rint(t / PERIOD) * PERIOD) / 3600; }

    /** Eine Kolonie von Wattvögeln auf diesem Watt. */
    public static Waders waders(DemoWatt w, int month, long seed) {
        Waders v = new Waders();
        v.seed = seed;
        v.world = w;
        v.month = month;
        v.revier.add(new Waders.Area(-100, 0, 750));
        v.roosts.add(new Waders.Area(800, -150, 70));
        v.roosts.add(new Waders.Area(820, 200, 60));
        v.pastures.add(new Waders.Area(850, 0, 120));
        return v;
    }
}
