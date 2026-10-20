package com.dan.spray.demo;

import com.dan.spray.Body;
import com.dan.spray.Parcel;
import com.dan.spray.Spray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10): Flugbahn gegen schrittweises Rechnen, Grenzgeschwindigkeit,
 * Gipfelhöhe mit Reibung, Höhe der Schwälle nach Beaufort, Wiedereintritt, Treffer, Gischtfahnen erst ab Bft 7,
 * Streudichte, Reproduzierbarkeit. Endet mit „Alles in Ordnung.“ oder den Verstößen.
 */
public final class SprayCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        SprayCheck c = new SprayCheck();
        for (String l : c.run()) System.out.println(l);
        System.exit(c.bad == 0 ? 0 : 1);
    }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // Flugbahn: geschlossene Formel gegen kleine Schritte (Euler mit 0,1 ms)
        Parcel p = new Parcel(Parcel.SPLASH, 1, 2, 3, 0, 1.5, 14, -0.5, 6, 0.8, 0, 0, 1, 42, 0.1, -10, 1, 1, 0, 0, -1, 6, -2, 0);
        double[] x = {1, 2, 3}, v = {1.5, 14, -0.5};
        double h = 1e-4;
        for (int i = 0; i < 20_000; i++) {
            double ax = -0.8 * (v[0] - 6), ay = -0.8 * v[1] - Parcel.G, az = -0.8 * (v[2] + 2);
            for (int k = 0; k < 3; k++) x[k] += v[k] * h;
            v[0] += ax * h; v[1] += ay * h; v[2] += az * h;
        }
        double[] c = p.center(2.0);
        double err = Math.max(Math.abs(c[0] - x[0]), Math.max(Math.abs(c[1] - x[1]), Math.abs(c[2] - x[2])));
        check(err < 0.01, f("Flugbahn nach 2 s wie schrittweise gerechnet (Abweichung %.1f mm)", err * 1000));
        double[] late = p.particle(0, 400);
        check(late[4] == 0, "Tropfen unter der Wasserhöhe oder nach Ablauf der Lebensdauer sind tot");
        Parcel slow = new Parcel(Parcel.MIST, 0, 50, 0, 0, 0, 0, 0, 100, 2, 0, 0, 1, 7, 0.1, -1e9, 1, 1, 0, 0, 1, 10, 0, 0);
        double[] pv = slow.particle(0, 30);
        check(Math.abs(pv[5] - 10) < 0.01 && Math.abs(pv[6] + Parcel.G / 2) < 0.01,
                f("Grenzgeschwindigkeit: Wind 10 m/s waagerecht, %.2f m/s Fall (g/k)", -pv[6]));
        // Gipfel mit Reibung
        double v0 = Spray.launchSpeed(20, Spray.SPLASH_K);
        Parcel up = new Parcel(Parcel.SPLASH, 0, 0, 0, 0, 0, v0, 0, 10, Spray.SPLASH_K, 0, 0, 1, 1, 0.1, -1e9, 1, 1, 0, 0, 1, 0, 0, 0);
        double top = 0;
        for (double t = 0; t < 5; t += 0.001) top = Math.max(top, up.center(t)[1]);
        check(Math.abs(top - 20) < 0.02 && v0 > Math.sqrt(2 * Parcel.G * 20), f("Startgeschwindigkeit %.1f m/s für 20 m trotz Reibung (ohne: %.1f m/s), Gipfel %.2f m", v0, Math.sqrt(2 * Parcel.G * 20), top));
        // Tropfen: Streuung, Reproduzierbarkeit
        double[] a1 = p.particle(17, 1.0), a2 = p.particle(17, 1.0), b1 = p.particle(18, 1.0);
        check(a1[1] == a2[1] && a1[1] != b1[1], "gleicher seed und Tropfen, gleiche Lage; anderer Tropfen, andere Lage");
        // Höhe der Schwälle nach Beaufort, an einem Sockel wie dem Roten Sand
        double[] med = new double[13], p90 = new double[13];
        long[] n = new long[13];
        for (int bft : new int[]{3, 5, 7, 9, 11}) {
            Spray s = Spray.beaufort(bft);
            SineSea sea = new SineSea(s.waveHeight, s.windSpeed, 240, 7);
            s.sea = sea;
            s.addBody(new Body(0, 0, -3, 8, 5.25, 4.0));
            s.addBody(new Body(0, 0, 8, 19.6, 3.75, 2.95));
            List<Double> ap = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            double dt = 1 / 30.0;
            for (int i = 0; i < 30 * 240; i++) {
                sea.t += dt;
                s.step(dt);
                for (Parcel q : s.parcels()) if (q.kind() == Parcel.SPLASH && seen.add(q.seed())) ap.add(q.y() + Spray.apex(q.vy(), q.k()));
            }
            Collections.sort(ap);
            n[bft] = ap.size();
            med[bft] = ap.isEmpty() ? 0 : ap.get(ap.size() / 2);
            p90[bft] = ap.isEmpty() ? 0 : ap.get(ap.size() * 9 / 10);
        }
        check(med[5] > 1.5 && med[5] < 4.5, f("Bft 5: Schwälle im Mittel %.1f m über dem Mittelwasser", med[5]));
        check(med[7] > 4.5 && med[7] < 9.5, f("Bft 7: im Mittel %.1f m", med[7]));
        check(p90[9] > 15 && p90[9] < 24, f("Bft 9: jeder zehnte bis %.1f m, an den Dienstraum (19,6 m)", p90[9]));
        check(med[11] > 23, f("Bft 11: im Mittel %.1f m, über die Galerie (23 m)", med[11]));
        check(med[3] < med[5] && med[5] < med[7] && med[7] < med[9] && med[9] < med[11], "je mehr Wind, desto höher");
        // Ereignisse
        Spray s = Spray.beaufort(8);
        SineSea sea = new SineSea(s.waveHeight, s.windSpeed, 240, 9);
        s.sea = sea;
        s.addBody(new Body(0, 0, -3, 8, 5.25, 4.0));
        int[] hits = new int[4], re = new int[1], snd = new int[1];
        double[] hitMax = {-99}, reR = {0};
        s.onHit = (hx, hy, hz, m, sp, k) -> { hits[k]++; hitMax[0] = Math.max(hitMax[0], Math.abs(Math.hypot(hx, hz) - new Body(0, 0, -3, 8, 5.25, 4.0).radiusAt(hy))); };
        s.onReentry = (rx, rz, st) -> { re[0]++; reR[0] = Math.max(reR[0], Math.hypot(rx, rz)); };
        s.onSound = (sx, sy, sz, st) -> snd[0]++;
        double dt = 1 / 30.0;
        for (int i = 0; i < 30 * 120; i++) { sea.t += dt; s.step(dt); }
        long[] tot = s.totals();
        check(hits[Parcel.SPLASH] > 0 && hitMax[0] < 0.01, f("Treffer an der Wand: %d vom Spritzwasser, %d vom Nebel, alle auf der Wand", hits[0], hits[1]));
        check(re[0] > 0 && reR[0] > 5.25, f("%d Bündel fallen zurück ins Meer, alle außerhalb des Körpers (bis %.0f m)", re[0], reR[0]));
        check(snd[0] == tot[0], f("ein Klangereignis je Schwall (%d)", snd[0]));
        check(tot[2] > 0 && s.parcels().size() <= s.quality.maxParcels, f("Bft 8: %d Gischtfahnen, höchstens %d Bündel zugleich", tot[2], s.quality.maxParcels));
        Spray quiet = Spray.beaufort(6);
        SineSea qs = new SineSea(quiet.waveHeight, quiet.windSpeed, 240, 9);
        quiet.sea = qs;
        for (int i = 0; i < 30 * 60; i++) { qs.t += dt; quiet.step(dt); }
        check(quiet.totals()[2] == 0, "Bft 6: keine Gischtfahnen (erst ab Bft 7)");
        // Streudichte
        Spray sc = Spray.beaufort(10);
        sc.sea = new SineSea(sc.waveHeight, sc.windSpeed, 240, 9);
        Body b = new Body(0, 0, -3, 8, 5.25, 4.0);
        sc.addBody(b);
        sc.splash(b, Math.toRadians(240), 0, 15);
        sc.step(1.5);
        double[] mid = sc.parcels().get(0).center(sc.time());
        double near = sc.scatter(mid[0], mid[1], mid[2]), far = sc.scatter(200, 8, 200);
        check(near > 0.2 && far == 0, f("Streudichte im Schwall %.2f, 200 m daneben 0", near));
        // Weißwasser (F32): erst ab schwerer See, am Fuß, zerfällt in Sekunden
        double washTop = -99, washLife = 0;
        for (Parcel q : sc.parcels()) if (q.kind() == Parcel.WASH) { washTop = Math.max(washTop, q.center(sc.time())[1]); washLife = Math.max(washLife, q.life()); }
        check(tot[5] == 0 && sc.totals()[5] >= 3 && washTop < 15 * 0.5 && washLife < 10,
                f("Weißwasser: Bft 8 keins, Bft 10 %d Ballen am Fuß (bis %.1f m nach 1,5 s), längstens %.1f s", sc.totals()[5], washTop, washLife));
        // Reproduzierbar
        check(run(5, 11) == run(5, 11) && run(5, 11) != run(5, 12), "gleicher seed, gleiche Gischt; anderer seed, andere");
        check(Spray.calm().waveHeight < Spray.breeze().waveHeight && Spray.breeze().waveHeight < Spray.gale().waveHeight
                && Spray.gale().waveHeight < Spray.storm().waveHeight, "Voreinstellungen steigen von calm bis storm");
        check(Spray.beaufort(-1).windSpeed == Spray.beaufort(0).windSpeed && Spray.beaufort(30).waveHeight == Spray.beaufort(12).waveHeight, "beaufort(n) begrenzt auf 0..12");
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        return new ArrayList<>(lines);
    }

    static long run(int bft, int seed) {
        Spray s = Spray.beaufort(bft);
        s.seed = seed;
        s.clear();
        SineSea sea = new SineSea(s.waveHeight, s.windSpeed, 240, 3);
        s.sea = sea;
        s.addBody(new Body(0, 0, -3, 8, 5.25, 4.0));
        long h = 17;
        for (int i = 0; i < 300; i++) { sea.t += 1 / 30.0; s.step(1 / 30.0); }
        for (Parcel p : s.parcels()) h = h * 31 + p.seed() + Double.hashCode(p.vy());
        return h;
    }

    private void check(boolean c, String what) {
        if (!c) bad++;
        lines.add((c ? "  ok    " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }
}
