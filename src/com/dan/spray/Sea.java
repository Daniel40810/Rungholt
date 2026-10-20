package com.dan.spray;

/**
 * Woher das Package die Wellen kennt (eigene Form; die App reicht ihr Meer hindurch, eine Demo ihre eigene See).
 * Alle Werte für die aktuelle Zeit.
 */
public interface Sea {
    /** Höhe der Wasseroberfläche über (x, z) in m. */
    double height(double x, double z);

    /** Geschwindigkeit des Wassers an der Oberfläche über (x, z) in m/s: {vx, vy, vz}. */
    double[] velocity(double x, double z);

    /** Ruhiger Wasserstand (Gezeit) in m. */
    double level();
}
