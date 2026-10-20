package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Alles, was FMoon fuer einen Zeitpunkt und einen Ort ueber den Mond wissen
 * muss, in einem Stueck: Position von Sonne und Mond, Beleuchtung, Libration,
 * Auf- und Untergang, scheinbare Groesse und die beiden Winkel, die ueber die
 * Neigung der Sichel am Himmel entscheiden.
 *
 * <p>Diese Klasse ist die einzige Schnittstelle, die die Anzeigekomponenten
 * benutzen sollen. Alles darunter ist Rechenwerk.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonEphemeris {

    /** Julianisches Datum in Weltzeit. */
    public final double jdUt;

    /** Julianisches Datum in dynamischer Zeit. */
    public final double jde;

    /** Ort der Beobachtung. */
    public final FGeoLocation location;

    /** Sonnenposition. */
    public final FSun sun;

    /** Mondposition. */
    public final FMoonPosition moon;

    /** Beleuchtung. */
    public final FMoonPhase phase;

    /** Libration. */
    public final FLibration libration;

    /**
     * Parallaktischer Winkel des Mondes in Grad: der Winkel zwischen der
     * Richtung zum Himmelsnordpol und der Senkrechten am Ort des Mondes.
     * Erst zusammen mit dem Positionswinkel der hellen Gliedmitte ergibt er
     * die Neigung der Sichel, wie sie am Himmel tatsaechlich steht.
     */
    public final double parallacticAngle;

    /**
     * Neigung der Sichel gegen die Senkrechte in Grad: Positionswinkel der
     * hellen Gliedmitte abzueglich des parallaktischen Winkels. Null heisst,
     * die helle Seite zeigt genau nach oben (die Sichel liegt wie eine
     * Schale), 90 Grad heisst, sie zeigt nach links.
     */
    public final double limbTiltFromVertical;

    /** Topozentrische Hoehe des Mondes ueber dem Horizont in Grad. */
    public final double altitude;

    /** Azimut des Mondes in Grad, von Norden ueber Osten gezaehlt. */
    public final double azimuth;

    private FMoonEphemeris(double jdUt, double jde, FGeoLocation location,
            FSun sun, FMoonPosition moon, FMoonPhase phase, FLibration libration,
            double parallacticAngle, double limbTiltFromVertical,
            double altitude, double azimuth) {
        this.jdUt = jdUt;
        this.jde = jde;
        this.location = location;
        this.sun = sun;
        this.moon = moon;
        this.phase = phase;
        this.libration = libration;
        this.parallacticAngle = parallacticAngle;
        this.limbTiltFromVertical = limbTiltFromVertical;
        this.altitude = altitude;
        this.azimuth = azimuth;
    }

    /**
     * Berechnet alle Angaben fuer einen Zeitpunkt und einen Ort.
     *
     * @param millis   Millisekunden seit 1970-01-01 00:00 UTC
     * @param location Beobachtungsort
     * @return vollstaendige Angaben
     */
    public static FMoonEphemeris at(long millis, FGeoLocation location) {
        double jdUt = FAstroTime.julianDayFromMillis(millis);
        return atJd(jdUt, location);
    }

    /**
     * Berechnet alle Angaben fuer ein julianisches Datum in Weltzeit.
     *
     * @param jdUt     julianisches Datum in UT
     * @param location Beobachtungsort
     * @return vollstaendige Angaben
     */
    public static FMoonEphemeris atJd(double jdUt, FGeoLocation location) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        FSun sun = FSun.at(jde);
        FMoonPosition moon = FMoonPosition.at(jde);
        FMoonPhase phase = FMoonPhase.of(sun, moon);
        FLibration lib = FLibration.of(jde, moon);

        // Stundenwinkel, Laenge oestlich positiv
        double theta = FAstroTime.apparentSiderealTime(jdUt);
        double hDeg = FAngle.norm360(theta + location.getLongitudeDeg() - moon.rightAscension);
        double h = Math.toRadians(hDeg);
        double dec = Math.toRadians(moon.declination);
        double phi = Math.toRadians(location.getLatitudeDeg());

        // parallaktischer Winkel, Meeus 14.1
        double q = Math.toDegrees(Math.atan2(Math.sin(h),
                Math.tan(phi) * Math.cos(dec) - Math.sin(dec) * Math.cos(h)));

        double tilt = FAngle.norm180(phase.brightLimbAngle - q);

        double alt = FRiseSet.topocentricAltitude(jdUt, location,
                moon.rightAscension, moon.declination, moon.parallax);
        double az = FAngle.norm360(180.0 + Math.toDegrees(Math.atan2(Math.sin(h),
                Math.cos(h) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi))));

        return new FMoonEphemeris(jdUt, jde, location, sun, moon, phase, lib,
                q, tilt, alt, az);
    }

    /**
     * Beleuchteter Anteil der Mondscheibe, 0 bis 1.
     *
     * @return Anteil
     */
    public double illuminatedFraction() {
        return phase.illuminatedFraction;
    }

    /**
     * Abstand Erde-Mond in Kilometern.
     *
     * @return Abstand in km
     */
    public double distanceKm() {
        return moon.distanceKm;
    }

    /**
     * Scheinbarer Durchmesser der Mondscheibe in Bogenminuten.
     *
     * @return Durchmesser in Bogenminuten
     */
    public double apparentDiameterArcmin() {
        return moon.apparentDiameterArcmin();
    }

    /**
     * Sucht alle Erdnaehen oder Erdfernen in einem Zeitraum durch Abtasten der
     * Entfernungskurve. Meeus gibt in Kapitel 50 eine eigene Reihe dafuer an;
     * hier wird bewusst im Minimum gesucht, weil der Abstand aus derselben
     * Rechnung stammt, die auch die Anzeige speist - eine zweite Reihe
     * koennte davon abweichen und zwei verschiedene Wahrheiten erzeugen.
     *
     * @param jdUtFrom Beginn der Suche als julianisches Datum in UT
     * @param jdUtTo   Ende der Suche
     * @param perigee  wahr fuer die Erdnaehe, falsch fuer die Erdferne
     * @return Zeitpunkte in zeitlicher Reihenfolge, moeglicherweise leer
     */
    public static double[] findApsides(double jdUtFrom, double jdUtTo, boolean perigee) {
        double step = 0.25;
        double[] found = new double[64];
        int count = 0;
        double prev2 = Double.NaN;
        double prev1 = Double.NaN;
        double t2 = Double.NaN;

        for (double t = jdUtFrom; t <= jdUtTo; t += step) {
            double d = FMoonPosition.at(t + FAstroTime.deltaTDays(t)).distanceKm;
            if (!Double.isNaN(prev2)) {
                boolean extremum = perigee
                        ? (prev1 < prev2 && prev1 <= d)
                        : (prev1 > prev2 && prev1 >= d);
                if (extremum) {
                    if (count == found.length) {
                        double[] groesser = new double[count * 2];
                        System.arraycopy(found, 0, groesser, 0, count);
                        found = groesser;
                    }
                    double refined = refineApsis(t2, t, perigee);
                    // Zwei Nachbedingungen, ohne die das grobe Raster von
                    // sechs Stunden auf den kleinen Nebenwellen der
                    // Abstandskurve zusaetzliche Scheinextrema meldet:
                    // Der Abstand muss im bekannten Bereich liegen, und der
                    // Abstand zum vorigen Ereignis muss zu einem Umlauf passen
                    // (der anomalistische Monat dauert 27,55 Tage).
                    double dist = distanceAt(refined);
                    boolean plausibel = perigee
                            ? (dist < 372000.0)
                            : (dist > 402000.0);
                    boolean weitGenug = (count == 0)
                            || (refined - found[count - 1]) > 20.0;
                    if (plausibel && weitGenug) {
                        found[count++] = refined;
                    }
                }
            }
            prev2 = prev1;
            prev1 = d;
            t2 = t - step;
        }
        double[] out = new double[count];
        System.arraycopy(found, 0, out, 0, count);
        return out;
    }

    /**
     * Sucht die ERSTE Erdnaehe oder Erdferne in einem Zeitraum. Liegen mehrere
     * im Fenster, gewinnt die frueheste - nicht die tiefste; sonst haengt das
     * Ergebnis davon ab, wo man das Fenster hinlegt.
     *
     * @param jdUtFrom Beginn der Suche als julianisches Datum in UT
     * @param jdUtTo   Ende der Suche
     * @param perigee  wahr fuer die Erdnaehe, falsch fuer die Erdferne
     * @return julianisches Datum des Ereignisses, oder NaN wenn keines im
     *         Zeitraum liegt
     */
    public static double findApsis(double jdUtFrom, double jdUtTo, boolean perigee) {
        double[] all = findApsides(jdUtFrom, jdUtTo, perigee);
        return (all.length == 0) ? Double.NaN : all[0];
    }

    private static double refineApsis(double a, double b, boolean perigee) {
        double phi = 0.6180339887498949;
        double lo = a;
        double hi = b;
        double x1 = hi - phi * (hi - lo);
        double x2 = lo + phi * (hi - lo);
        double f1 = distanceAt(x1);
        double f2 = distanceAt(x2);
        for (int i = 0; i < 50; i++) {
            boolean takeLeft = perigee ? (f1 < f2) : (f1 > f2);
            if (takeLeft) {
                hi = x2;
                x2 = x1;
                f2 = f1;
                x1 = hi - phi * (hi - lo);
                f1 = distanceAt(x1);
            } else {
                lo = x1;
                x1 = x2;
                f1 = f2;
                x2 = lo + phi * (hi - lo);
                f2 = distanceAt(x2);
            }
        }
        return 0.5 * (lo + hi);
    }

    private static double distanceAt(double jdUt) {
        return FMoonPosition.at(jdUt + FAstroTime.deltaTDays(jdUt)).distanceKm;
    }

    @Override
    public String toString() {
        return String.format("%s | k=%.3f (%s) | %.0f km | %.1f' | Hoehe %.1f°",
                location.getName(), phase.illuminatedFraction, phase.phaseName(),
                moon.distanceKm, apparentDiameterArcmin(), altitude);
    }
}
