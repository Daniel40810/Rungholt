package com.dan.fog.demo;

import com.dan.fog.Fog;
import com.dan.fog.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“. Geprüft werden Sicht
 * nach Koschmieder, die Eichung der Bedeckung, Höhe und Obergrenze, das Ziehen mit dem Wind, Presets nach Beaufort,
 * Reproduzierbarkeit, weiche Ränder, die Kennungen der Nebelsignale und dass sie nur im Nebel tönen.
 */
public final class FogCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new FogCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Sicht und Dichte
        Fog d = Fog.dense();
        d.wisps = 0;
        d.step(0);
        double vis = d.visibilityAt(100, 3, -200);
        double tr = d.transmittance(0, 3, 0, vis, 3, 0);
        check(Math.abs(vis - d.visibilityM) < 0.5 && Math.abs(tr - 0.05) < 0.005,
                f("Koschmieder: geschlossener Nebel %.0f m → Sicht %.1f m, Durchlässigkeit über die Sicht %.3f (5 %%)", d.visibilityM, vis, tr));
        double topHere = d.topAt(100, -200);
        double hi = topHere * (1 + d.softness * 0.5) + 0.5;
        check(d.density(100, hi, -200) == 0 && d.density(100, 1, -200) > 0 && Math.abs(topHere / d.top - 1) <= 0.25 + 1e-9,
                f("Obergrenze am Ort %.0f m (Mittel %.0f m, ±25 %%): darüber klare Luft, darunter Nebel", topHere, d.top));
        double tmin = 1e9, tmax = 0;
        for (int i = 0; i < 400; i++) { double t = d.topAt(i * 137.0, i * -91.0); tmin = Math.min(tmin, t); tmax = Math.max(tmax, t); }
        check(tmin >= d.top * 0.75 - 1e-6 && tmax <= d.top * 1.25 + 1e-6 && tmax - tmin > d.top * 0.3,
                f("Obergrenze wellt sich: %.0f bis %.0f m an 400 Orten", tmin, tmax));

        // ------------------------------------------------ Eichung der Bedeckung
        StringBuilder cs = new StringBuilder();
        boolean calOk = true;
        for (double c : new double[]{0.2, 0.45, 0.7}) {
            Fog b = Fog.banks();
            b.coverage = c;
            b.seed = 3;
            b.step(0);
            double m = b.measuredCoverage();
            calOk &= Math.abs(m - c) < 0.07;
            cs.append(cs.length() > 0 ? ", " : "").append(f("%.0f → %.0f %%", c * 100, m * 100));
        }
        check(calOk, "Bedeckung nach Eichtabelle (gewollt → gemessen über 24 km): " + cs);

        // ------------------------------------------------ weiche Ränder
        Fog b = Fog.banks();
        b.wisps = 0;
        b.step(0);
        double maxJump = 0;
        for (int i = 0; i < 4000; i++) {
            double x = i * 5.0 - 10_000;
            maxJump = Math.max(maxJump, Math.abs(b.bankAt(x + 5, 300) - b.bankAt(x, 300)));
        }
        check(maxJump < 0.35, f("Ränder der Bänke weich: größter Sprung der Bank auf 5 m %.2f", maxJump));

        // ------------------------------------------------ Ziehen mit dem Wind
        Fog w = Fog.banks();
        w.windSpeed = 5;
        w.windFromDeg = 270;     // Westwind: zieht nach Osten (+x)
        w.wisps = 0;
        w.step(0);
        double x0 = 1234, z0 = -567;
        double before = w.bankAt(x0, z0);
        for (int i = 0; i < 100; i++) w.step(1);
        double moved = 100 * 5 * Fog.DRIFT;
        double after = w.bankAt(x0 + moved, z0);
        check(Math.abs(after - before) < 1e-6, f("Bänke ziehen mit 80 %% des Winds: nach 100 s bei 5 m/s aus West %.0f m weiter östlich dieselbe Bank (%.3f / %.3f)", moved, before, after));

        // ------------------------------------------------ Presets, seed
        Fog p1 = Fog.beaufort(2), p2 = Fog.beaufort(2), p3 = Fog.beaufort(2);
        p3.seed = 99;
        p1.step(0); p2.step(0); p3.step(0);
        boolean same = true, diff = false;
        for (int i = 0; i < 50; i++) {
            double x = i * 311.0, z = i * -173.0;
            same &= p1.density(x, 5, z) == p2.density(x, 5, z);
            diff |= p1.density(x, 5, z) != p3.density(x, 5, z);
        }
        check(same && diff, "gleicher seed, gleiche Bänke; anderer seed, andere");
        check(Fog.beaufort(2).amount == 1 && Fog.beaufort(5).amount < 1 && Fog.beaufort(6).amount < 0.2 && Fog.beaufort(8).amount == 0 && Fog.calm().amount == 1 && Fog.storm().amount == 0,
                "Presets nach Beaufort: Nebel bis Bft 3, Fetzen bis 5, ab 7 reißt ihn der Wind auf");
        Fog sh = Fog.shallow();
        sh.coverage = 1;
        sh.wisps = 0;
        sh.step(0);
        check(sh.density(0, 3, 0) > 0 && sh.density(0, 28.3, 0) == 0,
                f("flacher Seenebel bis %.0f m: am Fuß eines Turms dicht (Sicht %.0f m), an der Laterne in 28 m klar", sh.topAt(0, 0), sh.visibilityAt(0, 3, 0)));
        // Untergrenze (seit Haithabu Phase 3): der Nebel hebt ab, unten wird es klar, oben bleibt die Schicht
        Fog lift = Fog.dense();
        lift.coverage = 1;
        lift.wisps = 0;
        lift.step(0);
        double dense0 = lift.density(0, 1, 0), mid0 = lift.density(0, 40, 0);
        lift.base = 30;
        lift.step(0);
        check(dense0 > 0 && lift.density(0, 1, 0) == 0 && Math.abs(lift.density(0, 40, 0) - mid0) < 1e-12 && lift.density(0, 25, 0) > 0
                        && lift.density(0, 25, 0) < mid0,
                f("Untergrenze 30 m: am Wasser klar (vorher Sicht %.0f m), in 40 m unverändert, weicher Übergang darunter", lift.visibilityAt(0, 40, 0)));

        // ------------------------------------------------ Nebelsignale
        Signal r = new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0);
        Signal g2 = new Signal("Feuerschiff", "Horn(2) 20s", 0, 0, 0);
        Signal ty = new Signal("Schiff", "Tyfon 120s", 0, 0, 0);
        check(r.blasts.size() == 3 && r.blasts.get(0).seconds() == 1 && r.blasts.get(1).seconds() == 3 && r.blasts.get(2).seconds() == 1 && r.onSeconds() == 5 && r.period == 30,
                "Kennung " + r.describe());
        check(g2.blasts.size() == 2 && g2.onSeconds() == 4 && ty.blasts.size() == 1 && ty.onSeconds() == 5 && ty.kind == Signal.Kind.TYFON,
                g2.describe() + "; " + ty.describe());
        String e1 = "", e2 = "";
        try { new Signal("x", "Horn Mo(SOS) 10s", 0, 0, 0); } catch (IllegalArgumentException ex) { e1 = ex.getMessage(); }
        try { new Signal("x", "Glocke rund", 0, 0, 0); } catch (IllegalArgumentException ex) { e2 = ex.getMessage(); }
        check(e1.contains("passen nicht") && e2.contains("nicht lesbar"), "falsche Kennungen werden gemeldet („" + e1 + "“)");

        Fog clear = Fog.banks();
        clear.amount = 0;
        Signal r2 = new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0);
        clear.signals.add(r2);
        int[] heard = new int[1];
        clear.onHorn = (x, y, z, s, sec, sig) -> heard[0]++;
        for (int i = 0; i < 120; i++) clear.step(1);
        int clearHeard = heard[0];
        Fog fogged = Fog.dense();
        fogged.top = 120;
        Signal r3 = new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0);
        fogged.signals.add(r3);
        heard[0] = 0;
        double[] dur = new double[1];
        fogged.onHorn = (x, y, z, s, sec, sig) -> { heard[0]++; dur[0] += sec; };
        for (int i = 0; i < 1200; i++) fogged.step(0.1);
        check(clearHeard == 0 && heard[0] == 12 && Math.abs(dur[0] - 20) < 1e-9 && r3.active(),
                f("Nebelhorn tönt nur im Nebel: klar 0 Töne, in 120 s dichtem Nebel %d Töne mit zusammen %.0f s (4 × kurz-lang-kurz)", heard[0], dur[0]));
        check(r3.soundingAt(0.5) && !r3.soundingAt(1.5) && r3.soundingAt(2.5) && !r3.soundingAt(9),
                "Takt: Ton bei 0,5 s und 2,5 s, Pause bei 1,5 s und 9 s");

        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Prüfungen fehlgeschlagen.");
        return lines;
    }

    public int failures() { return bad; }
}
