package com.dan.fracture.demo;

import com.dan.fracture.Fracture;
import com.dan.fracture.Material;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Shape;
import com.dan.fracture.Solid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Phase 6: Zellen, Säule, Balken und Kragplatte gegen die Handrechnung, Fall, Reibung, Auftrieb,
 * Stapel ohne Durchdringung, 1 000 Stücke im Budget, gleicher Seed – gleicher Bruch.
 */
public final class FractureCheck {
    private static int bad, count;
    static final double DT = 1.0 / 120;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        section("Hülle");
        Fracture f0 = Fracture.ice();
        check(Math.abs(f0.freeFall(50) - 3.19) < 0.01, fmt("freier Fall aus 50 m: %.2f s", f0.freeFall(50)));
        check(Fracture.rock().material.density > Fracture.concrete().material.density && Fracture.concrete().material.density > f0.material.density,
                "Presets: Fels schwerer als Beton schwerer als Eis");
        check(f0.onCrack == null && f0.onBreak == null && f0.onImpact == null && f0.water == null && f0.ground == null, "ohne Zuhörer, Wasser und Boden startklar");
        check(Fracture.STAGE == 6 && f0.describe().contains("Engine"), "Stand: Engine (Phase 6)");
        Material ice = Material.ice();
        double c = ice.cohesion(), p = Math.toRadians(ice.frictionAngle);
        check(Math.abs(2 * c * Math.cos(p) / (1 - Math.sin(p)) - ice.compressive) < 1, fmt("Mohr-Coulomb: Kohäsion %.2f MPa gibt σc = %.1f MPa", c / 1e6, ice.compressive / 1e6));

        section("Zellen");
        double[] m = Poly.box(0, 0, 0, 2, 3, 4, -1).mass();
        check(Math.abs(m[0] - 24) < 1e-9 && Math.abs(m[4] - 50) < 1e-9 && Math.abs(m[6] - 26) < 1e-9, "Quader 2 × 3 × 4: Volumen 24, Trägheit 50 / 40 / 26 (Handrechnung)");
        Fracture fv = Fracture.ice();
        Solid sv = fv.addSolid(Shape.box(0, 0, 0, 3, 7, 2), 300);
        double vol = 0;
        for (int i = 0; i < sv.cells(); i++) vol += sv.volume(i);
        check(Math.abs(vol - 42) < 1e-6, fmt("%d Zellen füllen den Quader ohne Lücke: %.6f von 42 m³", sv.cells(), vol));
        int sym = 0;
        for (int b = 0; b < sv.bonds(); b++) if (sv.bondArea(b) > 0 && sv.bondB(b) > sv.bondA(b)) sym++;
        check(sym == sv.bonds() && sv.bonds() > 3 * sv.cells(), fmt("%,d Nachbarflächen, je Zelle %.1f, alle mit Fläche", sv.bonds(), 2.0 * sv.bonds() / sv.cells()));
        Solid sv2 = Fracture.ice().addSolid(Shape.box(0, 0, 0, 3, 7, 2), 300);
        Fracture fs = Fracture.ice();
        fs.seed = 2;
        Solid sv3 = fs.addSolid(Shape.box(0, 0, 0, 3, 7, 2), 300);
        check(java.util.Arrays.equals(sv.seedOf(17), sv2.seedOf(17)) && !java.util.Arrays.equals(sv.seedOf(17), sv3.seedOf(17)), "gleicher Seed, gleiche Zellen; anderer Seed, andere");
        Solid dense = Fracture.ice().addSolid(Shape.box(0, 0, 0, 10, 2, 2), 300, (x, y, z) -> x > 7 ? 1 : 0.15, null);
        int fine = 0;
        for (int i = 0; i < dense.cells(); i++) if (dense.center(i)[0] > 7) fine++;
        check(fine > dense.cells() * 0.5, fmt("Dichte nach Ort: %d von %d Zellen im feinen Drittel", fine, dense.cells()));

