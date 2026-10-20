package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Zeitrechnung fuer den FMoon-Kern: Julianisches Datum, julianische Jahrhunderte,
 * Delta T, Nutation, Schiefe der Ekliptik und Sternzeit.
 *
 * <p>Alle Verfahren nach Jean Meeus, <i>Astronomical Algorithms</i>, 2. Auflage
 * (Kapitel 7, 10, 12, 22). Alle Winkel in Grad, sofern nicht anders vermerkt.</p>
 *
 * <p>Zeitskalen: Die Reihenentwicklungen von Sonne und Mond laufen in
 * dynamischer Zeit (TD = TT). Buergerliche Zeit ist UT. Der Unterschied
 * Delta T = TD - UT wird von {@link #deltaTSeconds(double)} geliefert.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FAstroTime {

    /** Julianisches Datum der Epoche J2000,0 (2000 Januar 1,5 TD). */
    public static final double J2000 = 2451545.0;

    /** Tage eines julianischen Jahrhunderts. */
    public static final double DAYS_PER_CENTURY = 36525.0;

    /** Millisekunden je Tag. */
    public static final double MS_PER_DAY = 86400000.0;

    /** Julianisches Datum der Unix-Epoche (1970-01-01 00:00 UTC). */
    public static final double JD_UNIX_EPOCH = 2440587.5;

    private FAstroTime() {
    }

    // ------------------------------------------------------------------
    // Julianisches Datum
    // ------------------------------------------------------------------

    /**
     * Julianisches Datum aus Kalenderdatum (Meeus 7.1). Der gregorianische
     * Kalender gilt ab 1582 Oktober 15, davor der julianische.
     *
     * @param year  Jahr (astronomische Zaehlung, 1 v.Chr. = 0)
     * @param month Monat 1..12
     * @param day   Tag, gebrochen (1,5 = 1. Tag 12:00)
     * @return julianisches Datum
     */
    public static double julianDay(int year, int month, double day) {
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int b = 0;
        // gregorianisch ab 1582-10-15
        boolean gregorian = (year > 1582)
                || (year == 1582 && (month > 10 || (month == 10 && day >= 15.0)));
        if (gregorian) {
            int a = (int) Math.floor(y / 100.0);
            b = 2 - a + (int) Math.floor(a / 4.0);
        }
        return Math.floor(365.25 * (y + 4716))
                + Math.floor(30.6001 * (m + 1))
                + day + b - 1524.5;
    }

    /**
     * Julianisches Datum aus einem Unix-Zeitstempel in Millisekunden (UTC).
     * Das Ergebnis ist JD(UT); fuer die Reihen ist noch {@link #deltaTSeconds(double)}
     * zu addieren.
     *
     * @param millis Millisekunden seit 1970-01-01 00:00 UTC
     * @return julianisches Datum in UT
     */
    public static double julianDayFromMillis(long millis) {
        return JD_UNIX_EPOCH + millis / MS_PER_DAY;
    }

    /**
     * Unix-Zeitstempel aus julianischem Datum (UT).
     *
     * @param jd julianisches Datum in UT
     * @return Millisekunden seit 1970-01-01 00:00 UTC
     */
    public static long millisFromJulianDay(double jd) {
        return Math.round((jd - JD_UNIX_EPOCH) * MS_PER_DAY);
    }

    /**
     * Julianische Jahrhunderte seit J2000,0.
     *
     * @param jd julianisches Datum
     * @return T in Jahrhunderten
     */
    public static double centuries(double jd) {
        return (jd - J2000) / DAYS_PER_CENTURY;
    }

    /**
     * Kalenderdatum aus julianischem Datum (Meeus Kapitel 7, Umkehrung).
     * Liefert {Jahr, Monat, Tag, Stunde, Minute, Sekunde}.
     *
     * @param jd julianisches Datum
     * @return sechsstelliges Feld
     */
    public static int[] calendarOf(double jd) {
        double x = jd + 0.5;
        int z = (int) Math.floor(x);
        double f = x - z;
        int a = z;
        if (z >= 2299161) {
            int alpha = (int) Math.floor((z - 1867216.25) / 36524.25);
            a = z + 1 + alpha - (int) Math.floor(alpha / 4.0);
        }
        int b = a + 1524;
        int c = (int) Math.floor((b - 122.1) / 365.25);
        int d = (int) Math.floor(365.25 * c);
        int e = (int) Math.floor((b - d) / 30.6001);
        double dayFrac = b - d - Math.floor(30.6001 * e) + f;
        int day = (int) Math.floor(dayFrac);
        double frac = dayFrac - day;
        int month = (e < 14) ? e - 1 : e - 13;
        int year = (month > 2) ? c - 4716 : c - 4715;
        double hours = frac * 24.0;
        int hh = (int) Math.floor(hours);
        double minutes = (hours - hh) * 60.0;
        int mm = (int) Math.floor(minutes);
        int ss = (int) Math.round((minutes - mm) * 60.0);
        if (ss >= 60) {
            ss -= 60;
            mm++;
        }
        if (mm >= 60) {
            mm -= 60;
            hh++;
        }
        return new int[]{year, month, day, hh, mm, ss};
    }

    // ------------------------------------------------------------------
    // Delta T
    // ------------------------------------------------------------------

    /**
     * Beobachtete Werte von Delta T in Sekunden, 1960 bis 2026 in Schritten
     * von zwei Jahren (IERS/USNO, gerundet). Ausserhalb greifen Polynome.
     */
    private static final double[] DELTA_T_TABLE = {
        33.2, 34.0, 35.0, 36.5, 38.3, 40.2, 42.2, 44.5, 46.5, 48.5, // 1960..1978
        50.5, 52.2, 53.8, 54.9, 55.8, 56.9, 58.3, 60.0, 61.6, 63.0, // 1980..1998
        63.8, 64.7, 65.2, 66.1, 66.9, 68.0, 69.2, 69.4, 69.3        // 2000..2024
    };

    /**
     * Delta T = TD - UT in Sekunden. Bis 2024 aus der Tabelle interpoliert,
     * davor und danach aus den Polynomen von Espenak und Meeus (NASA Eclipse
     * Web Site, "Polynomial Expressions for Delta T").
     *
     * <p>Der kuenftige Verlauf ist nicht vorhersagbar; ab 2025 liefert die
     * Methode daher eine Schaetzung. Fuer die Mondphase ist das ohne Belang
     * (1 Sekunde entspricht 0,00015 Grad in der Mondlaenge), fuer Auf- und
     * Untergangszeiten liegt der Fehler unter einer Sekunde.</p>
     *
     * @param year dezimales Jahr, z.B. 2026,7
     * @return Delta T in Sekunden
     */
    public static double deltaTSeconds(double year) {
        if (year >= 1960.0 && year <= 2024.0) {
            double pos = (year - 1960.0) / 2.0;
            int i = (int) Math.floor(pos);
            if (i >= DELTA_T_TABLE.length - 1) {
                return DELTA_T_TABLE[DELTA_T_TABLE.length - 1];
            }
            double frac = pos - i;
            return DELTA_T_TABLE[i] * (1.0 - frac) + DELTA_T_TABLE[i + 1] * frac;
        }
        if (year > 2024.0 && year <= 2150.0) {
            // Espenak/Meeus, 2005..2050 fortgesetzt; an 2024 stetig angeschlossen
            double t = year - 2000.0;
            double raw = 62.92 + 0.32217 * t + 0.005589 * t * t;
            double rawAt2024 = 62.92 + 0.32217 * 24.0 + 0.005589 * 24.0 * 24.0;
            return raw - (rawAt2024 - DELTA_T_TABLE[DELTA_T_TABLE.length - 1]);
        }
        if (year >= 1900.0 && year < 1960.0) {
            double t = year - 1900.0;
            return -2.79 + 1.494119 * t - 0.0598939 * t * t
                    + 0.0061966 * t * t * t - 0.000197 * t * t * t * t;
        }
        if (year >= 1800.0 && year < 1900.0) {
            double t = year - 1860.0;
            return 7.62 + 0.5737 * t - 0.251754 * t * t + 0.01680668 * t * t * t
                    - 0.0004473624 * t * t * t * t + t * t * t * t * t / 233174.0;
        }
        if (year >= 1600.0 && year < 1800.0) {
            double t = year - 1700.0;
            return 8.83 + 0.1603 * t - 0.0059285 * t * t + 0.00013336 * t * t * t
                    - t * t * t * t / 1174000.0;
        }
        // sehr grobe Naeherung ausserhalb
        double u = (year - 1820.0) / 100.0;
        return -20.0 + 32.0 * u * u;
    }

    /**
     * Delta T in Tagen fuer ein julianisches Datum.
     *
     * @param jd julianisches Datum
     * @return Delta T in Tagen
     */
    public static double deltaTDays(double jd) {
        int[] cal = calendarOf(jd);
        double year = cal[0] + (cal[1] - 0.5) / 12.0;
        return deltaTSeconds(year) / 86400.0;
    }

    // ------------------------------------------------------------------
    // Nutation und Schiefe der Ekliptik
    // ------------------------------------------------------------------

    /**
     * Nutation in Laenge (Delta psi) in Grad, Meeus 22.  Verwendet werden die
     * Glieder der Tabelle 22.A mit Betraegen ab 0,0003 Bogensekunden; der
     * verbleibende Fehler liegt unter 0,5 Bogensekunden.
     *
     * @param t julianische Jahrhunderte seit J2000
     * @return Delta psi in Grad
     */
    public static double nutationInLongitude(double t) {
        double d = Math.toRadians(FAngle.norm360(297.85036 + 445267.111480 * t
                - 0.0019142 * t * t + t * t * t / 189474.0));
        double m = Math.toRadians(FAngle.norm360(357.52772 + 35999.050340 * t
                - 0.0001603 * t * t - t * t * t / 300000.0));
        double mp = Math.toRadians(FAngle.norm360(134.96298 + 477198.867398 * t
                + 0.0086972 * t * t + t * t * t / 56250.0));
        double f = Math.toRadians(FAngle.norm360(93.27191 + 483202.017538 * t
                - 0.0036825 * t * t + t * t * t / 327270.0));
        double om = Math.toRadians(FAngle.norm360(125.04452 - 1934.136261 * t
                + 0.0020708 * t * t + t * t * t / 450000.0));

        double dpsi = 0.0;
        dpsi += (-171996.0 - 174.2 * t) * Math.sin(om);
        dpsi += (-13187.0 - 1.6 * t) * Math.sin(-2 * d + 2 * f + 2 * om);
        dpsi += (-2274.0 - 0.2 * t) * Math.sin(2 * f + 2 * om);
        dpsi += (2062.0 + 0.2 * t) * Math.sin(2 * om);
        dpsi += (1426.0 - 3.4 * t) * Math.sin(m);
        dpsi += (712.0 + 0.1 * t) * Math.sin(mp);
        dpsi += (-517.0 + 1.2 * t) * Math.sin(-2 * d + m + 2 * f + 2 * om);
        dpsi += (-386.0 - 0.4 * t) * Math.sin(2 * f + om);
        dpsi += -301.0 * Math.sin(mp + 2 * f + 2 * om);
        dpsi += (217.0 - 0.5 * t) * Math.sin(-2 * d - m + 2 * f + 2 * om);
        dpsi += -158.0 * Math.sin(-2 * d + mp);
        dpsi += (129.0 + 0.1 * t) * Math.sin(-2 * d + 2 * f + om);
        dpsi += 123.0 * Math.sin(-mp + 2 * f + 2 * om);
        dpsi += 63.0 * Math.sin(2 * d);
        dpsi += (63.0 + 0.1 * t) * Math.sin(mp + om);
        dpsi += -59.0 * Math.sin(2 * d - mp + 2 * f + 2 * om);
        dpsi += (-58.0 - 0.1 * t) * Math.sin(-mp + om);
        dpsi += -51.0 * Math.sin(mp + 2 * f + om);
        dpsi += 48.0 * Math.sin(-2 * d + 2 * mp);
        dpsi += 46.0 * Math.sin(-2 * mp + 2 * f + om);
        dpsi += -38.0 * Math.sin(2 * d + 2 * f + 2 * om);
        dpsi += -31.0 * Math.sin(2 * mp + 2 * f + 2 * om);
        dpsi += 29.0 * Math.sin(2 * mp);
        dpsi += 29.0 * Math.sin(-2 * d + mp + 2 * f + 2 * om);
        dpsi += 26.0 * Math.sin(2 * f);
        dpsi += -22.0 * Math.sin(-2 * d + 2 * f);
        dpsi += 21.0 * Math.sin(-mp + 2 * f + om);
        dpsi += (17.0 - 0.1 * t) * Math.sin(2 * m);
        dpsi += 16.0 * Math.sin(2 * d - mp + om);
        dpsi += (-16.0 + 0.1 * t) * Math.sin(-2 * d + 2 * m + 2 * f + 2 * om);
        dpsi += -15.0 * Math.sin(m + om);
        dpsi += -13.0 * Math.sin(-2 * d + mp + om);
        dpsi += -12.0 * Math.sin(-m + om);
        dpsi += 11.0 * Math.sin(2 * mp - 2 * f);
        dpsi += -10.0 * Math.sin(2 * d - mp + 2 * f + om);
        dpsi += -8.0 * Math.sin(2 * d + mp + 2 * f + 2 * om);
        dpsi += 7.0 * Math.sin(m + 2 * f + 2 * om);
        dpsi += -7.0 * Math.sin(-2 * d + m + mp);
        dpsi += -7.0 * Math.sin(-m + 2 * f + 2 * om);
        dpsi += -7.0 * Math.sin(2 * d + 2 * f + om);
        dpsi += 6.0 * Math.sin(2 * d + mp);
        dpsi += 6.0 * Math.sin(-2 * d + 2 * mp + 2 * f + 2 * om);
        dpsi += 6.0 * Math.sin(-2 * d + mp + 2 * f + om);
        dpsi += -6.0 * Math.sin(2 * d - 2 * mp + om);
        dpsi += -6.0 * Math.sin(2 * d + om);
        dpsi += 5.0 * Math.sin(-m + mp);
        dpsi += -5.0 * Math.sin(-2 * d - m + 2 * f + om);
        dpsi += -5.0 * Math.sin(-2 * d + om);
        dpsi += -5.0 * Math.sin(2 * mp + 2 * f + om);
        dpsi += 4.0 * Math.sin(-2 * d + 2 * mp + om);
        dpsi += 4.0 * Math.sin(-2 * d + m + 2 * f + om);
        dpsi += 4.0 * Math.sin(mp - 2 * f);
        dpsi += -4.0 * Math.sin(-d + mp);
        dpsi += -4.0 * Math.sin(-2 * d + m);
        dpsi += -4.0 * Math.sin(d);
        dpsi += 3.0 * Math.sin(mp + 2 * f);
        dpsi += -3.0 * Math.sin(-2 * mp + 2 * f + 2 * om);
        dpsi += -3.0 * Math.sin(-d - m + mp);
        dpsi += -3.0 * Math.sin(m + mp);
        dpsi += -3.0 * Math.sin(-m + mp + 2 * f + 2 * om);
        dpsi += -3.0 * Math.sin(2 * d - m - mp + 2 * f + 2 * om);
        dpsi += 3.0 * Math.sin(3 * mp + 2 * f + 2 * om);
        dpsi += -3.0 * Math.sin(2 * d - m + 2 * f + 2 * om);
        // Einheit der Tabelle: 0,0001 Bogensekunden
        return dpsi * 0.0001 / 3600.0;
    }

    /**
     * Nutation in der Schiefe (Delta epsilon) in Grad, Meeus 22.
     *
     * @param t julianische Jahrhunderte seit J2000
     * @return Delta epsilon in Grad
     */
    public static double nutationInObliquity(double t) {
        double d = Math.toRadians(FAngle.norm360(297.85036 + 445267.111480 * t
                - 0.0019142 * t * t + t * t * t / 189474.0));
        double m = Math.toRadians(FAngle.norm360(357.52772 + 35999.050340 * t
                - 0.0001603 * t * t - t * t * t / 300000.0));
        double mp = Math.toRadians(FAngle.norm360(134.96298 + 477198.867398 * t
                + 0.0086972 * t * t + t * t * t / 56250.0));
        double f = Math.toRadians(FAngle.norm360(93.27191 + 483202.017538 * t
                - 0.0036825 * t * t + t * t * t / 327270.0));
        double om = Math.toRadians(FAngle.norm360(125.04452 - 1934.136261 * t
                + 0.0020708 * t * t + t * t * t / 450000.0));

        double deps = 0.0;
        deps += (92025.0 + 8.9 * t) * Math.cos(om);
        deps += (5736.0 - 3.1 * t) * Math.cos(-2 * d + 2 * f + 2 * om);
        deps += (977.0 - 0.5 * t) * Math.cos(2 * f + 2 * om);
        deps += (-895.0 + 0.5 * t) * Math.cos(2 * om);
        deps += (54.0 - 0.1 * t) * Math.cos(m);
        deps += -7.0 * Math.cos(mp);
        deps += (224.0 - 0.6 * t) * Math.cos(-2 * d + m + 2 * f + 2 * om);
        deps += 200.0 * Math.cos(2 * f + om);
        deps += (129.0 - 0.1 * t) * Math.cos(mp + 2 * f + 2 * om);
        deps += (-95.0 + 0.3 * t) * Math.cos(-2 * d - m + 2 * f + 2 * om);
        deps += -70.0 * Math.cos(-2 * d + 2 * f + om);
        deps += -53.0 * Math.cos(-mp + 2 * f + 2 * om);
        deps += -33.0 * Math.cos(mp + om);
        deps += 26.0 * Math.cos(2 * d - mp + 2 * f + 2 * om);
        deps += 32.0 * Math.cos(-mp + om);
        deps += 27.0 * Math.cos(mp + 2 * f + om);
        deps += -24.0 * Math.cos(-2 * mp + 2 * f + om);
        deps += 16.0 * Math.cos(2 * d + 2 * f + 2 * om);
        deps += 13.0 * Math.cos(2 * mp + 2 * f + 2 * om);
        deps += -12.0 * Math.cos(-2 * d + mp + 2 * f + 2 * om);
        deps += -10.0 * Math.cos(-mp + 2 * f + om);
        deps += -8.0 * Math.cos(2 * d - mp + om);
        deps += 7.0 * Math.cos(-2 * d + 2 * m + 2 * f + 2 * om);
        deps += 9.0 * Math.cos(m + om);
        deps += 7.0 * Math.cos(-2 * d + mp + om);
        deps += 6.0 * Math.cos(-m + om);
        deps += 5.0 * Math.cos(2 * d - mp + 2 * f + om);
        deps += 3.0 * Math.cos(2 * d + mp + 2 * f + 2 * om);
        deps += -3.0 * Math.cos(m + 2 * f + 2 * om);
        deps += 3.0 * Math.cos(-m + 2 * f + 2 * om);
        deps += 3.0 * Math.cos(2 * d + 2 * f + om);
        deps += -3.0 * Math.cos(-2 * d + 2 * mp + 2 * f + 2 * om);
        deps += -3.0 * Math.cos(-2 * d + mp + 2 * f + om);
        deps += 3.0 * Math.cos(2 * d - 2 * mp + om);
        deps += 3.0 * Math.cos(2 * d + om);
        deps += 3.0 * Math.cos(-2 * d - m + 2 * f + om);
        deps += 3.0 * Math.cos(-2 * d + om);
        deps += 3.0 * Math.cos(2 * mp + 2 * f + om);
        return deps * 0.0001 / 3600.0;
    }

    /**
     * Mittlere Schiefe der Ekliptik in Grad (Meeus 22.3, Laskar).
     *
     * @param t julianische Jahrhunderte seit J2000
     * @return epsilon_0 in Grad
     */
    public static double meanObliquity(double t) {
        double u = t / 100.0;
        double sec = 21.448
                - u * (4680.93
                + u * (1.55
                - u * (1999.25
                - u * (51.38
                + u * (249.67
                + u * (39.05
                - u * (7.12
                + u * (27.87
                + u * (5.79 + u * 2.45)))))))));
        return 23.0 + 26.0 / 60.0 + sec / 3600.0;
    }

    /**
     * Wahre Schiefe der Ekliptik in Grad (mittlere Schiefe plus Nutation).
     *
     * @param t julianische Jahrhunderte seit J2000
     * @return epsilon in Grad
     */
    public static double trueObliquity(double t) {
        return meanObliquity(t) + nutationInObliquity(t);
    }

    // ------------------------------------------------------------------
    // Sternzeit
    // ------------------------------------------------------------------

    /**
     * Mittlere Sternzeit zu Greenwich in Grad (Meeus 12.4), fuer ein
     * beliebiges julianisches Datum in UT.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @return mittlere Sternzeit in Grad, 0..360
     */
    public static double meanSiderealTime(double jdUt) {
        double t = centuries(jdUt);
        double theta = 280.46061837
                + 360.98564736629 * (jdUt - J2000)
                + 0.000387933 * t * t
                - t * t * t / 38710000.0;
        return FAngle.norm360(theta);
    }

    /**
     * Scheinbare Sternzeit zu Greenwich in Grad (mittlere Sternzeit plus
     * Nutation in Rektaszension).
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @return scheinbare Sternzeit in Grad, 0..360
     */
    public static double apparentSiderealTime(double jdUt) {
        double t = centuries(jdUt);
        double dpsi = nutationInLongitude(t);
        double eps = trueObliquity(t);
        return FAngle.norm360(meanSiderealTime(jdUt)
                + dpsi * Math.cos(Math.toRadians(eps)));
    }

    // ------------------------------------------------------------------
    // Bequemlichkeit
    // ------------------------------------------------------------------

    /**
     * Julianisches Datum in dynamischer Zeit aus einem Unix-Zeitstempel.
     *
     * @param millis Millisekunden seit 1970-01-01 00:00 UTC
     * @return JDE (dynamische Zeit)
     */
    public static double jdeFromMillis(long millis) {
        double jdUt = julianDayFromMillis(millis);
        return jdUt + deltaTDays(jdUt);
    }

    /**
     * Kalenderobjekt in einer Zeitzone aus einem julianischen Datum in UT.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @param zone Zeitzone
     * @return Kalender in dieser Zone
     */
    public static Calendar calendarOf(double jdUt, TimeZone zone) {
        Calendar cal = new GregorianCalendar(zone);
        cal.setTime(new Date(millisFromJulianDay(jdUt)));
        return cal;
    }
}
