package com.dan.buoyancy.demo;

import com.dan.buoyancy.Buoy;
import com.dan.buoyancy.Buoyancy;
import com.dan.buoyancy.LightPoint;
import com.dan.buoyancy.Rhythm;
import com.dan.buoyancy.Ship;
import com.dan.buoyancy.Wiper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“. Geprüft werden Tiefgang
 * und Eigenperioden gegen die Rechnung von Hand, das Mitgehen in langer Dünung und das Glätten kurzer Wellen, die
 * Verankerung im Sturm, die Route des Schiffs, die Lichter nach KVR (Sektoren und Stärken), die Kennungen der
 * Tonnenfeuer, der Wischer, Reproduzierbarkeit und Stabilität bei Bft 12.
 */
public final class BuoyancyCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new BuoyancyCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    /** Lässt den Körper in ruhigem Wasser aus einer Auslenkung schwingen und misst die Periode (Nulldurchgänge). */
    static double decay(com.dan.buoyancy.Floater b, int what, double dt, double seconds) {
        DemoSea calm = DemoSea.regular(0, 10, 0);
        b.settle(calm);
        if (what == 0) b.y += 0.2; else if (what == 1) b.pitch = 0.03; else b.roll = 0.08;
        double prev = 0, t = 0, first = -1, last = -1;
        int crossings = 0;
        for (int i = 0; t < seconds; i++) {
            b.sample(calm);
            b.integrate(dt);
            t += dt;
            double v = what == 0 ? b.y : what == 1 ? b.pitch : b.roll;
            if (i > 0 && prev > 0 && v <= 0) {
                if (first < 0) first = t; else { last = t; crossings++; }
            }
            prev = v;
        }
        return crossings > 0 ? (last - first) / crossings : Double.NaN;
    }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Hydrostatik
        Buoy b = new Buoy(Buoy.Kind.STEUERBORD, "17", null, 0, 0);
        Ship s = Ship.coaster("Prüfling");
        DemoSea calm = DemoSea.regular(0, 10, 0);
        b.settle(calm);
        s.settle(calm);
        for (int i = 0; i < 600; i++) { b.sample(calm); b.integrate(0.05); s.sample(calm); s.integrate(0.05); }
        double sink = Math.abs(b.y), sinkS = Math.abs(s.y);
        check(sink < 0.01 && sinkS < 0.01 && Math.abs(s.pitch) < 1e-3 && Math.abs(b.draft - Buoy.DRAFT) < 1e-9,
                f("Tiefgang: Tonne %.1f t schwimmt auf %.2f m (nach 30 s %.1f mm daneben), Kümo %.0f t auf %.2f m (%.1f mm)", b.mass() / 1000, b.draft, sink * 1000, s.mass() / 1000, s.draft, sinkS * 1000));
        check(b.gm > 0.4 && b.gm < 1.2 && s.gm > 0.6 && s.gm < 1.6, f("Anfangsstabilität GM: Tonne %.2f m (Ballast tief), Kümo %.2f m", b.gm, s.gm));

        // ------------------------------------------------ Eigenperioden
        double th = decay(new Buoy(Buoy.Kind.STEUERBORD, "x", null, 0, 0), 0, 0.01, 30), tr = decay(new Buoy(Buoy.Kind.STEUERBORD, "x", null, 0, 0), 2, 0.01, 30);
        Buoy ref = new Buoy(Buoy.Kind.STEUERBORD, "x", null, 0, 0);
        check(Math.abs(th / ref.heavePeriod() - 1) < 0.12 && Math.abs(tr / ref.rollPeriod() - 1) < 0.15,
                f("Tonne: Tauchen %.2f s (von Hand %.2f s), Rollen %.2f s (von Hand %.2f s)", th, ref.heavePeriod(), tr, ref.rollPeriod()));
        double sh = decay(Ship.coaster("x"), 0, 0.01, 60), sr = decay(Ship.coaster("x"), 2, 0.01, 90), sp = decay(Ship.coaster("x"), 1, 0.01, 60);
        Ship sref = Ship.coaster("x");
        check(Math.abs(sh / sref.heavePeriod() - 1) < 0.15 && Math.abs(sr / sref.rollPeriod() - 1) < 0.15 && sr > 7 && sr < 16,
                f("Kümo: Tauchen %.1f s (von Hand %.1f s), Stampfen %.1f s, Rollen %.1f s (von Hand %.1f s)", sh, sref.heavePeriod(), sp, sr, sref.rollPeriod()));

        // ------------------------------------------------ Antwort auf Wellen
        DemoSea swell = DemoSea.regular(2.0, 12, 90);
        Buoy bs = new Buoy(Buoy.Kind.BACKBORD, "x", null, 0, 0);
        bs.settle(swell);
        double maxB = -9, minB = 9;
        for (double t = 0; t < 120; t += 0.02) {
            swell.t = t;
            bs.sample(swell);
            bs.integrate(0.02);
            if (t > 60) { maxB = Math.max(maxB, bs.y); minB = Math.min(minB, bs.y); }
        }
        double rao = (maxB - minB) / 2.0;
        DemoSea chop = DemoSea.regular(1.0, 2.6, 0);
        Ship sc = Ship.coaster("x");
        sc.heading = 90;
        sc.settle(chop);
        double maxS = -9, minS = 9;
        for (double t = 0; t < 120; t += 0.02) {
            chop.t = t;
            sc.sample(chop);
            sc.integrate(0.02);
            if (t > 60) { maxS = Math.max(maxS, sc.y); minS = Math.min(minS, sc.y); }
        }
        double raoS = (maxS - minS) / 1.0;
        check(Math.abs(rao - 1) < 0.15 && raoS < 0.15,
                f("Dünung 2 m, 12 s: die Tonne geht mit (Hub %.2f der Welle); Kabbelsee 1 m, 2,6 s quer: der Kümo hebt sich nur %.0f %% davon", rao, raoS * 100));

        // ------------------------------------------------ Verankerung im Sturm
        Buoyancy fleet = Buoyancy.beaufort(10);
        DemoSea storm = new DemoSea(9, 26.5, 250, 3);
        fleet.sea = storm;
        fleet.shipEveryMin = 0;
        fleet.layFairway(220, 900, 3);
        double maxOff = 0, maxRoll = 0;
        boolean finite = true;
        for (double t = 0; t < 600; t += 0.05) {
            storm.t = t;
            fleet.step(0.05);
            for (Buoy x : fleet.buoys) {
                maxOff = Math.max(maxOff, Math.hypot(x.x - x.anchorX, x.z - x.anchorZ));
                maxRoll = Math.max(maxRoll, Math.abs(Math.toDegrees(x.roll)));
                finite &= Double.isFinite(x.y) && Double.isFinite(x.roll);
            }
        }
        check(finite && maxOff < 25 && maxRoll > 5 && maxRoll < 46,
                f("Bft 10, H_s 9 m, 10 min: %d Tonnen bleiben an der Kette (größte Auslenkung %.1f m), krängen bis %.0f°", fleet.buoys.size(), maxOff, maxRoll));

        // ------------------------------------------------ Schiff auf der Route
        Buoyancy fw = Buoyancy.beaufort(5);
        DemoSea mod = new DemoSea(2, 9.4, 240, 5);
        fw.sea = mod;
        fw.shipEveryMin = 0;
        Ship k = fw.spawn(true, 0);
        double startRem = k.remaining(), off = 0;
        double a = Math.toRadians(fw.fairwayDeg), rx = Math.cos(a), rz = Math.sin(a);
        int steps = 0;
        for (double t = 0; t < 900; t += 0.1, steps++) {
            mod.t = t;
            fw.step(0.1);
            if (fw.ships.isEmpty()) break;
            off = Math.max(off, Math.abs(Math.abs((k.x - fw.fairwayX) * rx + (k.z - fw.fairwayZ) * rz) - 60));
        }
        double made = startRem - k.remaining();
        check(off < 6 && Math.abs(made / 900 / k.speed - 1) < 0.08,
                f("Kümo fährt sein Fahrwasser: in 15 min %.1f km (%.1f kn), höchstens %.1f m neben der Linie", made / 1000, made / 900 * 1.944, off));

        // ------------------------------------------------ Lichter nach KVR
        Ship l = Ship.coaster("Licht");
        l.heading = 0;
        l.settle(calm);
        String ahead = names(l.lights(0, 10, -2000)), stb = names(l.lights(2000, 10, 0)), port = names(l.lights(-2000, 10, 0)), astern = names(l.lights(0, 10, 2000));
        String stbAft = names(l.lights(2000, 10, 1155));
        check(ahead.equals("TTSB") && stb.equals("TTS") && port.equals("TTB") && astern.equals("H") && stbAft.equals("H"),
                f("Sektoren: voraus %s, querab Steuerbord %s, querab Backbord %s, achteraus %s, 30° achterlicher als querab %s (T Topplicht, S grün, B rot, H Heck)",
                        ahead, stb, port, astern, stbAft));
        check(Math.abs(Ship.colregCd(6) - 94) < 1 && Math.abs(Ship.colregCd(3) - 12) < 0.5,
                f("Lichtstärken nach KVR Anhang I: 6 sm %.0f cd, 3 sm %.1f cd", Ship.colregCd(6), Ship.colregCd(3)));

        // ------------------------------------------------ Tonnenfeuer
        Rhythm fl = new Rhythm("Fl G 4s"), q = new Rhythm("Q R"), iso = new Rhythm("Iso 8s"), fl2 = new Rhythm("Fl(2) R 6s"), mo = new Rhythm("Mo(A) 6s");
        check(fl.colour == 'G' && fl.period == 4 && Math.abs(fl.dutyCycle() - 0.125) < 1e-9 && q.period == 1 && Math.abs(q.dutyCycle() - 0.3) < 1e-9
                        && Math.abs(iso.dutyCycle() - 0.5) < 1e-9 && fl2.level(0.25) > 0.99 && fl2.level(1.0) < 0.01 && fl2.level(1.75) > 0.99 && mo.level(1.5) > 0.99 && mo.level(0.75) < 0.01,
                "Kennungen: Fl G 4s 12,5 % an, Q R jede Sekunde, Iso 8s halb an, Fl(2) R 6s zwei Blitze, Mo(A) 6s kurz-lang");
        String err = "";
        try { new Rhythm("Fl(9) G 2s"); } catch (IllegalArgumentException e) { err = e.getMessage(); }
        check(err.contains("passt nicht"), "unmögliche Kennung gemeldet („" + err + "“)");
        Buoyancy night = Buoyancy.calm();
        night.sea = calm;
        night.layFairway(220, 900, 2);
        night.ambientLux = 5;
        int lit = 0;
        for (double t = 0; t < 8; t += 0.05) { night.step(0.05); lit = Math.max(lit, night.lights(0, 10, 0).size()); }
        night.ambientLux = 5000;
        check(lit >= 3 && night.lights(0, 10, 0).isEmpty(), f("Tonnenfeuer: nachts blitzen bis zu %d gleichzeitig, am Tag keines", lit));

        // ------------------------------------------------ Wischer
        Wiper w = new Wiper(1.3, 0.05, 0.92);
        w.on = true;
        double amin = 1e9, amax = -1e9;
        for (int i = 0; i < 200; i++) { w.step(0.01); amin = Math.min(amin, w.angle()); amax = Math.max(amax, w.angle()); }
        w.on = false;
        for (int i = 0; i < 300; i++) w.step(0.01);
        double[] pts = w.sweep(-55, 55, 0.05);
        double umin = 1e9, umax = -1e9;
        for (int i = 0; i < pts.length; i += 2) { umin = Math.min(umin, pts[i]); umax = Math.max(umax, pts[i]); }
        check(amax - amin > 109 && Math.abs(w.angle() - w.parked()) < 0.5 && umax - umin > 1.4,
                f("Wischer: schwingt %.0f° in %.1f s, parkt nach dem Ausschalten links, wischt %.2f m breit", amax - amin, w.cycleSeconds, umax - umin));

        // ------------------------------------------------ Reproduzierbar und stabil
        double[] r1 = runFleet(7), r2 = runFleet(7), r3 = runFleet(8);
        check(r1[0] == r2[0] && r1[1] == r2[1] && r1[0] != r3[0], "gleicher seed, gleiche Bewegung und gleiche Schiffe; anderer seed, andere");
        Buoyancy hurricane = Buoyancy.beaufort(12);
        DemoSea hs = new DemoSea(14, 35, 270, 9);
        hurricane.sea = hs;
        hurricane.layFairway(220, 900, 2);
        hurricane.shipEveryMin = 0;
        Ship hk = hurricane.spawn(false, 2000);
        boolean ok = true;
        double maxPitch = 0, maxSR = 0;
        for (double t = 0; t < 300; t += 0.05) {
            hs.t = t;
            hurricane.step(0.05);
            ok &= Double.isFinite(hk.y) && Math.abs(hk.y) < 20;
            maxPitch = Math.max(maxPitch, Math.abs(Math.toDegrees(hk.pitch)));
            maxSR = Math.max(maxSR, Math.abs(Math.toDegrees(hk.roll)));
        }
        check(ok && maxPitch < 30, f("Bft 12, H_s 14 m, 5 min: alles bleibt endlich; der Kümo stampft bis %.0f°, rollt bis %.0f°", maxPitch, maxSR));

        // seit Rungholt Phase 11: die Kogge, die im Watt trockenfällt
        final double[] lvl = {1.0};
        com.dan.buoyancy.Sea tidal = new com.dan.buoyancy.Sea() {
            public double height(double x, double z) { return Math.max(-2.0, lvl[0]); }
            public double level() { return lvl[0]; }
            public double bed(double x, double z) { return -2.0; }
        };
        com.dan.buoyancy.Cog cog = new com.dan.buoyancy.Cog("Kogge");
        cog.place(tidal, 0, 0, 0);
        for (double t = 0; t < 60; t += 0.05) cog.step(tidal, 0.05);
        check(Math.abs(cog.draft - 2.0) < 0.35 && cog.gm > 0.5 && Math.abs(cog.y - 1.0) < 0.1 && !cog.grounded(),
                f("Kogge 23,3 × 7,6 m: schwimmt bei NHN +1,0 m mit mittlerem Tiefgang %.2f m, GM %.1f m, %.0f t", cog.draft, cog.gm, cog.mass() / 1000));
        for (double t = 0; t < 3 * 3600; t += 1) { lvl[0] = 1.0 - 3.0 * t / (3 * 3600); cog.step(tidal, 1); }
        check(cog.grounded() && Math.abs(cog.y - (-2.0 + cog.draftDesign)) < 0.02 && Math.abs(Math.toDegrees(cog.roll)) < 1 && Math.abs(Math.toDegrees(cog.pitch)) < 1,
                f("Ebbe bis NHN −2 m: sie sitzt aufrecht auf dem flachen Boden (Kiel auf dem Grund, %.1f° Schlagseite)", Math.toDegrees(cog.roll)));
        for (double t = 0; t < 3 * 3600; t += 1) { lvl[0] = -2.0 + 3.0 * t / (3 * 3600); cog.step(tidal, 1); }
        check(!cog.grounded() && Math.abs(cog.y - 1.0) < 0.15, f("die Flut hebt sie wieder (Wasserlinie bei NHN %+.2f m)", cog.y));
        cog.via(0, 0, 2.5).via(0, -200, 2.5).via(150, -400, 2.0);
        cog.start(tidal);
        double sailMax = 0;
        for (double t = 0; t < 900 && !cog.done(); t += 0.5) { cog.step(tidal, 0.5); sailMax = Math.max(sailMax, cog.sail); }
        double end = Math.hypot(cog.x - 150, cog.z + 400);
        for (double t = 0; t < 60; t += 0.5) cog.step(tidal, 0.5);
        check(cog.done() && end < 6 && sailMax > 0.9 && cog.sail < 0.3 && cog.speed() < 0.2,
                f("Fahrt mit gesetztem Segel über zwei Wegpunkte, am Ziel %.1f m daneben, Segel wieder aufgetucht", end));
        com.dan.buoyancy.Mesh cm0 = com.dan.buoyancy.Mesh.cog(cog, 0), cm1 = com.dan.buoyancy.Mesh.cog(cog, 1);
        check(cm0.triangles() > 500 && cm1.triangles() > cm0.triangles(), f("Netz der Kogge: %d Dreiecke aufgetucht, %d mit Segel", cm0.triangles(), cm1.triangles()));

        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Prüfungen fehlgeschlagen.");
        return lines;
    }

    static double[] runFleet(long seed) {
        Buoyancy b = Buoyancy.beaufort(5);
        b.seed = seed;
        DemoSea s = new DemoSea(2, 9.4, 240, 5);
        b.sea = s;
        b.layFairway(220, 900, 2);
        b.shipEveryMin = 2;
        double sum = 0;
        for (double t = 0; t < 400; t += 0.05) { s.t = t; b.step(0.05); for (Ship k : b.ships) sum += k.x + k.roll; }
        return new double[]{b.arrivals() * 1000 + sum, b.buoys.get(0).y};
    }

    static String names(List<LightPoint> l) {
        StringBuilder b = new StringBuilder();
        for (LightPoint p : l) {
            String n = p.name();
            b.append(n.contains("Topplicht") ? "T" : n.contains("Steuerbord") ? "S" : n.contains("Backbord") ? "B" : n.contains("Heck") ? "H" : "?");
        }
        return b.toString();
    }

    public int failures() { return bad; }
}