        section("Spannung");
        // Säule: Bruchlast gegen σc · A − Eigengewicht (Werkbuch: auf 10 %)
        double worst = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Fracture f = Fracture.ice();
            f.seed = seed;
            f.material.scatter = 0;
            Solid s = f.addSolid(Shape.box(0, 0, 0, 2, 10, 2), 300);
            s.anchorBelow(0);
            double lo = 0, hi = 60e6;
            for (int it = 0; it < 24; it++) {
                double F = (lo + hi) / 2;
                s.pressure(q -> q.ny > 0.99 && q.p[1] > 9.999, F / 4);
                if (s.solve() > 1) hi = F; else lo = F;
            }
            double hand = 5e6 * 4 - 917 * 9.81 * 40;
            worst = Math.max(worst, Math.abs(lo / hand - 1));
            if (seed == 1) {
                // Gleichgewicht im Schnitt y = 5: die Kraft, die der untere Teil auf den oberen ausübt
                s.pressure(q -> q.ny > 0.99 && q.p[1] > 9.999, 1e6 / 4);
                s.solve();
                double Fy = 0;
                for (int b = 0; b < s.bonds(); b++) {
                    double[] a = s.seedOf(s.bondA(b)), bb = s.seedOf(s.bondB(b));
                    if ((a[1] - 5) * (bb[1] - 5) >= 0) continue;
                    double fy = s.forceOf(b)[1];
                    Fy += bb[1] > a[1] ? fy : -fy;
                }
                double want = 1e6 + 917 * 9.81 * 4 * 5;
                check(Math.abs(Fy / want - 1) < 0.01, fmt("Säule: Kraft im Schnitt %.4g N, Handrechnung %.4g N", Fy, want));
            }
        }
        check(worst < 0.10, fmt("Säule 2 × 2 × 10 m Eis: Bruchlast auf %.1f %% an σc·A − G (Werkbuch: 10 %%)", worst * 100));
        // Balken: Kragarm aus der Wand, Eigengewicht
        {
            Fracture f = Fracture.ice();
            f.material.scatter = 0;
            double L = 10, h = 2, b = 2;
            Solid s = f.addSolid(Shape.box(0, 0, 0, L, h, b), 800);
            s.anchor(q -> q.nx < -0.99 && Math.abs(q.p[0]) < 1e-6);
            s.solve();
            double M = 0, xc = 3;
            for (int k = 0; k < s.bonds(); k++) {
                double[] a = s.seedOf(s.bondA(k)), bb = s.seedOf(s.bondB(k));
                if ((a[0] - xc) * (bb[0] - xc) >= 0) continue;
                double[] fv2 = s.forceOf(k), mv = s.momentOf(k), cc = s.bondCenter(k);
                double sg = bb[0] > a[0] ? 1 : -1;
                M += sg * ((cc[0] - xc) * fv2[1] - (cc[1] - h / 2) * fv2[0] + mv[2]);
            }
            double Mhand = 917 * 9.81 * b * h * (L - xc) * (L - xc) / 2;
            check(Math.abs(M / Mhand - 1) < 0.01, fmt("Balken: Moment im Schnitt %.4g N·m, Handrechnung ρ·g·b·h·a²/2 = %.4g N·m", M, Mhand));
            double maxT = 0;
            for (int k = 0; k < s.bonds(); k++) { double[] st = s.stressOf(k); maxT = Math.max(maxT, st[0] + st[2]); }
            double hand = 3 * 917 * 9.81 * L * L / h;
            check(Math.abs(maxT / hand - 1) < 0.15, fmt("Balken: größte Zugspannung %.2f MPa, Handrechnung 3ρgL²/h = %.2f MPa (auf 15 %%)", maxT / 1e6, hand / 1e6));
        }
        // Kragplatte: bricht erst ab der Länge der Handrechnung, und der Riss meldet sich vorher
        {
            double h = 4, ft = 2e5;
            double Lc = Math.sqrt(ft * h / (3 * 900 * 9.81));
            double[] held = new double[2];
            double[] tCrack = {Double.NaN}, tBreak = {Double.NaN};
            for (int r = 0; r < 2; r++) {
                double L = r == 0 ? 0.75 * Lc : 1.3 * Lc;
                Fracture f = Fracture.glacier();
                f.material.tensile = ft;
                f.material.scatter = 0;
                f.ground = (x, z) -> x < 0 ? 0.0 : -30.0;
                Solid s = f.addSolid(Shape.box(-12, 0, -2, L, h, 2), 500, (x, y, z) -> x > -4 ? 1 : 0.3, null);
                s.anchor(q -> q.ny < -0.99 && q.p[1] < 1e-6 && q.centroid()[0] < 0, 3);
                s.support(q -> q[0] < -10);
                final int rr = r;
                f.onCrack = (pth, e, br) -> { if (rr == 1 && Double.isNaN(tCrack[0])) tCrack[0] = f.time(); };
                f.onBreak = (id, v, mm) -> { if (rr == 1 && Double.isNaN(tBreak[0])) tBreak[0] = f.time(); };
                for (int i = 0; i < 120 * 4; i++) f.step(DT);
                double free = 0;
                for (Piece q : f.pieces()) free += q.volume();
                held[r] = free;
            }
            check(held[0] == 0, fmt("Kragplatte h = 4 m, Zug 200 kPa: hält mit 0,75 · L_c (L_c = √(f_t h / 3ρg) = %.1f m)", Lc));
            check(held[1] > 0.5 * 1.3 * Lc * h * 4, fmt("Kragplatte: bricht mit 1,3 · L_c ab (%.0f m³ frei, Überhang %.0f m³)", held[1], 1.3 * Lc * h * 4));
            check(tCrack[0] < tBreak[0], fmt("der Riss meldet sich vor dem Bruch: Riss %.2f s, frei %.2f s", tCrack[0], tBreak[0]));
        }

        section("Starrkörper");
        {
            Fracture f = Fracture.ice();
            f.ground = (x, z) -> 0.0;
            Piece p0 = f.addPiece(Shape.box(-1, 0, -1, 1, 2, 1), 1);
            p0.place(0, 51, 0);
            double t = 0;
            double deepest = 0;
            while (p0.position()[1] - 1 > 0.02 && t < 10) { f.step(DT); t += DT; }
            for (int i = 0; i < 30; i++) { f.step(DT); deepest = Math.max(deepest, -lowest(p0)); }
            check(Math.abs(t / f.freeFall(50) - 1) < 0.02, fmt("Fall aus 50 m bis zum Boden: %.2f s, Handrechnung √(2h/g) = %.2f s", t, f.freeFall(50)));
            check(deepest < 0.03, fmt("schlägt mit 31 m/s auf und dringt höchstens %.3f m in den Boden", deepest));
            for (int i = 0; i < 600; i++) f.step(DT);
            check(Math.abs(p0.position()[1] - 1) < 0.02 && p0.asleep(), fmt("liegt danach auf dem Boden (Mitte %.3f m, 1 m erwartet) und schläft", p0.position()[1]));
        }
        for (double mu : new double[]{0.5, 0.2}) {
            Fracture f = Fracture.ice();
            f.material.friction = mu;
            double a = Math.toRadians(20);
            f.ground = (x, z) -> -Math.tan(a) * x;
            Piece q = f.addPiece(Shape.box(-1, 0, -1, 1, 1, 1), 1);
            q.turn(0, 0, 1, -a);
            q.place(0, 0.5 / Math.cos(a) + 0.01, 0);
            for (int i = 0; i < 240; i++) f.step(DT);
            double x = q.position()[0], hand = Math.max(0, 0.5 * 9.81 * (Math.sin(a) - mu * Math.cos(a)) * 4 * Math.cos(a));
            check(Math.abs(x - hand) < Math.max(0.05, 0.05 * hand), fmt("Reibung μ = %.1f am 20°-Hang: %.2f m in 2 s, Handrechnung %.2f m", mu, x, hand));
        }
        {
            Fracture f = Fracture.ice();
            f.water = (x, z, t) -> 0.0;
            List<double[]> hits = new ArrayList<>();
            f.onImpact = (x, y, z, v, s, w) -> { if (w) hits.add(new double[]{v, s}); };
            Piece q = f.addPiece(Shape.box(-1, -1, -1, 1, 1, 1), 1);
            q.place(0, 4, 0);
            for (int i = 0; i < 120 * 25; i++) f.step(DT);
            check(Math.abs(q.submergedShare() - 0.917) < 0.03, fmt("Auftrieb: Eiswürfel schwimmt mit %.3f unter Wasser (917/1000 = 0,917)", q.submergedShare()));
            double dv = 0;
            for (double[] h : hits) dv += h[0];
            check(!hits.isEmpty() && dv > 0.5 * 8 * 0.917 && dv < 1.6 * 8, fmt("Einschlag ins Wasser gemeldet: %d Schritte, %.1f m³ verdrängt", hits.size(), dv));
        }
        {
            Fracture f = Fracture.ice();
            f.ground = (x, z) -> 0.0;
            f.seed = 3;
            Random r = new Random(1);
            for (int i = 0; i < 200; i++) {
                Piece q = f.addPiece(Shape.box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5), 1);
                q.place((i % 5) * 1.8 - 3.6 + r.nextGaussian() * 0.2, 1 + (i / 25) * 1.8, ((i / 5) % 5) * 1.8 - 3.6 + r.nextGaussian() * 0.2);
                q.turn(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble() * 3);
            }
            double E0 = energy(f), Emax = E0, minY = 1e9, late = 0;
            for (int i = 0; i < 120 * 30; i++) {
                f.step(DT);
                if (i % 12 == 0) {
                    Emax = Math.max(Emax, energy(f));
                    for (Piece q : f.pieces()) minY = Math.min(minY, lowest(q));
                }
                if (i > 120 * 25) late = Math.max(late, f.maxPenetration());
            }
            check(minY > -0.03, fmt("200 Würfel fallen aus bis zu 14 m auf einen Haufen: keine Ecke tiefer als %.3f m im Boden", -minY));
            check(late < 0.05, fmt("im ruhenden Haufen dringt nichts tiefer als %.3f m ein", late));
            check(Emax < E0 * 1.01, fmt("keine Energie aus dem Nichts: höchstens %.2f %% der Anfangsenergie", Emax / E0 * 100));
            check(f.awake() < 20, fmt("nach 30 s ruhen %d von 200", 200 - f.awake()));
        }
        {
            Fracture f = Fracture.ice();
            f.ground = (x, z) -> 0.0;
            Solid s = f.addSolid(Shape.box(-0.6, 0, -0.6, 0.6, 6, 0.6), 200);
            s.anchorBelow(0, 2);
            f.material = Material.rock();
            Piece blk = f.addPiece(Shape.box(-1.5, 0, -1.5, 1.5, 1.5, 1.5), 1);
            blk.place(0, 6.76, 0);
            for (int i = 0; i < 600; i++) f.step(DT);
            double Fy = 0;
            for (int b = s.bonds(); b < s.bonds() + s.anchors(); b++) Fy += s.forceOf(b)[1];
            double want = -(blk.mass() + 917 * 1.44 * 6) * 9.81;
            check(Math.abs(Fy / want - 1) < 0.03 && blk.asleep(), fmt("ein ruhender Block lastet auf dem Pfeiler: Anker tragen %.0f kN, Handrechnung %.0f kN", -Fy / 1e3, -want / 1e3));
        }
        {
            int[] parts = new int[2];
            for (int r = 0; r < 2; r++) {
                Fracture f = Fracture.rock();
                f.material.tensile = 4e5;
                f.material.compressive = 6e6;
                f.ground = (x, z) -> 0.0;
                Piece q = f.addPiece(Shape.box(-1.2, -1, -1, 1.2, 1, 1), 14);
                q.place(0, 1.02, 0);
                q.push(0, r == 0 ? -1 : -12, 0);
                for (int i = 0; i < 240; i++) f.step(DT);
                parts[r] = f.pieces().size();
            }
            check(parts[0] == 1 && parts[1] >= 2, fmt("Fels (Zug 400 kPa) schlägt auf: mit 1 m/s bleibt er ganz, mit 12 m/s bricht er in %d Stücke", parts[1]));
        }

        section("Wiederholbarkeit und Leistung");
        {
            double[] h = new double[2];
            for (int r = 0; r < 2; r++) {
                FractureScene sc = new FractureScene();
                sc.load(FractureScene.Case.FELS);
                sc.trigger();
                sc.step(6);
                double acc = 0;
                for (Piece q : sc.world.pieces()) { double[] ps = q.position(); acc += q.id * 31.7 + ps[0] * 1.3 + ps[1] * 2.9 + ps[2] * 0.7; }
                h[r] = acc + sc.world.pieces().size() * 1000;
            }
            check(h[0] == h[1], "gleicher Seed, gleicher Sturz: zwei Läufe des Felssturzes enden Stück für Stück gleich");
        }
        {
            Fracture f = Fracture.ice();
            f.water = (x, z, t) -> 0.0;
            f.ground = (x, z) -> -30 + 0.1 * x;
            f.seed = 5;
            Random r = new Random(2);
            for (int i = 0; i < 1000; i++) {
                double s = 0.5 + r.nextDouble() * 1.5;
                Piece q = f.addPiece(Shape.box(-s, -s * 0.7, -s, s, s * 0.7, s), 1);
                q.place(r.nextDouble() * 120 - 60, 2 + r.nextDouble() * 25, r.nextDouble() * 120 - 60);
                q.turn(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble() * 3);
            }
            for (int i = 0; i < 120; i++) f.step(DT);
            long a = System.nanoTime();
            for (int i = 0; i < 240; i++) f.step(DT);
            double ms = (System.nanoTime() - a) / 1e6 / 240;
            check(ms < 3 && f.awake() == 1000, fmt("1 000 wache Stücke fallen in einen See: %.2f ms je Schritt auf dem Prozessor (Werkbuch: unter 3 ms)", ms));
        }
        // Seit Glacier Phase 10: Boden aus Kacheln
        {
            int[] calls = {0};
            Fracture.Ground src = (x, z) -> { calls[0]++; return 3 * Math.sin(x * 0.05) + 2 * Math.cos(z * 0.07); };
            com.dan.fracture.GroundCache gc = new com.dan.fracture.GroundCache(src, 1.0);
            double err = 0;
            java.util.Random rq = new java.util.Random(3);
            for (int i = 0; i < 20000; i++) { double x = rq.nextDouble() * 120 - 60, z = rq.nextDouble() * 120 - 60; err = Math.max(err, Math.abs(gc.heightAt(x, z) - src.heightAt(x, z))); }
            check(err < 0.01 && gc.tiles() <= 9 && gc.hitRate() > 0.99, String.format(java.util.Locale.GERMAN,
                    "Boden aus Kacheln (GroundCache, 1 m): größter Fehler %.4f m, %d Kacheln, %.1f %% Treffer", err, gc.tiles(), 100 * gc.hitRate()));
        }
        System.out.println(fmt("%d Prüfungen, %.1f s", count, (System.nanoTime() - t0) / 1e9));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static double energy(Fracture f) {
        double e = 0;
        for (Piece q : f.pieces()) e += q.kinetic() + q.mass() * f.gravity * (q.position()[1] + 100);
        return e;
    }

    static double lowest(Piece q) {
        double y = 1e9;
        for (int i = 0; i < q.cellCount(); i++) {
            Poly c = q.worldCell(i);
            y = Math.min(y, -c.maxAbove(0, -1, 0, 0));
        }
        return y;
    }

    static String fmt(String f, Object... a) { return String.format(Locale.GERMAN, f, a); }

    static void section(String s) { System.out.println(s); }

    static void check(boolean c, String what) {
        count++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private FractureCheck() { }
}
