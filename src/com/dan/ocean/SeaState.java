package com.dan.ocean;

import java.util.Locale;

/**
 * Seegang nach Beaufort und Douglas: was bei welcher Windstärke auf offener See zu sehen ist. Die Wellenhöhen sind
 * die wahrscheinlichen signifikanten Höhen H<sub>s</sub> (Mittel des höchsten Drittels), die Schaumbedeckung ist der
 * Anteil der Fläche mit Schaumköpfen und Schaumstreifen. Das Spektrum wird so skaliert, dass H<sub>s</sub> genau
 * dieser Tabelle folgt, und die Schaumschwelle so gewählt, dass die Bedeckung stimmt.
 * <p>
 * Quellen: Beaufort-Skala nach WMO, Douglas-Skala, Schaumbedeckung nach Monahan (Größenordnung, gerundet).
 */
public final class SeaState {
    private SeaState() { }

    public static final String[] NAME = {"Windstille", "Leiser Zug", "Leichte Brise", "Schwache Brise", "Mäßige Brise", "Frische Brise",
            "Starker Wind", "Steifer Wind", "Stürmischer Wind", "Sturm", "Schwerer Sturm", "Orkanartiger Sturm", "Orkan"};
    /** Untere Grenzen der Stufen 1..12 in m/s (WMO). */
    static final double[] LOWER = {0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7};
    /** Mitte jeder Stufe in m/s. */
    public static final double[] MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};
    /** Signifikante Wellenhöhe in m je Stufe (Werkbuch, Abschnitt 4). */
    public static final double[] HS = {0.0, 0.1, 0.2, 0.6, 1.0, 2.0, 3.0, 4.0, 5.5, 7.0, 9.0, 11.5, 14.0};
    /** Douglas-Stufe (gerundet) je Beaufort-Stufe. */
    public static final int[] DOUGLAS = {0, 1, 2, 3, 3, 4, 5, 5, 6, 6, 7, 8, 9};
    public static final String[] DOUGLAS_NAME = {"spiegelglatt", "gekräuselt", "ruhig", "schwach bewegt", "mäßig bewegt", "grob",
            "sehr grob", "hoch", "sehr hoch", "außergewöhnlich schwer"};
    /** Schaumbedeckung 0..1 je Stufe: erste Schaumköpfe bei 3, die Hälfte bei 10, fast alles bei 12. */
    public static final double[] FOAM = {0, 0, 0, 0.005, 0.02, 0.05, 0.10, 0.18, 0.27, 0.37, 0.50, 0.68, 0.85};

    public static int beaufort(double windMs) {
        int b = 0;
        while (b < 12 && windMs >= LOWER[b]) b++;
        return b;
    }

    /** Stufe als Kommazahl (für weiche Übergänge zwischen den Tabellenwerten). */
    public static double beaufortSmooth(double windMs) {
        if (windMs <= MID[0]) return 0;
        for (int b = 0; b < 12; b++)
            if (windMs <= MID[b + 1]) return b + (windMs - MID[b]) / (MID[b + 1] - MID[b]);
        return 12;
    }

    /** Tabellenwert an einer Kommazahl-Stufe, linear dazwischen. */
    static double lerp(double[] table, double bft) {
        if (bft <= 0) return table[0];
        if (bft >= 12) return table[12];
        int i = (int) Math.floor(bft);
        return table[i] + (table[i + 1] - table[i]) * (bft - i);
    }

    public static double significantHeight(double windMs) { return lerp(HS, beaufortSmooth(windMs)); }
    public static double foamCoverage(double windMs) { return lerp(FOAM, beaufortSmooth(windMs)); }

    /**
     * Seit Haithabu (Phase 2): signifikante Höhe einer nicht ausgereiften Windsee nach JONSWAP (Hasselmann u. a. 1973):
     * H_s = 0,0016 · U · √(F/g), in flachem Wasser höchstens 0,6 · Tiefe (Brechen). Bei 1,5 km Streichlänge und Bft 6
     * rund 0,25 m.
     */
    public static double fetchLimitedHeight(double windMs, double fetchM, double depthM) {
        double hs = 0.0016 * Math.max(0, windMs) * Math.sqrt(Math.max(0, fetchM) / 9.81);
        return Math.min(hs, 0.6 * Math.max(0.1, depthM));
    }

    /** „Bft 6 · Starker Wind · Douglas 5 grob“ */
    public static String describe(double windMs) {
        int b = beaufort(windMs);
        return String.format(Locale.GERMAN, "Bft %d · %s · Douglas %d %s", b, NAME[b], DOUGLAS[b], DOUGLAS_NAME[DOUGLAS[b]]);
    }
}
