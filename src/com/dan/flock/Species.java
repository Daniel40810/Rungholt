package com.dan.flock;

/**
 * Die Vogelarten des Schwarms (seit Haithabu Phase 13). Je Art: Maße für das Netz, Flug (Geschwindigkeit gegen die
 * Luft, Sinken im Gleiten, Steigen mit Schlag, Schlagfrequenz, wie viel sie gleitet), was sie kann (schwimmen, am Boden
 * gehen, sitzen, tauchen), Fluchtdistanz und ob sie nur im Sommer da ist. Werte nach Bestimmungsbüchern gerundet [G].
 * Seit Glacier Phase 10 dazu der Andenkondor (nach McGahan 1973 und Shepard &amp; Lambertucci 2013 gerundet [G]).
 */
public enum Species {
    //            Name             Länge Spannw. vMin vReise vMax  Sinken Steigen  Hz  Gleiten Schwimmt Geht Sitzt Taucht Flucht Sommer
    MOEWE("Silbermöwe",          0.60, 1.40, 8.0, 10.5, 17.0, 0.6, 2.0, 3.1, 0.7, true, false, true, false, 6, false),
    KRAEHE("Nebelkrähe",         0.47, 0.98, 7.0, 9.5, 14.0, 0.9, 2.2, 4.2, 0.25, false, true, true, false, 18, false),
    SCHWALBE("Rauchschwalbe",    0.19, 0.33, 6.0, 9.0, 15.0, 0.5, 2.5, 8.0, 0.5, false, false, false, false, 2, true),
    ENTE("Stockente",            0.58, 0.90, 12.0, 16.0, 22.0, 1.4, 3.0, 5.5, 0.0, true, false, false, false, 25, false),
    SCHWAN("Höckerschwan",       1.50, 2.30, 14.0, 18.0, 23.0, 1.2, 1.6, 2.6, 0.05, true, false, false, false, 12, false),
    KORMORAN("Kormoran",         0.85, 1.40, 11.0, 15.0, 20.0, 1.2, 2.2, 3.6, 0.1, true, false, true, true, 30, false),
    /** Seit Glacier Phase 10: kreist in der Thermik über den Hängen, schlägt kaum je (nur beim Abflug). */
    KONDOR("Andenkondor",        1.20, 3.00, 9.0, 13.0, 25.0, 0.9, 0.8, 1.2, 0.99, false, false, true, false, 40, false);

    public final String label;
    /** Länge Schnabel bis Schwanz und Spannweite (m). */
    public final double length, span;
    /** Geschwindigkeit gegen die Luft (m/s): kleinste, Reise, größte; Sinken im Gleitflug, Steigen mit Schlag (m/s). */
    public final double vMin, vCruise, vMax, sink, climb;
    /** Schlagfrequenz (Hz) und Anteil, den sie gleitet (0 = schlägt immer). */
    public final double flapHz, glide;
    public final boolean swims, walks, perches, dives;
    /** Ab diesem Abstand (m) zu einem Störer weicht sie aus oder fliegt auf. */
    public final double flightDistance;
    /** Nur von Mitte April bis Mitte September da (Zugvogel). */
    public final boolean summer;

    Species(String label, double length, double span, double vMin, double vCruise, double vMax, double sink, double climb, double flapHz, double glide,
            boolean swims, boolean walks, boolean perches, boolean dives, double flightDistance, boolean summer) {
        this.label = label;
        this.length = length;
        this.span = span;
        this.vMin = vMin;
        this.vCruise = vCruise;
        this.vMax = vMax;
        this.sink = sink;
        this.climb = climb;
        this.flapHz = flapHz;
        this.glide = glide;
        this.swims = swims;
        this.walks = walks;
        this.perches = perches;
        this.dives = dives;
        this.flightDistance = flightDistance;
        this.summer = summer;
    }

    /** Maßstab gegen die Möwe (das Netz ist für 0,6 m Länge gebaut). */
    public double scale() { return length / MOEWE.length; }

    /** Bit für Masken (etwa {@link Flock.Perch#mask}). */
    public int bit() { return 1 << ordinal(); }

    /** Ist die Art am Tag doy (1..365) im Land? Schwalben von Mitte April bis Mitte September. */
    public boolean present(int doy) { return !summer || doy >= 105 && doy <= 258; }
}
