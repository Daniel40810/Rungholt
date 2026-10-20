package com.dan.timber.demo;

import com.dan.timber.Builder;
import com.dan.timber.Fortification;
import com.dan.timber.House;
import com.dan.timber.Interior;
import com.dan.timber.HouseType;
import com.dan.timber.Material;
import com.dan.timber.Church;
import com.dan.timber.Mesh;
import com.dan.timber.Structures;
import com.dan.timber.Timber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Läuft ohne Grafikkarte und ohne die App.
 */
public final class TimberCheck {
    private static int bad;

    public static void main(String[] args) {
        Timber timber = new Timber();
        for (HouseType t : HouseType.values()) {
            int[] tri = new int[Timber.LODS];
            for (int l = 0; l < Timber.LODS; l++) {
                Mesh m = timber.mesh(t, l);
                tri[l] = m.triangles();
                sound(m, t.label + " Stufe " + l);
            }
            check(tri[0] > tri[1] && tri[1] > tri[2] && tri[2] < 50,
                    String.format(Locale.GERMAN, "%s: Dreiecke fallen über die Stufen (%d / %d / %d)", t.label, tri[0], tri[1], tri[2]));
            double[] b = timber.mesh(t, 0).bounds();
            double halfL = t.outerHalfLength(), halfW = t.outerHalfWidth();
            check(b[0] >= -halfL && b[3] <= halfL && b[2] >= -halfW && b[5] <= halfW && b[4] <= t.height() + 0.01 && b[1] >= -t.sunk - 0.45,
                    String.format(Locale.GERMAN, "%s: Netz in den Maßen des Typs (%.1f × %.1f × %.1f m)", t.label, b[3] - b[0], b[5] - b[2], b[4] - b[1]));
            check(b[4] > t.ridgeY() - 0.01, t.label + ": First erreicht");
            check(timber.mesh(t, 0).area(Material.THATCH) > t.length * t.width * 0.8, t.label + ": Reetdach deckt das Haus");
            House h = new House(t, 30, 2, -40, 37, 1, 1, 0.5, 9);
            Timber.Box box = Timber.box(h);
            check(box.contains(h.x(), h.y() + 1, h.z()), t.label + ": Kasten enthält die Mitte");
            boolean holes = true;
            for (double[] p : Timber.smokeHoles(h)) if (!box.contains(p[0], p[1], p[2])) holes = false;
            check(holes && Timber.smokeHoles(h).size() == (t.smokeHole ? Builder.smokeHoles(t).size() : 0) && (!t.smokeHole || !Timber.smokeHoles(h).isEmpty()),
                    t.label + ": Rauchlöcher im Kasten");
        }
        // Drehung: örtlich +x zeigt bei 90° nach Osten, bei 0° nach Norden (−z)
        House east = House.of(HouseType.WOHNHAUS, 0, 0, 0, 90);
        double[] p = east.toWorld(5, 0, 0);
        check(Math.abs(p[0] - 5) < 1e-9 && Math.abs(p[2]) < 1e-9, "Richtung 90°: First nach Osten");
        p = House.of(HouseType.WOHNHAUS, 0, 0, 0, 0).toWorld(5, 0, 0);
        check(Math.abs(p[0]) < 1e-9 && Math.abs(p[2] + 5) < 1e-9, "Richtung 0°: First nach Norden");
        Timber.Box bx = Timber.box(east);
        check(bx.contains(5.5, 1, 0) && !bx.contains(0, 1, 5.5), "Kasten folgt der Richtung");

        // Bauwerke
        double[][] line = {{0, 0}, {30, 0}, {30, 20}};
        Mesh f = Structures.fence(line, Structures.FLAT, 1);
        sound(f, "Zaun");
        int posts = countPosts(f);
        double expect = Structures.length(line) / 0.9;
        check(Math.abs(posts - expect) < 4, String.format(Locale.GERMAN, "Zaun: %d Pfosten auf %.0f m (Soll ≈ %.0f)", posts, Structures.length(line), expect));
        check(java.util.Arrays.equals(f.vertexData(), Structures.fence(line, Structures.FLAT, 1).vertexData())
                && !java.util.Arrays.equals(f.vertexData(), Structures.fence(line, Structures.FLAT, 2).vertexData()), "Zaun: gleicher seed, gleicher Zaun; anderer seed, anderer");
        Structures.Ground hill = (x, z) -> x * 0.1;
        double[] fb = Structures.fence(line, hill, 1).bounds();
        check(fb[4] > 3 + 1.0 && fb[4] < 3 + 1.4, "Zaun folgt dem Boden");
        sound(Structures.walkway(line, 2.2, Structures.FLAT, 3), "Bohlenweg");
        sound(Structures.palisade(line, 3.4, Structures.FLAT, 4), "Palisade");
        Mesh pier = Structures.pier(new double[][]{{0, 0}, {0, 30}}, 4, 0.9, (x, z) -> z > 8 ? -2.5 : 0, 5);
        sound(pier, "Brücke");
        check(pier.bounds()[1] < -2.4 && pier.bounds()[4] > 0.9, "Brücke: Pfähle bis zum Grund, Deck über dem Wasser");

        Structures.Set set = new Structures.Set();
        set.fence(new double[][]{{-200, 0}, {200, 0}}, Structures.FLAT, 7);
        set.walkway(new double[][]{{0, -150}, {0, 150}}, 2.2, Structures.FLAT, 8);
        set.palisade(new double[][]{{-100, 60}, {100, 60}}, 3.4, Structures.FLAT, 9);
        set.pier(new double[][]{{50, 0}, {50, 40}}, 4, 0.9, (x, z) -> -2, 10);
        boolean coarser = true, size = true;
        for (Structures.Tile tl : set.tiles()) {
            if (tl.coarse().triangles() >= tl.detail().triangles()) coarser = false;
            double[] b = tl.detail().bounds();
            if (Math.hypot(b[0] - tl.cx(), b[2] - tl.cz()) > tl.radius() + 1 || Math.hypot(b[3] - tl.cx(), b[5] - tl.cz()) > tl.radius() + 1) size = false;
            sound(tl.coarse(), "Kachel grob");
        }
        check(set.tiles().size() >= 4, set.tiles().size() + " Kacheln für 400 m Bauwerke");
        check(coarser, String.format(Locale.GERMAN, "Kacheln: grob leichter als fein (%,d gegen %,d Dreiecke)", set.triangles(true), set.triangles(false)));
        check(size, "Kacheln umfassen ihre Bauwerke");

        // Auswahl: Stufe nach Entfernung, Häuser hinter dem Auge und jenseits von far fallen weg
        timber.quality = Timber.Quality.MEDIUM;
        List<House> hs = new ArrayList<>();
        hs.add(House.of(HouseType.HALLE, 0, 0, -20, 90));
        hs.add(House.of(HouseType.HALLE, 0, 0, -150, 90));
        hs.add(House.of(HouseType.HALLE, 0, 0, -1000, 90));
        hs.add(House.of(HouseType.HALLE, 0, 0, 5000, 90));
        hs.add(House.of(HouseType.HALLE, 0, 0, 200, 90));
        Timber.Selection s = timber.select(hs, new double[]{0, 2, 0}, new double[]{0, 0, -1}, Math.cos(Math.toRadians(40)));
        check(s.total(0) == 1 && s.total(1) == 1 && s.total(2) == 1 && s.culled == 2,
                String.format("Auswahl: nah %d, mittel %d, fern %d, ausgelassen %d (Soll 1/1/1/2)", s.total(0), s.total(1), s.total(2), s.culled));
        float[] a = new float[Timber.INSTANCE];
        Timber.pack(new House(HouseType.SCHUPPEN, 1, 2, 3, 180, 1.1, 0.9, 0.3, 42), a, 0);
        check(a[0] == 1 && a[2] == 3 && Math.abs(a[3] - Math.PI) < 1e-6 && a[7] >= 0 && a[7] < 1, "Instanz gepackt (Ort, Richtung, Zufall 0..1)");
        // seit Haithabu Phase 15: Brand im Zufall
        float rnd0 = a[7];
        float[] b2 = new float[Timber.INSTANCE];
        Timber.pack(new House(HouseType.SCHUPPEN, 1, 2, 3, 180, 1.1, 0.9, 0.3, 42), b2, 0, 1.6);
        double[] ub = Timber.unpackBurn(b2[7]), u0 = Timber.unpackBurn(rnd0);
        check(Math.abs(ub[0] - 1.6) < 0.003 && Math.abs(ub[1] - rnd0) < 1e-4 && u0[0] == 0,
                String.format(Locale.GERMAN, "Brand in der Instanz: 1,60 gepackt, %.3f gelesen, Zufall bleibt (%.4f)", ub[0], ub[1]));
        Timber tb = new Timber();
        House hb = hs.get(0);
        tb.burn = h -> h.equals(hb) ? 0.7 : 0;
        check(tb.burnOf(hb) == 0.7 && tb.burnOf(hs.get(1)) == 0, "Brand je Haus über Timber.burn");
        check(Timber.Quality.LOW.far < Timber.Quality.ULTRA.far && Timber.Quality.LOW.near < Timber.Quality.ULTRA.near, "Qualitätsstufen steigen");

        List<House> v = new ArrayList<>(), w = new ArrayList<>();
        TimberDemo.village(120, 3, v, new Structures.Set(), 0.5);
        TimberDemo.village(120, 3, w, new Structures.Set(), 0.5);
        check(v.equals(w) && v.size() == 120, "Dorf: gleicher seed, gleiche Häuser");
        // Hof und Ufer (seit Haithabu Phase 5): Fischgestell, Brunnen, Boot
        Mesh rk = Structures.rack(0, 0, 90, 5, Structures.FLAT, 1);
        sound(rk, "Fischgestell");
        check(rk.area(Material.FISH) > 1.0 && rk.bounds()[4] < 2.1 && rk.bounds()[4] > 1.8, String.format(Locale.GERMAN, "Fischgestell: %.1f m² Fisch, 1,9 m hoch", rk.area(Material.FISH)));
        Mesh wl = Structures.well(0, 0, 0, Structures.FLAT, 2);
        sound(wl, "Brunnen");
        check(wl.area(Material.DARK) > 1.0 && wl.bounds()[4] > 2.5, "Brunnen: Wasser im Kasten, Schwengel über 2,5 m");
        Mesh bt = Structures.boat(0, 0, 90, 6, 8, Structures.FLAT, 3, 12), bc = Structures.boat(0, 0, 90, 6, 8, Structures.FLAT, 3, 5);
        sound(bt, "Boot");
        double[] bb = bt.bounds();
        check(bb[3] - bb[0] > 5.5 && bb[3] - bb[0] < 6.5 && bb[1] > -0.05 && bb[4] < 1.3 && bc.triangles() < bt.triangles() / 2,
                String.format(Locale.GERMAN, "Boot: %.1f m lang, liegt auf dem Boden, grob %d statt %d Dreiecke", bb[3] - bb[0], bc.triangles(), bt.triangles()));
        Structures.Set hof = new Structures.Set();
        hof.rack(10, 10, 0, 4, Structures.FLAT, 4);
        hof.well(-10, 10, 0, Structures.FLAT, 5);
        hof.boat(10, -10, 45, 5, 0, Structures.FLAT, 6);
        check(hof.tiles().size() >= 1 && hof.triangles(true) < hof.triangles(false), "Gestell, Brunnen und Boot in den Kacheln, grob leichter");
        // Wehrbau (seit Haithabu Phase 7)
        Structures.Ground rampe = (x, z) -> 10 + 0.05 * x;
        Mesh ww = Fortification.wallWalk(new double[][]{{0, 0}, {40, 0}}, 1.6, 1.0, rampe, 11);
        sound(ww, "Wehrgang");
        double[] wb = ww.bounds();
        check(wb[4] > 10 + 2.0 + 0.95 && wb[4] < 10 + 2.0 + 1.1 && wb[1] < 10 - 0.3, String.format(Locale.GERMAN, "Wehrgang: Bohlen 1 m über dem Boden, folgt ihm (%.2f m oben am Ende)", wb[4]));
        Fortification.Gate gt = new Fortification.Gate(0, 0, 90, 10, 9.5, 17, 3.6, 22, 3.5, 0.62, 3.0, true, true);
        Mesh gm = Fortification.gate(gt, 12), gc = Fortification.coarseGate(gt);
        sound(gm, "Torbau");
        sound(gc, "Torbau grob");
        double[] gb = gm.bounds();
        check(gt.clearance() > 3 && gb[4] > 17 + 3.5 && gb[4] < 17 + 4.5 && gb[3] - gb[0] > 21 && gc.triangles() < gm.triangles() / 4,
                String.format(Locale.GERMAN, "Torbau: lichte Höhe %.1f m, Turm bis %.1f m, Gasse %.0f m lang, grob %d statt %d Dreiecke", gt.clearance(), gb[4], gb[3] - gb[0], gc.triangles(), gm.triangles()));
        check(gm.area(Material.DOOR) > 2 * 1.4 * 2.8 && gm.area(Material.PLANK) > 2 * 22 * 2, "Torbau: zwei Flügel, Fassung aus Bohlen auf beiden Seiten");
        double[] in = gt.at(-1, 0, 0), out = gt.at(1, 0, 0);
        check(out[0] > in[0] && Math.abs(gt.top(0) - 17) < 1e-9 && gt.top(11) < 17 - 4 && gt.floor(-11) == 10 && gt.floor(11) == 9.5, "Torbau: Richtung 90° nach Osten, Fassung folgt dem Wall, Boden fällt nach außen");
        Fortification.Gate dl = new Fortification.Gate(0, 0, 0, 8, 8, 14, 4.8, 18, 3.5, 0.62, 3.0, false, false);
        Mesh dm = Fortification.gate(dl, 13);
        check(dm.area(Material.DOOR) == 0 && dm.bounds()[4] < 14 + 0.3, "Durchlass ohne Flügel und Turm, Steg in Höhe des Wehrgangs");
        Structures.Set wehr = new Structures.Set();
        wehr.wallWalk(new double[][]{{0, 0}, {200, 0}}, 1.6, 1.0, rampe, 14);
        wehr.gate(gt, 15);
        check(wehr.tiles().size() >= 2 && wehr.triangles(true) < wehr.triangles(false), "Wehrgang und Tor in den Kacheln, grob leichter");
        // Hafenbau (seit Haithabu Phase 8)
        Structures.Ground grund = (x, z) -> -0.05 * z;
        Mesh pf = com.dan.timber.Wharf.platform(new double[][]{{0, 0}, {0, 40}}, 12, 0.9, grund, 16);
        sound(pf, "Plattform");
        double[] pb = pf.bounds();
        check(pb[1] < -2.5 && pb[4] > 0.9 && pb[4] < 1.0 && pb[3] - pb[0] > 11.5, String.format(Locale.GERMAN, "Plattform 12 m breit, Deck auf 0,9 m, Pfähle bis zum Grund (%.1f m)", pb[1]));
        Mesh pw = com.dan.timber.Wharf.palisadeTo(new double[][]{{0, 0}, {0, 60}}, 1.3, grund, 17);
        sound(pw, "Pfahlreihe im Wasser");
        check(pw.bounds()[1] < -3.4 && pw.bounds()[4] > 1.3 && pw.bounds()[4] < 1.45, "Pfahlreihe vom Grund bis 1,3 m über dem Mittelwasser");
        Mesh rv = com.dan.timber.Wharf.revetment(new double[][]{{0, 0}, {20, 0}}, 0.7, Structures.FLAT, 18);
        sound(rv, "Uferbefestigung");
        Mesh goods = new Mesh();
        com.dan.timber.Wharf.barrel(goods, 0, 0, 0, 0.31, 0.86);
        com.dan.timber.Wharf.bale(goods, 2, 0, 0, 30, 1.2, 0.8, 0.56);
        com.dan.timber.Wharf.millstone(goods, 4, 0, 0, 0.5, 0.18, 0, 0);
        com.dan.timber.Wharf.millstone(goods, 5, 0, 0, 0.5, 0.18, 70, 90);
        com.dan.timber.Wharf.pot(goods, 7, 0, 0, 0.22, 0.3);
        sound(goods, "Waren");
        check(goods.area(Material.STONE) > 2 * Math.PI * 0.5 * 0.5 && goods.area(Material.CLOTH) > 2 && goods.area(Material.SOAPSTONE) > 0.3 && goods.bounds()[1] > -0.01,
                "Waren: Fass, Ballen, Mühlsteine (Basalt), Topf (Speckstein), alle auf dem Boden");
        interior();
        // Kirche aus Backstein (seit Rungholt Phase 11)
        Church.Spec cs = Church.rungholt();
        Mesh ch = Church.build(cs);
        sound(ch, "Kirche");
        double[] cb = ch.bounds();
        check(Math.abs(cb[3] - cb[0] - cs.length()) < 1.0 && cs.length() > 39 && cs.length() < 43 && cb[5] - cb[2] > cs.naveWidth() && cb[5] - cb[2] < 15.5,
                String.format(Locale.GERMAN, "Kirche %.1f × %.1f m (Fund 2023: rund 40 × 15 m), Ost-West, Apsis bei +x", cb[3] - cb[0], cb[5] - cb[2]));
        check(Math.abs(cb[4] - cs.height() - 1.8) < 0.2 && cb[1] < -0.5, String.format(Locale.GERMAN, "Turm %.0f m mit Helm und Wetterstange, Mauern 0,6 m unter den Boden", cs.height()));
        check(ch.area(Material.CHURCH) > 900 && ch.area(Material.THATCH) > 400 && ch.area(Material.SHINGLE) > 60 && ch.area(Material.STONE) > 50,
                String.format(Locale.GERMAN, "Werkstoffe: Backstein %.0f m², Reet %.0f m², Schindeln %.0f m², Feldsteinsockel %.0f m²", ch.area(Material.CHURCH), ch.area(Material.THATCH), ch.area(Material.SHINGLE), ch.area(Material.STONE)));
        // die Apsis liegt östlich des Chors, der Turm im Westen
        double eastMost = -1e9, westMost = 1e9;
        for (int i = 0; i < ch.vertices(); i++) { double[] pp = ch.pos(i); if (ch.material(i) == Material.CHURCH) { eastMost = Math.max(eastMost, pp[0]); westMost = Math.min(westMost, pp[0]); } }
        check(eastMost > cs.length() / 2 - 0.1 && westMost < -cs.length() / 2 + 0.1, "Apsis im Osten, Turm im Westen");
        System.out.println(timber.describe());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit Haithabu Phase 11: das begehbare Hallenhaus und seine Einrichtung. */
    private static void interior() {
        House h = new House(HouseType.HALLE, 10, 1.5, -20, 0, 1.0, 0.86, 0.8, 7);
        Mesh open = Builder.buildOpen(HouseType.HALLE), shut = Builder.build(HouseType.HALLE, 0);
        sound(open, "Hallenhaus begehbar (Türen offen, Rauchlöcher offen)");
        check(open.area(Material.DARK) < shut.area(Material.DARK) && open.area(Material.DOOR) > 0,
                String.format(Locale.GERMAN, "Begehbar: die Rauchlöcher sind Öffnungen statt dunkler Flächen (%.2f statt %.2f m² dunkel), die Türblätter stehen offen", open.area(Material.DARK), shut.area(Material.DARK)));
        Interior.Room r = Interior.room(h);
        int doors = 0, holes = 0;
        for (Interior.Opening o : r.openings()) if (o.triangle()) holes++; else doors++;
        check(doors == 2 && holes == 2 && Math.abs(r.hz() - (7.0 * 0.86 / 2 - 0.18)) < 1e-9 && Math.abs(r.roofY(r.hz() + 0.18) - HouseType.HALLE.wallHeight) < 1e-6,
                r.describe() + "; die Dachunterseite trifft die Wandkrone");
        // Strahlen aus der Mitte: senkrecht ans Dach, durch die Tür, durch das Rauchloch
        double[] c = {0, 1.0, 0};
        Interior.Opening door = null, hole = null;
        for (Interior.Opening o : r.openings()) { if (!o.triangle() && o.face() == Interior.SIDE_PZ) door = o; if (o.triangle() && o.face() == Interior.GABLE_PX) hole = o; }
        double[][] fd = r.openingFrame(door), fh = r.openingFrame(hole);
        double[] toDoor = norm(new double[]{fd[0][0] - c[0], fd[0][1] - c[1], fd[0][2] - c[2]}), toHole = norm(new double[]{fh[0][0] - c[0], fh[0][1] - c[1], fh[0][2] - c[2]});
        check(r.through(c, new double[]{0, 1, 0}) == null && r.through(c, toDoor) == door && r.through(c, toHole) == hole
                        && r.through(c, norm(new double[]{0.3, 0.2, -1})) == null,
                "Aus der Mitte: nach oben Reet, zur Tür und zum Rauchloch ins Freie, zur Wand nicht");
        double sky = r.skyFactor(new double[]{0, 0.05, 1.5}, new double[]{0, 1, 0}), skyFar = r.skyFactor(new double[]{-7, 0.05, -2}, new double[]{0, 1, 0});
        double df = r.daylightFactor(0.15);
        check(sky > skyFar && sky > 0.002 && sky < 0.1 && df > 0.003 && df < 0.03,
                String.format(Locale.GERMAN, "Himmel durch die Öffnungen: am Boden vor der Tür %.2f %%, in der Ecke %.2f %%; mittlerer Tageslichtquotient %.1f %%", sky * 100, skyFar * 100, df * 100));
        Mesh in = Interior.hall(h, 7);
        sound(in, "Einrichtung der Halle");
        // zurückgestreckt muss alles im Raum liegen (Boden bis unter das Dach)
        int out = 0;
        for (int i = 0; i < in.vertices(); i++) {
            double[] p = in.pos(i);
            double x = p[0] * h.lengthScale(), y = p[1], z = p[2] * h.widthScale();
            if (!(Math.abs(x) <= r.hx() + 0.02 && Math.abs(z) <= r.hz() + 0.02 && y >= -0.01 && y <= r.roofY(z) + 0.01)) out++;
        }
        check(out == 0 && in.area(Material.FLOOR) > 4 * r.hx() * r.hz() * 0.98 && in.area(Material.FUR) > 2 && in.area(Material.IRON) > 0.3 && in.area(Material.WOOL) > 2 && in.area(Material.HEARTH) > 2,
                String.format(Locale.GERMAN, "Einrichtung im Raum (%d Ecken außerhalb): Lehmboden %.0f m², Felle %.1f m², Eisen %.1f m², Webstuhl %.1f m², Herd %.1f m²",
                        out, in.area(Material.FLOOR), in.area(Material.FUR), in.area(Material.IRON), in.area(Material.WOOL), in.area(Material.HEARTH)));
        // die Wege für die Kamera: Herd und Türen frei
        double[] hs = Interior.hearthSpot();
        check(Math.abs(hs[0]) < 1 && Math.abs(hs[2]) < 0.2 && Interior.lampSpots(r).size() == 2, "Langfeuer in der Mitte, zwei Talglampen an den Wänden");
    }

    private static double[] norm(double[] v) { double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return new double[]{v[0] / l, v[1] / l, v[2] / l}; }

    /** Indizes im Bereich, keine NaN, Normalen Länge 1 und auf der Seite der Windung. */
    private static void sound(Mesh m, String what) {
        float[] v = m.vertexData();
        int[] ix = m.indexData();
        boolean range = true, finite = true, unit = true;
        for (float x : v) if (!Float.isFinite(x)) finite = false;
        for (int i : ix) if (i < 0 || i >= m.vertices()) range = false;
        int wrong = 0, counted = 0;
        for (int t = 0; range && t < ix.length; t += 3) {
            double[] a = m.pos(ix[t]), b = m.pos(ix[t + 1]), c = m.pos(ix[t + 2]);
            double[] n = Mesh.cross(Mesh.sub(b, a), Mesh.sub(c, a));
            if (Mesh.len(n) < 1e-8) continue;
            counted++;
            int k = ix[t] * Mesh.STRIDE;
            double[] sn = {v[k + 3], v[k + 4], v[k + 5]};
            if (Math.abs(Mesh.len(sn) - 1) > 1e-3) unit = false;
            if (Mesh.dot(n, sn) <= 0) wrong++;
        }
        check(range && finite && unit && wrong <= counted / 100,
                String.format(Locale.GERMAN, "%s: %,d Dreiecke heil%s", what, m.triangles(), wrong > 0 ? " (" + wrong + " verkehrt)" : ""));
    }

    private static int countPosts(Mesh m) {
        // Pfosten sind 5-seitige Balken mit Deckeln: je Pfosten ein Scheitelpunkt in der Deckelmitte oben
        float[] v = m.vertexData();
        int n = 0;
        for (int i = 0; i < m.vertices(); i++) {
            int k = i * Mesh.STRIDE;
            if (v[k + 8] == Material.POST.ordinal() && v[k + 4] > 0.99 && v[k + 1] > 1.1) n++;
        }
        return n / 6;
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FEHL ") + what);
        if (!ok) bad++;
    }
}
