package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Auf- und Untergang sowie Kulmination von Mond und Sonne fuer einen Ort.
 *
 * <p>Statt der Interpolationsformel aus Meeus Kapitel 15 wird die Hoehe des
 * Gestirns als Funktion der Zeit unmittelbar ausgewertet und ihre Nullstelle
 * gesucht (grobes Raster, dann Intervallhalbierung auf eine Sekunde). Das ist
 * langsamer, aber unempfindlich gegen die Sonderfaelle, an denen die
 * Interpolation scheitert: Der Mond geht an manchen Tagen gar nicht auf oder
 * zweimal, und im hohen Norden kommt beides regelmaessig vor.</p>
 *
 * <p>Beim Mond ist die topozentrische Umrechnung nicht wegzulassen: Die
 * Parallaxe von knapp einem Grad verschiebt den Aufgang um bis zu zwei
 * Minuten. Sie ist hier eingerechnet (Meeus Kapitel 40).</p>
 *
 * @author Claude fuer Daniel
 */
public final class FRiseSet {

    /** Standardhoehe des Sonnenmittelpunktes bei Auf- und Untergang. */
    public static final double SUN_ALTITUDE = -0.8333;

    /** Kein Ereignis an diesem Tag (Gestirn bleibt oben oder unten). */
    public static final double NONE = Double.NaN;

    /** Julianisches Datum (UT) des Aufgangs, oder {@link #NONE}. */
    public final double riseJdUt;

    /** Julianisches Datum (UT) des Untergangs, oder {@link #NONE}. */
    public final double setJdUt;

    /** Julianisches Datum (UT) der oberen Kulmination, oder {@link #NONE}. */
    public final double transitJdUt;

    /** Hoehe bei der Kulmination in Grad. */
    public final double transitAltitude;

    /** Wahr, wenn das Gestirn den ganzen Tag ueber dem Horizont bleibt. */
    public final boolean alwaysUp;

    /** Wahr, wenn das Gestirn den ganzen Tag unter dem Horizont bleibt. */
    public final boolean alwaysDown;

    private FRiseSet(double riseJdUt, double setJdUt, double transitJdUt,
            double transitAltitude, boolean alwaysUp, boolean alwaysDown) {
        this.riseJdUt = riseJdUt;
        this.setJdUt = setJdUt;
        this.transitJdUt = transitJdUt;
        this.transitAltitude = transitAltitude;
        this.alwaysUp = alwaysUp;
        this.alwaysDown = alwaysDown;
    }

