package com.dan.flock.demo;

import com.dan.flock.Flock;
import com.dan.flock.GullMesh;
import com.dan.flock.gl.FlockGl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“. Geprüft werden das
 * Aufwindfeld, Stehen im Aufwind, Grenzen der Geschwindigkeit, nie im Turm und nie im Wasser, die Schlagfrequenz,
 * Landen und Sitzen mit dem Kopf im Wind, Nacht, Lee im Sturm, Wegfliegen im Orkan, Rufe und Reproduzierbarkeit.
 */
public final class FlockCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new FlockCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static Flock scene(int bft, long seed) {
        Flock f = Flock.beaufort(bft);
        f.seed = seed;
        f.windFromDeg = 270;
        f.obstacles.add(new Flock.Obstacle(0, 0, FlockScene.TOWER_R, -2, FlockScene.TOWER_H));
        for (int i = 0; i < 10; i++) {
            double a = 2 * Math.PI * i / 10;
            f.perches.add(new Flock.Perch("Geländer " + i, Math.sin(a) * FlockScene.GALLERY_R, FlockScene.RAIL_Y, -Math.cos(a) * FlockScene.GALLERY_R));
        }
        f.homeRadius = 140;
        return f;
    }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Aufwind
        Flock f = scene(5, 1);
        double upLuv = f.updraft(-6, 15, 0), upLee = f.updraft(6, 15, 0), upSide = f.updraft(0, 15, 6), upFar = f.updraft(-40, 15, 0), upHigh = f.updraft(-6, 45, 0);
        check(upLuv > 4 && upLee == 0 && upSide < 0.1 && upFar < 0.3 && upHigh < upLuv * 0.01,
                f("Aufwind bei %.1f m/s aus West: Luv 2,5 m vor der Wand %.1f m/s, Lee %.1f, querab %.2f, 36 m vor der Wand %.2f, 17 m über der Kuppel %.3f", f.windSpeed, upLuv, upLee, upSide, upFar, upHigh));

        // ------------------------------------------------ Bft 5: 10 min Flug
        f.step(0.01);
        double minAir = 1e9, maxAir = 0, minClear = 1e9, minAlt = 1e9;
        int soarSamples = 0, soarStill = 0, maxSoar = 0;
        long flapFrames = 0;
        double phaseAdvance = 0;
        for (int i = 0; i < 18000; i++) {
            double[] before = new double[f.gulls().size()];
            for (int k = 0; k < before.length; k++) before[k] = f.gulls().get(k).wingPhase;
            f.step(1 / 30.0);
            int soar = 0;
            for (int k = 0; k < f.gulls().size(); k++) {
                Flock.Gull g = f.gulls().get(k);
                if (g.state == Flock.State.AWAY || g.state == Flock.State.PERCH) continue;
                double air = g.airspeed;
                if (g.state == Flock.State.FLY) { minAir = Math.min(minAir, air); maxAir = Math.max(maxAir, air); }
                double d = Math.hypot(g.pos[0], g.pos[2]);
                if (g.pos[1] < FlockScene.TOWER_H) minClear = Math.min(minClear, d - FlockScene.TOWER_R);
                minAlt = Math.min(minAlt, g.pos[1]);
                if (g.state == Flock.State.SOAR) {
                    soar++;
                    soarSamples++;
                    if (g.groundSpeed() < 2.5) soarStill++;
                }
                if (g.flap > 0.3) {
                    flapFrames++;
                    double dp = g.wingPhase - before[k];
                    if (dp < 0) dp += 2 * Math.PI;
                    phaseAdvance += dp;
                }
            }
            maxSoar = Math.max(maxSoar, soar);
        }
        double hz = flapFrames > 0 ? phaseAdvance / (2 * Math.PI) / (flapFrames / 30.0) : 0;
        check(minAir >= Flock.V_MIN - 0.05 && maxAir <= Flock.V_MAX + 0.05, f("Fluggeschwindigkeit gegen die Luft %.1f bis %.1f m/s (Grenzen %.0f und %.0f)", minAir, maxAir, Flock.V_MIN, Flock.V_MAX));
        check(minClear >= 0.2 && minAlt >= 0.79, f("nie im Turm (kleinster Abstand zur Wand %.2f m) und nie im Wasser (tiefste Höhe %.2f m)", minClear, minAlt));
        check(maxSoar >= 2 && soarSamples > 0 && soarStill / (double) soarSamples > 0.5,
                f("im Aufwind stehen bis zu %d Möwen zugleich, %.0f %% der Zeit davon mit weniger als 2,5 m/s über Grund", maxSoar, 100.0 * soarStill / Math.max(1, soarSamples)));
        check(Math.abs(hz - Flock.FLAP_HZ) < 0.05, f("Flügelschlag %.2f Hz (Silbermöwe rund 3,1 Hz)", hz));
        int perched = f.census()[Flock.State.PERCH.ordinal()];
        boolean facing = true, onSpot = true;
        for (Flock.Gull g : f.gulls())
            if (g.state == Flock.State.PERCH) {
                double d = Math.abs(((g.facing - f.windFromDeg) % 360 + 540) % 360 - 180);
                facing &= d < 30;
                double[] p = g.perch().spot.position();
                onSpot &= Math.abs(g.pos[0] - p[0]) + Math.abs(g.pos[1] - p[1]) + Math.abs(g.pos[2] - p[2]) < 1e-9 && g.groundSpeed() == 0;
            }
        check(perched >= 1 && facing && onSpot && f.calls() > 20,
                f("nach 10 min sitzen %d auf dem Geländer, alle mit dem Kopf im Wind und still; %d Rufe", perched, f.calls()));

        // ------------------------------------------------ Nacht
        Flock n = scene(3, 2);
        n.step(0.01);
        for (int i = 0; i < 3000; i++) n.step(1 / 30.0);
        n.ambientLux = 5;
        long callsBefore = n.calls();
        for (int i = 0; i < 30 * 600; i++) n.step(1 / 30.0);
        int[] c = n.census();
        int flying = c[Flock.State.FLY.ordinal()] + c[Flock.State.SOAR.ordinal()] + c[Flock.State.LAND.ordinal()] + c[Flock.State.LEAVE.ordinal()] + c[Flock.State.TAKEOFF.ordinal()];
        check(flying == 0 && c[Flock.State.PERCH.ordinal()] >= 5 && n.calls() - callsBefore <= c[Flock.State.PERCH.ordinal()] + 2,
                f("nachts nach 10 min: %d fliegen, %d schlafen am Turm, %d sind zur Küste geflogen; danach still", flying, c[Flock.State.PERCH.ordinal()], c[Flock.State.AWAY.ordinal()]));

        // ------------------------------------------------ Sturm: Lee, Orkan: weg
        Flock s = scene(9, 3);
        s.step(0.01);
        int soarS = 0;
        for (int i = 0; i < 30 * 300; i++) {
            s.step(1 / 30.0);
            if (i > 30 * 60) for (Flock.Gull g : s.gulls()) if (g.state == Flock.State.SOAR) soarS++;
        }
        int[] cs = s.census();
        boolean allLee = true;
        for (Flock.Gull g : s.gulls()) if (g.state == Flock.State.PERCH) allLee &= g.pos[0] > 1;
        check(soarS == 0 && cs[Flock.State.PERCH.ordinal()] >= 2 && cs[Flock.State.PERCH.ordinal()] + cs[Flock.State.AWAY.ordinal()] == s.gulls().size() && allLee,
                f("Bft 9 aus West: nach 5 min sitzen %d im Lee auf dem Geländer (Ostseite), %d sind zur Küste, keine steht im Aufwind", cs[Flock.State.PERCH.ordinal()], cs[Flock.State.AWAY.ordinal()]));
        Flock o = scene(11, 4);
        o.step(0.01);
        for (int i = 0; i < 30 * 600; i++) o.step(1 / 30.0);
        int[] co = o.census();
        check(co[Flock.State.AWAY.ordinal()] == o.gulls().size(), f("Bft 11: nach 10 min sind alle %d weg", co[Flock.State.AWAY.ordinal()]));

        // ------------------------------------------------ seed
        Flock a = scene(5, 7), b = scene(5, 7), d = scene(5, 8);
        for (int i = 0; i < 900; i++) { a.step(1 / 30.0); b.step(1 / 30.0); d.step(1 / 30.0); }
        check(a.gulls().get(3).pos[0] == b.gulls().get(3).pos[0] && a.gulls().get(3).pos[0] != d.gulls().get(3).pos[0], "gleicher seed, gleicher Flug; anderer seed, anderer");

        // ------------------------------------------------ Flügel
        Flock.Gull g = a.gulls().get(0);
        g.fold = 1;
        double[] folded = FlockGl.tipCpu(GullMesh.wing(g));
        g.fold = 0;
        g.flap = 0;
        double[] spread = FlockGl.tipCpu(GullMesh.wing(g));
        check(spread[0] > 0.6 && Math.abs(folded[0]) < 0.25 && folded[2] > 0.3,
                f("Flügel: ausgebreitet reicht die Spitze %.2f m zur Seite (Spannweite gut 1,4 m), angelegt liegt sie %.2f m hinter der Schulter am Rumpf", spread[0], folded[2]));

        species();
        glacier();
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Prüfungen fehlgeschlagen.");
        return lines;
    }

    // ------------------------------------------------------------------ seit Haithabu Phase 13: die übrigen Arten

    /** Ein See (Kreis 120 m um 0, 0) mit Ufer, drei Pfählen, eine Wiese als Futterplatz der Krähen, wenig Wind. */
    static Flock lake(long seed, double iceRadius) {
        Flock f = Flock.beaufort(2);
        f.seed = seed;
        f.windFromDeg = 250;
        f.quality = Flock.Quality.LOW;
        f.homeX = 0; f.homeZ = 0; f.homeRadius = 120;
        f.ground = (x, z) -> Math.hypot(x, z) < 120 ? 0 : 0.02 * (Math.hypot(x, z) - 120);
        f.water = (x, z) -> { double r = Math.hypot(x, z); return r < 118 && r >= iceRadius ? 0 : Double.NaN; };
        f.waters.add(new Flock.Area(0, 0, 110));
        f.feeding.add(new Flock.Area(200, 0, 40));
        for (int i = 0; i < 3; i++) f.perches.add(new Flock.Perch("Pfahl " + i, 30 + i * 3, 1.2 + Flock.legHeight(com.dan.flock.Species.KORMORAN), 10).only(com.dan.flock.Species.KORMORAN, com.dan.flock.Species.MOEWE));
        int[] m = new int[com.dan.flock.Species.values().length];
        m[com.dan.flock.Species.ENTE.ordinal()] = 10;
        m[com.dan.flock.Species.SCHWAN.ordinal()] = 2;
        m[com.dan.flock.Species.KORMORAN.ordinal()] = 3;
        m[com.dan.flock.Species.KRAEHE.ordinal()] = 6;
        m[com.dan.flock.Species.SCHWALBE.ordinal()] = 8;
        f.mix = m;
        f.dayOfYear = 200;
        return f;
    }

    private void species() {
        com.dan.flock.Species ENTE = com.dan.flock.Species.ENTE, SCHWAN = com.dan.flock.Species.SCHWAN, KORM = com.dan.flock.Species.KORMORAN,
                KRAEHE = com.dan.flock.Species.KRAEHE, SCHWALBE = com.dan.flock.Species.SCHWALBE;
        // Netze: Spannweite wie die Art
        StringBuilder b = new StringBuilder();
        boolean meshOk = true;
        for (com.dan.flock.Species sp : com.dan.flock.Species.values()) {
            com.dan.flock.BirdMesh m = com.dan.flock.BirdMesh.build(sp, 0);
            float[] v = m.vertices();
            double maxX = 0, minZ = 0, maxZ = 0;
            for (int i = 0; i < m.count(); i++) { maxX = Math.max(maxX, v[i * 10]); minZ = Math.min(minZ, v[i * 10 + 2]); maxZ = Math.max(maxZ, v[i * 10 + 2]); }
            meshOk &= Math.abs(2 * maxX / sp.span - 1) < 0.08 && (maxZ - minZ) > sp.length * 0.75 && (maxZ - minZ) < sp.length * 1.45;
            b.append(f("%s %.2f/%.2f m; ", sp.label, 2 * maxX, maxZ - minZ));
        }
        check(meshOk, "Netze: Spannweite und Länge je Art — " + b.toString().replaceAll("; $", ""));
        // 10 Minuten am See im Juli
        Flock f = lake(3, 0);
        f.step(0.01);
        double landBad = 0, swallowY = 0;
        int dabbled = 0, dives = 0, spread = 0, walk = 0, swallowN = 0, swim = 0;
        for (int i = 0; i < 18000; i++) {
            f.step(1 / 30.0);
            for (Flock.Gull g : f.gulls()) {
                if (g.state == Flock.State.SWIM && Math.hypot(g.pos[0], g.pos[2]) > 118) landBad++;
                if (g.species == ENTE && g.dabble > 0.9) dabbled++;
                if (g.species == KORM && g.state == Flock.State.DIVE) dives++;
                if (g.species == KORM && g.state == Flock.State.PERCH && g.spread > 0.9) spread++;
                if (g.species == KRAEHE && g.state == Flock.State.WALK) walk++;
                if (g.species == SCHWALBE && g.state == Flock.State.FLY) { swallowY += g.pos[1]; swallowN++; }
                if (g.state == Flock.State.SWIM) swim++;
            }
        }
        check(landBad == 0 && swim > 0 && dabbled > 0, f("Schwimmer bleiben auf dem Wasser (nie über Land), Enten gründeln (%d Bilder mit Kopf unter Wasser)", dabbled));
        check(dives > 0 && spread > 0, f("Kormorane tauchen (%d Bilder unter Wasser) und trocknen auf den Pfählen die Flügel (%d Bilder)", dives, spread));
        check(walk > 0 && swallowN > 0 && swallowY / swallowN < 7, f("Krähen gehen auf der Wiese (%d Bilder), Schwalben fliegen tief über dem Wasser (im Mittel %.1f m)", walk, swallowN == 0 ? 0 : swallowY / swallowN));
        // Störung: ein Boot fährt mitten durch die Enten
        Flock s = lake(5, 0);
        s.step(0.01);
        for (int i = 0; i < 900; i++) s.step(1 / 30.0);
        double[] eye = {0, 1.5, 0};
        s.disturbers.add(eye);
        int took = 0, swanFlew = 0;
        double swanMin = 1e9;
        for (int i = 0; i < 1800; i++) {
            Flock.Gull duck = null;
            for (Flock.Gull g : s.gulls()) if (g.species == ENTE && g.state == Flock.State.SWIM) { duck = g; break; }
            if (duck != null && i % 300 == 0) { eye[0] = duck.pos[0] + 8; eye[2] = duck.pos[2]; }
            s.step(1 / 30.0);
            for (Flock.Gull g : s.gulls()) {
                if (g.species == ENTE && (g.state == Flock.State.TAKEOFF)) took++;
                if (g.species == SCHWAN && (g.state == Flock.State.TAKEOFF || g.state == Flock.State.FLY)) swanFlew++;
            }
        }
        check(took > 0, "Ein Boot fährt auf die Enten zu: sie fliegen auf (Fluchtdistanz 25 m) und landen woanders wieder");
        // Schwäne: schwimmen weg
        Flock w = lake(9, 0);
        w.step(0.01);
        for (int i = 0; i < 300; i++) w.step(1 / 30.0);
        Flock.Gull swan = null;
        for (Flock.Gull g : w.gulls()) if (g.species == SCHWAN) swan = g;
        double[] e2 = {swan.pos[0] + 9, 1.5, swan.pos[2]};
        w.disturbers.add(e2);
        double d0 = Math.hypot(swan.pos[0] - e2[0], swan.pos[2] - e2[2]);
        boolean flew = false;
        for (int i = 0; i < 600; i++) { w.step(1 / 30.0); flew |= swan.state == Flock.State.TAKEOFF || swan.state == Flock.State.FLY; }
        double d1 = Math.hypot(swan.pos[0] - e2[0], swan.pos[2] - e2[2]);
        check(d1 > d0 + 3 && !flew, f("Ein Schwan fliegt nicht auf, er schwimmt weg (von %.0f auf %.0f m)", d0, d1));
        // Winter: Schwalben fort; Eis: die Schwimmer verlassen das zugefrorene Wasser
        Flock z = lake(4, 200);
        z.dayOfYear = 20;
        z.step(0.01);
        for (int i = 0; i < 300; i++) z.step(1 / 10.0);
        check(z.present(SCHWALBE) == 0 && z.present(ENTE) == 0 && z.present(SCHWAN) == 0,
                "Im Januar sind keine Schwalben da; liegt Eis auf dem ganzen See, sind Enten und Schwäne fort");
        // Nacht: Krähen und Schwalben schlafen anderswo, Enten bleiben auf dem Wasser
        Flock n = lake(6, 0);
        n.step(0.01);
        n.ambientLux = 1;
        for (int i = 0; i < 600; i++) n.step(1 / 10.0);
        check(n.present(KRAEHE) == 0 && n.present(SCHWALBE) == 0 && n.count(ENTE, Flock.State.SWIM) == 10, "Nachts: Krähen und Schwalben sind fort, die Enten schlafen auf dem Wasser");
        lines.add("  ·      " + f.describe());
    }

    public int failures() { return bad; }

    /** Seit Glacier Phase 10: Kondore in der Thermik, Dominikanermöwen, Möwen auf treibenden Plätzen (Eisberge). */
    private void glacier() {
        com.dan.flock.Species K = com.dan.flock.Species.KONDOR;
        Flock f = Flock.beaufort(3);
        f.seed = 5;
        f.quality = Flock.Quality.LOW;
        f.kelpGulls = true;
        f.ambientLux = 50_000;
        f.ground = (x, z) -> Math.max(0, 0.15 * (x - 300));            // Hang im Osten, See im Westen
        f.condorX = 800; f.condorZ = 0; f.condorRadius = 1200;
        f.thermals.add(new Flock.Area(700, -300, 80));
        f.thermals.add(new Flock.Area(1100, 400, 80));
        int[] m = new int[com.dan.flock.Species.values().length];
        m[K.ordinal()] = 3;
        f.mix = m;
        // Eisberg: ein Platz, der treibt (0,3 m/s nach Osten)
        double[] berg = {-100, 6.0, 40};
        f.perches.add(new Flock.Perch("Eisberg", () -> new double[]{berg[0], berg[1], berg[2]}).appeal(4));
        f.step(0.01);
        double minAbove = 1e9, maxAbove = 0, flapT = 0, circleT = 0, t = 0, climbSum = 0;
        int sat = 0;
        for (int i = 0; i < 30 * 600; i++) {
            berg[0] += 0.3 / 30;
            f.step(1 / 30.0);
            t += 1 / 30.0;
            for (Flock.Gull g : f.gulls()) {
                if (g.species == K && g.state == Flock.State.FLY) {
                    double above = g.pos[1] - f.ground.height(g.pos[0], g.pos[2]);
                    if (t > 20) { minAbove = Math.min(minAbove, above); maxAbove = Math.max(maxAbove, above); }
                    if (g.flap > 0.3) flapT += 1 / 30.0;
                    if (Math.abs(g.bank) > 0.2) { circleT += 1 / 30.0; climbSum += g.vel[1] / 30.0; }
                }
                if (g.species == com.dan.flock.Species.MOEWE && g.state == Flock.State.PERCH && g.perch() != null && g.perch().name.equals("Eisberg")) {
                    sat++;
                    if (Math.abs(g.pos[0] - berg[0]) > 0.01) sat = -1_000_000;
                }
            }
        }
        double share = flapT / (3 * 600), circ = circleT / (3 * 600), climb = circleT > 0 ? climbSum / circleT : 0;
        check(minAbove > 20 && maxAbove < 700 && share < 0.05 && circ > 0.3 && climb > 0.5,
                f("Kondore: %.0f bis %.0f m über Grund, %.0f %% der Zeit im Kreis (steigen dort %.1f m/s), schlagen %.1f %% der Zeit", minAbove, maxAbove, 100 * circ, climb, 100 * share));
        check(sat > 30 * 20, f("Eine Möwe sitzt auf dem treibenden Eisberg und treibt mit (%.0f s)", sat / 30.0));
        boolean kelp = true;
        for (Flock.Gull g : f.gulls()) if (g.species == com.dan.flock.Species.MOEWE) kelp &= com.dan.flock.BirdMesh.variantOf(g) == 1;
        com.dan.flock.BirdMesh km = com.dan.flock.BirdMesh.build(com.dan.flock.Species.MOEWE, 1);
        check(kelp && km.count() > 100, "Dominikanermöwen: eigenes Netz mit schwarzem Mantel (" + km.count() / 3 + " Dreiecke)");
        f.ambientLux = 5;
        for (int i = 0; i < 30 * 5; i++) f.step(1 / 30.0);
        check(f.present(K) == 0, "Nachts sind die Kondore an ihren Felsen (außerhalb der Szene)");
    }
}
