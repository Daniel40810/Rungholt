package com.dan.flock.demo;

import com.dan.flock.WaderMesh;
import com.dan.flock.WaderSpecies;
import com.dan.flock.Waders;

import java.util.Locale;

/**
 * Prüfung der Wattvögel ohne Fenster (seit Rungholt Phase 8, Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“
 * oder mit den Verstößen (Rückgabewert 1): die Arten als Datensätze, ihre Netze, eine Tide auf {@link DemoWatt}
 * (fressen bei Ebbe in ihrem Abstand zur Kante, stochern, vor der Flut auffliegen, rasten, zurück mit der Ebbe), ein
 * Störer, der Schwarm der Alpenstrandläufer mit dem Umschlag, die Wiederholbarkeit und die Zeit.
 */
public final class WaderCheck {
    private static int bad;

    public static void main(String[] args) {
        arten();
        netze();
        tide();
        schwarm();
        wiederholbar();
        zug();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static void arten() {
        System.out.println("Arten");
        check(WaderSpecies.ALL.size() == 7 && WaderSpecies.byKey("Austernfischer") == WaderSpecies.AUSTERNFISCHER, "7 Arten als Datensätze: " + WaderSpecies.ALL);
        check(WaderSpecies.RINGELGANS.presence(1) == 1 && WaderSpecies.RINGELGANS.presence(7) == 0 && WaderSpecies.SAEBELSCHNAEBLER.presence(1) == 0,
                "Ringelgans nur im Winterhalbjahr, Säbelschnäbler nur im Sommerhalbjahr");
        check(WaderSpecies.LACHMOEWE.headIn(6) != WaderSpecies.LACHMOEWE.headIn(12) && WaderSpecies.ALPENSTRANDLAEUFER.summerPatch(6) && !WaderSpecies.ALPENSTRANDLAEUFER.summerPatch(11),
                "Lachmöwe mit brauner Kappe im Sommer, Alpenstrandläufer mit schwarzem Bauchfleck im Brutkleid");
        check(WaderSpecies.ALPENSTRANDLAEUFER.murmur() && WaderSpecies.ALPENSTRANDLAEUFER.groupMax() >= 2000 && WaderSpecies.RINGELGANS.graze(),
                "Alpenstrandläufer wendet im Schwarm (bis " + WaderSpecies.ALPENSTRANDLAEUFER.groupMax() + "), die Ringelgans weidet");
    }

    static void netze() {
        System.out.println("Netze");
        int worst = 0;
        boolean ok = true;
        for (WaderSpecies s : WaderSpecies.ALL) {
            WaderMesh n = WaderMesh.build(s, 0, false), f = WaderMesh.build(s, 1, true);
            worst = Math.max(worst, n.count() / 3);
            float[] v = n.vertices();
            boolean head = false;
            double maxX = 0;
            for (int i = 0; i < v.length; i += WaderMesh.FLOATS) { if (v[i + 9] == 4) head = true; maxX = Math.max(maxX, Math.abs(v[i])); }
            ok &= head && f.count() < n.count() * 0.6 && n.footY < -s.legLength() && Math.abs(maxX * 2 - s.span()) < s.span() * 0.2;
        }
        check(ok, "jede Art: Kopf zum Stochern (Teil 4), Füße unter dem Bauch, Spannweite stimmt, das ferne Netz mit weniger als 60 % der Dreiecke");
        check(worst < 2500, "höchstens " + worst + " Dreiecke je Vogel (nah)");
    }

    /** Eine Tide von Hochwasser zu Hochwasser. */
    static void tide() {
        System.out.println("Eine Tide");
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 9, 3);
        w.t = 0;
        v.settle();
        int birds = v.birds();
        check(birds > 800 && v.groups().size() >= 6, String.format(Locale.GERMAN, "September: %,d Vögel in %d Gruppen", birds, v.groups().size()));
        int[] c0 = v.census();
        check(c0[Waders.Mode.RASTEN.ordinal()] >= birds * 0.9, "Hochwasser: " + c0[Waders.Mode.RASTEN.ordinal()] + " rasten (" + v.describe() + ")");
        double maxFeed = 0, probing = 0, edgeDun = 0, edgeOys = 0;
        int nDun = 0, nOys = 0, wetViol = 0, samples = 0, tFeed = -1;
        boolean flewBefore = false;
        long t0 = System.nanoTime();
        int steps = 0;
        double hwNext = DemoWatt.PERIOD;
        int feedBeforeHw = -1, restAtHw = -1;
        for (double t = 0; t < DemoWatt.PERIOD; t += 5) {
            w.t = t;
            v.step(5);
            steps++;
            int[] c = v.census();
            maxFeed = Math.max(maxFeed, c[Waders.Mode.FRESSEN.ordinal()]);
            if (tFeed < 0 && c[Waders.Mode.FRESSEN.ordinal()] > birds / 2) tFeed = (int) t;
            if (((int) t) % 300 == 0) {
                for (Waders.Group g : v.groups()) {
                    if (g.mode != Waders.Mode.FRESSEN) continue;
                    for (Waders.Bird b : g.birds) {
                        if (b.air) continue;
                        samples++;
                        if (w.depth(b.x, b.z) > g.species.wade() + 0.05) wetViol++;
                        if (b.probe > 0.2) probing++;
                    }
                    double e = v.edgeDistance(g.cx, g.cz, 600);
                    if (g.species == WaderSpecies.ALPENSTRANDLAEUFER && !Double.isInfinite(e)) { edgeDun += e; nDun++; }
                    if (g.species == WaderSpecies.AUSTERNFISCHER && !Double.isInfinite(e)) { edgeOys += e; nOys++; }
                }
            }
            if (Math.abs(t - (hwNext - 1.2 * 3600)) < 3) feedBeforeHw = c[Waders.Mode.FRESSEN.ordinal()];
            if (Math.abs(t - (hwNext - 0.3 * 3600)) < 3) restAtHw = c[Waders.Mode.RASTEN.ordinal()] + c[Waders.Mode.FLIEGEN.ordinal()];
        }
        double ms = (System.nanoTime() - t0) / 1e6 / steps;
        check(tFeed > 0 && tFeed < 5 * 3600, String.format(Locale.GERMAN, "die Hälfte frisst %.1f h nach Hochwasser, höchstens %,.0f zugleich", tFeed / 3600.0, maxFeed));
        check(samples > 1000 && wetViol < samples * 0.03, String.format(Locale.GERMAN, "wer frisst, steht nicht tiefer, als er waten kann (%d von %d)", wetViol, samples));
        check(probing > samples * 0.15, String.format(Locale.GERMAN, "%.0f %% stochern gerade", probing * 100 / Math.max(1, samples)));
        check(nDun > 0 && nOys > 0 && edgeDun / nDun < 40 && edgeOys / nOys > edgeDun / nDun, String.format(Locale.GERMAN,
                "Alpenstrandläufer am Saum (im Mittel %.0f m von der Kante), Austernfischer weiter oben (%.0f m)", edgeDun / Math.max(1, nDun), edgeOys / Math.max(1, nOys)));
        check(restAtHw > birds * 0.85 && v.takeoffs >= v.groups().size(), String.format(Locale.GERMAN,
                "vor der Flut fliegen alle auf: 20 min vor Hochwasser %,d von %,d rasten oder fliegen (eine Stunde vorher fressen noch %,d), %d Abflüge",
                restAtHw, birds, Math.max(0, feedBeforeHw), v.takeoffs));
        check(ms < 6, String.format(Locale.GERMAN, "5 s Szenenzeit für %,d Vögel: %.2f ms", birds, ms));
    }

