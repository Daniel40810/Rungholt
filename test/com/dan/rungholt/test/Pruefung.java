package com.dan.rungholt.test;

import com.dan.rungholt.camera.Ansichten;
import com.dan.rungholt.camera.Camera;
import com.dan.rungholt.gl.ComputeProbe;
import com.dan.rungholt.gl.Mat4;
import com.dan.rungholt.gl.Quality;
import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.gl.Shader;
import com.dan.rungholt.scene.Buehne;
import com.dan.rungholt.scene.Geo;
import com.dan.rungholt.scene.Physik;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.scene.Site;
import com.dan.rungholt.scene.Sun;
import com.dan.rungholt.scene.Terrain;
import com.dan.rungholt.ui.AppIcon;
import com.dan.rungholt.wiring.Beaufort;
import com.dan.rungholt.wiring.Collider;
import com.dan.rungholt.wiring.Wiring;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Prüfprogramm der App ohne Grafikkarte und ohne Datenbank (aus Glacier). In NetBeans: Rechtsklick → „Run File“,
 * oder Ant-Target {@code pruefung}. Endet mit „ERGEBNIS: alles in Ordnung“ oder mit der Liste der Verstöße
 * (Rückgabewert 1). Wächst mit jeder Phase. Die Packages haben zusätzlich je ihre eigene Prüfklasse in {@code .demo}.
 */
public final class Pruefung {
    private static int ok, bad;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        mathe();
        ort();
        sonne();
        uhr();
        pegel();
        buehne();
        gelaende();
        ueberflutung();
        kamera();
        qualitaet();
        physik();
        shader();
        verdrahtung();
        daten();
        wetter();
        symbol();
        pakete();
        isolation();
        System.out.printf(Locale.GERMAN, "%n%d Prüfungen, %d Verstöße, %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + bad + " Verstöße");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean cond, String what) {
        if (cond) ok++; else bad++;
        System.out.println((cond ? "  ok    " : "  FEHLER ") + what);
    }

    static void mathe() {
        System.out.println("Matrizen");
        float[] v = Mat4.lookAt(10, 20, 30, 0, 14, 0);
        float[] p = Mat4.perspective(55, 16 / 9.0, 0.5, 80000, true);
        float[] vp = Mat4.mul(p, v), id = Mat4.mul(vp, Mat4.invert(vp));
        double err = 0;
        for (int i = 0; i < 16; i++) err = Math.max(err, Math.abs(id[i] - (i % 5 == 0 ? 1 : 0)));
        check(err < 1e-4, String.format(Locale.GERMAN, "Projektion·Blick mal Inverse = Einheit (Fehler %.1e)", err));
        float[] look = Mat4.lookAt(0, 0, 0, 0, 0, -1);
        double[] c = Mat4.apply(Mat4.mul(p, look), 0, 0, -100);
        check(Math.abs(c[2] / c[3] - 0.5 / 100) < 1e-6, "Umgekehrte Tiefe: Punkt in 100 m hat Tiefe Nahebene/100");
        double[] far = Mat4.apply(Mat4.mul(p, look), 0, 0, -12000);
        check(far[2] / far[3] > 0 && far[2] / far[3] < 1e-4, "Berge in 12 km liegen zwischen 0 und Nahebene, vor dem Unendlichen");
    }

    static void ort() {
        System.out.println("Ort und Maßstab");
        double[] sf = Site.suedfall();
        check(Math.abs(sf[0] + 1000) < 0.5 && Math.abs(sf[1]) < 0.5, String.format(Locale.GERMAN, "Warft von Südfall liegt 1 km westlich der Kirche [G]: x %.1f m, z %.1f m", sf[0], sf[1]));
        check(Site.dms(Site.LAT, Site.LON).startsWith("54°27′53″ N · 8°44′"), "Kirche [G] in Grad, Minuten, Sekunden: " + Site.dms(Site.LAT, Site.LON));
        double x = 1234.5, z = -987.25;
        double[] back = Site.xz(Site.latLon(x, z));
        check(Math.abs(back[0] - x) < 1e-6 && Math.abs(back[1] - z) < 1e-6, "Szene ↔ Breite/Länge hin und zurück");
        check(Site.lat(-1000) > Site.LAT && Site.lon(1000) > Site.LON, "Nord ist −z (Breite steigt), Ost ist +x");
        double[] u = Geo.UTM32N.forward(Site.LAT, Site.LON), ll = Geo.UTM32N.inverse(u[0], u[1]);
        check(Math.abs(ll[0] - Site.LAT) < 1e-9 && Math.abs(ll[1] - Site.LON) < 1e-9 && u[0] < 500_000 && u[1] > 6_000_000,
                String.format(Locale.GERMAN, "UTM 32 Nord hin und zurück: %.0f E, %.0f N", u[0], u[1]));
        // Die Warft in den Höhendaten: Zelle (Zeile 618, Spalte 797) des Zuschnitts ab 474 039 E, 6 041 453 N, 10 m
        double[] w = Site.fromUtm(474_039 + 797.5 * 10, 6_041_453 - 618.5 * 10);
        check(Math.hypot(w[0] - sf[0], w[1] - sf[1]) < 15, String.format(Locale.GERMAN, "Zelle der Warft aus UTM 32 trifft Südfall auf %.1f m", Math.hypot(w[0] - sf[0], w[1] - sf[1])));
        double[] nw = Site.xz(new double[]{Site.NORTH, Site.WEST}), se = Site.xz(new double[]{Site.SOUTH, Site.EAST});
        double wkm = (se[0] - nw[0]) / 1000, hkm = (se[1] - nw[1]) / 1000;
        check(wkm > 13 && wkm < 15 && hkm > 11.5 && hkm < 12.8, String.format(Locale.GERMAN, "Ausschnitt F1: %.1f × %.1f km (Werkbuch: rund 14 × 12 km)", wkm, hkm));
        check(Math.abs(Site.TIDAL_RANGE - 3.5) < 1e-9, "Tidenhub Husum [G]: MThw +1,6 m, MTnw −1,9 m, Hub 3,5 m");
        check(Math.abs(Site.waveSpeed(2) - 4.43) < 0.01, String.format(Locale.GERMAN, "Lange Welle bei 2 m Tiefe: %.1f m/s, 1 km in %.0f s", Site.waveSpeed(2), 1000 / Site.waveSpeed(2)));
    }

    static void sonne() {
        System.out.println("Sonne (54,5° Nord)");
        SceneClock c = new SceneClock(LocalDate.of(2026, 6, 21), 0);
        double day = c.epochMillis();
        double up = hourOf(Sun.crossing(day, Site.LAT, Site.LON, true)), down = hourOf(Sun.crossing(day, Site.LAT, Site.LON, false));
        check(up > 4.5 && up < 5.2 && down > 21.8 && down < 22.4, "21.6.2026 Aufgang " + hm(up) + ", Untergang " + hm(down) + " MESZ");
        check(down - up > 16.8 && down - up < 17.6, String.format(Locale.GERMAN, "längster Tag %.2f h", down - up));
        double best = -90, bestH = 0;
        for (double h = 11; h < 16; h += 1 / 60.0) {
            c.set(LocalDate.of(2026, 6, 21), h);
            double e = c.sun()[0];
            if (e > best) { best = e; bestH = h; }
        }
        c.set(LocalDate.of(2026, 6, 21), bestH);
        double az = c.sun()[1];
        check(Math.abs(az - 180) < 3 && bestH > 13.2 && bestH < 13.7 && best > 58 && best < 60,
                String.format(Locale.GERMAN, "Höchststand %s MESZ bei %.1f°, Azimut %03.0f° – die Sonne steht mittags im Süden", hm(bestH), best, az));
        SceneClock dez = new SceneClock(LocalDate.of(2026, 12, 21), 0);
        double du = hourOf(Sun.crossing(dez.epochMillis(), Site.LAT, Site.LON, true)), dd = hourOf(Sun.crossing(dez.epochMillis(), Site.LAT, Site.LON, false));
        check(dd - du > 6.8 && dd - du < 7.6, String.format(Locale.GERMAN, "21.12.2026 Tag %.1f h (%s bis %s MEZ)", dd - du, hm(du), hm(dd)));
    }

    static void uhr() {
        System.out.println("Uhr");
        SceneClock c = new SceneClock();
        check(c.label().equals("21.7.2026 13:30 MESZ"), "Start: " + c.label());
        check(c.local().getOffset().getTotalSeconds() == 2 * 3600, "Sommerzeit UTC+2");
        c.lapse = 60;
        c.step(30);
        check(c.label().equals("21.7.2026 14:00 MESZ"), "Zeitraffer 1 min/s, 30 s → " + c.label());
        double e0 = c.epochMillis();
        c.lapse = 1e9;
        c.step(1);
        check(Math.abs(c.epochMillis() - e0 - SceneClock.MAX_LAPSE * 1000) < 1e-3, "Zeitraffer höchstens 3600-fach");
        c.set(LocalDate.of(2026, 12, 31), 23.5);
        c.lapse = 3600;
        c.step(1);
        check(c.dateLabel().equals("1.1.2027") && c.zoneLabel().equals("MEZ"), "Jahreswechsel in MEZ: " + c.label());
        SceneClock m = new SceneClock(Site.MANDRAENKE.minusDays(1), 18);
        check(m.label().equals("23.1.1362 (jul. 15.1.) 18:00 Ortszeit") && m.epochMillis() < 0, "1362 rechnet die Uhr mit Ortszeit und nennt das julianische Datum: " + m.label());
        check(Site.MANDRAENKE.equals(LocalDate.of(1362, 1, 24)) && java.util.Arrays.equals(SceneClock.julian(LocalDate.of(1582, 10, 15)), new int[]{1582, 10, 5})
                        && SceneClock.gregorian(2026, 10, 7).equals(LocalDate.of(2026, 10, 20)) && new SceneClock(LocalDate.of(1582, 10, 15), 12).dateLabel().equals("15.10.1582"),
                "Kalender: 16.1.1362 julianisch ist der 24.1. gregorianisch, 15.10.1582 folgt auf den 4.10. julianisch, ab dann nur gregorianisch");
        SceneClock s = new SceneClock();
        s.lapse = 0;
        s.step(2.5);
        check(Math.abs(s.t() - 2.5) < 1e-9 && s.label().equals("21.7.2026 13:30 MESZ"), "Uhr steht, Laufzeit läuft weiter (Wellen bewegen sich)");
    }

    /** Phase 4: die Tide aus com.dan.tide statt des Platzhalters. */
    static void pegel() {
        System.out.println("Tide (Phase 4)");
        SceneClock c = new SceneClock();
        Wiring w = new Wiring(c);
        double h = w.level.hours();
        com.dan.tide.Tide.Turn nw = w.level.husum.next(h - 3, false);
        java.time.ZonedDateTime z = nw.instant().atZone(SceneClock.ZONE);
        check(Math.abs(z.getHour() * 60 + z.getMinute() - (13 * 60 + 30)) <= 15, String.format(Locale.GERMAN,
                "Husum 21.7.2026: Niedrigwasser %d:%02d MESZ (Gezeitenkalender des BSH: 13:30)", z.getHour(), z.getMinute()));
        check(w.level.level() < -1.4, String.format(Locale.GERMAN, "Start 13:30 MESZ: Pegel an der Norderhever NHN %+.2f m, das Watt liegt frei", w.level.level()));
        c.lapse = 3600;
        c.step(2);
        check(w.level.rising(), "zwei Stunden später: Flut");
        w.level.hand = 1.0;
        check(w.level.level() == 1.0 && w.level.floodTide().levelAt(h) == 1.0 && !w.level.rising(), "Pegel von Hand hält, auch für die Überflutung");
        w.level.hand = Double.NaN;
        com.dan.tide.Tide.Turn a = w.level.husum.next(h, true), b = w.level.tide.next(a.hours() - 2, true);
        check(Math.abs((a.hours() - b.hours()) * 60 - 27) < 2, String.format(Locale.GERMAN, "Rand (Norderhever [G]): Hochwasser %.0f min vor Husum", (a.hours() - b.hours()) * 60));
        // Windstau aus dem Wetter: Orkan aus WNW
        w.wind.speed = 33;
        w.wind.fromDeg = 295;
        for (int k = 0; k < 36; k++) w.feedTide(0.25);
        check(w.level.surge() > 3.0 && w.level.surge() < 3.6, String.format(Locale.GERMAN, "Orkan aus WNW, 9 h: Windstau %+.2f m", w.level.surge()));
        w.feedTide(12);
        check(Math.abs(w.level.surge() - com.dan.tide.Tide.surgeTarget(33, 295)) < 1e-9, "nach einem Sprung der Uhr steht der Windstau gleich am Ziel");
    }

    static void buehne() {
        System.out.println("Bühne (Phase 1)");
        check(Math.abs(Buehne.height(Buehne.WARFT_X, Buehne.WARFT_Z) - Site.SUEDFALL_WARFT) < 1e-9 && Buehne.zone(Buehne.WARFT_X, Buehne.WARFT_Z).equals("Warft von Südfall"),
                "Warft von Südfall auf NHN +5,8 m (aus den Höhendaten)");
        check(Math.abs(Buehne.height(-600, -80) - Buehne.HALLIG_H) < 0.05 && Buehne.zone(-600, -80).equals("Südfall"), "Hallig auf NHN +1,8 m, über dem mittleren Hochwasser");
        double hev = Buehne.height(Buehne.HEVER_X, Buehne.HEVER_Z);
        check(hev < -17 && Buehne.zone(Buehne.HEVER_X, Buehne.HEVER_Z).equals("Norderhever"), String.format(Locale.GERMAN, "Norderhever bis NHN %.1f m", hev));
        double wmin = 9, wmax = -9;
        for (double x = 0; x <= 4000; x += 50) for (double z = -1500; z <= 1500; z += 50) { double h = Buehne.height(x, z); wmin = Math.min(wmin, h); wmax = Math.max(wmax, h); }
        check(wmin > Site.MTNW && wmax < Site.MTHW, String.format(Locale.GERMAN, "Watt um die Kirche zwischen NHN %+.2f und %+.2f m: fällt bei jedem Niedrigwasser frei", wmin, wmax));
        check(Buehne.diked(-6170, -5580) && Math.abs(Buehne.height(-6170, -5580) - Buehne.KOOG) < 1e-9 && Buehne.zone(-6170, -5580).equals("Pellworm"), "Pellworm: Koog hinter dem Deich, dort steht kein Meer");
        check(Buehne.height(5240, -2000) > 7.5 && Buehne.zone(5240, -2000).equals("Nordstrand"), String.format(Locale.GERMAN, "Deich von Nordstrand bei Fuhlehörn: NHN %+.1f m", Buehne.height(5240, -2000)));
        check(Buehne.height(0, Buehne.PRIEL_Z) < -7.5, "Priel im Süden bis NHN −8 m");
        Terrain f = Terrain.flat();
        double d = 0;
        for (int k = 0; k < 200; k++) {
            int i = (k * 9973) % Terrain.N, j = (k * 7919) % Terrain.N;
            d = Math.max(d, Math.abs(f.at(i, j) - Buehne.height(Terrain.cx(i), Terrain.cz(j))));
        }
        check(!f.measured() && d < 0.002, String.format(Locale.GERMAN, "ohne daten/gelaende: die Bühne als Raster (größte Abweichung %.1f mm)", d * 1000));
        check(f.artAt(-6170, -5580) == Terrain.KOOG && f.artAt(0, 0) == Terrain.WATT && f.artAt(Buehne.WARFT_X, Buehne.WARFT_Z) == Terrain.ERHOEHT,
                "Arten der Bühne: Koog hinter dem Deich von Pellworm, Watt an der Kirche, Warft erhöht");
    }

    /** Phase 2: das Gelände aus den BAW-Küstenhöhen, wenn daten/gelaende da ist. */
    static void gelaende() {
        System.out.println("Gelände (Phase 2)");
        File dir = Terrain.defaultDir();
        if (!new File(dir, Terrain.INFO).isFile()) { System.out.println("  (daten/gelaende fehlt: erst tools.GelaendeImport laufen lassen; übersprungen)"); return; }
        Terrain t = Terrain.load(dir);
        check(t.measured() && t.source.startsWith("BAW"), "geladen: " + t.describe());
        double wy = Double.parseDouble(t.props.getProperty("warft.y")), wx = Double.parseDouble(t.props.getProperty("warft.x")), wz = Double.parseDouble(t.props.getProperty("warft.z"));
        double[] sf = Site.suedfall();
        check(wy > 5 && wy < 6.2 && Math.hypot(wx - sf[0], wz - sf[1]) < 30, String.format(Locale.GERMAN, "Warft von Südfall NHN %+.2f m, %.0f m neben der Lage aus der Datenprüfung", wy, Math.hypot(wx - sf[0], wz - sf[1])));
        double watt = Double.parseDouble(t.props.getProperty("watt.km2"));
        check(watt > 100 && watt < 200, String.format(Locale.GERMAN, "%.0f km² Watt zwischen MTnw und MThw im Raster (16,4 km)", watt));
        double lo = Double.parseDouble(t.props.getProperty("tiefste")), hi = Double.parseDouble(t.props.getProperty("hoechste"));
        check(lo < -25 && lo > -35 && hi > 7 && hi < 12, String.format(Locale.GERMAN, "Höhen NHN %.1f m (Norderhever) bis %+.1f m (Deiche)", lo, hi));
        check(t.artAt(-6800, -5800) == Terrain.KOOG && !t.seaward(-6800, -5800), "Pellworm: Koog hinter dem Deich, dort steht kein Meer");
        check(t.artAt(0, 0) == Terrain.WATT && t.seaward(0, 0) && t.waterDepth(0, 0, Site.MTHW) > 1, String.format(Locale.GERMAN, "an der Kirche [G] Watt auf NHN %+.2f m, bei mittlerem Hochwasser %.1f m Wasser", t.height(0, 0), t.waterDepth(0, 0, Site.MTHW)));
        check(t.height(-3000, 800) < -3 && t.height(600, 2500) < -15, "Priele und Strom: westlich von Südfall unter NHN −3 m, im Süden unter −15 m");
        double[] sh = t.shares();
        check(sh[Terrain.KOOG] + sh[Terrain.RAND] > 0.1 && sh[Terrain.WATT] > 0.6 && sh[Terrain.ERHOEHT] > 0.002, String.format(Locale.GERMAN, "Anteile: Watt %.0f %%, Köge %.0f %%, Deich/Warft %.1f %%", sh[0] * 100, (sh[3] + sh[4]) * 100, sh[2] * 100));
    }

