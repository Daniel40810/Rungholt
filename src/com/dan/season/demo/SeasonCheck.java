package com.dan.season.demo;

import com.dan.season.Climate;
import com.dan.season.Year;

import java.util.List;
import java.util.Locale;

/** Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ */
public final class SeasonCheck {
    private static int bad;

    public static void main(String[] a) {
        climate();
        split();
        melt();
        stefan();
        water();
        frost();
        heated();
        trees();
        plants();
        statistics();
        acceptance();
        jump();
        seed();
        south();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        if (!ok) bad++;
        System.out.println((ok ? "  ok   " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... v) { return String.format(Locale.GERMAN, fmt, v); }

    /** Treibt das Jahr hours Stunden mit festem Wetter (Temperatur je nach Stunde) in Schritten von 10 min. */
    interface Temp { double at(double hourOfDay); }

    static Year.State drive(Year y, int yr, int doy, double hour, double hours, Temp t, double precip, double oktas, double wind) {
        Year.State s = null;
        double h = hour;
        int d = doy;
        for (int i = 0; i < hours * 6; i++) {
            h += 1 / 6.0;
            if (h >= 24) { h -= 24; d++; }
            s = y.step(600, yr, d, h, t.at(h), precip, Year.sunAltitude(d, h), oktas, wind);
        }
        return s;
    }

    static void climate() {
        Climate c = Climate.schleswig();
        double sum = 0, p = 0;
        for (int m = 0; m < 12; m++) { sum += c.temp[m]; p += c.precip[m]; }
        check(Math.abs(sum / 12 - 9.0) < 0.2 && Math.abs(p - 809) < 5 && Math.abs(c.meanTemp(16) - 1.5) < 0.2 && Math.abs(c.meanTemp(197) - 17.2) < 0.2,
                f("Klima Schleswig: Jahresmittel %.1f °C, %.0f mm, Mitte Januar %.1f °C, Mitte Juli %.1f °C", sum / 12, p, c.meanTemp(16), c.meanTemp(197)));
        // gewürfelte Jahre: über 30 Jahre Monatsmittel und Niederschlag wie das Klima
        double[] tm = new double[12], pm = new double[12];
        int n = 30;
        for (int k = 0; k < n; k++) {
            double[][] s = c.days(1, 2000 + k, 1, 365);
            int d = 0;
            for (int m = 0; m < 12; m++) {
                int len = new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31}[m];
                for (int i = 0; i < len; i++, d++) { tm[m] += s[0][d] / len / n; pm[m] += s[1][d] / n; }
            }
        }
        double worst = 0, ptot = 0;
        for (int m = 0; m < 12; m++) { worst = Math.max(worst, Math.abs(tm[m] - c.temp[m])); ptot += pm[m]; }
        check(worst < 1.0 && Math.abs(ptot / 809 - 1) < 0.1, f("30 gewürfelte Jahre: Monatsmittel höchstens %.2f K vom Klima, %.0f mm im Jahr", worst, ptot));
    }

    static void split() {
        check(Year.rainFraction(-1) == 0 && Year.rainFraction(4) == 1 && Year.rainFraction(1.5) > 0.3 && Year.rainFraction(1.5) < 0.7,
                f("Niederschlag unter 0,5 °C als Schnee, über 2,5 °C als Regen, bei 1,5 °C gemischt (%.0f %% Regen)", Year.rainFraction(1.5) * 100));
        Year y = new Year();
        y.reset(950, 20, 0, 0, 100, 0, 2);
        Year.State s = drive(y, 950, 20, 0, 10, h -> -3, 1.0, 8, 3);
        check(s.swe() > 9 && s.swe() < 10.5 && s.snowDepth() > 0.07 && s.snowDepth() < 0.11 && s.snowCover() > 0.95,
                f("10 Stunden 1 mm/h bei −3 °C: %.1f mm Schneewasser, %.0f cm Neuschnee, Boden bedeckt", s.swe(), s.snowDepth() * 100));
        y.reset(950, 200, 0, 0, 100, 0, 18);
        Year.State r = drive(y, 950, 200, 0, 10, h -> 15, 3.0, 8, 3);
        check(r.snowDepth() == 0, "Im Juli fällt Regen, kein Schnee");
    }

    static void melt() {
        Year y = new Year();
        // 20 cm gesetzter Schnee (150 kg/m³ → 30 mm), +5 °C, bedeckt
        y.reset(950, 40, 0, 30, 150, 0, 0);
        double hours = 0;
        Year.State s = y.state();
        while (s.swe() > 0 && hours < 24 * 10) { s = drive(y, 950, 40 + (int) (hours / 24), hours % 24, 1, h -> 5, 0, 8, 3); hours++; }
        check(hours > 24 && hours < 24 * 3, f("Tauwetter: 20 cm Schnee (30 mm) schmelzen bei +5 °C unter Wolken in %.1f Tagen (Gradtag 3,6 mm)", hours / 24));
        y.reset(950, 40, 0, 30, 150, 0, 0);
        Year.State cold = drive(y, 950, 40, 0, 72, h -> -6, 0, 0, 2);
        check(cold.swe() > 28, f("Bei −6 °C und Sonne im Februar bleibt der Schnee (%.1f von 30 mm nach drei Tagen)", cold.swe()));
        y.reset(950, 40, 0, 10, 100, 0, 0);
        Year.State settle = drive(y, 950, 40, 0, 24 * 8, h -> -4, 0, 8, 2);
        check(settle.snowDepth() < 0.1 * 0.75 && settle.snowDepth() > 0.03, f("Schnee setzt sich: 10 cm Neuschnee sind nach acht Frosttagen %.1f cm", settle.snowDepth() * 100));
    }

    static void stefan() {
        Year y = new Year();
        y.reset(950, 10, 0, 0, 100, 0, Year.FREEZE);
        Year.State s = drive(y, 950, 10, 0, 24 * 10, h -> -10, 0, 0, 2);
        double expect = Year.STEFAN * Math.sqrt(9.6 * 10);
        check(Math.abs(s.ice() - expect) < 0.02 && s.ice() > 0.2, f("Eis nach Stefan: zehn Tage −10 °C geben %.1f cm (h = α·√FDD: %.1f cm)", s.ice() * 100, expect * 100));
        y.reset(950, 10, 0, 50, 200, 0, Year.FREEZE);
        Year.State sn = drive(y, 950, 10, 0, 24 * 10, h -> -10, 0, 0, 2);
        check(sn.ice() < s.ice() * 0.5, f("Unter 25 cm Schnee wächst das Eis langsamer (%.1f cm)", sn.ice() * 100));
        y.reset(950, 10, 0, 0, 100, 0, Year.FREEZE);
        Year.State wind = drive(y, 950, 10, 0, 24, h -> -3, 0, 4, 11);
        check(wind.ice() < 0.005, "Bei Wind über 9 m/s bleibt das Noor offen, solange das Eis nicht 3 cm dick ist");
        y.reset(950, 60, 0, 0, 100, 0.20, Year.FREEZE);
        Year.State thaw = drive(y, 950, 60, 0, 24 * 5, h -> 6, 0, 8, 4);
        check(thaw.ice() < 0.15 && thaw.ice() > 0, f("Tauwetter +6 °C: 20 cm Eis sind nach fünf Tagen %.1f cm", thaw.ice() * 100));
        check(s.iceDepth() > 5 && thaw.iceDepth() > 2 && new Year().state().iceDepth() == 0,
                f("Eisgrenze als Wassertiefe: bei %.0f cm Eis bis %.1f m, offen: 0", s.ice() * 100, s.iceDepth()));
    }

    static void water() {
        Year y = new Year();
        y.reset(950, 340, 0, 0, 100, 0, 6);
        Year.State s = drive(y, 950, 340, 0, 24 * 2, h -> -5, 0, 0, 2);
        check(s.ice() == 0 && s.waterTemp() > 0, f("Zwei kalte Tage im Dezember: das Noor ist noch %.1f °C warm und friert nicht", s.waterTemp()));
        Year.State l = drive(y, 950, 342, 0, 24 * 10, h -> -5, 0, 0, 2);
        check(l.ice() > 0.05, f("Nach zwölf Frosttagen ist es zugefroren (%.0f cm)", l.ice() * 100));
    }

    static void frost() {
        Year y = new Year();
        y.reset(950, 290, 18, 0, 100, 0, 10);
        Year.State n = drive(y, 950, 290, 18, 13, h -> h > 12 ? 4 : -1.5, 0, 1, 1);
        check(n.frost() > 0.8, f("Klare, windstille Nacht mit −1,5 °C: Reif am Morgen (%.0f %%)", n.frost() * 100));
        Year.State m = drive(y, 950, 291, 7, 4, h -> 5, 0, 1, 1);
        check(m.frost() < 0.1, f("Die Sonne nimmt ihn bis 11 Uhr (%.0f %%)", m.frost() * 100));
        y.reset(950, 290, 18, 0, 100, 0, 10);
        Year.State c = drive(y, 950, 290, 18, 13, h -> -1.5, 0, 8, 1);
        check(c.frost() < 0.05, "Unter Wolken kein Reif");
    }

    static void heated() {
        Year y = new Year();
        y.reset(950, 20, 0, 0, 100, 0, Year.FREEZE);
        Year.State s = drive(y, 950, 20, 0, 6, h -> -2, 2, 8, 3);
        Year.State l = drive(y, 950, 20, 6, 24 * 3, h -> -2, 0, 8, 3);
        check(l.roofSnow() > 0.03 && l.roofSnow() < l.snowDepth() * 1.5 && l.swe() > 11, f("Dächer: %.0f cm Schnee, am Boden %.0f cm (12 mm gefallen)", l.roofSnow() * 100, l.snowDepth() * 100));
        Year.State t = drive(y, 950, 23, 6, 24 * 2, h -> 1, 0, 8, 3);
        check(t.roofSnow() * 180 < t.swe() * 1.0, f("Bei +1 °C tauen die Dächer schneller als der Boden (Dach %.1f mm, Boden %.1f mm)", t.roofSnow() * 180, t.swe()));
    }

    static void trees() {
        Year y = new Year();
        y.reset(950, 20, 0, 0, 100, 0, Year.FREEZE);
        Year.State s = drive(y, 950, 20, 0, 5, h -> -3, 1.5, 8, 1);
        Year.State l = drive(y, 950, 20, 5, 24, h -> -3, 0, 6, 8);
        check(s.treeSnow() > 0.9 && l.treeSnow() < 0.3, f("Kronen: nach Schneefall voll (%.0f %%), ein Tag Wind wirft ihn ab (%.0f %%)", s.treeSnow() * 100, l.treeSnow() * 100));
    }

    static void plants() {
        Year y = new Year();
        int oct = Year.dayOfYear(10, 16), jan = Year.dayOfYear(1, 16), may = Year.dayOfYear(5, 16), jul = Year.dayOfYear(7, 16), apr = Year.dayOfYear(4, 16);
        check(Math.abs(y.reedSeason(oct) - 0.75) < 0.02 && Math.abs(y.grassLush(oct) - 0.5) < 0.02 && Math.abs(y.autumn(oct) - 0.5) < 0.02,
                f("Mitte Oktober wie bisher: Schilf %.2f, Gras %.2f, Herbst %.2f", y.reedSeason(oct), y.grassLush(oct), y.autumn(oct)));
        check(y.foliage(jan) == 0 && y.foliage(jul) == 1 && y.foliage(apr) > 0 && y.foliage(apr) < 0.6 && y.reedSeason(jan) == 1 && y.reedSeason(jul) == 0
                        && y.grassLush(may) > 0.9 && y.grassLush(jan) < 0.2,
                f("Januar kahl und strohgrau, Mitte April Austrieb (%.0f %%), Mai saftig (%.2f), Juli belaubt, Schilf grün", y.foliage(apr) * 100, y.grassLush(may)));
    }

    static void statistics() {
        Year y = new Year();
        int snowDays = 0, iceWinters = 0, summerSnow = 0, n = 30;
        for (int k = 0; k < n; k++) {
            y.seed = 100 + k;
            List<Year.State> tr = y.trace(1000 + k, 8);
            boolean iced = false;
            for (int i = 0; i < tr.size(); i++) {
                Year.State s = tr.get(i);
                int doy = i + Year.FIRST;
                if (doy < 1) continue;
                if (s.snowDepth() >= 0.01) snowDays++;
                if (s.ice() > 0.05 && doy < 120) iced = true;
                if (doy > 140 && doy < 270 && (s.snowDepth() > 0 || s.ice() > 0)) summerSnow++;
            }
            if (iced) iceWinters++;
        }
        double sd = snowDays / (double) n;
        check(sd > 12 && sd < 45 && iceWinters > n / 4 && iceWinters < n && summerSnow == 0,
                f("30 gewürfelte Jahre: im Mittel %.0f Tage mit Schnee (Schleswig heute 20 bis 30), Eis über 5 cm in %d von %d Wintern, Mai bis September nie", sd, iceWinters, n));
    }

    static void acceptance() {
        Year y = new Year();
        Year.State jan = y.settle(950, 16, 12), apr = y.settle(950, Year.dayOfYear(4, 16), 12), jul = y.settle(950, Year.dayOfYear(7, 16), 12),
                oct = y.settle(950, Year.dayOfYear(10, 16), 12);
        check(jan.snowDepth() > 0.08 && jan.ice() > 0.04 && apr.snowDepth() == 0 && apr.ice() == 0 && jul.snowDepth() == 0 && oct.snowDepth() == 0 && oct.ice() == 0,
                f("Abnahme 950 (Startwert %d): 16.1. %s · 16.4. %s · 16.7. %s · 16.10. %s", y.seed, jan.describe(), apr.describe(), jul.describe(), oct.describe()));
    }

    static void jump() {
        Year y = new Year();
        Year.State a = y.settle(950, 16, 12);
        y.step(600, 950, 200, 12, 18, 0, 50, 3, 3);       // Sprung in den Juli
        Year.State b = y.step(600, 950, 16, 12.1, -3, 0, 12, 3, 3);   // und zurück
        check(b.snowDepth() > 0 && Math.abs(b.ice() - a.ice()) < 0.01, f("Ein Sprung im Kalender rechnet vor: zurück am 16.1. wieder %s", b.describe()));
    }

    static void seed() {
        Year a = new Year(), b = new Year();
        Year.State x = a.settle(950, 30, 9), z = b.settle(950, 30, 9);
        b.seed = 15;
        Year.State w = b.settle(950, 30, 9);
        check(x.equals(z) && !x.equals(w), "Gleicher Startwert, gleiches Jahr; anderer Startwert, anderer Winter");
    }

    /** Seit Glacier Phase 11: El Calafate, vor dem Perito Moreno, Südhalbkugel. */
    static void south() {
        Climate e = Climate.elCalafate(), p = Climate.peritoMoreno();
        double sumE = 0, sumP = 0;
        for (int m = 0; m < 12; m++) { sumE += e.precip[m]; sumP += p.precip[m]; }
        check(Math.abs(e.meanTemp(16) - 13.9) < 0.6 && Math.abs(e.meanTemp(197) - 0.6) < 0.6 && Math.abs(sumE - 161) < 1 && e.summerDay == 15,
                String.format(java.util.Locale.GERMAN, "El Calafate: Januar %.1f °C, Juli %.1f °C, %.0f mm im Jahr (SMN 1991–2020)", e.meanTemp(16), e.meanTemp(197), sumE));
        // 30 Jahre vor dem Gletscher: Juli kalt, Januar warm, Schnee im Winter, keiner im Sommer
        int julySnow = 0, janSnow = 0, julyIce = 0;
        for (long seed = 1; seed <= 30; seed++) {
            Year y = new Year(p);
            y.southern = true;
            y.seed = seed;
            if (y.settle(2026, 197, 12).snowCover() > 0.2) julySnow++;
            if (y.state().ice() > 0.005) julyIce++;
            if (y.settle(2026, 16, 12).snowCover() > 0.2) janSnow++;
        }
        check(julySnow >= 4 && julySnow <= 24 && janSnow == 0 && julyIce >= 3,
                String.format(java.util.Locale.GERMAN, "Vor dem Perito Moreno (%.0f mm, Juli %.1f °C): in 30 Jahren am 16. Juli %d-mal Schnee am Ufer und %d-mal Eis, am 16. Januar nie", sumP, p.meanTemp(197), julySnow, julyIce));
        Year y = new Year(p);
        y.southern = true;
        check(y.autumn(110) > 0.4 && y.autumn(290) < 0.05 && y.grassLush(320) > y.grassLush(200),
                String.format(java.util.Locale.GERMAN, "Südhalbkugel: Herbstfärbung im April %.0f %%, im Oktober %.0f %%; Gras im November saftiger als im Juli", 100 * y.autumn(110), 100 * y.autumn(290)));
    }
}
