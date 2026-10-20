package com.dan.seal.demo;

import com.dan.seal.Colony;
import com.dan.seal.Seal;
import com.dan.seal.SealBody;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Seit Phase 7: der Körper (Länge bleibt bei jeder Biegung, Bauch auf dem Grund, Kopf über dem
 * Wasser), eine Tide an der Bank aus {@link DemoBank} (kommen mit dem fallenden Wasser, liegen auf dem Trockenen mit
 * Abstand und dem Kopf zum Wasser, gehen mit der Flut), die Flucht vor einem Wanderer und die Rückkehr, Heuler im Juni,
 * Spuren, Rufe und die Wiederholbarkeit.
 */
public final class SealCheck {
    private static int bad;

    public static void main(String[] args) {
        regeln();
        koerper();
        tide();
        flucht();
        heuler();
        wiederholbar();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static void regeln() {
        System.out.println("Regeln");
        Seal s = Seal.harbourSeal();
        check(s.pupSeason(6) && s.pupSeason(7) && !s.pupSeason(8) && !s.pupSeason(12), "Seehund: Heuler im Juni und Juli");
        Seal k = Seal.greySeal();
        check(k.pupSeason(12) && k.pupSeason(1) && !k.pupSeason(6), "Kegelrobbe: Junge im Winter, über den Jahreswechsel");
        check(s.stays(0, 1000) && !s.stays(0.5, 1000) && !s.stays(0, 100), "liegt bei trockener Bank ohne Störer, geht bei Wasser oder Störung");
        check(Seal.Pose.values().length == 6 && s.maxAnimals == 60, "sechs Haltungen, höchstens 60 Tiere");
        check(s.animalsFor(Seal.Quality.LOW) < s.animalsFor(Seal.Quality.MEDIUM) && s.animalsFor(Seal.Quality.MEDIUM) < s.animalsFor(Seal.Quality.HIGH)
                && s.animalsFor(Seal.Quality.ULTRA) == 60, String.format("Tiere je Stufe: %d, %d, %d, %d", s.animalsFor(Seal.Quality.LOW), s.animalsFor(Seal.Quality.MEDIUM), s.animalsFor(Seal.Quality.HIGH), s.animalsFor(Seal.Quality.ULTRA)));
        check(s.describe().startsWith("seal: Stufe 7"), s.describe());
    }

    static void koerper() {
        System.out.println("Körper");
        float[][] ps = SealDemo.poses();
        double worst = 0;
        for (float[] p : ps) worst = Math.max(worst, Math.abs(SealBody.spineLength(p) - SealBody.END));
        check(worst < 0.01, String.format(Locale.GERMAN, "Wirbelsäule behält ihre Länge in allen %d Haltungen (größte Abweichung %.1f %%)", ps.length, worst / SealBody.END * 100));
        int tri = SealBody.triangles();
        check(tri > 1000 && tri < 3000, tri + " Dreiecke je Tier (60 Tiere: " + tri * 60 + ")");
        double low = SealBody.lowest(ps[0]);
        check(low < -0.07 && low > -0.13, String.format(Locale.GERMAN, "liegend reicht der Bauch %.0f cm unter den Anker (1,6 m Tier: %.0f cm)", -low * 100, -low * 160));
        double h0 = SealBody.head(ps[0])[1], h1 = SealBody.head(ps[1])[1], hb = SealBody.head(ps[2])[1];
        check(h1 > h0 + 0.05 && hb > h0, String.format(Locale.GERMAN, "Kopf heben hebt den Kopf um %.0f cm (1,6 m Tier), Banane um %.0f cm", (h1 - h0) * 160, (hb - h0) * 160));
        double[] nose = SealBody.deform(0, 0, 0.004, 0, 0, 1, 0, 0, ps[0]), tail = SealBody.deform(0, 0, 0, 0, 0, 1, SealBody.END, 0, ps[0]);
        check(nose[2] > 0.4 && tail[2] < -0.4, String.format(Locale.GERMAN, "Nase vorn (z %+.2f), Schwanz hinten (z %+.2f)", nose[2], tail[2]));
        // Normale zeigt nach außen: oben auf dem Rücken nach oben
        float[] mesh = SealBody.mesh();
        int up = 0, n = 0;
        for (int i = 0; i < mesh.length; i += SealBody.FLOATS)
            if (mesh[i + 7] == 0 && mesh[i + 6] > 0.3 && mesh[i + 6] < 0.6 && Math.abs(mesh[i]) < 0.02 && mesh[i + 1] > 0.05) { n++; if (mesh[i + 4] > 0.8) up++; }
        check(n > 0 && up == n, "Normalen auf dem Rücken zeigen nach oben (" + up + " von " + n + ")");
    }

    static Colony colony(DemoBank b, int count, int month, long seed) {
        Seal s = Seal.harbourSeal();
        s.seed = seed;
        Colony c = new Colony(s);
        c.world = b;
        c.banks.add(DemoBank.bank());
        c.count = count;
        c.month = month;
        c.sunAltDeg = 40;
        return c;
    }

    /** Eine Tide von Hochwasser zu Hochwasser. */
    static void tide() {
        System.out.println("Eine Tide an der Bank");
        DemoBank b = new DemoBank();
        Colony c = colony(b, 30, 9, 3);
        b.t = 0;
        c.settle();
        check(c.lying() == 0 && !c.bankAvailable(), "Hochwasser: die Bank ist überflutet, alle im Wasser (" + c.describe() + ")");
        double tDry = -1, tFirst = -1, tCover = -1, tGone = -1, maxLying = 0;
        int dryViol = 0, samples = 0, spacingViol = 0, facing = 0, faced = 0;
        double farMax = 0;
        int near = 0, nearN = 0;
        long t0 = System.nanoTime();
        int steps = 0;
        for (double t = 0; t < DemoBank.PERIOD; t += 10) {
            b.t = t;
            c.step(10);
            steps++;
            if (tDry < 0 && c.bankAvailable()) tDry = t;
            if (tFirst < 0 && c.lying() > 0) tFirst = t;
            maxLying = Math.max(maxLying, c.lying());
            if (tDry > 0 && tCover < 0 && t > DemoBank.PERIOD / 2 && c.bankShare() == 0) tCover = t;
            if (tCover > 0 && tGone < 0 && c.lying() == 0) tGone = t;
            if (((int) t) % 600 == 0) {
                for (Colony.Animal a : c.animals()) {
                    if (a.act != Colony.Activity.LIEGEN) continue;
                    samples++;
                    if (c.depth(a.x, a.z) > c.seal.leaveDepth + 0.02) dryViol++;
                    if (!a.pup) {
                        for (Colony.Animal o : c.animals()) if (o != a && !o.pup && o.act == Colony.Activity.LIEGEN && Math.hypot(o.x - a.x, o.z - a.z) < 0.8 * c.seal.spacing * a.length) spacingViol++;
                        double dw = c.distToWater(a.x, a.z, 200);
                        farMax = Math.max(farMax, dw);
                        nearN++;
                        if (dw <= 30) near++;
                    }
                }
            }
            if (Math.abs(t - 3.0 * 3600) < 5) for (Colony.Animal a : c.animals()) if (a.act == Colony.Activity.LIEGEN && !a.pup) {
                faced++;
                double[] wd = waterDir(c, a.x, a.z);
                if (wd != null && Math.sin(a.heading) * wd[0] - Math.cos(a.heading) * wd[1] > -0.1) facing++;
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6 / steps;
        int adults = c.animals().size() - c.pups();
        check(tDry > 0 && tFirst >= tDry && tFirst < tDry + 50 * 60, String.format(Locale.GERMAN, "die Bank fällt %.1f h nach Hochwasser trocken, die ersten liegen %.0f min später", tDry / 3600, (tFirst - tDry) / 60));
        check(maxLying >= adults * 0.6, String.format(Locale.GERMAN, "bei Ebbe liegen bis zu %.0f von %d auf der Bank (ein Teil bleibt zum Fressen im Wasser)", maxLying, adults));
        check(tCover > 0 && tGone > 0 && tGone - tCover < 10 * 60, String.format(Locale.GERMAN, "mit der Flut: Bank %.1f h nach Hochwasser ganz überflutet, %.0f min danach liegt keiner mehr", tCover / 3600, Math.max(0, tGone - tCover) / 60));
        check(dryViol == 0 && samples > 50, "wer liegt, liegt auf dem Trockenen (" + samples + " Stichproben, " + dryViol + " im Wasser)");
        check(spacingViol == 0, "Abstand zum Nachbarn mindestens 0,8 × " + c.seal.spacing + " Körperlängen (" + spacingViol / 2 + " zu nah)");
        check(near >= nearN * 0.8 && farMax < 90, String.format(Locale.GERMAN, "liegen nahe am Wasser: %d von %d Stichproben in 30 m (weitestes Tier %.0f m, es rückt nach)", near, nearN, farMax));
        check(faced > 5 && facing >= faced * 0.7, "mit dem Kopf zum Wasser: " + facing + " von " + faced);
        check(c.haulOuts >= adults * 0.6 && c.departures > 0, "an Land: " + c.haulOuts + ", nachgerückt: " + c.moves + ", mit der Flut gegangen: " + c.departures);
        check(c.tracks > c.haulOuts * 5 && c.tracks < (c.haulOuts + c.moves) * 200, String.format(Locale.GERMAN, "Spuren im Watt: %d (im Mittel %.0f m trocken gerobbt je Weg an Land oder Nachrücken)", c.tracks, c.tracks * 0.35 / Math.max(1, c.haulOuts + c.moves)));
        check(ms < 5, String.format(Locale.GERMAN, "10 s Szenenzeit für %d Tiere: %.2f ms", c.animals().size(), ms));
    }

    static double[] waterDir(Colony c, double x, double z) {
        for (double r = 3; r < 80; r *= 1.4) {
            double sx = 0, sz = 0;
            int n = 0;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                if (c.depth(x + Math.cos(a) * r, z + Math.sin(a) * r) > Colony.WATER) { sx += Math.cos(a); sz += Math.sin(a); n++; }
            }
            if (n > 0) { double l = Math.hypot(sx, sz); return l < 1e-9 ? null : new double[]{sx / l, sz / l}; }
        }
        return null;
    }

    /** Ein Wanderer kommt bei Niedrigwasser von Westen auf die Bank zu und geht wieder. */
    static void flucht() {
        System.out.println("Flucht vor einem Wanderer");
        DemoBank b = new DemoBank();
        Colony c = colony(b, 30, 9, 5);
        b.t = 5.0 * 3600;
        c.settle();
        for (int i = 0; i < 30; i++) { b.t += 10; c.step(10); }
        int before = c.lying();
        double cx = 0, cz = 0;
        for (Colony.Animal a : c.animals()) if (a.act == Colony.Activity.LIEGEN) { cx += a.x; cz += a.z; }
        cx /= Math.max(1, before);
        cz /= Math.max(1, before);
        SealDemo.Walker w = new SealDemo.Walker();
        w.start(cx - 900, cz, cx, cz);
        double firstFlee = -1, allInWater = -1;
        for (int i = 0; i < 3000 && w.on; i++) {
            w.step(1);
            b.t += 1;
            c.disturbers.clear();
            c.disturbers.add(new Colony.Disturber(w.x, w.z, 1));
            c.step(1);
            double d = Math.hypot(w.x - cx, w.z - cz);
            if (firstFlee < 0 && c.flights > 0) firstFlee = c.lastFleeDistance();
            if (allInWater < 0 && firstFlee > 0 && c.lying() == 0 && c.census()[Colony.Activity.FLUCHT.ordinal()] == 0) allInWater = d;
        }
        check(before >= 10, before + " liegen, bevor der Wanderer kommt");
        check(firstFlee > 0 && firstFlee <= c.seal.flightDistance + 2 && firstFlee > c.seal.flightDistance * 0.6,
                String.format(Locale.GERMAN, "die Ersten fliehen bei %.0f m (Fluchtdistanz %.0f m)", firstFlee, c.seal.flightDistance));
        check(allInWater > 60, String.format(Locale.GERMAN, "alle im Wasser, als der Wanderer noch %.0f m von der Gruppe war", allInWater));
        int fled = c.flights;
        check(fled >= before * 0.8, fled + " Tiere geflohen");
        // nach dem Störer: Ruhe, dann zurück auf die Bank, solange sie trocken ist
        c.disturbers.clear();
        double back = -1;
        for (int i = 0; i < 360; i++) {
            b.t += 10;
            c.step(10);
            if (back < 0 && c.lying() >= before / 2) back = i * 10;
        }
        check(back > c.seal.calmMinutes * 60 * 0.7 && back < (c.seal.calmMinutes + 45) * 60, String.format(Locale.GERMAN, "zurück auf der Bank nach %.0f min (Ruhe %.0f min)", back / 60, c.seal.calmMinutes));
        // ein Boot im Wasser: die Schwimmer weichen aus
        b.t = 0;
        c.settle();
        double d0 = 0;
        for (Colony.Animal a : c.animals()) d0 += Math.hypot(a.x - 60, a.z - 200);
        for (int i = 0; i < 120; i++) { b.t += 5; c.disturbers.clear(); c.disturbers.add(new Colony.Disturber(60, 200, 1)); c.step(5); }
        double d1 = 0;
        for (Colony.Animal a : c.animals()) d1 += Math.hypot(a.x - 60, a.z - 200);
        int n = c.animals().size();
        check(d1 > d0 + n * 20, String.format(Locale.GERMAN, "Boot bei Hochwasser: die Schwimmer weichen aus (im Mittel %.0f → %.0f m)", d0 / n, d1 / n));
    }

    static void heuler() {
        System.out.println("Heuler");
        DemoBank b = new DemoBank();
        Colony c = colony(b, 30, 6, 11);
        b.t = 5.5 * 3600;
        c.settle();
        int calls0 = c.calls;
        for (int i = 0; i < 180; i++) { b.t += 10; c.step(10); }
        int pups = c.pups(), near = 0, lyingPups = 0;
        for (Colony.Animal p : c.animals()) {
            if (!p.pup || p.act != Colony.Activity.LIEGEN) continue;
            lyingPups++;
            for (Colony.Animal m : c.animals()) if (m.id == p.mother && Math.hypot(m.x - p.x, m.z - p.z) < 2.0 * m.length) near++;
        }
        check(pups > 3, "Juni: " + pups + " Heuler bei 30 Tieren");
        check(lyingPups > 0 && near == lyingPups, near + " von " + lyingPups + " liegenden Heulern liegen bei der Mutter");
        check(c.calls > calls0, (c.calls - calls0) + " Rufe in 30 min (onCall für den Klang)");
        Colony sep = colony(new DemoBank(), 30, 9, 11);
        sep.settle();
        check(sep.pups() == 0, "September: keine Heuler");
    }

    static void wiederholbar() {
        System.out.println("Wiederholbar");
        double[] r = new double[2];
        for (int k = 0; k < 2; k++) {
            DemoBank b = new DemoBank();
            Colony c = colony(b, 20, 7, 42);
            b.t = 3600 * 3;
            c.settle();
            for (int i = 0; i < 300; i++) { b.t += 10; c.step(10); }
            double s = 0;
            for (Colony.Animal a : c.animals()) s += a.x * 1.3 + a.z * 0.7 + a.heading;
            r[k] = s;
        }
        check(r[0] == r[1], "gleicher Startwert, gleiche Lagen nach 50 min");
    }

    private SealCheck() { }
}
