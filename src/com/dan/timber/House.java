package com.dan.timber;

/**
 * Ein Haus als Datensatz: Typ, Lage (x, Bodenhöhe y, z in der Szene; y nach oben, Nord = −z), Richtung des Firsts in
 * Grad von Nord im Uhrzeigersinn, Streckung von Länge und Breite (0,85 bis 1,15 macht aus einem Typ viele Häuser),
 * Zustand 0..1 (0 neu, 1 alt: graues, bemoostes Reet, dunkles Holz) und eine Zufallszahl für die Färbung.
 */
public record House(HouseType type, double x, double y, double z, double headingDeg, double lengthScale, double widthScale, double state, long seed) {

    public House {
        lengthScale = Math.max(0.6, Math.min(1.5, lengthScale));
        widthScale = Math.max(0.6, Math.min(1.5, widthScale));
        state = Math.max(0, Math.min(1, state));
    }

    /** Ein Haus ohne Streckung, mittlerer Zustand. */
    public static House of(HouseType t, double x, double y, double z, double headingDeg) {
        return new House(t, x, y, z, headingDeg, 1, 1, 0.4, 1);
    }

    /** Richtung des Firsts (örtlich +x) und quer dazu (örtlich +z) als Einheitsvektoren der Szene {x, z}. */
    public double[] axisX() { double h = Math.toRadians(headingDeg); return new double[]{Math.sin(h), -Math.cos(h)}; }
    public double[] axisZ() { double h = Math.toRadians(headingDeg); return new double[]{Math.cos(h), Math.sin(h)}; }

    /** Örtlicher Punkt (mit Streckung) in die Szene. */
    public double[] toWorld(double lx, double ly, double lz) {
        double[] ax = axisX(), az = axisZ();
        double sx = lx * lengthScale, sz = lz * widthScale;
        return new double[]{x + ax[0] * sx + az[0] * sz, y + ly, z + ax[1] * sx + az[1] * sz};
    }

    /** Länge und Breite mit Streckung (m). */
    public double length() { return type.length * lengthScale; }
    public double width() { return type.width * widthScale; }

    /** Radius eines Kreises um das ganze Haus mit Dachüberstand (m), für Sichtprüfung und Abstände. */
    public double radius() {
        double l = type.outerHalfLength() + (length() - type.length) / 2, w = type.outerHalfWidth() + (width() - type.width) / 2;
        return Math.sqrt(l * l + w * w);
    }
}
