package com.dan.buoyancy;

/** Ein Licht in der Welt mit seiner Stärke je Farbe in Richtung des Betrachters (cd, linear rgb). */
public record LightPoint(String name, double x, double y, double z, double r, double g, double b) {
    /** Lichtstärke als Helligkeit (cd). */
    public double candela() { return 0.2126 * r + 0.7152 * g + 0.0722 * b; }
}
