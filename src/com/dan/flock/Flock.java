package com.dan.flock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Silbermöwen um einen Turm auf See.
 * <p>
 * <b>Flug:</b> Jede Möwe fliegt mit 8 bis 15 m/s gegen die Luft. Ohne Flügelschlag sinkt sie im Gleitflug um 0,6 m/s,
 * Aufwind trägt sie, Flügelschlag (3,1 Hz) bringt bis 2 m/s Steigen. Sie steuert nach Zielen und nach drei Schwarmregeln
 * (Abstand, Richtung, Zusammenhalt), weicht Hindernissen aus und bleibt über den Wellen.
 * <p>
 * <b>Aufwind am Turm:</b> Wind, der auf einen senkrechten Zylinder trifft, steigt an dessen Luvseite auf
 * ({@link #updraft}): am stärksten dicht vor der Wand, nach außen und über der Oberkante abklingend, im Lee nichts.
 * Ab gut 5 m/s Wind stellen sich Möwen dort in den Wind und stehen fast still in der Luft.
 * <p>
 * <b>Landen:</b> Sitzplätze ({@link Perch}, auch bewegte wie Tonnen) werden gegen den Wind angeflogen; die Möwe bremst,
 * fängt ab, sitzt eine Weile mit dem Kopf im Wind und fliegt wieder ab.
 * <p>
 * <b>Wetter und Tag:</b> Nachts (unter {@link #nightLux}) fliegt keine: Wer keinen Platz hat, fliegt zur Küste. Ab
 * Bft 8 suchen sie das Lee hinter dem Turm und fliegen tief, ab Bft 10 fliegen alle weg. Rufe kommen als Ereignis
 * ({@link #onCall}).
 * <p>
 * Nur Java 21; Koordinaten wie überall: x Ost, y oben, z Süd.
 */
public final class Flock {

    /** Stufen: Zahl der Möwen. */
    public enum Quality {
        LOW(6), MEDIUM(10), HIGH(16), ULTRA(28);
        public final int birds;
        Quality(int b) { birds = b; }
    }

    /** Was ein Vogel gerade tut; SWIM, DIVE und WALK seit Haithabu Phase 13 (schwimmt, taucht, geht am Boden). */
    public enum State { FLY, SOAR, LAND, PERCH, TAKEOFF, LEAVE, AWAY, SWIM, DIVE, WALK }

    /** Ein Ruf: Ort, Stärke 0..1, die Möwe. */
    @FunctionalInterface public interface Call { void call(double x, double y, double z, double strength, Gull g); }

    /** Die Oberfläche darunter (Wellen), eigene kleine Schnittstelle. */
    @FunctionalInterface public interface Ground { double height(double x, double z); }

    /** Ein Ort zum Sitzen; kann sich bewegen (Tonne) oder zeitweise fehlen (Platte bei Flut). */
    public interface Spot {
        double[] position();
        default boolean usable() { return true; }
    }

    /** Ein Sitzplatz mit Namen; seit Haithabu Phase 13 mit einer Maske der Arten, die ihn nehmen ({@link Species#bit}). */
    public static final class Perch {
        public final String name;
        public final Spot spot;
        public volatile int mask = -1;
        /** Seit Haithabu Phase 14: wie gern Vögel hier sitzen (1 üblich; ein Pfahl mitten im Hafen 3 bis 4). */
        public volatile double appeal = 1;
        volatile Gull by;
        public Perch(String name, Spot spot) { this.name = name; this.spot = spot; }
        public Perch(String name, double x, double y, double z) { this(name, () -> new double[]{x, y, z}); }
        public boolean taken() { return by != null; }
        /** Nur für diese Arten. */
        public Perch only(Species... sp) { int m = 0; for (Species x : sp) m |= x.bit(); mask = m; return this; }
        /** Beliebtheit setzen (siehe {@link #appeal}). */
        public Perch appeal(double a) { appeal = Math.max(0.05, a); return this; }
    }

    /** Seit Haithabu Phase 13: Wasser am Ort (Höhe der Oberfläche) oder NaN, wo kein offenes Wasser ist (Land, Eis, zu flach). */
    @FunctionalInterface public interface Water { double surface(double x, double z); }

    /** Ein Kreis (Mitte, Halbmesser): Futterplatz am Boden für Krähen, Wasserfläche für Enten und Schwäne. */
    public record Area(double x, double z, double r) { }

    /** Ein senkrechter Zylinder (Turm, Pfahl): Mitte, Halbmesser, Unter- und Oberkante. */
    public record Obstacle(double x, double z, double r, double y0, double y1) { }

    /** Ein Vogel (der Name kommt von den Möwen; seit Haithabu Phase 13 jede {@link Species}). */
    public static final class Gull {
        public final int id;
        public final Species species;
        /** Gründeln 0..1 (Kopf unter Wasser, Schwanz hoch), Flügel zum Trocknen gespreizt 0..1 (Kormoran), Schritt beim Gehen. */
        public double dabble, spread, walkPhase;
        double homeX, homeZ;
        int area = -1;
        /** Seit Glacier Phase 10: Dominikanermöwe (schwarzer Mantel) statt Silbermöwe; nur das Netz unterscheidet sich. */
        public boolean kelp;
        /** Winkel auf dem Kreis in der Thermik (Kondor). */
        double orbit;
        public final double[] pos = new double[3], vel = new double[3];
        public volatile State state = State.FLY;
        /** Flügel: Phase des Schlags (rad), wie stark sie schlägt 0..1, Querneigung (rad), Faltung beim Sitzen 0..1. */
        public double wingPhase, flap, bank, fold;
        /** Geschwindigkeit gegen die Luft im letzten Schritt (m/s). */
        public double airspeed;
        /** Blickrichtung (Grad von Nord) beim Sitzen. */
        public double facing;
        double timer, nextCall, wanderX, wanderY, wanderZ, slotSide, slotDist, slotUp, homeR = 150, fleeX, fleeZ;
        /** Anflug: 0 = zum Punkt im Lee des Platzes, 1 = Endanflug gegen den Wind. */
        int approach;
        Perch perch;
        final Random rnd;
        Gull(int id, long seed) { this(id, seed, Species.MOEWE); }
        Gull(int id, long seed, Species sp) { this.id = id; this.species = sp; rnd = new Random(seed * 7919 + id * 104729L + sp.ordinal() * 15485863L); }
        public Perch perch() { return perch; }
        /** Geschwindigkeit über Grund (m/s). */
        public double groundSpeed() { return Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]); }
    }

    // ------------------------------------------------------------------ Eingänge
    public volatile long seed = 1;
    public volatile double windSpeed = 6, windFromDeg = 240;
    /** Helligkeit in lx; darunter ist Nacht. */
    public volatile double ambientLux = 20_000, nightLux = 30;
    /** Mitte des Reviers und sein Halbmesser (m). */
    public volatile double homeX = 0, homeZ = 0, homeRadius = 160;
    /** Richtung zur Küste (Grad), wohin sie abends und im Sturm fliegen. */
    public volatile double coastDeg = 150;
    public volatile Ground ground = (x, z) -> 0;
    public final List<Obstacle> obstacles = new CopyOnWriteArrayList<>();
    public final List<Perch> perches = new CopyOnWriteArrayList<>();
    public volatile Quality quality = Quality.HIGH;
    public volatile Call onCall;
    /**
     * Seit Haithabu Phase 13: wie viele Vögel der übrigen Arten (Reihenfolge wie {@link Species}, der Wert für MOEWE
     * zählt nicht, die Möwen kommen aus {@link #quality}). Alle 0: nur Möwen wie bisher.
     */
    public volatile int[] mix = new int[Species.values().length];
    /** Offenes Wasser (null: überall Wasser auf Höhe von {@link #ground}). */
    public volatile Water water;
    /** Futterplätze der Krähen am Boden und Wasserflächen der Enten, Schwäne und Kormorane. */
    public final List<Area> feeding = new CopyOnWriteArrayList<>(), waters = new CopyOnWriteArrayList<>();
    /** Wer stört (Auge, Hund, Boote): Orte x, y, z; Vögel näher als ihre Fluchtdistanz weichen aus oder fliegen auf. */
    public final List<double[]> disturbers = new CopyOnWriteArrayList<>();
    /** Tag im Jahr (Schwalben nur im Sommer). */
    public volatile int dayOfYear = 200;
    /**
     * Seit Glacier Phase 10: die Möwen sind Dominikanermöwen (Larus dominicanus, Patagonien: schwarzer Mantel); Verhalten
     * wie die Silbermöwe.
     */
    public volatile boolean kelpGulls;
    /** Seit Glacier Phase 10: Thermik für die Kondore (Kreise über sonnigen Hängen); leer: Kreise im Revier. */
    public final List<Area> thermals = new CopyOnWriteArrayList<>();
    /** Höhenband der Kondore über dem Boden darunter (m) und ihr Revier um (condorX, condorZ). */
    public volatile double condorFloor = 80, condorCeiling = 650, condorX = Double.NaN, condorZ = Double.NaN, condorRadius = 2500;

    // ------------------------------------------------------------------ Zustand
    private final List<Gull> gulls = new CopyOnWriteArrayList<>();
    private double time;
    private long calls;
    private boolean started;

    /** Grenzen des Flugs gegen die Luft (m/s), Sinken im Gleitflug, Steigen mit Flügelschlag, Schlagfrequenz (Hz). */
    public static final double V_MIN = 8, V_CRUISE = 10.5, V_MAX = 17, SINK = 0.6, CLIMB = 2.0, FLAP_HZ = 3.1;
    /** Ab diesem Wind (m/s) stehen sie im Aufwind; ab Bft 8 suchen sie Lee, ab Bft 10 fliegen sie weg. */
    public static final int MAX_SOAR = 5;
    public static final double SOAR_WIND = 5.5, LEE_WIND = 17.2, AWAY_WIND = 24.5;

    // ------------------------------------------------------------------ Presets
    public static Flock calm() { return beaufort(1); }
    public static Flock breeze() { return beaufort(4); }
    public static Flock gale() { return beaufort(8); }
    public static Flock storm() { return beaufort(10); }

    public static Flock beaufort(int bft) {
        Flock f = new Flock();
        f.windSpeed = BFT_MID[Math.max(0, Math.min(12, bft))];
        return f;
    }

    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    public List<Gull> gulls() { return gulls; }
    public double time() { return time; }
    public long calls() { return calls; }
    public boolean night() { return ambientLux < nightLux; }

    // ------------------------------------------------------------------ Wind

    /** Wind am Ort in der Höhe y über dem Wasser (Vektor, wohin er weht), mit dem logarithmischen Profil über See. */
    public double[] windAt(double y) {
        double a = Math.toRadians(windFromDeg + 180), h = Math.max(0.5, y);
        double f = Math.log(h / 2e-4) / Math.log(10 / 2e-4);
        return new double[]{Math.sin(a) * windSpeed * f, 0, -Math.cos(a) * windSpeed * f};
    }

    /**
     * Wind am Ort mit dem Windschatten der Hindernisse: hinter einem Zylinder bis gut zehn Halbmesser weit
     * schwächer (in der Mitte des Nachlaufs auf ein Fünftel), so hoch wie das Hindernis. Dort halten Möwen im Sturm aus.
     */
    public double[] windAt(double x, double y, double z) {
        double[] w = windAt(y - 0);
        double a = Math.toRadians(windFromDeg + 180), dx0 = Math.sin(a), dz0 = -Math.cos(a);
        double k = 1;
        for (Obstacle o : obstacles) {
            if (y > o.y1 + o.r || y < o.y0) continue;
            double rx = x - o.x, rz = z - o.z, along = rx * dx0 + rz * dz0, lat = Math.abs(rx * dz0 - rz * dx0);
            if (along <= 0) continue;
            double width = o.r * (1.2 + along / (o.r * 8));
            double s = Math.exp(-Math.pow(lat / width, 2)) * Math.exp(-along / (o.r * 12)) * (y > o.y1 ? Math.exp(-(y - o.y1) / o.r) : 1);
            k = Math.min(k, 1 - 0.8 * s);
        }
        return new double[]{w[0] * k, 0, w[2] * k};
    }

    /**
     * Aufwind (m/s) am Ort aus allen Hindernissen: vor der Luvseite eines Zylinders
     * w = 0,9 · U · cos^1,5(Winkel zur Luv) · exp(−Abstand / 1,5 R), bis zur Oberkante voll, darüber mit 0,8 R
     * abklingend; im Lee und seitlich nichts. Eine Näherung, kein Strömungsfeld.
     */
    public double updraft(double x, double y, double z) {
        double w = 0;
        double a = Math.toRadians(windFromDeg), ux = Math.sin(a), uz = -Math.cos(a);   // zur Luv
        for (Obstacle o : obstacles) {
            double dx = x - o.x, dz = z - o.z, d = Math.hypot(dx, dz);
            if (d < o.r * 0.5) continue;
            double cosA = (dx * ux + dz * uz) / d;
            if (cosA <= 0) continue;
            double gap = Math.max(0, d - o.r);
            double hf = y < o.y0 ? 0 : y < o.y1 ? Math.min(1, (y - o.y0) / (0.25 * (o.y1 - o.y0) + 1e-6)) : Math.exp(-(y - o.y1) / (0.8 * o.r));
            w += 0.9 * windSpeed * Math.pow(cosA, 1.5) * Math.exp(-gap / (1.5 * o.r)) * hf;
        }
        return w;
    }

    // ------------------------------------------------------------------ Zeit

    /** Möwen ins Revier setzen (verteilt in der Luft); geschieht beim ersten Schritt von selbst. */
    public void populate() {
        gulls.clear();
        for (Perch p : perches) p.by = null;
        Random r = new Random(seed);
        int n = quality.birds;
        for (int i = 0; i < n; i++) {
            Gull g = new Gull(i, seed);
            g.kelp = kelpGulls;
            double a = r.nextDouble() * 2 * Math.PI, rr = homeRadius * (0.3 + 0.6 * r.nextDouble());
            g.pos[0] = homeX + Math.sin(a) * rr;
            g.pos[2] = homeZ - Math.cos(a) * rr;
            g.pos[1] = 12 + r.nextDouble() * 30;
            double h = r.nextDouble() * 2 * Math.PI;
            g.vel[0] = Math.sin(h) * V_CRUISE;
            g.vel[2] = -Math.cos(h) * V_CRUISE;
            g.wingPhase = r.nextDouble() * 6.28;
            g.nextCall = 5 + r.nextDouble() * 40;
            pickWander(g);
            gulls.add(g);
        }
        int[] m = mix;
        for (Species sp : Species.values()) if (sp != Species.MOEWE) for (int i = 0; i < count(m, sp); i++) gulls.add(spawn(sp, gulls.size()));
        started = true;
    }

    static int count(int[] m, Species sp) { return m == null || sp.ordinal() >= m.length ? 0 : Math.max(0, m[sp.ordinal()]); }

    /** Um dt Sekunden weiter (Szenenzeit; große Schritte werden in Stücke von 1/30 s geteilt, höchstens 3 s je Aufruf). */
    public void step(double dt) {
        if (!started) populate();
        if (dt <= 0) return;
        adjustCount();
        double left = Math.min(dt, 3);
        while (left > 1e-9) {
            double h = Math.min(1 / 30.0, left);
            left -= h;
            time += h;
            for (Gull g : gulls) stepGull(g, h);
        }
    }

    private void adjustCount() {
        int[] m = mix;
        for (Species sp : Species.values()) {
            int want = sp == Species.MOEWE ? quality.birds : count(m, sp), have = 0;
            for (Gull g : gulls) if (g.species == sp) have++;
            if (have == want) continue;
            for (int i = gulls.size() - 1; i >= 0 && have > want; i--) {
                Gull g = gulls.get(i);
                if (g.species != sp) continue;
                gulls.remove(i);
                if (g.perch != null) g.perch.by = null;
                have--;
            }
            int next = 0;
            for (Gull g : gulls) next = Math.max(next, g.id + 1);
            while (have < want) {
                Gull g = sp == Species.MOEWE ? new Gull(next, seed) : spawn(sp, next);
                if (sp == Species.MOEWE) { g.state = State.AWAY; g.timer = 5 + g.rnd.nextDouble() * 60; g.kelp = kelpGulls; }
                gulls.add(g);
                next++;
                have++;
            }
        }
    }

    private void stepGull(Gull g, double h) {
        if (g.species != Species.MOEWE) { stepOther(g, h); return; }
        boolean night = night(), lee = windSpeed >= LEE_WIND, away = windSpeed >= AWAY_WIND;
        g.timer -= h;
        // seit Haithabu Phase 13: Möwen schwimmen auch, und ein Störer scheucht sie vom Platz
        if (g.state == State.SWIM) { swim(g, h, 0.4); if (g.timer <= 0 || disturbed(g)) takeoffWater(g); return; }
        if (g.state == State.LAND && g.perch == null) { stepOther(g, h); return; }
        if (g.state == State.PERCH && disturbed(g)) { takeoff(g); return; }
        switch (g.state) {
            case AWAY -> {
                // draußen bei der Küste; kommt am Tag bei mäßigem Wind zurück
                if (!night && !away && !lee && g.timer <= 0) {
                    double a = Math.toRadians(coastDeg);
                    g.pos[0] = homeX + Math.sin(a) * homeRadius * 4;
                    g.pos[2] = homeZ - Math.cos(a) * homeRadius * 4;
                    g.pos[1] = 25;
                    g.vel[0] = -Math.sin(a) * V_CRUISE;
                    g.vel[1] = 0;
                    g.vel[2] = Math.cos(a) * V_CRUISE;
                    g.state = State.FLY;
                    pickWander(g);
                } else return;
            }
            case PERCH -> {
                double[] p = g.perch.spot.position();
                g.pos[0] = p[0]; g.pos[1] = p[1]; g.pos[2] = p[2];
                g.vel[0] = g.vel[1] = g.vel[2] = 0;
                g.flap = 0;
                g.fold = Math.min(1, g.fold + h * 1.5);
                g.facing = turnToward(g.facing, windFromDeg, 40 * h);
                g.bank = 0;
                if (!g.perch.spot.usable() || away || (g.timer <= 0 && !night && !lee) || lee && !inLee(g.perch.spot.position())) takeoff(g);
                if (!night) maybeCall(g, h, 1 / 45.0);
                return;
            }
            default -> { }
        }
        // ------------------------------------------------ Ziele je Zustand
        double[] wind = windAt(g.pos[0], g.pos[1] - groundAt(g), g.pos[2]);
        double[] want = new double[3];      // gewünschte Geschwindigkeit über Grund
        double wantY = g.pos[1];
        if (g.state == State.FLY) {
            if ((night || lee) && !away) {
                // abends auf einen Platz am Turm, im Sturm auf einen im Lee; sonst zur Küste
                Perch p = freePerch(g, lee);
                if (p != null) { g.state = State.LAND; g.perch = p; p.by = g; g.timer = 90; g.approach = 0; }
                else g.state = State.LEAVE;
            } else if (away) { g.state = State.LEAVE; }
            else {
                double dx = g.wanderX - g.pos[0], dz = g.wanderZ - g.pos[2];
                if (Math.hypot(dx, dz) < 25 || g.timer <= 0) pickWander(g);
                want = toward(g, g.wanderX, g.wanderZ, V_CRUISE, wind);
                wantY = g.wanderY;
                if (g.timer <= 0) g.timer = 15 + g.rnd.nextDouble() * 30;
                // Entscheidungen
                if (g.rnd.nextDouble() < h / 25 && !lee) {
                    Perch p = freePerch(g, false);
                    if (p != null) { g.state = State.LAND; g.perch = p; p.by = g; g.timer = 60; g.approach = 0; }
                } else if (water != null && !waters.isEmpty() && g.rnd.nextDouble() < h / 35 && !lee) {
                    landOnWater(g);
                } else if (windSpeed >= SOAR_WIND && !lee && !obstacles.isEmpty() && g.rnd.nextDouble() < h / 40 && census()[State.SOAR.ordinal()] < MAX_SOAR) {
                    g.state = State.SOAR;
                    g.timer = 25 + g.rnd.nextDouble() * 50;
                    g.slotSide = (g.rnd.nextDouble() - 0.5) * 1.4;
                    g.slotDist = 0.9 + g.rnd.nextDouble() * 1.2;
                    g.slotUp = (g.rnd.nextDouble() - 0.4) * 10;
                }
            }
        }
        if (g.state == State.SOAR) {
            Obstacle o = obstacles.get(0);
            g.facing = turnToward(g.facing, windFromDeg, 90 * h);
            if (g.timer <= 0 || windSpeed < SOAR_WIND * 0.9 || lee || night) { g.state = State.FLY; pickWander(g); }
            else {
                double a = Math.toRadians(windFromDeg), ux = Math.sin(a), uz = -Math.cos(a);
                double side = g.slotSide, dist = o.r * (1 + g.slotDist);
                double sx = o.x + (ux * Math.cos(side) - uz * Math.sin(side)) * dist, sz = o.z + (uz * Math.cos(side) + ux * Math.sin(side)) * dist;
                double dx = sx - g.pos[0], dz = sz - g.pos[2];
                // Stehen: gegen Grund fast still, Nase im Wind
                want = new double[]{dx * 0.5, 0, dz * 0.5};
                double l = Math.hypot(want[0], want[2]);
                if (l > 6) { want[0] *= 6 / l; want[2] *= 6 / l; }
                wantY = o.y1 + g.slotUp + Math.sin(time * 0.7 + g.id) * 1.5;
            }
        }
        if (g.state == State.LAND && g.perch == null) { stepOther(g, h); return; }
        if (g.state == State.LAND) {
            double[] p = g.perch.spot.position();
            double dd0 = dist(g, p);
            if (!g.perch.spot.usable() || lee && !inLee(p) || g.timer <= 0) { release(g); g.state = State.FLY; pickWander(g); }
            else if (dd0 < 3) {
                // Abfangen: schlagend auf den Platz, langsam und gerade
                double sp = Math.min(1.8, dd0 * 2 + 0.3), step = Math.min(dd0, sp * h);
                for (int k = 0; k < 3; k++) { g.vel[k] = (p[k] - g.pos[k]) / Math.max(1e-6, dd0) * sp; g.pos[k] += (p[k] - g.pos[k]) / Math.max(1e-6, dd0) * step; }
                g.flap += (1 - g.flap) * Math.min(1, h * 4);
                g.wingPhase = (g.wingPhase + 2 * Math.PI * g.species.flapHz * h) % (2 * Math.PI);
                g.bank *= 0.9;
                if (dd0 - step < 0.05) {
                    g.state = State.PERCH;
                    g.timer = 40 + g.rnd.nextDouble() * 160;
                    g.facing = Math.toDegrees(Math.atan2(g.vel[0], -g.vel[2]));
                    g.pos[0] = p[0]; g.pos[1] = p[1]; g.pos[2] = p[2];
                    g.vel[0] = g.vel[1] = g.vel[2] = 0;
                    callNow(g);
                }
                return;
            }
            else {
                // gegen den Wind anfliegen: Ziel zuerst 12 m im Lee des Platzes, dann der Platz selbst
                double a = Math.toRadians(windFromDeg + 180), lx = Math.sin(a), lz = -Math.cos(a);
                double dx = p[0] - g.pos[0], dz = p[2] - g.pos[2], d = Math.hypot(dx, dz);
                double tx = p[0], tz = p[2];
                // erst zu einem Punkt 14 m im Lee des Platzes, von dort im Endanflug gegen den Wind (mit Gedächtnis, kein Pendeln)
                double qx = p[0] + lx * 14, qz = p[2] + lz * 14;
                if (g.approach == 0 && Math.hypot(qx - g.pos[0], qz - g.pos[2]) < 5) g.approach = 1;
                if (g.approach == 1 && d > 40) g.approach = 0;
                if (g.approach == 0) { tx = qx; tz = qz; }
                if (d > homeRadius * 6) { release(g); g.state = State.LEAVE; return; }
                double sp = Math.max(1.0, Math.min(V_CRUISE, d * 0.55));
                want = toward(g, tx, tz, sp, null);
                wantY = p[1] + Math.min(6, d * 0.15);
            }
        }
        if (g.state == State.TAKEOFF) {
            double a = Math.toRadians(windFromDeg + 180);
            want = new double[]{Math.sin(a) * 4, 0, -Math.cos(a) * 4};
            wantY = g.pos[1] + 6;
            g.fold = Math.max(0, g.fold - h * 4);
            if (g.timer <= 0) { g.state = State.FLY; pickWander(g); }
        }
        if (g.state == State.LEAVE) {
            double a = Math.toRadians(coastDeg);
            want = new double[]{Math.sin(a) * 12, 0, -Math.cos(a) * 12};
            wantY = lee ? 8 : 30;
            if (Math.hypot(g.pos[0] - homeX, g.pos[2] - homeZ) > homeRadius * 6) {
                g.state = State.AWAY;
                g.timer = 120 + g.rnd.nextDouble() * 600;
                return;
            }
            if (!night && !away && !lee) { g.state = State.FLY; pickWander(g); }
        }
        fly(g, want, wantY, wind, h);
        if (g.state != State.PERCH && g.state != State.AWAY) maybeCall(g, h, g.state == State.LAND ? 1 / 10.0 : 1 / 70.0);
    }

    /** Fliegen: gewünschte Geschwindigkeit plus Schwarm und Ausweichen, Grenzen gegen die Luft, Steigen und Sinken. */
    private void fly(Gull g, double[] want, double wantY, double[] wind, double h) {
        double[] steer = {want[0] - g.vel[0], 0, want[2] - g.vel[2]};
        // Schwarm: Abstand (4 m), Richtung und Zusammenhalt (25 m)
        double ax = 0, az = 0, cx = 0, cz = 0, sx = 0, sz = 0, sy = 0;
        int n = 0;
        for (Gull o : gulls) {
            if (o == g || o.species != g.species || o.state == State.AWAY || o.state == State.PERCH || o.state == State.SWIM || o.state == State.DIVE || o.state == State.WALK) continue;
            double dx = o.pos[0] - g.pos[0], dy = o.pos[1] - g.pos[1], dz = o.pos[2] - g.pos[2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 4 && d > 1e-6) { sx -= dx / d * (4 - d); sz -= dz / d * (4 - d); sy -= dy / d * (4 - d); }
            if (d < 25) { ax += o.vel[0]; az += o.vel[2]; cx += o.pos[0]; cz += o.pos[2]; n++; }
        }
        double flockW = g.state == State.FLY ? 1 : 0.2;
        if (n > 0) {
            steer[0] += flockW * (0.15 * (ax / n - g.vel[0]) + 0.02 * (cx / n - g.pos[0]));
            steer[2] += flockW * (0.15 * (az / n - g.vel[2]) + 0.02 * (cz / n - g.pos[2]));
        }
        steer[0] += sx * 1.5;
        steer[2] += sz * 1.5;
        // Hindernisse: weg von der Wand, wenn nah und darunter
        for (Obstacle o : obstacles) {
            double dx = g.pos[0] - o.x, dz = g.pos[2] - o.z, d = Math.hypot(dx, dz);
            double clear = g.state == State.LAND || g.state == State.TAKEOFF ? 0.8 : 5;
            boolean nearPerch = g.state == State.LAND && g.perch != null && dist(g, g.perch.spot.position()) < 25 || g.state == State.TAKEOFF;
            if (!nearPerch && g.pos[1] < o.y1 + 3 && d < o.r + clear + 8 && d > 1e-6) {
                double push = (o.r + clear + 8 - d) * 1.2;
                steer[0] += dx / d * push;
                steer[2] += dz / d * push;
            }
        }
        // Ins Revier zurück
        double hX = g.species == Species.MOEWE ? homeX : g.homeX, hZ = g.species == Species.MOEWE ? homeZ : g.homeZ, hR = g.species == Species.MOEWE ? homeRadius : g.homeR;
        double hx = g.pos[0] - hX, hz = g.pos[2] - hZ, hd = Math.hypot(hx, hz);
        if (g.state == State.FLY && hd > hR && hd > 1e-6) { steer[0] -= hx / hd * (hd - hR) * 0.05; steer[2] -= hz / hd * (hd - hR) * 0.05; }
        double maxA = g.state == State.LAND || g.state == State.SOAR ? 6 : g.species == Species.SCHWALBE ? 14 : 4;
        double sl = Math.hypot(steer[0], steer[2]);
        if (sl > maxA) { steer[0] *= maxA / sl; steer[2] *= maxA / sl; }
        double ox = g.vel[0], oz = g.vel[2];
        g.vel[0] += steer[0] * h;
        g.vel[2] += steer[2] * h;
        // gegen die Luft zwischen V_MIN und V_MAX (beim Landen und Stehen darf sie langsamer werden: sie hängt im Wind)
        double vx = g.vel[0] - wind[0], vz = g.vel[2] - wind[2], va = Math.hypot(vx, vz);
        Species sp = g.species;
        double vmin = g.state == State.LAND || g.state == State.TAKEOFF ? 2 : g.state == State.SOAR ? Math.min(sp.vMin, windSpeed * 0.9) : sp.vMin;
        if (va < vmin) {
            double k = va < 1e-6 ? 0 : vmin / va;
            if (va < 1e-6) { vx = -wind[0]; vz = -wind[2]; va = Math.hypot(vx, vz); k = va < 1e-6 ? 0 : vmin / va; if (va < 1e-6) { vx = vmin; vz = 0; k = 1; } }
            g.vel[0] = wind[0] + vx * k;
            g.vel[2] = wind[2] + vz * k;
        } else if (va > sp.vMax) {
            g.vel[0] = wind[0] + vx * sp.vMax / va;
            g.vel[2] = wind[2] + vz * sp.vMax / va;
        }
        g.airspeed = Math.hypot(g.vel[0] - wind[0], g.vel[2] - wind[2]);
        // senkrecht: Aufwind und Sinken, Flügelschlag nach Bedarf
        double up = updraft(g.pos[0], g.pos[1], g.pos[2]);
        double ground = groundAt(g);
        wantY = Math.max(wantY, ground + (g.state == State.LAND ? -10 : sp == Species.SCHWALBE ? 0.5 : 2.5));
        double needVy = Math.max(-3, Math.min(3, (wantY - g.pos[1]) * 0.6));
        double free = up - sp.sink * (g.state == State.SOAR ? 0.8 : 1);
        double flapNeed = (needVy - free) / sp.climb;
        double flap = Math.max(0, Math.min(1, flapNeed));
        if (g.state == State.TAKEOFF || g.state == State.LAND && Math.hypot(g.vel[0], g.vel[2]) < 4) flap = Math.max(flap, 0.8);
        // Arten, die kaum gleiten (Enten, Schwäne, Kormorane, Krähen), schlagen fast immer
        flap = Math.max(flap, 1 - sp.glide - (sp.glide < 0.5 ? 0 : 1));
        if (sp == Species.SCHWALBE) flap = 0.5 + 0.5 * Math.signum(Math.sin(time * 1.3 + g.id * 2.1));
        g.flap += (flap - g.flap) * Math.min(1, h * 3);
        double vy = Math.min(needVy, free + g.flap * sp.climb);
        if (needVy < free) vy = needVy;              // Höhe abbauen geht immer (Bremsen, Gleiten)
        g.vel[1] += (vy - g.vel[1]) * Math.min(1, h * 2.5);
        g.pos[0] += g.vel[0] * h;
        g.pos[1] += g.vel[1] * h;
        g.pos[2] += g.vel[2] * h;
        double minAbove = sp == Species.SCHWALBE ? 0.35 : 0.8;
        if (g.pos[1] < ground + minAbove && g.state != State.LAND) { g.pos[1] = ground + minAbove; g.vel[1] = Math.max(0, g.vel[1]); }
        // nie in ein Hindernis
        for (Obstacle o : obstacles) {
            double dx = g.pos[0] - o.x, dz = g.pos[2] - o.z, d = Math.hypot(dx, dz);
            if (d < o.r + 0.3 && g.pos[1] > o.y0 && g.pos[1] < o.y1 && d > 1e-6) {
                g.pos[0] = o.x + dx / d * (o.r + 0.3);
                g.pos[2] = o.z + dz / d * (o.r + 0.3);
            }
        }
        // Flügel und Querneigung
        if (g.state != State.SOAR && Math.hypot(g.vel[0], g.vel[2]) > 0.5) g.facing = Math.toDegrees(Math.atan2(g.vel[0], -g.vel[2]));
        // Schlag mit 3,1 Hz, solange sie schlägt; im Gleiten bleibt die Phase stehen
        if (g.flap > 0.05) g.wingPhase = (g.wingPhase + 2 * Math.PI * sp.flapHz * h) % (2 * Math.PI);
        double lat = ((g.vel[0] - ox) * -g.vel[2] + (g.vel[2] - oz) * g.vel[0]) / Math.max(1e-6, Math.hypot(g.vel[0], g.vel[2])) / h;
        double bank = Math.atan(Math.max(-15, Math.min(15, lat)) / 9.81);
        g.bank += (bank - g.bank) * Math.min(1, h * 3);
        g.fold = Math.max(0, g.fold - h * 3);
    }

    static double dist(Gull g, double[] p) { return Math.sqrt((g.pos[0] - p[0]) * (g.pos[0] - p[0]) + (g.pos[1] - p[1]) * (g.pos[1] - p[1]) + (g.pos[2] - p[2]) * (g.pos[2] - p[2])); }

    private double groundAt(Gull g) { Ground gr = ground; return gr == null ? 0 : gr.height(g.pos[0], g.pos[2]); }

    private double[] toward(Gull g, double tx, double tz, double speed, double[] wind) {
        double dx = tx - g.pos[0], dz = tz - g.pos[2], d = Math.max(1e-6, Math.hypot(dx, dz));
        return new double[]{dx / d * speed + (wind != null ? wind[0] * 0.6 : 0), 0, dz / d * speed + (wind != null ? wind[2] * 0.6 : 0)};
    }

    private void pickWander(Gull g) {
        boolean lee = windSpeed >= LEE_WIND;
        double a, r;
        if (lee && !obstacles.isEmpty()) {
            Obstacle o = obstacles.get(0);
            double wa = Math.toRadians(windFromDeg + 180), along = o.r + 6 + g.rnd.nextDouble() * 45, lat = (g.rnd.nextDouble() - 0.5) * o.r * 2.4;
            g.wanderX = o.x + Math.sin(wa) * along + Math.cos(wa) * lat;
            g.wanderZ = o.z - Math.cos(wa) * along + Math.sin(wa) * lat;
            g.wanderY = 3 + g.rnd.nextDouble() * 10;
        } else {
            a = g.rnd.nextDouble() * 2 * Math.PI;
            r = homeRadius * Math.sqrt(g.rnd.nextDouble());
            g.wanderX = homeX + Math.sin(a) * r;
            g.wanderZ = homeZ - Math.cos(a) * r;
            g.wanderY = g.rnd.nextDouble() < 0.25 ? 3 + g.rnd.nextDouble() * 4 : 10 + g.rnd.nextDouble() * 45;
        }
        g.timer = 15 + g.rnd.nextDouble() * 30;
    }

    /** Liegt der Ort im Lee des ersten Hindernisses (hinter ihm, vom Wind aus gesehen)? */
    boolean inLee(double[] p) {
        if (obstacles.isEmpty()) return true;
        Obstacle o = obstacles.get(0);
        double a = Math.toRadians(windFromDeg + 180);
        return (p[0] - o.x) * Math.sin(a) - (p[2] - o.z) * Math.cos(a) > o.r * 0.3;
    }

    private Perch freePerch(Gull g, boolean leeOnly) {
        List<Perch> free = new ArrayList<>();
        for (Perch p : perches) if (p.by == null && (p.mask & g.species.bit()) != 0 && p.spot.usable() && (!leeOnly || inLee(p.spot.position()))) free.add(p);
        if (free.isEmpty()) return null;
        // nahe und beliebte Plätze bevorzugen (seit Haithabu Phase 14: Abstand geteilt durch die Beliebtheit, fünf Proben)
        Perch best = null;
        double bd = 1e18;
        for (int k = 0; k < 5; k++) {
            Perch p = free.get(g.rnd.nextInt(free.size()));
            double[] q = p.spot.position();
            double d = (Math.hypot(q[0] - g.pos[0], q[2] - g.pos[2]) + 20) / p.appeal;
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private void takeoff(Gull g) {
        release(g);
        g.state = State.TAKEOFF;
        g.timer = 2.5;
        double a = Math.toRadians(windFromDeg);
        // springt gegen den Wind ab
        g.vel[0] = Math.sin(a) * 2;
        g.vel[2] = -Math.cos(a) * 2;
        g.vel[1] = 2;
        callNow(g);
    }

    private void release(Gull g) {
        if (g.perch != null && g.perch.by == g) g.perch.by = null;
        g.perch = null;
    }

    /** Rufe kommen zufällig mit der Rate (je s), aber nie kurz nacheinander. */
    private void maybeCall(Gull g, double h, double rate) {
        if (night()) return;
        g.nextCall -= h;
        if (g.nextCall > 0) return;
        g.nextCall = 4 - Math.log(1 - g.rnd.nextDouble()) / Math.max(1e-3, rate);
        fire(g);
    }

    /** Ein Ruf jetzt (beim Landen und Abfliegen), wenn der letzte mindestens 2 s her ist. */
    private void callNow(Gull g) {
        if (night()) return;
        fire(g);
        g.nextCall = Math.max(g.nextCall, 6);
    }

    private void fire(Gull g) {
        calls++;
        Call c = onCall;
        if (c != null) c.call(g.pos[0], g.pos[1], g.pos[2], 0.6 + 0.4 * g.rnd.nextDouble(), g);
    }

    static double turnToward(double from, double to, double maxStep) {
        double d = ((to - from) % 360 + 540) % 360 - 180;
        return (from + Math.max(-maxStep, Math.min(maxStep, d)) + 360) % 360;
    }


    // ------------------------------------------------------------------ Phase 13: die übrigen Arten

    /** Höhe des Beins unter der Körpermitte (m) einer Art (das Netz der Möwe: 0,16 m). */
    public static double legHeight(Species sp) { return 0.16 * sp.scale() * (sp == Species.SCHWAN ? 0.8 : 1); }

    /** Lage der Körpermitte über dem Wasser beim Schwimmen (m): Enten und Schwäne liegen hoch, Kormorane tief. */
    public static double floatHeight(Species sp) {
        return switch (sp) { case KORMORAN -> -0.02 * sp.scale(); case SCHWAN -> 0.03 * sp.scale(); default -> 0.04 * sp.scale(); };
    }

    private double surface(double x, double z) {
        Water w = water;
        if (w != null) return w.surface(x, z);
        Ground gr = ground;
        return gr == null ? 0 : gr.height(x, z);
    }

    private double groundOf(double x, double z) { Ground gr = ground; return gr == null ? 0 : gr.height(x, z); }

    /** Ein Vogel einer Art an seinem Platz: Schwimmer auf dem Wasser, Krähen auf einem Futterplatz, Schwalben in der Luft. */
    private Gull spawn(Species sp, int id) {
        Gull g = new Gull(id, seed, sp);
        g.homeX = homeX;
        g.homeZ = homeZ;
        g.homeR = homeRadius * (sp == Species.SCHWALBE ? 0.8 : 1.6);
        g.nextCall = 5 + g.rnd.nextDouble() * 60;
        g.wingPhase = g.rnd.nextDouble() * 6.28;
        g.facing = g.rnd.nextDouble() * 360;
        if (!sp.present(dayOfYear)) { g.state = State.AWAY; g.timer = 30; return g; }
        if (sp.swims) {
            double[] p = waterPoint(g);
            if (p == null) { g.state = State.AWAY; g.timer = 60; return g; }
            g.pos[0] = p[0]; g.pos[2] = p[2]; g.pos[1] = p[1] + floatHeight(sp);
            g.state = State.SWIM;
            g.fold = 1;
            g.timer = 20 + g.rnd.nextDouble() * 120;
        } else if (sp == Species.KONDOR) {
            double cx = Double.isNaN(condorX) ? homeX : condorX, cz = Double.isNaN(condorZ) ? homeZ : condorZ;
            double an = g.rnd.nextDouble() * 6.283, rr = condorRadius * 0.7 * Math.sqrt(g.rnd.nextDouble());
            g.pos[0] = cx + Math.sin(an) * rr; g.pos[2] = cz - Math.cos(an) * rr;
            g.pos[1] = groundOf(g.pos[0], g.pos[2]) + condorFloor + (condorCeiling - condorFloor) * (0.3 + 0.5 * g.rnd.nextDouble());
            g.vel[0] = Math.sin(an) * sp.vCruise; g.vel[2] = -Math.cos(an) * sp.vCruise;
            g.state = State.FLY;
            g.approach = 0;
            pickThermal(g);
        } else if (sp == Species.KRAEHE && !feeding.isEmpty()) {
            int ai = g.rnd.nextInt(feeding.size());
            Area a = feeding.get(ai);
            g.area = ai;
            g.homeX = a.x(); g.homeZ = a.z(); g.homeR = Math.max(60, a.r() * 2);
            double an = g.rnd.nextDouble() * 6.283, rr = a.r() * Math.sqrt(g.rnd.nextDouble());
            g.pos[0] = a.x() + Math.sin(an) * rr; g.pos[2] = a.z() - Math.cos(an) * rr;
            g.pos[1] = groundOf(g.pos[0], g.pos[2]) + legHeight(sp);
            g.state = State.WALK;
            g.fold = 1;
            g.timer = 20 + g.rnd.nextDouble() * 90;
        } else {
            double an = g.rnd.nextDouble() * 6.283, rr = g.homeR * Math.sqrt(g.rnd.nextDouble());
            g.pos[0] = g.homeX + Math.sin(an) * rr; g.pos[2] = g.homeZ - Math.cos(an) * rr;
            g.pos[1] = groundOf(g.pos[0], g.pos[2]) + 3 + g.rnd.nextDouble() * 6;
            g.vel[0] = Math.sin(an) * sp.vCruise; g.vel[2] = -Math.cos(an) * sp.vCruise;
            g.state = State.FLY;
            wanderOther(g);
        }
        return g;
    }

    /** Ein Punkt offenen Wassers in einer der Wasserflächen (bevorzugt der des Vogels), oder null. */
    private double[] waterPoint(Gull g) {
        if (waters.isEmpty()) return null;
        for (int k = 0; k < 16; k++) {
            int ai = g.area >= 0 && g.area < waters.size() && k < 8 ? g.area : g.rnd.nextInt(waters.size());
            Area a = waters.get(ai);
            double an = g.rnd.nextDouble() * 6.283, rr = a.r() * Math.sqrt(g.rnd.nextDouble());
            double x = a.x() + Math.sin(an) * rr, z = a.z() - Math.cos(an) * rr, y = surface(x, z);
            if (!Double.isNaN(y)) { g.area = ai; return new double[]{x, y, z}; }
        }
        return null;
    }

    /** Ist ein Störer näher als die Fluchtdistanz? Merkt sich die Richtung weg von ihm. */
    boolean disturbed(Gull g) {
        double fd = g.species.flightDistance;
        for (double[] d : disturbers) {
            double dx = g.pos[0] - d[0], dz = g.pos[2] - d[2], dy = g.pos[1] - d[1];
            double r = Math.hypot(dx, dz);
            if (r < fd && Math.abs(dy) < Math.max(8, fd)) {
                double l = Math.max(1e-6, r);
                g.fleeX = dx / l;
                g.fleeZ = dz / l;
                return true;
            }
        }
        return false;
    }

    /** Schwimmen: langsam mit eigenem Kurs, beim Ufer drehen, lose beieinander; Enten und Schwäne gründeln. */
    private void swim(Gull g, double h, double speed) {
        Species sp = g.species;
        double a = Math.toRadians(g.facing);
        // Zusammenhalt: zu Artgenossen in 30 m, Abstand unter 1,5 Körperlängen
        double cx = 0, cz = 0, sx = 0, sz = 0;
        int n = 0;
        for (Gull o : gulls) {
            if (o == g || o.species != sp || o.state != State.SWIM) continue;
            double dx = o.pos[0] - g.pos[0], dz = o.pos[2] - g.pos[2], d = Math.hypot(dx, dz);
            if (d < 30) { cx += dx; cz += dz; n++; }
            double near = sp.length * 1.5;
            if (d < near && d > 1e-6) { sx -= dx / d * (near - d); sz -= dz / d * (near - d); }
        }
        double tx = Math.sin(a), tz = -Math.cos(a);
        if (n > 0) { tx += cx / n * 0.02; tz += cz / n * 0.02; }
        tx += sx * 0.8; tz += sz * 0.8;
        // Ufer und Eis: vorausschauen und abdrehen
        double look = 1.5 + sp.length * 2;
        if (Double.isNaN(surface(g.pos[0] + tx * look, g.pos[2] + tz * look))) {
            boolean found = false;
            double bx = -tx, bz = -tz;
            for (int k = 1; k < 12 && !found; k++) {
                double ang = a + 0.52 * ((k + 1) / 2) * (k % 2 == 0 ? 1 : -1);
                double qx = Math.sin(ang), qz = -Math.cos(ang);
                if (!Double.isNaN(surface(g.pos[0] + qx * look, g.pos[2] + qz * look))) { bx = qx; bz = qz; found = true; }
            }
            tx = bx; tz = bz;
        }
        double want = Math.toDegrees(Math.atan2(tx, -tz));
        if (g.rnd.nextDouble() < h / 6) g.slotSide = (g.rnd.nextDouble() - 0.5) * 70;
        g.facing = turnToward(g.facing, want + g.slotSide * 0.3, 35 * h);
        double sp0 = g.dabble > 0.3 ? 0.05 : speed;
        double fa = Math.toRadians(g.facing);
        double nx = g.pos[0] + Math.sin(fa) * sp0 * h, nz = g.pos[2] - Math.cos(fa) * sp0 * h;
        double y = surface(nx, nz);
        if (!Double.isNaN(y)) { g.pos[0] = nx; g.pos[2] = nz; g.pos[1] = y + floatHeight(sp); }
        g.vel[0] = Math.sin(fa) * sp0; g.vel[1] = 0; g.vel[2] = -Math.cos(fa) * sp0;
        g.fold = Math.min(1, g.fold + h * 2);
        g.flap = 0;
        g.bank = 0;
        g.spread = Math.max(0, g.spread - h);
        // Gründeln: Enten und Schwäne stecken alle halbe Minute den Kopf ins Wasser (Schwanz hoch), nachts nicht
        boolean dab = (sp == Species.ENTE || sp == Species.SCHWAN) && !night();
        if (dab && g.dabble <= 0.01 && g.slotDist <= 0 && g.rnd.nextDouble() < h / 25) g.slotDist = 3 + g.rnd.nextDouble() * 6;
        if (g.slotDist > 0) { g.slotDist -= h; g.dabble = Math.min(1, g.dabble + h * 3); }
        else g.dabble = Math.max(0, g.dabble - h * 3);
    }

    /** Vom Wasser auffliegen: steil, mit vollem Schlag, weg vom Störer. */
    private void takeoffWater(Gull g) {
        Species sp = g.species;
        g.state = State.TAKEOFF;
        g.timer = sp == Species.SCHWAN ? 5 : 2.2;
        double fx = g.fleeX, fz = g.fleeZ;
        if (Math.hypot(fx, fz) < 0.1) { double a = Math.toRadians(windFromDeg); fx = Math.sin(a); fz = -Math.cos(a); }
        g.vel[0] = fx * 3; g.vel[2] = fz * 3; g.vel[1] = sp == Species.ENTE ? 3.5 : 1.5;
        g.dabble = 0;
        g.slotDist = 0;
        g.fold = 0.3;
        g.flap = 1;
        g.fleeX = g.fleeZ = 0;
        callNow(g);
    }

    /** Auf dem Wasser landen: ein Ziel in einer Wasserfläche, flach anfliegen. */
    private void landOnWater(Gull g) {
        double[] p = waterPoint(g);
        if (p == null) return;
        g.wanderX = p[0]; g.wanderY = p[1]; g.wanderZ = p[2];
        g.state = State.LAND;
        release(g);
        g.timer = 60;
        g.approach = 2;      // 2 = aufs Wasser
    }

    private void wanderOther(Gull g) {
        Species sp = g.species;
        double an = g.rnd.nextDouble() * 6.283, rr = g.homeR * Math.sqrt(g.rnd.nextDouble());
        g.wanderX = g.homeX + Math.sin(an) * rr;
        g.wanderZ = g.homeZ - Math.cos(an) * rr;
        double w = surface(g.wanderX, g.wanderZ), base = Math.max(groundOf(g.wanderX, g.wanderZ), Double.isNaN(w) ? -1e9 : w);
        g.wanderY = base + switch (sp) {
            case SCHWALBE -> 0.6 + g.rnd.nextDouble() * 6;
            case KRAEHE -> 8 + g.rnd.nextDouble() * 25;
            default -> 4 + g.rnd.nextDouble() * 10;
        };
        g.timer = sp == Species.SCHWALBE ? 1.5 + g.rnd.nextDouble() * 2.5 : 10 + g.rnd.nextDouble() * 20;
    }

    /** Ein Schritt für Krähe, Schwalbe, Ente, Schwan und Kormoran. */
    private void stepOther(Gull g, double h) {
        Species sp = g.species;
        boolean night = night();
        g.timer -= h;
        if (!sp.present(dayOfYear)) {
            if (g.state != State.AWAY) { release(g); g.state = State.AWAY; }
            return;
        }
        if (g.state == State.AWAY) {
            // zurück am Tag (Schwimmer auch nachts, wenn es wieder offenes Wasser gibt)
            if (g.timer > 0 || night && !sp.swims) return;
            Gull fresh = spawn(sp, g.id);
            if (fresh.state == State.AWAY) { g.timer = 60; return; }
            System.arraycopy(fresh.pos, 0, g.pos, 0, 3);
            System.arraycopy(fresh.vel, 0, g.vel, 0, 3);
            g.state = fresh.state; g.timer = fresh.timer; g.fold = fresh.fold; g.area = fresh.area;
            g.homeX = fresh.homeX; g.homeZ = fresh.homeZ; g.homeR = fresh.homeR;
            g.wanderX = fresh.wanderX; g.wanderY = fresh.wanderY; g.wanderZ = fresh.wanderZ;
            return;
        }
        if (sp == Species.KONDOR) { stepCondor(g, h, night); return; }
        double[] wind = windAt(g.pos[0], g.pos[1] - groundOf(g.pos[0], g.pos[2]), g.pos[2]);
        switch (g.state) {
            case SWIM -> {
                // Eis oder Land unter dem Vogel: weg
                if (Double.isNaN(surface(g.pos[0], g.pos[2]))) {
                    if (waterPoint(g) == null) { g.state = State.AWAY; g.timer = 600; return; }
                    takeoffWater(g);
                    return;
                }
                swim(g, h, sp == Species.SCHWAN ? 0.35 : sp == Species.KORMORAN ? 0.6 : 0.45);
                if (disturbed(g)) {
                    if (sp == Species.KORMORAN) { g.state = State.DIVE; g.timer = 15 + g.rnd.nextDouble() * 20; return; }
                    if (sp == Species.SCHWAN) {
                        // Schwäne schwimmen weg, fliegen nur, wenn es sehr eng wird
                        double d = Math.toDegrees(Math.atan2(g.fleeX, -g.fleeZ));
                        g.facing = turnToward(g.facing, d, 120 * h);
                        swim(g, h, 1.2);
                        boolean close = false;
                        for (double[] q : disturbers) close |= Math.hypot(q[0] - g.pos[0], q[2] - g.pos[2]) < 4;
                        if (close) takeoffWater(g);
                        return;
                    }
                    takeoffWater(g);
                    return;
                }
                if (g.timer <= 0 && !night) {
                    g.timer = 30 + g.rnd.nextDouble() * 150;
                    if (sp == Species.KORMORAN) {
                        double u = g.rnd.nextDouble();
                        if (u < 0.55) { g.state = State.DIVE; g.timer = 20 + g.rnd.nextDouble() * 25; }
                        else if (u < 0.8 && freePerch(g, false) != null) { takeoffWater(g); g.approach = 3; }
                    } else if (sp == Species.ENTE && g.rnd.nextDouble() < 0.25) takeoffWater(g);
                }
                if (!night) maybeCall(g, h, sp == Species.ENTE ? 1 / 60.0 : 1 / 200.0);
                return;
            }
            case DIVE -> {
                // unter Wasser: 1 m/s in eigener Richtung, dann wieder auftauchen
                double fa = Math.toRadians(g.facing);
                double nx = g.pos[0] + Math.sin(fa) * h, nz = g.pos[2] - Math.cos(fa) * h;
                if (!Double.isNaN(surface(nx, nz))) { g.pos[0] = nx; g.pos[2] = nz; } else g.facing = (g.facing + 150) % 360;
                double y = surface(g.pos[0], g.pos[2]);
                g.pos[1] = (Double.isNaN(y) ? g.pos[1] : y) - 1.2;
                if (g.timer <= 0) { g.state = State.SWIM; g.timer = 15 + g.rnd.nextDouble() * 40; g.pos[1] = (Double.isNaN(y) ? 0 : y) + floatHeight(sp); }
                return;
            }
            case PERCH -> {
                // Krähen schlafen gemeinsam in einem Wäldchen außerhalb der Szene
                if (night && sp == Species.KRAEHE) { release(g); g.state = State.AWAY; g.timer = 600 + g.rnd.nextDouble() * 1200; return; }
                double[] p = g.perch.spot.position();
                g.pos[0] = p[0]; g.pos[1] = p[1]; g.pos[2] = p[2];
                g.vel[0] = g.vel[1] = g.vel[2] = 0;
                g.flap = 0;
                g.facing = turnToward(g.facing, (windFromDeg + (sp == Species.KORMORAN ? 180 : 0)) % 360, 30 * h);
                // der Kormoran trocknet die Flügel: ausgebreitet, Rücken zum Wind
                if (sp == Species.KORMORAN && !night) { g.spread = Math.min(1, g.spread + h * 0.8); g.fold = Math.max(0, 1 - g.spread); }
                else { g.spread = Math.max(0, g.spread - h); g.fold = Math.min(1, g.fold + h * 1.5); }
                if (disturbed(g) || g.timer <= 0 && !night || !g.perch.spot.usable()) {
                    release(g);
                    g.spread = 0;
                    g.state = State.TAKEOFF;
                    g.timer = 2;
                    g.approach = 0;
                    double a = Math.toRadians(windFromDeg);
                    g.vel[0] = Math.sin(a) * 2; g.vel[2] = -Math.cos(a) * 2; g.vel[1] = 1.5;
                    callNow(g);
                }
                return;
            }
            case WALK -> {
                // am Boden: Schritte, Pausen, Picken (Kopf runter); weg, wenn jemand kommt oder es dunkel wird
                boolean dist = disturbed(g);
                if (dist || night || g.timer <= 0) {
                    g.state = State.TAKEOFF;
                    g.timer = 1.8;
                    g.approach = 0;
                    double fx = g.fleeX, fz = g.fleeZ;
                    if (!dist) { double a = Math.toRadians(windFromDeg); fx = Math.sin(a); fz = -Math.cos(a); }
                    g.vel[0] = fx * 3; g.vel[2] = fz * 3; g.vel[1] = 2.5;
                    g.dabble = 0;
                    callNow(g);
                    return;
                }
                if (g.slotDist <= 0 && g.rnd.nextDouble() < h / 2.5) g.slotDist = 0.6 + g.rnd.nextDouble() * 2;      // eine Weile gehen
                if (g.slotDist > 0) {
                    g.slotDist -= h;
                    if (g.rnd.nextDouble() < h) g.facing = (g.facing + (g.rnd.nextDouble() - 0.5) * 90 + 360) % 360;
                    // im Futterplatz bleiben
                    Area a = g.area >= 0 && g.area < feeding.size() ? feeding.get(g.area) : null;
                    if (a != null && Math.hypot(g.pos[0] - a.x(), g.pos[2] - a.z()) > a.r())
                        g.facing = turnToward(g.facing, Math.toDegrees(Math.atan2(a.x() - g.pos[0], -(a.z() - g.pos[2]))), 180 * h);
                    double fa = Math.toRadians(g.facing), v = 0.45;
                    g.pos[0] += Math.sin(fa) * v * h;
                    g.pos[2] -= Math.cos(fa) * v * h;
                    g.walkPhase = (g.walkPhase + h * 2 * Math.PI * 2.2) % (2 * Math.PI);
                    g.dabble = Math.max(0, g.dabble - h * 4);
                } else {
                    // picken
                    if (g.rnd.nextDouble() < h * 1.5) g.slotUp = g.slotUp > 0 ? 0 : 0.35;
                    g.slotUp -= h;
                    g.dabble = g.slotUp > 0 ? Math.min(1, g.dabble + h * 6) : Math.max(0, g.dabble - h * 6);
                }
                g.pos[1] = groundOf(g.pos[0], g.pos[2]) + legHeight(sp);
                g.vel[0] = g.vel[1] = g.vel[2] = 0;
                g.fold = Math.min(1, g.fold + h * 2);
                g.flap = 0;
                maybeCall(g, h, 1 / 40.0);
                return;
            }
            default -> { }
        }
        // in der Luft: FLY, LAND, TAKEOFF
        double[] want;
        double wantY;
        if (g.state == State.TAKEOFF) {
            want = new double[]{g.vel[0] * 1.2, 0, g.vel[2] * 1.2};
            double l = Math.hypot(want[0], want[2]);
            if (l < sp.vMin * 0.5) {
                if (l < 1e-6) { want[0] = sp.vMin * 0.5; want[2] = 0; }
                else { want[0] *= sp.vMin * 0.5 / l; want[2] *= sp.vMin * 0.5 / l; }
            }
            wantY = g.pos[1] + 5;
            g.fold = Math.max(0, g.fold - h * 4);
            if (g.timer <= 0) {
                g.state = State.FLY;
                Perch p = g.approach == 3 ? freePerch(g, false) : null;
                if (p != null) { g.perch = p; p.by = g; g.state = State.LAND; g.timer = 90; g.approach = 0; }
                else if (sp.swims) landOnWater(g);
                if (g.state == State.FLY) wanderOther(g);
            }
        } else if (g.state == State.LAND) {
            double[] p = g.perch != null ? g.perch.spot.position() : new double[]{g.wanderX, g.wanderY, g.wanderZ};
            if (g.perch == null && g.approach == 2) p[1] = surface(p[0], p[2]) + floatHeight(sp);
            if (g.perch == null && sp == Species.KRAEHE) p[1] = groundOf(p[0], p[2]) + legHeight(sp);
            if (Double.isNaN(p[1]) || g.timer <= 0) { release(g); g.state = State.FLY; wanderOther(g); return; }
            double dx = p[0] - g.pos[0], dz = p[2] - g.pos[2], d = Math.hypot(dx, dz), dd = Math.sqrt(d * d + (p[1] - g.pos[1]) * (p[1] - g.pos[1]));
            if (dd < (g.approach == 2 ? 1.5 : 1.0)) {
                if (Math.hypot(g.vel[0], g.vel[2]) > 0.3) g.facing = Math.toDegrees(Math.atan2(g.vel[0], -g.vel[2]));
                g.pos[0] = p[0]; g.pos[1] = p[1]; g.pos[2] = p[2];
                g.vel[0] = g.vel[1] = g.vel[2] = 0;
                if (g.perch != null) { g.state = State.PERCH; g.timer = 60 + g.rnd.nextDouble() * 240; }
                else if (sp.swims) { g.state = State.SWIM; g.timer = 30 + g.rnd.nextDouble() * 120; }
                else { g.state = State.WALK; g.timer = 25 + g.rnd.nextDouble() * 90; }
                g.approach = 0;
                callNow(g);
                return;
            }
            if (d < 4) {
                // Endanflug: gerade auf den Platz
                double step = Math.min(dd, 2.5 * h);
                for (int k = 0; k < 3; k++) {
                    g.vel[k] = (p[k] - g.pos[k]) / Math.max(1e-6, dd) * 2.5;
                    g.pos[k] += (p[k] - g.pos[k]) / Math.max(1e-6, dd) * step;
                }
                g.flap += (1 - g.flap) * Math.min(1, h * 4);
                g.wingPhase = (g.wingPhase + 2 * Math.PI * sp.flapHz * h) % (2 * Math.PI);
                return;
            }
            double v = Math.max(1.5, Math.min(sp.vCruise, d * 0.5));
            want = toward(g, p[0], p[2], v, null);
            wantY = p[1] + Math.min(8, d * (g.approach == 2 ? 0.08 : 0.2));
        } else {
            // FLY: umherfliegen; Schwalben tief und wendig, Krähen landen auf Futterplätzen oder Firsten, Schwimmer suchen Wasser
            if (night && (sp == Species.SCHWALBE || sp == Species.KRAEHE)) {
                // zum Schlafplatz (außerhalb der Szene)
                g.state = State.AWAY;
                g.timer = 600 + g.rnd.nextDouble() * 1200;
                return;
            }
            double dx = g.wanderX - g.pos[0], dz = g.wanderZ - g.pos[2];
            if (Math.hypot(dx, dz) < (sp == Species.SCHWALBE ? 6 : 20) || g.timer <= 0) wanderOther(g);
            want = toward(g, g.wanderX, g.wanderZ, sp.vCruise, wind);
            wantY = g.wanderY;
            if (sp == Species.KRAEHE && g.rnd.nextDouble() < h / 18) {
                Perch p = g.rnd.nextDouble() < 0.35 ? freePerch(g, false) : null;
                if (p != null) { g.perch = p; p.by = g; g.state = State.LAND; g.timer = 90; g.approach = 0; }
                else if (!feeding.isEmpty()) {
                    int ai = g.area >= 0 && g.area < feeding.size() && g.rnd.nextDouble() < 0.7 ? g.area : g.rnd.nextInt(feeding.size());
                    Area a = feeding.get(ai);
                    g.area = ai;
                    double an = g.rnd.nextDouble() * 6.283, rr = a.r() * Math.sqrt(g.rnd.nextDouble());
                    g.wanderX = a.x() + Math.sin(an) * rr; g.wanderZ = a.z() - Math.cos(an) * rr; g.wanderY = groundOf(g.wanderX, g.wanderZ);
                    g.homeX = a.x(); g.homeZ = a.z();
                    release(g); g.state = State.LAND; g.timer = 90; g.approach = 1;
                }
            } else if (sp.swims && g.rnd.nextDouble() < h / 12) landOnWater(g);
        }
        fly(g, want, wantY, wind, h);
        if (g.state == State.FLY && !night) maybeCall(g, h, sp == Species.KRAEHE ? 1 / 25.0 : sp == Species.SCHWALBE ? 1 / 15.0 : 1 / 90.0);
    }

    // ------------------------------------------------------------------ Glacier Phase 10: Kondore

    /** Steigen in der Thermik (m/s): mit der Sonne (Helligkeit) und als Hangwind mit dem Wind; nachts nichts. */
    public double condorLift() {
        double sun = Math.max(0, Math.min(1, ambientLux / 60_000));
        return 0.4 + 2.4 * sun + 0.06 * Math.min(windSpeed, 15);
    }

    /** Nächste Thermik: eine der {@link #thermals} (bevorzugt eine andere als die letzte) oder ein Punkt im Revier. */
    private void pickThermal(Gull g) {
        double cx = Double.isNaN(condorX) ? homeX : condorX, cz = Double.isNaN(condorZ) ? homeZ : condorZ;
        if (!thermals.isEmpty()) {
            int k = g.rnd.nextInt(thermals.size());
            if (k == g.area && thermals.size() > 1) k = (k + 1) % thermals.size();
            Area a = thermals.get(k);
            g.area = k;
            double an = g.rnd.nextDouble() * 6.283, rr = a.r() * 0.5 * Math.sqrt(g.rnd.nextDouble());
            g.wanderX = a.x() + Math.sin(an) * rr;
            g.wanderZ = a.z() - Math.cos(an) * rr;
        } else {
            double an = g.rnd.nextDouble() * 6.283, rr = condorRadius * Math.sqrt(g.rnd.nextDouble());
            g.wanderX = cx + Math.sin(an) * rr;
            g.wanderZ = cz - Math.cos(an) * rr;
        }
        g.slotDist = 38 + g.rnd.nextDouble() * 30;            // Halbmesser des Kreises
        g.slotSide = g.rnd.nextBoolean() ? 1 : -1;             // Drehsinn
        g.timer = 60 + g.rnd.nextDouble() * 140;
    }

    /**
     * Der Kondor: gleitet von Thermik zu Thermik (16 m/s, sinkt dabei), kreist in ihr mit 40 bis 70 m Halbmesser und
     * steigt mit {@link #condorLift} minus seinem Sinken bis an die Decke. Schlägt nur, wenn er zu tief ist. Nachts sitzt
     * er außerhalb der Szene an seinem Felsen.
     */
    private void stepCondor(Gull g, double h, boolean night) {
        Species sp = g.species;
        if (night) { release(g); g.state = State.AWAY; g.timer = 300 + g.rnd.nextDouble() * 900; return; }
        if (g.state != State.FLY) { g.state = State.FLY; g.approach = 0; pickThermal(g); }
        double ground = groundOf(g.pos[0], g.pos[2]), above = g.pos[1] - ground;
        double[] wind = windAt(Math.min(above, 200));
        double ox = g.vel[0], oz = g.vel[2], vy;
        if (g.approach == 0) {
            // Gleiten zur Thermik
            double dx = g.wanderX - g.pos[0], dz = g.wanderZ - g.pos[2], d = Math.hypot(dx, dz);
            if (d < g.slotDist * 1.2) { g.approach = 1; g.orbit = Math.atan2(g.pos[2] - g.wanderZ, g.pos[0] - g.wanderX); }
            double v = 16;
            double tx = dx / Math.max(1e-6, d) * v, tz = dz / Math.max(1e-6, d) * v;
            g.vel[0] += (tx - g.vel[0]) * Math.min(1, h * 0.6);
            g.vel[2] += (tz - g.vel[2]) * Math.min(1, h * 0.6);
            vy = -sp.sink * 1.4;
        } else {
            // Kreisen: der Mittelpunkt treibt mit dem Wind
            g.wanderX += wind[0] * 0.8 * h;
            g.wanderZ += wind[2] * 0.8 * h;
            double r = g.slotDist, v = sp.vCruise, om = v / r * g.slotSide;
            g.orbit += om * h;
            double px = g.wanderX + Math.cos(g.orbit) * r, pz = g.wanderZ + Math.sin(g.orbit) * r;
            double tvx = -Math.sin(g.orbit) * v * g.slotSide + wind[0] * 0.8, tvz = Math.cos(g.orbit) * v * g.slotSide + wind[2] * 0.8;
            g.vel[0] = tvx + (px - g.pos[0]) * 0.5;
            g.vel[2] = tvz + (pz - g.pos[2]) * 0.5;
            vy = condorLift() - sp.sink;
            if (above > condorCeiling) vy = Math.min(vy, -0.2);
            if (g.timer <= 0 || above > condorCeiling + 20) { g.approach = 0; pickThermal(g); }
        }
        // nie zu tief: dann schlägt er (langsam, schwer) und steigt
        double flap = 0;
        if (above < condorFloor * 0.5) { flap = 0.8; vy = Math.max(vy, sp.climb); }
        if (above < 15) { g.pos[1] = ground + 15; vy = Math.max(vy, 0); }
        g.vel[1] += (vy - g.vel[1]) * Math.min(1, h * 1.5);
        g.pos[0] += g.vel[0] * h;
        g.pos[1] += g.vel[1] * h;
        g.pos[2] += g.vel[2] * h;
        g.airspeed = Math.hypot(g.vel[0] - wind[0], g.vel[2] - wind[2]);
        g.flap += (flap - g.flap) * Math.min(1, h * 2);
        if (g.flap > 0.05) g.wingPhase = (g.wingPhase + 2 * Math.PI * sp.flapHz * h) % (2 * Math.PI);
        if (Math.hypot(g.vel[0], g.vel[2]) > 0.5) g.facing = Math.toDegrees(Math.atan2(g.vel[0], -g.vel[2]));
        double lat = ((g.vel[0] - ox) * -g.vel[2] + (g.vel[2] - oz) * g.vel[0]) / Math.max(1e-6, Math.hypot(g.vel[0], g.vel[2])) / h;
        double bank = Math.atan(Math.max(-8, Math.min(8, lat)) / 9.81);
        g.bank += (bank - g.bank) * Math.min(1, h * 1.5);
        g.fold = 0;
        if (g.rnd.nextDouble() < h / 600) fire(g);   // Kondore sind fast stumm; selten ein Zischen
    }

    // ------------------------------------------------------------------ Anzeige

    /** Zahl der Möwen je Zustand. */
    public int[] census() {
        int[] c = new int[State.values().length];
        for (Gull g : gulls) c[g.state.ordinal()]++;
        return c;
    }

    /** Zahl der Vögel einer Art, die da sind (nicht weg). */
    public int present(Species sp) {
        int n = 0;
        for (Gull g : gulls) if (g.species == sp && g.state != State.AWAY) n++;
        return n;
    }

    /** Zahl der Vögel einer Art im Zustand st. */
    public int count(Species sp, State st) {
        int n = 0;
        for (Gull g : gulls) if (g.species == sp && g.state == st) n++;
        return n;
    }

    /** „16 Möwen: 9 fliegen, 3 stehen im Aufwind, 2 sitzen, 2 weg · 41 Rufe“, mit den übrigen Arten dahinter. */
    public String describe() {
        StringBuilder b = new StringBuilder(describeGulls());
        for (Species sp : Species.values()) {
            if (sp == Species.MOEWE) continue;
            int n = 0;
            for (Gull g : gulls) if (g.species == sp) n++;
            if (n == 0) continue;
            int sw = count(sp, State.SWIM) + count(sp, State.DIVE), pe = count(sp, State.PERCH), wa = count(sp, State.WALK), aw = count(sp, State.AWAY);
            b.append(String.format(Locale.GERMAN, " · %d %s", n, plural(sp)));
            if (sw > 0) b.append(", ").append(sw).append(" auf dem Wasser");
            if (pe > 0) b.append(", ").append(pe).append(" sitzen");
            if (wa > 0) b.append(", ").append(wa).append(" am Boden");
            if (aw > 0) b.append(", ").append(aw).append(" weg");
        }
        return b.toString();
    }

    static String plural(Species sp) {
        return switch (sp) { case MOEWE -> "Möwen"; case KRAEHE -> "Krähen"; case SCHWALBE -> "Schwalben"; case ENTE -> "Enten"; case SCHWAN -> "Schwäne"; case KORMORAN -> "Kormorane"; case KONDOR -> "Kondore"; };
    }

    private String describeGulls() {
        int[] c = new int[State.values().length];
        int gullsN = 0;
        for (Gull g : gulls) if (g.species == Species.MOEWE) { c[g.state.ordinal()]++; gullsN++; }
        int fly = c[State.FLY.ordinal()] + c[State.LAND.ordinal()] + c[State.TAKEOFF.ordinal()] + c[State.LEAVE.ordinal()];
        return String.format(Locale.GERMAN, "%d Möwen: %d fliegen, %d stehen im Aufwind, %d sitzen, %d schwimmen, %d weg · %d Rufe", gullsN, fly, c[State.SOAR.ordinal()],
                c[State.PERCH.ordinal()], c[State.SWIM.ordinal()], c[State.AWAY.ordinal()], calls);
    }
}
