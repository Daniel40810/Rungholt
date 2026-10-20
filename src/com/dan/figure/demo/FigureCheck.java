package com.dan.figure.demo;

import com.dan.figure.FigureMesh;
import com.dan.figure.Hike;
import com.dan.figure.Outfit;
import com.dan.figure.Person;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Prüfung der Figuren ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1): Kleidung nach dem Monat, das Netz (Größe, Teile, Dreiecke), die Haltung (Füße auf dem Boden beim
 * Stehen, Gehen und Bücken, Schrittlänge, der Standfuß gleitet nicht), eine Wanderung auf {@link DemoFlat} (Kolonne,
 * Halte im Halbkreis, Führer bückt sich und zeigt, Spuren, kein Wasser über die Knöchel, ankommen), Warten vor dem
 * Priel, die Wiederholbarkeit und die Zeit.
 */
public final class FigureCheck {
    private static int bad;

    /** Seit Rungholt Phase 11: Kleidung von 1362. */
    static void mittelalter() {
        SplittableRandom r = new SplittableRandom(1362);
        int hats = 0, bare = 0, packs = 0, n = 200;
        boolean kneeIsTunic = true;
        for (int i = 0; i < n; i++) {
            Outfit o = Outfit.medieval(7, r, false);
            if (o.hat) hats++;
            if (o.barefoot) bare++;
            if (o.pack) packs++;
            kneeIsTunic &= o.color(Outfit.KNEE)[0] == o.color(Outfit.JACKET)[0];
        }
        int bareJan = 0;
        for (int i = 0; i < n; i++) if (Outfit.medieval(1, r, false).barefoot) bareJan++;
        check(packs == 0 && kneeIsTunic && hats > 40 && bare > 30 && bareJan == 0, String.format(Locale.GERMAN,
                "Kleidung 1362: Kittel bis über das Knie, %d von %d mit Gugel oder Haube, im Juli %d barfuß, im Januar keiner, kein Rucksack", hats, n, bare));
    }

