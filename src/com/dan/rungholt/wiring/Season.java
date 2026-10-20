package com.dan.rungholt.wiring;

/**
 * Die Jahreszeit als Zahlen. Gibt: {@code season} mit dem Klima „Nordfriesland“ [G] (Phase 9). Nimmt: flock (Arten,
 * Zug, Ringelgänse im Winter), seal (Heuler im Juni und Juli), reed (Queller und Andel), forest, weather, mudflat
 * (Trocknung nach Temperatur).
 */
public interface Season {
    /** Anteil des Laubs am Baum 0..1 und seine Färbung 0 = grün … 1 = braun. */
    double foliage();
    double autumn();

    /** Schneehöhe in m auf ebenem Boden (Deich, Warft) und Eis am Ufer (m vom Ufer, 0 = offen; Eiswinter im Ideenspeicher). */
    double snowDepth();
    double iceReach();

    /** Lufttemperatur in °C (für die Trocknung des Watts) und ob Brutzeit/Heuler-Zeit ist. */
    default double temperature() { return 10; }
    default boolean pupping() { return false; }
}
