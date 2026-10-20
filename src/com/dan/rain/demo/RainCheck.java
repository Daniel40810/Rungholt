package com.dan.rain.demo;

import com.dan.rain.Rain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“.
 * Geprüft wird die Physik (Verteilung, Fallgeschwindigkeit, Regenmenge, Sicht), der Tropfenkasten (fest in der Welt,
 * fällt mit der Fallgeschwindigkeit, treibt mit dem Wind), die Größenverteilung der gezeichneten Tropfen, die Karte der
 * Vorhänge, die Treffer auf Flächen, Voreinstellungen und Reproduzierbarkeit.
 */
public final class RainCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new RainCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // Physik
        double worst = 0;
        for (double r : new double[]{0.3, 1, 5, 15, 60}) worst = Math.max(worst, Math.abs(Rain.rateFromDistribution(r) / r - 1));
        check(worst < 0.25, f("Regenmenge aus Verteilung und Fallgeschwindigkeit trifft die Rate auf %.0f %% (0,3 bis 60 mm/h)", worst * 100));
        check(Math.abs(Rain.terminal(1) - 4.03) < 0.3 && Math.abs(Rain.terminal(2) - 6.49) < 0.3 && Math.abs(Rain.terminal(5) - 9.09) < 0.2,
                f("Fallgeschwindigkeit 1/2/5 mm: %.2f/%.2f/%.2f m/s (Gunn und Kinzer: 4,03/6,49/9,09)", Rain.terminal(1), Rain.terminal(2), Rain.terminal(5)));
        double n1 = Rain.numberDensity(1, 0.5);
        check(n1 > 200 && n1 < 300 && Rain.numberDensity(10, 0.5) > n1 && 1 / Rain.lambda(30) > 1 / Rain.lambda(1),
                f("1 mm/h: %.0f Tropfen über 0,5 mm je m³; mehr Regen, mehr und größere Tropfen", n1));
        check(Rain.visibilityKm(1) > 10 && Rain.visibilityKm(60) < 1.5 && Rain.visibilityKm(5) < Rain.visibilityKm(1),
                f("Sicht im Regen: 1 mm/h %.0f km, 5 mm/h %.1f km, 60 mm/h %.1f km", Rain.visibilityKm(1), Rain.visibilityKm(5), Rain.visibilityKm(60)));
        double imp = Rain.impacts(5, 1.0);
        check(imp > 400 && imp < 1200 && Rain.impacts(15, 1.0) > imp, f("5 mm/h: %.0f sichtbare Einschläge je m² und Sekunde (ab 1 mm)", imp));
        // Tropfenkasten
        Rain r = Rain.moderate();
        r.windSpeed = 8;
        r.eyeX = 100;
        r.eyeY = 12;
        r.eyeZ = -40;
        r.reset();
        Rain.Snapshot s0 = r.snapshot();
        int n = 4096, inside = 0, live = 0;
        double sumD = 0;
        int cntD = 0;
        for (int i = 0; i < n; i++) {
            double[] d = s0.drop(i);
            if (Math.abs(d[0] - r.eyeX) <= s0.box() + 1e-9 && Math.abs(d[2] - r.eyeZ) <= s0.box() + 1e-9 && Math.abs(d[1] - r.eyeY) <= s0.boxY() + 1e-9) inside++;
            if (d[4] > 0) live++;
            sumD += d[3] - Rain.DMIN;
            cntD++;
        }
        check(inside == n, f("alle %d Tropfen im Kasten um das Auge (±%.0f m, ±%.0f m hoch)", n, s0.box(), s0.boxY()));
        double frac = live / (double) n, want = Rain.activeFraction(r.rate);
        check(Math.abs(frac - want) < 0.03, f("es leben %.1f %% der gezeichneten Tropfen, erwartet %.1f %%", frac * 100, want * 100));
        double lam = Rain.lambda(r.rate), span = Rain.DMAX - Rain.DMIN, e = Math.exp(-lam * span);
        double meanTrunc = 1 / lam - span * e / (1 - e);
        check(Math.abs(sumD / cntD - meanTrunc) < 0.04, f("Größen nach Marshall-Palmer: mittlerer Überschuss über 0,5 mm %.3f mm, Formel %.3f mm", sumD / cntD, meanTrunc));
        // fest in der Welt: Auge 1 m weiter, Tropfen bleiben (außer am Rand)
        r.eyeX += 1;
        r.step(0);
        Rain.Snapshot s1 = r.snapshot();
        int same = 0, cmp = 0;
        for (int i = 0; i < 1000; i++) {
            double[] a = s0.drop(i), b = s1.drop(i);
            if (Math.abs(a[0] - (r.eyeX - 1)) > s0.box() - 2) continue;
            cmp++;
            if (Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9 && Math.abs(a[2] - b[2]) < 1e-9) same++;
        }
        check(same == cmp, f("Kasten fest in der Welt: Auge 1 m weiter, %d von %d Tropfen stehen still", same, cmp));
        // fällt und treibt
        r.gust = 0;
        r.step(0);
        Rain.Snapshot a0 = r.snapshot();
        r.step(0.05);
        Rain.Snapshot a1 = r.snapshot();
        double errFall = 0;
        int fallN = 0;
        for (int i = 0; i < 1000; i++) {
            double[] p = a0.drop(i), q = a1.drop(i);
            double dy = q[1] - p[1], dx = q[0] - p[0], dz = q[2] - p[2];
            if (Math.abs(dy) > 1 || Math.abs(dx) > 1 || Math.abs(dz) > 1) continue;   // umgeklappt
            fallN++;
            errFall = Math.max(errFall, Math.max(Math.abs(dy + Rain.terminal(p[3]) * 0.05), Math.max(Math.abs(dx - a1.windX() * 0.05), Math.abs(dz - a1.windZ() * 0.05))));
        }
        check(fallN > 900 && errFall < 1e-6, f("in 0,05 s fällt jeder Tropfen um v(D)·t und treibt mit dem Wind (Abweichung %.1e m, %d Tropfen)", errFall, fallN));
        // Vorhänge
        Rain c = Rain.heavy();
        c.curtain = (x, z) -> x > 0 ? 1 : 0;
        c.reset();
        Rain.Snapshot cs = c.snapshot();
        double cell = cs.mapCell();
        boolean centers = true;
        for (int k = 0; k < 20; k++) {
            double x = cs.mapX0() + (5 + k * 3 + 0.5) * cell, z = cs.mapZ0() + (40 + 0.5) * cell;
            if (Math.abs(cs.curtainAt(x, z) - (x > 0 ? 1 : 0)) > 1e-6) centers = false;
        }
        check(centers && c.rateAt(2000, 0) == c.rate && c.rateAt(-2000, 0) == 0 && cs.curtainAt(0, 0) > 0.2 && cs.curtainAt(0, 0) < 0.8,
                f("Karte der Vorhänge: %d² Zellen zu %.0f m, an den Zellmitten genau, dazwischen weich (an der Kante %.2f)", cs.mapN(), cell, cs.curtainAt(0, 0)));
        int liveE = 0, liveW = 0;
        c.eyeX = 1000;
        c.step(0.6);
        for (int i = 0; i < 4096; i++) if (c.snapshot().drop(i)[4] > 0) liveE++;
        c.eyeX = -1000;
        c.step(0.6);
        for (int i = 0; i < 4096; i++) if (c.snapshot().drop(i)[4] > 0) liveW++;
        check(liveW == 0 && Math.abs(liveE / 4096.0 - Rain.activeFraction(c.rate)) < 0.03,
                f("Tropfen nur unter dem Vorhang: 1 km im Regen leben %d, 1 km daneben im Trockenen %d", liveE, liveW));
        // Unterstand: im Zylinder um das Auge regnet es nicht
        Rain u = Rain.heavy();
        u.eyeX = 0;
        u.eyeY = 5;
        u.eyeZ = 0;
        u.shelter = new double[]{0, 0, 4, 8};
        u.reset();
        int under = 0, outside = 0;
        for (int i = 0; i < 4096; i++) {
            double[] d = u.snapshot().drop(i);
            if (d[4] <= 0) continue;
            if (Math.hypot(d[0], d[2]) < 4 && d[1] < 8) under++;
            else outside++;
        }
        check(under == 0 && outside > 0, f("Unterstand: im Zylinder (4 m, bis 8 m hoch) kein Tropfen, draußen %d", outside));
        // Wolkenuntergrenze und Streuung
        check(c.scatter(2000, 100, 0) > 0 && c.scatter(2000, 900, 0) == 0 && c.scatter(-2000, 100, 0) == 0,
                f("Streudichte im Regen %.2f, über der Wolkenuntergrenze und im Trockenen 0", c.scatter(2000, 100, 0)));
        // Treffer auf Flächen
        Rain hRain = Rain.moderate();
        hRain.windSpeed = 10;
        hRain.windFromDeg = 270;   // aus West: weht nach Ost (+x)
        hRain.reset();
        double up = hRain.expectedHits(0, 0, 1, 0, 1, 0, 1, 1), down = hRain.expectedHits(0, 0, 1, 0, -1, 0, 1, 1);
        double westWall = hRain.expectedHits(0, 0, 1, -1, 0, 0, 1, 1), eastWall = hRain.expectedHits(0, 0, 1, 1, 0, 0, 1, 1);
        check(up > 0 && down == 0 && westWall > 0 && eastWall == 0,
                f("Treffer je m² und s: Dach %.0f, Unterseite 0, Wand nach West %.0f, nach Ost 0", up, westWall));
        Random rnd = new Random(3);
        double mx = 0;
        for (int i = 0; i < 2000; i++) mx = Math.max(mx, hRain.sampleDrop(rnd, 1)[0]);
        check(mx <= Rain.DMAX && mx > 2.5, f("gezogene Tropfen ab 1 mm bis höchstens %.0f mm (größter %.1f mm)", Rain.DMAX, mx));
        // Voreinstellungen, Stufen, Reproduzierbarkeit
        check(Rain.calm().rate < Rain.breeze().rate && Rain.breeze().rate < Rain.gale().rate && Rain.gale().rate < Rain.storm().rate
                && Rain.drizzle().rate < Rain.light().rate && Rain.heavy().rate < Rain.cloudburst().rate, "Voreinstellungen steigen von calm bis storm und von Niesel bis Wolkenbruch");
        check(Rain.beaufort(-3).windSpeed == Rain.beaufort(0).windSpeed && Rain.beaufort(40).rate == Rain.beaufort(12).rate, "beaufort(n) begrenzt auf 0..12");
        boolean q = true;
        Rain.Quality[] qs = Rain.Quality.values();
        for (int i = 1; i < qs.length; i++) if (qs[i].drops <= qs[i - 1].drops || qs[i].box < qs[i - 1].box) q = false;
        check(q, f("Stufen: %,d bis %,d Tropfen im Kasten", qs[0].drops, qs[qs.length - 1].drops));
        Rain x1 = Rain.moderate(), x2 = Rain.moderate(), x3 = Rain.moderate();
        x3.seed = 7;
        for (Rain x : new Rain[]{x1, x2, x3}) { x.reset(); x.step(1.5); }
        double[] d1 = x1.snapshot().drop(123), d2 = x2.snapshot().drop(123), d3 = x3.snapshot().drop(123);
        check(d1[0] == d2[0] && d1[1] == d2[1] && d1[3] == d2[3] && (d1[0] != d3[0] || d1[3] != d3[3]), "gleicher seed, gleicher Regen; anderer seed, anderer");
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        return new ArrayList<>(lines);
    }
}
