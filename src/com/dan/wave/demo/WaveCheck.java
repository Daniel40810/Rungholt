package com.dan.wave.demo;

import com.dan.wave.Wave;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Seit Phase 8 gegen Handrechnungen: Tempo gegen √(g·h), Erhaltung des Volumens, Auflaufhöhe einer
 * Einzelwelle am flachen Strand gegen Synolakis (1987), Reflexion an einer Wand, Dämpfung am Rand und der Übergang
 * zwischen feinem und grobem Gitter.
 */
public final class WaveCheck {
    private static int bad, all;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        System.out.println("Grundwerte");
        Wave w0 = Wave.lake();
        double c = w0.celerity(100);
        check(Math.abs(c - 31.32) < 0.01, String.format(Locale.GERMAN, "lange Welle bei 100 m Tiefe: %.1f m/s (Werkbuch: gut 30 m/s)", c));
        double dt = w0.cflStep(2, 170);
        check(dt > 0.02 && dt < 0.03, String.format(Locale.GERMAN, "Schritt auf 2 m bei 170 m Tiefe: %.3f s (Werkbuch: rund 0,03 s)", dt));
        check(Wave.STAGE == 8, "wave ist ein Löser (Stufe 8), keine Hülle mehr");

        System.out.println("Tempo");
        celerity();
        System.out.println("Erhaltung");
        volume();
        System.out.println("Auflaufen und Reflexion");
        runup();
        wall();
        System.out.println("Zwei Gitter");
        nested();
        System.out.println("Deichbruch mit gehaltenem Meer (seit Rungholt Phase 12)");
        dambreak();

        System.out.printf(Locale.GERMAN, "%d Prüfungen, %.1f s%n", all, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Ein Hügel in einem Becken von 10 m Tiefe: der Scheitel kommt nach Weg / √(g·h) am Pegel an. */
    static void celerity() {
        Wave w = Wave.basin();
        w.place(0, 0, 0, 0).build((x, z) -> 10);
        Wave.Gauge g = w.gauge("80 m", 80, 0);
        w.addVolume(0, 0, 60, 3);
        double peak = 0, tPeak = 0;
        for (int i = 0; i < 400; i++) {
            w.step(0.05);
            double e = w.levelAt(80, 0);
            if (e > peak) { peak = e; tPeak = w.time(); }
        }
        double hand = 80 / w.celerity(10);
        check(Math.abs(tPeak / hand - 1) < 0.06, String.format(Locale.GERMAN, "Scheitel nach 80 m bei 10 m Tiefe in %.2f s, √(g·h): %.2f s", tPeak, hand));
        check(g.max() > 0 && g.max() < 0.2, String.format(Locale.GERMAN, "Pegel zeichnet auf: größte Höhe %.3f m", g.max()));
    }

    /** Ein Einschlag in ein Becken mit Ufer: das Volumen bleibt, auch wenn Wasser auf- und abläuft. */
    static void volume() {
        Wave w = Wave.basin();
        // Schüssel: in der Mitte 12 m tief, nach außen Land bis 3 m hoch
        w.place(0, 0, 0, 0).build((x, z) -> 12 - 15 * (x * x + z * z) / (120.0 * 120.0));
        w.addVolume(-30, 10, 1500);
        w.step(0.1);
        double v0 = w.volume();
        for (int i = 0; i < 600; i++) w.step(0.1);
        double v1 = w.volume();
        check(Math.abs(v0 - 1500) < 1 && Math.abs(v1 / v0 - 1) < 1e-3,
                String.format(Locale.GERMAN, "Volumen nach 60 s mit Auflaufen: %.1f m³ von %.1f m³ (eingesetzt 1 500 m³)", v1, v0));
        check(w.runupAt(-118, 0) > 0 || w.runupAt(0, 117) > 0 || maxRunup(w) > 0, "Wasser ist an Land gelaufen (Auflaufen)");
    }

    static double maxRunup(Wave w) {
        double m = Double.NaN;
        for (int i = -128; i < 128; i++)
            for (int j = -128; j < 128; j += 4) {
                double r = w.runupAt(i, j);
                if (!Double.isNaN(r)) m = Double.isNaN(m) ? r : Math.max(m, r);
            }
        return m;
    }

    /**
     * Einzelwelle H/d = 0,02 auf einen Strand 1:20 (Rinne 1 m breit gerechnet): Auflaufhöhe nach Synolakis
     * R/d = 2,831·√(cot β)·(H/d)^1,25 für nicht brechende Wellen.
     */
    static void runup() {
        double d = 5, H = 0.1, slope = 1 / 20.0, toe = 300;
        Wave w = Wave.basin();
        w.fineN = 520;
        w.fineNz = 6;
        w.manning = 0;
        w.place(260, 0, 0, 0).build((x, z) -> x < toe ? d : d - (x - toe) * slope);
        double k = Math.sqrt(3 * H / (4 * d * d * d)), x0 = 120, cw = Math.sqrt(9.81 * d);
        w.initial(new Wave.Init() {
            @Override public double eta(double x, double z) { double s = 1 / Math.cosh(k * (x - x0)); return H * s * s; }
            @Override public double qx(double x, double z) { double s = 1 / Math.cosh(k * (x - x0)); double e = H * s * s; return e * cw * (1 + e / (2 * d)); }
        });
        double r = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 1200; i++) {
            w.step(0.05);
            for (double x = toe + d / slope - 10; x < toe + d / slope + 40; x += 1) {
                double ru = w.runupAt(x, 0);
                if (!Double.isNaN(ru)) r = Math.max(r, ru);
            }
        }
        double hand = d * 2.831 * Math.sqrt(1 / slope) * Math.pow(H / d, 1.25);
        check(Math.abs(r / hand - 1) < 0.25, String.format(Locale.GERMAN, "Auflaufen einer Einzelwelle 0,1 m auf 5 m, Strand 1:20: %.2f m, Synolakis: %.2f m", r, hand));
    }

