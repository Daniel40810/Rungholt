package com.dan.soundscape;

/**
 * Ein Körper, an dem der Wind singt: Stab, Rohr, Draht oder Seil quer zur Strömung. Hinter ihm lösen sich Wirbel im
 * Takt der Strouhal-Zahl ab (f = 0,2 · U / d); das gibt einen Äolston, der mit dem Wind steigt und mit den Böen
 * wandert. Ort in m (für Abstand und Richtung zum Ohr), Durchmesser d in m, gesamte Länge in m (mehrere gleiche Stäbe
 * zählen zusammen), Höhe über dem Wasser für das Windprofil.
 */
public record Aeolian(String name, double x, double y, double z, double diameter, double length) {
    /** Frequenz des Äolstons bei der Anströmung u (m/s). */
    public double frequency(double u) { return Soundscape.STROUHAL * u / diameter; }
}