    public static void main(String[] args) {
        kleidung();
        netz();
        haltung();
        wanderung();
        priel();
        wiederholbar();
        mittelalter();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static void kleidung() {
        System.out.println("Kleidung");
        SplittableRandom r = new SplittableRandom(3);
        int bareJul = 0, bootsJan = 0, hatJan = 0, n = 200;
        for (int i = 0; i < n; i++) {
            if (Outfit.forMonth(7, r, false, false).barefoot) bareJul++;
            Outfit w = Outfit.forMonth(1, r, false, false);
            if (!w.barefoot && w.label.contains("Stiefel")) bootsJan++;
            if (w.hat) hatJan++;
        }
        check(bareJul > n * 0.6 && bootsJan == n && hatJan == n, String.format(Locale.GERMAN, "Juli: %d von %d barfuß; Januar: alle in Stiefeln und mit Mütze", bareJul, n));
        Outfit g = Outfit.forMonth(7, r, true, false);
        check(g.pack && g.label.startsWith("Wattführer"), "der Wattführer hat immer einen Rucksack: " + g);
    }

    static void netz() {
        System.out.println("Netz");
        FigureMesh m = FigureMesh.build(12), f = FigureMesh.build(6);
        float[] v = m.vertices();
        double top = 0, bottom = 9;
        boolean parts = true;
        int[] seen = new int[15];
        for (int i = 0; i < v.length; i += FigureMesh.FLOATS) {
            int part = (int) v[i + 7];
            if (part < 0 || part > 14) parts = false; else seen[part]++;
            if (part != 12 && part != 11) top = Math.max(top, v[i + 1]);
            bottom = Math.min(bottom, v[i + 1]);
        }
        for (int s : seen) parts &= s > 0;
        check(Math.abs(top - 1.76) < 0.03 && Math.abs(bottom) < 1e-6 && parts, String.format(Locale.GERMAN, "ohne Mütze %.2f m groß, Sohle bei 0, alle 15 Teile", top));
        check(m.count() / 3 < 4000 && f.count() < m.count() * 0.5, String.format(Locale.GERMAN, "%,d Dreiecke nah, %,d fern", m.count() / 3, f.count() / 3));
    }

    /** Tiefster Punkt der Füße und Höhe des Scheitels in einer Haltung. */
    static double[] measure(FigureMesh m, double[] p) {
        float[] v = m.vertices();
        double low = 9, head = -9;
        for (int i = 0; i < v.length; i += FigureMesh.FLOATS) {
            int part = (int) v[i + 7];
            double[] q = FigureMesh.transform(v[i], v[i + 1], v[i + 2], part, p);
            if (part == 5 || part == 6 || part == 13 || part == 14) low = Math.min(low, q[1]);
            if (part == 2) head = Math.max(head, q[1]);
        }
        return new double[]{low, head};
    }

    static void haltung() {
        System.out.println("Haltung");
        FigureMesh m = FigureMesh.build(8);
        double[] st = measure(m, FigureMesh.pose(0, 0, 0, 0));
        check(Math.abs(st[0]) < 0.005, String.format(Locale.GERMAN, "im Stehen steht der Fuß auf dem Boden (%.3f m)", st[0]));
        double worst = 0, headMin = 9, headMax = 0;
        for (int k = 0; k < 40; k++) {
            double[] w = measure(m, FigureMesh.pose(k / 40.0, 1, 0, 0));
            worst = Math.max(worst, Math.abs(w[0]));
            headMin = Math.min(headMin, w[1]); headMax = Math.max(headMax, w[1]);
        }
        check(worst < 0.03 && headMax - headMin < 0.07, String.format(Locale.GERMAN, "beim Gehen bleibt ein Fuß am Boden (höchstens %.3f m daneben), der Kopf wippt %.0f mm", worst, (headMax - headMin) * 1000));
        double[] b = measure(m, FigureMesh.pose(0, 0, 1, 0));
        check(Math.abs(b[0]) < 0.03 && st[1] - b[1] > 0.35, String.format(Locale.GERMAN, "gebückt: Kopf %.2f m tiefer, Füße am Boden", st[1] - b[1]));
        double stride = FigureMesh.stride(1, 1);
        // der Standfuß gleitet nicht: in der Stützphase wandert er um eine Schrittlänge nach hinten
        double[] p0 = FigureMesh.pose(0.25, 1, 0, 0), p1 = FigureMesh.pose(0.75, 1, 0, 0);
        double travel = FigureMesh.footForward(p0[0], p0[2]) - FigureMesh.footForward(p1[0], p1[2]);
        check(stride > 0.6 && stride < 0.85 && Math.abs(travel - stride) < 0.12 * stride, String.format(Locale.GERMAN,
                "Schrittlänge %.2f m (Doppelschritt %.2f m, bei 1,25 m/s %.1f Schritte je Sekunde); der Fuß wandert in der Stütze %.2f m", stride, 2 * stride, 1.25 / stride, travel));
    }

    static void wanderung() {
        System.out.println("Wanderung");
        DemoFlat w = new DemoFlat();
        Hike h = new Hike(DemoFlat.route(), 13, 7, 11);
        h.stops.add(new Hike.Stop(380, 240, "Wattwurm"));
        h.stops.add(new Hike.Stop(850, 180, "Priel"));
        int[] prints = new int[2];
        double maxSpread = 0, gatherWorst = 0, bend = 0, point = 0;
        long t0 = System.nanoTime();
        int n = 0;
        while (h.state != Hike.State.ANGEKOMMEN && h.time < 4 * 3600) {
            h.step(1, w, (x, z, dx, dz, bare, s) -> prints[bare ? 1 : 0]++);
            n++;
            if (h.state == Hike.State.GEHEN && h.s > 50) maxSpread = Math.max(maxSpread, h.spread());
            if (h.state == Hike.State.HALT) {
                bend = Math.max(bend, h.guide.bend);
                point = Math.max(point, h.guide.point);
                if (h.currentStop() != null && h.time % 10 < 1) {
                    double far = 0;
                    for (Person p : h.people()) far = Math.max(far, Math.hypot(p.x - h.guide.x, p.z - h.guide.z));
                    if (far > gatherWorst && h.stopElapsed() > 60) gatherWorst = far;
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6 / n;
        double walk = h.length() / h.guideSpeed + 420;
        check(h.state == Hike.State.ANGEKOMMEN && h.time < walk * 1.4, String.format(Locale.GERMAN, "angekommen nach %.0f min (%.1f km, zwei Halte; ohne Schlick und Warten %.0f min)", h.time / 60, h.length() / 1000, walk / 60));
        check(maxSpread > 10 && maxSpread < 70, String.format(Locale.GERMAN, "unterwegs eine lockere Kolonne, höchstens %.0f m lang", maxSpread));
        check(h.halts == 2 && gatherWorst > 2 && gatherWorst < 11, String.format(Locale.GERMAN, "an den Halten stehen alle im Halbkreis vor dem Führer (höchstens %.1f m weg)", gatherWorst));
        check(bend > 0.9 && point > 0.9, "der Führer bückt sich (Wattwurm) und zeigt in die Weite");
        check(prints[0] + prints[1] > 20_000 && prints[1] > prints[0], String.format(Locale.GERMAN, "%,d Fußspuren, davon %,d barfuß", prints[0] + prints[1], prints[1]));
        check(h.wettest < 0.05, String.format(Locale.GERMAN, "bei Niedrigwasser bleibt der Weg trocken (tiefstes Wasser am Fuß %.2f m)", h.wettest));
        check(ms < 1.0, String.format(Locale.GERMAN, "1 s Szenenzeit für %d Personen: %.3f ms", h.count(), ms));
    }

    static void priel() {
        System.out.println("Priel");
        DemoFlat w = new DemoFlat();
        w.level = -0.5;   // der Priel führt Wasser (bis 0,8 m)
        Hike h = new Hike(DemoFlat.route(), 6, 9, 5);
        for (int i = 0; i < 3600 && h.state != Hike.State.WARTEN; i++) h.step(1, w, null);
        check(h.state == Hike.State.WARTEN && h.s < 930 - 5 && h.wettest < 0.3, String.format(Locale.GERMAN, "vor dem vollen Priel wartet die Gruppe (%.0f m vor der Mitte, tiefstes Wasser am Fuß %.2f m)", 930 - h.s, h.wettest));
        w.level = -1.5;
        for (int i = 0; i < 60 && h.state == Hike.State.WARTEN; i++) h.step(1, w, null);
        check(h.state == Hike.State.GEHEN, "fällt der Priel trocken, geht sie weiter");
    }

    static void wiederholbar() {
        System.out.println("Wiederholbar");
        double[] r = new double[2];
        for (int k = 0; k < 2; k++) {
            Hike h = new Hike(DemoFlat.route(), 9, 7, 42);
            DemoFlat w = new DemoFlat();
            for (int i = 0; i < 600; i++) h.step(1, w, null);
            double s = 0;
            for (Person p : h.people()) s += p.x * 1.3 + p.z * 0.7 + p.phase;
            r[k] = s;
        }
        check(r[0] == r[1], "gleicher Startwert, gleiche Gruppe nach 10 min");
    }

    private FigureCheck() { }
}