    /** Eine Welle läuft gegen eine senkrechte Wand (Gletscherfront): an der Wand doppelt so hoch, dann zurück. */
    static void wall() {
        double d = 20, H = 0.05;
        Wave w = Wave.basin();
        w.fineN = 400;
        w.fineNz = 6;
        w.manning = 0;
        w.place(200, 0, 0, 0).build((x, z) -> x < 380 ? d : Double.NaN);
        double cw = Math.sqrt(9.81 * d);
        w.initial(new Wave.Init() {
            @Override public double eta(double x, double z) { return H * Math.exp(-Math.pow((x - 150) / 15, 2)); }
            @Override public double qx(double x, double z) { return eta(x, z) * cw; }
        });
        Wave.Gauge atWall = w.gauge("Wand", 379.5, 0), back = w.gauge("zurück", 150, 0);
        for (int i = 0; i < 800; i++) w.step(0.05);
        check(Math.abs(atWall.max() / (2 * H) - 1) < 0.12, String.format(Locale.GERMAN, "an der Wand %.3f m, doppelte Höhe %.3f m", atWall.max(), 2 * H));
        float[][] s = back.series(40);
        double late = 0;
        for (int q = 0; q < s[0].length; q++) if (s[0][q] > 2 * 230 / cw - 2 && !Float.isNaN(s[1][q])) late = Math.max(late, s[1][q]);
        check(late > 0.8 * H, String.format(Locale.GERMAN, "die Welle kommt zurück: %.3f m (ausgesandt %.3f m)", late, H));
    }

