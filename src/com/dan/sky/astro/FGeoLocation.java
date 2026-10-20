package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

import java.io.Serializable;
import java.util.TimeZone;

/**
 * Ein Beobachtungsort auf der Erde.
 *
 * <p>Die geografische Laenge wird nach heutiger Gewohnheit oestlich positiv
 * gezaehlt. Meeus zaehlt in seinem Buch westlich positiv; die Umrechnung
 * geschieht an genau einer Stelle, naemlich im Stundenwinkel in
 * {@link FRiseSet}, und ist dort vermerkt.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FGeoLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Timmendorfer Strand an der Lubecker Bucht (Ostsee). */
    public static final FGeoLocation TIMMENDORFER_STRAND =
            new FGeoLocation("Timmendorfer Strand", 53.9950, 10.7783, 3.0, "Europe/Berlin");

    /** Niendorfer Hafen, unmittelbar suedlich davon. */
    public static final FGeoLocation NIENDORF =
            new FGeoLocation("Niendorf/Ostsee", 53.9806, 10.8156, 2.0, "Europe/Berlin");

    /** Luebeck. */
    public static final FGeoLocation LUEBECK =
            new FGeoLocation("Lübeck", 53.8654, 10.6866, 13.0, "Europe/Berlin");

    /** Buesum an der Nordsee. */
    public static final FGeoLocation BUESUM =
            new FGeoLocation("Büsum", 54.1333, 8.8578, 2.0, "Europe/Berlin");

    /** Husum an der Nordsee. */
    public static final FGeoLocation HUSUM =
            new FGeoLocation("Husum", 54.4739, 9.0517, 4.0, "Europe/Berlin");

    /** Helgoland. */
    public static final FGeoLocation HELGOLAND =
            new FGeoLocation("Helgoland", 54.1797, 7.8859, 5.0, "Europe/Berlin");

    /** List auf Sylt. */
    public static final FGeoLocation LIST_SYLT =
            new FGeoLocation("List auf Sylt", 55.0181, 8.4406, 3.0, "Europe/Berlin");

    /** Cuxhaven an der Elbmuendung. */
    public static final FGeoLocation CUXHAVEN =
            new FGeoLocation("Cuxhaven", 53.8700, 8.7050, 3.0, "Europe/Berlin");

    /** Alle vorgegebenen Orte in einer Reihenfolge von Ost nach West. */
    public static final FGeoLocation[] DEFAULTS = {
        TIMMENDORFER_STRAND, NIENDORF, LUEBECK, CUXHAVEN, BUESUM, HUSUM, HELGOLAND, LIST_SYLT
    };

    private final String name;
    private final double latitudeDeg;
    private final double longitudeDeg;
    private final double elevationMeters;
    private final String timeZoneId;

    /**
     * Legt einen Ort an.
     *
     * @param name            Anzeigename
     * @param latitudeDeg     geografische Breite in Grad, Nord positiv
     * @param longitudeDeg    geografische Laenge in Grad, Ost positiv
     * @param elevationMeters Hoehe ueber dem Meeresspiegel in Metern
     * @param timeZoneId      Kennung der Zeitzone, etwa "Europe/Berlin"
     */
    public FGeoLocation(String name, double latitudeDeg, double longitudeDeg,
            double elevationMeters, String timeZoneId) {
        this.name = name;
        this.latitudeDeg = latitudeDeg;
        this.longitudeDeg = longitudeDeg;
        this.elevationMeters = elevationMeters;
        this.timeZoneId = timeZoneId;
    }

    /**
     * Anzeigename des Ortes.
     *
     * @return Name
     */
    public String getName() {
        return name;
    }

    /**
     * Geografische Breite.
     *
     * @return Breite in Grad, Nord positiv
     */
    public double getLatitudeDeg() {
        return latitudeDeg;
    }

    /**
     * Geografische Laenge.
     *
     * @return Laenge in Grad, Ost positiv
     */
    public double getLongitudeDeg() {
        return longitudeDeg;
    }

    /**
     * Hoehe ueber dem Meeresspiegel.
     *
     * @return Hoehe in Metern
     */
    public double getElevationMeters() {
        return elevationMeters;
    }

    /**
     * Zeitzone des Ortes.
     *
     * @return Zeitzone
     */
    public TimeZone getTimeZone() {
        return TimeZone.getTimeZone(timeZoneId);
    }

    /**
     * Kennung der Zeitzone.
     *
     * @return Kennung, etwa "Europe/Berlin"
     */
    public String getTimeZoneId() {
        return timeZoneId;
    }

    /**
     * Der Faktor rho*sin(phi') fuer die topozentrische Umrechnung
     * (Meeus Kapitel 11), berechnet aus Breite und Hoehe mit der Abplattung
     * des IAU-1976-Ellipsoids.
     *
     * @return rho*sin(phi')
     */
    public double rhoSinPhiPrime() {
        double phi = Math.toRadians(latitudeDeg);
        double u = Math.atan(0.99664719 * Math.tan(phi));
        return 0.99664719 * Math.sin(u) + (elevationMeters / 6378140.0) * Math.sin(phi);
    }

    /**
     * Der Faktor rho*cos(phi') fuer die topozentrische Umrechnung.
     *
     * @return rho*cos(phi')
     */
    public double rhoCosPhiPrime() {
        double phi = Math.toRadians(latitudeDeg);
        double u = Math.atan(0.99664719 * Math.tan(phi));
        return Math.cos(u) + (elevationMeters / 6378140.0) * Math.cos(phi);
    }

    @Override
    public String toString() {
        return name;
    }
}
