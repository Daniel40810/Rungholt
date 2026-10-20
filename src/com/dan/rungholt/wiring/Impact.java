package com.dan.rungholt.wiring;

/**
 * Ein Einschlag eines Körpers ins Wasser oder auf den Boden. Gibt: {@code fracture.onImpact} (Phase 12: Stücke des
 * Deichs, Häuser, die treiben). Nimmt: wave (Quelle), spray (Schwall), soundscape (Aufschlag), mudflat (Spuren).
 */
@FunctionalInterface
public interface Impact {
    /** Ort (m), verdrängtes Volumen in diesem Schritt (m³), Tempo senkrecht zur Fläche (m/s), ob ins Wasser. */
    void impact(double x, double y, double z, double volumeM3, double speed, boolean water);
}
