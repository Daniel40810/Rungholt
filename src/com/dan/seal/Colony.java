package com.dan.seal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Eine Gruppe Seehunde an ihren Bänken (Phase 7). Die Kolonie kennt kein Watt: Grund und Wasser fragt sie bei der
 * {@link World} ab, die Bänke ({@link Bank}) und die Störer ({@link Disturber}) bekommt sie von außen. Sie rechnet in
 * Szenenzeit (Sekunden) mit Schritten von höchstens einer Sekunde und gibt je Tier Lage und Haltung für die Grafik.
 * <p>
 * <b>Ablauf einer Tide</b>: Bei Hochwasser schwimmen alle in der Nähe der Bank, die Köpfe über der Fläche, ab und zu
 * tauchen sie. Fällt die Bank trocken, warten sie je nach Tier einige Minuten, dann robben sie aus dem Wasser an einen
 * freien Platz nahe am Wasser, mit Abstand zum Nachbarn und zur Gruppe hin, und legen sich mit dem Kopf zum Wasser.
 * Dort ruhen sie, heben in Abständen den Kopf, liegen in der Sonne als Banane auf der Seite. Läuft das Wasser weit weg,
 * rücken einige nach. Steigt das Wasser über ihren Platz, gehen sie mit der Flut. Ein Teil bleibt zum Fressen im
 * Wasser, nachts mehr [G].
 * <p>
 * <b>Störung</b>: Kommt ein Störer näher als das {@link Seal#alertFactor}-fache der Fluchtdistanz, heben alle die Köpfe;
 * unter der Fluchtdistanz robben sie so schnell sie können ins nächste Wasser vom Störer weg, und wer flieht, reißt die
 * Nachbarn mit. Danach bleiben sie eine Weile im Wasser ({@link Seal#calmMinutes}).
 * <p>
 * <b>Heuler</b>: Im Juni und Juli hat ein Teil der Weibchen ein Junges, das bei der Mutter liegt, ihr folgt und ruft.
 */
public final class Colony {
    /** Der Ort, den die Kolonie abfragt: Höhe des Grunds und Wasserspiegel (NaN, wo es trocken ist), m über Null. */
    public interface World {
        double ground(double x, double z);
        double water(double x, double z);
    }

    /** Eine Bank: Mitte (m), Halbmesser (m). */
    public record Bank(double x, double z, double radius) { }

    /** Ein Störer: Ort (m), Stärke 0..1 (eine Figur 1, ein Boot 1, ein Hund mehr …). */
    public record Disturber(double x, double z, double strength) { }

    /** Eine Spur im Watt: Ort und Richtung des robbenden Tiers. */
    @FunctionalInterface public interface TrackSink { void track(double x, double z, double dirX, double dirZ); }

    public enum Activity {
        SCHWIMMEN("schwimmt"), TAUCHEN("taucht"), ANLANDEN("robbt an Land"), LIEGEN("liegt"), UMLEGEN("rückt nach"), FLUCHT("flüchtet");
        public final String label;
        Activity(String l) { label = l; }
    }

    /** Ein Tier. Lage in Szenenmetern, Richtung der Nase als Azimut (rad, 0 Nord = −z, π/2 Ost = +x). */
    public static final class Animal {
        public final int id;
        public final boolean pup;
        public final int mother;
        public final double length;
        /** Fell 0..1 (Startwert der Flecken und der Färbung). */
        public final float tone;
        public double x, y, z, heading;
        public Activity act = Activity.SCHWIMMEN;
        /** Haltung wie {@link SealBody} (jetzt und Ziel). */
        public final float[] pose = new float[SealBody.N], goal = new float[SealBody.N];
        /** Nässe des Fells 0..1. */
        public double wet = 1;
        /** Liegeform: 0 ruhen, 1 Kopf hoch, 2 Banane. */
        public int rest;
        double wx, wz, spotCool, timer, restTimer, tx, tz, burst, pause, gait, haulWait, delay, prefWater, calm, loiterX, loiterZ, trackAcc, callTimer, side, lookPhase, lowCache;
        boolean forager, crawling, hasTarget;
        int epoch = -1;
        final float[] lowPose = new float[SealBody.N];

        Animal(int id, boolean pup, int mother, double length, float tone) {
            this.id = id;
            this.pup = pup;
            this.mother = mother;
            this.length = length;
            this.tone = tone;
            lowCache = Double.NaN;
        }

        /** Auf dem Trockenen (liegt, robbt an Land oder flüchtet an Land)? */
        public boolean onLand() { return act == Activity.LIEGEN || ((act == Activity.ANLANDEN || act == Activity.UMLEGEN || act == Activity.FLUCHT) && crawling); }

        /** Die Haltung, die man sieht. */
        public Seal.Pose pose() {
            return switch (act) {
                case SCHWIMMEN -> Seal.Pose.SCHWIMMEN;
                case TAUCHEN -> Seal.Pose.TAUCHEN;
                case ANLANDEN, UMLEGEN, FLUCHT -> crawling ? (pause > 0 ? Seal.Pose.KOPF_HEBEN : Seal.Pose.ROBBEN) : Seal.Pose.SCHWIMMEN;
                case LIEGEN -> rest == 2 ? Seal.Pose.BANANE : rest == 1 ? Seal.Pose.KOPF_HEBEN : Seal.Pose.LIEGEN;
            };
        }
    }

    public final Seal seal;
    public final List<Bank> banks = new ArrayList<>();
    /** Störer jetzt (die App setzt sie vor jedem Schritt). */
    public final List<Disturber> disturbers = new ArrayList<>();
    public World world;
    public TrackSink onTrack;
    /** Monat (1..12) und Sonnenhöhe (Grad) für Heuler, Banane und Nacht. */
    public volatile int month = 7;
    public volatile double sunAltDeg = 30;
    /** Zahl der Tiere (ohne Junge); 0 = keine. */
    public volatile int count = 36;
    /** Längster Teilschritt (s). */
    public static final double MAX_STEP = 1.0;

    private final List<Animal> animals = new ArrayList<>();
    private SplittableRandom rng;
    private double time, bankTimer, bankShare;
    private boolean bankAvailable, flooding;
    private int epoch, builtCount = -1;
    private boolean builtPups;
    private long builtSeed;
    private double lastFleeDist = Double.NaN;
    /** Zähler für die Prüfung. */
    public int flights, haulOuts, departures, tracks, calls, moves;

    public Colony(Seal seal) { this.seal = seal; rng = new SplittableRandom(seal.seed); }

    public List<Animal> animals() { return Collections.unmodifiableList(animals); }
    public double time() { return time; }
    public boolean bankAvailable() { return bankAvailable; }
    /** Anteil der Bänke, der trocken liegt (0..1). */
    public double bankShare() { return bankShare; }

    // ------------------------------------------------------------------ Aufbau

    /** Tiere neu anlegen (Zahl, Junge nach dem Monat), alle im Wasser um die Bänke. */
    public void reset() {
        animals.clear();
        rng = new SplittableRandom(seal.seed);
        time = 0;
        flights = haulOuts = departures = tracks = calls = moves = 0;
        int n = Math.max(0, Math.min(seal.maxAnimals, count));
        boolean pups = seal.pupSeason(month);
        int id = 0;
        for (int i = 0; i < n && animals.size() < seal.maxAnimals; i++) {
            double len = seal.length * (0.82 + 0.26 * rng.nextDouble());
            Animal a = new Animal(id++, false, -1, len, (float) rng.nextDouble());
            init(a);
            animals.add(a);
            // ein Viertel der Weibchen hat im Juni und Juli ein Junges [G]
            if (pups && rng.nextDouble() < 0.28 && animals.size() < seal.maxAnimals) {
                Animal p = new Animal(id++, true, a.id, seal.pupLength * (0.92 + 0.16 * rng.nextDouble()), (float) rng.nextDouble());
                init(p);
                p.x = a.x + 1.5;
                p.z = a.z;
                animals.add(p);
            }
        }
        builtCount = count;
        builtPups = pups;
        builtSeed = seal.seed;
        bankTimer = 0;
        epoch = 0;
    }

    private void init(Animal a) {
        Bank b = banks.isEmpty() ? new Bank(0, 0, 50) : banks.get(rng.nextInt(banks.size()));
        double ang = rng.nextDouble() * 2 * Math.PI, r = b.radius() + 30 + 120 * rng.nextDouble();
        a.x = b.x() + Math.cos(ang) * r;
        a.z = b.z() + Math.sin(ang) * r;
        a.loiterX = a.x;
        a.loiterZ = a.z;
        a.heading = rng.nextDouble() * 2 * Math.PI;
        a.delay = 60 * (seal.haulDelayMin + (seal.haulDelayMax - seal.haulDelayMin) * Math.pow(rng.nextDouble(), 1.5));
        a.prefWater = seal.nearWaterMin + (seal.nearWaterMax - seal.nearWaterMin) * rng.nextDouble();
        a.side = rng.nextBoolean() ? 1 : -1;
        a.timer = 20 + 100 * rng.nextDouble();
        a.callTimer = 20 + 60 * rng.nextDouble();
        a.lookPhase = rng.nextDouble() * 6.28;
        a.act = Activity.SCHWIMMEN;
        a.wet = 1;
    }

    /** Muss neu angelegt werden (Zahl, Startwert oder Zeit der Jungen geändert)? */
    public boolean stale() { return builtCount != count || builtPups != seal.pupSeason(month) || builtSeed != seal.seed; }

    /**
     * Sofort in einen Zustand wie nach einer Weile (nach Sprüngen der Uhr und beim Start): Liegt die Bank trocken und
     * ist kein Störer nah, liegen die meisten schon an ihren Plätzen; die übrigen schwimmen um die Bank.
     */
    public void settle() {
        if (world == null) return;
        if (stale() || animals.isEmpty()) reset();
        updateBank(true);
        double night = sunAltDeg < -3 ? 1 : 0;
        List<Animal> placed = new ArrayList<>();
        for (Animal a : animals) {
            a.lowCache = Double.NaN;
            a.calm = 0;
            a.haulWait = 0;
            a.forager = rng.nextDouble() < (night > 0 ? seal.stayInWaterNight : seal.stayInWaterDay);
            a.epoch = epoch;
            if (a.pup) continue;
            boolean land = bankAvailable && !a.forager && nearestDisturber(a.x, a.z)[0] > seal.flightDistance * seal.alertFactor;
            if (land) {
                double[] s = findSpot(a);
                if (s != null) {
                    a.x = s[0];
                    a.z = s[1];
                    lieDown(a);
                    a.rest = rng.nextDouble() < 0.2 ? 1 : (sunAltDeg > 15 && rng.nextDouble() < 0.25 ? 2 : 0);
                    a.restTimer = 30 + 200 * rng.nextDouble();
                    a.wet = 0.2 + 0.6 * rng.nextDouble();
                    placed.add(a);
                    continue;
                }
            }
            toWater(a, false);
            double[] w = waterPoint(a, true);
            if (w != null) { a.x = w[0]; a.z = w[1]; }
            a.wet = 1;
        }
        for (Animal p : animals) if (p.pup) {
            Animal m = byId(p.mother);
            if (m == null) continue;
            p.act = m.act;
            double[] f = followPoint(p, m);
            p.x = f[0];
            p.z = f[1];
            p.heading = m.heading;
            p.rest = 0;
            p.wet = m.wet;
            if (m.act == Activity.LIEGEN) lieDown(p);
        }
        for (Animal a : animals) { setGoal(a, 0); System.arraycopy(a.goal, 0, a.pose, 0, SealBody.N); }
        place();
    }

    // ------------------------------------------------------------------ Schritt

    /** Szenenzeit dt (s) weiterrechnen, in Teilschritten von höchstens {@link #MAX_STEP}. */
    public void step(double dt) {
        if (world == null || dt <= 0) return;
        if (stale() || animals.isEmpty()) { reset(); settle(); }
        int n = (int) Math.ceil(dt / MAX_STEP);
        double h = dt / n;
        for (int i = 0; i < n; i++) sub(h);
        place();
    }

    private void sub(double dt) {
        time += dt;
        bankTimer -= dt;
        if (bankTimer <= 0) updateBank(false);
        boolean night = sunAltDeg < -3;
        for (Animal a : animals) {
            if (a.epoch != epoch) {   // die Bank ist wieder aufgetaucht: neu würfeln, wer im Wasser bleibt
                a.epoch = epoch;
                a.forager = rng.nextDouble() < (night ? seal.stayInWaterNight : seal.stayInWaterDay);
                a.haulWait = 0;
            }
            if (a.pup) pupStep(a, dt); else adultStep(a, dt);
            // Fell: im Wasser nass, an Land trocknet es (in der Sonne schneller)
            if (a.act == Activity.LIEGEN) {
                double half = seal.furHalfMinutes * 60 * (sunAltDeg > 10 ? 1 : 3);
                a.wet *= Math.pow(0.5, dt / half);
            } else if (!a.crawling) a.wet = 1;
            a.callTimer -= dt;
            if (a.callTimer <= 0) {
                a.callTimer = a.pup ? 20 + 70 * rng.nextDouble() : 120 + 600 * rng.nextDouble();
                if (a.pup || (a.act == Activity.LIEGEN && rng.nextDouble() < 0.3)) {
                    calls++;
                    if (seal.onCall != null) seal.onCall.call(a.x, a.z, a.pup ? 0.5 : 0.3);
                }
            }
            setGoal(a, dt);
            double k = 1 - Math.exp(-dt / (a.crawling && a.pause <= 0 ? 0.12 : 0.9));
            for (int j = 0; j < SealBody.N; j++) a.pose[j] += (a.goal[j] - a.pose[j]) * k;
        }
    }

    private void adultStep(Animal a, double dt) {
        double[] dist = nearestDisturber(a.x, a.z);
        double d = dist[0], fd = seal.flightDistance * Math.max(0.2, dist[3]);
        boolean alert = d < fd * seal.alertFactor, alarm = d < fd;
        double depth = depth(a.x, a.z);
        a.calm = Math.max(0, a.calm - dt);
        switch (a.act) {
            case LIEGEN -> {
                if (depth > seal.leaveDepth) { toWater(a, false); departures++; break; }
                if (alarm || panicNear(a)) { flee(a, dist); break; }
                a.timer -= dt;
                a.restTimer -= dt;
                if (alert) a.rest = 1;
                else if (a.restTimer <= 0) nextRest(a);
                // das Wasser ist weit weggelaufen: einige rücken nach
                if (a.timer <= 0) {
                    a.timer = 200 + 300 * rng.nextDouble();
                    if (!alert && distToWater(a.x, a.z, 70) > a.prefWater * 2 + 8 && rng.nextDouble() < 0.75) {
                        double[] s = findSpot(a);
                        if (s != null && Math.hypot(s[0] - a.x, s[1] - a.z) > 4) { target(a, s, Activity.UMLEGEN); moves++; }
                    }
                }
            }
            case SCHWIMMEN -> {
                a.crawling = false;
                if (alert && d < fd * 0.8) {   // im Wasser vom Störer weg
                    a.calm = Math.max(a.calm, seal.calmMinutes * 60 * 0.5);
                    if (a.timer > 30) a.timer = 30;
                }
                a.spotCool -= dt;
                boolean look = a.spotCool <= 0;
                if (look) a.spotCool = 10;
                if (look && depth < 0.3 && bankAvailable && !alert && !a.forager && a.calm <= 0) {   // gestrandet oder am Rand: gleich an Land
                    double[] s = findSpot(a);
                    if (s != null) { target(a, s, Activity.ANLANDEN); break; }
                }
                if (look && depth < 0.25) {   // zu flach: ins tiefere Wasser
                    double[] w = waterPoint(a, true);
                    if (w != null) { a.loiterX = w[0]; a.loiterZ = w[1]; }
                }
                // an Land, wenn die Bank lange genug trocken ist
                if (bankAvailable && !alert && !a.forager && a.calm <= 0) {
                    a.haulWait += dt;
                    if (a.haulWait > a.delay && look) {
                        a.haulWait = a.delay * 0.8;
                        double[] s = findSpot(a);
                        if (s != null) { target(a, s, Activity.ANLANDEN); break; }
                    }
                } else if (!bankAvailable) a.haulWait = 0;
                // treiben und herumschwimmen um die Bank, ab und zu tauchen
                double lx = a.loiterX - a.x, lz = a.loiterZ - a.z, ll = Math.hypot(lx, lz);
                if (ll < 3 || a.timer <= 0) {
                    double[] w = alert ? awayPoint(a, dist) : waterPoint(a, false);
                    if (w != null) { a.loiterX = w[0]; a.loiterZ = w[1]; }
                    a.timer = 60 + 240 * rng.nextDouble();
                    if (!alert && depth > 1.2 && rng.nextDouble() < 0.45) { a.act = Activity.TAUCHEN; a.timer = 30 + 100 * rng.nextDouble(); }
                } else {
                    move(a, lx / ll, lz / ll, Math.min(ll, (alert ? 0.9 : 0.3) * seal.swimSpeed * dt), dt, false);
                }
                a.timer -= dt;
            }
            case TAUCHEN -> {
                a.crawling = false;
                a.timer -= dt;
                double fx = Math.sin(a.heading), fz = -Math.cos(a.heading);
                if (depth(a.x + fx * 3, a.z + fz * 3) > 0.9) move(a, fx, fz, 0.4 * dt, dt, false);
                else a.heading += 0.8 * dt;
                if (a.timer <= 0 || depth < 0.8) { a.act = Activity.SCHWIMMEN; a.timer = 20 + 90 * rng.nextDouble(); }
            }
            case ANLANDEN, UMLEGEN -> {
                if (alarm || panicNear(a)) { flee(a, dist); break; }
                a.timer -= dt;
                if (a.timer <= 0) {   // Platz noch gut?
                    a.timer = 8;
                    if (depth(a.tx, a.tz) > 0.03 || occupied(a, a.tx, a.tz)) {
                        double[] s = findSpot(a);
                        if (s == null) { toWater(a, false); break; }
                        a.tx = s[0];
                        a.tz = s[1];
                    }
                }
                if (travel(a, dt, false)) {
                    if (a.act == Activity.ANLANDEN) haulOuts++;
                    lieDown(a);
                    // ein Nachbar zu nah: Knurren
                    for (Animal o : animals) if (o != a && o.act == Activity.LIEGEN && Math.hypot(o.x - a.x, o.z - a.z) < a.length * seal.spacing) {
                        calls++;
                        if (seal.onCall != null) seal.onCall.call(a.x, a.z, 0.6);
                        break;
                    }
                }
            }
            case FLUCHT -> {
                if (depth > 0.45) {
                    a.act = Activity.SCHWIMMEN;
                    a.calm = seal.calmMinutes * 60 * (0.8 + 0.4 * rng.nextDouble());
                    double[] w = awayPoint(a, dist);
                    if (w != null) { a.loiterX = w[0]; a.loiterZ = w[1]; }
                    a.timer = 120;
                    break;
                }
                if (travel(a, dt, true)) {   // am Ziel und immer noch flach: weiter ins Wasser
                    double[] w = waterPoint(a, true);
                    if (w != null) { a.tx = w[0]; a.tz = w[1]; } else { a.act = Activity.SCHWIMMEN; a.calm = seal.calmMinutes * 60; }
                }
            }
        }
    }

    /** Das Junge folgt der Mutter: liegt neben ihr, schwimmt und flieht mit ihr. */
    private void pupStep(Animal p, double dt) {
        Animal m = byId(p.mother);
        if (m == null) { adultStep(p, dt); return; }
        double[] f = followPoint(p, m);
        double dx = f[0] - p.x, dz = f[1] - p.z, dl = Math.hypot(dx, dz);
        double depth = depth(p.x, p.z);
        boolean mLand = m.act == Activity.LIEGEN || m.act == Activity.ANLANDEN || m.act == Activity.UMLEGEN || (m.act == Activity.FLUCHT && m.crawling);
        if (m.act == Activity.FLUCHT) {
            if (p.act != Activity.FLUCHT) { p.act = Activity.FLUCHT; p.pause = 0; }
        } else if (m.act == Activity.LIEGEN && dl < 0.6 && depth < (p.act == Activity.LIEGEN ? seal.leaveDepth : 0.05)) {
            if (p.act != Activity.LIEGEN) lieDown(p);
            p.heading += angleTo(p.heading, m.heading) * Math.min(1, dt * 0.5);
            p.restTimer -= dt;
            if (p.restTimer <= 0) { p.rest = rng.nextDouble() < 0.35 ? 1 : 0; p.restTimer = 10 + 60 * rng.nextDouble(); }
            return;
        } else if (mLand) p.act = Activity.ANLANDEN;
        else p.act = m.act == Activity.TAUCHEN && depth > 1 ? Activity.TAUCHEN : Activity.SCHWIMMEN;
        if (dl > 0.3) {
            boolean land = depth < 0.3;
            double sp = land ? (m.act == Activity.FLUCHT ? seal.fleeSpeed : seal.crawlSpeed * 1.2) : seal.swimSpeed * (dl > 6 ? 1.2 : 0.6);
            p.crawling = land;
            move(p, dx / dl, dz / dl, Math.min(dl, sp * dt), dt, land);
            p.pause = 0;
        } else {
            p.crawling = depth < 0.3;
            p.heading += angleTo(p.heading, m.heading) * Math.min(1, dt);
        }
    }

    /** Wo das Junge hin will: an Land neben der Mutter, im Wasser dicht hinter ihr. */
    private double[] followPoint(Animal p, Animal m) {
        double fx = Math.sin(m.heading), fz = -Math.cos(m.heading), rx = -fz, rz = fx;
        boolean land = m.act == Activity.LIEGEN;
        double side = land ? (m.length * 0.32 + p.length * 0.3) * p.side : 0.8 * p.side, back = land ? 0.15 * m.length : 1.2 + m.length * 0.5;
        return new double[]{m.x + rx * side - fx * back, m.z + rz * side - fz * back};
    }

    // ------------------------------------------------------------------ Bewegung

    /**
     * Zum Ziel (tx, tz): im Wasser schwimmen, an Land robben in Schüben mit Pausen (Kopf hoch). Gibt true, wenn das Ziel
     * erreicht ist.
     */
    private boolean travel(Animal a, double dt, boolean fleeing) {
        double depth = depth(a.x, a.z);
        // erst im Wasser bis vor den Platz, dann an Land (nicht quer über die Bank)
        if (a.hasTarget) {
            double wdx = a.wx - a.x, wdz = a.wz - a.z, wl = Math.hypot(wdx, wdz);
            if (wl < 2 || depth < 0.3) a.hasTarget = false;
            else {
                a.crawling = false;
                move(a, wdx / wl, wdz / wl, Math.min(wl, seal.swimSpeed * dt), dt, false);
                return false;
            }
        }
        double dx = a.tx - a.x, dz = a.tz - a.z, dl = Math.hypot(dx, dz);
        if (dl < 0.4) return true;
        boolean land = depth < 0.3;
        a.crawling = land;
        if (!land) {
            move(a, dx / dl, dz / dl, Math.min(dl, seal.swimSpeed * (fleeing ? 1.4 : 1) * dt), dt, false);
            a.pause = 0;
            return false;
        }
        if (a.pause > 0) {
            a.pause -= dt;
            // in der Pause erst drehen
            a.heading += angleTo(a.heading, Math.atan2(dx, -dz)) * Math.min(1, dt * 1.5);
            return false;
        }
        double sp = (fleeing ? seal.fleeSpeed : seal.crawlSpeed) * (0.85 + 0.3 * Math.sin(a.id * 1.7 + time * 0.05));
        double step = Math.min(dl, sp * dt);
        move(a, dx / dl, dz / dl, step, dt, true);
        a.burst -= step;
        if (a.burst <= 0) {
            a.burst = fleeing ? 6 + 10 * rng.nextDouble() : 1.5 + 4 * rng.nextDouble();
            a.pause = fleeing ? 0.2 + 0.6 * rng.nextDouble() : 1.5 + 5 * rng.nextDouble();
        }
        return false;
    }

    /** Ein Stück in Richtung (fx, fz): die Nase dreht mit höchstens 1,5 rad/s; an Land Spuren und Gangart. */
    private void move(Animal a, double fx, double fz, double step, double dt, boolean land) {
        double want = Math.atan2(fx, -fz);
        double turn = angleTo(a.heading, want);
        double maxTurn = (land ? 1.2 : 1.8) * dt;
        a.heading += Math.max(-maxTurn, Math.min(maxTurn, turn));
        // erst drehen, dann gehen (an Land nicht seitwärts)
        double f = land ? Math.max(0, Math.cos(turn)) : 0.4 + 0.6 * Math.max(0, Math.cos(turn));
        double s = step * f;
        double hx = Math.sin(a.heading), hz = -Math.cos(a.heading);
        a.x += hx * s;
        a.z += hz * s;
        a.gait += s / (0.42 * a.length);
        if (land && s > 0 && depth(a.x, a.z) < 0.03) {   // Spuren nur im trockenen Watt
            a.trackAcc += s;
            if (a.trackAcc > 0.35) {
                a.trackAcc = 0;
                tracks++;
                if (onTrack != null) onTrack.track(a.x, a.z, hx, hz);
            }
        }
    }

    private void target(Animal a, double[] s, Activity act) {
        a.act = act;
        a.tx = s[0];
        a.tz = s[1];
        a.hasTarget = false;
        if (act == Activity.ANLANDEN && depth(a.x, a.z) > 0.3) {
            // Landepunkt: das Wasser, das dem Platz am nächsten liegt
            double[] rs = {2, 4, 7, 11, 16, 22, 30, 40, 52, 66};
            outer:
            for (double r : rs)
                for (int k = 0; k < 16; k++) {
                    double ang = k * Math.PI / 8, px = s[0] + Math.cos(ang) * r, pz = s[1] + Math.sin(ang) * r;
                    if (depth(px, pz) > 0.35) { a.wx = px; a.wz = pz; a.hasTarget = true; break outer; }
                }
        }
        a.timer = 8;
        a.burst = 0.5;
        a.pause = 0;
    }

    private void lieDown(Animal a) {
        a.act = Activity.LIEGEN;
        a.crawling = false;
        a.pause = 0;
        a.timer = 200 + 300 * rng.nextDouble();
        a.restTimer = 20 + 60 * rng.nextDouble();
        a.rest = 1;
        // mit dem Kopf zum Wasser [G]
        double[] w = waterDir(a.x, a.z);
        if (w != null) a.heading = Math.atan2(w[0], -w[1]) + (rng.nextDouble() - 0.5) * 1.2;
    }

    private void nextRest(Animal a) {
        double r = rng.nextDouble();
        boolean sun = sunAltDeg > 15;
        if (a.rest == 2 && r < 0.5) { a.restTimer = 120 + 300 * rng.nextDouble(); return; }   // bleibt Banane
        if (sun && r < 0.18) { a.rest = 2; a.restTimer = 300 + 900 * rng.nextDouble(); }
        else if (r < 0.45) { a.rest = 1; a.restTimer = 4 + 20 * rng.nextDouble(); }
        else { a.rest = 0; a.restTimer = 40 + 260 * rng.nextDouble(); }
    }

    private void toWater(Animal a, boolean fast) {
        a.act = Activity.SCHWIMMEN;
        a.crawling = false;
        a.timer = 30 + 90 * rng.nextDouble();
        a.haulWait = 0;
        a.loiterX = a.x;
        a.loiterZ = a.z;
    }

    private void flee(Animal a, double[] dist) {
        double[] w = fleePoint(a, dist);
        if (w == null) { a.rest = 1; return; }
        if (a.act != Activity.FLUCHT) flights++;
        a.act = Activity.FLUCHT;
        a.tx = w[0];
        a.tz = w[1];
        a.pause = 0;
        a.burst = 4 + 8 * rng.nextDouble();
        a.crawling = depth(a.x, a.z) < 0.3;
        lastFleeDist = dist[0];
    }

    /** Flieht ein Nachbar in 30 m? Dann flieht man mit. */
    private boolean panicNear(Animal a) {
        for (Animal o : animals)
            if (o != a && o.act == Activity.FLUCHT && o.crawling && Math.abs(o.x - a.x) < 30 && Math.abs(o.z - a.z) < 30 && Math.hypot(o.x - a.x, o.z - a.z) < 30) return true;
        return false;
    }

    // ------------------------------------------------------------------ Haltung

    /** Die Zielhaltung nach Tätigkeit, Gangart und Liegeform; im Wasser der Kopf über der Fläche. */
    private void setGoal(Animal a, double dt) {
        float[] g = a.goal;
        java.util.Arrays.fill(g, 0);
        double ph = a.gait * 2 * Math.PI;
        a.lookPhase += dt * 0.6;
        double look = Math.sin(a.lookPhase) * 0.5 + Math.sin(a.lookPhase * 0.37 + a.id) * 0.3;
        Seal.Pose p = a.pose();
        switch (p) {
            case LIEGEN -> {
                g[SealBody.HEAD_LIFT] = -0.03f;
                g[SealBody.HEAD_YAW] = (float) (0.25 * a.side * (0.5 + 0.5 * Math.sin(a.id)));
                g[SealBody.TAIL_LIFT] = 0.12f;
                g[SealBody.ROLL] = (float) (0.18 * a.side * Math.sin(a.id * 2.3));
                g[SealBody.FORE_SWING] = 0.1f;
                g[SealBody.HIND_SPREAD] = 0.10f;
            }
            case KOPF_HEBEN -> {
                g[SealBody.HEAD_LIFT] = 0.55f;
                g[SealBody.HEAD_YAW] = (float) (0.7 * look);
                g[SealBody.TAIL_LIFT] = 0.18f;
                g[SealBody.ROLL] = a.act == Activity.LIEGEN ? (float) (0.12 * a.side * Math.sin(a.id * 2.3)) : 0;
                g[SealBody.FORE_SWING] = 0.05f;
                g[SealBody.HIND_SPREAD] = 0.15f;
            }
            case BANANE -> {
                g[SealBody.HEAD_LIFT] = 0.55f;
                g[SealBody.HEAD_YAW] = (float) (0.3 * look);
                g[SealBody.TAIL_LIFT] = 0.75f;
                g[SealBody.ROLL] = (float) (1.25 * a.side);
                g[SealBody.FORE_SWING] = -0.4f;
                g[SealBody.HIND_SPREAD] = 0.35f;
            }
            case ROBBEN -> {
                g[SealBody.ARCH] = (float) (0.22 * Math.sin(ph));
                g[SealBody.HEAD_LIFT] = (float) (0.38 + 0.12 * Math.sin(ph + 1.2));
                g[SealBody.TAIL_LIFT] = (float) (0.25 + 0.15 * Math.sin(ph + 2.5));
                g[SealBody.FORE_SWING] = (float) (0.6 * Math.sin(ph + 0.5));
                g[SealBody.HIND_SPREAD] = 0.15f;
            }
            case SCHWIMMEN -> {
                boolean moving = a.act != Activity.SCHWIMMEN || Math.hypot(a.loiterX - a.x, a.loiterZ - a.z) > 3;
                if (moving) {
                    g[SealBody.PITCH] = 0.12f;
                    g[SealBody.HEAD_LIFT] = 0.25f;
                    g[SealBody.TAIL_YAW] = (float) (0.35 * Math.sin(ph));
                } else {
                    g[SealBody.PITCH] = 0.55f;   // „Flaschenhaltung“: steht im Wasser, der Kopf darüber
                    g[SealBody.HEAD_LIFT] = 0.35f;
                    g[SealBody.HEAD_YAW] = (float) (0.6 * look);
                    g[SealBody.TAIL_YAW] = (float) (0.15 * Math.sin(time * 0.9 + a.id));
                }
                g[SealBody.FORE_SWING] = (float) (0.3 * Math.sin(time * 1.1 + a.id));
                g[SealBody.HIND_SPREAD] = 0.25f;
            }
            case TAUCHEN -> {
                g[SealBody.PITCH] = -0.25f;
                g[SealBody.TAIL_YAW] = (float) (0.4 * Math.sin(ph));
                g[SealBody.FORE_SWING] = -0.3f;
                g[SealBody.HIND_SPREAD] = 0.3f;
            }
        }
        if (a.act == Activity.LIEGEN && a.rest != 2 && a.pup) g[SealBody.HEAD_LIFT] += 0.1f;
    }

    /** Höhe jedes Tiers aus Grund oder Wasser und Haltung (nach dem Schritt). */
    public void place() {
        if (world == null) return;
        for (Animal a : animals) {
            double L = a.length;
            double fx = Math.sin(a.heading), fz = -Math.cos(a.heading);
            double w = world.water(a.x, a.z), g = world.ground(a.x, a.z);
            boolean inWater = !a.onLand() && !Double.isNaN(w) && w - g > 0.05;
            if (inWater) {
                if (a.act == Activity.TAUCHEN) a.y = Math.max(g + 0.25 * L, w - 1.1);
                else {
                    double[] head = SealBody.head(a.pose);
                    a.y = Math.max(g + 0.1, w + 0.03 * L - head[1] * L);
                }
            } else {
                // Neigung des Grunds längs der Nase in die Haltung, dann auf den tiefsten Punkt setzen
                double gn = world.ground(a.x + fx * 0.4 * L, a.z + fz * 0.4 * L), gt = world.ground(a.x - fx * 0.4 * L, a.z - fz * 0.4 * L);
                double slope = Math.atan2(gn - gt, 0.8 * L);
                a.pose[SealBody.PITCH] = (float) (a.pose[SealBody.PITCH] * 0.5 + slope * 0.5);
                boolean same = !Double.isNaN(a.lowCache);
                for (int j = 0; same && j < SealBody.N; j++) same = Math.abs(a.lowPose[j] - a.pose[j]) < 0.03;
                if (!same) { a.lowCache = SealBody.lowest(a.pose); System.arraycopy(a.pose, 0, a.lowPose, 0, SealBody.N); }
                a.y = Math.max(g, (gn + gt) * 0.5 * 0.5 + g * 0.5) - a.lowCache * L - 0.01 * L;
            }
        }
    }

    // ------------------------------------------------------------------ Bank und Wasser

    /** Wasser am Ort (m; 0 trocken). */
    public double depth(double x, double z) {
        double w = world.water(x, z);
        return Double.isNaN(w) ? 0 : Math.max(0, w - world.ground(x, z));
    }

    /** Ob die Bank trocken genug ist: Anteil trockener Punkte auf allen Bänken (alle 30 s). */
    private void updateBank(boolean now) {
        bankTimer = 30;
        int n = 0, dry = 0;
        for (Bank b : banks)
            for (int k = 0; k < 48; k++) {
                double ang = k * 2.39996, r = b.radius() * Math.sqrt((k + 0.5) / 48);
                double x = b.x() + Math.cos(ang) * r, z = b.z() + Math.sin(ang) * r;
                n++;
                if (depth(x, z) < 0.02) dry++;
            }
        double share = n == 0 ? 0 : dry / (double) n;
        flooding = share < bankShare - 1e-9 || (flooding && share <= bankShare);
        bankShare = share;
        boolean av = bankShare > 0.06;
        if (av && !bankAvailable) epoch++;
        bankAvailable = av;
    }

    /** Ab dieser Tiefe (m) zählt Wasser als Wasser, in das ein Tier fliehen kann (flacher: Pfützen und Saum). */
    public static final double WATER = 0.25;

    /** Abstand zum nächsten Wasser (mindestens {@link #WATER} tief), bis max (m); sonst unendlich. */
    public double distToWater(double x, double z, double max) {
        double[] rs = {1.5, 3, 5, 8, 12, 17, 23, 30, 40, 52, 66, 85, 110, 140, 180};
        for (double r : rs) {
            if (r > max) break;
            for (int k = 0; k < 12; k++) {
                double ang = k * Math.PI / 6, px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
                if (depth(px, pz) > WATER) { lastWaterLevel = world.water(px, pz); return r; }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** Spiegel des Wassers, das {@link #distToWater} zuletzt gefunden hat. */
    private double lastWaterLevel;

    /** Richtung (x, z) zum nächsten Wasser, oder null. */
    private double[] waterDir(double x, double z) {
        double[] rs = {3, 6, 10, 16, 24, 34, 48, 66};
        for (double r : rs) {
            double sx = 0, sz = 0;
            int n = 0;
            for (int k = 0; k < 16; k++) {
                double ang = k * Math.PI / 8, cx = Math.cos(ang), cz = Math.sin(ang);
                if (depth(x + cx * r, z + cz * r) > WATER) { sx += cx; sz += cz; n++; }
            }
            if (n > 0) {
                double l = Math.hypot(sx, sz);
                if (l < 1e-6) return new double[]{1, 0};
                return new double[]{sx / l, sz / l};
            }
        }
        return null;
    }

    /** Fluchtziel: das nächste Wasser, möglichst vom Störer weg, ein Stück hinein. */
    private double[] fleePoint(Animal a, double[] dist) {
        double ux = dist[1] - a.x, uz = dist[2] - a.z, ul = Math.hypot(ux, uz);
        if (ul > 1e-6) { ux /= ul; uz /= ul; }
        double[] rs = {3, 6, 10, 15, 22, 30, 40, 55, 75, 100, 130};
        double best = -1e9;
        double[] out = null;
        for (double r : rs) {
            for (int k = 0; k < 24; k++) {
                double ang = k * Math.PI / 12, cx = Math.cos(ang), cz = Math.sin(ang);
                double px = a.x + cx * r, pz = a.z + cz * r;
                if (depth(px, pz) < 0.5) continue;
                double s = -r / 40 - 1.2 * (cx * ux + cz * uz);
                if (s > best) { best = s; out = new double[]{px + cx * 4, pz + cz * 4}; }
            }
            if (out != null && r > 20) break;
        }
        return out;
    }

    /** Ein Punkt im Wasser vom Störer weg (60 bis 160 m), sonst einer um die Bank. */
    private double[] awayPoint(Animal a, double[] dist) {
        double ux = a.x - dist[1], uz = a.z - dist[2], ul = Math.hypot(ux, uz);
        if (ul < 1e-6) return waterPoint(a, false);
        ux /= ul;
        uz /= ul;
        for (int t = 0; t < 12; t++) {
            double r = 60 + 100 * rng.nextDouble(), ang = (rng.nextDouble() - 0.5) * 1.6, c = Math.cos(ang), s = Math.sin(ang);
            double dx = ux * c - uz * s, dz = ux * s + uz * c;
            double px = a.x + dx * r, pz = a.z + dz * r;
            if (depth(px, pz) > 0.8) return new double[]{px, pz};
        }
        return waterPoint(a, false);
    }

    /**
     * Ein Punkt im Wasser (mindestens 0,8 m tief), in dem das Tier treibt: 10 bis 120 m vor der Bank, mit
     * {@code near} so nah wie möglich am Tier.
     */
    private double[] waterPoint(Animal a, boolean near) {
        Bank b = nearestBank(a.x, a.z);
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (int t = 0; t < (near ? 40 : 16); t++) {
            double px, pz;
            if (near) {
                double ang = rng.nextDouble() * 2 * Math.PI, r = 4 + 60 * rng.nextDouble();
                px = a.x + Math.cos(ang) * r;
                pz = a.z + Math.sin(ang) * r;
            } else {
                double ang = rng.nextDouble() * 2 * Math.PI, r = b == null ? 60 : b.radius() * 0.6 + 10 + 140 * rng.nextDouble();
                px = (b == null ? a.x : b.x()) + Math.cos(ang) * r;
                pz = (b == null ? a.z : b.z()) + Math.sin(ang) * r;
            }
            if (depth(px, pz) < 0.8) continue;
            double d = Math.hypot(px - a.x, pz - a.z);
            if (!near) return new double[]{px, pz};
            if (d < bd) { bd = d; best = new double[]{px, pz}; }
        }
        return best;
    }

    /**
     * Ein freier Platz auf der Bank: trocken, nahe am Wasser (nach der Vorliebe des Tiers), mit Abstand zu allen, die
     * liegen oder dorthin unterwegs sind, und zur Gruppe hin. Null, wenn keiner frei ist.
     */
    double[] findSpot(Animal a) {
        Bank b = nearestBank(a.x, a.z);
        if (b == null) return null;
        double cx = 0, cz = 0;
        int n = 0;
        for (Animal o : animals) if (o != a && !o.pup && o.act == Activity.LIEGEN) { cx += o.x; cz += o.z; n++; }
        if (n > 0) { cx /= n; cz /= n; }
        double best = -1e9;
        double[] out = null;
        int tries = seal.quality == Seal.Quality.LOW ? 24 : 40;
        for (int t = 0; t < tries; t++) {
            double ang = rng.nextDouble() * 2 * Math.PI, r = b.radius() * Math.sqrt(rng.nextDouble());
            double x = b.x() + Math.cos(ang) * r, z = b.z() + Math.sin(ang) * r;
            double w = world.water(x, z), g = world.ground(x, z);
            if (!Double.isNaN(w) && w > g - 0.06) continue;   // nass oder knapp darüber
            if (occupied(a, x, z)) continue;
            double dw = distToWater(x, z, 45);
            if (Double.isInfinite(dw)) continue;
            // mit der Flut nur Plätze, die noch eine Weile trocken bleiben (sonst ein ständiges Hin und Her am Saum)
            if (flooding && g - lastWaterLevel < 0.3) continue;
            double score = -Math.abs(dw - a.prefWater) / 5 - Math.hypot(x - a.x, z - a.z) / 70 + 0.4 * rng.nextDouble();
            if (n > 0) score -= Math.hypot(x - cx, z - cz) / 35;   // zur Gruppe hin
            if (score > best) { best = score; out = new double[]{x, z}; }
        }
        return out;
    }

    /** Liegt oder robbt schon jemand dicht an (x, z)? */
    private boolean occupied(Animal a, double x, double z) {
        for (Animal o : animals) {
            if (o == a || o.pup || (a.pup && o.id == a.mother) || (o.pup && o.mother == a.id)) continue;
            double ox, oz;
            if (o.act == Activity.LIEGEN) { ox = o.x; oz = o.z; }
            else if (o.act == Activity.ANLANDEN || o.act == Activity.UMLEGEN) { ox = o.tx; oz = o.tz; }
            else continue;
            if (Math.hypot(ox - x, oz - z) < seal.spacing * Math.max(a.length, o.length) + (o.pup ? 0 : 0.2)) return true;
        }
        return false;
    }

    private Bank nearestBank(double x, double z) {
        Bank best = null;
        double bd = Double.MAX_VALUE;
        for (Bank b : banks) { double d = Math.hypot(b.x() - x, b.z() - z) - b.radius(); if (d < bd) { bd = d; best = b; } }
        return best;
    }

    /** {Abstand, x, z, Stärke} des nächsten Störers (Abstand unendlich ohne Störer). */
    public double[] nearestDisturber(double x, double z) {
        double bd = Double.POSITIVE_INFINITY, bx = 0, bz = 0, bs = 1;
        for (Disturber d : disturbers) {
            double dd = Math.hypot(d.x() - x, d.z() - z) / Math.max(0.2, d.strength());
            if (dd < bd) { bd = Math.hypot(d.x() - x, d.z() - z); bx = d.x(); bz = d.z(); bs = d.strength(); }
        }
        return new double[]{bd, bx, bz, bs};
    }

    private Animal byId(int id) {
        if (id >= 0 && id < animals.size() && animals.get(id).id == id) return animals.get(id);
        for (Animal a : animals) if (a.id == id) return a;
        return null;
    }

    static double angleTo(double from, double to) {
        double d = (to - from) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    // ------------------------------------------------------------------ Ausgabe

    /** Zahl je Tätigkeit (Reihenfolge von {@link Activity}). */
    public int[] census() {
        int[] c = new int[Activity.values().length];
        for (Animal a : animals) c[a.act.ordinal()]++;
        return c;
    }

    /** Zahl der Tiere, die gerade an Land liegen (Erwachsene und Junge). */
    public int lying() { return census()[Activity.LIEGEN.ordinal()]; }

    /** Zahl der Jungen. */
    public int pups() { int n = 0; for (Animal a : animals) if (a.pup) n++; return n; }

    /**
     * Daten für die Grafik: je Tier 16 Werte (x, y, z, Länge · Richtung, Rolle, Nässe, Fell · Kopf heben, Kopf drehen,
     * Schwanz heben, Buckel · Schwanzschlag, Vorderflosse, Hinterflossen, Neigung). Gibt die Zahl der Tiere.
     */
    public int instances(float[] out) {
        int n = 0;
        for (Animal a : animals) {
            if ((n + 1) * 16 > out.length) break;
            int o = n * 16;
            float[] p = a.pose;
            out[o] = (float) a.x; out[o + 1] = (float) a.y; out[o + 2] = (float) a.z; out[o + 3] = (float) a.length;
            out[o + 4] = (float) a.heading; out[o + 5] = p[SealBody.ROLL]; out[o + 6] = (float) a.wet; out[o + 7] = a.tone + (a.pup ? 2 : 0);
            out[o + 8] = p[SealBody.HEAD_LIFT]; out[o + 9] = p[SealBody.HEAD_YAW]; out[o + 10] = p[SealBody.TAIL_LIFT]; out[o + 11] = p[SealBody.ARCH];
            out[o + 12] = p[SealBody.TAIL_YAW]; out[o + 13] = p[SealBody.FORE_SWING]; out[o + 14] = p[SealBody.HIND_SPREAD]; out[o + 15] = p[SealBody.PITCH];
            n++;
        }
        return n;
    }

    /** Schatten auf dem Grund: je Tier an Land x, Grund, z, Länge, Richtung (5 Werte). Gibt die Zahl. */
    public int shadows(float[] out) {
        int n = 0;
        for (Animal a : animals) {
            if (!a.onLand() || (n + 1) * 5 > out.length) continue;
            int o = n * 5;
            out[o] = (float) a.x; out[o + 1] = (float) world.ground(a.x, a.z); out[o + 2] = (float) a.z; out[o + 3] = (float) a.length; out[o + 4] = (float) a.heading;
            n++;
        }
        return n;
    }

    /** „Seehunde 36 (8 Heuler): 24 liegen, 10 im Wasser, 2 robben · Bank 64 % trocken · Störer 820 m“ */
    public String describe() {
        int[] c = census();
        int water = c[Activity.SCHWIMMEN.ordinal()] + c[Activity.TAUCHEN.ordinal()], move = c[Activity.ANLANDEN.ordinal()] + c[Activity.UMLEGEN.ordinal()];
        double d = Double.POSITIVE_INFINITY;
        for (Bank b : banks) d = Math.min(d, nearestDisturber(b.x(), b.z())[0]);
        int pu = pups();
        return String.format(Locale.GERMAN, "Seehunde %d%s: %d liegen, %d im Wasser (%d tauchen)%s%s · Bank %.0f %% trocken%s",
                animals.size() - pu, pu > 0 ? " (" + pu + " Heuler)" : "", c[Activity.LIEGEN.ordinal()], water, c[Activity.TAUCHEN.ordinal()],
                move > 0 ? ", " + move + " robben an Land" : "", c[Activity.FLUCHT.ordinal()] > 0 ? ", " + c[Activity.FLUCHT.ordinal()] + " flüchten" : "",
                bankShare * 100, Double.isInfinite(d) ? "" : String.format(Locale.GERMAN, " · Störer %.0f m", d));
    }

    /** Abstand des Störers, als zuletzt jemand floh (für die Prüfung), NaN ohne Flucht. */
    public double lastFleeDistance() { return lastFleeDist; }
}