    /** Phase 4: die Überflutung auf dem Gelände (BAW-Küstenhöhen), 16-m-Gitter wie „Mittel“. */
    static void ueberflutung() {
        System.out.println("Überflutung (Phase 4)");
        if (!new File(Terrain.defaultDir(), Terrain.INFO).isFile()) { System.out.println("  (daten/gelaende fehlt; übersprungen)"); return; }
        SceneClock c = new SceneClock(LocalDate.of(2026, 7, 21), 13.5);
        Wiring w = new Wiring(c);
        Renderer r = new Renderer(c, w);
        try { r.cacheDir = Files.createTempDirectory("rungholt-tide").toFile(); } catch (java.io.IOException e) { r.cacheDir = null; }
        long t0 = System.nanoTime();
        r.buildFlood(2);
        double buildS = (System.nanoTime() - t0) / 1e9;
        com.dan.tide.Flood f = r.flood();
        double tK = f.travelAt(0, 0), tH = f.travelAt(-3000, 800);
        check(f != null && f.nx == 1024 && tK > 10 * 60 && tK < 40 * 60 && tH < tK, String.format(Locale.GERMAN,
                "Karte 1024² zu 16 m in %.1f s: die Flut braucht vom Rand %.0f min zur Kirche [G], %.0f min in die Hever westlich von Südfall", buildS, tK / 60, tH / 60));
        check(Double.isInfinite(f.spillAt(-6800, -5800)), "der Koog von Pellworm hat keine Verbindung zum Meer (hinter dem Deich)");
        // Niedrigwasser 21.7.2026 13:30: Kirche trocken, Hever nass; Hochwasser 19:45: Kirche unter Wasser
        double lw = w.level.husum.next(w.level.hours() - 2, false).hours(), hw = w.level.husum.next(lw, true).hours();
        c.setEpochMillis(lw * 3_600_000);
        r.stepTideNow();
        check(f.depthAt(0, 0) == 0 && f.depthAt(-3000, 800) > 1 && f.wetShare < 0.55, String.format(Locale.GERMAN,
                "Niedrigwasser: an der Kirche trocken, in der Hever %.1f m Wasser, nass %.0f %% der Fläche", f.depthAt(-3000, 800), f.wetShare * 100));
        // Flut: in 5-min-Schritten bis Hochwasser; zur halben Flut liegen die nassen Zellen näher am Rand (Priele) als die trockenen
        double half = 0;
        for (double t = lw; t <= hw + 1e-6; t += 1 / 12.0) {
            c.setEpochMillis(t * 3_600_000);
            r.stepTideNow();
            if (half == 0 && t >= (lw + hw) / 2) {
                double tw = 0, td = 0, gw = 0, gd = 0;
                int nw = 0, nd = 0;
                for (int k = 0; k < f.travel.length; k++) {
                    if (Float.isInfinite(f.travel[k]) || f.ground[k] < Site.MTNW || f.ground[k] > Site.MTHW) continue;
                    if (f.surface[k] - f.ground[k] > 0.01) { tw += f.travel[k]; gw += f.ground[k]; nw++; } else { td += f.travel[k]; gd += f.ground[k]; nd++; }
                }
                half = 1;
                check(nw > 0 && nd > 0 && tw / nw < td / nd && gw / nw < gd / nd, String.format(Locale.GERMAN,
                        "halbe Flut: nasses Watt im Mittel %.0f min vom Rand und NHN %+.2f m, trockenes %.0f min und %+.2f m – die Flut kommt durch die Priele",
                        tw / nw / 60, gw / nw, td / nd / 60, gd / nd));
            }
        }
        check(f.depthAt(0, 0) > 1.0, String.format(Locale.GERMAN, "Hochwasser: %.2f m Wasser über der Kirche [G]", f.depthAt(0, 0)));
        // Ebbe: dieselbe Richtung zurück, in den Senken bleiben Pfützen
        for (double t = hw; t <= hw + 6.5; t += 1 / 12.0) { c.setEpochMillis(t * 3_600_000); r.stepTideNow(); }
        check(f.puddles > 100, String.format(Locale.GERMAN, "nach der Ebbe %d Pfützenzellen (höchstens %.2f m tief)", f.puddles, w.level.tide.maxPuddle));
        double dry = f.dryShareAbove(Site.MTNW + 0.3);
        check(dry > 0.8, String.format(Locale.GERMAN, "Niedrigwasser: %.0f %% des Watts über MTnw + 0,3 m liegen trocken", dry * 100));
        long s0 = System.nanoTime();
        for (int k = 0; k < 10; k++) { c.setEpochMillis(c.epochMillis() + 60_000); r.stepTideNow(); }
        double ms = (System.nanoTime() - s0) / 1e6 / 10;
        check(ms < 60, String.format(Locale.GERMAN, "ein Schritt %.1f ms auf %d Kernen (Ziel im Faden: unter 4 ms auf dem Ryzen mit 16 Fäden)", ms, Runtime.getRuntime().availableProcessors()));
        String tf = Shader.read("terrain.frag");
        String ov;
        try (java.io.InputStream in = com.dan.ocean.gl.OceanGl.class.getResourceAsStream("surface.vert")) { ov = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        catch (Exception e) { ov = ""; }
        check(tf.contains("floodLevel(xz)") && tf.contains("uFloodRect") && ov.contains("depth += rise") && ov.contains("min(-depth, 0.0) - 1.0"),
                "Bild: das Gelände nimmt die nasse Kante aus der Wassertextur, das Meer seine Tiefe unter dem Spiegel je Zelle (trocken: nie über dem Grund)");
        c.setEpochMillis((hw + 3) * 3_600_000);
        r.stepTideNow();
        double[] u = f.velocityAt(-3000, 800);
        check(f.maxCurrent > 0.2 && Math.hypot(u[0], u[1]) > 0.05, String.format(Locale.GERMAN,
                "halbe Ebbe: Strömung in der Hever westlich von Südfall %.2f m/s, im Ausschnitt bis %.2f m/s (Betrag [G]; im Bild ab Phase 6)", Math.hypot(u[0], u[1]), f.maxCurrent));
        watt(r, w, c, f, hw);
    }

    /** Phase 5: das Watt als Material auf der Überflutung (dieselbe Karte). */
    static void watt(Renderer r, Wiring w, SceneClock c, com.dan.tide.Flood f, double hw) {
        System.out.println("Watt (Phase 5)");
        com.dan.mudflat.WetField wf = r.wetField();
        check(wf != null && wf.nx == f.nx && wf.cell == f.cell && w.wetness.field == wf, "das Feld liegt auf dem Gitter der Überflutung und hängt an der Verdrahtung (Wetness)");
        double[] sh = wf.shares(f.ground, Site.MTNW, Site.MTHW);
        check(sh[0] > 0.1 && sh[2] > 0.05, String.format(Locale.GERMAN, "Sediment im Watt zwischen MTnw und MThw: Sand %.0f %%, Mischwatt %.0f %%, Schlick %.0f %%", sh[0] * 100, sh[1] * 100, sh[2] * 100));
        // Kanten und Priele sandiger als die Buchten am Vorland
        double sDeep = 0, sHigh = 0;
        int nDeep = 0, nHigh = 0;
        for (int k = 0; k < f.ground.length; k++) {
            if (Float.isInfinite(f.travel[k])) continue;
            if (f.ground[k] < Site.MTNW - 1) { sDeep += wf.sediment[k]; nDeep++; }
            else if (f.ground[k] > Site.MTHW - 0.6 && f.ground[k] < Site.MTHW) { sHigh += wf.sediment[k]; nHigh++; }
        }
        check(nDeep > 0 && nHigh > 0 && sDeep / nDeep < sHigh / nHigh - 0.2, String.format(Locale.GERMAN, "Rinnen sandig (%.2f), obere Watten am Vorland schlickig (%.2f)", sDeep / nDeep, sHigh / nHigh));
        // nach dem nächsten Niedrigwasser: wer länger trocken liegt, ist trockener
        for (double t = hw + 3; t <= hw + 6.5; t += 1 / 12.0) { c.setEpochMillis(t * 3_600_000); r.stepTideNow(); }
        double wLong = 0, wShort = 0;
        int nL = 0, nS = 0;
        for (int k = 0; k < f.ground.length; k++) {
            if (f.surface[k] - f.ground[k] > 0.01 || Float.isInfinite(f.travel[k]) || wf.sediment[k] > 0.33) continue;
            if (f.dryHours[k] > 3) { wLong += wf.wet[k]; nL++; } else if (f.dryHours[k] < 0.7) { wShort += wf.wet[k]; nS++; }
        }
        check(nL > 0 && nS > 0 && wLong / nL < wShort / nS - 0.15, String.format(Locale.GERMAN, "Sand: über 3 h trocken %.0f %% nass, unter 0,7 h %.0f %% (im Schatten, ohne Sonne im Test)", wLong / nL * 100, wShort / nS * 100));
        double wn = w.wetness.wetnessAt(0, 0);
        check(wn >= 0 && wn <= 1 && f.depthAt(-3000, 800) > 0.01 == (w.wetness.wetnessAt(-3000, 800) == 1), String.format(Locale.GERMAN, "Wetness: an der Kirche %.0f %%, in der Hever nass", wn * 100));
        check(wf.stepMs < 40, String.format(Locale.GERMAN, "Schritt des Watts %.1f ms auf %d Kernen (Ziel auf dem Ryzen unter 1,5 ms)", wf.stepMs, Runtime.getRuntime().availableProcessors()));
        String tf = Shader.read("terrain.frag");
        check(tf.contains("#include \"/com/dan/mudflat/gl/mudflat.glsl\"") && tf.contains("mudflatR(xz, nw, above, pxD, runnel, n.xz)") && Shader.class.getResource("/com/dan/mudflat/gl/mudflat.glsl") != null,
                "Bild: das Gelände nimmt das Material des Watts aus com.dan.mudflat (Albedo, Rippel, Wasserfilm)");
        check(r.mudLine().startsWith("Watt: "), r.mudLine());
        priel(r, w, c, f, hw);
        seehunde(r, w, c, f);
        voegel(r, w, c, f);
        wanderung(r, w, c, f);
        land1362(r, w, c);
        stadt(r, w, c);
        mandraenke(r, w, c);
        if (!Boolean.getBoolean("ohne13")) spuren(r, w, c);   // nach der Mandränke: der Sturm des Regiebuchs bleibt in der Regie
        if (!Boolean.getBoolean("ohne14")) klang(r, w, c);
        if (!Boolean.getBoolean("ohne15")) regie(r, w, c);
        if (!Boolean.getBoolean("ohne16")) { try { datenSzene(r, w, c); } catch (Exception e) { e.printStackTrace(); check(false, "Phase 16 an der Szene: " + e); } }
    }

    /**
     * Phase 13: die Spuren (genau unter den Warften), die Magnetik, die Fundstellen, die Grabung bei Ebbe, die
     * Überblendung (Häuser, Gras, Kamera), die Warftkarte, die Glocken und ihr Regiebuch.
     */
    static void spuren(Renderer r, Wiring w, SceneClock c) {
        System.out.println("Die Zeitreise und die Spuren (Phase 13)");
        r.stopMandraenke(true);   // nach der Mandränke: ruhiges Wetter, kein Windstau
        w.regie = false;
        w.resumeRegie();
        w.wind.speed = Beaufort.mid(3);
        w.surgeScript = Double.NaN;
        w.level.tide.surge = 0;
        long t0 = System.nanoTime();
        r.buildSpuren();
        com.dan.rungholt.scene.Spuren sp = r.spuren;
        double ms = (System.nanoTime() - t0) / 1e6;
        if (sp == null) { check(false, "die Spurenkarte wird gebaut"); return; }
        // genau unter den Warften: der Schwerpunkt des Kleis um jede Warft liegt auf ihrer Mitte, die Mitte ist Spur
        int n = 0, centred = 0, under = 0;
        double worst = 0;
        double C = com.dan.rungholt.scene.Spuren.CELL, X0 = com.dan.rungholt.scene.Spuren.X0, Z0 = com.dan.rungholt.scene.Spuren.Z0;
        for (com.dan.rungholt.scene.Land1362.Warft wf : com.dan.rungholt.scene.Land1362.warften()) {
            n++;
            if (sp.sample(wf.x(), wf.z(), 0) > 0.5 && com.dan.rungholt.scene.Spuren.warftAt(wf.x(), wf.z()) == wf) under++;
            double sx = 0, sz = 0, sw = 0, rad = Math.min(wf.a(), wf.b());   // nur die Krone: der Rand ist ausgefranst
            for (double z = wf.z() - rad; z <= wf.z() + rad; z += C)
                for (double x = wf.x() - rad; x <= wf.x() + rad; x += C) {
                    double k = sp.sample(x, z, 0);
                    sx += k * x; sz += k * z; sw += k;
                }
            double off = sw > 0 ? Math.hypot(sx / sw - wf.x(), sz / sw - wf.z()) : 1e9;
            worst = Math.max(worst, off);
            if (off < 2 * C) centred++;
        }
        check(n == 54 && under == 54 && centred == 54, String.format(Locale.GERMAN,
                "54 Warften als Abdruck im Watt, jede Mitte liegt auf ihrer Spur, der Schwerpunkt des Kleis höchstens %.1f m neben der Warft", worst));
        double outside = 0;
        for (int k = 0; k < 2000; k++) {
            double x = -3000 + 6000 * ((k * 0.618034) % 1), z = -2500 + 2500 * ((k * 0.381966) % 1);
            boolean near = false;
            for (com.dan.rungholt.scene.Land1362.Warft wf : com.dan.rungholt.scene.Land1362.warften()) if (Math.hypot(x - wf.x(), z - wf.z()) < wf.radius() + 8) near = true;
            if (!near && Math.abs(com.dan.rungholt.scene.Land1362.dikeSide(x, z)) > 16 && Math.hypot(x - com.dan.rungholt.scene.Land1362.SIEL[0], z - com.dan.rungholt.scene.Land1362.SIEL[1]) > 30)
                outside = Math.max(outside, sp.sample(x, z, 0));
        }
        check(outside < 0.02, String.format(Locale.GERMAN, "außerhalb der Warften und des Deichs kein Klei (größter Wert %.3f)", outside));
        check(ms < 8000 && sp.surveyKm2 > 6 && sp.surveyKm2 < 12, String.format(Locale.GERMAN, "Spurenkarte in %.1f s, Magnetik auf %.1f km² (Werkbuch: über 10 km² Messfläche)", ms / 1000, sp.surveyKm2));
        double mS = sp.magnetik(0, 9), mN = sp.magnetik(0, -9), mC = sp.magnetik(0, 0);
        check(!Double.isNaN(mC) && Math.max(mS, mC) > 3 && mS > mN, String.format(Locale.GERMAN, "Magnetik an der Kirche: %+.1f nT in der Mitte, %+.1f nT südlich, %+.1f nT nördlich (Plus südlich der Quelle, Inklination 69°)", mC, mS, mN));
        java.util.List<com.dan.rungholt.scene.Spuren.Fund> nearChurch = com.dan.rungholt.scene.Spuren.findsNear(0, 0, 100);
        check(com.dan.rungholt.scene.Spuren.FINDS.size() == 14 && nearChurch.stream().anyMatch(f -> f.year() == 2023 && f.name().startsWith("Kirche")) && com.dan.rungholt.scene.Spuren.wells().size() >= 30,
                String.format(Locale.GERMAN, "%d Fundstellen (Busch, Museum, 2023), die Kirche darunter; %d Sodenbrunnen", com.dan.rungholt.scene.Spuren.FINDS.size(), com.dan.rungholt.scene.Spuren.wells().size()));
        // der Kleirücken in Java wie im Shader, nur heute
        w.era.target = 0; w.era.blend = 0;
        r.selectEra();
        r.spurForce = true;
        double withSpur = r.groundHeight(0, 0);
        r.showSpuren = false;
        double without = r.groundHeight(0, 0);
        r.showSpuren = true;
        w.era.blend = 1;
        double then = r.groundHeight(0, 0), land = r.land1362.height(0, 0);
        w.era.blend = 0;
        check(Math.abs(withSpur - without - sp.height(0, 0)) < 1e-9 && sp.height(0, 0) > 0.15 && Math.abs(then - land) < 0.05, String.format(Locale.GERMAN,
                "Kleirücken an der Kirche %.0f cm über dem Watt (Kamera und Shader gleich), in 1362 gilt nur die Kirchwarft (NHN %+.2f m)", 100 * (withSpur - without), then));
        String vert = readOr("src/com/dan/rungholt/gl/shader/terrain.vert"), hgt = readOr("src/com/dan/rungholt/gl/shader/terrain_height.glsl"), frag = readOr("src/com/dan/rungholt/gl/shader/terrain.frag");
        check(vert.contains("spurHeight(p) * watt") && hgt.contains("uSpuren.w * spurAt(w, 0.0).r") && frag.contains("churchSd(xz)") && frag.contains("uWells") && frag.contains("ovl"),
                "Shader: Kleirücken im Gelände (Stufe 0 wie Java), Kirche, Brunnen, Gräben, Magnetik und Fundstellen als Karte nach dem Dunst");
        // die Grabung: kommen bei Ebbe, nie mit nassen Füßen an der Stelle, gehen vor der Flut
        c.set(java.time.LocalDate.of(2026, 7, 21), 8.0);
        r.stepTideNow();
        com.dan.rungholt.scene.Grabung g = r.grabung;
        g.state = com.dan.rungholt.scene.Grabung.State.FORT;
        double arrive = Double.NaN, leave = Double.NaN, wetAtWork = 0, firstWet = Double.NaN;
        for (int k = 0; k < 12 * 60 / 2; k++) {
            c.setEpochMillis(c.epochMillis() + 120_000);
            r.stepTideNow();
            g.step(120, r.digWorld, null);
            double hr = c.hour();
            if (g.state == com.dan.rungholt.scene.Grabung.State.ARBEIT && Double.isNaN(arrive)) arrive = hr;
            if (!Double.isNaN(arrive) && g.state == com.dan.rungholt.scene.Grabung.State.ABMARSCH && Double.isNaN(leave)) leave = hr;
            if (g.state == com.dan.rungholt.scene.Grabung.State.ARBEIT) wetAtWork = Math.max(wetAtWork, w.level.depthAt(g.site[0], g.site[1]));
            if (!Double.isNaN(leave) && Double.isNaN(firstWet) && w.level.depthAt(g.site[0], g.site[1]) > 0.02) firstWet = hr;
        }
        check(!Double.isNaN(arrive) && !Double.isNaN(leave) && wetAtWork < 0.02 && !Double.isNaN(firstWet) && firstWet - leave > 0.5, String.format(Locale.GERMAN,
                "Grabung am 21.7.2026: bei der Arbeit ab %s, zurück ab %s, die Stelle nass ab %s (%.0f min Vorsprung); nie Wasser an der Stelle (%.2f m)",
                hm(arrive), hm(leave), hm(firstWet), (firstWet - leave) * 60, wetAtWork));
        check(Math.hypot(g.site[0], g.site[1]) < 15 && com.dan.rungholt.scene.Spuren.churchSdf(g.site[0], g.site[1], com.dan.timber.Church.rungholt()) < 1.5 && g.pathLength > 300 && g.pathLength < 1200,
                String.format(Locale.GERMAN, "die Rahmen liegen über der Nordwand des Schiffs, der Weg von Südfall %.0f m", g.pathLength));
        com.dan.buoyancy.Mesh eq = com.dan.rungholt.scene.Grabung.equipment(r::groundHeight);
        check(eq.triangles() > 500 && eq.triangles() < 6000, String.format(Locale.GERMAN, "Gerät der Grabung: %,d Dreiecke (Rahmen, Eimer, Sieb, Stab, Fluchtstangen, Kiste)", eq.triangles()));
        // die Überblendung: Kirche zuerst, Häuser später, bei 1362 alle oben; das Gras wächst
        com.dan.timber.House ch = r.town.church, h0 = r.town.houses.get(0);
        check(Renderer.riseDepth(ch, 0.1, true) > 30 && Renderer.riseDepth(ch, 0.6, true) < 1e-9 && Renderer.riseDepth(h0, 0.2, false) > 10 && Renderer.riseDepth(h0, 1.0, false) == 0,
                "Überblendung: die Kirche steigt zuerst aus dem Boden (bis 60 %), dann die Häuser; bei 1362 stehen alle");
        double[] fen = {300, -400};
        double g0 = r.reedDensity(fen[0], fen[1], com.dan.reed.Reed.Kind.GRAS, 0), g5 = r.reedDensity(fen[0], fen[1], com.dan.reed.Reed.Kind.GRAS, 0.5), g1 = r.reedDensity(fen[0], fen[1], com.dan.reed.Reed.Kind.GRAS, 1);
        check(r.land1362.artAt(fen[0], fen[1]) == com.dan.rungholt.scene.Terrain.MARSCH && g0 == 0 && g5 > 0 && g5 < g1, String.format(Locale.GERMAN, "Gras über dem Sand: Dichte auf einer Fenne %.2f (heute), %.2f (50 %%), %.2f (1362)", g0, g5, g1));
        // die Warftkarte auf Klick
        r.camera.place(0, r.groundHeight(0, 100) + 50, 100, 0, Math.toDegrees(Math.atan2(50, 100)));
        double[] hit = r.pickGround(800, 450, 1600, 900);
        r.warftCard = null;
        r.clickAt(800, 450, 1600, 900);
        com.dan.rungholt.scene.Land1362.Warft wk = r.lastWarft;
        String[] card = r.warftCard;
        check(hit != null && Math.hypot(hit[0], hit[2]) < 4 && wk != null && wk.name().equals("Kirchwarft") && card != null && String.join("\n", card).contains("Kirche: Fundamente"),
                String.format(Locale.GERMAN, "Klick in die Bildmitte trifft den Grund %.1f m neben der Kirche: Warftkarte „%s“ mit %d Zeilen", hit == null ? -1 : Math.hypot(hit[0], hit[2]), wk == null ? "-" : wk.name(), card == null ? 0 : card.length));
        r.clickAt(800, 10, 1600, 900);
        check(r.warftCard == null, "Ein Klick schließt die offene Warftkarte");
        r.clickAt(800, 10, 1600, 900);
        check(r.warftCard == null && r.lastHit == null, "Klick in den Himmel öffnet keine Karte");
        // die Glocken
        check(com.dan.rungholt.scene.Glocken.sturmnacht(true, -8, 23, 275, 1.5) && !com.dan.rungholt.scene.Glocken.sturmnacht(true, -8, 23, 90, 1.5)
                        && !com.dan.rungholt.scene.Glocken.sturmnacht(true, 5, 23, 275, 1.5) && !com.dan.rungholt.scene.Glocken.sturmnacht(false, -8, 23, 275, 1.5)
                        && !com.dan.rungholt.scene.Glocken.sturmnacht(true, -8, 12, 275, 1.5) && !com.dan.rungholt.scene.Glocken.sturmnacht(true, -8, 23, 275, 0.2),
                "Glocken nach der Sage: nur heute, nach der Dämmerung, ab Bft 8 aus Südwest bis Nordwest, mit Wasser über der Kirchwarft");
        com.dan.rungholt.scene.Glocken gl = new com.dan.rungholt.scene.Glocken();
        for (int k = 0; k < 600; k++) gl.step(10, false);
        boolean quiet = !gl.ringing && gl.ghost == 0;
        int rings = 0;
        double ringS = 0;
        for (int k = 0; k < 8 * 360; k++) { gl.step(10, true); if (gl.ringing) ringS += 10; }
        rings = gl.peals;
        gl.force();
        for (int k = 0; k < 3; k++) gl.step(4, false);
        check(quiet && rings >= 8 && rings <= 60 && ringS / (8 * 3600) < 0.2 && gl.ringing && gl.ghost > 0.6, String.format(Locale.GERMAN,
                "ohne Sturmnacht still; in 8 h Sturmnacht %d-mal geläutet, %.0f %% der Zeit; „Glocken jetzt“ läutet auch so, Schemen %.0f %%", rings, 100 * ringS / (8 * 3600), gl.ghost * 100));
        // seit Phase 15 als Text: das erste warte des Regiebuchs liegt kurz vor dem Hochwasser des 30.11.2026
        com.dan.rungholt.scene.Regie.Book gb = com.dan.rungholt.scene.Regie.glocken();
        c.set(com.dan.rungholt.scene.Glocken.DAY, com.dan.rungholt.scene.Glocken.START_HOUR);
        double hwG = w.level.tide.next(w.level.hours(), true).hours();
        double firstWait = gb.cues().stream().filter(q -> q.warte() != null).findFirst().map(q -> q.warte().hours()).orElse(Double.NaN);
        java.time.ZonedDateTime hwT = java.time.Instant.ofEpochMilli((long) (hwG * 3_600_000)).atZone(SceneClock.ZONE);
        check(hwT.getHour() == 17 && firstWait < hwG && firstWait > hwG - 0.6, String.format(Locale.GERMAN,
                "Regiebuch „Die Glocken“: 30.11.2026, Hochwasser %02d:%02d, die Glocken %.0f min davor", hwT.getHour(), hwT.getMinute(), (hwG - firstWait) * 60));
        w.regie = false;
        w.resumeRegie();
        w.wind.speed = Beaufort.mid(3);
        r.spurForce = false;
        c.set(LocalDate.of(2026, 7, 21), 13.5);
        r.stepTideNow();
    }

    /** Phase 12: die Grote Mandränke (Windstau, Überlauf, Bruch mit fracture, Dammbruch mit wave, Marsch als Becken, Morgen). */
    /** Phase 14: der Klang an der Kamera (gl.Klang mit com.dan.soundscape), ohne Lautsprecher. */
    static void klang(Renderer r, Wiring w, SceneClock c) {
        System.out.println("Der Klang (Phase 14)");
        com.dan.rungholt.gl.Klang k = r.klang;
        k.offline = true;
        if (r.mandraenke != null) r.stopMandraenke(true);
        w.era.target = w.era.blend = 0;
        r.selectEra();
        w.regie = false;
        w.resumeRegie();
        w.surgeScript = Double.NaN;
        w.level.tide.surge = 0;
        w.wind.speed = Beaufort.mid(3);
        w.wind.fromDeg = 250;
        check(r.colony.seal.onCall != null && r.waders.onCall != null && r.lightning.onThunder != null && k.s.windCarry,
                "Rufe der Seehunde und Wattvögel, Donner der Blitze gehen an den Klang; der Wind trägt den Schall");
        // Niedrigwasser an der Kirche: das Watt knistert, die See ist fern; Hochwasser: die See ist da
        double lw = w.level.husum.next(w.level.hours(), false).hours();
        double hw = w.level.husum.next(lw, true).hours();
        c.setEpochMillis((lw - 1.5) * 3_600_000);
        for (int i = 0; i < 24; i++) { c.setEpochMillis(c.epochMillis() + 300_000); r.stepTideNow(); }
        ohr(r, 0, 0);
        k.feed(0.02);
        double crackLow = k.crackle, seaLow = k.seaFactor;
        c.setEpochMillis(hw * 3_600_000);
        for (int i = 0; i < 12; i++) { c.setEpochMillis(c.epochMillis() - 3_600_000 + i * 300_000 + 300_000); r.stepTideNow(); }
        c.setEpochMillis(hw * 3_600_000);
        r.stepTideNow();
        ohr(r, 0, 0);
        k.feed(0.02);
        check(crackLow > 0.2 && k.crackle == 0 && k.seaFactor > seaLow + 0.3, String.format(Locale.GERMAN,
                "an der Kirche bei Niedrigwasser knistert das Watt (%.0f %% der Fläche), See %.0f %%; bei Hochwasser kein Knistern, See %.0f %%", crackLow * 100, seaLow * 100, k.seaFactor * 100));
        // halbe Ebbe: die Priele laufen
        c.setEpochMillis((hw + 3) * 3_600_000);
        r.stepTideNow();
        ohr(r, -2600, 600);
        k.feed(0.02);
        double maxP = 0;
        for (com.dan.soundscape.Stream p : k.s.streams) maxP = Math.max(maxP, p.speed);
        check(maxP > 0.2, String.format(Locale.GERMAN, "halbe Ebbe in der Hever: ein Priel in der Nähe klingt mit %.2f m/s (Zelle ×%.0f für die Rinne [G])", maxP, com.dan.rungholt.gl.Klang.RINNE));
        // die Glocken aus dem Watt: läuten nur nach der Sage; mit dem Wind getragen, gegen ihn im Schallschatten
        r.glocken.ringing = false; r.glocken.forced = false;
        int b0 = k.wattBells;
        for (int i = 0; i < 200; i++) k.feed(0.05);
        int silent = k.wattBells - b0;
        r.glocken.force();
        for (int i = 0; i < 200; i++) k.feed(0.05);
        int rung = k.wattBells - b0;
        ohr(r, 4660, -1404);
        w.wind.speed = 23; w.wind.fromDeg = 275;
        k.feed(0.02);
        double with = k.s.carryDb(0, 0, 4870);
        w.wind.fromDeg = 95;
        k.feed(0.02);
        double against = k.s.carryDb(0, 0, 4870);
        w.wind.speed = Beaufort.mid(3); w.wind.fromDeg = 250;
        r.glocken.ringing = false; r.glocken.forced = false;
        check(silent == 0 && rung >= 3 && with > 3 && against < -20, String.format(Locale.GERMAN,
                "Glocken aus dem Watt: still ohne Sage, %d Schläge in 10 s, wenn sie läuten; bei Fuhlehörn mit dem Weststurm %+.0f dB, gegen den Wind %+.0f dB", rung, with, against));
        // 1362: Herdfeuer, Kaje, die Glocke zur Mittagsstunde
        w.era.target = w.era.blend = 1;
        r.selectEra();
        c.set(java.time.LocalDate.of(2026, 7, 21), 12.0);
        r.stepTideNow();
        ohr(r, 46.5, 23.3);
        int a0 = k.angelusBells, be0 = k.beastCalls;
        for (int i = 0; i < 1200; i++) { c.setEpochMillis(c.epochMillis() + 50); k.feed(0.05); }
        check(!k.s.blazes.isEmpty() && !k.s.pilings.isEmpty() && k.s.workshops.size() == 2 && k.angelusBells - a0 >= 30 && k.beastCalls - be0 > 0, String.format(Locale.GERMAN,
                "1362: %d Herdfeuer, Kaje mit %d Pfählen und zwei Werkstätten, Mittagsläuten (%d Schläge in 60 s), %d Rufe vom Vieh", k.s.blazes.size(), k.s.pilings.size(), k.angelusBells - a0, k.beastCalls - be0));
        w.era.target = w.era.blend = 0;
        r.selectEra();
        k.feed(0.02);
        check(k.s.blazes.isEmpty() && k.s.pilings.isEmpty() && k.s.workshops.isEmpty(), "zurück nach heute: keine Herdfeuer, keine Kaje");
        // die Hörproben liegen im Projekt
        File hp = new File("docs/hoerproben");
        String[] names = {"1_Ebbe_am_Priel", "2_Die_Bank", "3_Der_Hafen_1362", "4a_Mandraenke_Sturmlaeuten_auf_der_Kirchwarft", "4b_Mandraenke_der_Bruch",
                "5_Glocken_Fuhlehoern_mit_dem_Wind", "6_Glocken_Fuhlehoern_gegen_den_Wind"};
        int have = 0;
        for (String n : names) if (new File(hp, n + ".wav").length() > 100_000 && new File(hp, n + ".png").isFile()) have++;
        check(have == names.length, String.format(Locale.GERMAN, "Hörproben in docs/hoerproben: %d von %d (WAV und Spektrogramm; tools.Hoerproben)", have, names.length));
        String ren = readOr("src/com/dan/rungholt/gl/Renderer.java");
        check(ren.contains("double wob = 0.06 * Math.sin(clock.t() * 0.35)"), "der Schemen der Kirche schwankt kaum noch (±0,09 m statt ±0,4 m mit dem Wellengang)");
        k.offline = false;
    }

    /** Phase 16: Schema RUNGHOLT ohne Datenbank (Skripte, Spalten, Startdaten, Pegel), Dateien, Wasserlinie. */
    static void daten() throws Exception {
        System.out.println("Datenbank (Phase 16)");
        File dir = new File("db/sql");
        java.util.Map<String, java.util.List<com.dan.store.Skript.Anweisung>> sk = new java.util.LinkedHashMap<>();
        boolean lesbar = true;
        for (String n : new String[]{"01_tabellen.sql", "02_spatial.sql", "03_sichten.sql", "04_startdaten.sql", "99_entfernen.sql"}) {
            try { sk.put(n, com.dan.store.Skript.zerlegen(Files.readString(new File(dir, n).toPath(), StandardCharsets.UTF_8))); }
            catch (Exception e) { lesbar = false; System.out.println("    " + n + ": " + e); }
        }
        check(lesbar, "fünf Skripte in db/sql, alle zerlegbar wie in SQL Developer (" + sk.values().stream().mapToInt(java.util.List::size).sum() + " Anweisungen)");
        if (!lesbar) return;
        int tab = 0, seq = 0, trg = 0, idx = 0;
        for (var a : sk.get("01_tabellen.sql")) {
            String o = com.dan.store.Skript.objekt(a.sql());
            if (o == null) continue;
            if (o.startsWith("TABLE ")) tab++; else if (o.startsWith("SEQUENCE ")) seq++; else if (o.startsWith("TRIGGER ")) trg++; else if (o.startsWith("INDEX ")) idx++;
        }
        check(tab == 27 && seq == 26 && trg == 25, String.format(Locale.GERMAN, "01_tabellen: %d Tabellen, %d Sequenzen, %d Trigger, %d Indizes (RH_LOG ohne Trigger, RH_SCHEMA ohne ID)",
                tab, seq, trg, idx));
        String t01 = Files.readString(new File(dir, "01_tabellen.sql").toPath()), t02 = Files.readString(new File(dir, "02_spatial.sql").toPath()),
                t03 = Files.readString(new File(dir, "03_sichten.sql").toPath()), t04 = Files.readString(new File(dir, "04_startdaten.sql").toPath());
        check(!Pattern.compile("GENERATED\\s+(ALWAYS|BY\\s+DEFAULT)", Pattern.CASE_INSENSITIVE).matcher(t01).find(), "Sequence + Trigger, nie IDENTITY");
        boolean meta = true;
        for (String m : com.dan.rungholt.data.Verbindung.METADATEN) {
            String[] tc = m.split("\\.");
            meta &= t02.contains("VALUES ('" + tc[0] + "', '" + tc[1] + "'") && t02.contains("CREATE INDEX " + tc[0] + "_" + tc[1] + "_SX ON " + tc[0] + " (" + tc[1] + ")");
        }
        boolean vmeta = t02.contains("VALUES ('RH_WARFT_V', 'FLAECHE'") && t02.contains("VALUES ('RH_RUNGHOLT_V', 'GEOM'") && t02.contains("VALUES ('RH_TAGESKARTE_V', 'GEOM'");
        check(meta && vmeta && com.dan.rungholt.data.Verbindung.METADATEN.length == 11,
                "02_spatial: Metadaten (SRID 8307) und räumlicher Index für 11 Geometriespalten, Metadaten für 3 Sichten (QGIS)");
        boolean views = true;
        for (String v : com.dan.rungholt.data.Verbindung.SICHTEN) views &= t03.contains("CREATE OR REPLACE VIEW " + v + " AS");
        check(views && com.dan.rungholt.data.Verbindung.SICHTEN.length == 9, "03_sichten: die 9 Sichten der App, darunter RH_WARFT_V und RH_TAGESKARTE_V");
        // jede Spalte, die App und Skripte nennen, steht in 01_tabellen
        java.util.Map<String, java.util.Set<String>> spalten = new java.util.HashMap<>();
        for (var s : com.dan.store.Skript.spalten(sk.get("01_tabellen.sql"))) spalten.computeIfAbsent(s.tabelle(), x -> new java.util.HashSet<>()).add(s.name());
        java.util.Set<String> alle = new java.util.HashSet<>();
        for (var e : spalten.values()) alle.addAll(e);
        String app = Files.readString(new File("src/com/dan/rungholt/data/DbSpeicher.java").toPath()) + Files.readString(new File("src/com/dan/rungholt/data/Verbindung.java").toPath());
        app = app.replaceAll("\"\\s*\\+\\s*\"", "").replaceAll("\"\\s*\\n\\s*\\+\\s*\"", "");
        java.util.List<String> fehlt = new java.util.ArrayList<>();
        int geprueft = 0;
        for (String quelle : new String[]{app, t04}) {
            java.util.regex.Matcher m = Pattern.compile("INSERT INTO (RH_\\w+) \\(([^)]*)\\)").matcher(quelle);
            while (m.find()) for (String col : m.group(2).split(",")) { geprueft++; if (!spalten.getOrDefault(m.group(1), java.util.Set.of()).contains(col.strip())) fehlt.add(m.group(1) + "." + col.strip()); }
            m = Pattern.compile("MERGE INTO (RH_\\w+) t .*?WHEN NOT MATCHED THEN INSERT \\(([^)]*)\\)", Pattern.DOTALL).matcher(quelle);
            while (m.find()) for (String col : m.group(2).split(",")) { geprueft++; if (!spalten.getOrDefault(m.group(1), java.util.Set.of()).contains(col.strip())) fehlt.add(m.group(1) + "." + col.strip()); }
            m = Pattern.compile("UPDATE (RH_\\w+)(?: \\w)? SET (.*?)(?: WHERE|\" \\+ cols|\"\\))", Pattern.DOTALL).matcher(quelle);
            while (m.find()) {
                java.util.regex.Matcher c = Pattern.compile("(?:^|,\\s*|\\n\\s*)(?:t\\.)?([A-Z_]+) =").matcher(m.group(2));
                while (c.find()) { geprueft++; if (!spalten.getOrDefault(m.group(1), java.util.Set.of()).contains(c.group(1))) fehlt.add(m.group(1) + "." + c.group(1)); }
            }
            m = Pattern.compile("SELECT ([A-Z_, ]+?) FROM (RH_[A-Z_]+)(?! \\w+ JOIN)\\b").matcher(quelle);
            while (m.find()) {
                if (!spalten.containsKey(m.group(2))) continue;
                for (String col : m.group(1).split(",")) { String cc = col.strip(); if (cc.isEmpty() || !cc.matches("[A-Z_]+")) continue; geprueft++; if (!spalten.get(m.group(2)).contains(cc)) fehlt.add(m.group(2) + "." + cc); }
            }
        }
        java.util.regex.Matcher am = Pattern.compile("\\bAS ([A-Z][A-Z0-9_]+)").matcher(t03);
        java.util.Set<String> aliase = new java.util.HashSet<>();
        while (am.find()) aliase.add(am.group(1));   // Spalten einer Sicht, die eine andere Sicht liest (RH_RUNGHOLT_V in RH_TAGESKARTE_V)
        java.util.regex.Matcher vm = Pattern.compile("\\b[a-z]{1,2}\\.([A-Z][A-Z0-9_]+)").matcher(t03);
        while (vm.find()) { geprueft++; String col = vm.group(1); if (!alle.contains(col) && !aliase.contains(col) && !col.startsWith("SDO_")) fehlt.add("03:" + col); }
        check(fehlt.isEmpty() && geprueft > 400, geprueft + " Spaltennamen aus DbSpeicher, 03_sichten und 04_startdaten stehen in 01_tabellen" + (fehlt.isEmpty() ? "" : "; fehlen: " + fehlt));
        // Prüfregeln (CHECK) passen zur App
        boolean ck = true;
        for (String b : com.dan.rungholt.scene.Regie.BEFEHLE) ck &= t01.contains("'" + b.toUpperCase(Locale.ROOT) + "'");
        for (com.dan.weather.Weather.Phase p : com.dan.weather.Weather.Phase.values()) ck &= t01.contains("'" + p.name() + "'");
        for (com.dan.rungholt.scene.Mandraenke.Stelle s : com.dan.rungholt.scene.Mandraenke.Stelle.values()) ck &= t01.contains("'" + s.name() + "'");
        for (String a : new String[]{"KIRCHWARFT", "KETTE", "HOF", "SEEDEICH", "SIELZUG", "HAFENBECKEN", "SEEHUND", "VOGEL", "HW", "NW", "JULIANISCH", "GREGORIANISCH", "PRIEL", "WANDERUNG"})
            ck &= t01.contains("'" + a + "'");
        int stufeMax = 0;
        for (var s : com.dan.rungholt.scene.Mandraenke.Stage.values()) stufeMax = Math.max(stufeMax, s.name().length());
        check(ck && stufeMax <= 10, "CHECK-Regeln kennen alle Befehle der Regiebücher, Wetterlagen (mit Orkan), Bruchstellen, Arten von Warft, Bauwerk, Zählung, Kalender, Fahrt");
        // Startdaten
        java.util.Set<String> kurz = new java.util.HashSet<>();
        for (var q : com.dan.rungholt.data.Startdaten.quellen()) kurz.add(q.kurz());
        boolean belegt = kurz.size() == com.dan.rungholt.data.Startdaten.quellen().size();
        for (var b : com.dan.rungholt.data.Startdaten.befunde()) for (String q : b.quellen()) belegt &= kurz.contains(q);
        for (var f : com.dan.rungholt.data.Startdaten.sturmfluten()) belegt &= f.quelle() != null && kurz.contains(f.quelle());
        for (var w : com.dan.rungholt.data.Startdaten.warften()) belegt &= kurz.contains(w.quelle());
        for (var f : com.dan.rungholt.data.Startdaten.fundstellen()) belegt &= kurz.contains(f.quelle());
        check(belegt, com.dan.rungholt.data.Startdaten.quellen().size() + " Quellen; jedes Kurzzeichen in Befunden, Sturmfluten, Warften und Fundstellen hat eine Quelle");
        var sf = com.dan.rungholt.data.Startdaten.sturmfluten();
        var capella = sf.stream().filter(f -> f.jahr() == 1976 && Integer.valueOf(3).equals(f.tag())).findFirst().orElse(null);
        long mitScheitel = sf.stream().filter(f -> f.scheitelCm() != null).count();
        check(sf.size() == 13 && sf.get(0).jahr() == 1362 && "JULIANISCH".equals(sf.get(0).kalender()) && capella != null && capella.scheitelCm() == 566 && mitScheitel == 3
                        && sf.stream().filter(f -> f.jahr() < 1900).allMatch(f -> f.scheitelCm() == null),
                String.format(Locale.GERMAN, "%d belegte Sturmfluten 1362–2013 (F32): Scheitel Husum nur, wo gemessen (%d; Capella 1976 %.2f m über NN), vor 1900 leer", sf.size(), mitScheitel,
                        capella == null ? 0 : capella.scheitelCm() / 100.0));
        var ws = com.dan.rungholt.data.Startdaten.warften();
        int haeuser = ws.stream().mapToInt(com.dan.rungholt.data.Daten.Warft::haeuser).sum(), brunnen = ws.stream().mapToInt(com.dan.rungholt.data.Daten.Warft::brunnen).sum();
        long kette = com.dan.rungholt.scene.Land1362.warften().stream().filter(x -> x.name().startsWith("Warft ")).count();
        check(ws.size() == Site.WARFTEN && ws.get(0).art().equals("KIRCHWARFT") && ws.stream().filter(w -> w.art().equals("KETTE")).count() == kette
                        && haeuser == com.dan.rungholt.scene.Stadt1362.houses(com.dan.rungholt.scene.Land1362::estimate).size() && brunnen == com.dan.rungholt.scene.Spuren.wells().size(),
                String.format(Locale.GERMAN, "54 Warften (Kirchwarft, %d der Kette, %d Höfe) mit %d Häusern und %d Sodenbrunnen aus der Szene (F30)", kette, 53 - kette, haeuser, brunnen));
        var fs = com.dan.rungholt.data.Startdaten.fundstellen();
        check(fs.size() == 14 && fs.stream().allMatch(f -> com.dan.rungholt.data.Daten.MITGELIEFERT.equals(f.herkunft())), "14 Fundstellen aus scene.Spuren (F31)");
        int pf = 0;
        for (var p : com.dan.rungholt.data.Startdaten.presets()) {
            Object o = Class.forName(p.paket() + "." + p.klasse()).getMethod(p.name()).invoke(null);
            pf += com.dan.store.Felder.schreiben(o, p.felder()).size();
        }
        check(pf == 0 && com.dan.rungholt.data.Startdaten.presets().size() >= 60, com.dan.rungholt.data.Startdaten.presets().size() + " Presets aus "
                + com.dan.rungholt.data.Startdaten.PAKETE.length + " Packages, jedes Feld lässt sich zurückschreiben");
        // F26: die Partialtiden, wie sie in RH_PARTIALTIDE stehen, geben dieselbe Kurve wie der Code
        java.util.Map<String, com.dan.rungholt.data.Daten.Pegelort> ausSql = pegelAusSql(t04);
        double dRand = com.dan.rungholt.data.Pegel.abweichung(ausSql.get("NORDERHEVER"), com.dan.tide.Tide.hever(), 2026),
                dHusum = com.dan.rungholt.data.Pegel.abweichung(ausSql.get("HUSUM"), com.dan.tide.Tide.husum(), 2026),
                d1362 = abweichung1362(ausSql.get("NORDERHEVER"));
        check(ausSql.size() == 3 && dRand < 0.001 && dHusum < 0.001 && d1362 < 0.001, String.format(Locale.GERMAN,
                "F26: die Partialtiden aus 04_startdaten geben die Kurve des Codes: Rand %.3f mm, Husum %.3f mm über 2026, Rand %.3f mm im Januar 1362", dRand * 1000, dHusum * 1000,
                d1362 * 1000));
        var kal = com.dan.rungholt.data.Startdaten.kalender();
        long rand2026 = kal.stream().filter(k -> k.pegel().equals("NORDERHEVER") && k.tag().startsWith("2026-")).count(),
                j1362 = kal.stream().filter(k -> k.tag().startsWith("1362-01")).count();
        var tag = kal.stream().filter(k -> k.pegel().equals("NORDERHEVER") && k.tag().equals("2026-07-21")).toList();
        check(rand2026 > 1400 && rand2026 < 1420 && j1362 > 110 && j1362 < 125 && tag.size() == 4 && tag.stream().anyMatch(k -> k.art().equals("NW") && k.text().startsWith("21.07.2026 13:")),
                String.format(Locale.GERMAN, "F27: Tidenkalender %d Zeilen, am Rand 2026 %d Wenden, im Januar 1362 %d; am 21.7.2026 Niedrigwasser um %s", kal.size(), rand2026, j1362,
                        tag.stream().filter(k -> k.art().equals("NW")).map(k -> k.text().substring(11, 16)).toList()));
        // 04_startdaten ist aktuell
        if (new File(Terrain.defaultDir(), Terrain.INFO).isFile()) {
            long g0 = System.nanoTime();
            String neu = com.dan.rungholt.tools.StartdatenSql.erzeugen(Terrain.defaultDir());
            check(neu.equals(t04), String.format(Locale.GERMAN, "04_startdaten.sql ist, was tools.StartdatenSql jetzt schreibt (%,d kB, %.1f s)", neu.length() / 1024,
                    (System.nanoTime() - g0) / 1e9));
            java.util.Map<String, Integer> z = com.dan.rungholt.data.Verbindung.zahlen(dir);
            check(z.size() == 18 && z.get("Warften") == 54 && z.get("Fundstellen") == 14 && z.get("Partialtiden") == 30 && z.get("Tidenkalender") == kal.size(),
                    "Zeile „-- ZAHLEN:“ mit 18 Arten für den Einrichter: " + z);
        }
        // Wasserlinie: ein Gitter mit Insel im Ring
        String[] rows = {"########", "########", "##....##", "##.##.##", "##.##.##", "##....##", "########", "########"};
        boolean[] n = new boolean[64];
        for (int j = 0; j < 8; j++) for (int i = 0; i < 8; i++) n[j * 8 + i] = rows[j].charAt(i) == '#';
        var wl = com.dan.rungholt.data.Wasserlinie.flaeche(new com.dan.rungholt.data.Wasserlinie.Gitter(n, 8, 8, -256, -256, 64));
        String wkt = wl == null ? "" : wl.wkt();
        var zurueck = com.dan.rungholt.data.Daten.Geom.ausWkt(wkt);
        check(wl != null && wl.ringe().size() == 2 && wl.ringe().get(0).size() == 2 && Math.abs(wl.flaecheM2() - 52 * 64 * 64) < 1 && wkt.startsWith("MULTIPOLYGON (((")
                        && zurueck != null && zurueck.ringe().size() == 2 && Math.abs(zurueck.flaecheM2() - wl.flaecheM2()) < 5,
                String.format(Locale.GERMAN, "Wasserlinie: Ring mit Loch und Insel darin als Mehrfachfläche (%.0f m² = 52 Zellen), WKT hin und zurück", wl == null ? 0 : wl.flaecheM2()));
        // Dateien ohne Datenbank (F34 A)
        File tmp = Files.createTempDirectory("rungholt-daten").toFile();
        com.dan.rungholt.data.DateiSpeicher d = new com.dan.rungholt.data.DateiSpeicher(tmp);
        long ms = java.time.ZonedDateTime.of(1362, 1, 15, 22, 30, 0, 0, SceneClock.ZONE).toInstant().toEpochMilli();
        long tid = d.tide(new com.dan.rungholt.data.Daten.Tide(0, ms, "1362", "NW", -1.2, -1.8, 0.6, 0.41, 120, 1.3, wl), 3L);
        d.zaehlungen(tid, java.util.List.of(new com.dan.rungholt.data.Daten.Zaehlung(ms, "SEEHUND", "Seehundbank; \"Süd\"", 30, 22, 6, 2, -2980, 200),
                new com.dan.rungholt.data.Daten.Zaehlung(ms, "VOGEL", "Austernfischer", 140, null, null, null, 300, 500)), 3L);
        long lid = d.mandraenkeLauf(new com.dan.rungholt.data.Daten.MandraenkeLauf(0, "KIRCHE", ms, null, null, null, null, null, "VORABEND", 2.1, Double.NaN, 86, 0, 0,
                new double[]{-650, 600}, null, "Die Grote Mandränke"), 3L);
        d.mandraenkeLauf(new com.dan.rungholt.data.Daten.MandraenkeLauf(lid, "KIRCHE", ms, ms + 1000, ms + 2000, ms + 3000, null, null, "BRUCH", 4.9, 1.2, 86, 3, 7,
                new double[]{-650, 600}, com.dan.rungholt.data.Daten.Geom.linie(new double[][]{{-675, 598}, {-625, 601}}), "Die Grote Mandränke"), 3L);
        d.glocken(new com.dan.rungholt.data.Daten.Glocken(ms, 75, 24, 280, 1.4, true), 3L);
        long fid = d.fundstelle(new com.dan.rungholt.data.Daten.Fundstelle(0, "Scherbe; \"grau\"", "eigener Fund", 2026, "Prüfung", 120.5, -33.2, "GESTALTET", null, "Test", "EIGEN"), 3L);
        com.dan.rungholt.data.DateiSpeicher d2 = new com.dan.rungholt.data.DateiSpeicher(tmp);
        var ti = d2.tidenAm(java.time.LocalDate.of(1362, 1, 15));
        var za = d2.zaehlungenAm(java.time.LocalDate.of(1362, 1, 15));
        var ml = d2.mandraenkeLaeufe();
        var fu = d2.fundstellen();
        var zelle = d2.zelle(-256 + 2.5 * 64, -256 + 2.5 * 64);   // im Loch: trocken
        var zelle2 = d2.zelle(-256 + 0.5 * 64, -256 + 0.5 * 64);  // im Ring: nass
        var zelle4 = d2.zelle(-256 + 3.5 * 64, -256 + 3.5 * 64);  // die Insel im Loch: nass
        var zelle3 = d2.zelle(130, -30);
        check(ti.size() == 1 && ti.get(0).wasserlinie() != null && ti.get(0).wasserlinie().ringe().size() == 2 && za.size() == 2 && za.get(0).gruppe().equals("Seehundbank; \"Süd\"")
                        && ml.size() == 1 && "BRUCH".equals(ml.get(0).stufe()) && ml.get(0).bresche() != null && ml.get(0).bruchMs() == ms + 3000 && fu.size() == 15
                        && fu.get(14).name().equals("Scherbe; \"grau\"") && fid == 1,
                "Dateien: Tide mit Wasserlinie, Zählungen, Lauf der Mandränke fortgeschrieben (mit Bresche), Glocken, eigene Fundstelle; Text mit „;“ und Anführungszeichen hin und zurück");
        check(zelle.niedrigwasser() == 1 && zelle.nass() == 0 && zelle2.nass() == 1 && zelle4.nass() == 1 && zelle3.funde().size() == 1 && zelle3.funde().get(0).contains("eigen")
                        && zelle3.warft() != null,
                "Zellkarte aus den Dateien: das Loch blieb trocken, Ring und Insel darin nass; die eigene Fundstelle in 100 m; nächste Warft " + zelle3.warft());
        String csv = Files.readString(new File(tmp, "chronik/tiden.csv").toPath());
        check(csv.startsWith("ID;SITZUNG;SZENE_MS;SZENE_TEXT;SZENE_TAG") && csv.contains("15.01.1362 22:30 Ortszeit") && csv.contains("MULTIPOLYGON (((")
                        && Files.readString(new File(tmp, "chronik/fundstellen.csv").toPath()).contains("POINT (") && d2.chronikLeeren(3) == 5 && d2.tidenAm(java.time.LocalDate.of(1362, 1, 15)).isEmpty(),
                "CSV für QGIS (Semikolon, Spalte WKT in Länge und Breite, Szenenzeit F33); „Chronik leeren“ löscht fünf Zeilen der Sitzung, die Fundstelle bleibt");
        // Quellen in den Startdaten zu den Befunden des Werkbuchs
        check(com.dan.rungholt.data.Startdaten.befunde().size() >= 25 && com.dan.rungholt.data.Startdaten.wetterlagen().stream().anyMatch(w -> w.name().startsWith("Mandränke-Nacht")
                        && w.werte()[com.dan.weather.Weather.Var.TEMPERATURE.ordinal()] == 5),
                com.dan.rungholt.data.Startdaten.befunde().size() + " Befunde; Wetterlage „Mandränke-Nacht (mild)“ mit 5 °C");
    }

    /** Die Pegelorte, wie 04_startdaten sie in RH_PEGELORT und RH_PARTIALTIDE schreibt (aus dem Text gelesen). */
    static java.util.Map<String, com.dan.rungholt.data.Daten.Pegelort> pegelAusSql(String t04) {
        java.util.Map<String, Double> mittel = new java.util.HashMap<>();
        java.util.regex.Matcher m = Pattern.compile("MERGE INTO RH_PEGELORT t USING \\(SELECT '(\\w+)' AS KURZ.*?t\\.MITTEL_M = (-?[0-9.]+)", Pattern.DOTALL).matcher(t04);
        while (m.find()) mittel.put(m.group(1), Double.parseDouble(m.group(2)));
        java.util.Map<String, java.util.List<com.dan.rungholt.data.Daten.Partialtide>> pt = new java.util.LinkedHashMap<>();
        m = Pattern.compile("INSERT INTO RH_PARTIALTIDE \\(PEGELORT_ID, NAME, GRAD_JE_H, AMPLITUDE_M, PHASE_GRAD, HERKUNFT\\) SELECT ID, '(\\w+)', ([0-9.]+), ([0-9.]+), ([0-9.]+), 'MITGELIEFERT' FROM RH_PEGELORT WHERE KURZ = '(\\w+)'")
                .matcher(t04);
        while (m.find()) pt.computeIfAbsent(m.group(5), k -> new java.util.ArrayList<>()).add(new com.dan.rungholt.data.Daten.Partialtide(m.group(1),
                Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4))));
        java.util.Map<String, com.dan.rungholt.data.Daten.Pegelort> r = new java.util.LinkedHashMap<>();
        for (var e : pt.entrySet())
            r.put(e.getKey(), new com.dan.rungholt.data.Daten.Pegelort(e.getKey(), e.getKey(), 0, 0, mittel.getOrDefault(e.getKey(), Double.NaN), 0, 1, false, "", null, e.getValue()));
        return r;
    }

    static double abweichung1362(com.dan.rungholt.data.Daten.Pegelort p) {
        if (p == null) return 1;
        com.dan.tide.Tide a = com.dan.rungholt.data.Startdaten.tide(p), b = com.dan.tide.Tide.hever();
        double h0 = com.dan.rungholt.data.Daten.beginn(LocalDate.of(1362, 1, 1)) / 3_600_000.0, d = 0;
        for (double h = h0; h < h0 + 31 * 24; h += 1 / 6.0) d = Math.max(d, Math.abs(a.astronomical(h) - b.astronomical(h)));
        return d;
    }

    /** Phase 16: Zustand, Chronik, Zellkarte und Tageskarte an der laufenden Szene ohne Fenster. */
    static void datenSzene(Renderer r, Wiring w, SceneClock c) throws Exception {
        System.out.println("Chronik, Zustand, Karten (Phase 16)");
        if (r.mandraenke != null) r.stopMandraenke(true);
        w.era.target = w.era.blend = 0;
        r.selectEra();
        w.regie = false;
        w.resumeRegie();
        w.surgeScript = Double.NaN;
        w.level.tide.surge = 0;
        w.level.hand = Double.NaN;
        c.lapse = 60;
        c.set(LocalDate.of(2026, 7, 21), 9);
        r.stepTideNow();
        r.ocean.windSpeed = 7.25;
        var z = com.dan.rungholt.data.Zustand.erfassen(r);
        long ms0 = (long) Math.floor(c.epochMillis());
        c.set(LocalDate.of(1362, 1, 15), 3);
        c.lapse = 1;
        r.ocean.windSpeed = 1;
        var fehlerZ = com.dan.rungholt.data.Zustand.anwenden(r, z);
        if (!fehlerZ.isEmpty()) System.out.println("    nicht hergestellt: " + fehlerZ);
        var sp = com.dan.rungholt.data.Zustand.spalten(z);
        check(fehlerZ.isEmpty() && (long) c.epochMillis() == ms0 && c.lapse == 60 && r.ocean.windSpeed == 7.25 && z.size() > 200 && sp.get(com.dan.rungholt.data.Speicher.SZENE_MS).equals(Long.toString(ms0))
                        && "0".equals(sp.get(com.dan.rungholt.data.Speicher.AERA)),
                String.format(Locale.GERMAN, "Zustand mit %d Werten hin und zurück: Uhr (ms), Zeitraffer, Ära, Felder der Packages; Spalten für RH_SITZUNG", z.size()));
        // die Chronik in Dateien: zwei Tiden im Zeitraffer
        File tmp = Files.createTempDirectory("rungholt-chronik").toFile();
        com.dan.rungholt.data.DateiSpeicher ds = new com.dan.rungholt.data.DateiSpeicher(tmp);
        com.dan.rungholt.data.Datenseite.setzen(ds);
        com.dan.rungholt.data.Chronik ch = new com.dan.rungholt.data.Chronik(r);
        ch.jetzt();
        double h0 = c.epochMillis() / 3_600_000.0;
        for (double h = h0; h < h0 + 13; h += 0.25) { c.setEpochMillis(h * 3_600_000); r.stepTideNow(); ch.jetzt(); }
        ch.warten();
        var ti = ds.tidenAm(LocalDate.of(2026, 7, 21));
        var nw = ti.stream().filter(t -> t.art().equals("NW") && t.wasserlinie() != null).findFirst().orElse(null);
        var za = ds.zaehlungenAm(LocalDate.of(2026, 7, 21));
        String wkt = nw == null ? "" : nw.wasserlinie().wkt();
        try { Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "rungholt_wasserlinie.wkt"), wkt); } catch (Exception e) { /* nur zum Nachsehen */ }
        check(ti.size() >= 2 && nw != null && com.dan.rungholt.data.Daten.text(nw.ms()).startsWith("21.07.2026 13:") && nw.wasserlinie().ringe().size() > 0
                        && wkt.length() < 400_000 && nw.trockenAnteil() > 0.3,
                String.format(Locale.GERMAN, "Chronik am 21.7.2026: %d Tiden, Niedrigwasser %s mit Wasserlinie (%d Flächen, %d Punkte, %,d Zeichen WKT), %.0f %% trocken",
                        ti.size(), nw == null ? "—" : com.dan.rungholt.data.Daten.text(nw.ms()), nw == null ? 0 : nw.wasserlinie().ringe().size(), nw == null ? 0 : nw.wasserlinie().punkte(),
                        wkt.length(), nw == null ? 0 : nw.trockenAnteil() * 100));
        check(za.stream().anyMatch(x -> x.art().equals("VOGEL")) || za.stream().anyMatch(x -> x.art().equals("SEEHUND")) || (!r.showSeals && !r.showBirds),
                "Zählung bei Niedrigwasser: " + za.size() + " Zeilen (" + String.join(", ", za.stream().map(x -> x.gruppe() + " " + x.anzahl()).toList()) + ")");
        // Glocken von Hand
        r.glocken.force();
        ch.jetzt();
        r.glocken.ringing = false;
        r.glocken.forced = false;
        c.setEpochMillis(c.epochMillis() + 90_000);
        ch.jetzt();
        var zelle = ds.zelle(0, 0);
        check(ch.glocken == 1 && zelle.niedrigwasser() >= 1 && "Kirchwarft".equals(zelle.warft()) && zelle.funde().size() >= 1,
                String.format(Locale.GERMAN, "Glocken von Hand in der Chronik; Zellkarte an der Kirche: %d Niedrigwasser, %d-mal nass, %d Fundstellen in 100 m", zelle.niedrigwasser(), zelle.nass(),
                        zelle.funde().size()));
        // die Zellkarte im Bild
        r.warftCard = null;
        r.camera.place(2600, r.groundHeight(2600, 900) + 60, 900 + 120, 0, Math.toDegrees(Math.atan2(60, 120)));
        r.clickAt(800, 450, 1600, 900);
        String[] karte = r.warftCard;
        check(karte != null && r.lastWarft == null && karte[0].startsWith("Watt bei") && String.join("\n", karte).contains("#1362") && String.join("\n", karte).contains("Niedrigwasser"),
                "Klick ins Watt: Zellkarte „" + (karte == null ? "—" : karte[0]) + "“ mit Grund heute und 1362 und den Wenden am Rand");
        r.cardAppend(karte, java.util.List.of("#Datenbank", "x"));
        check(r.warftCard != karte && r.warftCard.length == karte.length + 2, "Die Datenbank hängt an die offene Karte an");
        r.warftCard = null;
        // Tageskarte
        var tk = com.dan.rungholt.data.Tageskarte.zeichnen(ds, r.terrain, LocalDate.of(2026, 7, 21), tmp);
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(tk.datei());
        check(img != null && img.getWidth() == com.dan.rungholt.data.Tageskarte.W && tk.tiden() == ti.size() && tk.wasserlinie() && tk.fundstellen() == 14,
                "Tageskarte " + tk.datei().getName() + ": " + tk.tiden() + " Tiden mit Wasserlinie, " + tk.zaehlungen() + " Zählungen, " + tk.fundstellen() + " Fundstellen");
        // die Mandränke in der Chronik und die milde Nacht
        r.manualMandraenke = true;
        boolean ok = r.startMandraenke(com.dan.rungholt.scene.Mandraenke.Stelle.KIRCHE);
        r.selectEra();
        var st = w.season.state();
        double temp = w.weather.handValues()[com.dan.weather.Weather.Var.TEMPERATURE.ordinal()];
        ch.jetzt();
        ch.warten();
        var ml = ds.mandraenkeLaeufe();
        check(ok && st != null && st.snowDepth() < 0.005 && temp == 5 && ml.size() == 1 && "KIRCHE".equals(ml.get(0).stelle()) && ml.get(0).beginnMs() < 0,
                String.format(Locale.GERMAN, "Mandränke: die Nacht ist mild (%.0f °C, Schnee %.0f cm); ihr Lauf steht in der Chronik (Stelle %s, Beginn %s)", temp, st == null ? -1 : st.snowDepth() * 100,
                        ml.isEmpty() ? "—" : ml.get(0).stelle(), ml.isEmpty() ? "—" : com.dan.rungholt.data.Daten.text(ml.get(0).beginnMs())));
        r.stopMandraenke(true);
        ch.jetzt();
        ch.beenden();
        com.dan.rungholt.data.Datenseite.setzen(new com.dan.rungholt.data.DateiSpeicher(com.dan.rungholt.data.DateiSpeicher.ordner()));
        w.era.target = w.era.blend = 0;
        r.selectEra();
        c.lapse = 1;
    }

    /** Phase 15: die Regiebücher als Text, ohne Bild durchgespielt; die Prielfahrt; das Kino. */
    static void regie(Renderer r, Wiring w, SceneClock c) {
        System.out.println("Regiebücher, Prielfahrt, Kino (Phase 15)");
        java.util.List<com.dan.rungholt.scene.Regie.Book> all;
        try { all = com.dan.rungholt.scene.Regie.all(); } catch (RuntimeException e) { check(false, "die drei Regiebücher lesen: " + e.getMessage()); return; }
        int views = 0, badViews = 0;
        StringBuilder bad = new StringBuilder();
        for (com.dan.rungholt.scene.Regie.Book b : all)
            for (com.dan.rungholt.scene.Regie.Zeile z : b.zeilen())
                if (z.befehl().equals("ansicht")) { views++; if (!r.placeNamed(z.argumente())) { badViews++; bad.append(' ').append(z.argumente()); } }
        boolean blatt = all.stream().allMatch(b -> b.zeilen().stream().anyMatch(z -> z.befehl().equals("blatt")));
        check(all.size() == 3 && blatt && badViews == 0 && all.stream().mapToInt(com.dan.rungholt.scene.Regie.Book::fotos).sum() >= 25, String.format(Locale.GERMAN,
                "drei Regiebücher aus scene/regie/*.txt (%s): %d Fotos, jedes mit Blatt, %d Ansichten, alle bekannt%s", String.join(", ", all.stream().map(com.dan.rungholt.scene.Regie.Book::name).toList()),
                all.stream().mapToInt(com.dan.rungholt.scene.Regie.Book::fotos).sum(), views, bad.length() == 0 ? "" : " – unbekannt:" + bad));
        boolean caught = false;
        try { com.dan.rungholt.scene.Regie.parse("Regiebuch: X\n0:00 zeit 1.1.2026 10:00\n0:05 warte ebbe\n"); } catch (IllegalArgumentException e) { caught = e.getMessage().startsWith("Zeile 3"); }
        check(caught, "ein falsches warte nennt die Zeile");
        // die Prielfahrt: der Weg bleibt in der Rinne
        if (r.mandraenke != null) r.stopMandraenke(true);
        w.era.target = w.era.blend = 0;
        r.selectEra();
        w.regie = false;
        w.resumeRegie();
        w.surgeScript = Double.NaN;
        w.level.tide.surge = 0;
        c.set(LocalDate.of(2026, 7, 21), 14.5);
        r.stepTideNow();
        com.dan.rungholt.camera.Fahrt pf = r.prielFahrt();
        double minDepth = 99;
        if (pf != null) for (double u = 0; u <= 1; u += 0.01) { double[] p = pf.pose(u); minDepth = Math.min(minDepth, w.level.depthAt(p[0], p[2])); }
        check(pf != null && pf.length > 1200 && pf.length < 3000 && minDepth > 0.3 && pf.maxTurnPerMeter() < 6, pf == null ? "Prielfahrt: kein Weg" : String.format(Locale.GERMAN,
                "Prielfahrt: %.1f km in %.0f s den Priel hinauf, bei halber Flut überall mindestens %.1f m Wasser unter dem Boot", pf.length / 1000, pf.duration, minDepth));
        // Eine Tide ohne Bild: Hochwasser bis Hochwasser, die Fahrt als Störer
        r.ohneBlatt = true;
        com.dan.rungholt.scene.Regie.Book tide = com.dan.rungholt.scene.Regie.tide();
        r.regieStart(tide);
        boolean boat = false;
        double lowAtBank = Double.NaN;
        int steps = 0;
        while (r.regie.running() && steps < 4000) {
            r.step(0.5);
            r.bildlos();
            if (steps++ % 2 == 0) r.stepTideNow();
            boat |= r.boatOn();
            if (Double.isNaN(lowAtBank) && r.regie.describe().contains("Seehunde liegen")) lowAtBank = r.level();
        }
        java.time.ZonedDateTime end = c.local();
        check(!r.regie.running() && end.getHour() == 19 && end.getMinute() >= 19 && end.getMinute() <= 30 && boat && lowAtBank < -0.8 && r.regie.waitedTotal() < 60, String.format(Locale.GERMAN,
                "„Eine Tide“ ohne Bild: endet %02d:%02d beim Abendhochwasser, die Seehundbank bei NHN %+.1f m, das Boot fährt, %.0f s gewartet", end.getHour(), end.getMinute(), lowAtBank, r.regie.waitedTotal()));
        // Die Glocken ohne Bild: sie läuten zur Kirche im Sturm, am Mittag die Grabung, dann 1362 und zurück
        com.dan.rungholt.scene.Regie.Book gl = com.dan.rungholt.scene.Regie.glocken();
        r.regieStart(gl);
        boolean rang = false, dug = false, then1362 = false;
        steps = 0;
        while (r.regie.running() && steps < 4000) {
            r.step(0.5);
            r.bildlos();
            if (steps++ % 2 == 0) r.stepTideNow();
            String d = r.regie.describe();
            rang |= d.contains("läuten die Glocken") && r.glocken.ringing && r.sturmnacht();
            dug |= d.contains("Archäologen") && r.grabung.present();
            then1362 |= w.era.blend() > 0.99;
        }
        check(!r.regie.running() && rang && dug && then1362 && w.era.blend() < 0.01, String.format(Locale.GERMAN,
                "„Die Glocken“ ohne Bild: die Glocken läuten in der Sturmnacht, am Mittag gräbt die Grabung, nach 1362 und zurück (%.0f s gewartet)", r.regie.waitedTotal()));
        // Die Grote Mandränke bis zum Überlauf (der Bruch braucht den Faden der Physik)
        r.regieStart(com.dan.rungholt.scene.Regie.mandraenke());
        steps = 0;
        while (r.regie.running() && steps < 2000 && !(r.mandraenke != null && r.mandraenke.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF.ordinal())) {
            r.step(0.5);
            r.bildlos();
            if (steps++ % 2 == 0) r.stepTideNow();
        }
        boolean over = r.mandraenke != null && r.mandraenke.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF.ordinal();
        check(over && !c.date().isBefore(com.dan.rungholt.scene.Mandraenke.DAY) && !c.date().isAfter(com.dan.rungholt.scene.Site.MANDRAENKE) && w.era.blend() > 0.99, String.format(Locale.GERMAN,
                "„Die Grote Mandränke“ ohne Bild: %s ab 22 Uhr, um %s laufen die Wellen über die Krone", com.dan.rungholt.scene.Mandraenke.DAY, c.timeLabel()));
        r.regieStop();
        r.stopMandraenke(true);
        // das Kino: Vor- und Abspann, Untertitel aus den Stichworten
        r.kinoStart(com.dan.rungholt.scene.Regie.glocken());
        java.util.List<com.dan.kino.Kino.Lage> seen = new java.util.ArrayList<>();
        String ut = null;
        steps = 0;
        String abspann = null;
        while (steps++ < 400 && (r.kino.laeuft() || seen.isEmpty())) {
            r.step(0.1);
            r.bildlos();
            com.dan.kino.Kino.Lage l = r.kino.lage();
            if (seen.isEmpty() || seen.get(seen.size() - 1) != l) seen.add(l);
            com.dan.kino.Kino.Tafel t = r.kino.tafel();
            if (t != null && t.art() == com.dan.kino.Kino.Art.UNTERTITEL && ut == null) ut = t.zeilen().get(0);
            if (t != null && t.art() == com.dan.kino.Kino.Art.ABSPANN && abspann == null) abspann = String.join(" / ", t.zeilen());
            if (steps == 120) r.regie.stop();   // nach 12 s ist das Regiebuch aus: Abblende, Abspann, Balken heraus
        }
        check(seen.equals(java.util.List.of(com.dan.kino.Kino.Lage.VORSPANN, com.dan.kino.Kino.Lage.FILM, com.dan.kino.Kino.Lage.ABSPANN, com.dan.kino.Kino.Lage.AUSKLANG, com.dan.kino.Kino.Lage.AUS))
                        && ut != null && ut.startsWith("30. November") && abspann != null && abspann.startsWith("Die Glocken") && abspann.contains("BAW"),
                "Kino: " + String.join(" → ", seen.stream().map(Enum::name).toList()) + "; erster Untertitel „" + ut + "“; Abspann „" + (abspann == null ? "—" : abspann.substring(0, Math.min(60, abspann.length()))) + " …“");
        r.kinoVerlassen();
        r.ohneBlatt = false;
        w.regie = false;
        w.resumeRegie();
        w.wind.speed = Beaufort.mid(3);
        w.era.target = w.era.blend = 0;
        r.selectEra();
        c.lapse = 1;
    }

    static void ohr(Renderer r, double x, double z) {
        double g = r.groundHeight(x, z), wl = r.wiring.level.depthAt(x, z) > 0 ? r.wiring.level.surfaceAt(x, z) : g;
        r.camera.place(x, Math.max(g, wl) + 1.7, z, 270, 0);
        r.camera.groundLevel = g;
        r.camera.waterLevel = wl;
    }

    /** Phase 17: Spring- oder Nippfaktor am Rand zur Stunde h (1 im Mittel). */
    static double springfaktor(double h) { return com.dan.tide.Tide.hever().springFactor(h); }

    static void mandraenke(Renderer r, Wiring w, SceneClock c) {
        System.out.println("Die Grote Mandränke (Phase 12)");
        double q0 = com.dan.rungholt.scene.Mandraenke.weir(1, 0, 0), q1 = com.dan.rungholt.scene.Mandraenke.weir(1, 0.8, 0), q2 = com.dan.rungholt.scene.Mandraenke.weir(0, 1, 0);
        check(Math.abs(q0 - 1.7) < 1e-9 && q1 > 0 && q1 < q0 * 0.8 && Math.abs(q2 + q0) < 1e-9, String.format(Locale.GERMAN,
                "Wehr: 1 m Überfall 1,70 m²/s, mit Rückstau 0,8 m %.2f m²/s, rückwärts −1,70 m²/s", q1));
        r.manualMandraenke = true;
        boolean ok = r.startMandraenke(com.dan.rungholt.scene.Mandraenke.Stelle.KIRCHE);
        r.selectEra();
        com.dan.rungholt.scene.Mandraenke m = r.mandraenke;
        if (!ok || m == null) { check(false, "die Mandränke startet"); return; }
        com.dan.rungholt.scene.Terrain l = r.land1362;
        double crestBefore = l.height(m.bruch.x, m.bruch.z);
        check(c.date().equals(com.dan.rungholt.scene.Mandraenke.DAY) && Math.abs(m.surge(m.hwH) - 3.6) < 1e-9 && m.hwH > m.startH + 3 && m.hwH < m.startH + 16 && springfaktor(m.hwH) > 1.1,
                String.format(Locale.GERMAN, "23.1.1362 (jul. 15.1.) ab 22 Uhr; Windstau 3,6 m zum Hochwasser der Nacht (%s, Springfaktor %.2f)", hm((m.hwH - m.startH) + com.dan.rungholt.scene.Mandraenke.START_HOUR),
                        springfaktor(m.hwH)));
        // bis zum Bruch in Minuten
        boolean sawStorm = false, sawOver = false;
        double seaAtOver = Double.NaN;
        for (int k = 0; k < 12 * 10 * 10 && m.stage.ordinal() < com.dan.rungholt.scene.Mandraenke.Stage.BRUCH.ordinal(); k++) {
            int dt = m.stage != com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF ? 300 : m.overtopH < com.dan.rungholt.scene.Mandraenke.OVERTOP_HOURS - 0.02 ? 30 : 1;   // kurz vor dem Bruch in Sekunden
            c.setEpochMillis(c.epochMillis() + dt * 1000L);
            r.stepTideNow();
            r.stepMandraenke(dt, true);
            sawStorm |= m.stage == com.dan.rungholt.scene.Mandraenke.Stage.STURM;
            if (m.stage == com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF && !sawOver) { sawOver = true; seaAtOver = r.level(); }
        }
        check(sawStorm && sawOver && m.stage == com.dan.rungholt.scene.Mandraenke.Stage.BRUCH && seaAtOver >= com.dan.rungholt.scene.Land1362.CREST - 1.01 && r.town.people.always,
                String.format(Locale.GERMAN, "Sturm, dann Wellen über der Krone ab NHN %+.2f m, der Deich bricht um %s nach %.1f h Überlauf", seaAtOver, hm(c.hour()), m.overtopH));
        // Phase 17: die Gischt über der Krone, Kamera auf dem Deich neben der Bruchstelle
        r.camera.place(m.bruch.x + 120, com.dan.rungholt.scene.Land1362.CREST + 1.7, m.bruch.z, 180, 0);
        long br0 = r.dikeBreakers;
        int maxParcels = 0;
        for (int k = 0; k < 20; k++) { r.stepSpray(0.25); maxParcels = Math.max(maxParcels, r.spray.parcels().size()); }
        r.camera.place(0, 4000, -3000, 0, 0);   // weit weg vom Deich: keine Brecher
        long br1 = r.dikeBreakers;
        r.stepSpray(1);
        check(br1 - br0 >= 20 && maxParcels > 10 && r.dikeBreakers == br1, String.format(Locale.GERMAN,
                "Gischt über der Krone (Phase 17): %d Brecher in 5 s an der Seeseite, bis zu %d Bündel; 1,5 km vom Deich keine", br1 - br0, maxParcels));
        check(m.bruch.solid != null && m.bruch.solid.cells() > 100 && m.bruch.solid.anchors() > 20 && l.height(m.bruch.x, m.bruch.z) < com.dan.rungholt.scene.Deichbruch.BOTTOM + 0.01 && crestBefore > com.dan.rungholt.scene.Land1362.CREST - 0.6,
                String.format(Locale.GERMAN, "der Abschnitt wird ein Körper aus Klei (%d Zellen, %d Anker), das Gelände darunter ausgehoben (Krone %+.1f → %+.1f m)",
                        m.bruch.solid.cells(), m.bruch.solid.anchors(), crestBefore, l.height(m.bruch.x, m.bruch.z)));
        // der Bruch in Echtzeit, Sekunde für Sekunde
        double tb = c.epochMillis();
        int secs = 0;
        for (; secs < 300 && m.stage == com.dan.rungholt.scene.Mandraenke.Stage.BRUCH; secs++) {
            c.setEpochMillis(tb + (secs + 1) * 1000.0);
            r.stepMandraenke(1, true);
        }
        boolean flutNow = m.stage == com.dan.rungholt.scene.Mandraenke.Stage.FLUT;
        // die Welle durch die Bresche
        com.dan.wave.Wave wv = r.flutWelle;
        for (int k = 0; k < 60; k++) { c.setEpochMillis(c.epochMillis() + 2000); r.stepMandraenke(2, true); }
        double xi = m.bruch.x - m.bruch.s[0] * 150, zi = m.bruch.z - m.bruch.s[1] * 150;
        double inLand = wv == null ? Double.NaN : wv.levelAt(xi, zi), held = wv == null ? Double.NaN : wv.levelAt(m.bruch.x + m.bruch.s[0] * 250 + m.bruch.t[0] * 200, m.bruch.z + m.bruch.s[1] * 250 + m.bruch.t[1] * 200);
        check(wv != null && r.welleOn && !Double.isNaN(inLand) && Math.abs(held + r.welleRestForTest() - r.level()) < 0.1, String.format(Locale.GERMAN,
                "Dammbruch mit com.dan.wave: nach 2 min steht 150 m hinter der Bresche Wasser (NHN %+.2f m), die See davor bleibt auf dem Pegel (%+.2f m)", inLand + r.welleRestForTest(), held + r.welleRestForTest()));
        double sMean = 0;
        int pn = 0;
        for (com.dan.fracture.Fracture.Pose p : m.bruch.world.poses()) { sMean += (p.position()[0] - m.bruch.x) * m.bruch.s[0] + (p.position()[2] - m.bruch.z) * m.bruch.s[1]; pn++; }
        sMean /= Math.max(1, pn);
        double reachSea = com.dan.rungholt.scene.Land1362.CREST_HALF + (com.dan.rungholt.scene.Land1362.CREST - com.dan.rungholt.scene.Deichbruch.BOTTOM) * com.dan.rungholt.scene.Land1362.SEA_SLOPE;
        check(flutNow && secs < 300 && pn > 20 && sMean < reachSea && sMean > -60, String.format(Locale.GERMAN,
                "der Abschnitt bricht in %d s: %d Stücke, 2 min später liegen sie als Brocken in der Bresche (im Mittel %+.0f m von der Krone, zur See +)", secs, pn, sMean));
        r.welleOn = false;
        // die Nacht in 10-min-Schritten
        double peak = -9, peakAt = 0;
        int maxFloat = 0;
        while (c.epochMillis() / 3_600_000.0 < m.hwH + 12) {
            c.setEpochMillis(c.epochMillis() + 600_000);
            r.stepTideNow();
            r.stepMandraenke(600, true);
            if (!Double.isNaN(m.polder) && m.polder > peak) { peak = m.polder; peakAt = c.hour(); }
            maxFloat = Math.max(maxFloat, m.afloat());
        }
        check(peak > 3.5 && peak < 5.6 && maxFloat > 10 && m.gone() > 30,   // Phase 17: auf der Springtide bis gut 5,2 m (vorher Nipp, unter 5,0)
                String.format(Locale.GERMAN,
                "die Marsch läuft in Stunden voll: höchstens NHN %+.2f m um %s; bis zu %d Häuser treiben zugleich, %d zerfallen", peak, hm(peakAt), maxFloat, m.gone()));
        double foot = r.waterSurface(0, 75), top = r.waterSurface(0, 0);
        check(m.stage == com.dan.rungholt.scene.Mandraenke.Stage.MORGEN && !Double.isNaN(foot) && Double.isNaN(top) && r.town.church != null, String.format(Locale.GERMAN,
                "am Morgen (%s) steht die Kirche im Wasser: am Fuß der Kirchwarft NHN %+.2f m, die Kuppe trocken, die Marsch NHN %+.2f m", hm(c.hour()), foot, m.polder));
        r.stopMandraenke(true);
        check(r.mandraenke == null && Math.abs(r.land1362.height(m.bruch.x, m.bruch.z) - crestBefore) < 0.01 && Double.isNaN(w.surgeScript),
                "Beenden: Deich und Gelände wie vorher, Windstau wieder aus dem Wind, zurück im Sommer 1361");
        r.manualMandraenke = false;
        w.era.target = 0; w.era.blend = 0;
        r.selectEra();
    }

    /** Phase 11: die Stadt von 1362 (Häuser, Kirche, Kogge, Vieh, Leute, Herdrauch, Ansichten, Fahrt). */
    static void stadt(Renderer r, Wiring w, SceneClock c) {
        System.out.println("Die Stadt 1362 (Phase 11)");
        Renderer.Town t = r.town;
        com.dan.rungholt.scene.Terrain l = r.land1362;
        if (t == null || l == null) { check(false, "die Stadt wird mit dem Gelände von 1362 gebaut"); return; }
        int onWarft = 0, floating = 0, overlap = 0;
        for (com.dan.timber.House h : t.houses) {
            if (l.artAt(h.x(), h.z()) == com.dan.rungholt.scene.Terrain.WARFT) onWarft++;
            if (Math.abs(h.y() - l.height(h.x(), h.z())) > 0.6) floating++;
            for (com.dan.timber.House o : t.houses) if (o != h && Math.hypot(o.x() - h.x(), o.z() - h.z()) < (o.radius() + h.radius()) * 0.55) overlap++;
        }
        check(t.houses.size() >= 90 && onWarft == t.houses.size() && floating == 0 && overlap == 0, String.format(Locale.GERMAN,
                "%d Häuser aus com.dan.timber, alle auf Warften, keins schwebt oder steht in einem anderen", t.houses.size()));
        com.dan.timber.Church.Spec cs = com.dan.timber.Church.rungholt();
        check(Math.abs(t.church.y() - 3.8) < 0.3 && t.churchMesh.triangles() > 150 && Math.abs(cs.length() - 41) < 1.5 && t.church.headingDeg() > 80 && t.church.headingDeg() < 100,
                String.format(Locale.GERMAN, "Kirche aus Backstein auf der Kirchwarft (NHN %+.1f m), %.0f × %.0f m, Turm %.0f m, Apsis nach Osten", t.church.y(), cs.length(), cs.naveWidth(), cs.height()));
        String tf = readOr("src/com/dan/timber/gl/timber.frag");
        check(tf.contains("CHURCH = " + com.dan.timber.Material.CHURCH.ordinal()) && tf.contains("SHINGLE = " + com.dan.timber.Material.SHINGLE.ordinal()),
                "timber.frag kennt die Werkstoffe der Kirche (Klosterformat mit Rundbogenfenstern, Schindeln) mit denselben Nummern wie Material");
        int cows = 0, sheepOk = 0, cowsOk = 0;
        for (com.dan.rungholt.scene.Stadt1362.Beast b : t.beasts) {
            if (b.kind() == 0) { cows++; if (l.artAt(b.x(), b.z()) == com.dan.rungholt.scene.Terrain.MARSCH) cowsOk++; }
            else if (l.artAt(b.x(), b.z()) == com.dan.rungholt.scene.Terrain.DEICH1362) sheepOk++;
        }
        check(cows > 80 && cowsOk == cows && sheepOk == t.beasts.size() - cows && sheepOk > 30, String.format(Locale.GERMAN, "%d Rinder auf den Fennen, %d Schafe am Seedeich", cows, sheepOk));
        double minAbove = 1e9;
        for (com.dan.smoke.Source sr : t.hearths) {
            com.dan.timber.House near = null;
            for (com.dan.timber.House h : t.houses) if (near == null || Math.hypot(h.x() - sr.x(), h.z() - sr.z()) < Math.hypot(near.x() - sr.x(), near.z() - sr.z())) near = h;
            minAbove = Math.min(minAbove, sr.y() - near.y());
        }
        check(t.hearths.size() > 30 && minAbove > 3, String.format(Locale.GERMAN, "%d Herdfeuer: Rauch aus den Rauchlöchern (mindestens %.1f m über dem Boden)", t.hearths.size(), minAbove));
        // ein Sommertag 1361: die Kogge läuft mit der Flut ein und fällt bei Ebbe trocken
        w.era.target = 1; w.era.blend = 1;
        c.set(java.time.LocalDate.of(1361, 7, 21), 3.5);
        r.selectEra();
        r.stepTideNow();
        com.dan.rungholt.scene.Sielhafen sh = t.sielhafen;
        sh.place(r.harbourSea, false, w.level.hours());
        double arrive = Double.NaN, levelIn = Double.NaN, lowTide = Double.NaN;
        boolean stuck = false, rose = true;
        double t0 = c.epochMillis();
        for (int k = 1; k <= 12 * 11; k++) {
            c.setEpochMillis(t0 + k * 300_000L);
            r.stepTideNow();
            r.stepAnimalsScene(300);
            if (sh.state == com.dan.rungholt.scene.Sielhafen.State.EINLAUFEN && sh.cog.grounded()) stuck = true;
            if (Double.isNaN(arrive) && sh.state == com.dan.rungholt.scene.Sielhafen.State.LIEGT) { arrive = c.hour(); levelIn = r.level(); rose = w.level.rising() || r.level() > 0.9; }
            if (Double.isNaN(lowTide) && c.hour() > 13.2) lowTide = r.level();
        }
        double[] bt = sh.berth;
        check(!Double.isNaN(arrive) && !stuck && rose && sh.arrivals == 1, String.format(Locale.GERMAN, "21.7.1361: die Kogge läuft mit der Flut ein, an der Kaje um %s bei NHN %+.2f m, nirgends auf Grund", Double.isNaN(arrive) ? "—" : hm(arrive), levelIn));
        check(sh.cog.grounded() && Math.abs(Math.toDegrees(sh.cog.roll)) < 1 && Math.hypot(sh.cog.x - bt[0], sh.cog.z - bt[1]) < 6, String.format(Locale.GERMAN,
                "bei Niedrigwasser (NHN %+.2f m) sitzt sie aufrecht im Schlick des Hafenbeckens (%.1f° Schlagseite)", lowTide, Math.toDegrees(sh.cog.roll)));
        // die Leute
        t.people.step(1, r::groundHeight, 1);
        boolean onGround = true, period = true;
        for (com.dan.figure.Person p : t.people.people) { onGround &= Math.abs(p.y - r.groundHeight(p.x, p.z)) < 0.05; period &= !p.outfit.pack; }
        t.people.step(1, r::groundHeight, 0);
        check(t.people.people.size() == 64 && onGround && period && !t.people.visible, "64 Leute in Kleidung von 1362 auf dem Grund der Warften und der Kette; nachts im Haus");
        // Ansicht 8 und Fahrt
        w.era.target = 0; w.era.blend = 0;
        boolean v8 = r.placeView(com.dan.rungholt.camera.Ansichten.byKey(8));
        check(v8 && w.era.blend == 1 && Math.abs(r.camera.eye[1] - (r.land1362.height(-17, -1) + 19)) < 0.5, String.format(Locale.GERMAN,
                "Ansicht 8 schaltet nach 1362 und steht in den Schallöffnungen des Turms (Auge NHN %+.1f m)", r.camera.eye[1]));
        r.startChainFlight();
        check(r.camera.fahrt != null && r.camera.fahrt.length > 2000 && r.camera.fahrt.name.contains("Warftenkette"), String.format(Locale.GERMAN, "Fahrt „Über die Warftenkette“ %.1f km", r.camera.fahrt == null ? 0 : r.camera.fahrt.length / 1000));
        r.camera.stopFahrt();
        check(r.townLine().startsWith("Rungholt 1362: ") && r.townLine().contains("Kogge"), r.townLine());
        w.era.target = 0; w.era.blend = 0;
        r.selectEra();
    }

    /** Phase 10: das Land von 1362 (Marsch, Warften, Seedeich, Siel) und die Tide davor. */
    static void land1362(Renderer r, Wiring w, SceneClock c) {
        System.out.println("Das Land 1362 (Phase 10)");
        long t0 = System.nanoTime();
        if (r.land1362 == null) r.buildLand1362();
        com.dan.rungholt.scene.Terrain l = r.land1362;
        double sec = (System.nanoTime() - t0) / 1e9;
        java.util.List<com.dan.rungholt.scene.Land1362.Warft> wf = com.dan.rungholt.scene.Land1362.warften();
        com.dan.rungholt.scene.Land1362.Warft kirche = wf.get(0);
        check(l != null && wf.size() == 54 && kirche.name().startsWith("Kirchwarft") && Math.abs(l.height(0, 0) - kirche.top()) < 0.3, String.format(Locale.GERMAN,
                "Gelände 1362 in %.1f s: %d Warften, die Kirchwarft NHN %+.1f m (Raster %+.1f m)", sec, wf.size(), kirche.top(), l == null ? 0 : l.height(0, 0)));
        double chain = com.dan.rungholt.scene.Land1362.chainLength();
        int onChain = 0;
        for (com.dan.rungholt.scene.Land1362.Warft x : wf) if (com.dan.rungholt.scene.Land1362.chainDist(x.x(), x.z()) < 40) onChain++;
        check(chain > 2000 && chain < 2500 && onChain >= 20, String.format(Locale.GERMAN, "Warftenkette %.1f km mit %d Warften, die übrigen Hofwarften in der Marsch", chain / 1000, onChain));
        double crest = -9;
        for (int k = 480; k <= 580; k++) crest = Math.max(crest, l.height(60, k));
        java.util.List<Double> hm = new java.util.ArrayList<>();
        for (int i = -40; i <= 40; i++) for (int k = -30; k <= 10; k++) {
            double x = i * 50, z = k * 50;
            if (l.artAt(x, z) == com.dan.rungholt.scene.Terrain.MARSCH) hm.add(l.height(x, z));
        }
        java.util.Collections.sort(hm);
        int nM = hm.size();
        double lo = nM == 0 ? 9 : hm.get(nM / 20), hi = nM == 0 ? -9 : hm.get(nM - 1 - nM / 20);
        check(Math.abs(crest - com.dan.rungholt.scene.Land1362.CREST) < 0.3 && nM > 500 && lo > 0.2 && hi < 1.4, String.format(Locale.GERMAN,
                "Seedeich südlich der Kirche Krone NHN %+.1f m; Marsch um Rungholt NHN %+.1f bis %+.1f m (5. bis 95. Perzentil von %d Proben; Gräben und Warftfüße außen vor)", crest, lo, hi, nM));
        check(l.artAt(-3600, 1300) == com.dan.rungholt.scene.Terrain.DEICH1362 || l.artAt(-3600, 1300) == com.dan.rungholt.scene.Terrain.SIELZUG,
                "Siel im Deich bei x = −3600 m, Sielzüge aus der Marsch dorthin");
        // die Überblendung im Grund
        w.era.target = 1; w.era.blend = 0.5;
        double half = r.groundHeight(0, 0);
        w.era.blend = 1;
        double full = r.groundHeight(0, 0);
        check(Math.abs(full - l.height(0, 0)) < 0.05 && half < full - 0.5, String.format(Locale.GERMAN, "Grund an der Kirche: heute %+.1f m, halb übergeblendet %+.1f m, 1362 %+.1f m", r.terrain.height(0, 0), half, full));
        // Springflut: das Wasser steht am Deich, nicht dahinter
        c.set(java.time.LocalDate.of(2026, 8, 4), 6);
        r.selectEra();
        double hw = w.level.tide.next(w.level.hours(), true).hours();
        c.setEpochMillis(hw * 3_600_000);
        r.stepTideNow();
        com.dan.tide.Flood f = r.flood();
        int land = 0, wetLand = 0, sea = 0, wetSea = 0;
        for (int j = 0; j < f.nz; j++) for (int i = 0; i < f.nx; i++) {
            int k = j * f.nx + i;
            double x = f.x0 + (i + 0.5) * f.cell, z = f.z0 + (j + 0.5) * f.cell;
            byte a = l.artAt(x, z);
            boolean wet = f.surface[k] - f.ground[k] > 0.02;
            if (a == com.dan.rungholt.scene.Terrain.MARSCH || a == com.dan.rungholt.scene.Terrain.WARFT) { land++; if (wet) wetLand++; }
            else if (a == com.dan.rungholt.scene.Terrain.WATT && com.dan.rungholt.scene.Land1362.dikeSide(x, z) < -300) { sea++; if (wet) wetSea++; }
        }
        double lvl = w.level.level();
        check(land > 1000 && wetLand == 0 && sea > 1000 && wetSea > 0.9 * sea, String.format(Locale.GERMAN,
                "Hochwasser 4. August 2026 NHN %+.2f m: vor dem Deich %.0f %% nass, hinter dem Deich %d von %,d Zellen Marsch und Warft nass", lvl, wetSea * 100.0 / Math.max(1, sea), wetLand, land));
        String zn = r.zone();
        check(r.land() == l && r.landLine().startsWith("Gelände 1362: 54 Warften"), r.landLine());
        String tf = readOr("src/com/dan/rungholt/gl/shader/terrain.frag"), th = readOr("src/com/dan/rungholt/gl/shader/terrain_height.glsl");
        check(th.contains("uHeight1362") && th.contains("uEra") && tf.contains("uCanals") && tf.contains("uRoads"), "Bild: das Gelände blendet Höhe und Art über (uEra), Gräben, Sielzüge und Wege aus der Marsch im Shader");
        w.era.target = 0; w.era.blend = 0;
        r.selectEra();
        check(r.land() == r.terrain && r.flood() != f, "zurück nach heute: Gelände und Überflutung von heute (Ort zuvor: " + zn + ")");
    }

    /** Phase 6: Prielkante, Rinnsale, Strömung im Meer, unter Wasser. */
    static void priel(Renderer r, Wiring w, SceneClock c, com.dan.tide.Flood f, double hw) {
        System.out.println("Priel, Kante, Strömung (Phase 6)");
        Ansichten.Ansicht am = Ansichten.byKey(4), un = Ansichten.byKey(7);
        // die Kante vor „Am Priel“: entlang des Blicks bis 60 m, steilster Hang im Raster und mit der Feinform
        double hd = Math.toRadians(am.heading()), dx = Math.sin(hd), dz = -Math.cos(hd);
        double maxRaw = 0, maxRel = 0, drop = 0, step = 1;
        for (int side = -3; side <= 3; side++) {
            double ox = -dz * side * 6, oz = dx * side * 6, prevRaw = Double.NaN, prevRel = Double.NaN;
            for (double t = 0; t <= 60; t += step) {
                double x = am.x() + dx * t + ox, z = am.z() + dz * t + oz;
                double raw = r.terrain.height(x, z), rel = r.groundHeight(x, z);
                if (!Double.isNaN(prevRaw)) { maxRaw = Math.max(maxRaw, (prevRaw - raw) / step); maxRel = Math.max(maxRel, (prevRel - rel) / step); }
                prevRaw = raw;
                prevRel = rel;
            }
            drop = Math.max(drop, r.terrain.height(am.x() + ox, am.z() + oz) - r.terrain.height(am.x() + dx * 60 + ox, am.z() + dz * 60 + oz));
        }
        check(drop > 2.5 && maxRel > maxRaw * 1.5, String.format(Locale.GERMAN, "„Am Priel“: %.1f m Abbruch auf 60 m, steilster Hang %.0f %% mit Feinform statt %.0f %% im Raster", drop, maxRel * 100, maxRaw * 100));
        // Rinnsale im Watt um die Kirche (eben, über MTnw): ein kleiner Teil der Fläche liegt 6 cm und mehr tiefer
        int n = 0, in = 0;
        for (double z = -200; z < 200; z += 1.3) for (double x = -200; x < 200; x += 1.3) {
            if (r.terrain.artAt(x, z) != Terrain.WATT) continue;
            n++;
            if (r.groundHeight(x, z) - r.terrain.height(x, z) < -0.06) in++;
        }
        check(n > 0 && in > n * 0.01 && in < n * 0.3, String.format(Locale.GERMAN, "Rinnsale um die Kirche: %.1f %% der Fläche 6 cm und mehr unter dem Raster", 100.0 * in / Math.max(1, n)));
        // Niedrigwasser: im Priel vor der Kante steht Wasser, die Kante liegt trocken
        double lw2 = w.level.husum.next(hw, false).hours();
        c.setEpochMillis(lw2 * 3_600_000);
        r.stepTideNow();
        double ex = am.x() + dx * 45, ez = am.z() + dz * 45;
        check(f.depthAt(ex, ez) > 0.5 && f.depthAt(am.x(), am.z()) == 0, String.format(Locale.GERMAN, "Niedrigwasser: die Kante trocken, 45 m davor %.1f m Wasser im Priel", f.depthAt(ex, ez)));
        // Hochwasser: „Unter Wasser“ liegt unter dem Spiegel; bei Niedrigwasser noch im Priel
        double hw2 = w.level.husum.next(lw2, true).hours();
        c.setEpochMillis(hw2 * 3_600_000);
        r.stepTideNow();
        double eye = r.groundHeight(un.x(), un.z()) + un.eyeAboveGround();
        check(f.surfaceAt(un.x(), un.z()) > eye + 2, String.format(Locale.GERMAN, "„Unter Wasser“ bei Hochwasser %.1f m unter dem Spiegel", f.surfaceAt(un.x(), un.z()) - eye));
        // Strömung zur halben Flut im Priel: das Meer bekommt sie als Textur
        c.setEpochMillis((hw2 - 3) * 3_600_000);
        for (int k = 0; k < 5; k++) { c.setEpochMillis(c.epochMillis() + 60_000); r.stepTideNow(); }
        double[] v = f.velocityAt(ex, ez);
        check(Math.hypot(v[0], v[1]) > 0.1 && f.currentVersion > 0, String.format(Locale.GERMAN, "halbe Flut: im Priel vor der Kante %.2f m/s (Kräuselung ab 0,05, Schaumstreifen ab 0,4 m/s)", Math.hypot(v[0], v[1])));
        // Bild: Bausteine eingesetzt
        String tv = Shader.read("terrain.vert"), tf = Shader.read("terrain.frag"), sf, oc, uw;
        try (java.io.InputStream a = com.dan.ocean.gl.OceanGl.class.getResourceAsStream("surface.frag");
             java.io.InputStream b = com.dan.ocean.gl.OceanGl.class.getResourceAsStream("ocean_common.glsl");
             java.io.InputStream d = com.dan.ocean.gl.OceanGl.class.getResourceAsStream("underwater.frag")) {
            sf = new String(a.readAllBytes(), StandardCharsets.UTF_8);
            oc = new String(b.readAllBytes(), StandardCharsets.UTF_8);
            uw = d == null ? "" : new String(d.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { sf = oc = uw = ""; }
        check(tv.contains("mudflat_relief.glsl") && tv.contains("groundAt(") && tf.contains("mudflatR(") && tf.contains("dFdx(xz)"),
                "Bild: das Gelände nimmt die Feinform (Kante, Rinnsale) und die Rippel ohne Ringe um das Auge");
        check(sf.contains("flowAt(vRest)") && sf.contains("uUnder") && oc.contains("cascadeGain") && uw.contains("exp(-uSigma.x * dist)"),
                "Bild: das Meer nimmt die Strömung, bricht das Kachelmuster und zeigt sich von unten; trübes Wasser unter dem Spiegel");
        check(Quality.MITTEL.terrainFine == 2 && Quality.NIEDRIG.terrainFine == 8, "Gelände am Auge auf 2 m (Niedrig 8 m)");
    }

    /** Phase 7: die Seehunde an der Bank nach der echten Tide, die Flucht vor dem Wanderer, die Spuren im Watt. */
    static void seehunde(Renderer r, Wiring w, SceneClock c, com.dan.tide.Flood f) {
        System.out.println("Seehunde (Phase 7)");
        double lw = w.level.husum.next(w.level.hours() - 3, false).hours(), hw = w.level.husum.next(lw, true).hours();
        double hwPrev = w.level.husum.next(lw - 12.5, true).hours();
        c.setEpochMillis(hwPrev * 3_600_000);
        r.stepTideNow();
        r.settleSeals();
        check(r.colony.lying() == 0 && w.haulout != null && w.disturber != null, "Hochwasser: alle im Wasser; Bank und Störer hängen an der Verdrahtung (" + r.colony.describe() + ")");
        double tFirst = -1;
        for (double t = hwPrev; t <= lw + 1e-6; t += 1 / 12.0) {
            c.setEpochMillis(t * 3_600_000);
            r.stepTideNow();
            r.stepAnimalsScene(300);
            if (tFirst < 0 && r.colony.lying() > 0) tFirst = t;
        }
        int adults = r.colony.animals().size() - r.colony.pups(), dry = 0, lying = r.colony.lying();
        double wet = 0;
        for (com.dan.seal.Colony.Animal a : r.colony.animals()) if (a.act == com.dan.seal.Colony.Activity.LIEGEN) { if (r.colony.depth(a.x, a.z) < 0.2) dry++; else wet = Math.max(wet, r.colony.depth(a.x, a.z)); }
        check(tFirst > hwPrev + 1.5 && tFirst < lw, String.format(Locale.GERMAN, "die ersten liegen %.1f h nach Hochwasser (die Bank fällt trocken), Niedrigwasser nach %.1f h", tFirst - hwPrev, lw - hwPrev));
        check(lying >= adults * 0.6 && dry == lying, String.format(Locale.GERMAN, "Niedrigwasser: %d liegen auf der Bank (%d Erwachsene), %d auf dem Trockenen (sonst bis %.2f m Wasser)", lying, adults, dry, wet));
        com.dan.mudflat.WetField wf = r.wetField();
        int seal = 0;
        for (com.dan.mudflat.WetField.Track t : wf.tracks()) if (t.kind() == 2) seal++;
        check(seal > 20, seal + " Robbenspuren im Watt (bis zur nächsten Flut)");
        com.dan.mudflat.TrackMap tm = new com.dan.mudflat.TrackMap(512, 0.08);
        double tx = 0, tz = 0;
        for (com.dan.mudflat.WetField.Track t : wf.tracks()) if (t.kind() == 2) { tx = t.x(); tz = t.z(); }
        tm.update(wf.tracks(), tx, tz);
        check(tm.drawn > 0 && tm.depthAt(tx, tz) > 0.3, tm.describe() + String.format(Locale.GERMAN, ", am Ort der letzten Spur %.0f %% tief", tm.depthAt(tx, tz) * 100));
        // der Wanderer
        boolean sent = r.sendWalker();
        int flights0 = r.colony.flights;
        double minD = 1e9;
        for (int i = 0; i < 300 && r.walker.active; i++) {
            r.stepAnimalsScene(10);
            minD = Math.min(minD, Math.hypot(r.walker.x - com.dan.rungholt.scene.Seehundbank.X, r.walker.z - com.dan.rungholt.scene.Seehundbank.Z));
        }
        int boots = 0;
        for (com.dan.mudflat.WetField.Track t : wf.tracks()) if (t.kind() == 0) boots++;
        check(sent && r.colony.flights - flights0 >= lying * 0.7 && r.colony.lying() < lying / 4, String.format(Locale.GERMAN,
                "Wattwanderer bei Niedrigwasser: %d Tiere fliehen ins Wasser, %d liegen noch; er kam bis %.0f m an die Mitte der Bank", r.colony.flights - flights0, r.colony.lying(), minD));
        check(boots > 100 && !r.walker.active, boots + " Stiefelabdrücke; der Wanderer ist wieder fort");
        c.setEpochMillis(hw * 3_600_000);
        r.stepTideNow();
        r.settleSeals();
        check(r.colony.lying() == 0, "nächstes Hochwasser: wieder alle im Wasser");
        check(r.sealLine().startsWith("Seehunde ") && java.util.Arrays.asList(Renderer.PASSES).contains("Leben") && Quality.MITTEL.seal == com.dan.seal.Seal.Quality.MEDIUM,
                "HUD-Zeile, Durchgang „Tiere“, Stufe der Seehunde: " + r.sealLine());
        check(Shader.class.getResource("/com/dan/seal/gl/seal.vert") != null && Shader.class.getResource("/com/dan/seal/gl/shadow.frag") != null
                && Shader.read("terrain.frag").length() > 0 && readOr("src/com/dan/mudflat/gl/mudflat.glsl").contains("uMudTracks"), "Shader der Seehunde im Package, Spuren im Baustein des Watts");
    }

    /** Phase 8: die Wattvögel nach der echten Tide: rasten bei Hochwasser auf dem Vorland, fressen bei Ebbe am Saum, ein Schwarm wendet. */
    static void voegel(Renderer r, Wiring w, SceneClock c, com.dan.tide.Flood f) {
        System.out.println("Wattvögel (Phase 8)");
        com.dan.flock.Waders v = r.waders;
        double lw = w.level.tide.next(w.level.hours() - 3, false).hours(), hw = w.level.tide.next(lw, true).hours();
        double hwPrev = w.level.tide.next(lw - 12.5, true).hours();
        c.setEpochMillis(hwPrev * 3_600_000);
        r.stepTideNow();
        r.settleSeals();
        int n = v.birds(), wet = 0;
        int[] c0 = v.census();
        for (com.dan.flock.Waders.Group g : v.groups()) if (g.mode == com.dan.flock.Waders.Mode.RASTEN) for (com.dan.flock.Waders.Bird b : g.birds) if (w.level.depthAt(b.x, b.z) > 0.05) wet++;
        check(n > 500 && c0[com.dan.flock.Waders.Mode.RASTEN.ordinal()] >= n * 0.85 && wet < n * 0.02 && w.links().stream().anyMatch(l -> l.to().startsWith("flock")),
                String.format(Locale.GERMAN, "Hochwasser: %,d von %,d rasten auf dem Vorland, %d davon im Wasser; flock hängt an der Verdrahtung", c0[com.dan.flock.Waders.Mode.RASTEN.ordinal()], n, wet));
        double tHalf = -1;
        for (double t = hwPrev; t <= lw + 1e-6; t += 1 / 12.0) {
            c.setEpochMillis(t * 3_600_000);
            r.stepTideNow();
            r.stepAnimalsScene(300);
            if (tHalf < 0 && v.census()[com.dan.flock.Waders.Mode.FRESSEN.ordinal()] > v.birds() / 2) tHalf = t;
        }
        int[] c1 = v.census();
        double dun = 0, oys = 0;
        int nd = 0, no = 0, deep = 0, probe = 0, ground = 0;
        for (com.dan.flock.Waders.Group g : v.groups()) {
            if (g.mode != com.dan.flock.Waders.Mode.FRESSEN) continue;
            double e = v.edgeDistance(g.cx, g.cz, 600);
            if (!Double.isInfinite(e)) {
                if (g.species == com.dan.flock.WaderSpecies.ALPENSTRANDLAEUFER) { dun += e; nd++; }
                if (g.species == com.dan.flock.WaderSpecies.AUSTERNFISCHER) { oys += e; no++; }
            }
            for (com.dan.flock.Waders.Bird b : g.birds) if (!b.air) { ground++; if (w.level.depthAt(b.x, b.z) > g.species.wade() + 0.05) deep++; if (b.probe > 0.2) probe++; }
        }
        check(tHalf > hwPrev && tHalf < lw + 0.1 && c1[com.dan.flock.Waders.Mode.FRESSEN.ordinal()] >= v.birds() * 0.6, String.format(Locale.GERMAN,
                "die Hälfte frisst %.1f h nach Hochwasser; bei Niedrigwasser fressen %,d von %,d", tHalf - hwPrev, c1[com.dan.flock.Waders.Mode.FRESSEN.ordinal()], v.birds()));
        check(ground > 300 && deep < ground * 0.03 && probe > ground * 0.15, String.format(Locale.GERMAN, "Austernfischer und Co. stochern (%d von %d), keiner tiefer, als er waten kann (%d)", probe, ground, deep));
        check(nd > 0 && no > 0 && dun / nd < 40 && oys / no > dun / nd, String.format(Locale.GERMAN, "Alpenstrandläufer am Saum (%.0f m von der Kante), Austernfischer weiter oben (%.0f m)", dun / Math.max(1, nd), oys / Math.max(1, no)));
        com.dan.flock.Waders.Group fg = r.flushBirds();
        r.stepAnimalsScene(1);
        check(fg != null && fg.species.murmur() && fg.mode == com.dan.flock.Waders.Mode.SCHWARM, "aufgescheucht: " + (fg == null ? "keiner" : fg.species.label() + " " + fg.size() + " " + fg.mode.label));
        int to0 = v.takeoffs, fSum = 0, fWet = 0;
        for (double t = lw; t <= hw - 0.3 + 1e-6; t += 1 / 12.0) {
            c.setEpochMillis(t * 3_600_000);
            r.stepTideNow();
            r.stepAnimalsScene(300);
            for (com.dan.flock.Waders.Group g : v.groups())
                if (g.mode == com.dan.flock.Waders.Mode.FRESSEN) for (com.dan.flock.Waders.Bird b : g.birds) if (!b.air) { fSum++; if (w.level.depthAt(b.x, b.z) > g.species.wade() + 0.05) fWet++; }
        }
        check(fSum > 1000 && fWet < fSum * 0.05, String.format(Locale.GERMAN, "mit der Flut: von %,d Vögeln, die fressen, stehen %,d tiefer im Wasser, als sie waten können", fSum, fWet));
        int[] c2 = v.census();
        int up = c2[com.dan.flock.Waders.Mode.RASTEN.ordinal()] + c2[com.dan.flock.Waders.Mode.FLIEGEN.ordinal()] + c2[com.dan.flock.Waders.Mode.FORT.ordinal()];
        check(up >= v.birds() * 0.85 && v.takeoffs > to0, String.format(Locale.GERMAN, "vor der Flut fliegen alle auf: 20 min vor Hochwasser %,d von %,d rasten oder fliegen (%d Abflüge)", up, v.birds(), v.takeoffs - to0));
        check(r.birdLine().startsWith("Wattvögel ") && Quality.MITTEL.birds == com.dan.flock.Waders.Quality.MEDIUM && Shader.class.getResource("/com/dan/flock/gl/wader.vert") != null,
                "HUD-Zeile, Stufe und Shader der Wattvögel: " + r.birdLine());
    }

    /** Phase 9: die Wattwanderung nach Südfall auf den echten Höhen, das Jahr, Südfall im Detail. */
    static void wanderung(Renderer r, Wiring w, SceneClock c, com.dan.tide.Flood f) {
        System.out.println("Wattwanderung und Jahr (Phase 9)");
        double lw = w.level.tide.next(w.level.hours(), false).hours();
        c.setEpochMillis((lw - com.dan.rungholt.scene.Wattwanderung.START_BEFORE_LW) * 3_600_000);
        r.stepTideNow();
        r.settleSeals();
        int alarms0 = r.waders.alarms;
        boolean dry = r.startHike();
        com.dan.figure.Hike h = r.hike;
        com.dan.mudflat.WetField wf = r.wetField();
        double t0 = c.epochMillis(), nearest = 1e9;
        for (int i = 0; i < 4 * 12 && h.state != com.dan.figure.Hike.State.ANGEKOMMEN; i++) {
            c.setEpochMillis(c.epochMillis() + 300_000);
            r.stepTideNow();
            for (int k = 0; k < 30; k++) {
                r.stepAnimalsScene(10);
                double[] hc = h.centre();
                for (com.dan.flock.Waders.Group g : r.waders.groups()) if (g.mode == com.dan.flock.Waders.Mode.FRESSEN) nearest = Math.min(nearest, Math.hypot(g.cx - hc[0], g.cz - hc[1]));
            }
        }
        double hours = (c.epochMillis() - t0) / 3_600_000;
        int bare = 0, boots = 0;
        for (com.dan.mudflat.WetField.Track t : wf.tracks()) { if (t.kind() == 3) bare++; else if (t.kind() == 4) boots++; }
        check(dry && h.state == com.dan.figure.Hike.State.ANGEKOMMEN && hours < 2.8 && h.halts == 4, String.format(Locale.GERMAN,
                "Wattwanderung %.1f km von Fuhlehörn an die Warft von Südfall in %.1f h, vier Halte (Start 1,5 h vor Niedrigwasser)", h.length() / 1000, hours));
        check(h.wettest < 0.05 && h.waited < 900, String.format(Locale.GERMAN, "der Weg bleibt trocken (tiefstes Wasser am Fuß %.2f m), gewartet %.0f s", h.wettest, h.waited));
        check(bare + boots > 2000, String.format(Locale.GERMAN, "%,d Fußspuren im Watt (%,d barfuß, %,d Stiefel)", bare + boots, bare, boots));
        check(r.waders.alarms > alarms0 || nearest > 250, String.format(Locale.GERMAN, "%d Gruppen Vögel fliegen vor der Gruppe auf (die nächste, die fraß, war %.0f m entfernt)", r.waders.alarms - alarms0, nearest));
        double[] near = h.at(com.dan.rungholt.scene.Wattwanderung.nearest(h, 0, 0), 0);
        check(Math.hypot(near[0], near[1]) < 400 && r.hikeLine().startsWith("Wattwanderung "), String.format(Locale.GERMAN, "Halt bei Rungholt %.0f m von der Kirche; %s", Math.hypot(near[0], near[1]), r.hikeLine()));
        r.stopHike();
        // das Jahr
        Wiring.Jahreszeit j = w.season;
        com.dan.season.Climate cl = com.dan.season.Climate.nordfriesland();
        check(Math.abs(cl.meanTemp(15) - 1.5) < 0.5 && Math.abs(cl.meanTemp(196) - 17.3) < 0.4, "Klima Nordfriesland: Januar 1,5 °C, Juli 17,3 °C");
        double[] snow = new double[4], red = new double[4];
        int[] months = {1, 4, 7, 10};
        for (int k = 0; k < 4; k++) {
            c.set(java.time.LocalDate.of(2026, months[k], 21), 13);
            j.step(1e9);
            snow[k] = j.snowDepth();
            red[k] = j.marshRed();
        }
        check(snow[0] > 0.05 && snow[1] == 0 && snow[2] == 0 && snow[3] == 0, String.format(Locale.GERMAN, "Schnee am 21. Januar %.0f cm (gewürfelter Winter, Startwert 26), April, Juli, Oktober schneefrei", snow[0] * 100));
        check(red[3] > 0.6 && red[2] < 0.1 && red[0] < 0.1, String.format(Locale.GERMAN, "der Queller wird im Oktober rot (%.0f %%), im Juli grün", red[3] * 100));
        check(readOr("src/com/dan/rungholt/gl/shader/terrain.frag").contains("uSeason") && Shader.class.getResource("/com/dan/figure/gl/figure.vert") != null
                && Shader.class.getResource("/com/dan/season/gl/snowfall.vert") != null, "Gelände mit Schnee und Salzwiese, Figuren und Schneefall als Shader im Package");
        // Südfall
        double worst = 0;
        for (com.dan.timber.House hs : com.dan.rungholt.scene.Suedfall.placed(r::groundHeight)) worst = Math.max(worst, Math.abs(hs.y() - r.groundHeight(hs.x(), hs.z())));
        boolean ponds = true;
        for (com.dan.rungholt.scene.Suedfall.Pond p : com.dan.rungholt.scene.Suedfall.PONDS) ponds &= r.terrain.artAt(p.x(), p.z()) != com.dan.rungholt.scene.Terrain.WATT && Math.abs(p.level() - r.groundHeight(p.x(), p.z())) < 0.7;
        check(worst < 0.5 && ponds && com.dan.timber.HouseType.FRIESENHAUS.wall == com.dan.timber.Material.BRICK, String.format(Locale.GERMAN,
                "Südfall: Friesenhaus und Stall auf der Warft (höchstens %.2f m über oder unter dem Grund), Fething und Pütten auf Land", worst));
    }

    static void kamera() {
        System.out.println("Kamera");
        Camera cam = new Camera();
        check(cam.eye[0] > 1000 && cam.heading() > 230 && cam.heading() < 280,
                String.format(Locale.GERMAN, "Grundansicht: von Osten über das Watt, Blick %03.0f° auf Südfall und die Hever", cam.heading()));
        cam.place(-1000, 0, 0, 90, 0);
        cam.groundLevel = Buehne.height(-1000, 0);
        cam.step(0.1);
        check(cam.eye[1] >= Site.SUEDFALL_WARFT + Camera.MIN_EYE - 1e-9, String.format(Locale.GERMAN, "Frei auf der Warft: Auge bleibt über dem Boden (%.1f m)", cam.eye[1]));
        cam.setMode(Camera.Mode.KREISEN);
        check(cam.mode == Camera.Mode.KREISEN && cam.dist > 1, "Wechsel Frei → Kreisen behält die Ansicht");
        Terrain t = Terrain.load(Terrain.defaultDir());
        int ready = 0;
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            Camera c = new Camera();
            if (Ansichten.apply(a, c, Renderer.PHASE, t::height)) {
                ready++;
                if (c.eye[1] < t.height(a.x(), a.z())) check(false, a + " liegt unter dem Gelände");
            }
        }
        check(ready == 9 && Ansichten.ALLE.size() == 9, ready + " von 9 Ansichten stehen, seit Phase 11 auch „Rungholt 1362“ (vom Kirchturm) und „Sielhafen“");
        if (t.measured()) {
            Ansichten.Ansicht d = Ansichten.byKey(1), sf = Ansichten.byKey(3), b = Ansichten.byKey(5), pr = Ansichten.byKey(4);
            check(t.artAt(d.x(), d.z()) == Terrain.ERHOEHT && t.height(d.x(), d.z()) > 6.5, String.format(Locale.GERMAN, "1 Vom Deich steht auf der Deichkrone von Nordstrand (NHN %+.1f m)", t.height(d.x(), d.z())));
            check(t.artAt(sf.x(), sf.z()) == Terrain.ERHOEHT && t.height(sf.x(), sf.z()) > 5, "3 Südfall steht auf der Warft");
            Ansichten.Ansicht w = Ansichten.byName("AufdemWatt");
            check(w != null && t.artAt(w.x(), w.z()) == Terrain.WATT && t.height(w.x(), w.z()) > Site.MTNW, "2 Auf dem Watt steht im Watt über dem Niedrigwasser");
            double bh = t.height(com.dan.rungholt.scene.Seehundbank.X, com.dan.rungholt.scene.Seehundbank.Z), pr7 = t.height(-2790, 440);
            check(t.height(b.x(), b.z()) > -1.4 && Math.hypot(b.x() - com.dan.rungholt.scene.Seehundbank.X, b.z() - com.dan.rungholt.scene.Seehundbank.Z) < com.dan.rungholt.scene.Seehundbank.R
                    && bh > -0.5 && pr7 < -2.0, String.format(Locale.GERMAN, "5 Seehundbank: auf der Bank (Mitte NHN %+.1f m), Blick über die Liegeplätze zum Priel (%+.1f m)", bh, pr7));
            double ph = Math.toRadians(pr.heading());
            check(t.height(pr.x(), pr.z()) > -1.2 && t.height(pr.x() + Math.sin(ph) * 55, pr.z() - Math.cos(ph) * 55) < -3.5, String.format(Locale.GERMAN, "4 Am Priel: auf der Kante (NHN %+.1f m), 55 m voraus der Priel (%+.1f m)", t.height(pr.x(), pr.z()), t.height(pr.x() + Math.sin(ph) * 55, pr.z() - Math.cos(ph) * 55)));
        } else {
            check(Buehne.zone(Ansichten.byKey(3).x(), Ansichten.byKey(3).z()).equals("Warft von Südfall"), "3 Südfall steht auf der Warft (Bühne)");
        }
    }

    static void qualitaet() {
        System.out.println("Qualität");
        boolean mono = true;
        Quality[] q = Quality.values();
        for (int i = 1; i < q.length; i++) mono &= q[i].renderScale >= q[i - 1].renderScale && q[i].budgetMs() > q[i - 1].budgetMs();
        check(mono, "Stufen Niedrig → Ultra steigen an");
        check(Math.abs(Quality.MITTEL.budgetMs() - 15.2) < 1e-9 && Math.abs(Quality.HOCH.budgetMs() - 2 * 15.2) < 1e-9, "„Mittel“ hat das Budget aus Abschnitt 10: 15,2 ms, „Hoch“ das Doppelte");
        Renderer r = new Renderer();
        check(r.quality() == Quality.MITTEL, "Start auf „Mittel“ (F22 A)");
        r.regulateForTest(30, 10_000_000_000L);
        check(r.renderScale < Quality.MITTEL.renderScale, String.format(Locale.GERMAN, "Grafikkarte 30 ms: Auflösung sinkt auf %.0f %%", r.renderScale * 100));
        check(r.renderScale >= Renderer.MIN_SCALE, "nie unter " + Math.round(Renderer.MIN_SCALE * 100) + " %");
        // Phase 17: durchgängig – jede Stufe kommt in jedem Package an, und alles steigt mit der Stufe
        boolean alle = true, steigt = true;
        for (Quality s : q) {
            r.setQuality(s);
            alle &= r.ocean.quality == s.ocean && r.clouds.quality == s.clouds && r.mudflat.quality == s.mudflat() && r.timber.quality == s.timber()
                    && r.smoke.quality == s.smoke() && s.fracture().ordinal() == s.ordinal() && s.tide.ordinal() <= s.ordinal() && s.seal.ordinal() == s.ordinal()
                    && s.birds.ordinal() == s.ordinal() && s.ocean.ordinal() == s.ordinal();
        }
        for (int i = 1; i < q.length; i++)
            steigt &= q[i].terrainRange > q[i - 1].terrainRange && q[i].groundRadius > q[i - 1].groundRadius && q[i].timber().far > q[i - 1].timber().far
                    && q[i].smoke().nearCount > q[i - 1].smoke().nearCount && q[i].reed.range >= q[i - 1].reed.range && q[i].tide.coarse <= q[i - 1].tide.coarse;
        r.setQuality(Quality.MITTEL);
        check(alle && steigt, "Qualitätsstufen durchgängig (Phase 17): Meer, Wolken, Watt, Häuser, Rauch, Deichbruch, Seehunde, Vögel folgen der Stufe, Sichtweiten und Mengen steigen; "
                + Quality.ULTRA.summary());
    }

    static void physik() {
        System.out.println("Faden der Physik");
        Physik p = new Physik();
        int[] n = {0};
        p.add((t, dt) -> n[0]++);
        p.advance(0.5);
        int steps = p.drain();
        check(steps == Physik.MAX_BEHIND && n[0] == steps, "Fester Schritt 1/120 s, höchstens " + Physik.MAX_BEHIND + " Schritte nachgeholt (" + steps + ")");
        p.advance(Physik.DT * 3.5);
        check(p.drain() == 3, "3,5 Schritte Zeit → 3 Schritte, der Rest wartet");
        p.add((t, dt) -> { throw new IllegalStateException("Probe"); });
        p.advance(Physik.DT);
        p.drain();
        check(p.aufgaben() == 1, "Eine fehlerhafte Aufgabe fällt heraus, die anderen laufen weiter");
    }

    static void shader() {
        System.out.println("Shader");
        for (String[] prog : Renderer.shaderFiles()) {
            for (String f : prog) {
                boolean found;
                String text = "";
                try {
                    text = Shader.source(f, null).text;
                    found = text.startsWith("#version 430 core");
                } catch (RuntimeException e) {
                    found = false;
                }
                check(found, "Shader " + f + " gefunden und Bausteine eingesetzt (" + text.split("\n").length + " Zeilen)");
            }
        }
        String st = Shader.source("terrain.frag", null).text, th = Shader.source("terrain_height.glsl", null).text;
        check(st.contains("artShare(xz, 3u)") && st.contains("uLevel") && th.contains("uOutside") && th.contains("usampler2D uSource"), "Gelände: Art je Zelle weich gemischt, Nässe nach dem Pegel, Meeresboden außerhalb des Rasters");
        boolean swapped = false, half = false;
        for (String[] prog : Renderer.shaderFiles()) for (String f : prog) {
            String x = Shader.source(f, null).text;
            half |= Pattern.compile("\\bhalf\\b").matcher(x).find();
            swapped |= Pattern.compile("smoothstep\\((-?[\\d.]+), (-?[\\d.]+),").matcher(x).results().anyMatch(m -> Double.parseDouble(m.group(1)) > Double.parseDouble(m.group(2)));
        }
        check(!half && !swapped, "kein reserviertes Wort „half“, kein smoothstep mit vertauschten Kanten (AMD-Treiber sind streng)");
        check(ComputeProbe.expected(0, 63) == 64 * 65 / 2 && ComputeProbe.expected(1, 0) == 65, "Compute-Test: Erwartungswerte der Präfixsumme");
    }

    static void verdrahtung() {
        System.out.println("Verdrahtung");
        Wiring w = new Wiring(new SceneClock());
        check(w.links().size() == 87 && w.links().stream().filter(l -> l.phase() == 14).count() == 8 && w.links().stream().filter(l -> l.phase() == 15).count() == 3
                        && w.links().stream().filter(l -> l.phase() == 16).count() == 5 && w.links().stream().filter(l -> l.phase() == 17).count() == 3
                        && Wiring.IDEENSPEICHER.size() == 5
                        && w.links().stream().allMatch(l -> l.phase() <= Renderer.PHASE)
                        && Wiring.planned().stream().filter(l -> l.phase() > 4).allMatch(l -> l.phase() > Renderer.PHASE)
                        && w.links().stream().filter(l -> l.phase() == 14).allMatch(l -> l.to().startsWith("soundscape")),
                "Phase 17: " + w.summary() + " (3 neu: die Gischt über der Deichkrone mit Wind, Spiegel und Brechern; 5 in den Ideenspeicher)");
        int found = 0;
        for (String n : Wiring.INTERFACES) {
            try {
                Class<?> c = Class.forName("com.dan.rungholt.wiring." + n);
                if (c.isInterface()) found++;
            } catch (ClassNotFoundException e) { /* fehlt */ }
        }
        check(found == Wiring.INTERFACES.length && found == 20, found + " von 20 Schnittstellen aus Abschnitt 4 stehen als Interface, darunter neu Era, Level, Water, Wetness, Haulout, Disturber");
        check(w.links().stream().anyMatch(l -> l.via().startsWith("Level") && l.phase() == 4)
                && w.links().stream().anyMatch(l -> l.via().startsWith("Wetness") && l.phase() == 5)
                && w.links().stream().anyMatch(l -> l.via().startsWith("Water") && l.phase() == 5)
                && w.links().stream().anyMatch(l -> l.via().contains("Wetness") && l.phase() == 14 && l.to().startsWith("soundscape"))
                && Wiring.IDEENSPEICHER.stream().anyMatch(l -> l.via().startsWith("Wetness")), "Level (Phase 4), Wasser an mudflat und Nässe (Phase 5), Trockenzeit an soundscape (Phase 14) verbunden; Nässe an ground und weathering im Ideenspeicher (Phase 17)");
        check(Beaufort.of(w.wind.speed()) == 4, "Wind von Hand: Bft 4 (" + Beaufort.NAME[4] + ")");
        double[] to = w.wind.toVector();
        check(to[0] > 0.9 && to[2] < 0, "Westsüdwestwind weht nach Ostnordost auf die Küste");
        check(w.era.blend() == 0 && w.era.label().equals("Heute"), "Ära: Start heute");
        w.era.toggle();
        w.era.step(2);
        check(Math.abs(w.era.blend() - 0.5) < 1e-9 && w.era.label().startsWith("Zeitreise"), "Zeitreise: nach 2 s halb");
        w.era.step(3);
        check(w.era.blend() == 1 && w.era.is1362() && w.era.label().equals("1362"), "nach 4 s in 1362");
        Collider kirche = Collider.box(0, 10, 0, Site.CHURCH_LENGTH / 2, 10, Site.CHURCH_WIDTH / 2);
        check(kirche.distance(0, 10, 0) < 0 && Math.abs(kirche.distance(30, 10, 0) - 10) < 1e-9, "Collider: Kirche 40 × 15 m als Quader");
    }

    /** Phase 3: Himmel, Wetter mit dem Klima Nordfrieslands, Orkan, Nebel, Augenanpassung. */
    static void wetter() {
        System.out.println("Himmel und Wetter (Phase 3)");
        SceneClock clock = new SceneClock();
        Wiring w = new Wiring(clock);
        check(w.weather.climate != null && w.weather.climate.length == 12 && w.weather.climate[6] == 17.3 && w.weather.climate[0] == 1.5
                && w.weather.zone.getId().equals("Europe/Berlin") && w.weather.summerDay == 196, "Klima „Nordfriesland“: Juli 17,3 °C, Januar 1,5 °C [G], Ortszeit Europe/Berlin, Sommer Mitte Juli");
        // Der Himmel steht im Szenenraum richtig: Sonne über Süden (+z) am Mittag, Höhe wie die Uhr
        com.dan.sky.Sky sky = new com.dan.sky.Sky();
        w.feed(sky, 2);
        com.dan.sky.Sky.State st = sky.updateNow();
        double[] sp = clock.sun();
        check(Math.abs(st.sunAlt - sp[0]) < 0.7 && st.sunDir[2] > 0.5 && Math.abs(st.sunDir[0]) < 0.1,
                String.format(Locale.GERMAN, "com.dan.sky: Sonne %.1f° über Süden (+z), die Uhr sagt %.1f°", st.sunAlt, sp[0]));
        check(sky.groundAlbedo == 0.10 && sky.eyeHeight == 2 && sky.visibilityKm == w.visibilityKm, "Himmel bekommt Auge, Sicht und die dunkle Rückstrahlung von Watt und Wasser (0,10)");
        clock.set(LocalDate.of(2026, 7, 21), 23.5);
        w.feed(sky, 2);
        com.dan.sky.Sky.State night = sky.updateNow();
        double eDay = com.dan.sky.Sky.State.exposureFor(st.adaptation, 0.85), eNight = com.dan.sky.Sky.State.exposureFor(night.adaptation, 0.85);
        check(night.sunAlt < -8 && eNight > eDay * 50 && Renderer.scotopic(night) > 0.3 && Renderer.scotopic(st) < 0.01,
                String.format(Locale.GERMAN, "Augenanpassung: nachts %.0f-fach belichtet, Stäbchen %.0f %% (am Tag 0 %%)", eNight / eDay, Renderer.scotopic(night) * 100));
        check(Renderer.LAND_GAIN > 1 && Renderer.LAND_GAIN < 12 && Renderer.STORM_ADAPT > 0 && Renderer.STORM_ADAPT < 1,
                "Augenanpassung: trockenes Land zählt dreifach, unter Gewitterwolken bleibt es düster (nur zum Teil nachgeführt)");
        // Orkan aus Westnordwest
        clock.set(LocalDate.of(2026, 7, 21), 12);
        w.regie = true;
        w.startFree(com.dan.weather.Weather.Phase.ORKAN);
        double max = 0, dir = 0, p = 2000;
        for (int k = 0; k < 24; k++) {
            clock.setEpochMillis(clock.epochMillis() + 300_000);
            w.feedWeather(0.1);
            if (w.wind.speed() > max) { max = w.wind.speed(); dir = w.wind.fromDeg(); }
            p = Math.min(p, w.pressureHPa);
        }
        check(max > 28 && dir > 270 && dir < 320 && p < 985 && w.oktas > 6.5 && w.rainRate > 1,
                String.format(Locale.GERMAN, "Orkan: bis %.1f m/s (Bft %d) aus %03.0f°, %.0f hPa, %.0f/8, Regen %.1f mm/h", max, Beaufort.of(max), dir, p, w.oktas, w.rainRate));
        com.dan.ocean.Ocean o = com.dan.ocean.Ocean.watt(4);
        w.feed(o);
        check(o.windSpeed <= 28 && o.windSpeed >= 27, String.format(Locale.GERMAN, "Das Meer im Watt bekommt höchstens Bft 10 (%.1f m/s)", o.windSpeed));
        // Seenebel am Morgen, der sich hebt
        com.dan.fog.Fog f = new com.dan.fog.Fog();
        w.fogAmount = 0.9;
        w.wind.speed = 2.5;
        w.feed(f, -2);
        double top = f.top, amount = f.amount, base0 = f.base;
        w.feed(f, 15);
        check(amount > 0.9 && top > 80 && top < 100 && base0 == 0 && f.base > 20 && f.visibilityM < 120,
                String.format(Locale.GERMAN, "Seenebel: vor Sonnenaufgang %.0f m hoch, Sicht %.0f m; mit der Sonne hebt er sich (Untergrenze %.0f m)", top, f.visibilityM, f.base));
        w.wind.speed = 12;
        w.feed(f, -2);
        check(f.amount < 0.05, "Seenebel: Wind über 11 m/s reißt ihn auf");
        // Blitz trifft Wasser oder Land
        Terrain t = Terrain.load(Terrain.defaultDir());
        w.terrain = t;
        com.dan.lightning.Lightning l = new com.dan.lightning.Lightning();
        w.level.hand = 1.0;
        w.feed(l, new com.dan.cloud.Clouds().snapshot(), false, new double[]{0, 2, 0});
        double gW = l.ground.height(0, 0), gK = l.ground.height(-6800, -5800);
        check(Math.abs(gW - 1.0) < 1e-9 && (gK == t.height(-6800, -5800)), String.format(Locale.GERMAN,
                "Blitz: im Watt auf dem Wasser (NHN %+.1f m), im Koog von Pellworm auf dem Boden (NHN %+.1f m)", gW, gK));
        w.level.hand = Double.NaN;
        check(java.util.Arrays.asList(Renderer.PASSES).containsAll(java.util.List.of("Himmel", "Wolken", "Regen", "Nebel")), "Durchgänge: " + String.join(", ", Renderer.PASSES));
        String tf = Shader.read("terrain.frag");
        check(tf.contains("uCloudShadow") && tf.contains("uFlashRgb") && tf.contains("0.31831") && tf.contains("smoothstep(40.0, 150.0, vDist)") && tf.contains("2.0 * w / max(px"),
                "Gelände: Wolkenschatten, Blitzlicht, Licht/π mit com.dan.sky, Raster nah am Auge aus und nie breiter als sein Bildpunkt (Befund Status_Phase2)");
    }

    static void symbol() {
        System.out.println("Programmsymbol");
        boolean all = true;
        for (int s : AppIcon.SIZES) {
            java.awt.image.BufferedImage b = AppIcon.paint(s);
            all &= b.getWidth() == s && (b.getRGB(s / 2, s / 2) >>> 24) == 255 && (b.getRGB(0, 0) >>> 24) < 255;
        }
        check(all, "Symbol in " + AppIcon.SIZES.length + " Größen gemalt, deckend in der Mitte, runde Ecken");
        schliff();
    }

    /** Phase 17: Symbol im Jar und als .ico, Verknüpfung, Quellenliste, Dauerlauf, READMEs. */
    static void schliff() throws RuntimeException {
        System.out.println("Schliff (Phase 17)");
        boolean png = true;
        for (int s : AppIcon.SIZES) png &= AppIcon.class.getResource("icon/rungholt_" + s + ".png") != null && AppIcon.load(s).getWidth() == s;
        File ico = new File("Rungholt.ico"), lnk = new File("Rungholt_Verknuepfung.bat");
        boolean icoOk = false;
        try { byte[] h = java.nio.file.Files.readAllBytes(ico.toPath()); icoOk = h.length > 1000 && h[0] == 0 && h[1] == 0 && h[2] == 1 && (h[4] & 0xff) >= 5; } catch (java.io.IOException e) { /* fehlt */ }
        String bat = "";
        try { bat = java.nio.file.Files.readString(lnk.toPath()); } catch (java.io.IOException e) { /* fehlt */ }
        check(png && icoOk && bat.contains("Rungholt.ico") && bat.contains("Rungholt.bat") && bat.contains("\r\n"),
                "Programmsymbol in " + AppIcon.SIZES.length + " Größen im Jar und als Rungholt.ico, Rungholt_Verknuepfung.bat legt die Verknüpfung an (Windows-Zeilenenden)");
        String ql = "";
        try { ql = java.nio.file.Files.readString(com.dan.rungholt.tools.Quellenliste.DATEI.toPath()); } catch (java.io.IOException e) { /* fehlt */ }
        check(ql.equals(com.dan.rungholt.tools.Quellenliste.text()) && ql.contains("| WPMARC |") && ql.contains("Sturmfluten"),
                "docs/QUELLEN.md ist, was tools.Quellenliste jetzt schreibt (" + ql.length() / 1000 + " kB)");
        check(!new Renderer().showMarks, "Marken von Phase 1 (Raster, Ring um Südfall) ab Start aus: keine helle Linie im Vogelauge, kein oranger Strich an Südfall");
        check(com.dan.rungholt.app.Dauerlauf.MIN_RUNDEN == 2 && com.dan.rungholt.app.Dauerlauf.FREIE_TIDEN == 2 && !com.dan.rungholt.app.Dauerlauf.laeuft(),
                "Dauerlauf: je Runde „Eine Tide“, zwei Tiden frei, „Die Grote Mandränke“ (drei Tiden, eine Mandränke), mindestens zwei Runden");
        File base = new File("src/com/dan");
        java.util.List<String> ohne = new java.util.ArrayList<>();
        File[] ds = base.listFiles(File::isDirectory);
        if (ds != null) for (File d : ds) {
            File rm = new File(d, "README.md");
            String t = "";
            try { t = java.nio.file.Files.readString(rm.toPath()); } catch (java.io.IOException e) { /* fehlt */ }
            if (t.length() < 800 || !t.contains("##")) ohne.add(d.getName());
        }
        String gh = "";
        try { gh = java.nio.file.Files.readString(new File("README.md").toPath()); } catch (java.io.IOException e) { /* fehlt */ }
        java.util.regex.Matcher bm = java.util.regex.Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)").matcher(gh);
        int bilder = 0, fehlen = 0;
        while (bm.find()) { bilder++; if (!new File(bm.group(1)).isFile()) fehlen++; }
        check(ohne.isEmpty() && bilder >= 6 && fehlen == 0, String.format(Locale.GERMAN, "README je Package (%d), GitHub-README mit %d Bildern, alle vorhanden%s",
                ds == null ? 0 : ds.length, bilder, ohne.isEmpty() ? "" : "; zu kurz: " + ohne));
    }

    /** Die 21 Element-Packages stehen im Quellordner, jedes mit README; die 7 abgestellten liegen in _alt/packages (F6 A). */
    /** Die 29 Element-Packages stehen im Quellordner, jedes mit README; ice liegt in _alt/packages (F7 A). */
    static void pakete() {
        System.out.println("Packages");
        File base = new File("src/com/dan");
        if (!base.isDirectory()) { System.out.println("  (Quellordner nicht gefunden, übersprungen)"); return; }
        String[] wanted = {"tide", "mudflat", "seal", "sky", "cloud", "weather", "rain", "fog", "lightning", "ocean", "spray", "buoyancy",
                "weathering", "flock", "forest", "ground", "river", "reed", "season", "soundscape", "store", "wave", "fracture", "kino",
                "timber", "road", "hearth", "smoke", "muster", "figure"};
        int n = 0;
        for (String w : wanted) if (new File(base, w + "/README.md").isFile()) n++;
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("rungholt"));
        check(n == wanted.length && dirs != null && dirs.length == wanted.length, n + " von 30 Packages mit README im Build (21 aus Glacier, 5 aus Haithabu, 4 neu: tide, mudflat, seal, figure)");
        check(!new File(base, "ice").exists() && new File("_alt/packages/ice").isDirectory(), "ice nach _alt/packages abgestellt (F7 A)");
        check(!new File("db/Object_nach_Schema.sql").exists() && !new File("db/TableFormat.sql").exists() && new File("db/USER.sql").isFile(),
                "db/: USER.sql bleibt, die beiden fremden Dateien in _alt/db (F7 A)");
        check(readOr(".gitignore").contains("/templates/"), ".gitignore hält templates/ aus dem Git");
    }

    static String readOr(String f) {
        try { return Files.readString(Path.of(f), StandardCharsets.UTF_8); } catch (Exception e) { return ""; }
    }

    /** Kein Element-Package kennt die App (die volle Prüfung macht check-packages). */
    static void isolation() throws Exception {
        System.out.println("Isolation");
        File base = new File("src/com/dan");
        if (!base.isDirectory()) { System.out.println("  (Quellordner nicht gefunden, übersprungen)"); return; }
        Pattern p = Pattern.compile("com\\.dan\\.rungholt");
        int found = 0, files = 0;
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("rungholt"));
        if (dirs != null) for (File d : dirs) {
            try (Stream<Path> s = Files.walk(d.toPath())) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    files++;
                    if (p.matcher(Files.readString(f, StandardCharsets.UTF_8)).find()) { found++; System.out.println("    " + f); }
                }
            }
        }
        check(found == 0, files + " Dateien in " + (dirs == null ? 0 : dirs.length) + " Packages, keine nennt com.dan.rungholt");
    }

    static double hourOf(double epochMs) {
        var z = Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE);
        return z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
    }

    static String hm(double h) { return String.format("%02d:%02d", (int) h, (int) Math.round((h - (int) h) * 60) % 60); }

    private Pruefung() { }
}
