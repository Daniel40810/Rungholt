package com.dan.ocean;

/**
 * Seit Haithabu Phase 9: der Umriss eines Schiffsrumpfs in der Wasserlinie, damit das Wasser nicht durch offene Boote
 * schneidet. Mitte (x, z), Kurs in Grad von Nord im Uhrzeigersinn, halbe Länge und halbe Breite in der Wasserlinie (m), Völligkeit.
 * Die Form ist eine spitze Linse: Halbbreite an der Stelle u = s / halfLength gleich
 * {@code halfBeam · (1 − |u|^fullness)^0,8}: Völligkeit 2 für schlanke Rümpfe, mehr für bauchige (Haithabu Phase 9: Langschiff 2, Knorr gut 3). Innen zeichnet
 * {@code surface.frag} kein Wasser, außen einen schmalen Saum aus Schaum (Bugwelle).
 */
public record HullMask(double x, double z, double headingDeg, double halfLength, double halfBeam, double fullness) {
    /** Mit der Völligkeit 2 (schlanke Rümpfe). */
    public HullMask(double x, double z, double headingDeg, double halfLength, double halfBeam) { this(x, z, headingDeg, halfLength, halfBeam, 2); }

    /** Halbbreite an der Stelle u (−1 … 1). */
    public double halfWidth(double u) {
        double a = Math.abs(u);
        return a >= 1 ? 0 : halfBeam * Math.pow(1 - Math.pow(a, fullness), 0.8);
    }

    /** Etwa der Abstand von (px, pz) zum Umriss (m, negativ innen); genau quer zur Mitte, an den Steven grob. */
    public double distance(double px, double pz) {
        double h = Math.toRadians(headingDeg), fx = Math.sin(h), fz = -Math.cos(h);
        double dx = px - x, dz = pz - z;
        double s = dx * fx + dz * fz, q = -dx * fz + dz * fx;
        double u = s / halfLength;
        if (Math.abs(u) >= 1) return Math.hypot(Math.abs(s) - halfLength, q);
        return Math.abs(q) - halfWidth(u);
    }
}
