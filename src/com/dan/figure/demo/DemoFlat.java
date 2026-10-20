package com.dan.figure.demo;

import com.dan.figure.Hike;

/**
 * Ein Watt zum Probieren: eine flache Sandfläche (leichte Wellen), ein Schlickfeld bei x 300 bis 450 m und ein Priel
 * quer bei x 900 bis 960 m, dessen Wasser mit {@link #level} steigt. Der Weg der Demo läuft von (0, 0) nach Osten.
 */
public final class DemoFlat implements Hike.World {
    /** Wasserspiegel (m) im Priel; der Grund dort liegt bei −1,2 m, sonst um 0. */
    public double level = -1.5;

    @Override public double ground(double x, double z) {
        double g = 0.15 * Math.sin(x * 0.006) * Math.cos(z * 0.004) + 0.05 * Math.sin(x * 0.05 + z * 0.03);
        double c = Math.max(0, 1 - Math.abs(x - 930) / 30);
        return g - 1.3 * c * c * (3 - 2 * c);
    }

    @Override public double depth(double x, double z) { return Math.max(0, level - ground(x, z)); }

    @Override public double mud(double x, double z) { return x > 300 && x < 450 ? 0.8 : 0.1; }

    /** Der Weg: 1,6 km nach Osten mit einem Bogen, Halte am Schlick (Wattwurm) und vor dem Priel. */
    public static double[][] route() {
        return new double[][]{{0, 0}, {250, 30}, {500, 10}, {800, -40}, {1100, -20}, {1600, 40}};
    }
}
