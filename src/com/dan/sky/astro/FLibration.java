package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Libration des Mondes nach Meeus, <i>Astronomical Algorithms</i>, Kapitel 53.
 *
 * <p>Gerechnet wird die optische Libration in Laenge und Breite, dazu der
 * Positionswinkel der Rotationsachse. Die physische Libration bleibt
 * aussen vor: Sie erreicht nur 0,04 Grad und waere auf einer Bildschirmkugel
 * von wenigen hundert Bildpunkten nicht zu sehen - ein Zehntel Bildpunkt am
 * Rand. Die optische Libration dagegen betraegt bis zu 7,9 Grad in Laenge
 * und 6,7 Grad in Breite und ist deutlich sichtbar: Der Mond wackelt.</p>
 *
 * <p>Pruefstein ist Beispiel 53.a: 1992 April 12, 0h TD ergibt die optische
 * Libration l' = -1,206 Grad und b' = +4,194 Grad.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FLibration {

    /** Neigung des Mondaequators gegen die Ekliptik in Grad. */
    public static final double INCLINATION = 1.54242;

    /** Optische Libration in Laenge in Grad, positiv nach Osten. */
    public final double longitude;

    /** Optische Libration in Breite in Grad, positiv nach Norden. */
    public final double latitude;

    /** Positionswinkel der Rotationsachse in Grad. */
    public final double axisPositionAngle;

    private FLibration(double longitude, double latitude, double axisPositionAngle) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.axisPositionAngle = axisPositionAngle;
    }

    /**
     * Berechnet die Libration fuer ein julianisches Datum in dynamischer Zeit.
     *
     * @param jde julianisches Datum in TD
     * @return Librationswinkel
     */
    public static FLibration at(double jde) {
        return of(jde, FMoonPosition.at(jde));
    }

    /**
     * Berechnet die Libration aus einer bereits vorliegenden Mondposition.
     *
     * @param jde  julianisches Datum in TD
     * @param moon Mondposition zu demselben Zeitpunkt
     * @return Librationswinkel
     */
    public static FLibration of(double jde, FMoonPosition moon) {
        double t = FAstroTime.centuries(jde);
        double i = Math.toRadians(INCLINATION);

        // W = lambda - Delta psi - Omega, mit der Laenge OHNE Nutation
        double w = Math.toRadians(FAngle.norm360(
                moon.longitudeWithoutNutation - moon.ascendingNode));
        double beta = Math.toRadians(moon.latitude);

        // Meeus 53.1
        double a = Math.atan2(
                Math.sin(w) * Math.cos(beta) * Math.cos(i) - Math.sin(beta) * Math.sin(i),
                Math.cos(w) * Math.cos(beta));
        double lPrime = FAngle.norm180(Math.toDegrees(a) - moon.argumentOfLatitude);
        double bPrime = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0,
                -Math.sin(w) * Math.cos(beta) * Math.sin(i) - Math.sin(beta) * Math.cos(i)))));

        // Positionswinkel der Achse, Meeus 53.5 (ohne die physische Libration)
        double dpsi = FAstroTime.nutationInLongitude(t);
        double eps = Math.toRadians(FAstroTime.trueObliquity(t));
        double omega = Math.toRadians(moon.ascendingNode);
        double v = omega + Math.toRadians(dpsi);
        double x = Math.sin(i) * Math.sin(v);
        double y = Math.sin(i) * Math.cos(v) * Math.cos(eps) - Math.cos(i) * Math.sin(eps);
        double omegaAngle = Math.atan2(x, y);
        double bRad = Math.toRadians(bPrime);
        double alphaM = Math.toRadians(moon.rightAscension);
        double p = Math.asin(Math.max(-1.0, Math.min(1.0,
                Math.sqrt(x * x + y * y) * Math.cos(alphaM - omegaAngle)
                / Math.cos(bRad))));

        return new FLibration(lPrime, bPrime, Math.toDegrees(p));
    }

    @Override
    public String toString() {
        return String.format("Libration: l'=%+.3f° b'=%+.3f° P=%+.3f°",
                longitude, latitude, axisPositionAngle);
    }
}