    /** Ein Störer scheucht die Strandläufer auf; der Schwarm wendet mit dem Umschlag. */
    static void schwarm() {
        System.out.println("Schwarm und Störer");
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 10, 5);
        w.t = DemoWatt.PERIOD / 2;
        v.settle();
        Waders.Group dun = null;
        for (Waders.Group g : v.groups()) if (g.species == WaderSpecies.ALPENSTRANDLAEUFER && g.mode == Waders.Mode.FRESSEN && (dun == null || g.size() > dun.size())) dun = g;
        check(dun != null && dun.size() >= 200, "ein Schwarm Alpenstrandläufer frisst bei Niedrigwasser (" + (dun == null ? 0 : dun.size()) + ")");
        if (dun == null) return;
        v.disturbers.add(new double[]{dun.cx - 40, dun.cz, 1});
        v.step(0.2);
        check(dun.mode == Waders.Mode.SCHWARM && v.alarms > 0, "ein Störer in 40 m: der Schwarm fliegt auf und wendet (" + dun.mode.label + ")");
        v.disturbers.clear();
        // Umschlag: Anteil der Vögel, die gleichsinnig stark geneigt sind (dann zeigt der Schwarm Bäuche oder Rücken)
        int flips = 0, strong = 0, frames = 0;
        double lastSign = 0, maxSpread = 0;
        for (int k = 0; k < 600 && dun.mode == Waders.Mode.SCHWARM; k++) {
            v.step(0.2);
            frames++;
            int pos = 0, neg = 0;
            double minX = 1e9, maxX = -1e9;
            for (Waders.Bird b : dun.birds) { if (b.bank > 0.35) pos++; else if (b.bank < -0.35) neg++; minX = Math.min(minX, b.x); maxX = Math.max(maxX, b.x); }
            maxSpread = Math.max(maxSpread, maxX - minX);
            int n = dun.size();
            double sign = pos > n * 0.6 ? 1 : neg > n * 0.6 ? -1 : 0;
            if (sign != 0) { strong++; if (lastSign != 0 && sign != lastSign) flips++; lastSign = sign; }
        }
        check(strong > frames * 0.1 && flips >= 2, String.format(Locale.GERMAN, "Umschlag: in %d von %d Bildern neigt sich der Schwarm gemeinsam, %d mal von einer Seite zur anderen", strong, frames, flips));
        check(maxSpread > 15 && maxSpread < 400, String.format(Locale.GERMAN, "der Schwarm bleibt zusammen (größte Ausdehnung %.0f m)", maxSpread));
        for (int k = 0; k < 1200 && dun.mode != Waders.Mode.FRESSEN && dun.mode != Waders.Mode.RASTEN; k++) v.step(0.5);
        boolean ground = dun.mode == Waders.Mode.FRESSEN || dun.mode == Waders.Mode.RASTEN;
        for (int k = 0; k < 60; k++) v.step(1);
        int landed = 0;
        for (Waders.Bird b : dun.birds) if (!b.air) landed++;
        check(ground && landed > dun.size() * 0.95 && v.murmurations > 0, "danach landet er wieder (" + dun.mode.label + ", " + landed + " am Boden)");
    }

    static void wiederholbar() {
        System.out.println("Wiederholbar");
        double[] r = new double[2];
        for (int k = 0; k < 2; k++) {
            DemoWatt w = new DemoWatt();
            Waders v = DemoWatt.waders(w, 9, 42);
            w.t = 3 * 3600;
            v.settle();
            for (int i = 0; i < 200; i++) { w.t += 6; v.step(6); }
            double s = 0;
            for (Waders.Group g : v.groups()) for (Waders.Bird b : g.birds) s += b.x * 1.1 + b.z * 0.7 + b.y;
            r[k] = s;
        }
        check(r[0] == r[1], "gleicher Startwert, gleiche Vögel nach 20 min");
    }

    /** Seit Rungholt Phase 9: Ringelgänse ziehen im Oktober in Keilen über das Watt, im Juli nicht. */
    static void zug() {
        System.out.println("Vogelzug");
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 10, 8);
        v.settle();
        int birdsBefore = v.birds();
        double minY = 1e9, maxSpan = 0;
        boolean leaderAhead = true;
        for (int i = 0; i < 6 * 360; i++) {
            w.t += 10; v.step(10);
            for (Waders.Group g : v.skeins()) {
                Waders.Bird lead = g.birds.get(0), last = g.birds.get(g.birds.size() - 1);
                double ahead = (lead.x - last.x) * g.dx + (lead.z - last.z) * g.dz;
                if (g.birds.size() > 4 && ahead < 2) leaderAhead = false;
                for (Waders.Bird b : g.birds) minY = Math.min(minY, b.y - w.ground(b.x, b.z));
                maxSpan = Math.max(maxSpan, Math.hypot(lead.x - last.x, lead.z - last.z));
            }
        }
        check(v.skeinsSeen >= 6 && v.birds() == birdsBefore, String.format(Locale.GERMAN, "Oktober: in 6 h %d Keile Ringelgänse (die Gruppen am Boden bleiben %d Vögel)", v.skeinsSeen, birdsBefore));
        check(minY > 50 && leaderAhead && maxSpan > 10 && maxSpan < 150, String.format(Locale.GERMAN, "im Keil: der Führende vorn, mindestens %.0f m hoch, Keil bis %.0f m lang", minY, maxSpan));
        Waders j = DemoWatt.waders(new DemoWatt(), 7, 8);
        j.settle();
        for (int i = 0; i < 360; i++) j.step(10);
        check(j.skeinsSeen == 0, "Juli: kein Zug");
    }

    private WaderCheck() { }
}
