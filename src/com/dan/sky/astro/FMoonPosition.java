package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Geozentrische Position des Mondes nach Meeus, <i>Astronomical Algorithms</i>,
 * Kapitel 47 (gekuerzte ELP-2000/82-Theorie von Chapront-Touze und Chapront).
 *
 * <p>Aufgenommen sind beide Tabellen vollstaendig: 60 Glieder fuer Laenge und
 * Abstand (47.A) und 60 Glieder fuer die Breite (47.B), dazu die additiven
 * Glieder aus den Argumenten A1, A2 und A3, die Venus, Jupiter und die
 * Abplattung der Erde beruecksichtigen.  Die Genauigkeit betraegt rund
 * 10 Bogensekunden in der Laenge und 4 Bogensekunden in der Breite.</p>
 *
 * <p>Pruefstein ist Beispiel 47.a bei Meeus: 1992 April 12, 0h TD ergibt
 * lambda = 133,162655 Grad, beta = -3,229126 Grad, Delta = 368409,7 km.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonPosition {

    /**
     * Tabelle 47.A: Argumente D, M, M', F und die Koeffizienten fuer
     * Sigma l (in 1e-6 Grad) und Sigma r (in 1e-3 Kilometer).
     */
    private static final double[][] TABLE_A = {
        {0, 0, 1, 0, 6288774, -20905355},
        {2, 0, -1, 0, 1274027, -3699111},
        {2, 0, 0, 0, 658314, -2955968},
        {0, 0, 2, 0, 213618, -569925},
        {0, 1, 0, 0, -185116, 48888},
        {0, 0, 0, 2, -114332, -3149},
        {2, 0, -2, 0, 58793, 246158},
        {2, -1, -1, 0, 57066, -152138},
        {2, 0, 1, 0, 53322, -170733},
        {2, -1, 0, 0, 45758, -204586},
        {0, 1, -1, 0, -40923, -129620},
        {1, 0, 0, 0, -34720, 108743},
        {0, 1, 1, 0, -30383, 104755},
        {2, 0, 0, -2, 15327, 10321},
        {0, 0, 1, 2, -12528, 0},
        {0, 0, 1, -2, 10980, 79661},
        {4, 0, -1, 0, 10675, -34782},
        {0, 0, 3, 0, 10034, -23210},
        {4, 0, -2, 0, 8548, -21636},
        {2, 1, -1, 0, -7888, 24208},
        {2, 1, 0, 0, -6766, 30824},
        {1, 0, -1, 0, -5163, -8379},
        {1, 1, 0, 0, 4987, -16675},
        {2, -1, 1, 0, 4036, -12831},
        {2, 0, 2, 0, 3994, -10445},
        {4, 0, 0, 0, 3861, -11650},
        {2, 0, -3, 0, 3665, 14403},
        {0, 1, -2, 0, -2689, -7003},
        {2, 0, -1, 2, -2602, 0},
        {2, -1, -2, 0, 2390, 10056},
        {1, 0, 1, 0, -2348, 6322},
        {2, -2, 0, 0, 2236, -9884},
        {0, 1, 2, 0, -2120, 5751},
        {0, 2, 0, 0, -2069, 0},
        {2, -2, -1, 0, 2048, -4950},
        {2, 0, 1, -2, -1773, 4130},
        {2, 0, 0, 2, -1595, 0},
        {4, -1, -1, 0, 1215, -3958},
        {0, 0, 2, 2, -1110, 0},
        {3, 0, -1, 0, -892, 3258},
        {2, 1, 1, 0, -810, 2616},
        {4, -1, -2, 0, 759, -1897},
        {0, 2, -1, 0, -713, -2117},
        {2, 2, -1, 0, -700, 2354},
        {2, 1, -2, 0, 691, 0},
        {2, -1, 0, -2, 596, 0},
        {4, 0, 1, 0, 549, -1423},
        {0, 0, 4, 0, 537, -1117},
        {4, -1, 0, 0, 520, -1571},
        {1, 0, -2, 0, -487, -1739},
        {2, 1, 0, -2, -399, 0},
        {0, 0, 2, -2, -381, -4421},
        {1, 1, 1, 0, 351, 0},
        {3, 0, -2, 0, -340, 0},
        {4, 0, -3, 0, 330, 0},
        {2, -1, 2, 0, 327, 0},
        {0, 2, 1, 0, -323, 1165},
        {1, 1, -1, 0, 299, 0},
        {2, 0, 3, 0, 294, 0},
        {2, 0, -1, -2, 0, 8752}
    };

    /**
     * Tabelle 47.B: Argumente D, M, M', F und der Koeffizient fuer
     * Sigma b (in 1e-6 Grad).
     */
    private static final double[][] TABLE_B = {
        {0, 0, 0, 1, 5128122},
        {0, 0, 1, 1, 280602},
        {0, 0, 1, -1, 277693},
        {2, 0, 0, -1, 173237},
        {2, 0, -1, 1, 55413},
        {2, 0, -1, -1, 46271},
        {2, 0, 0, 1, 32573},
        {0, 0, 2, 1, 17198},
        {2, 0, 1, -1, 9266},
        {0, 0, 2, -1, 8822},
        {2, -1, 0, -1, 8216},
        {2, 0, -2, -1, 4324},
        {2, 0, 1, 1, 4200},
        {2, 1, 0, -1, -3359},
        {2, -1, -1, 1, 2463},
        {2, -1, 0, 1, 2211},
        {2, -1, -1, -1, 2065},
        {0, 1, -1, -1, -1870},
        {4, 0, -1, -1, 1828},
        {0, 1, 0, 1, -1794},
        {0, 0, 0, 3, -1749},
        {0, 1, -1, 1, -1565},
        {1, 0, 0, 1, -1491},
        {0, 1, 1, 1, -1475},
        {0, 1, 1, -1, -1410},
        {0, 1, 0, -1, -1344},
        {1, 0, 0, -1, -1335},
        {0, 0, 3, 1, 1107},
        {4, 0, 0, -1, 1021},
        {4, 0, -1, 1, 833},
        {0, 0, 1, -3, 777},
        {4, 0, -2, 1, 671},
        {2, 0, 0, -3, 607},
        {2, 0, 2, -1, 596},
        {2, -1, 1, -1, 491},
        {2, 0, -2, 1, -451},
        {0, 0, 3, -1, 439},
        {2, 0, 2, 1, 422},
        {2, 0, -3, -1, 421},
        {2, 1, -1, 1, -366},
        {2, 1, 0, 1, -351},
        {4, 0, 0, 1, 331},
        {2, -1, 1, 1, 315},
        {2, -2, 0, -1, 302},
        {0, 0, 1, 3, -283},
        {2, 1, 1, -1, -229},
        {1, 1, 0, -1, 223},
        {1, 1, 0, 1, 223},
        {0, 1, -2, -1, -220},
        {2, 1, -1, -1, -220},
        {1, 0, 1, 1, -185},
        {2, -1, -2, -1, 181},
        {0, 1, 2, 1, -177},
        {4, 0, -2, -1, 176},
        {4, -1, -1, -1, 166},
        {1, 0, 1, -1, -164},
        {4, 0, 1, -1, 132},
        {1, 0, -1, -1, -119},
        {4, -1, 0, -1, 115},
        {2, -2, 0, 1, 107}
    };

    /** Scheinbare geozentrische ekliptikale Laenge in Grad. */
    public final double longitude;

    /** Geozentrische ekliptikale Breite in Grad. */
    public final double latitude;

    /** Abstand Erde-Mond (Mittelpunkt zu Mittelpunkt) in Kilometern. */
    public final double distanceKm;

    /** Aequatorial-Horizontalparallaxe in Grad. */
    public final double parallax;

    /** Scheinbare Rektaszension in Grad. */
    public final double rightAscension;

    /** Scheinbare Deklination in Grad. */
    public final double declination;

    /** Mittlere Laenge L' in Grad (fuer Libration und Phasen gebraucht). */
    public final double meanLongitude;

    /** Mittlere Anomalie des Mondes M' in Grad. */
    public final double meanAnomaly;

    /** Mittlere Elongation D in Grad. */
    public final double meanElongation;

    /** Argument der Breite F in Grad. */
    public final double argumentOfLatitude;

    /** Laenge des aufsteigenden Knotens Omega in Grad. */
    public final double ascendingNode;

    /** Laenge ohne Nutation, fuer die Libration gebraucht. */
    public final double longitudeWithoutNutation;

    private FMoonPosition(double longitude, double latitude, double distanceKm,
            double parallax, double rightAscension, double declination,
            double meanLongitude, double meanAnomaly, double meanElongation,
            double argumentOfLatitude, double ascendingNode,
            double longitudeWithoutNutation) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.distanceKm = distanceKm;
        this.parallax = parallax;
        this.rightAscension = rightAscension;
        this.declination = declination;
        this.meanLongitude = meanLongitude;
        this.meanAnomaly = meanAnomaly;
        this.meanElongation = meanElongation;
        this.argumentOfLatitude = argumentOfLatitude;
        this.ascendingNode = ascendingNode;
        this.longitudeWithoutNutation = longitudeWithoutNutation;
    }

    /**
     * Berechnet die Mondposition fuer ein julianisches Datum in dynamischer
     * Zeit.
     *
     * @param jde julianisches Datum in TD
     * @return Mondposition
     */
    public static FMoonPosition at(double jde) {
        double t = FAstroTime.centuries(jde);
        double t2 = t * t;
        double t3 = t2 * t;
        double t4 = t3 * t;

        // Fundamentale Argumente, Meeus 47.1 bis 47.5
        double lp = FAngle.norm360(218.3164477 + 481267.88123421 * t
                - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0);
        double d = FAngle.norm360(297.8501921 + 445267.1114034 * t
                - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0);
        double m = FAngle.norm360(357.5291092 + 35999.0502909 * t
                - 0.0001536 * t2 + t3 / 24490000.0);
        double mp = FAngle.norm360(134.9633964 + 477198.8675055 * t
                + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0);
        double f = FAngle.norm360(93.2720950 + 483202.0175233 * t
                - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0);

        // Argumente fuer die additiven Glieder, Meeus Seite 338
        double a1 = FAngle.norm360(119.75 + 131.849 * t);
        double a2 = FAngle.norm360(53.09 + 479264.290 * t);
        double a3 = FAngle.norm360(313.45 + 481266.484 * t);

        // Exzentrizitaet der Erdbahn, Meeus 47.6
        double e = 1.0 - 0.002516 * t - 0.0000074 * t2;
        double e2 = e * e;

        double sumL = 0.0;
        double sumR = 0.0;
        for (int i = 0; i < TABLE_A.length; i++) {
            double[] row = TABLE_A[i];
            double arg = row[0] * d + row[1] * m + row[2] * mp + row[3] * f;
            double factor = eccentricityFactor((int) row[1], e, e2);
            sumL += row[4] * factor * FAngle.sin(arg);
            sumR += row[5] * factor * FAngle.cos(arg);
        }

        double sumB = 0.0;
        for (int i = 0; i < TABLE_B.length; i++) {
            double[] row = TABLE_B[i];
            double arg = row[0] * d + row[1] * m + row[2] * mp + row[3] * f;
            double factor = eccentricityFactor((int) row[1], e, e2);
            sumB += row[4] * factor * FAngle.sin(arg);
        }

        // additive Glieder
        sumL += 3958.0 * FAngle.sin(a1)
                + 1962.0 * FAngle.sin(lp - f)
                + 318.0 * FAngle.sin(a2);
        sumB += -2235.0 * FAngle.sin(lp)
                + 382.0 * FAngle.sin(a3)
                + 175.0 * FAngle.sin(a1 - f)
                + 175.0 * FAngle.sin(a1 + f)
                + 127.0 * FAngle.sin(lp - mp)
                - 115.0 * FAngle.sin(lp + mp);

        double lambdaGeom = FAngle.norm360(lp + sumL / 1.0e6);
        double beta = sumB / 1.0e6;
        double delta = 385000.56 + sumR / 1000.0;
        double pi = Math.toDegrees(Math.asin(6378.14 / delta));

        // scheinbare Laenge: Nutation aufschlagen
        double dpsi = FAstroTime.nutationInLongitude(t);
        double lambda = FAngle.norm360(lambdaGeom + dpsi);

        // Knoten, Meeus 47.7
        double omega = FAngle.norm360(125.0445479 - 1934.1362891 * t
                + 0.0020754 * t2 + t3 / 467441.0 - t4 / 60616000.0);

        // Aequatorkoordinaten
        double eps = FAstroTime.trueObliquity(t);
        double lamRad = Math.toRadians(lambda);
        double betRad = Math.toRadians(beta);
        double epsRad = Math.toRadians(eps);
        double ra = Math.toDegrees(Math.atan2(
                Math.sin(lamRad) * Math.cos(epsRad) - Math.tan(betRad) * Math.sin(epsRad),
                Math.cos(lamRad)));
        double dec = Math.toDegrees(Math.asin(
                Math.sin(betRad) * Math.cos(epsRad)
                + Math.cos(betRad) * Math.sin(epsRad) * Math.sin(lamRad)));

        return new FMoonPosition(lambda, beta, delta, pi, FAngle.norm360(ra), dec,
                lp, mp, d, f, omega, lambdaGeom);
    }

    /**
     * Beruecksichtigt die veraenderliche Exzentrizitaet der Erdbahn: Glieder
     * mit dem Argument M werden mit E multipliziert, solche mit 2M mit E hoch 2.
     */
    private static double eccentricityFactor(int mCoefficient, double e, double e2) {
        int abs = Math.abs(mCoefficient);
        if (abs == 1) {
            return e;
        }
        if (abs == 2) {
            return e2;
        }
        return 1.0;
    }

    /**
     * Scheinbarer Halbmesser der Mondscheibe in Grad, aus dem Abstand.
     *
     * @return Halbmesser in Grad
     */
    public double apparentRadiusDeg() {
        return Math.toDegrees(Math.asin(1737.4 / distanceKm));
    }

    /**
     * Scheinbarer Durchmesser der Mondscheibe in Bogenminuten.
     *
     * @return Durchmesser in Bogenminuten
     */
    public double apparentDiameterArcmin() {
        return apparentRadiusDeg() * 2.0 * 60.0;
    }

    @Override
    public String toString() {
        return String.format("Mond: lambda=%.6f° beta=%.6f° Delta=%.1f km "
                + "pi=%.6f° RA=%.5f° Dec=%.5f°",
                longitude, latitude, distanceKm, parallax, rightAscension, declination);
    }
}
