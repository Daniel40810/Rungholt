package com.dan.buoyancy;

/**
 * Die Wasseroberfläche, auf der die Körper schwimmen (eigene kleine Schnittstelle, Paketvertrag Punkt 4). Die App
 * gibt hier ihr Meer ({@code heightAt}, {@code velocityAt}) hinein, die Demo eine schlichte See aus Wellenzügen.
 */
public interface Sea {
    /** Höhe der Oberfläche über dem Bezugsniveau am Ort (m), mit Wasserstand. */
    double height(double x, double z);

    /** Geschwindigkeit des Wassers an der Oberfläche (m/s): x, y, z. */
    default double[] velocity(double x, double z) { return new double[3]; }

    /** Ruhewasserstand (m). */
    double level();

    /** Höhe des Grunds am Ort (m; seit Glacier Phase 9 für Eisberge, die stranden). Ohne Grund weit unten. */
    default double bed(double x, double z) { return -1e9; }
}
