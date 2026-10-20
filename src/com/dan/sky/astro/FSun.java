package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Position der Sonne nach der vollstaendigen VSOP87-Kurzfassung fuer die Erde
 * (Meeus, <i>Astronomical Algorithms</i>, Kapitel 25 und Anhang III).
 *
 * <p>Es sind alle Glieder der Reihen L0..L5, B0..B1 und R0..R4 aufgenommen.
 * Die Genauigkeit liegt bei etwa einer Bogensekunde in der Laenge, also rund
 * zwanzigmal besser als die vereinfachte Formel aus Kapitel 25.  Der Aufwand
 * lohnt hier, weil der Phasenwinkel des Mondes unmittelbar aus der Differenz
 * der Laengen von Sonne und Mond folgt: Ein Fehler von 0,01 Grad in der
 * Sonnenlaenge wuerde einen Vollmondzeitpunkt schon um mehr als eine Minute
 * verschieben.</p>
 *
 * <p>Die Terme sind als {A, B, C} abgelegt; jede Reihe wird als
 * Summe A*cos(B + C*tau) mit tau = T/10 (julianische Jahrtausende) gebildet.
 * A steht in Einheiten von 1e-8 Radiant beziehungsweise 1e-8 AE.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FSun {

    // ------------------------------------------------------------------
    // VSOP87D, Erde - heliozentrische Laenge
    // ------------------------------------------------------------------

    private static final double[][] L0 = {
        {175347046, 0, 0}, {3341656, 4.6692568, 6283.07585},
        {34894, 4.6261, 12566.1517}, {3497, 2.7441, 5753.3849},
        {3418, 2.8289, 3.5231}, {3136, 3.6277, 77713.7715},
        {2676, 4.4181, 7860.4194}, {2343, 6.1352, 3930.2097},
        {1324, 0.7425, 11506.7698}, {1273, 2.0371, 529.691},
        {1199, 1.1096, 1577.3435}, {990, 5.233, 5884.927},
        {902, 2.045, 26.298}, {857, 3.508, 398.149},
        {780, 1.179, 5223.694}, {753, 2.533, 5507.553},
        {505, 4.583, 18849.228}, {492, 4.205, 775.523},
        {357, 2.92, 0.067}, {317, 5.849, 11790.629},
        {284, 1.899, 796.298}, {271, 0.315, 10977.079},
        {243, 0.345, 5486.778}, {206, 4.806, 2544.314},
        {205, 1.869, 5573.143}, {202, 2.458, 6069.777},
        {156, 0.833, 213.299}, {132, 3.411, 2942.463},
        {126, 1.083, 20.775}, {115, 0.645, 0.98},
        {103, 0.636, 4694.003}, {102, 0.976, 15720.839},
        {102, 4.267, 7.114}, {99, 6.21, 2146.17},
        {98, 0.68, 155.42}, {86, 5.98, 161000.69},
        {85, 1.3, 6275.96}, {85, 3.67, 71430.7},
        {80, 1.81, 17260.15}, {79, 3.04, 12036.46},
        {75, 1.76, 5088.63}, {74, 3.5, 3154.69},
        {74, 4.68, 801.82}, {70, 0.83, 9437.76},
        {62, 3.98, 8827.39}, {61, 1.82, 7084.9},
        {57, 2.78, 6286.6}, {56, 4.39, 14143.5},
        {56, 3.47, 6279.55}, {52, 0.19, 12139.55},
        {52, 1.33, 1748.02}, {51, 0.28, 5856.48},
        {49, 0.49, 1194.45}, {41, 5.37, 8429.24},
        {41, 2.4, 19651.05}, {39, 6.17, 10447.39},
        {37, 6.04, 10213.29}, {37, 2.57, 1059.38},
        {36, 1.71, 2352.87}, {36, 1.78, 6812.77},
        {33, 0.59, 17789.85}, {30, 0.44, 83996.85},
        {30, 2.74, 1349.87}, {25, 3.16, 4690.48}
    };

    private static final double[][] L1 = {
        {628331966747.0, 0, 0}, {206059, 2.678235, 6283.07585},
        {4303, 2.6351, 12566.1517}, {425, 1.59, 3.523},
        {119, 5.796, 26.298}, {109, 2.966, 1577.344},
        {93, 2.59, 18849.23}, {72, 1.14, 529.69},
        {68, 1.87, 398.15}, {67, 4.41, 5507.55},
        {59, 2.89, 5223.69}, {56, 2.17, 155.42},
        {45, 0.4, 796.3}, {36, 0.47, 775.52},
        {29, 2.65, 7.11}, {21, 5.34, 0.98},
        {19, 1.85, 5486.78}, {19, 4.97, 213.3},
        {17, 2.99, 6275.96}, {16, 0.03, 2544.31},
        {16, 1.43, 2146.17}, {15, 1.21, 10977.08},
        {12, 2.83, 1748.02}, {12, 3.26, 5088.63},
        {12, 5.27, 1194.45}, {12, 2.08, 4694.0},
        {11, 0.77, 553.57}, {10, 1.3, 6286.6},
        {10, 4.24, 1349.87}, {9, 2.7, 242.73},
        {9, 5.64, 951.72}, {8, 5.3, 2352.87},
        {6, 2.65, 9437.76}, {6, 4.67, 4690.48}
    };

    private static final double[][] L2 = {
        {52919, 0, 0}, {8720, 1.0721, 6283.0758},
        {309, 0.867, 12566.152}, {27, 0.05, 3.52},
        {16, 5.19, 26.3}, {16, 3.68, 155.42},
        {10, 0.76, 18849.23}, {9, 2.06, 77713.77},
        {7, 0.83, 775.52}, {5, 4.66, 1577.34},
        {4, 1.03, 7.11}, {4, 3.44, 5573.14},
        {3, 5.14, 796.3}, {3, 6.05, 5507.55},
        {3, 1.19, 242.73}, {3, 6.12, 529.69},
        {3, 0.31, 398.15}, {3, 2.28, 553.57},
        {2, 4.38, 5223.69}, {2, 3.75, 0.98}
    };

    private static final double[][] L3 = {
        {289, 5.844, 6283.076}, {35, 0, 0},
        {17, 5.49, 12566.15}, {3, 5.2, 155.42},
        {1, 4.72, 3.52}, {1, 5.3, 18849.23},
        {1, 5.97, 242.73}
    };

    private static final double[][] L4 = {
        {114, 3.142, 0}, {8, 4.13, 6283.08}, {1, 3.84, 12566.15}
    };

    private static final double[][] L5 = {
        {1, 3.14, 0}
    };

    // ------------------------------------------------------------------
    // VSOP87D, Erde - heliozentrische Breite
    // ------------------------------------------------------------------

    private static final double[][] B0 = {
        {280, 3.199, 84334.662}, {102, 5.422, 5507.553},
        {80, 3.88, 5223.69}, {44, 3.7, 2352.87},
        {32, 4.0, 1577.34}
    };

    private static final double[][] B1 = {
        {9, 3.9, 5507.55}, {6, 1.73, 5223.69}
    };

    // ------------------------------------------------------------------
    // VSOP87D, Erde - Radiusvektor
    // ------------------------------------------------------------------

    private static final double[][] R0 = {
        {100013989.0, 0, 0}, {1670700, 3.0984635, 6283.07585},
        {13956, 3.05525, 12566.1517}, {3084, 5.1985, 77713.7715},
        {1628, 1.1739, 5753.3849}, {1576, 2.8469, 7860.4194},
        {925, 5.453, 11506.77}, {542, 4.564, 3930.21},
        {472, 3.661, 5884.927}, {346, 0.964, 5507.553},
        {329, 5.9, 5223.694}, {307, 0.299, 5573.143},
        {243, 4.273, 11790.629}, {212, 5.847, 1577.344},
        {186, 5.022, 10977.079}, {175, 3.012, 18849.228},
        {110, 5.055, 5486.778}, {98, 0.89, 6069.78},
        {86, 5.69, 15720.84}, {86, 1.27, 161000.69},
        {65, 0.27, 17260.15}, {63, 0.92, 529.69},
        {57, 2.01, 83996.85}, {56, 5.24, 71430.7},
        {49, 3.25, 2544.31}, {47, 2.58, 775.52},
        {45, 5.54, 9437.76}, {43, 6.01, 6275.96},
        {39, 5.36, 4694.0}, {38, 2.39, 8827.39},
        {37, 0.83, 19651.05}, {37, 4.9, 12139.55},
        {36, 1.67, 12036.46}, {35, 1.84, 2942.46},
        {33, 0.24, 7084.9}, {32, 0.18, 5088.63},
        {32, 1.78, 398.15}, {28, 1.21, 6286.6},
        {28, 1.9, 6279.55}, {26, 4.59, 10447.39}
    };

    private static final double[][] R1 = {
        {103019, 1.10749, 6283.07585}, {1721, 1.0644, 12566.1517},
        {702, 3.142, 0}, {32, 1.02, 18849.23},
        {31, 2.84, 5507.55}, {25, 1.32, 5223.69},
        {18, 1.42, 1577.34}, {10, 5.91, 10977.08},
        {9, 1.42, 6275.96}, {9, 0.27, 5486.78}
    };

    private static final double[][] R2 = {
        {4359, 5.7846, 6283.0758}, {124, 5.579, 12566.152},
        {12, 3.14, 0}, {9, 3.63, 77713.77},
        {6, 1.87, 5573.14}, {3, 5.47, 18849.23}
    };

    private static final double[][] R3 = {
        {145, 4.273, 6283.076}, {7, 3.92, 12566.15}
    };

    private static final double[][] R4 = {
        {4, 2.56, 6283.08}
    };

    /** Geozentrische scheinbare Laenge der Sonne in Grad. */
    public final double apparentLongitude;

    /** Geozentrische Breite der Sonne in Grad (stets sehr klein). */
    public final double latitude;

    /** Abstand Erde-Sonne in astronomischen Einheiten. */
    public final double distanceAu;

    /** Scheinbare Rektaszension in Grad. */
    public final double rightAscension;

    /** Scheinbare Deklination in Grad. */
    public final double declination;

    /** Wahre geometrische Laenge (ohne Aberration und Nutation) in Grad. */
    public final double trueLongitude;

    /**
     * Heliozentrische Laenge der Erde in Grad, unmittelbar aus der
     * VSOP87-Reihe und ohne jede Nachbehandlung. Dieser Wert ist der
     * eigentliche Pruefstein gegen Meeus Beispiel 25.b (19,907372 Grad).
     */
    public final double heliocentricLongitude;

    /** Heliozentrische Breite der Erde in Grad, ohne Nachbehandlung. */
    public final double heliocentricLatitude;

    private FSun(double apparentLongitude, double latitude, double distanceAu,
            double rightAscension, double declination, double trueLongitude,
            double heliocentricLongitude, double heliocentricLatitude) {
        this.apparentLongitude = apparentLongitude;
        this.latitude = latitude;
        this.distanceAu = distanceAu;
        this.rightAscension = rightAscension;
        this.declination = declination;
        this.trueLongitude = trueLongitude;
        this.heliocentricLongitude = heliocentricLongitude;
        this.heliocentricLatitude = heliocentricLatitude;
    }

    private static double series(double[][] terms, double tau) {
        double sum = 0.0;
        for (int i = 0; i < terms.length; i++) {
            sum += terms[i][0] * Math.cos(terms[i][1] + terms[i][2] * tau);
        }
        return sum;
    }

    /**
     * Berechnet die Sonnenposition fuer ein julianisches Datum in dynamischer
     * Zeit.
     *
     * @param jde julianisches Datum in TD
     * @return Sonnenposition
     */
    public static FSun at(double jde) {
        double t = FAstroTime.centuries(jde);
        double tau = t / 10.0;

        double l = series(L0, tau)
                + series(L1, tau) * tau
                + series(L2, tau) * tau * tau
                + series(L3, tau) * tau * tau * tau
                + series(L4, tau) * tau * tau * tau * tau
                + series(L5, tau) * tau * tau * tau * tau * tau;
        l = Math.toDegrees(l / 1.0e8);

        double b = series(B0, tau) + series(B1, tau) * tau;
        b = Math.toDegrees(b / 1.0e8);

        double r = series(R0, tau)
                + series(R1, tau) * tau
                + series(R2, tau) * tau * tau
                + series(R3, tau) * tau * tau * tau
                + series(R4, tau) * tau * tau * tau * tau;
        r = r / 1.0e8;

        // heliozentrisch -> geozentrisch
        double theta = FAngle.norm360(l + 180.0);
        double beta = -b;

        // Umrechnung VSOP87 (dynamische Ekliptik) -> FK5, Meeus 25.9
        double lambdaP = theta - 1.397 * t - 0.00031 * t * t;
        double dTheta = -0.09033 / 3600.0;
        double dBeta = 0.03916 * (FAngle.cos(lambdaP) - FAngle.sin(lambdaP)) / 3600.0;
        theta += dTheta;
        beta += dBeta;

        double trueLon = theta;

        // Nutation in Laenge und Aberration
        double dpsi = FAstroTime.nutationInLongitude(t);
        double aberration = -20.4898 / (3600.0 * r);
        double lambda = FAngle.norm360(theta + dpsi + aberration);

        // scheinbare Rektaszension und Deklination
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

        return new FSun(lambda, beta, r, FAngle.norm360(ra), dec, FAngle.norm360(trueLon),
                FAngle.norm360(l), b);
    }

    /**
     * Scheinbarer Halbmesser der Sonnenscheibe in Grad.
     *
     * @return Halbmesser in Grad
     */
    public double apparentRadiusDeg() {
        return 0.266563888 / distanceAu;
    }

    @Override
    public String toString() {
        return String.format("Sonne: lambda=%.6f° beta=%.6f° R=%.8f AE "
                + "RA=%.5f° Dec=%.5f°",
                apparentLongitude, latitude, distanceAu, rightAscension, declination);
    }
}
