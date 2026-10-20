package com.dan.rungholt.gl;

import com.dan.rungholt.scene.Land1362;
import com.dan.rungholt.scene.Mandraenke;
import com.dan.rungholt.scene.Stadt1362;
import com.dan.rungholt.scene.Terrain;
import com.dan.soundscape.AudioOut;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Soundscape.Kind;
import com.dan.soundscape.Stream;
import com.dan.soundscape.Torrent;
import com.dan.tide.Flood;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 14: der Klang der Szene (F23 A) mit {@code com.dan.soundscape}. Diese Klasse verdrahtet: Ohr = Kamera; Wind,
 * Regen, Temperatur aus der Regie; die See nach dem Wasser in der Nähe (bei Ebbe hört man sie nur von fern); das
 * Knistern des Watts nach der Fläche, die um das Ohr frisch trockengefallen ist; die Priele nach der Strömung der
 * Überflutung; die Rufe der Wattvögel ({@code flock.Waders.onCall}) und der Seehunde ({@code seal.onCall}); der
 * Donner der Blitze; 1362 die Herdfeuer, Rinder und Schafe, die Pfähle der Kaje, die Glocke der Kirche zu Mittag und
 * am Abend; in der Mandränke Brecher am Deich, der Bruch, das Wasser durch die Bresche und das Sturmläuten; heute in
 * Sturmnächten die Glocken aus dem Watt (die Sage, {@link com.dan.rungholt.scene.Glocken}), vom Wind getragen.
 * <p>
 * Alles Planen läuft in Echtzeit (auch im Zeitraffer klingt eine Glocke wie eine Glocke). {@link #feed} wird vom
 * Bildlauf und von {@code tools.Hoerproben} gerufen; den Ton gibt {@link AudioOut} in einem eigenen Faden aus.
 */
public final class Klang {
    /**
     * Die Überflutung rechnet die Strömung als Mittel über 16-m-Zellen; in der Rinne eines Priels läuft sie schneller
     * als im Mittel über Rinne und Rand. Für den Klang darum die doppelte Zellgeschwindigkeit [G].
     */
    public static final double RINNE = 2;
    public final Soundscape s = new Soundscape();
    private final Renderer r;
    private AudioOut out;
    public volatile boolean on;
    private final Torrent breach = new Torrent("Bresche", 0, 0, 0);
    private final Stream[] priele = {new Stream("Priel 1"), new Stream("Priel 2"), new Stream("Priel 3"), new Stream("Priel 4")};
    private final SplittableRandom rng = new SplittableRandom(1362);
    private double beastT = 5, breakerT, chunkT, stormBellT, wattBellT, swarmT, bellT;
    private int bellsLeft, bellTone;
    private Mandraenke.Stage lastStage;
    private Object hearthsFor;
    private boolean pilingsSet;
    private final com.dan.soundscape.Workshop[] quay = new com.dan.soundscape.Workshop[2];
    private com.dan.soundscape.Crowd quayCrowd;
    private long lastAngelusKey = -1;
    /** Was zuletzt geplant wurde (für HUD und Prüfung). */
    public volatile String note = "";
    public volatile int wattBells, stormBells, angelusBells, beastCalls, birdCalls, sealCalls, breakers;
    public volatile double crackle, seaFactor;

    Klang(Renderer r) {
        this.r = r;
        s.seed = 1362;
        s.quality = Soundscape.Quality.MEDIUM;
        s.windCarry = true;
        s.torrents.add(breach);
        for (Stream p : priele) s.streams.add(p);
        r.colony.seal.onCall = (x, z, str) -> {
            if (r.wiring == null || r.wiring.era.is1362()) return;
            sealCalls++;
            ev(Math.abs(str - 0.5) < 1e-6 ? Kind.HEULER : Kind.SEEHUND, x, r.groundHeight(x, z) + 0.3, z, str > 0.55 ? 1 : 0.7);
        };
        r.waders.onCall = (x, y, z, str, sp) -> {
            if (r.wiring == null || r.wiring.era.is1362()) return;
            birdCalls++;
            bird(sp, x, y, z, Math.min(1, str + 0.2), 6);
        };
        r.lightning.onThunder = th -> run(() -> s.thunder(th.x(), th.y(), th.z(), th.levelDb(), null, null, null, true));
    }

    /** Offline (Hörproben): Ereignisse gleich im rufenden Faden erzeugen, auch ohne Lautsprecher. */
    public volatile boolean offline;
    private final java.util.concurrent.ExecutorService synth = java.util.concurrent.Executors.newSingleThreadExecutor(q -> {
        Thread t = new Thread(q, "Klang-Synthese");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

    /** Ein Ereignis erzeugen lassen: im Bildlauf in einem eigenen Faden (eine Glocke rechnet einige ms), nur bei Ton an. */
    private void run(Runnable job) {
        if (offline) { job.run(); return; }
        if (!on || queued.get() > 24) return;
        queued.incrementAndGet();
        synth.execute(() -> { try { job.run(); } catch (RuntimeException e) { note = "!" + e; } finally { queued.decrementAndGet(); } });
    }

    private void ev(Kind k, double x, double y, double z, double str) { run(() -> s.event(k, x, y, z, str)); }

    private void ev(Kind k, double x, double y, double z, double str, double tone) { run(() -> s.event(k, x, y, z, str, tone)); }

    static Kind kindOf(com.dan.flock.WaderSpecies sp) {
        return switch (sp.key()) {
            case "austernfischer" -> Kind.AUSTERNFISCHER;
            case "alpenstrandlaeufer" -> Kind.ALPENSTRANDLAEUFER;
            case "saebelschnaebler" -> Kind.SAEBELSCHNAEBLER;
            case "brandgans" -> Kind.BRANDGANS;
            case "lachmoewe" -> Kind.LACHMOEWE;
            case "ringelgans" -> Kind.RINGELGANS;
            default -> Kind.MOEWE;   // Silbermöwe
        };
    }

    /** Ein Ruf der Art; bei Alpenstrandläufern und Ringelgänsen rufen mehrere zugleich (bis {@code many}). */
    private void bird(com.dan.flock.WaderSpecies sp, double x, double y, double z, double str, int many) {
        Kind k = kindOf(sp);
        double tone = k == Kind.ALPENSTRANDLAEUFER ? 2 + (many - 2) * rng.nextDouble() : k == Kind.RINGELGANS ? 3 + (many - 3) * rng.nextDouble() : 0.5;
        ev(k, x, y + 0.2, z, str, tone);
    }

    /**
     * Wie oft eine Gruppe am Boden ruft (s zwischen zwei Rufen) [G]: Austernfischer und Möwen oft, Strandläufer
     * leise im Trupp, Brandgänse selten, Ringelgänse murmeln fast ständig.
     */
    static double chatterSeconds(Kind k, boolean rest) {
        double s = switch (k) {
            case AUSTERNFISCHER -> 9;
            case ALPENSTRANDLAEUFER -> 14;
            case SAEBELSCHNAEBLER -> 20;
            case BRANDGANS -> 30;
            case LACHMOEWE -> 10;
            case RINGELGANS -> 5;
            default -> 16;
        };
        return rest ? s * 2.5 : s;
    }

    /** Ton an oder aus (Lautsprecher im eigenen Faden). */
    public synchronized void setOn(boolean b) {
        on = b;
        if (b && out == null) {
            try { out = new AudioOut(s); out.start(); } catch (RuntimeException | LinkageError e) { note = "!kein Ton: " + e.getMessage(); out = null; on = false; }
        } else if (!b && out != null) { out.stop(); out = null; }
    }

    public String describe() {
        return (on ? "Klang an · " : "Klang aus · ") + s.describe() + (note.isEmpty() ? "" : " · " + note);
    }

    /** Je Bild (Echtzeit dt): alle Eingänge setzen, Ereignisse planen. */
    public void feed(double dt) {
        var w = r.wiring;
        if (w == null) return;
        var cam = r.camera;
        double ex = cam.eye[0], ey = cam.eye[1], ez = cam.eye[2];
        s.earX = ex; s.earY = ey; s.earZ = ez;
        s.earYawDeg = cam.heading();
        s.windSpeed = w.wind.speed();
        s.windFromDeg = w.wind.fromDeg();
        s.gustiness = w.regie ? 0.18 + 0.12 * Math.min(1, w.wind.speed() / 20) : 0.2;
        s.rainRate = w.rainRate;
        s.temperatureC = w.airTempC;
        s.waterLevel = cam.waterLevel;
        s.inside = 0;
        boolean old = w.era.is1362();
        Terrain land = r.land();
        Flood f = r.flood();

        // See: so laut wie das Wasser nah ist (bei Ebbe nur von fern)
        double dw = nearestWater(ex, ez, f);
        var os = r.ocean.state();
        double hs = os == null ? 0.3 : os.totalHs();
        seaFactor = Math.max(0, 1 - dw / 900) * (dw < 15 ? 1 : 15 / Math.max(15, dw) * 0.6 + 0.4);
        s.waveHeight = Math.min(hs, 0.45 * waterDepth) * seaFactor;   // im flachen Priel bricht keine hohe See
        // Brandung: steht das Ohr auf Deich, Warft oder Vorland am offenen Wasser, brechen die Wellen an der Kante vor ihm
        byte artHere = land.artAt(ex, ez);
        boolean hard = artHere == Terrain.ERHOEHT || artHere == Terrain.DEICH1362 || artHere == Terrain.WARFT || artHere == Terrain.VORLAND || artHere == Terrain.KOOG;
        if (hard && dw > 0 && dw < 250 && !Double.isNaN(edgeX) && hs > 0.3) {
            s.surfX = edgeX; s.surfZ = edgeZ; s.surfRadius = 40;
            s.surfStrength = Math.min(1, hs / 1.5);
        } else s.surfRadius = 0;
        s.wavePeriod = os == null ? 4 : Math.max(2, os.peakPeriod);

        // Knistern: frisch trockengefallenes Watt um das Ohr (heute, nah am Boden)
        double cr = 0;
        if (!old && f != null && ey - cam.groundLevel < 25) {
            int dry = 0, tot = 0;
            for (int k = 0; k < 16; k++) {
                double a = k * 2.39996, rad = 4 + 36 * Math.sqrt((k + 0.5) / 16);
                double x = ex + Math.cos(a) * rad, z = ez + Math.sin(a) * rad;
                if (land.artAt(x, z) != Terrain.WATT) continue;
                tot++;
                double dh = f.dryHoursAt(x, z);
                if (f.depthAt(x, z) <= 0.01 && dh > 0.05 && dh < 7) dry++;
            }
            cr = tot == 0 ? 0 : dry / 16.0 * (1 - Math.min(1, (ey - cam.groundLevel) / 25));
        }
        crackle = cr;
        s.mudCrackle = cr;

        // Priele: die vier stärksten Strömungen in 500 m
        double[][] best = new double[4][];
        if (f != null) {
            for (int k = 0; k < 120; k++) {   // dicht am Ohr, locker in der Ferne
                double a = k * 2.39996, rad = 6 + 494 * Math.pow((k + 0.5) / 120, 1.6);
                double x = ex + Math.cos(a) * rad, z = ez + Math.sin(a) * rad;
                double dep = f.depthAt(x, z);
                if (dep < 0.25) continue;
                double[] v = f.velocityAt(x, z);
                double sp = Math.hypot(v[0], v[1]);
                if (sp < 0.08) continue;
                double score = sp / (1 + rad / 120);
                int worst = -1;
                for (int i = 0; i < 4; i++) if (best[i] == null) { worst = i; break; }
                if (worst < 0) { worst = 0; for (int i = 1; i < 4; i++) if (best[i][3] < best[worst][3]) worst = i; if (best[worst][3] >= score) worst = -1; }
                if (worst >= 0) best[worst] = new double[]{x, z, sp, score, dep};
            }
        }
        for (int i = 0; i < 4; i++) {
            Stream p = priele[i];
            if (best[i] == null) { p.speed = 0; continue; }
            // die Quelle an die Kante des Priels zum Ohr hin (dort gluckst es; die Mitte der Zelle liegt im Strom)
            double qx = best[i][0], qz = best[i][1], ux = ex - qx, uz = ez - qz, ul = Math.max(1e-6, Math.hypot(ux, uz));
            for (int st = 0; st < 40 && ul > 4 && f.depthAt(qx, qz) > 0.2; st++) { qx += ux / ul * 4; qz += uz / ul * 4; }
            p.x = qx; p.z = qz; p.y = r.groundHeight(qx, qz) + 0.1;
            p.speed = best[i][2] * RINNE;
            p.width = 8 + 12 * Math.min(4, best[i][4]);
        }

        // Halme und Reet, Rauigkeit, Dämpfung über Land
        byte art = land.artAt(ex, ez);
        s.reedNear = art == Terrain.VORLAND ? 0.6 : art == Terrain.MARSCH || art == Terrain.WARFT || art == Terrain.DEICH1362 ? 0.25 : art == Terrain.KOOG || art == Terrain.ERHOEHT ? 0.2 : 0;
        Renderer.Town tw = r.town;
        boolean townNow = old && tw != null && r.showTown;
        s.roughness = townNow && Math.hypot(ex, ez) < 1500 ? 0.3 : art == Terrain.WATT ? 0.0005 : 0.03;
        s.obstruction = townNow && Math.hypot(ex, ez) < 1500 ? 3 : 0;
        double nearHouse = 1e9;
        if (townNow) for (var h : tw.houses) nearHouse = Math.min(nearHouse, Math.hypot(h.x() - ex, h.z() - ez));
        s.thatchNear = townNow ? Math.max(0, 1 - nearHouse / 25) : 0;

        // 1362: Herdfeuer, Vieh, Kaje, die Glocke
        Mandraenke md = r.mandraenke;
        if (townNow) {
            var src = r.smoke.sources();
            if (hearthsFor != src) {
                s.blazes.clear();
                if (src != null) for (var h : src) s.blazes.add(new com.dan.soundscape.Blaze("Herd", h.x(), h.y() - 3.5, h.z(), 0.25, false));
                hearthsFor = src;
            }
            if (!pilingsSet) {
                double[] hb = tw.basin;
                double ax = hb[2], az = hb[3], qx = -az, qz = ax;
                if (qx < 0) { qx = -qx; qz = -qz; }
                double off = Land1362.BASIN_ACROSS + 1;
                for (double t = -30; t <= 30; t += 2.5) s.pilings.add(new double[]{hb[0] + qx * off + ax * t, hb[1] + qz * off + az * t});
                // an der Kaje [G]: ein Schiffszimmerer mit dem Beil, ein Sägeplatz, Leute beim Laden
                double kx = hb[0] + qx * (off + 8), kz = hb[1] + qz * (off + 8), ky = Land1362.estimate(kx, kz);
                quay[0] = new com.dan.soundscape.Workshop("Schiffszimmerer", com.dan.soundscape.Workshop.Craft.ZIMMERER, kx + ax * 22, ky + 0.8, kz + az * 22);
                quay[1] = new com.dan.soundscape.Workshop("Sägeplatz", com.dan.soundscape.Workshop.Craft.SAEGE, kx - ax * 26, ky + 0.8, kz - az * 26);
                quayCrowd = new com.dan.soundscape.Crowd("Kaje", kx, ky + 1.6, kz, 6);
                quayCrowd.radius = 18;
                s.workshops.add(quay[0]);
                s.workshops.add(quay[1]);
                s.crowds.add(quayCrowd);
                pilingsSet = true;
            }
            boolean drowned = md != null && md.stage.ordinal() >= Mandraenke.Stage.FLUT.ordinal();
            boolean work = r.clock.sun()[0] > -3 && (md == null || md.stage == Mandraenke.Stage.VORABEND) && w.wind.speed() < 14;
            for (var q : quay) if (q != null) q.active = work;
            if (quayCrowd != null) quayCrowd.people = work ? 6 : 0;
            beastT -= dt;
            if (beastT <= 0 && !drowned) {
                beastT = 3 + 9 * rng.nextDouble();
                // eins der nächsten Tiere ruft (unter den acht nächsten zufällig)
                var bs = tw.beasts;
                Stadt1362.Beast pick = null;
                double bd = 1e9;
                java.util.PriorityQueue<double[]> near = new java.util.PriorityQueue<>((p1, p2) -> Double.compare(p2[0], p1[0]));
                for (int k = 0; k < bs.size(); k++) {
                    var b = bs.get(k);
                    near.add(new double[]{Math.hypot(b.x() - ex, b.z() - ez), k});
                    if (near.size() > 8) near.poll();
                }
                if (!near.isEmpty()) {
                    double[][] arr = near.toArray(new double[0][]);
                    double[] c = arr[rng.nextInt(arr.length)];
                    pick = bs.get((int) c[1]);
                    bd = c[0];
                }
                if (pick != null && bd < 1200) { beastCalls++; ev(pick.kind() == 0 ? Kind.RIND : Kind.SCHAF, pick.x(), r.groundHeight(pick.x(), pick.z()) + 1, pick.z(), 0.5 + 0.5 * rng.nextDouble()); }
            }
            // Angelus [G]: um 6, 12 und 18 Uhr Ortszeit eine Minute Läuten (nicht in der Mandränke)
            var lt = r.clock.local();
            int hr = lt.getHour();
            long key = lt.toLocalDate().toEpochDay() * 24 + hr;
            if (md == null && (hr == 6 || hr == 12 || hr == 18) && lt.getMinute() < 2 && key != lastAngelusKey) { lastAngelusKey = key; bellsLeft = 40; bellT = 0; }
        } else {
            if (!s.blazes.isEmpty()) s.blazes.clear();
            hearthsFor = null;
            if (pilingsSet) { s.pilings.clear(); s.workshops.clear(); s.crowds.clear(); pilingsSet = false; }
            bellsLeft = 0;
        }
        double[] tower = Stadt1362.tower(Land1362::estimate);
        if (bellsLeft > 0) {
            bellT -= dt;
            if (bellT <= 0) {
                bellsLeft--;
                angelusBells++;
                ev(Kind.GLOCKE, tower[0], tower[1], tower[2], 0.8 + 0.2 * rng.nextDouble(), bellTone);
                bellTone = 1 - bellTone;
                bellT = 1.25 + 0.1 * rng.nextDouble();   // zwei Glocken im Wechsel, gleichmäßig geschwungen
            }
        }

        // Die Mandränke: Brecher am Deich, der Bruch, das Wasser durch die Bresche, Sturmläuten
        breach.flow = 0;
        if (md != null && old) {
            double sea = r.level();
            double[] dp = nearestDike(ex, ez);
            double over = sea - (Land1362.CREST - 1.5);
            if (md.stage.ordinal() >= Mandraenke.Stage.STURM.ordinal() && over > 0 && dp[2] < 1500) {
                breakerT -= dt;
                if (breakerT <= 0) {
                    breakerT = s.wavePeriod * (0.6 + 0.8 * rng.nextDouble());
                    breakers++;
                    // die See steht seeseitig am Deich: der Brecher ein Stück vor der Krone
                    ev(Kind.BRECHER, dp[0] + dp[3] * 6, sea, dp[1] + dp[4] * 6, Math.min(1, 0.4 + over / 2));
                }
            }
            double bx = md.stelle.x, bz = Stadt1362.dikeZ(bx);
            if (md.stage != lastStage && md.stage == Mandraenke.Stage.BRUCH) ev(Kind.EINSTURZ, bx, Land1362.CREST, bz, 1);
            if (md.stage == Mandraenke.Stage.BRUCH) {
                chunkT -= dt;
                if (chunkT <= 0) { chunkT = 0.7 + 2.2 * rng.nextDouble(); ev(Kind.AUFSCHLAG, bx + (rng.nextDouble() - 0.5) * 40, Land1362.CREST - 1, bz, 0.8, 20 + 60 * rng.nextDouble()); }
            }
            if (md.stage.ordinal() >= Mandraenke.Stage.BRUCH.ordinal()) {
                double pol = Double.isNaN(md.polder) ? 0.6 : md.polder, h = Math.max(0, sea - Math.max(pol, 0.6));
                breach.x = bx; breach.z = bz; breach.y = sea;
                breach.flow = md.stage == Mandraenke.Stage.MORGEN ? 0 : 50 * 1.7 * Math.pow(h, 1.5) + (md.stage == Mandraenke.Stage.BRUCH ? 300 : 0);
            }
            lastStage = md.stage;
            // Sturmläuten vom Kirchturm, unregelmäßig, bis das Wasser in den Turm steigt
            boolean ring = md.stage.ordinal() >= Mandraenke.Stage.STURM.ordinal() && md.stage.ordinal() <= Mandraenke.Stage.FLUT.ordinal() && tw != null && !tw.people.hidden;
            if (ring) {
                stormBellT -= dt;
                if (stormBellT <= 0) {
                    stormBells++;
                    ev(Kind.GLOCKE, tower[0], tower[1], tower[2], 0.6 + 0.4 * rng.nextDouble(), stormBells % 3 == 0 ? 0 : 1);
                    stormBellT = 0.35 + 1.4 * rng.nextDouble() * rng.nextDouble();   // gerissen, hastig, ungleich
                }
            }
        } else lastStage = null;

        // Heute: die Glocken aus dem Watt, solange sie nach der Sage läuten (ungleiche Schläge, wie vom Wind geschwungen)
        var gl = r.glocken;
        if (!old && gl.ringing) {
            wattBellT -= dt;
            if (wattBellT <= 0) {
                wattBells++;
                double y = w.level.depthAt(0, 0) > 0 ? w.level.surfaceAt(0, 0) : r.groundHeight(0, 0);
                ev(Kind.GLOCKE_WATT, 0, y + 6, 0, 0.6 + 0.4 * rng.nextDouble(), rng.nextDouble() < 0.35 ? 0 : 1);
                wattBellT = 1.3 + 2.2 * rng.nextDouble();
            }
        } else wattBellT = 0.2;

        // Wattvögel am Boden rufen beim Fressen und Rasten; ein wendender Schwarm in der Nähe rauscht mit den Flügeln
        if (!old && r.showBirds) {
            swarmT -= dt;
            boolean swarm = swarmT <= 0;
            if (swarm) swarmT = 1.4;
            for (var g : r.waders.groups()) {
                if (g.birds.isEmpty()) continue;
                var m = g.mode;
                if (m == com.dan.flock.Waders.Mode.SCHWARM) {
                    var b = g.birds.get(0);
                    if (swarm && Math.hypot(b.x - ex, b.z - ez) < 400) { ev(Kind.SCHWARM, b.x, b.y, b.z, 1, g.size()); swarm = false; }
                    continue;
                }
                if (m != com.dan.flock.Waders.Mode.FRESSEN && m != com.dan.flock.Waders.Mode.RASTEN) continue;
                double d = Math.hypot(g.cx - ex, g.cz - ez);
                if (d > 700) continue;
                double per = chatterSeconds(kindOf(g.species), m == com.dan.flock.Waders.Mode.RASTEN) / Math.sqrt(Math.max(1, g.size() / 8.0));
                if (rng.nextDouble() < dt / per) {
                    birdCalls++;
                    var b = g.birds.get(rng.nextInt(g.size()));
                    bird(g.species, b.x, b.y, b.z, 0.35 + 0.4 * rng.nextDouble(), Math.max(3, Math.min(12, g.size() / 4)));
                }
            }
        }
        note = String.format(Locale.GERMAN, "See %.0f %% (Wasser %.0f m) · Knistern %.0f %% · Priele %s", seaFactor * 100, dw, cr * 100,
                best[0] == null ? "still" : String.format(Locale.GERMAN, "bis %.1f m/s", Math.max(best[0][2], best[1] == null ? 0 : best[1][2])));
    }

    /** Abstand zum nächsten offenen Wasser (m, höchstens 2 000), aus der Überflutung in Ringen um das Ohr. */
    private double waterDepth = 20, edgeX = Double.NaN, edgeZ = Double.NaN;

    double nearestWater(double x, double z, Flood f) {
        if (f == null) return 0;
        waterDepth = 20;
        edgeX = edgeZ = Double.NaN;
        // im Wasser nur, wenn der feine Grund am Ohr wirklich unter dem Spiegel liegt (die Zelle von 16 m reicht über die Deichkrone)
        if (f.depthAt(x, z) > 0.5 && r.wiring.level.surfaceAt(x, z) > r.groundHeight(x, z) + 0.5) { waterDepth = f.depthAt(x, z); return 0; }
        // offenes Wasser: ein Priel von 30 m zählt nicht als See (mindestens 4 von 12 Richtungen nass und tiefer als 1 m)
        for (double rad : new double[]{15, 40, 80, 150, 300, 500, 800, 1200, 2000}) {
            int wet = 0;
            double dep = 0;
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6, d = f.depthAt(x + Math.cos(a) * rad, z + Math.sin(a) * rad);
                if (d > 1.0) { wet++; dep = Math.max(dep, d); }
            }
            if (wet >= 4) {
                waterDepth = dep;
                if (Double.isNaN(edgeX)) for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI / 6;
                    if (f.depthAt(x + Math.cos(a) * rad, z + Math.sin(a) * rad) > 1.0) { edgeX = x + Math.cos(a) * rad; edgeZ = z + Math.sin(a) * rad; break; }
                }
                return rad;
            }
            if (wet > 0 && Double.isNaN(edgeX)) for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                if (f.depthAt(x + Math.cos(a) * rad, z + Math.sin(a) * rad) > 1.0) { edgeX = x + Math.cos(a) * rad; edgeZ = z + Math.sin(a) * rad; break; }
            }
        }
        return 2000;
    }

    /** Nächster Punkt der Deichlinie von 1362: x, z, Abstand, Richtung zur See (Einheitsvektor). */
    static double[] nearestDike(double x, double z) {
        double[][] d = Land1362.DIKE;
        double best = 1e18, bx = 0, bz = 0, nx = 0, nz = 1;
        for (int i = 1; i < d.length; i++) {
            double ax = d[i - 1][0], az = d[i - 1][1], vx = d[i][0] - ax, vz = d[i][1] - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            double px = ax + vx * t, pz = az + vz * t, dd = Math.hypot(x - px, z - pz);
            if (dd < best) { best = dd; bx = px; bz = pz; double l = Math.hypot(vx, vz); nx = vz / l; nz = -vx / l; if (nz < 0) { nx = -nx; nz = -nz; } }
        }
        return new double[]{bx, bz, best, nx, nz};
    }
}