    /** Feines Gitter im groben: die Welle läuft hinüber, ohne viel zurückzuwerfen, und der Rand dämpft sie. */
    static void nested() {
        Wave w = Wave.lake();
        w.fineN = 128;
        w.coarseN = 256;
        w.place(0, 0, 0, 0).build((x, z) -> 50);
        Wave.Gauge mid = w.gauge("Mitte", 0, 0), out = w.gauge("draußen", 600, 0);
        w.addVolume(0, 0, 4000, 20);
        double first = 0, rest = 0, tPeak = 0, pk = 0, v10 = 0;
        for (int i = 0; i < 800; i++) {
            w.step(0.05);
            double t = w.time(), e60 = Math.abs(w.levelAt(60, 0)), e600 = w.levelAt(600, 0);
            if (t < 4) first = Math.max(first, e60);
            if (t > 8 && t < 14) rest = Math.max(rest, e60);
            if (e600 > pk) { pk = e600; tPeak = t; }
            if (v10 == 0 && t >= 10) v10 = w.volume();
        }
        double hand = 600 / w.celerity(50);
        check(Math.abs(tPeak / hand - 1) < 0.08, String.format(Locale.GERMAN, "aus dem feinen ins grobe Gitter: Scheitel nach 600 m in %.1f s, √(g·h): %.1f s", tPeak, hand));
        check(rest < 0.1 * first, String.format(Locale.GERMAN, "zurückgeworfen am Übergang: %.4f m gegen %.4f m im ersten Durchgang", rest, first));
        check(Math.abs(v10 / 4000 - 1) < 1e-3, String.format(Locale.GERMAN, "Volumen über beide Gitter nach 10 s: %.1f m³ von 4 000 m³", v10));
        for (int i = 0; i < 1000; i++) w.step(0.05);
        check(w.maxHeight() < 0.05 * first || !w.active(), String.format(Locale.GERMAN, "der Saum am Rand dämpft: nach 90 s %.5f m (anfangs %.4f m)%s", w.maxHeight(), first, w.active() ? "" : ", See ruhig"));
        // Rechenzeit im See (Werkbuch Abschnitt 10)
        Wave big = Wave.lake();
        big.place(0, 0, 0, 0).build((x, z) -> 100);
        big.addVolume(0, 0, 20000);
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) big.step(0.05);
        double ms = (System.nanoTime() - t0) / 1e6 / 5;
        check(ms < 400, String.format(Locale.GERMAN, "512² und 1 024² bei 100 m Tiefe: %.0f ms Rechnung je Sekunde Welle (eigener Faden)", ms));
    }

    /**
     * Dammbruch nach Ritter: links ein Meer 2 m tief, festgehalten (Hold), rechts trockener Grund auf Höhe der Sohle.
     * Durchfluss am Bruch q = (8/27)·√(g h0³) = 2,62 m²/s, Stirn mit 2√(g h0) = 8,9 m/s (ohne Reibung).
     */
    static void dambreak() {
        Wave w = new Wave();
        w.coarseN = 0;
        w.fineN = 200;
        w.fineNz = 40;
        w.fineCell = 1;
        w.spongeCells = 0;
        w.manning = 0;
        w.edgeDamping = 0;
        double h0 = 2;
        w.place(100, 20, 0, 0).build((x, z) -> h0);   // Sohle überall 2 m unter dem Ruhespiegel
        w.initial((x, z) -> x < 60 ? 0 : -h0);         // rechts von x = 60 trocken
        w.hold(new Wave.Hold() {
            public boolean held(double x, double z) { return x < 20; }
            public double eta(double t) { return 0; }
        });
        double v0 = 0, v1 = 0, front = 0;
        for (int k = 0; k < 120; k++) {
            w.step(0.05);
            if (k == 59) v0 = wetVolume(w, 60);
        }
        v1 = wetVolume(w, 60);
        for (double x = 60; x < 200; x += 0.5) if (water(w, x, 20, h0) > 0.01) front = x;
        double q = (v1 - v0) / 3 / 40, ritter = 8.0 / 27 * Math.sqrt(9.81 * h0 * h0 * h0);
        double hb = water(w, 10, 20, h0);
        check(q / ritter > 0.8 && q / ritter < 1.5 && front > 60 + 15 && Math.abs(hb - h0) < 0.05 && w.heldCount() == 20 * 40,
                String.format(Locale.GERMAN, "Durchfluss am Bruch %.2f m²/s (Ritter %.2f), Stirn (über 1 cm) nach 6 s bei %.0f m (Ritter, bis zur Spitze: %.0f m), das gehaltene Meer bleibt %.2f m tief",
                        q, ritter, front - 60, 6 * 2 * Math.sqrt(9.81 * h0), hb));
    }

    /** Wassertiefe über einer Sohle h0 unter dem Ruhespiegel (levelAt ist NaN, wo es trocken ist). */
    static double water(Wave w, double x, double z, double h0) { double l = w.levelAt(x, z); return Double.isNaN(l) ? 0 : Math.max(0, l + h0); }

    /** Wasser rechts von x0 (m³) im feinen Gitter (1 m Zellen, 40 m breit). */
    static double wetVolume(Wave w, double x0) {
        double v = 0;
        for (double x = x0 + 0.5; x < 200; x += 1) for (double z = 0.5; z < 40; z += 1) v += water(w, x, z, 2);
        return v;
    }

    static void check(boolean c, String what) {
        all++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private WaveCheck() { }
}
