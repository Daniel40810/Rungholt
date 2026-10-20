package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

import java.util.ArrayList;
import java.util.List;

/**
 * Zeitpunkte von Neumond, erstem Viertel, Vollmond und letztem Viertel nach
 * Meeus, <i>Astronomical Algorithms</i>, Kapitel 49.
 *
 * <p>Die Zeitpunkte werden nicht durch Nullstellensuche in der Phasenkurve
 * bestimmt, sondern aus der eigenen Reihenentwicklung des Kapitels: mittlere
 * Phase plus Korrekturglieder plus vierzehn Planetenargumente. Das ist
 * genauer (wenige Sekunden) und schneller, und es ist unabhaengig vom
 * Rechenkern fuer die Position - zwei Wege zum selben Ergebnis, die sich
 * gegenseitig pruefen lassen.</p>
 *
 * <p>Pruefsteine: Beispiel 49.a (Neumond Februar 1977, JDE 2443192,94102)
 * und Beispiel 49.b (letztes Viertel Januar 2044, JDE 2467636,49186).</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonPhaseTimes {

    /** Neumond. */
    public static final int NEW_MOON = 0;
    /** Erstes Viertel. */
    public static final int FIRST_QUARTER = 1;
    /** Vollmond. */
    public static final int FULL_MOON = 2;
    /** Letztes Viertel. */
    public static final int LAST_QUARTER = 3;

    private FMoonPhaseTimes() {
    }

    /**
     * Eine einzelne Mondphase mit Zeitpunkt.
     */
    public static final class Phase {

        /** Art der Phase, eine der Konstanten dieser Klasse. */
        public final int type;

        /** Julianisches Datum in dynamischer Zeit. */
        public final double jde;

        /** Julianisches Datum in Weltzeit. */
        public final double jdUt;

        /** Laufende Nummer k der Phase seit der Epoche 2000 Januar 6. */
        public final double k;

        Phase(int type, double jde, double k) {
            this.type = type;
            this.jde = jde;
            this.k = k;
            this.jdUt = jde - FAstroTime.deltaTDays(jde);
        }

        /**
         * Zeitpunkt als Unix-Zeitstempel in Millisekunden (UTC).
         *
         * @return Millisekunden seit 1970-01-01
         */
        public long millis() {
            return FAstroTime.millisFromJulianDay(jdUt);
        }

        /**
         * Deutscher Name der Phase.
         *
         * @return Name
         */
        public String name() {
            switch (type) {
                case NEW_MOON:
                    return "Neumond";
                case FIRST_QUARTER:
                    return "erstes Viertel";
                case FULL_MOON:
                    return "Vollmond";
                default:
                    return "letztes Viertel";
            }
        }

        @Override
        public String toString() {
            int[] c = FAstroTime.calendarOf(jdUt);
            return String.format("%s %04d-%02d-%02d %02d:%02d UT",
                    name(), c[0], c[1], c[2], c[3], c[4]);
        }
    }

    /**
     * Berechnet den Zeitpunkt einer Phase aus ihrer laufenden Nummer.
     *
     * @param k ganzzahlig fuer Neumond, +0,25 erstes Viertel, +0,5 Vollmond,
     *          +0,75 letztes Viertel; k = 0 entspricht dem Neumond vom
     *          6. Januar 2000
     * @return Zeitpunkt der Phase
     */
    public static Phase phaseAt(double k) {
        double t = k / 1236.85;
        double t2 = t * t;
        double t3 = t2 * t;
        double t4 = t3 * t;

        // mittlere Phase, Meeus 49.1
        double jde = 2451550.09766 + 29.530588861 * k
                + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4;

        double e = 1.0 - 0.002516 * t - 0.0000074 * t2;
        double e2 = e * e;

        double m = FAngle.norm360(2.5534 + 29.10535670 * k
                - 0.0000014 * t2 - 0.00000011 * t3);
        double mp = FAngle.norm360(201.5643 + 385.81693528 * k
                + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4);
        double f = FAngle.norm360(160.7108 + 390.67050284 * k
                - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4);
        double om = FAngle.norm360(124.7746 - 1.56375588 * k
                + 0.0020672 * t2 + 0.00000215 * t3);

        int type = phaseTypeOf(k);
        double corr;
        if (type == NEW_MOON) {
            corr = newMoonCorrection(e, e2, m, mp, f, om);
        } else if (type == FULL_MOON) {
            corr = fullMoonCorrection(e, e2, m, mp, f, om);
        } else {
            corr = quarterCorrection(e, e2, m, mp, f, om);
            double w = 0.00306
                    - 0.00038 * e * FAngle.cos(m)
                    + 0.00026 * FAngle.cos(mp)
                    - 0.00002 * FAngle.cos(mp - m)
                    + 0.00002 * FAngle.cos(mp + m)
                    + 0.00002 * FAngle.cos(2.0 * f);
            corr += (type == FIRST_QUARTER) ? w : -w;
        }

        jde += corr + planetaryCorrection(k, t);
        return new Phase(type, jde, k);
    }

    private static int phaseTypeOf(double k) {
        double frac = k - Math.floor(k);
        if (frac < 0.125) {
            return NEW_MOON;
        }
        if (frac < 0.375) {
            return FIRST_QUARTER;
        }
        if (frac < 0.625) {
            return FULL_MOON;
        }
        return LAST_QUARTER;
    }

    private static double newMoonCorrection(double e, double e2,
            double m, double mp, double f, double om) {
        double c = 0.0;
        c += -0.40720 * FAngle.sin(mp);
        c += 0.17241 * e * FAngle.sin(m);
        c += 0.01608 * FAngle.sin(2.0 * mp);
        c += 0.01039 * FAngle.sin(2.0 * f);
        c += 0.00739 * e * FAngle.sin(mp - m);
        c += -0.00514 * e * FAngle.sin(mp + m);
        c += 0.00208 * e2 * FAngle.sin(2.0 * m);
        c += -0.00111 * FAngle.sin(mp - 2.0 * f);
        c += -0.00057 * FAngle.sin(mp + 2.0 * f);
        c += 0.00056 * e * FAngle.sin(2.0 * mp + m);
        c += -0.00042 * FAngle.sin(3.0 * mp);
        c += 0.00042 * e * FAngle.sin(m + 2.0 * f);
        c += 0.00038 * e * FAngle.sin(m - 2.0 * f);
        c += -0.00024 * e * FAngle.sin(2.0 * mp - m);
        c += -0.00017 * FAngle.sin(om);
        c += -0.00007 * FAngle.sin(mp + 2.0 * m);
        c += 0.00004 * FAngle.sin(2.0 * mp - 2.0 * f);
        c += 0.00004 * FAngle.sin(3.0 * m);
        c += 0.00003 * FAngle.sin(mp + m - 2.0 * f);
        c += 0.00003 * FAngle.sin(2.0 * mp + 2.0 * f);
        c += -0.00003 * FAngle.sin(mp + m + 2.0 * f);
        c += 0.00003 * FAngle.sin(mp - m + 2.0 * f);
        c += -0.00002 * FAngle.sin(mp - m - 2.0 * f);
        c += -0.00002 * FAngle.sin(3.0 * mp + m);
        c += 0.00002 * FAngle.sin(4.0 * mp);
        return c;
    }

    private static double fullMoonCorrection(double e, double e2,
            double m, double mp, double f, double om) {
        double c = 0.0;
        c += -0.40614 * FAngle.sin(mp);
        c += 0.17302 * e * FAngle.sin(m);
        c += 0.01614 * FAngle.sin(2.0 * mp);
        c += 0.01043 * FAngle.sin(2.0 * f);
        c += 0.00734 * e * FAngle.sin(mp - m);
        c += -0.00515 * e * FAngle.sin(mp + m);
        c += 0.00209 * e2 * FAngle.sin(2.0 * m);
        c += -0.00111 * FAngle.sin(mp - 2.0 * f);
        c += -0.00057 * FAngle.sin(mp + 2.0 * f);
        c += 0.00056 * e * FAngle.sin(2.0 * mp + m);
        c += -0.00042 * FAngle.sin(3.0 * mp);
        c += 0.00042 * e * FAngle.sin(m + 2.0 * f);
        c += 0.00038 * e * FAngle.sin(m - 2.0 * f);
        c += -0.00024 * e * FAngle.sin(2.0 * mp - m);
        c += -0.00017 * FAngle.sin(om);
        c += -0.00007 * FAngle.sin(mp + 2.0 * m);
        c += 0.00004 * FAngle.sin(2.0 * mp - 2.0 * f);
        c += 0.00004 * FAngle.sin(3.0 * m);
        c += 0.00003 * FAngle.sin(mp + m - 2.0 * f);
        c += 0.00003 * FAngle.sin(2.0 * mp + 2.0 * f);
        c += -0.00003 * FAngle.sin(mp + m + 2.0 * f);
        c += 0.00003 * FAngle.sin(mp - m + 2.0 * f);
        c += -0.00002 * FAngle.sin(mp - m - 2.0 * f);
        c += -0.00002 * FAngle.sin(3.0 * mp + m);
        c += 0.00002 * FAngle.sin(4.0 * mp);
        return c;
    }

    private static double quarterCorrection(double e, double e2,
            double m, double mp, double f, double om) {
        double c = 0.0;
        c += -0.62801 * FAngle.sin(mp);
        c += 0.17172 * e * FAngle.sin(m);
        c += -0.01183 * e * FAngle.sin(mp + m);
        c += 0.00862 * FAngle.sin(2.0 * mp);
        c += 0.00804 * FAngle.sin(2.0 * f);
        c += 0.00454 * e * FAngle.sin(mp - m);
        c += 0.00204 * e2 * FAngle.sin(2.0 * m);
        c += -0.00180 * FAngle.sin(mp - 2.0 * f);
        c += -0.00070 * FAngle.sin(mp + 2.0 * f);
        c += -0.00040 * FAngle.sin(3.0 * mp);
        c += -0.00034 * e * FAngle.sin(2.0 * mp - m);
        c += 0.00032 * e * FAngle.sin(m + 2.0 * f);
        c += 0.00032 * e * FAngle.sin(m - 2.0 * f);
        c += -0.00028 * e2 * FAngle.sin(mp + 2.0 * m);
        c += 0.00027 * e * FAngle.sin(2.0 * mp + m);
        c += -0.00017 * FAngle.sin(om);
        c += -0.00005 * FAngle.sin(mp - m - 2.0 * f);
        c += 0.00004 * FAngle.sin(2.0 * mp + 2.0 * f);
        c += -0.00004 * FAngle.sin(mp + m + 2.0 * f);
        c += 0.00004 * FAngle.sin(mp - 2.0 * m);
        c += 0.00003 * FAngle.sin(mp + m - 2.0 * f);
        c += 0.00003 * FAngle.sin(3.0 * m);
        c += 0.00002 * FAngle.sin(2.0 * mp - 2.0 * f);
        c += 0.00002 * FAngle.sin(mp - m + 2.0 * f);
        c += -0.00002 * FAngle.sin(3.0 * mp + m);
        return c;
    }

    /**
     * Die vierzehn zusaetzlichen Glieder aus den Stoerungen durch Venus,
     * Jupiter und die Exzentrizitaet der Mondbahn (Meeus Seite 352).
     */
    private static double planetaryCorrection(double k, double t) {
        double a1 = FAngle.norm360(299.77 + 0.107408 * k - 0.009173 * t * t);
        double a2 = FAngle.norm360(251.88 + 0.016321 * k);
        double a3 = FAngle.norm360(251.83 + 26.651886 * k);
        double a4 = FAngle.norm360(349.42 + 36.412478 * k);
        double a5 = FAngle.norm360(84.66 + 18.206239 * k);
        double a6 = FAngle.norm360(141.74 + 53.303771 * k);
        double a7 = FAngle.norm360(207.14 + 2.453732 * k);
        double a8 = FAngle.norm360(154.84 + 7.306860 * k);
        double a9 = FAngle.norm360(34.52 + 27.261239 * k);
        double a10 = FAngle.norm360(207.19 + 0.121824 * k);
        double a11 = FAngle.norm360(291.34 + 1.844379 * k);
        double a12 = FAngle.norm360(161.72 + 24.198154 * k);
        double a13 = FAngle.norm360(239.56 + 25.513099 * k);
        double a14 = FAngle.norm360(331.55 + 3.592518 * k);

        return 0.000325 * FAngle.sin(a1)
                + 0.000165 * FAngle.sin(a2)
                + 0.000164 * FAngle.sin(a3)
                + 0.000126 * FAngle.sin(a4)
                + 0.000110 * FAngle.sin(a5)
                + 0.000062 * FAngle.sin(a6)
                + 0.000060 * FAngle.sin(a7)
                + 0.000056 * FAngle.sin(a8)
                + 0.000047 * FAngle.sin(a9)
                + 0.000042 * FAngle.sin(a10)
                + 0.000040 * FAngle.sin(a11)
                + 0.000037 * FAngle.sin(a12)
                + 0.000035 * FAngle.sin(a13)
                + 0.000023 * FAngle.sin(a14);
    }

    /**
     * Naeherungsweise Phasennummer k fuer ein julianisches Datum.
     *
     * @param jd julianisches Datum
     * @return gebrochenes k
     */
    public static double approximateK(double jd) {
        int[] c = FAstroTime.calendarOf(jd);
        double year = c[0] + (c[1] - 1.0) / 12.0 + (c[2] - 1.0) / 365.25;
        return (year - 2000.0) * 12.3685;
    }

    /**
     * Alle Phasen in einem Zeitraum, nach Zeit geordnet.
     *
     * @param jdFrom Beginn als julianisches Datum (UT)
     * @param jdTo   Ende als julianisches Datum (UT)
     * @return Liste der Phasen, moeglicherweise leer
     */
    public static List<Phase> between(double jdFrom, double jdTo) {
        List<Phase> out = new ArrayList<Phase>();
        double kStart = Math.floor(approximateK(jdFrom)) - 2.0;
        double kEnd = Math.ceil(approximateK(jdTo)) + 2.0;
        for (double k = kStart; k <= kEnd; k += 0.25) {
            Phase p = phaseAt(k);
            if (p.jdUt >= jdFrom && p.jdUt <= jdTo) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Die naechste Phase einer bestimmten Art nach einem Zeitpunkt.
     *
     * @param jd   julianisches Datum (UT), ab dem gesucht wird
     * @param type eine der Konstanten dieser Klasse
     * @return die naechste Phase dieser Art
     */
    public static Phase next(double jd, int type) {
        double offset = type * 0.25;
        double k = Math.floor(approximateK(jd)) - 2.0 + offset;
        Phase p = phaseAt(k);
        while (p.jdUt < jd) {
            k += 1.0;
            p = phaseAt(k);
        }
        return p;
    }

    /**
     * Die vorige Phase einer bestimmten Art vor einem Zeitpunkt.
     *
     * @param jd   julianisches Datum (UT), vor dem gesucht wird
     * @param type eine der Konstanten dieser Klasse
     * @return die vorige Phase dieser Art
     */
    public static Phase previous(double jd, int type) {
        double offset = type * 0.25;
        double k = Math.ceil(approximateK(jd)) + 2.0 + offset;
        Phase p = phaseAt(k);
        while (p.jdUt > jd) {
            k -= 1.0;
            p = phaseAt(k);
        }
        return p;
    }

    /**
     * Alter des Mondes in Tagen, gerechnet als Abstand zum letzten Neumond.
     * Genauer als die Naeherung ueber die Laengendifferenz, weil der
     * tatsaechliche Neumondzeitpunkt verwendet wird.
     *
     * @param jdUt julianisches Datum in Weltzeit
     * @return Alter in Tagen
     */
    public static double ageDays(double jdUt) {
        return jdUt - previous(jdUt, NEW_MOON).jdUt;
    }
}
