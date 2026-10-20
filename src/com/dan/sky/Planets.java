package com.dan.sky;

/**
 * Die fünf Planeten, die man mit bloßem Auge sieht, aus den Bahnelementen von Standish (JPL, „Keplerian Elements for
 * Approximate Positions of the Major Planets“, Tafel 1, gültig 1800 bis 2050, Fehler unter einer Bogenminute für die
 * inneren Planeten, wenige Bogenminuten für Jupiter und Saturn). Das reicht für einen Lichtpunkt am Himmel.
 * Helligkeiten nach Meeus, Kapitel 41. Die Lichtlaufzeit fehlt (bei Jupiter rund 40 Minuten, also 0,1°).
 */
public final class Planets {
    private Planets() { }

    /** Ein Planet am Himmel: Name, Richtung (äquatorial J2000, Einheitsvektor), Helligkeit in mag, Abstand in AE, Farbe. */
    public record Planet(String name, double[] equatorialJ2000, double magnitude, double distanceAu, double[] color) { }

    public static final String[] NAMES = {"Merkur", "Venus", "Mars", "Jupiter", "Saturn"};
    /** a, e, I, L, ϖ (Länge des Perihels), Ω, je mit Änderung je Jahrhundert; Index 2 ist das Erde-Mond-Baryzentrum. */
    static final double[][] EL = {
            {0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
                    0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081},
            {0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
                    0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418},
            {1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
                    0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0},
            {1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
                    0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343},
            {5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
                    -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106},
            {9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
                    -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794}};
    /** Element-Index je Planet in {@link #NAMES}. */
    static final int[] INDEX = {0, 1, 3, 4, 5};
    /** Farbe am Himmel (Venus weiß, Mars rötlich, Jupiter cremeweiß, Saturn gelblich). */
    static final double[][] COLOR = {{1.0, 0.92, 0.85}, {1.0, 0.98, 0.93}, {1.0, 0.70, 0.52}, {1.0, 0.95, 0.86}, {1.0, 0.90, 0.72}};

    /** Heliozentrische Position (ekliptikal J2000, AE) des Elementsatzes k für jde. */
    public static double[] heliocentric(int k, double jde) {
        double t = (jde - 2451545.0) / 36525.0;
        double[] e = EL[k];
        double a = e[0] + e[6] * t, ecc = e[1] + e[7] * t, inc = Math.toRadians(e[2] + e[8] * t);
        double L = e[3] + e[9] * t, peri = e[4] + e[10] * t, node = e[5] + e[11] * t;
        double w = Math.toRadians(peri - node), om = Math.toRadians(node);
        double M = Math.toRadians(((L - peri) % 360 + 540) % 360 - 180);
        double E = M + ecc * Math.sin(M);
        for (int i = 0; i < 12; i++) E -= (E - ecc * Math.sin(E) - M) / (1 - ecc * Math.cos(E));
        double xp = a * (Math.cos(E) - ecc), yp = a * Math.sqrt(1 - ecc * ecc) * Math.sin(E);
        double cw = Math.cos(w), sw = Math.sin(w), co = Math.cos(om), so = Math.sin(om), ci = Math.cos(inc), si = Math.sin(inc);
        return new double[]{
                (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
                (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
                (sw * si) * xp + (cw * si) * yp};
    }

    /** Ekliptikal J2000 nach äquatorial J2000. */
    static double[] eclToEq(double[] v) {
        double e = Math.toRadians(Celestial.OBLIQUITY_J2000), c = Math.cos(e), s = Math.sin(e);
        return new double[]{v[0], c * v[1] - s * v[2], s * v[1] + c * v[2]};
    }

    /** Alle fünf Planeten für jde (dynamische Zeit). */
    public static Planet[] at(double jde) {
        double[] earth = heliocentric(2, jde);
        double rE = norm(earth);
        Planet[] out = new Planet[NAMES.length];
        for (int p = 0; p < NAMES.length; p++) {
            double[] h = heliocentric(INDEX[p], jde);
            double[] g = {h[0] - earth[0], h[1] - earth[1], h[2] - earth[2]};
            double delta = norm(g), r = norm(h);
            double cosI = (r * r + delta * delta - rE * rE) / (2 * r * delta);
            double i = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosI))));
            double[] eq = eclToEq(new double[]{g[0] / delta, g[1] / delta, g[2] / delta});
            out[p] = new Planet(NAMES[p], eq, magnitude(p, r, delta, i), delta, COLOR[p]);
        }
        return out;
    }

    /** Scheinbare Helligkeit nach Meeus 41 (Phasenwinkel i in Grad); Saturn ohne Ring-Neigung. */
    static double magnitude(int p, double r, double delta, double i) {
        double l = 5 * Math.log10(r * delta);
        return switch (p) {
            case 0 -> -0.42 + l + 0.0380 * i - 0.000273 * i * i + 0.000002 * i * i * i;
            case 1 -> -4.40 + l + 0.0009 * i + 0.000239 * i * i - 0.00000065 * i * i * i;
            case 2 -> -1.52 + l + 0.016 * i;
            case 3 -> -9.40 + l + 0.005 * i;
            default -> -8.88 + l + 0.044 * i;
        };
    }

    static double norm(double[] v) { return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }
}
