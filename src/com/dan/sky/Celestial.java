package com.dan.sky;

import com.dan.sky.astro.FAstroTime;

/**
 * Himmelskoordinaten und ihre Wege in die Szene: Präzession von J2000 auf das Datum (Meeus 21.4), Übergang vom
 * Äquator des Datums in den Horizont des Orts, Richtungsvektoren der Szene (x Ost, y oben, z Süd) und die
 * Strahlenbrechung an der Kimm.
 * <p>
 * Vektoren am Himmel: äquatorial (x zum Frühlingspunkt, z zum Himmelsnordpol), Matrizen zeilenweise als double[9].
 */
public final class Celestial {
    private Celestial() { }

    /** Schiefe der Ekliptik zu J2000 in Grad. */
    public static final double OBLIQUITY_J2000 = 23.4392911;

    /** Einheitsvektor aus Rektaszension und Deklination in Grad. */
    public static double[] equatorial(double raDeg, double decDeg) {
        double a = Math.toRadians(raDeg), d = Math.toRadians(decDeg);
        return new double[]{Math.cos(d) * Math.cos(a), Math.cos(d) * Math.sin(a), Math.sin(d)};
    }

    /** Rektaszension und Deklination in Grad aus einem Vektor. */
    public static double[] raDec(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double ra = Math.toDegrees(Math.atan2(v[1], v[0]));
        return new double[]{ra < 0 ? ra + 360 : ra, Math.toDegrees(Math.asin(v[2] / n))};
    }

    /**
     * Präzession von J2000 auf das Datum jde nach Meeus, Formel 21.4 (ζ, z, θ). Weil die Drehung linear ist, wird sie
     * auf die drei Basisvektoren angewandt; das Ergebnis ist die Matrix.
     */
    public static double[] precession(double jde) {
        double t = (jde - 2451545.0) / 36525.0;
        double zeta = Math.toRadians((2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600);
        double z = Math.toRadians((2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600);
        double theta = Math.toRadians((2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600);
        double[] m = new double[9];
        double[][] basis = {{0, 0}, {90, 0}, {0, 90}};
        for (int col = 0; col < 3; col++) {
            double[] p = precess(basis[col][0], basis[col][1], zeta, z, theta);
            double[] v = equatorial(p[0], p[1]);
            m[col] = v[0];
            m[3 + col] = v[1];
            m[6 + col] = v[2];
        }
        return m;
    }

    /** Meeus 21.4 für eine Richtung (Grad), Winkel im Bogenmaß; Ergebnis RA, Dec in Grad. */
    static double[] precess(double raDeg, double decDeg, double zeta, double z, double theta) {
        double a0 = Math.toRadians(raDeg), d0 = Math.toRadians(decDeg);
        double A = Math.cos(d0) * Math.sin(a0 + zeta);
        double B = Math.cos(theta) * Math.cos(d0) * Math.cos(a0 + zeta) - Math.sin(theta) * Math.sin(d0);
        double C = Math.sin(theta) * Math.cos(d0) * Math.cos(a0 + zeta) + Math.cos(theta) * Math.sin(d0);
        double a = Math.atan2(A, B) + z, d = Math.asin(Math.max(-1, Math.min(1, C)));
        double ra = Math.toDegrees(a) % 360;
        return new double[]{ra < 0 ? ra + 360 : ra, Math.toDegrees(d)};
    }

    /**
     * Vom Äquator des Datums in die Szene (x Ost, y oben, z Süd) für die Ortssternzeit lst und die Breite lat (Grad).
     * Stundenwinkel H = lst − α; x = −cos δ sin H, y = Höhe, z = −(Nordkomponente).
     */
    public static double[] horizon(double lstDeg, double latDeg) {
        double s = Math.sin(Math.toRadians(lstDeg)), c = Math.cos(Math.toRadians(lstDeg));
        double sp = Math.sin(Math.toRadians(latDeg)), cp = Math.cos(Math.toRadians(latDeg));
        return new double[]{
                -s, c, 0,
                cp * c, cp * s, sp,
                sp * c, sp * s, -cp};
    }

    public static double[] mul(double[] a, double[] b) {
        double[] m = new double[9];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                m[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        return m;
    }

    public static double[] apply(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    /** Ortssternzeit (scheinbar) in Grad. */
    public static double localSiderealTime(double jdUt, double lonDeg) {
        double t = (FAstroTime.apparentSiderealTime(jdUt) + lonDeg) % 360;
        return t < 0 ? t + 360 : t;
    }

    /** Höhe und Azimut (Grad, Azimut von Nord über Ost) eines Szenenvektors. */
    public static double[] altAz(double[] d) {
        double n = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double az = Math.toDegrees(Math.atan2(d[0], -d[2]));
        return new double[]{Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1] / n)))), az < 0 ? az + 360 : az};
    }

    /** Szenenvektor aus Höhe und Azimut in Grad. */
    public static double[] direction(double altDeg, double azDeg) {
        double e = Math.toRadians(altDeg), a = Math.toRadians(azDeg);
        return new double[]{Math.cos(e) * Math.sin(a), Math.sin(e), -Math.cos(e) * Math.cos(a)};
    }

    /**
     * Strahlenbrechung in Grad für die wahre Höhe h in Grad (Sæmundsson, Meeus 16.4, 10 °C und 1010 hPa). Unter −2°
     * wird sie festgehalten; dort sieht man nichts mehr.
     */
    public static double refraction(double hDeg) {
        double h = Math.max(-2, hDeg);
        return 1.02 / Math.tan(Math.toRadians(h + 10.3 / (h + 5.11))) / 60;
    }

    /** Umkehrung: Brechung für die scheinbare Höhe (Bennett, Meeus 16.3). */
    public static double refractionApparent(double hDeg) {
        double h = Math.max(-1.5, hDeg);
        return 1 / Math.tan(Math.toRadians(h + 7.31 / (h + 4.4))) / 60;
    }

    /** Hebt einen Szenenvektor um die Strahlenbrechung an. */
    public static double[] refract(double[] d) {
        double[] aa = altAz(d);
        return direction(aa[0] + refraction(aa[0]), aa[1]);
    }

    /** Luftmasse nach Kasten und Young (1989) für die scheinbare Höhe in Grad. */
    public static double airmass(double hDeg) {
        double h = Math.max(0, hDeg);
        return 1 / (Math.sin(Math.toRadians(h)) + 0.50572 * Math.pow(h + 6.07995, -1.6364));
    }
}