    /**
     * Topozentrische Hoehe des Mondes ueber dem Horizont.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @param loc  Beobachtungsort
     * @return Hoehe in Grad
     */
    public static double moonAltitude(double jdUt, FGeoLocation loc) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        FMoonPosition moon = FMoonPosition.at(jde);
        return topocentricAltitude(jdUt, loc, moon.rightAscension, moon.declination,
                moon.parallax);
    }

    /**
     * Hoehe der Sonne ueber dem Horizont. Die Parallaxe der Sonne betraegt
     * 8,8 Bogensekunden und wird mitgerechnet, obwohl sie hier keine Rolle
     * spielt - so gibt es nur einen Rechenweg.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @param loc  Beobachtungsort
     * @return Hoehe in Grad
     */
    public static double sunAltitude(double jdUt, FGeoLocation loc) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        FSun sun = FSun.at(jde);
        double parallax = 8.794 / 3600.0 / sun.distanceAu;
        return topocentricAltitude(jdUt, loc, sun.rightAscension, sun.declination, parallax);
    }

    /**
     * Rechnet geozentrische Aequatorkoordinaten in die topozentrische Hoehe um.
     *
     * @param jdUt        julianisches Datum in Weltzeit
     * @param loc         Beobachtungsort
     * @param ra          geozentrische Rektaszension in Grad
     * @param dec         geozentrische Deklination in Grad
     * @param parallaxDeg Aequatorial-Horizontalparallaxe in Grad
     * @return Hoehe in Grad
     */
    public static double topocentricAltitude(double jdUt, FGeoLocation loc,
            double ra, double dec, double parallaxDeg) {
        double theta = FAstroTime.apparentSiderealTime(jdUt);
        // Meeus zaehlt die Laenge westlich positiv, hier ist sie oestlich
        // positiv: H = theta + lambda_ost - alpha.
        double h = FAngle.norm360(theta + loc.getLongitudeDeg() - ra);

        double sinPi = Math.sin(Math.toRadians(parallaxDeg));
        double rhoSin = loc.rhoSinPhiPrime();
        double rhoCos = loc.rhoCosPhiPrime();

        double hRad = Math.toRadians(h);
        double decRad = Math.toRadians(dec);

        // Meeus 40.2 und 40.3
        double dAlpha = Math.atan2(-rhoCos * sinPi * Math.sin(hRad),
                Math.cos(decRad) - rhoCos * sinPi * Math.cos(hRad));
        double decTopo = Math.atan2(
                (Math.sin(decRad) - rhoSin * sinPi) * Math.cos(dAlpha),
                Math.cos(decRad) - rhoCos * sinPi * Math.cos(hRad));
        double hTopo = hRad + dAlpha;

        double phi = Math.toRadians(loc.getLatitudeDeg());
        double sinAlt = Math.sin(phi) * Math.sin(decTopo)
                + Math.cos(phi) * Math.cos(decTopo) * Math.cos(hTopo);
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sinAlt))));
    }

    /**
     * Die Grenzhoehe, bei der der Mond als auf- oder untergegangen gilt
     * (Meeus Seite 102): 0,7275 mal Parallaxe minus Refraktion.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @return Grenzhoehe in Grad
     */
    public static double moonStandardAltitude(double jdUt) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        return 0.7275 * FMoonPosition.at(jde).parallax - 0.5667;
    }

    /**
     * Sucht Auf- und Untergang des Mondes an einem buergerlichen Tag.
     *
     * @param jdUtMidnight julianisches Datum der oertlichen Mitternacht in UT
     * @param loc          Beobachtungsort
     * @return Ereignisse des Tages
     */
    public static FRiseSet moonFor(double jdUtMidnight, FGeoLocation loc) {
        return compute(jdUtMidnight, loc, true);
    }

    /**
     * Sucht Auf- und Untergang der Sonne an einem buergerlichen Tag.
     *
     * @param jdUtMidnight julianisches Datum der oertlichen Mitternacht in UT
     * @param loc          Beobachtungsort
     * @return Ereignisse des Tages
     */
    public static FRiseSet sunFor(double jdUtMidnight, FGeoLocation loc) {
        return compute(jdUtMidnight, loc, false);
    }

    private static double altitude(double jdUt, FGeoLocation loc, boolean moon) {
        return moon ? moonAltitude(jdUt, loc) : sunAltitude(jdUt, loc);
    }

    private static double standardAltitude(double jdUt, boolean moon) {
        return moon ? moonStandardAltitude(jdUt) : SUN_ALTITUDE;
    }

    private static FRiseSet compute(double jd0, FGeoLocation loc, boolean moon) {
        final int steps = 144;              // alle zehn Minuten
        final double dt = 1.0 / steps;

        double rise = NONE;
        double set = NONE;
        double transit = NONE;
        double transitAlt = -90.0;
        boolean anyAbove = false;
        boolean anyBelow = false;

        double prevT = jd0;
        double prevDiff = altitude(prevT, loc, moon) - standardAltitude(prevT, moon);
        double prevAlt = prevDiff + standardAltitude(prevT, moon);
        double prevPrevAlt = prevAlt;

        for (int i = 1; i <= steps; i++) {
            double t = jd0 + i * dt;
            double alt = altitude(t, loc, moon);
            double diff = alt - standardAltitude(t, moon);

            if (diff >= 0.0) {
                anyAbove = true;
            } else {
                anyBelow = true;
            }

            if (prevDiff < 0.0 && diff >= 0.0 && Double.isNaN(rise)) {
                rise = refine(prevT, t, loc, moon);
            }
            if (prevDiff >= 0.0 && diff < 0.0 && Double.isNaN(set)) {
                set = refine(prevT, t, loc, moon);
            }

            // Kulmination: Hoehenmaximum, an einem Vorzeichenwechsel der
            // Steigung erkannt und dann genauer eingegrenzt
            if (i >= 2 && prevAlt > prevPrevAlt && prevAlt >= alt) {
                double cand = refineTransit(prevT - dt, t, loc, moon);
                double candAlt = altitude(cand, loc, moon);
                if (candAlt > transitAlt) {
                    transitAlt = candAlt;
                    transit = cand;
                }
            }

            prevPrevAlt = prevAlt;
            prevAlt = alt;
            prevT = t;
            prevDiff = diff;
        }

        boolean up = anyAbove && !anyBelow;
        boolean down = anyBelow && !anyAbove;
        return new FRiseSet(rise, set, transit, transitAlt, up, down);
    }

    /**
     * Intervallhalbierung auf eine Sekunde genau.
     */
    private static double refine(double a, double b, FGeoLocation loc, boolean moon) {
        double lo = a;
        double hi = b;
        double fLo = altitude(lo, loc, moon) - standardAltitude(lo, moon);
        for (int i = 0; i < 24; i++) {
            double mid = 0.5 * (lo + hi);
            double fMid = altitude(mid, loc, moon) - standardAltitude(mid, moon);
            if ((fLo < 0.0) == (fMid < 0.0)) {
                lo = mid;
                fLo = fMid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Goldener Schnitt auf das Hoehenmaximum.
     */
    private static double refineTransit(double a, double b, FGeoLocation loc, boolean moon) {
        double phi = 0.6180339887498949;
        double lo = a;
        double hi = b;
        double x1 = hi - phi * (hi - lo);
        double x2 = lo + phi * (hi - lo);
        double f1 = altitude(x1, loc, moon);
        double f2 = altitude(x2, loc, moon);
        for (int i = 0; i < 40; i++) {
            if (f1 < f2) {
                lo = x1;
                x1 = x2;
                f1 = f2;
                x2 = lo + phi * (hi - lo);
                f2 = altitude(x2, loc, moon);
            } else {
                hi = x2;
                x2 = x1;
                f2 = f1;
                x1 = hi - phi * (hi - lo);
                f1 = altitude(x1, loc, moon);
            }
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Sucht den Meridiandurchgang des Mondes, also den Augenblick, in dem der
     * Stundenwinkel null wird.
     *
     * <p>Das ist NICHT dasselbe wie {@link #transitJdUt}, der das Maximum der
     * Hoehe meint. Beim Mond laufen beide bis zu fuenf Minuten auseinander,
     * weil sich die Deklination um bis zu 0,2 Grad je Stunde aendert und die
     * Hoehe dadurch ihr Maximum neben dem Meridian erreicht. Fuer die
     * Gezeitenrechnung nach der Hafenzeit ist der Meridiandurchgang die
     * richtige Groesse: Das Hochwasser folgt der Kulmination im Sinne des
     * Stundenwinkels, nicht der groessten Hoehe.</p>
     *
     * @param jd0   julianisches Datum in UT, ab dem gesucht wird
     * @param loc   Beobachtungsort
     * @param upper wahr fuer die obere Kulmination, falsch fuer die untere
     * @return julianisches Datum des Durchgangs, oder {@link #NONE}
     */
    public static double meridianTransit(double jd0, FGeoLocation loc, boolean upper) {
        double ziel = upper ? 0.0 : 180.0;
        // Halbe Stunde je Schritt. Der Stundenwinkel des Mondes waechst um
        // rund 14,5 Grad je Stunde, ein Schritt traegt also gut sieben Grad -
        // weit genug von den 180 Grad entfernt, ab denen zwei Durchgaenge in
        // einen Schritt fielen. Feiner zu rastern kostet nur Rechenzeit: Was
        // der Raster findet, findet danach ohnehin die Intervallhalbierung
        // genau. Frueher waren es zehn Minuten und 30 Halbierungen, zusammen
        // 174 Mondpositionen fuer eine Genauigkeit von einer Mikrosekunde -
        // bei einer Hafenzeit, die auf Minuten geschaetzt ist.
        final int steps = 48;
        final double dt = 1.0 / steps;
        double prevT = jd0;
        double prevDiff = FAngle.norm180(hourAngle(prevT, loc) - ziel);

        for (int i = 1; i <= steps; i++) {
            double t = jd0 + i * dt;
            double diff = FAngle.norm180(hourAngle(t, loc) - ziel);
            // Der Stundenwinkel waechst; ein Sprung von negativ nach positiv
            // ohne Ueberlauf markiert den Durchgang.
            if (prevDiff < 0.0 && diff >= 0.0 && (diff - prevDiff) < 90.0) {
                double lo = prevT;
                double hi = t;
                // 22 Halbierungen einer halben Stunde: eine halbe
                // Zehntausendstel Sekunde. Mehr waere Zierde.
                for (int j = 0; j < 22; j++) {
                    double mid = 0.5 * (lo + hi);
                    if (FAngle.norm180(hourAngle(mid, loc) - ziel) < 0.0) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                return 0.5 * (lo + hi);
            }
            prevT = t;
            prevDiff = diff;
        }
        return NONE;
    }

    /**
     * Stundenwinkel des Mondes fuer einen Ort.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @param loc  Beobachtungsort
     * @return Stundenwinkel in Grad, 0 bis 360
     */
    public static double hourAngle(double jdUt, FGeoLocation loc) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        double ra = FMoonPosition.at(jde).rightAscension;
        return FAngle.norm360(FAstroTime.apparentSiderealTime(jdUt)
                + loc.getLongitudeDeg() - ra);
    }

    /**
     * Gibt es an diesem Tag einen Aufgang?
     *
     * @return wahr, wenn ein Aufgang gefunden wurde
     */
    public boolean hasRise() {
        return !Double.isNaN(riseJdUt);
    }

    /**
     * Gibt es an diesem Tag einen Untergang?
     *
     * @return wahr, wenn ein Untergang gefunden wurde
     */
    public boolean hasSet() {
        return !Double.isNaN(setJdUt);
    }

    /**
     * Gibt es an diesem Tag eine obere Kulmination?
     *
     * @return wahr, wenn eine Kulmination gefunden wurde
     */
    public boolean hasTransit() {
        return !Double.isNaN(transitJdUt);
    }
}
