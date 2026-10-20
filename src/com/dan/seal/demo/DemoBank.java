package com.dan.seal.demo;

import com.dan.seal.Colony;

/**
 * Eine Sandbank mit Priel für Demo und Prüfung: die Bank als flache Kuppe (oben NHN +0,5 m, 340 × 240 m), östlich ein
 * Priel bis −4 m, ringsum Watt bei −2,3 m; das Wasser als Spiegel einer Tide mit 12 h 25 min (MThw +1,6 m, MTnw −1,9 m),
 * von Hand festzuhalten. Ohne Pfützen und ohne Laufzeit: das macht in Rungholt {@code com.dan.tide}.
 */
public final class DemoBank implements Colony.World {
    /** Pegel von Hand (m); NaN = aus der Tide. */
    public double hand = Double.NaN;
    /** Szenenzeit (s) seit Hochwasser. */
    public double t;

    public static final double PERIOD = 12.42 * 3600;

    @Override public double ground(double x, double z) {
        double bank = 0.5 - 2.6 * ((x / 170) * (x / 170) + (z / 120) * (z / 120));
        double flat = -2.3;
        double g = Math.max(bank, flat);
        double ch = -4.2 + Math.pow((x - 230) / 32, 2);   // Priel östlich, von Nord nach Süd
        // feine Wellen im Sand (der Grund ist nicht eben)
        g += 0.04 * Math.sin(x * 0.11) * Math.cos(z * 0.13);
        return Math.min(g, ch);
    }

    /** Pegel jetzt (m). */
    public double level() { return Double.isNaN(hand) ? -0.15 + 1.75 * Math.cos(2 * Math.PI * t / PERIOD) : hand; }

    @Override public double water(double x, double z) {
        double l = level(), g = ground(x, z);
        return l > g ? l : Double.NaN;
    }

    /** Die Bank für die Kolonie. */
    public static Colony.Bank bank() { return new Colony.Bank(0, 0, 175); }
}
