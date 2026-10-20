package com.dan.tide.demo;

import com.dan.tide.Flood;
import com.dan.tide.Tide;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen (Rückgabewert
 * 1). Phase 4: Pegel gegen Hoch- und Niedrigwasser aus dem Gezeitenkalender 2026 (BSH, Husum), Mittelwerte, Spring und
 * Nipp, Windstau, Ereignisse; die Überflutung auf einem Becken mit Priel, Watt, Mulde und Koog: Laufzeit gegen Weglänge
 * und Tiefe, Volumen der Flut gegen Hub mal Fläche, Pfützen, Trockenfallzeit, Deich, Strömung, Rechenzeit.
 */
public final class TideCheck {
    private static int bad;
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    public static void main(String[] args) {
        grundlagen();
        husum();
        ereignisse();
        becken();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void grundlagen() {
        System.out.println("Grundlagen");
        double p = 360 / Tide.M2;
        check(Math.abs(p - 12.4206) < 1e-3, String.format(Locale.GERMAN, "M2: eine Tide in %.4f h (12 h 25 min)", p));
        double sn = Tide.springNeapDays();
        check(Math.abs(sn - 14.77) < 0.01, String.format(Locale.GERMAN, "Spring zu Spring aus M2 und S2: %.2f Tage", sn));
        Tide b = Tide.basin();
        b.constituents.removeIf(c -> !c.name().equals("M2"));
        double hi = -9, lo = 9;
        for (double h = 0; h < 25; h += 0.01) { double l = b.levelAt(h); hi = Math.max(hi, l); lo = Math.min(lo, l); }
        check(Math.abs(hi - 1) < 1e-3 && Math.abs(lo + 1) < 1e-3, "Becken: reine M2 schwingt ±1 m");
        double tr = Tide.travelSeconds(1000, 2);
        check(tr > 220 && tr < 230, String.format(Locale.GERMAN, "lange Welle: 1 km über 2 m Wasser in %.0f s", tr));
        Tide hu = Tide.husum();
        check(Tide.STAGE == 4 && hu.constituents.size() == 10 && hu.describe().startsWith("tide: Stufe 4"), hu.describe());
    }

    /** Hoch- und Niedrigwasser aus dem Gezeitenkalender 2026 für Husum (BSH; Hrsg. Husum Tourismus, gesetzliche Zeit). */
    static final String[][] KALENDER = {
            {"2026-01-03", "HW", "0:53", "13:29"}, {"2026-01-03", "NW", "7:29", "19:51"},
            {"2026-01-16", "HW", "11:59"}, {"2026-01-16", "NW", "5:42", "18:09"},
            {"2026-04-12", "HW", "9:31", "22:10"}, {"2026-04-12", "NW", "2:53", "15:22"},
            {"2026-04-24", "HW", "7:36", "19:52"},
            {"2026-07-21", "HW", "7:35", "19:45"}, {"2026-07-21", "NW", "1:31", "13:30"},
            {"2026-07-28", "HW", "1:45", "14:06"},
            {"2026-10-20", "HW", "8:24", "21:12"}, {"2026-10-20", "NW", "1:27", "14:28"},
            {"2026-10-26", "HW", "1:23", "13:39"}, {"2026-10-26", "NW", "7:36", "20:04"}};

    static void husum() {
        System.out.println("Pegel Husum");
        Tide t = Tide.husum();
        double sum = 0, worst = 0;
        int n = 0;
        for (String[] e : KALENDER) {
            LocalDate d = LocalDate.parse(e[0]);
            boolean high = e[1].equals("HW");
            List<Tide.Turn> cal = t.calendar(d.minusDays(1), BERLIN);
            cal.addAll(t.calendar(d, BERLIN));
            cal.addAll(t.calendar(d.plusDays(1), BERLIN));
            for (int i = 2; i < e.length; i++) {
                String[] hm = e[i].split(":");
                double target = LocalDateTime.of(d.getYear(), d.getMonthValue(), d.getDayOfMonth(), Integer.parseInt(hm[0]), Integer.parseInt(hm[1]))
                        .atZone(BERLIN).toInstant().toEpochMilli() / 3_600_000.0;
                double best = 99;
                for (Tide.Turn x : cal) if (x.high() == high) best = Math.min(best, Math.abs(x.hours() - target) * 60);
                sum += best;
                worst = Math.max(worst, best);
                n++;
            }
        }
        check(sum / n < 15 && worst < 35, String.format(Locale.GERMAN, "%d Hoch- und Niedrigwasser aus dem Gezeitenkalender 2026 (BSH): im Mittel %.0f min daneben, höchstens %.0f min", n, sum / n, worst));
        List<Tide.Turn> year = t.calendar(2026, BERLIN);
        double hw = 0, nw = 0, top = -9, bottom = 9;
        int nh = 0, nl = 0;
        for (Tide.Turn x : year) {
            if (x.high()) { hw += x.level(); nh++; top = Math.max(top, x.level()); }
            else { nw += x.level(); nl++; bottom = Math.min(bottom, x.level()); }
        }
        hw /= nh;
        nw /= nl;
        check(Math.abs(hw - 1.60) < 0.03 && Math.abs(nw + 1.90) < 0.03 && Math.abs(hw - nw - 3.50) < 0.03,
                String.format(Locale.GERMAN, "2026: MThw NHN %+.2f m, MTnw %+.2f m, mittlerer Hub %.2f m (Husumer Bucht 3,50 m)", hw, nw, hw - nw));
        check(nh + nl > 1400 && nh + nl < 1420 && top < 2.6 && bottom > -2.8,
                String.format(Locale.GERMAN, "Tidenkalender 2026: %d Hoch- und Niedrigwasser, höchstes HW %+.2f m, tiefstes NW %+.2f m", nh + nl, top, bottom));
        // Flut kürzer als Ebbe (Kalender: 6,16 h und 6,26 h)
        double flood = 0, ebb = 0;
        int nf = 0, ne = 0;
        for (int i = 1; i < year.size(); i++) {
            double d = year.get(i).hours() - year.get(i - 1).hours();
            if (year.get(i).high()) { flood += d; nf++; } else { ebb += d; ne++; }
        }
        flood /= nf;
        ebb /= ne;
        check(flood < ebb && Math.abs(flood - 6.16) < 0.1 && Math.abs(ebb - 6.26) < 0.1,
                String.format(Locale.GERMAN, "Flut %.2f h, Ebbe %.2f h (Kalender: 6,16 h und 6,26 h)", flood, ebb));
        // Spring 1 bis 3 Tage nach Vollmond (26.10.2026, 4:12 UTC [G])
        double full = java.time.Instant.parse("2026-10-26T04:12:00Z").toEpochMilli() / 3_600_000.0;
        double spring = t.nextSpring(full - 24);
        check(spring - full > 12 && spring - full < 80, String.format(Locale.GERMAN, "Springtide %.1f Tage nach dem Vollmond am 26.10.2026", (spring - full) / 24));
        double sMax = 0, sMin = 9;
        for (double h = full; h < full + 30 * 24; h += 3) { sMax = Math.max(sMax, t.springFactor(h)); sMin = Math.min(sMin, t.springFactor(h)); }
        check(sMax > 1.2 && sMax < 1.45 && sMin > 0.5 && sMin < 0.85, String.format(Locale.GERMAN, "Spring und Nipp: Hub das %.2f- bis %.2f-fache des mittleren", sMin, sMax));
        // Windstau [G]
        double sturm = Tide.surgeTarget(22, 290), orkan = Tide.surgeTarget(33, 295), ost = Tide.surgeTarget(15, 90);
        check(Math.abs(sturm - 1.5) < 1e-9 && orkan > 3.2 && orkan < 3.6 && ost < 0 && ost > -0.5,
                String.format(Locale.GERMAN, "Windstau: Sturm aus 290° %+.1f m, Orkan aus 295° %+.1f m, Ostwind %+.2f m", sturm, orkan, ost));
        Tide s = Tide.husum();
        for (int k = 0; k < 36; k++) s.stepSurge(orkan, 0.25);
        check(s.surge > orkan * 0.9 && s.surge < orkan, String.format(Locale.GERMAN, "Windstau läuft in 9 h auf %+.2f m zu (Zeitkonstante %.0f h)", s.surge, Tide.SURGE_HOURS));
        Tide pw = Tide.pellworm();
        Tide.Turn a = t.next(t.extremes(full, full + 13).get(0).hours() - 1, true), b = pw.next(a.hours() - 1, true);
        check(b != null && (a.hours() - b.hours()) * 60 > 5 && (a.hours() - b.hours()) * 60 < 12, String.format(Locale.GERMAN, "Pellworm [G]: Hochwasser %.0f min vor Husum", (a.hours() - b.hours()) * 60));
        // seit Rungholt Phase 17: Bremerhaven und Roter Sand (für Lighthouse)
        Tide br = Tide.bremerhaven(), rs = Tide.roterSand();
        double h17 = java.time.ZonedDateTime.of(2026, 10, 17, 0, 0, 0, 0, java.time.ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli() / 3_600_000.0;
        Tide.Turn hb = br.next(h17, true), hr = rs.next(h17, true);
        double ab = hb.hours() - h17, rangeSum = 0;   // Stunden nach Mitternacht MESZ
        int nr = 0;
        java.util.List<Tide.Turn> ex = rs.extremes(h17, h17 + 30 * 24);
        for (int k = 0; k + 1 < ex.size(); k++) if (ex.get(k).high()) { rangeSum += ex.get(k).level() - ex.get(k + 1).level(); nr++; }
        check(Math.abs(ab - (5 + 17 / 60.0)) < 0.4 && Math.abs((hb.hours() - hr.hours()) * 60 - 60) < 2 && Math.abs(rangeSum / nr - 3.0) < 0.1,
                String.format(Locale.GERMAN, "Bremerhaven: 17.10.2026 Hochwasser %s MESZ (BSH 05:17), Roter Sand eine Stunde früher, dort mittlerer Hub %.2f m",
                        String.format(Locale.ROOT, "%02d:%02d", (int) ab, (int) Math.round((ab - (int) ab) * 60)), rangeSum / nr));
    }

    static void ereignisse() {
        System.out.println("Ereignisse");
        Tide t = Tide.husum();
        double start = java.time.Instant.parse("2026-07-21T00:00:00Z").toEpochMilli() / 3_600_000.0;
        List<Tide.Turn> ex = t.extremes(start, start + 25);
        int[] hw = {0}, nw = {0}, turns = {0};
        double[] hwAt = {0};
        t.onHighWater = (h, l) -> { hw[0]++; hwAt[0] = h; };
        t.onLowWater = (h, l) -> nw[0]++;
        t.onTurn = (h, l) -> turns[0]++;
        for (double h = start; h < start + 25; h += 1 / 60.0) t.step(h);
        long nh = ex.stream().filter(Tide.Turn::high).count(), nl = ex.size() - nh;
        check(hw[0] == nh && nw[0] == nl && turns[0] == ex.size(), String.format(Locale.GERMAN, "in 25 h: %d Hoch-, %d Niedrigwasser, %d Kenterungen gemeldet", hw[0], nw[0], turns[0]));
        check(Math.abs(hwAt[0] - ex.stream().filter(Tide.Turn::high).reduce((x, y) -> y).get().hours()) * 60 < 1, "das gemeldete Hochwasser liegt auf der Minute der Rechnung");
        check(Tide.format(ex.get(0), BERLIN).matches("(Hoch|Niedrig)wasser \\d+:\\d\\d [+−-]\\d,\\d\\d m"), Tide.format(ex.get(0), BERLIN));
    }

    /**
     * Ein Becken 3 × 1 km zu 10 m: offenes Meer am Westrand, Watt auf NHN −0,5 m, ein Priel in der Mitte bis −3 m, eine
     * Mulde (Grund −0,2 m, Rand +0,3 m), ein Deich (+8 m) mit Koog (+0,5 m) dahinter im Osten.
     */
    static void becken() {
        System.out.println("Überflutung");
        int nx = 300, nz = 100;
        double cell = 10;
        float[] h = new float[nx * nz];
        boolean[] seed = new boolean[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = (i + 0.5) * cell, z = (j + 0.5) * cell;
                double g = -0.5;
                double pr = Math.abs(z - 500);
                if (pr < 30) g = -3 + 2.5 * (pr / 30) * (pr / 30);
                double md = Math.hypot(x - 1500, z - 800);
                if (md < 60) g = -0.2 + 0.4 * md / 60 * md / 60;
                else if (md < 80) g = 0.3;
                if (x > 2500 && x < 2560) g = 8;
                if (x >= 2560) g = 0.5;
                h[j * nx + i] = (float) g;
                seed[j * nx + i] = i == 0;
            }
        Flood f = Flood.build(h, nx, nz, 0, 0, cell, seed, null, 1.6, 1);
        double tPriel = f.travelAt(1005, 505), tWatt = f.travelAt(1005, 205);
        double expect = 1000 / (Math.sqrt(9.81 * (1.6 + 3)) * Flood.FRICTION);
        check(Math.abs(tPriel - expect) / expect < 0.12, String.format(Locale.GERMAN, "Laufzeit im Priel bis 1 km: %.0f s (Weglänge/(0,7·√(g·4,6 m)) = %.0f s)", tPriel, expect));
        check(tWatt > tPriel * 1.2, String.format(Locale.GERMAN, "das Watt neben dem Priel kommt später: %.0f s gegen %.0f s", tWatt, tPriel));
        check(Math.abs(f.spillAt(1505, 805) - 0.3) < 0.01 && Math.abs(f.spillAt(1005, 205) + 0.5) < 0.01 && f.spillAt(2705, 505) >= 8,
                String.format(Locale.GERMAN, "Verbindungshöhe: Mulde %+.2f m (ihr Rand), Watt %+.2f m, Koog hinter dem Deich %+.1f m", f.spillAt(1505, 805), f.spillAt(1005, 205), f.spillAt(2705, 505)));
        // Volumen der Flut gegen Hub mal Fläche (Becken-Tide um ±1 m; das Watt fällt trocken, die Mulde ist nach dem Sprung leer)
        Tide t = Tide.basin();
        t.maxPuddle = 1;   // die Mulde des Beckens hält bis zu ihrem Rand
        List<Tide.Turn> ex = t.extremes(10, 40);
        Tide.Turn lw = ex.stream().filter(x -> !x.high()).findFirst().get();
        Tide.Turn hw = ex.stream().filter(x -> x.high() && x.hours() > lw.hours()).findFirst().get();
        f.step(t, lw.hours() + 0.1);
        double v0 = volume(f, 0, 2400);
        f.step(t, hw.hours() + 0.1);
        double v1 = volume(f, 0, 2400);
        // erwartet: je Zelle (min(HW, …) − max(NW, Grund)) über die Fläche bis zum Deich
        double exp = 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < 240; i++) {
                double g = h[j * nx + i], top = hw.level(), bot = Math.max(lw.level(), g);
                if (top > bot) exp += (top - bot) * cell * cell;
            }
        check(Math.abs(v1 - v0 - exp) / exp < 0.05, String.format(Locale.GERMAN, "Volumen der Flut %.0f m³, erwartet aus Hub und Fläche %.0f m³", v1 - v0, exp));
        // Pfütze: nach der Ebbe bleibt die Mulde voll bis zum Rand, dann versickert sie
        double t0 = hw.hours();
        for (double tt = t0; tt < t0 + 6.5; tt += 1 / 30.0) f.step(t, tt);
        double dMulde = f.depthAt(1505, 805), dWatt = f.depthAt(1005, 205);
        check(dMulde > 0.4 && !f.connectedAt(1505, 805) && dWatt == 0, String.format(Locale.GERMAN, "Niedrigwasser: die Mulde hält %.2f m Wasser als Pfütze, das Watt daneben ist trocken", dMulde));
        check(f.puddles > 0 && f.dryHoursAt(1005, 205) > 1, String.format(Locale.GERMAN, "%d Pfützenzellen; das Watt liegt seit %.1f h trocken", f.puddles, f.dryHoursAt(1005, 205)));
        double expectSeep = t.seepage * (f.lastHours - (hw.hours() + 0));   // grob: seit die Schwelle unterschritten wurde
        check(f.surfaceAt(1505, 805) < 0.3 && f.surfaceAt(1505, 805) > 0.3 - expectSeep - 0.01,
                String.format(Locale.GERMAN, "die Pfütze versickert: Spiegel %+.3f m (Rand +0,30 m, %.0f cm je Stunde)", f.surfaceAt(1505, 805), t.seepage * 100));
        check(f.depthAt(2705, 505) == 0, "der Koog hinter dem Deich bleibt trocken");
        // seit Rungholt Phase 12: der Koog als Becken mit eigenem Spiegel (ein Polder hinter einem gebrochenen Deich)
        boolean[] bm = new boolean[nx * nz];
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) bm[j * nx + i] = (i + 0.5) * cell >= 2560;
        f.basinMask = bm;
        f.basinLevel = 1.4;
        f.step(t, f.lastHours + 0.05);
        check(Math.abs(f.depthAt(2705, 505) - 0.9) < 0.01 && f.depthAt(2555, 505) == 0,
                String.format(Locale.GERMAN, "Becken: der Koog steht auf seinem eigenen Spiegel (%.2f m Wasser über NHN +0,5 m), der Deich bleibt trocken", f.depthAt(2705, 505)));
        f.basinLevel = Double.NaN;
        f.step(t, f.lastHours + 0.05);
        Tide flach = Tide.basin();   // mit der Grenze der Pfützentiefe (0,15 m) läuft die Mulde bis auf eine Handbreit leer
        f.step(flach, hw.hours());
        for (double tt = hw.hours(); tt < hw.hours() + 6.5; tt += 1 / 30.0) f.step(flach, tt);
        check(f.depthAt(1505, 805) <= flach.maxPuddle + 1e-6 && f.depthAt(1505, 805) > 0.05,
                String.format(Locale.GERMAN, "Pfützen höchstens %.2f m tief (Rinnen, die das Raster nicht kennt): die Mulde hält %.2f m", flach.maxPuddle, f.depthAt(1505, 805)));
        // Strömung: im Priel stärker als auf dem Watt, bei Flut landwärts (Osten)
        double mid = (lw.hours() + hw.hours()) / 2;
        f.step(t, mid);
        double[] up = f.velocityAt(1005, 505), uw = f.velocityAt(1005, 255);
        check(up[0] > 0.05 && Math.hypot(up[0], up[1]) > Math.hypot(uw[0], uw[1]),
                String.format(Locale.GERMAN, "Strömung bei halber Flut: im Priel %.2f m/s nach Osten, auf dem Watt %.2f m/s", up[0], Math.hypot(uw[0], uw[1])));
        f.step(t, mid + 6.2);
        check(f.velocityAt(1005, 505)[0] < 0, String.format(Locale.GERMAN, "bei Ebbe seewärts: %.2f m/s", f.velocityAt(1005, 505)[0]));
        // Deich: erst eine Sturmflut über die Krone flutet den Koog (die Mandränke)
        t.surge = 7.5;
        f.step(t, hw.hours() + 24.84);
        check(f.depthAt(2705, 505) > 0.5, String.format(Locale.GERMAN, "Sturmflut %.1f m über der Deichkrone: der Koog läuft voll (%.1f m Wasser)", t.levelAt(hw.hours() + 24.84) - 8, f.depthAt(2705, 505)));
        t.surge = 0;
        // Trockenfallen: Watt auf −0,5 m bei Husum 4 bis 5 h je Tide [G]
        Tide hu = Tide.husum();
        Flood g = Flood.build(new float[]{-0.5f, -0.5f, -0.5f, -0.5f}, 2, 2, 0, 0, 10, null, null, 1.6, 1);
        double start = 495_000, dry = 0;
        for (double tt = start; tt < start + 24.84 * 4; tt += 1 / 60.0) { g.step(hu, tt); if (g.depthAt(5, 5) == 0) dry += 1 / 60.0; }
        check(dry / 8 > 4 && dry / 8 < 6, String.format(Locale.GERMAN, "Watt auf NHN −0,5 m liegt %.1f h je Tide trocken (Faustregel: 4 bis 6 h [G])", dry / 8));
        // Rechenzeit: 1024² Zellen
        int m = 1024;
        float[] big = new float[m * m];
        for (int k = 0; k < big.length; k++) big[k] = (float) (-2 + 3 * Math.sin(k % m * 0.01) * Math.cos(k / m * 0.013));
        long b0 = System.nanoTime();
        Flood fb = Flood.build(big, m, m, 0, 0, 16, null, null, 1.6, 1);
        double buildS = (System.nanoTime() - b0) / 1e9;
        for (int k = 0; k < 10; k++) fb.step(hu, 495_000 + k * 0.01);
        long s0 = System.nanoTime();
        for (int k = 0; k < 20; k++) fb.step(hu, 495_100 + k * 0.01);
        double ms = (System.nanoTime() - s0) / 1e6 / 20;
        check(ms < 40, String.format(Locale.GERMAN, "1024² Zellen: Bau %.1f s, Schritt %.1f ms auf %d Kernen (Ziel im Faden: unter 4 ms auf acht Kernen)", buildS, ms, Runtime.getRuntime().availableProcessors()));
        try {
            java.io.File tmp = java.io.File.createTempFile("tide", ".bin");
            long key = Flood.key(h, seed, null, 1.6, 1, cell);
            f.save(tmp, key);
            Flood back = Flood.load(tmp, key), wrong = Flood.load(tmp, key + 1);
            check(back != null && wrong == null && back.spillAt(1505, 805) == f.spillAt(1505, 805) && back.travelAt(1005, 505) == f.travelAt(1005, 505),
                    "Zwischendatei: gleiche Karte zurück, mit falschem Schlüssel keine");
            tmp.delete();
        } catch (java.io.IOException e) {
            check(false, "Zwischendatei: " + e);
        }
    }

    static double volume(Flood f, double xa, double xb) {
        double v = 0;
        for (int j = 0; j < f.nz; j++)
            for (int i = 0; i < f.nx; i++) {
                double x = f.x0 + (i + 0.5) * f.cell;
                if (x < xa || x > xb) continue;
                int k = j * f.nx + i;
                v += Math.max(0, f.surface[k] - f.ground[k]) * f.cell * f.cell;
            }
        return v;
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private TideCheck() { }
}
