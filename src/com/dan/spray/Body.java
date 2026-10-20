package com.dan.spray;

/**
 * Ein fester Körper, an dem Wellen anschlagen: ein senkrechter Kegelstumpf um (x, z) von y0 bis y1 mit den Halbmessern
 * r0 unten und r1 oben (ein Zylinder hat r0 = r1). Turmsockel, Pfahl, Fels, eine Mole aus mehreren Stümpfen. Die
 * eigene Form des Packages; die App übersetzt ihre Collider hierher.
 */
public record Body(double x, double z, double y0, double y1, double r0, double r1) {
    public Body {
        if (!(r0 > 0) || !(r1 > 0) || !(y1 > y0)) throw new IllegalArgumentException("Körper braucht r0, r1 > 0 und y1 > y0");
    }

    /** Senkrechter Zylinder. */
    public static Body cylinder(double x, double z, double r, double y0, double y1) { return new Body(x, z, y0, y1, r, r); }

    /** Halbmesser auf Höhe y (außerhalb von y0..y1 der Rand). */
    public double radiusAt(double y) {
        double f = Math.max(0, Math.min(1, (y - y0) / (y1 - y0)));
        return r0 + (r1 - r0) * f;
    }

    /** Waagerechter Abstand von der Wand auf Höhe y (negativ innen); NaN über oder unter dem Körper. */
    public double distance(double px, double py, double pz) {
        if (py < y0 || py > y1) return Double.NaN;
        return Math.hypot(px - x, pz - z) - radiusAt(py);
    }
}
