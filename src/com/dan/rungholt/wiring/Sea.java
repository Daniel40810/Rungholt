package com.dan.rungholt.wiring;

/**
 * Die Höhe des Wassers als Summe. Gibt: die App (Phase 12, in der Mandränke {@code tide.levelAt + wave.heightAt}; sonst
 * der Pegel plus die Windsee aus {@code ocean}). Nimmt: ocean, buoyancy (Boot, Kogge, Treibgut), spray, fracture (Last
 * auf dem Deich, Auftrieb der Häuser).
 */
@FunctionalInterface
public interface Sea {
    /** Höhe der Oberfläche in m über NHN bei (x, z) zur Laufzeit t (s). */
    double heightAt(double x, double z, double t);

    /** Geschwindigkeit des Wassers an der Oberfläche (m/s), {vx, vz}; ohne Angabe ruhend. */
    default double[] velocityAt(double x, double z, double t) { return new double[]{0, 0}; }
}
