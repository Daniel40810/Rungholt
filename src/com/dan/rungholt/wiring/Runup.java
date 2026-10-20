package com.dan.rungholt.wiring;

/**
 * Eine Welle läuft am Ufer auf. Gibt: {@code wave.onRunup} (Phase 12: Brecher am Deich). Nimmt: weathering (nasser
 * Deich), soundscape (Brecher), die App (Wasser über der Krone als Last für fracture).
 */
@FunctionalInterface
public interface Runup {
    /** Ort am Ufer (m), Auflaufhöhe über dem Ruhespiegel (m), Laufzeit (s). */
    void runup(double x, double z, double heightM, double t);
}
