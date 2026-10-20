package com.dan.rungholt.wiring;

/**
 * Zeit der Szene. Gibt: die App ({@code SceneClock}). Nimmt: alle Packages, vor allem {@code tide} (das Datum macht
 * Spring- und Nipptide). Die Laufzeit {@code t} läuft auch bei stehender Uhr weiter (Wellen, Wolken), die Uhrzeit
 * {@code epochMillis} folgt dem Zeitraffer. Für 1362 rechnet die Uhr im proleptischen gregorianischen Kalender.
 */
public interface Clock {
    /** Laufzeit der Szene in s und letzter Schritt in s. */
    double t();
    double dt();

    /** Szenenzeit in ms seit 1970 (UTC); vor 1970 negativ. */
    double epochMillis();

    /** Ort in Grad (Kirche von Rungholt [G]). */
    double latDeg();
    double lonDeg();
}
