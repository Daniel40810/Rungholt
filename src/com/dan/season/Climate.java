package com.dan.season;

import java.util.SplittableRandom;

/**
 * Das Klima eines Orts als Monatstabelle und ein daraus gewürfeltes Jahr Tag für Tag. Vorgabe ist Schleswig an der
 * Schlei (Mittel 1991–2020 des Deutschen Wetterdienstes, gerundet): Januar 1,5 °C, Juli 17,2 °C, im Jahr rund 810 mm
 * Niederschlag an 180 Tagen. Für 950 sind dieselben Zahlen gesetzt [G]; die mittelalterliche Warmzeit war im Norden
 * kaum ein Grad wärmer, und einzelne Winter waren hart.
 * <p>
 * Das gewürfelte Jahr: Tagesmittel = Monatsmittel (zwischen den Monatsmitten glatt) + eine Abweichung, die von Tag zu
 * Tag nachwirkt (AR(1), ρ = 0,8, im Winter ±3,5 K, im Sommer ±2,2 K); an Regentagen fällt eine Menge nach einer
 * Exponentialverteilung, so dass Monatssumme und Zahl der Regentage stimmen. Gleicher Startwert, gleiches Jahr.
 * <p>
 * Nur Java 21; keine Abhängigkeit zu anderen Packages (Paketvertrag).
 */
public final class Climate {
    /** Monatsmittel der Lufttemperatur (°C), Januar bis Dezember. */
    public final double[] temp;
    /** Monatssumme des Niederschlags (mm) und Zahl der Tage mit mindestens 1 mm. */
    public final double[] precip, wetDays;
    /** Streuung der Tagesmittel um das Monatsmittel (K) im Winter und im Sommer. */
    public double sigmaWinter = 3.5, sigmaSummer = 2.2, rho = 0.8;
    /** Tag im Jahr mitten im Sommer (Vorgabe 196, Mitte Juli; seit Glacier Phase 11 auf der Südhalbkugel 15). */
    public int summerDay = 196;

    public Climate(double[] temp, double[] precip, double[] wetDays) {
        if (temp.length != 12 || precip.length != 12 || wetDays.length != 12) throw new IllegalArgumentException("season: 12 Monate erwartet");
        this.temp = temp.clone();
        this.precip = precip.clone();
        this.wetDays = wetDays.clone();
    }

    /** Schleswig an der Schlei, auch für Haithabu um 950 [G]. */
    public static Climate schleswig() {
        return new Climate(new double[]{1.5, 1.8, 4.0, 7.8, 11.8, 14.9, 17.2, 17.0, 13.8, 9.7, 5.6, 2.7},
                new double[]{70, 52, 55, 42, 55, 72, 82, 83, 70, 78, 75, 75},
                new double[]{17, 14, 15, 12, 12, 13, 14, 15, 15, 16, 18, 18});
    }

    /**
     * Seit Rungholt Phase 9: Nordfriesland an der Küste bei Husum (DWD-Mittel 1991–2020 gerundet [G]): Januar 1,5 °C,
     * Juli 17,3 °C, rund 870 mm im Jahr, der meiste Regen im Spätsommer und Herbst; milder als im Binnenland, die
     * Nordsee hält den Herbst warm.
     */
    public static Climate nordfriesland() {
        return new Climate(new double[]{1.5, 1.8, 4.0, 7.8, 11.8, 15.0, 17.3, 17.2, 14.3, 10.4, 6.0, 2.9},
                new double[]{76, 56, 56, 38, 50, 68, 80, 90, 86, 92, 88, 84},
                new double[]{17, 14, 14, 10, 11, 12, 13, 14, 15, 17, 18, 18});
    }

    /**
     * Seit Glacier Phase 10/11: El Calafate am Lago Argentino (Servicio Meteorológico Nacional, Mittel 1991–2020):
     * Januar 13,9 °C, Juli 0,6 °C, im Jahr nur 161 mm an 43 Tagen (Regenschatten der Anden).
     */
    public static Climate elCalafate() {
        return new Climate(new double[]{13.9, 13.6, 10.9, 7.1, 3.4, 1.0, 0.6, 2.0, 5.2, 8.0, 10.7, 12.8},
                new double[]{6.9, 13.7, 16.7, 19.5, 20.3, 14.6, 13.6, 14.2, 11.4, 13.5, 6.9, 9.5},
                new double[]{2.1, 3.1, 4.0, 4.3, 4.9, 4.9, 4.2, 4.7, 3.3, 3.0, 2.0, 2.7}).south();
    }

    /** Südhalbkugel: Sommer Mitte Januar (für die Streuung der Tage). */
    public Climate south() { summerDay = 15; return this; }

