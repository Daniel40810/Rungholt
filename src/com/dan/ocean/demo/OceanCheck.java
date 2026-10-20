package com.dan.ocean.demo;

import com.dan.ocean.Fft;
import com.dan.ocean.Obstacle;
import com.dan.ocean.Ocean;
import com.dan.ocean.Ripples;
import com.dan.ocean.SeaState;
import com.dan.ocean.Spectrum;
import com.dan.ocean.Swell;
import com.dan.ocean.gl.RingMesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder
 * mit den Verstößen (Rückgabewert 1). {@link #run()} lässt sich auch aus einer anderen Prüfung aufrufen.
 */
public final class OceanCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        OceanCheck c = new OceanCheck();
        c.run();
        for (String l : c.lines) System.out.println(l);
        System.out.println(c.bad == 0 ? "Alles in Ordnung." : c.bad + " Verstöße.");
        System.exit(c.bad == 0 ? 0 : 1);
    }

    /** Alle Prüfungen; Ergebnis als Zeilen „ok …“ / „FEHLER …“. */
    public List<String> run() {
        fft();
        heights();
        determinism();
        cpuVersusExact();
        velocity();
        foam();
        obstacleField();
        hullMask();
        swell();
        ripples();
        obstacles();
        mesh();
        return lines;
    }

    public int failures() { return bad; }

    /** Seit Haithabu Phase 8: viele Pfähle als Abstandsraster, Brandungsring auch auf dem flachen Noor. */
    void obstacleField() {
        java.util.List<double[]> piles = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) piles.add(new double[]{i * 2.5, 0, 0.14});
        com.dan.ocean.ObstacleField f = com.dan.ocean.ObstacleField.of(piles, -10, -10, 70, 20, 0.5, 6);
        check(f.distance(5, 0) < 0.3 && Math.abs(f.distance(5, 4) - 3.86) < 0.3 && f.distance(5, 9) >= 5.9 && f.distance(500, 0) > 1e8,
                String.format(java.util.Locale.GERMAN, "Abstandsraster: am Pfahl %.2f m, 4 m daneben %.2f m, außerhalb weit", f.distance(5, 0), f.distance(5, 4)));
        // seit Rungholt Phase 2: das Wattenmeer – kurze Streichlänge, flach, weniger See als auf der offenen Nordsee
        Ocean watt = Ocean.watt(6), open = Ocean.beaufort(6);
        check(watt.state().windSeaHs < open.state().windSeaHs * 0.7 && watt.state().windSeaHs > 0.2,
                String.format(java.util.Locale.GERMAN, "Watt bei Bft 6: H_s %.2f m gegen %.2f m auf offener See", watt.state().windSeaHs, open.state().windSeaHs));
        Ocean noor = Ocean.noor(5);
        noor.obstacleField = f;
        double near = 0, far = 0;
        for (int i = 0; i < 16; i++) { near += noor.foamAt(5 + i * 2.5 * 0 + 0.35, i * 0.02) / 16; far += noor.foamAt(30, 8 + i * 0.02) / 16; }
        check(near > far + 0.1, String.format(java.util.Locale.GERMAN, "Brandungsring am Pfahl im Noor bei Bft 5: Schaum %.0f %% gegen %.0f %% abseits", near * 100, far * 100));
    }

    /** Seit Haithabu Phase 9: Umriss eines Rumpfs, innen kein Wasser. */
    void hullMask() {
        com.dan.ocean.HullMask m = new com.dan.ocean.HullMask(10, -5, 90, 11, 2.6);
        // Kurs 90°: der Bug zeigt nach Osten (+x)
        check(m.distance(10, -5) < -2.5 && m.distance(10, -5 + 3.6) > 0.9 && m.distance(10, -5 + 3.6) < 1.1
                        && m.distance(20.5, -5) < 0 && m.distance(22, -5) > 0.9 && m.distance(10, 10) > 12,
                String.format(Locale.GERMAN, "Rumpf in der Wasserlinie: Mitte %.1f m innen, 1 m querab %.2f m außen, Bug am Ende", m.distance(10, -5), m.distance(10, -1.4)));
    }

    void check(boolean c, String what) {
        if (!c) bad++;
        lines.add((c ? "  ok    " : "  FEHLER ") + what);
    }

    void fft() {
        int n = 16;
        Random r = new Random(1);
        double[] re = new double[n * n], im = new double[n * n], er = new double[n * n], ei = new double[n * n];
        for (int i = 0; i < n * n; i++) { re[i] = r.nextGaussian(); im[i] = r.nextGaussian(); }
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                for (int v = 0; v < n; v++)
                    for (int u = 0; u < n; u++) {
                        double a = 2 * Math.PI * (u * x + v * y) / n;
                        er[y * n + x] += re[v * n + u] * Math.cos(a) - im[v * n + u] * Math.sin(a);
                        ei[y * n + x] += re[v * n + u] * Math.sin(a) + im[v * n + u] * Math.cos(a);
                    }
        Fft.transform2d(re, im, n, true);
        double worst = 0;
        for (int i = 0; i < n * n; i++) worst = Math.max(worst, Math.abs(re[i] - er[i]) + Math.abs(im[i] - ei[i]));
        check(worst < 1e-9, String.format(Locale.GERMAN, "FFT 16² gegen die direkte Summe (Fehler %.1e)", worst));
    }

    /** H_s aus der genauen Summe über die ganze große Kachel gegen die Tabelle. */
    void heights() {
        for (int b : new int[]{2, 6, 10}) {
            Ocean o = Ocean.beaufort(b);
            o.swellHeight = 0;
            o.rebuildNow();
            double l0 = o.state().cascades.get(0).length, s = 0, s2 = 0;
            int k = 0, g = 22;
            for (int i = 0; i < g; i++)
                for (int j = 0; j < g; j++) {
                    double h = o.exactWindSea((i + 0.5) * l0 / g, (j + 0.5) * l0 / g, 0)[1];
                    s += h; s2 += h * h; k++;
                }
            double hs = 4 * Math.sqrt(s2 / k - (s / k) * (s / k)), want = SeaState.HS[b];
            check(Math.abs(hs - want) < 0.12 * want, String.format(Locale.GERMAN, "Bft %d: H_s %.2f m im Bild, Tabelle %.2f m", b, hs, want));
        }
        Ocean calm = Ocean.beaufort(0);
        calm.swellHeight = 0;
        calm.rebuildNow();
        check(Math.abs(calm.exactWindSea(10, 10, 5)[1]) < 1e-9, "Bft 0: Windsee spiegelglatt");
        double tp6 = Ocean.beaufort(6).state().peakPeriod, tp10 = Ocean.beaufort(10).state().peakPeriod;
        check(tp6 > 5 && tp6 < 9 && tp10 > tp6, String.format(Locale.GERMAN, "Spitzenperiode Bft 6: %.1f s, Bft 10: %.1f s (Deutsche Bucht, 150 km Streichlänge)", tp6, tp10));
    }

    void determinism() {
        Ocean a = Ocean.beaufort(5), b = Ocean.beaufort(5);
        a.setTime(33);
        b.setTime(33);
        check(a.heightAt(12.5, -40) == b.heightAt(12.5, -40), "gleicher seed, gleiche See");
        b.seed = 2;
        b.rebuildNow();
        b.setTime(33);
        check(a.heightAt(12.5, -40) != b.heightAt(12.5, -40), "anderer seed, andere See");
        double h0 = a.exactWindSea(5, 5, 7)[1], h1 = a.exactWindSea(5, 5, 7 + Spectrum.REPEAT)[1];
        check(Math.abs(h0 - h1) < 1e-6, "Die See läuft nach " + (int) Spectrum.REPEAT + " s ohne Sprung in Schleife");
    }

    /** heightAt (Prozessor, Gitter) gegen die genaue Summe aller Moden an verschobenen Punkten. */
    void cpuVersusExact() {
        for (int b : new int[]{4, 8}) {
            Ocean o = Ocean.beaufort(b);
            o.swellHeight = 0;
            o.rebuildNow();
            o.setTime(42.5);
            Random r = new Random(9);
            double e2 = 0;
            int n = 60;
            for (int i = 0; i < n; i++) {
                double x0 = r.nextDouble() * 400, z0 = r.nextDouble() * 400;
                double[] d = o.exactWindSea(x0, z0, 42.5);
                double h = o.heightAt(x0 + d[0], z0 + d[2]);
                e2 += (h - d[1]) * (h - d[1]);
            }
            double rms = Math.sqrt(e2 / n), hs = o.state().windSeaHs;
            check(rms < 0.03 * hs, String.format(Locale.GERMAN, "Bft %d: heightAt gegen alle Moden, Abweichung %.1f cm (%.1f %% von H_s)", b, rms * 100, rms / hs * 100));
        }
    }

    void velocity() {
        Ocean o = Ocean.beaufort(6);
        o.setTime(10);
        double[] v = o.velocityAt(3, 4);
        Ocean.Sample s0 = o.sample(3, 4);
        double dt = 0.01;
        o.setTime(10 + dt);
        // die Höhe am selben Teilchen (Ruhepunkt) ändert sich mit vy
        Ocean.Sample s1 = o.sample(3 + v[0] * dt, 4 + v[2] * dt);
        double vyNum = (s1.height - s0.height) / dt;
        check(Math.abs(vyNum - v[1]) < 0.05 + 0.05 * Math.abs(v[1]), String.format(Locale.GERMAN, "velocityAt: vy %.3f m/s, aus der Höhe %.3f m/s", v[1], vyNum));
        double[] n = o.normalAt(3, 4);
        check(Math.abs(n[0] * n[0] + n[1] * n[1] + n[2] * n[2] - 1) < 1e-9 && n[1] > 0.3, "normalAt ist ein Einheitsvektor nach oben");
    }

    void foam() {
        double last = -1;
        boolean rising = true;
        for (int b : new int[]{3, 5, 7, 10}) {
            Ocean o = Ocean.beaufort(b);
            double got = o.measuredFoamCoverage(), want = o.state().foamCoverage;
            check(Math.abs(got - want) < Math.max(0.006, 0.25 * want), String.format(Locale.GERMAN, "Bft %d: Schaum %.1f %% der Fläche, Tabelle %.1f %%", b, got * 100, want * 100));
            if (got <= last) rising = false;
            last = got;
        }
        check(rising, "Schaum nimmt mit der Windstärke zu");
        check(Ocean.beaufort(2).measuredFoamCoverage() == 0, "Bft 2: keine Schaumköpfe");
        check(Ocean.streaks(100, 50, 0, 10) == 0 && maxStreak(19) > 0.3, "Schaumstraßen erst ab Bft 7, dann deutlich");
    }

    static double maxStreak(double wind) {
        double m = 0;
        for (int i = 0; i < 400; i++) m = Math.max(m, Ocean.streaks(i * 0.37, i * 1.91, 0.7, wind));
        return m;
    }

    void swell() {
        double[] c = Swell.components(2.0, 10, 270, 10_000);
        double var = 0;
        for (int i = 0; i < Swell.COUNT; i++) var += c[i * Swell.STRIDE + 4] * c[i * Swell.STRIDE + 4] / 2;
        check(Math.abs(4 * Math.sqrt(var) - 2.0) < 1e-9, "Dünung: H_s 2,0 m aus vier Zügen");
        check(c[0] > 0.9, "Dünung aus Westen läuft nach Osten (+x)");
        double l = 2 * Math.PI / c[2];
        check(Math.abs(l - 9.81 * 100 / (2 * Math.PI)) < 2, String.format(Locale.GERMAN, "Wellenlänge bei 10 s im Tiefwasser %.0f m (g·T²/2π = 156 m)", l));
        Ocean o = new Ocean();
        o.windSpeed = 0;
        o.swellHeight = 1.5;
        o.rebuildNow();
        double lo = 9, hi = -9;
        for (int i = 0; i < 2000; i++) { double h = o.heightAt(i * 0.8, i * 0.3); lo = Math.min(lo, h); hi = Math.max(hi, h); }
        check(hi - lo > 1.0 && hi - lo < 4.0, String.format(Locale.GERMAN, "Windstille mit Dünung 1,5 m: Spanne %.2f m", hi - lo));
    }

    void ripples() {
        check(Ripples.ring(0.9, 1.0, 1) != 0 && Ripples.ring(5, 1.0, 1) == Ripples.ring(5, 1.0, 1), "Ringwelle läuft vom Einschlag nach außen");
        check(Ripples.ring(1, Ripples.LIFE + 0.1, 1) == 0, "Ringwelle klingt nach " + Ripples.LIFE + " s ab");
        Ocean o = Ocean.beaufort(0);
        o.swellHeight = 0;
        o.rebuildNow();
        o.addImpulse(0, 0, 1);
        o.step(1.0);
        double peak = 0;
        for (int i = 0; i < 200; i++) peak = Math.max(peak, Math.abs(o.heightAt(i * 0.01, 0)));
        check(peak > 0.005, String.format(Locale.GERMAN, "addImpulse: Ringwelle nach 1 s %.1f cm hoch", peak * 100));
        o.step(Ripples.LIFE);
        check(o.impulses().isEmpty(), "abgelaufene Einschläge werden entfernt");
    }

    void obstacles() {
        Ocean o = Ocean.beaufort(7);
        o.addObstacle(new Obstacle(0, 0, 4.5));
        double near = 0, far = 0;
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI / 18;
            near += o.foamAt(5.0 * Math.cos(a), 5.0 * Math.sin(a));
            far += Ocean.surfRing(30, o.state().totalHs(), 30 * Math.cos(a), 30 * Math.sin(a), 0);
        }
        check(near / 36 > 0.3 && far / 36 < 0.01, String.format(Locale.GERMAN, "Brandungsring: Schaum 0,5 m vor dem Sockel %.0f %%, 25 m davor %.1f %%", near / 36 * 100, far / 36 * 100));
        boolean threw = false;
        try { new Obstacle(0, 0, 0); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "Hindernis ohne Radius wird abgelehnt");
    }

    void mesh() {
        RingMesh m = new RingMesh(200, 256, 0.12, 60_000);
        check(m.downwardTriangles() == 0, "Ringgitter: alle " + m.idx.length / 3 + " Dreiecke von oben gegen den Uhrzeigersinn");
        double rmax = 0;
        for (int i = 0; i < m.verts.length; i += 3) rmax = Math.max(rmax, Math.hypot(m.verts[i], m.verts[i + 1]));
        check(Math.abs(rmax - 60_000) < 1, String.format(Locale.GERMAN, "Ringgitter reicht %.0f km weit (über die Kimm aus 100 m Höhe)", rmax / 1000));
    }
}
