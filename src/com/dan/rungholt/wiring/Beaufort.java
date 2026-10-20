package com.dan.rungholt.wiring;

/** Beaufort-Skala nach WMO: Grenzen in m/s und deutsche Bezeichnungen (aus Lighthouse). */
public final class Beaufort {
    private Beaufort() { }

    /** Untere Grenze jeder Stufe 1..12 in m/s. */
    static final double[] LOWER = {0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7};
    public static final String[] NAME = {"Windstille", "Leiser Zug", "Leichte Brise", "Schwache Brise", "Mäßige Brise", "Frische Brise",
            "Starker Wind", "Steifer Wind", "Stürmischer Wind", "Sturm", "Schwerer Sturm", "Orkanartiger Sturm", "Orkan"};

    public static int of(double ms) {
        int b = 0;
        while (b < 12 && ms >= LOWER[b]) b++;
        return b;
    }

    /** Mitte einer Stufe in m/s (für Presets {@code beaufort(n)}). */
    public static double mid(int bft) {
        if (bft <= 0) return 0.1;
        if (bft >= 12) return 35;
        return (LOWER[bft - 1] + LOWER[bft]) / 2;
    }
}
