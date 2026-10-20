package com.dan.rungholt.wiring;

/**
 * Strömung (neu in Rungholt). Gibt: {@code tide} (Phase 6: aus dem Gefälle der Laufzeit und dem Vorzeichen der Tide).
 * Nimmt: ocean (Kräuselung, Schaum, Trübung), buoyancy, mudflat (Rippel quer zur letzten Strömung).
 */
@FunctionalInterface
public interface Current {
    /** Geschwindigkeit an der Oberfläche (m/s), {vx, vz}. */
    double[] velocityAt(double x, double z);
}
