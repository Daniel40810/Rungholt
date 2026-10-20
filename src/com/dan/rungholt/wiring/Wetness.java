package com.dan.rungholt.wiring;

/**
 * Nässe des Watts (neu in Rungholt). Gibt: {@code mudflat} (Phase 5: Nässe als Zustand je Texel, Trocknung). Nimmt:
 * ground, weathering, soundscape (Knistern), flock (Fressen im feuchten Sand).
 */
public interface Wetness {
    /** Nässe 0 = trocken … 1 = gerade frei gefallen. */
    double wetnessAt(double x, double z);

    /** Wie lange der Ort schon trocken liegt (h), 0 unter Wasser. */
    double dryHoursAt(double x, double z);
}
