package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Beleuchtung des Mondes zu einem Zeitpunkt: Phasenwinkel, beleuchteter
 * Anteil, Positionswinkel der hellen Gliedmitte und Mondalter.
 *
 * <p>Nach Meeus, <i>Astronomical Algorithms</i>, Kapitel 48. Pruefstein ist
 * Beispiel 48.a: 1992 April 12, 0h TD ergibt den Phasenwinkel i = 69,0756 Grad
 * und den beleuchteten Anteil k = 0,6786.</p>
 *
 * <p>Der Positionswinkel chi ist die Groesse, an der sich eine gemalte Sichel
 * von einer gerechneten unterscheidet: Er sagt, wohin die helle Seite am
 * Himmel zeigt, und aendert sich im Lauf einer Nacht und einer Jahreszeit
 * betraechtlich. Er wird vom Nordpunkt der Mondscheibe gegen Osten gezaehlt.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonPhase {

    /** Laenge des mittleren synodischen Monats in Tagen. */
    public static final double SYNODIC_MONTH = 29.530588861;

    /** Phasenwinkel Sonne-Mond-Erde in Grad (0 = Vollmond, 180 = Neumond). */
    public final double phaseAngle;

    /** Beleuchteter Anteil der Scheibe, 0 bis 1. */
    public final double illuminatedFraction;

    /**
     * Positionswinkel der hellen Gliedmitte in Grad, vom Himmelsnordpol aus
     * gegen Osten gezaehlt.
     */
    public final double brightLimbAngle;

    /**
     * Elongation vom Sonnenmittelpunkt in Grad, 0 bis 180 - der scheinbare
     * Abstand am Himmel.
     */
    public final double elongation;

    /**
     * Differenz der ekliptikalen Laengen Mond minus Sonne in Grad, 0 bis 360.
     * Diese Groesse waechst gleichmaessig durch den Monat und ist das Mass
     * fuer die Phasenlage: 0 = Neumond, 90 = erstes Viertel, 180 = Vollmond,
     * 270 = letztes Viertel.
     */
    public final double phaseAngleFromLongitudes;

    /** Alter des Mondes in Tagen seit dem letzten Neumond (genaehert). */
    public final double ageDays;

    /** Wahr, solange der Mond zunimmt. */
    public final boolean waxing;

    private FMoonPhase(double phaseAngle, double illuminatedFraction,
            double brightLimbAngle, double elongation,
            double phaseAngleFromLongitudes, double ageDays, boolean waxing) {
        this.phaseAngle = phaseAngle;
        this.illuminatedFraction = illuminatedFraction;
        this.brightLimbAngle = brightLimbAngle;
        this.elongation = elongation;
        this.phaseAngleFromLongitudes = phaseAngleFromLongitudes;
        this.ageDays = ageDays;
        this.waxing = waxing;
    }

    /**
     * Berechnet die Beleuchtung aus bereits vorliegenden Positionen.
     *
     * @param sun  Sonnenposition
     * @param moon Mondposition
     * @return Beleuchtungsangaben
     */
    public static FMoonPhase of(FSun sun, FMoonPosition moon) {
        double raS = Math.toRadians(sun.rightAscension);
        double decS = Math.toRadians(sun.declination);
        double raM = Math.toRadians(moon.rightAscension);
        double decM = Math.toRadians(moon.declination);

        // Elongation, Meeus 48.2
        double cosPsi = Math.sin(decS) * Math.sin(decM)
                + Math.cos(decS) * Math.cos(decM) * Math.cos(raS - raM);
        cosPsi = Math.max(-1.0, Math.min(1.0, cosPsi));
        double psi = Math.acos(cosPsi);

        // Phasenwinkel, Meeus 48.3. Der Sonnenabstand muss in dieselbe
        // Einheit wie der Mondabstand - eine AE in Kilometern.
        double rKm = sun.distanceAu * 149597870.7;
        double i = Math.atan2(rKm * Math.sin(psi), moon.distanceKm - rKm * Math.cos(psi));
        double iDeg = FAngle.norm360(Math.toDegrees(i));
        if (iDeg > 180.0) {
            iDeg = 360.0 - iDeg;
        }

        // beleuchteter Anteil, Meeus 48.1
        double k = (1.0 + Math.cos(Math.toRadians(iDeg))) / 2.0;

        // Positionswinkel der hellen Gliedmitte, Meeus 48.5
        double chi = Math.atan2(
                Math.cos(decS) * Math.sin(raS - raM),
                Math.sin(decS) * Math.cos(decM)
                - Math.cos(decS) * Math.sin(decM) * Math.cos(raS - raM));
        double chiDeg = FAngle.norm360(Math.toDegrees(chi));

        // Phasenlage aus den ekliptikalen Laengen
        double dLon = FAngle.norm360(moon.longitude - sun.apparentLongitude);
        double age = dLon / 360.0 * SYNODIC_MONTH;
        boolean wax = dLon < 180.0;

        return new FMoonPhase(iDeg, k, chiDeg, Math.toDegrees(psi), dLon, age, wax);
    }

    /**
     * Berechnet die Beleuchtung fuer ein julianisches Datum in dynamischer Zeit.
     *
     * @param jde julianisches Datum in TD
     * @return Beleuchtungsangaben
     */
    public static FMoonPhase at(double jde) {
        return of(FSun.at(jde), FMoonPosition.at(jde));
    }

    /**
     * Name der Phase in acht Stufen. Die Grenzen der vier ausgezeichneten
     * Phasen sind bewusst eng gesetzt (ein Grad Laengendifferenz, rund zwei
     * Stunden), damit "Vollmond" im Kalender den Tag meint und nicht die
     * halbe Woche.
     *
     * @return deutscher Name der Phase
     */
    public String phaseName() {
        double d = phaseAngleFromLongitudes;
        if (d < 1.0 || d >= 359.0) {
            return "Neumond";
        }
        if (Math.abs(d - 90.0) < 1.0) {
            return "erstes Viertel";
        }
        if (Math.abs(d - 180.0) < 1.0) {
            return "Vollmond";
        }
        if (Math.abs(d - 270.0) < 1.0) {
            return "letztes Viertel";
        }
        if (d < 90.0) {
            return "zunehmende Sichel";
        }
        if (d < 180.0) {
            return "zunehmender Mond";
        }
        if (d < 270.0) {
            return "abnehmender Mond";
        }
        return "abnehmende Sichel";
    }

    @Override
    public String toString() {
        return String.format("Phase: i=%.4f° k=%.4f chi=%.2f° Alter=%.2f d (%s)",
                phaseAngle, illuminatedFraction, brightLimbAngle, ageDays, phaseName());
    }
}
