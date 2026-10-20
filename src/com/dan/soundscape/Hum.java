package com.dan.soundscape;

/**
 * Eine dauernde Schallquelle mit Grundton, etwa der Diesel eines Schiffs: Zündfrequenz (Hz), Pegel in 1 m (dB),
 * Ort. Die App setzt den Ort je Bild. {@link #aboard}: Das Ohr ist an Bord (Steuerhaus), dann klingt die Maschine
 * gleichmäßig durch den Rumpf mit {@link #aboardDb}, ohne Abstand.
 */
public final class Hum {
    public final String name;
    public volatile double x, y, z;
    public volatile double firingHz = 37.5, levelDb = 112, aboardDb = 68;
    public volatile boolean aboard;

    public Hum(String name, double x, double y, double z) { this.name = name; this.x = x; this.y = y; this.z = z; }

    /** Viertakt-Diesel: Zündfrequenz = Drehzahl / 60 · Zylinder / 2. */
    public static double firing(double rpm, int cylinders) { return rpm / 60 * cylinders / 2; }
}