    /**
     * Seit Glacier Phase 11: vor dem Perito-Moreno-Gletscher, 78 km westlich von El Calafate [G]. Die Temperaturen
     * 1,5 K kühler (die Eisfront und das Gletscherwasser kühlen, Westwind vom Eisfeld), der Niederschlag fünfmal so
     * hoch an zweieinhalbmal so vielen Tagen (der Regenschatten setzt erst östlich ein; im Wald an der Front rund 800 mm).
     * Die Streuung der Tage ist im Winter größer (kalte Polarluft, föhnige Westlagen).
     */
    public static Climate peritoMoreno() {
        Climate e = elCalafate();
        double[] t = new double[12], p = new double[12], w = new double[12];
        for (int m = 0; m < 12; m++) { t[m] = e.temp[m] - 1.5; p[m] = e.precip[m] * 5; w[m] = Math.min(26, e.wetDays[m] * 2.5); }
        Climate c = new Climate(t, p, w).south();
        c.sigmaWinter = 3.8;
        c.sigmaSummer = 2.4;
        return c;
    }

    /** Länge der Monate (kein Schaltjahr; der 29. Februar zählt wie der 28.). */
    static final int[] DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    /** Tag im Jahr (1..365) der Mitte des Monats m (0..11). */
    static double mid(int m) {
        double d = 0;
        for (int i = 0; i < m; i++) d += DAYS[i];
        return d + DAYS[m] / 2.0 + 0.5;
    }

    /** Mittlere Tagestemperatur (°C) am Tag doy (1..365), zwischen den Monatsmitten glatt (kosinusförmig) verbunden. */
    public double meanTemp(double doy) { return interp(temp, doy); }

    /** Mittlerer Niederschlag je Tag (mm) am Tag doy. */
    public double meanPrecipPerDay(double doy) {
        int m = month(doy);
        return precip[m] / DAYS[m];
    }

    /** Wahrscheinlichkeit eines Regentags am Tag doy. */
    public double wetChance(double doy) {
        int m = month(doy);
        return Math.min(0.95, wetDays[m] / DAYS[m]);
    }

    static int month(double doy) {
        double d = ((doy - 1) % 365 + 365) % 365;
        int m = 0;
        while (m < 11 && d >= DAYS[m]) { d -= DAYS[m]; m++; }
        return m;
    }

    private static double interp(double[] v, double doy) {
        double d = ((doy - 1) % 365 + 365) % 365 + 1;
        int a = 11;
        for (int m = 0; m < 12; m++) if (mid(m) <= d) a = m;
        int b = (a + 1) % 12;
        double da = mid(a), db = mid(b) + (b < a ? 365 : 0), x = d < da ? d + 365 : d;
        double f = (x - da) / (db - da);
        f = 0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, f)));
        return v[a] + (v[b] - v[a]) * f;
    }

    /**
     * Ein gewürfeltes Jahr: je Tag Tagesmittel (°C) und Niederschlag (mm), von Tag fromDoy des Vorjahrs (negativ
     * gezählt: −121 ist der 1. September des Vorjahrs) bis zum Tag toDoy des Jahrs. Gleicher seed und year, gleiche Zahlen.
     */
    public double[][] days(long seed, int year, int fromDoy, int toDoy) {
        int n = toDoy - fromDoy + 1;
        double[] t = new double[n], p = new double[n];
        SplittableRandom r = new SplittableRandom(seed * 1_000_003L + year * 7919L);
        double anom = 0;
        // ein paar Tage Vorlauf, damit die Abweichung schon eingeschwungen ist
        for (int i = 0; i < 20; i++) anom = rho * anom + Math.sqrt(1 - rho * rho) * gauss(r);
        for (int i = 0; i < n; i++) {
            int doy = fromDoy + i;
            double dd = doy <= 0 ? doy + 365 : doy;
            double sum = 0.5 + 0.5 * Math.cos(2 * Math.PI * (dd - summerDay) / 365);      // 1 im Sommer, 0 im Winter
            double sig = sigmaWinter + (sigmaSummer - sigmaWinter) * sum;
            anom = rho * anom + Math.sqrt(1 - rho * rho) * gauss(r);
            t[i] = meanTemp(dd) + sig * anom;
            // nasse Tage hängen zusammen (Markow-Kette, Anteil der nassen Tage bleibt wetChance)
            double pw = wetChance(dd), p11 = Math.min(0.95, pw * 1.3), p01 = pw * (1 - p11) / Math.max(0.05, 1 - pw);
            double chance = i > 0 && p[i - 1] > 0 ? p11 : p01;
            double u = r.nextDouble();
            if (u < chance) {
                double mean = meanPrecipPerDay(dd) / Math.max(0.05, wetChance(dd));
                p[i] = -mean * Math.log(1 - r.nextDouble() * 0.999);
            }
        }
        return new double[][]{t, p};
    }

    private static double gauss(SplittableRandom r) {
        double u = Math.max(1e-12, r.nextDouble()), v = r.nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }
}
