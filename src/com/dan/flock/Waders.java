package com.dan.flock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Seit Rungholt Phase 8: die Vögel des Watts nach der Tide (F14 A). Gruppen je Art ({@link WaderSpecies}) fressen bei
 * Ebbe auf dem Watt, jede in ihrem Abstand zur Wasserkante: Alpenstrandläufer und Säbelschnäbler am Saum, der
 * Säbelschnäbler watend, Austernfischer und Brandgänse weiter oben, die Brandgans im Schlick. Sie laufen und stochern,
 * folgen der Kante, und wenn die Flut ihr Stück Watt nimmt und kein anderes in der Nähe frei ist, fliegen sie auf und
 * zum Hochwasserrastplatz (Südfall, Deich), wo sie dicht stehen, bis das Watt wieder frei wird. Die Ringelgans weidet
 * auf der Salzwiese. Ein Störer näher als die Fluchtdistanz scheucht eine Gruppe auf; der Alpenstrandläufer wendet
 * dann als Schwarm, wo jeder Vogel dem vor ihm mit Verzug folgt und die Wendung als Welle durch den Schwarm läuft:
 * dreht er, zeigt der ganze Schwarm auf einmal die weißen Bäuche (der Umschlag von Dunkel zu Hell).
 * <p>
 * Kennt kein Watt: Grund, Wassertiefe, Schlick und Salzwiese fragt sie bei der {@link World} ab; Revier und
 * Rastplätze sind Kreise ({@link Area}). Rechnet in Szenenzeit; Vögel in der Luft in Teilschritten von 0,2 s, am Boden
 * einmal je Aufruf. Reproduzierbar: gleicher {@code seed}, gleiche Welt, gleiche Schritte, gleiche Vögel.
 */
public final class Waders {
    /** Die Welt: Grund (m), Wassertiefe (m, 0 trocken), Schlick 0..1, Salzwiese (Weide der Gänse). */
    public interface World {
        double ground(double x, double z);
        double depth(double x, double z);
        default double mud(double x, double z) { return 0.4; }
        default boolean marsh(double x, double z) { return false; }
        /** Stunden seit dem nächsten Hochwasser (negativ davor); NaN, wenn die Welt keine Tide kennt. */
        default double highWaterHours() { return Double.NaN; }
    }

    /** Ein Kreis: Revier, Rastplatz, Weide. */
    public record Area(double x, double z, double r) { }

    /** Ein Ruf: Ort, Stärke, Art. */
    @FunctionalInterface public interface Call { void call(double x, double y, double z, double strength, WaderSpecies s); }

    public enum Mode {
        FRESSEN("fressen"), RASTEN("rasten"), FLIEGEN("fliegen"), SCHWARM("wenden"), FORT("fort");
        public final String label;
        Mode(String l) { label = l; }
    }

    /** Ein Vogel. Am Boden: Ort, Blick (Azimut, rad), Stochern; in der Luft: Ort, Geschwindigkeit, Querneigung, Flügel. */
    public static final class Bird {
        public final Group group;
        public double x, y, z, vx, vy, vz, heading, bank, wingPhase, flap, fold = 1, legs = 1, probe;
        public boolean air;
        /** Platz in der Gruppe am Boden (m) und im Flug (vorn, oben, rechts in Einheiten des Schwarms). */
        double ox, oz, fx, fy, fr, lag, tx, tz, pause, probePhase, gy;
        Bird(Group g) { group = g; }
    }

    /** Eine Gruppe einer Art. */
    public static final class Group {
        public final int id;
        public final WaderSpecies species;
        public final List<Bird> birds = new ArrayList<>();
        public Mode mode = Mode.FRESSEN;
        /** Mitte am Boden (m), Ziel des Flugs, Führungspunkt in der Luft mit Geschwindigkeit. */
        public double cx, cz, dx, dz;
        double lx, ly, lz, lvx, lvy, lvz, timer, restUntil, calm, wanderPhase, wanderR, wcx, wcz, airTime;
        boolean toRoost;
        Group(int id, WaderSpecies s) { this.id = id; species = s; }
        public int size() { return birds.size(); }
        /** Halbmesser der Gruppe am Boden (m). */
        public double radius() { return species.spacing() * Math.sqrt(Math.max(1, birds.size())) * 0.62; }
    }

    public enum Quality {
        LOW(0.35), MEDIUM(1.0), HIGH(1.8), ULTRA(3.0);
        public final double factor;
        Quality(double f) { factor = f; }
    }

    // ------------------------------------------------------------------ Einstellungen
    public long seed = 1;
    public volatile Quality quality = Quality.MEDIUM;
    public World world;
    /** Revier (dort fressen sie), Rastplätze bei Hochwasser, Weiden der Gänse. */
    public final List<Area> revier = new ArrayList<>(), roosts = new ArrayList<>(), pastures = new ArrayList<>();
    /** Störer: x, z, Stärke (die App setzt sie vor jedem Schritt). */
    public final List<double[]> disturbers = new ArrayList<>();
    /** Monat (1..12), Sonnenhöhe (Grad), Wind (m/s, aus Grad). */
    public volatile int month = 7;
    public volatile double sunAltDeg = 30, windSpeed = 5, windFromDeg = 250;
    /** Zahl je Art bei Anwesenheit 1 und Stufe MEDIUM (nach {@link WaderSpecies#ALL}). */
    public final int[] base = {900, 150, 80, 60, 60, 120, 300};
    /** Arten an oder aus (nach {@link WaderSpecies#ALL}). */
    public final boolean[] enabled = {true, true, true, true, true, true, true};
    public Call onCall;
    /** Teilschritt in der Luft (s). */
    public static final double AIR_STEP = 0.2;

    private final List<Group> groups = new ArrayList<>();
    private SplittableRandom rng;
    private double time;
    private String built = "";
    /** Zähler (Prüfung, HUD). */
    public int takeoffs, landings, alarms, murmurations, calls;

    public Waders() { rng = new SplittableRandom(seed); }

    public List<Group> groups() { return Collections.unmodifiableList(groups); }
    public double time() { return time; }

    /** Zahl der Vögel einer Art nach Monat und Stufe. */
    public int target(int i) {
        WaderSpecies s = WaderSpecies.ALL.get(i);
        return enabled[i] ? (int) Math.round(base[i] * s.presence(month) * quality.factor) : 0;
    }

    private String key() {
        StringBuilder b = new StringBuilder().append(seed).append('/').append(quality).append('/').append(month).append('/');
        for (int i = 0; i < base.length; i++) b.append(target(i)).append(',');
        return b.toString();
    }

    /** Muss neu angelegt werden (Monat, Stufe, Zahlen geändert)? */
    public boolean stale() { return !built.equals(key()); }

    /** Gruppen anlegen: je Art so viele, dass ihre Größe zwischen groupMin und groupMax liegt. */
    public void populate() {
        groups.clear();
        rng = new SplittableRandom(seed);
        skeins.clear();
        zugRng = new SplittableRandom(seed * 31 + 7);
        time = 0;
        takeoffs = landings = alarms = murmurations = calls = 0;
        int id = 0;
        for (int i = 0; i < WaderSpecies.ALL.size(); i++) {
            WaderSpecies s = WaderSpecies.ALL.get(i);
            int n = target(i);
            while (n >= Math.max(3, s.groupMin() / 2)) {
                int g = Math.min(n, (int) Math.round(s.groupMin() + (s.groupMax() - s.groupMin()) * Math.pow(rng.nextDouble(), 1.5)));
                if (n - g < s.groupMin() / 2) g = n;
                Group gr = new Group(id++, s);
                for (int k = 0; k < g; k++) gr.birds.add(new Bird(gr));
                layout(gr);
                Area a = revier.isEmpty() ? new Area(0, 0, 1000) : revier.get(rng.nextInt(revier.size()));
                double ang = rng.nextDouble() * 2 * Math.PI, r = a.r() * Math.sqrt(rng.nextDouble());
                gr.cx = a.x() + Math.cos(ang) * r;
                gr.cz = a.z() + Math.sin(ang) * r;
                gr.wanderPhase = rng.nextDouble() * 6.28;
                groups.add(gr);
                n -= g;
            }
        }
        built = key();
    }

    /** Plätze in der Gruppe: am Boden ein verrücktes Gitter, in der Luft eine Wolke (Strandläufer) oder eine Kette (Gänse, Austernfischer). */
    private void layout(Group g) {
        WaderSpecies s = g.species;
        int n = g.birds.size(), side = (int) Math.ceil(Math.sqrt(n));
        for (int k = 0; k < n; k++) {
            Bird b = g.birds.get(k);
            double gx = (k % side) - side / 2.0 + (rng.nextDouble() - 0.5) * 0.9, gz = (k / side) - side / 2.0 + (rng.nextDouble() - 0.5) * 0.9;
            b.ox = gx * s.spacing() * 0.95;
            b.oz = gz * s.spacing() * 0.95;
            if (s.murmur()) {   // Wolke: flach und breit
                double u = rng.nextDouble(), v = rng.nextDouble(), w = rng.nextDouble();
                double rad = Math.cbrt(u) * Math.sqrt(n) * 0.55;
                double th = 2 * Math.PI * v, ph = Math.acos(2 * w - 1);
                b.fx = rad * Math.sin(ph) * Math.cos(th) * 1.4;
                b.fr = rad * Math.sin(ph) * Math.sin(th) * 1.4;
                b.fy = rad * Math.cos(ph) * 0.45;
            } else if (s.span() > 1.0 || s == WaderSpecies.AUSTERNFISCHER) {   // Kette oder Keil
                int row = k / 2, sd = k % 2 == 0 ? 1 : -1;
                b.fx = -row * s.span() * 0.9;
                b.fr = sd * row * s.span() * (s.span() > 1.0 ? 0.9 : 0.35) + (rng.nextDouble() - 0.5) * 0.4;
                b.fy = (rng.nextDouble() - 0.5) * 0.6;
            } else {   // lockerer Trupp
                b.fx = (rng.nextDouble() - 0.5) * Math.sqrt(n) * s.span() * 1.6;
                b.fr = (rng.nextDouble() - 0.5) * Math.sqrt(n) * s.span() * 1.6;
                b.fy = (rng.nextDouble() - 0.5) * 2;
            }
            // Verzug: wer weiter hinten fliegt, folgt später (die Wendung läuft als Welle durch den Schwarm)
            b.lag = 0.25 + 0.05 * Math.max(0, -b.fx) / Math.max(0.1, s.span()) * (s.murmur() ? 0.6 : 0.2) + 0.15 * rng.nextDouble();
            b.probePhase = rng.nextDouble();
            b.heading = rng.nextDouble() * 2 * Math.PI;
        }
    }

    /**
     * Sofort in einen Zustand wie nach einer Weile (Start, Sprünge der Uhr): jede Gruppe frisst, wenn sie ein Stück Watt
     * findet, sonst rastet sie.
     */
    public void settle() {
        if (world == null) return;
        if (stale() || groups.isEmpty()) populate();
        for (Group g : groups) {
            g.calm = 0;
            double[] f = feedSpot(g, true);
            if (f != null && !restsNow(g)) { g.cx = f[0]; g.cz = f[1]; g.mode = Mode.FRESSEN; }
            else { double[] r = roostSpot(g); g.cx = r[0]; g.cz = r[1]; g.mode = Mode.RASTEN; }
            g.timer = 5 + 20 * rng.nextDouble();
            for (Bird b : g.birds) land(g, b, true);
        }
    }

    /** Ruhen jetzt (Möwen und Gänse nachts; alle außer der Ringelgans rund um Hochwasser)? */
    private boolean restsNow(Group g) {
        boolean night = sunAltDeg < -6;
        if (night && (g.species == WaderSpecies.SILBERMOEWE || g.species == WaderSpecies.LACHMOEWE || g.species == WaderSpecies.RINGELGANS)) return true;
        return highTide(g);
    }

    /**
     * Rund um Hochwasser ist das Watt zu: die Gruppe fliegt etwa 1,3 bis 1,8 h vorher zum Rastplatz und kommt 1,5 bis
     * 2,5 h danach zurück [G]; Möwen eine halbe Stunde kürzer. Je Gruppe etwas anders, damit nicht alle zugleich fliegen.
     */
    boolean highTide(Group g) {
        if (g.species.graze()) return false;
        double h = world.highWaterHours();
        if (Double.isNaN(h)) return false;
        double j = (g.id * 0.6180339887) % 1.0, gull = g.species == WaderSpecies.SILBERMOEWE || g.species == WaderSpecies.LACHMOEWE ? 0.5 : 0;
        return h > -(1.3 + 0.5 * j - gull) && h < 1.5 + 1.0 * j - gull;
    }

    // ------------------------------------------------------------------ Schritt

    /** Szenenzeit dt (s) weiter. */
    public void step(double dt) {
        if (world == null || dt <= 0) return;
        if (stale() || groups.isEmpty()) { populate(); settle(); }
        time += dt;
        stepMigration(dt);
        for (Group g : groups) {
            if (g.mode != Mode.SCHWARM) g.timer -= dt;   // im Schwarm zählt air() die Zeit
            g.calm = Math.max(0, g.calm - dt);
            decide(g, dt);
            if (g.mode == Mode.FLIEGEN || g.mode == Mode.SCHWARM) {
                int n = (int) Math.ceil(dt / AIR_STEP);
                double h = dt / n;
                for (int k = 0; k < n; k++) air(g, h);
            } else ground(g, dt);
        }
    }

    /** Entscheidungen der Gruppe: Störer, Flut, Kante, Rast, Weide. */
    private void decide(Group g, double dt) {
        WaderSpecies s = g.species;
        boolean onGround = g.mode == Mode.FRESSEN || g.mode == Mode.RASTEN;
        if (onGround) {
            double[] d = nearestDisturber(g.cx, g.cz);
            if (d[0] < s.flight() * Math.max(0.3, d[3]) + g.radius()) {   // aufgescheucht
                alarms++;
                double ax = g.cx - d[1], az = g.cz - d[2], al = Math.max(1e-6, Math.hypot(ax, az));
                double[] spot = null;
                for (int t = 0; t < 12 && spot == null; t++) {
                    double dist = 250 + 350 * rng.nextDouble(), ang = (rng.nextDouble() - 0.5) * 1.6;
                    double ux = ax / al * Math.cos(ang) - az / al * Math.sin(ang), uz = ax / al * Math.sin(ang) + az / al * Math.cos(ang);
                    spot = feedSpotNear(g, g.cx + ux * dist, g.cz + uz * dist, 120);
                }
                if (spot == null) spot = roostSpot(g);
                g.calm = 120;
                if (s.murmur()) wheel(g, spot); else takeOff(g, spot, false);
                return;
            }
        }
        if (g.timer > 0) return;
        g.timer = 10 + 10 * rng.nextDouble();
        switch (g.mode) {
            case FRESSEN -> {
                if (restsNow(g)) { takeOff(g, roostSpot(g), true); return; }
                if (s.graze()) return;
                // Wie viel Wasser steht in der Gruppe? Kommt die Flut, weichen sie aus; ist nichts mehr frei, fliegen alle auf
                double wet = wetShare(g), edge = edgeDistance(g.cx, g.cz, s.edgeMax() * 2 + 60);
                // die Flut kommt: wer am Saum stehen will, geht mit; wer Abstand will, weicht schon vorher aus
                boolean drowning = wet > 0.12, tooFar = edge > s.edgeMax() * 1.6 + 20, tooNear = s.edgeMin() > 5 && edge < s.edgeMin() - 5;
                if (drowning || tooFar || tooNear) {
                    double[] spot = feedSpotNear(g, g.cx, g.cz, drowning ? 160 : 400);
                    if (spot != null && Math.hypot(spot[0] - g.cx, spot[1] - g.cz) < 60) { g.cx = spot[0]; g.cz = spot[1]; }   // laufen
                    else if (spot != null) takeOff(g, spot, false);
                    else if (drowning) takeOff(g, roostSpot(g), true);
                }
            }
            case RASTEN -> {
                if (!s.graze() && wetShare(g) > 0.1) {   // die Flut erreicht den Rastplatz: ein Stück höher
                    double[] r = roostSpot(g);
                    if (Math.hypot(r[0] - g.cx, r[1] - g.cz) < 60) { g.cx = r[0]; g.cz = r[1]; } else takeOff(g, r, true);
                    return;
                }
                if (restsNow(g) || g.calm > 0) return;
                double[] spot = s.graze() ? pastureSpot(g) : feedSpot(g, false);
                if (spot != null && time > g.restUntil && rng.nextDouble() < 0.5) takeOff(g, spot, false);
            }
            case FLIEGEN, SCHWARM, FORT -> { }
        }
    }

    /** Auffliegen zum Ziel (Rast oder Fressen); die Gruppe fliegt, bis der Führungspunkt dort ist. */
    private void takeOff(Group g, double[] to, boolean roost) {
        g.mode = Mode.FLIEGEN;
        g.toRoost = roost;
        g.dx = to[0];
        g.dz = to[1];
        g.lx = g.cx; g.lz = g.cz; g.ly = groundAt(g.cx, g.cz) + 1;
        double ux = g.dx - g.cx, uz = g.dz - g.cz, ul = Math.max(1e-6, Math.hypot(ux, uz));
        g.lvx = ux / ul * g.species.speed() * 0.5; g.lvz = uz / ul * g.species.speed() * 0.5; g.lvy = 2;
        g.airTime = 0;
        takeoffs++;
        for (Bird b : g.birds) {
            b.air = true;
            b.vy = 1.5 + rng.nextDouble();
            b.vx = g.lvx * 0.5; b.vz = g.lvz * 0.5;
        }
        call(g, 0.8);
    }

    /** Der Strandläufer-Schwarm wendet über dem Watt, dann fliegt er zum Ziel. */
    private void wheel(Group g, double[] to) {
        takeOff(g, to, false);
        g.mode = Mode.SCHWARM;
        g.timer = 50 + 90 * rng.nextDouble();
        g.wcx = g.cx; g.wcz = g.cz;
        g.wanderR = 70 + 90 * rng.nextDouble();
        murmurations++;
    }

    // ------------------------------------------------------------------ in der Luft

    private void air(Group g, double h) {
        WaderSpecies s = g.species;
        g.airTime += h;
        // Führungspunkt
        double ty, ax, az;
        if (g.mode == Mode.SCHWARM) {
            g.timer -= h;
            g.wanderPhase += h * (0.35 + 0.15 * Math.sin(g.airTime * 0.13));
            double r = g.wanderR * (0.8 + 0.3 * Math.sin(g.airTime * 0.21 + g.id));
            double px = g.wcx + Math.cos(g.wanderPhase) * r + Math.sin(g.wanderPhase * 2.3) * r * 0.35;
            double pz = g.wcz + Math.sin(g.wanderPhase * 1.3) * r * 0.8;
            ty = groundAt(g.wcx, g.wcz) + 14 + 10 * Math.sin(g.airTime * 0.31);
            ax = px - g.lx; az = pz - g.lz;
            if (g.timer <= 0) { g.mode = Mode.FLIEGEN; }
        } else {
            ax = g.dx - g.lx; az = g.dz - g.lz;
            double dist = Math.hypot(ax, az);
            double cruise = s.murmur() ? 8 : s.span() > 1.0 ? 25 : 15;
            ty = groundAt(g.lx, g.lz) + Math.min(cruise, Math.max(2, dist * 0.15));
            if (dist < 12 && g.airTime > 6) { landAll(g); return; }
        }
        double al = Math.max(1e-6, Math.hypot(ax, az));
        double v = s.speed() * (g.mode == Mode.SCHWARM ? 1.15 : 1.0);
        double wx = ax / al * v, wz = az / al * v;
        double k = Math.min(1, h * (g.mode == Mode.SCHWARM ? 0.9 : 0.6));
        g.lvx += (wx - g.lvx) * k;
        g.lvz += (wz - g.lvz) * k;
        g.lvy += ((ty - g.ly) * 0.5 - g.lvy) * Math.min(1, h * 1.5);
        g.lx += g.lvx * h; g.ly += g.lvy * h; g.lz += g.lvz * h;
        // Vögel: jeder folgt seinem Platz hinter dem Führungspunkt, mit Verzug
        double sp = Math.max(1e-6, Math.hypot(g.lvx, g.lvz)), fx = g.lvx / sp, fz = g.lvz / sp, rx = -fz, rz = fx;
        double floor = groundAt(g.lx, g.lz) + 0.6;   // ein Grund für alle (die Vögel fliegen hoch genug)
        for (Bird b : g.birds) {
            double txp = g.lx + fx * b.fx + rx * b.fr, tzp = g.lz + fz * b.fx + rz * b.fr, typ = g.ly + b.fy;
            double kx = (txp - b.x) / b.lag - b.vx, ky = (typ - b.y) / b.lag - b.vy, kz = (tzp - b.z) / b.lag - b.vz;
            double kk = Math.min(1, h * 4 / Math.max(0.2, b.lag * 4));
            double ovx = b.vx, ovz = b.vz;
            b.vx += kx * kk; b.vy += ky * kk; b.vz += kz * kk;
            double vv = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz), vmax = s.speed() * 1.6;
            if (vv > vmax) { b.vx *= vmax / vv; b.vy *= vmax / vv; b.vz *= vmax / vv; }
            b.x += b.vx * h; b.y += b.vy * h; b.z += b.vz * h;
            if (b.y < floor) { b.y = floor; b.vy = Math.max(0, b.vy); }
            // Querneigung aus der Kurve (Zentripetal / g), Flügel schlagen, beim Steigen mehr
            double hv = Math.max(0.5, Math.hypot(b.vx, b.vz));
            double turn = ((b.vx - ovx) * -b.vz + (b.vz - ovz) * b.vx) / (hv * h);   // seitliche Beschleunigung
            double bankT = Math.max(-1.2, Math.min(1.2, Math.atan2(turn, 9.81)));
            b.bank += (bankT - b.bank) * Math.min(1, h * 6);
            b.heading = Math.atan2(b.vx, -b.vz);
            b.flap = Math.min(1, 0.55 + Math.max(0, b.vy) * 0.25 + Math.abs(b.bank) * 0.2);
            b.wingPhase += 2 * Math.PI * s.flapHz() * h * (0.7 + 0.5 * b.flap);
            b.fold = Math.max(0, b.fold - h * 3);
            b.legs = Math.max(0, b.legs - h * 2);
            b.probe = 0;
        }
    }

    /** Am Ziel: jeder Vogel nimmt seinen Platz um die neue Mitte und landet. */
    private void landAll(Group g) {
        g.cx = g.dx;
        g.cz = g.dz;
        g.mode = g.toRoost ? Mode.RASTEN : Mode.FRESSEN;
        g.timer = 20;
        g.restUntil = g.toRoost ? time + 900 + 600 * rng.nextDouble() : time;
        landings++;
        for (Bird b : g.birds) land(g, b, false);
        call(g, 0.6);
    }

    private void land(Group g, Bird b, boolean instant) {
        double dense = g.mode == Mode.RASTEN ? 0.55 : 1.0;
        b.tx = g.cx + b.ox * dense;
        b.tz = g.cz + b.oz * dense;
        if (instant) {
            b.x = b.tx; b.z = b.tz;
            b.heading = Math.toRadians(windFromDeg) + (rng.nextDouble() - 0.5) * 0.8;   // in den Wind
            b.fold = 1; b.legs = 1;
            b.y = groundAt(b.x, b.z);
            b.gy = b.y;
            b.air = false;
            b.vx = b.vy = b.vz = 0;
            b.bank = 0;
        } else b.air = true;   // gleitet noch zum Platz (am Boden fertig)
        b.pause = rng.nextDouble() * 2;
    }

    // ------------------------------------------------------------------ am Boden

    private void ground(Group g, double dt) {
        WaderSpecies s = g.species;
        boolean feed = g.mode == Mode.FRESSEN;
        double dense = g.mode == Mode.RASTEN ? 0.55 : 1.0;
        for (Bird b : g.birds) {
            if (b.air) {   // Landeanflug: zum Platz gleiten, Beine raus
                double ex = b.tx - b.x, ez = b.tz - b.z, el = Math.hypot(ex, ez), gy = groundAt(b.tx, b.tz);
                double step = Math.min(el, s.speed() * 0.6 * dt);
                if (el > 1e-6) { b.x += ex / el * step; b.z += ez / el * step; b.heading = Math.atan2(ex, -ez); }
                b.y += (gy - b.y) * Math.min(1, dt * 1.5);
                b.legs = Math.min(1, b.legs + dt * 1.5);
                b.fold = Math.min(1, b.fold + dt * 0.8);
                b.flap = 0.8;
                b.wingPhase += 2 * Math.PI * s.flapHz() * dt;
                b.bank *= Math.max(0, 1 - dt * 3);
                if (el < 0.3 && Math.abs(b.y - gy) < 0.3) { b.air = false; b.y = gy; b.gy = gy; b.fold = 1; b.legs = 1; b.flap = 0; }
                continue;
            }
            // laufen und stochern um den eigenen Platz; die Mitte wandert mit der Kante
            b.pause -= dt;
            double hx = g.cx + b.ox * dense, hz = g.cz + b.oz * dense;
            if (b.pause <= 0) {
                double roam = feed ? s.spacing() * 1.4 : s.spacing() * 0.2;
                b.tx = hx + (rng.nextDouble() - 0.5) * 2 * roam;
                b.tz = hz + (rng.nextDouble() - 0.5) * 2 * roam;
                b.pause = feed ? 1.5 + 5 * rng.nextDouble() / Math.max(0.5, s.walk() * 3) : 20 + 60 * rng.nextDouble();
            }
            double ex = b.tx - b.x, ez = b.tz - b.z, el = Math.hypot(ex, ez);
            double far = Math.hypot(hx - b.x, hz - b.z) > s.spacing() * 6 ? 3 : 1;   // weit weg: rennen
            double step = Math.min(el, s.walk() * far * dt);
            boolean walking = step > 1e-4 && el > 0.02;
            if (walking) {
                b.x += ex / el * step;
                b.z += ez / el * step;
                b.heading += angleTo(b.heading, Math.atan2(ex, -ez)) * Math.min(1, dt * 6);
                if (Math.abs(b.y - b.gy) > 0 || step > 0.05) { b.gy = groundAt(b.x, b.z); }
            } else if (!feed) {
                b.heading += angleTo(b.heading, Math.toRadians(windFromDeg)) * Math.min(1, dt * 0.5);   // rastend in den Wind
            }
            b.y = b.gy;
            b.fold = 1; b.legs = 1; b.flap = 0; b.bank = 0;
            // Stochern: in Stößen nach der Art, nicht beim Laufen; rastend Kopf eingezogen
            b.probePhase += dt * s.probeHz() * (walking ? 0.3 : 1);
            double ph = b.probePhase % 1.0;
            b.probe = feed ? (s.graze() ? 0.9 + 0.2 * Math.sin(ph * 6.28) : (ph < 0.45 ? Math.sin(ph / 0.45 * Math.PI) * 1.0 : 0)) : -0.25;
        }
    }

    // ------------------------------------------------------------------ Orte

    private double groundAt(double x, double z) { return world.ground(x, z); }

    /** Anteil der Vögel der Gruppe (Stichprobe), die tiefer stehen, als die Art waten kann. */
    private double wetShare(Group g) {
        int n = 0, wet = 0;
        for (int k = 0; k < g.birds.size(); k += Math.max(1, g.birds.size() / 16)) {
            Bird b = g.birds.get(k);
            n++;
            if (world.depth(b.x, b.z) > g.species.wade() + 0.01) wet++;
        }
        return n == 0 ? 0 : wet / (double) n;
    }

    /** Abstand zur Wasserkante (zu Wasser über 3 cm), bis max; sonst unendlich. 0 im Wasser. */
    public double edgeDistance(double x, double z, double max) {
        if (world.depth(x, z) > 0.03) return 0;
        for (double r = 4; r <= max; r *= 1.35)
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                if (world.depth(x + Math.cos(a) * r, z + Math.sin(a) * r) > 0.03) return r;
            }
        return Double.POSITIVE_INFINITY;
    }

    /** Eignung eines Orts zum Fressen für die Art (größer besser), NaN ungeeignet. */
    double feedScore(WaderSpecies s, double x, double z) {
        if (world.marsh(x, z)) return Double.NaN;
        double d = world.depth(x, z);
        if (d > s.wade()) return Double.NaN;
        double e = edgeDistance(x, z, s.edgeMax() + 80);
        if (Double.isInfinite(e)) return Double.NaN;
        if (e < s.edgeMin() - 5 || e > s.edgeMax() + 10) return Double.NaN;
        double mid = (s.edgeMin() + s.edgeMax()) * 0.5;
        return 1 - Math.abs(e - mid) / Math.max(10, s.edgeMax() - s.edgeMin() + 10) + 0.5 * s.mud() * (world.mud(x, z) - 0.4);
    }

    /** Ein Platz zum Fressen im Revier (oder nah bei der jetzigen Mitte, wenn near). */
    private double[] feedSpot(Group g, boolean near) {
        if (g.species.graze()) return pastureSpot(g);
        double[] best = feedSpotNear(g, g.cx, g.cz, near ? 500 : 900);
        if (best != null) return best;
        for (int t = 0; t < 3 && best == null; t++) {
            Area a = revier.isEmpty() ? new Area(g.cx, g.cz, 1500) : revier.get(rng.nextInt(revier.size()));
            best = feedSpotNear(g, a.x(), a.z(), a.r());
        }
        return best;
    }

    /** Bester von 24 Orten in r um (x, z), im Revier und nicht zu nah an anderen Gruppen derselben Art. */
    private double[] feedSpotNear(Group g, double x, double z, double r) {
        if (g.species.graze()) return pastureSpot(g);
        double best = -1e9;
        double[] out = null;
        for (int t = 0; t < 24; t++) {
            double ang = rng.nextDouble() * 2 * Math.PI, rr = r * Math.sqrt(rng.nextDouble());
            double px = x + Math.cos(ang) * rr, pz = z + Math.sin(ang) * rr;
            if (!inRevier(px, pz)) continue;
            double sc = feedScore(g.species, px, pz);
            if (Double.isNaN(sc)) continue;
            for (Group o : groups) if (o != g && o.species == g.species && Math.hypot(o.cx - px, o.cz - pz) < 120 + o.radius()) sc -= 1;
            double[] d = nearestDisturber(px, pz);
            if (d[0] < g.species.flight() * 1.5) continue;
            sc -= Math.hypot(px - g.cx, pz - g.cz) / 3000;
            if (sc > best) { best = sc; out = new double[]{px, pz}; }
        }
        return out;
    }

    private boolean inRevier(double x, double z) {
        if (revier.isEmpty()) return true;
        for (Area a : revier) if (Math.hypot(x - a.x(), z - a.z()) <= a.r()) return true;
        return false;
    }

    /** Hochwasserrastplatz: der nächste, etwas verteilt. */
    private double[] roostSpot(Group g) {
        if (roosts.isEmpty()) return new double[]{g.cx, g.cz};
        Area best = roosts.get(0);
        double bd = Double.MAX_VALUE;
        for (Area a : roosts) { double d = Math.hypot(a.x() - g.cx, a.z() - g.cz) * (0.7 + 0.6 * rng.nextDouble()); if (d < bd) { bd = d; best = a; } }
        // ein trockener Ort, die Gruppe dicht beisammen ganz auf dem Trockenen (bei Springflut ist das Vorland knapp)
        double rr = g.radius() * 0.55 + 2;
        double[] out = null;
        for (int t = 0; t < 16; t++) {
            double ang = rng.nextDouble() * 2 * Math.PI, r = best.r() * Math.sqrt(rng.nextDouble()) * (t < 12 ? 0.8 : 0.3);
            double px = best.x() + Math.cos(ang) * r, pz = best.z() + Math.sin(ang) * r;
            if (out == null) out = new double[]{px, pz};
            boolean dry = world.depth(px, pz) <= 0;
            for (int k = 0; k < 6 && dry; k++) dry = world.depth(px + Math.cos(k * 1.047) * rr, pz + Math.sin(k * 1.047) * rr) <= 0;
            if (dry) return new double[]{px, pz};
        }
        return out;
    }

    /** Weide der Gänse (sonst ein Rastplatz). */
    private double[] pastureSpot(Group g) {
        List<Area> l = pastures.isEmpty() ? roosts : pastures;
        if (l.isEmpty()) return null;
        Area a = l.get(rng.nextInt(l.size()));
        double ang = rng.nextDouble() * 2 * Math.PI, r = a.r() * Math.sqrt(rng.nextDouble());
        return new double[]{a.x() + Math.cos(ang) * r, a.z() + Math.sin(ang) * r};
    }

    /** {Abstand, x, z, Stärke} des nächsten Störers. */
    public double[] nearestDisturber(double x, double z) {
        double bd = Double.POSITIVE_INFINITY, bx = 0, bz = 0, bs = 1;
        for (double[] d : disturbers) { double dd = Math.hypot(d[0] - x, d[1] - z); if (dd < bd) { bd = dd; bx = d[0]; bz = d[1]; bs = d.length > 2 ? d[2] : 1; } }
        return new double[]{bd, bx, bz, bs};
    }

    // ------------------------------------------------------------------ Vogelzug (seit Rungholt Phase 9)

    /** Ziehende Keile zeigen (an), mittlerer Abstand zwischen zwei Keilen im Hauptmonat (s Szenenzeit) [G]. */
    public volatile boolean migration = true;
    public volatile double migrationInterval = 1800;
    /** Keile, die bisher gezogen sind. */
    public int skeinsSeen;
    private final List<Group> skeins = new ArrayList<>();
    private SplittableRandom zugRng = new SplittableRandom(4711);
    private int skeinId = 100_000;

    /** Die Keile, die gerade über die Szene ziehen (nicht in {@link #groups()}, nicht in der Zählung). */
    public List<Group> skeins() { return Collections.unmodifiableList(skeins); }

    /**
     * Anteil des Zugs im Monat (0..1) [G]: Ringelgänse kommen im Oktober aus Sibirien und ziehen im April und Mai
     * zurück; im März und September die ersten, im November die letzten.
     */
    public static double migrationShare(int month) {
        return switch (month) { case 3 -> 0.4; case 4 -> 1; case 5 -> 0.7; case 9 -> 0.5; case 10 -> 1; case 11 -> 0.5; default -> 0; };
    }

    /** Richtung des Zugs (Azimut, Grad): im Frühjahr nach Nordost, im Herbst nach Südwest. */
    public static double migrationHeading(int month) { return month <= 6 ? 50 : 230; }

    private void stepMigration(double dt) {
        double share = migration ? migrationShare(month) : 0;
        if (share > 0 && zugRng.nextDouble() < dt * share / Math.max(60, migrationInterval)) spawnSkein();
        for (java.util.Iterator<Group> it = skeins.iterator(); it.hasNext(); ) {
            Group g = it.next();
            double v = g.species.speed() * 1.1;
            g.lx += g.dx * v * dt; g.lz += g.dz * v * dt;
            g.airTime += dt;
            g.cx = g.lx; g.cz = g.lz;
            double px = -g.dz, pz = g.dx, hd = Math.atan2(g.dx, -g.dz);
            for (int i = 0; i < g.birds.size(); i++) {
                Bird b = g.birds.get(i);
                int rank = (i + 1) / 2, side = i == 0 ? 0 : (i % 2 == 0 ? 1 : -1);
                double wob = Math.sin(g.airTime * 0.4 + i * 0.7) * 0.25 * rank * 0.15;
                b.x = g.lx - g.dx * 2.3 * rank + px * side * (1.7 * rank + wob) + b.fx;
                b.z = g.lz - g.dz * 2.3 * rank + pz * side * (1.7 * rank + wob) + b.fy;
                b.y = g.ly + 0.12 * rank + 0.4 * Math.sin(g.airTime * 0.3 + i);
                b.vx = g.dx * v; b.vz = g.dz * v; b.vy = 0;
                b.heading = hd;
                b.wingPhase += dt * 2 * Math.PI * g.species.flapHz() * (0.95 + 0.1 * Math.sin(i));
                b.flap = 1; b.fold = 0; b.legs = 0; b.air = true; b.bank = 0;
            }
            if (g.airTime > 9000 / v) it.remove();
        }
    }

    private void spawnSkein() {
        WaderSpecies s = WaderSpecies.RINGELGANS;
        Group g = new Group(skeinId++, s);
        g.mode = Mode.FORT;
        double hd = Math.toRadians(migrationHeading(month) + (zugRng.nextDouble() - 0.5) * 30);
        g.dx = Math.sin(hd); g.dz = -Math.cos(hd);
        Area c = revier.isEmpty() ? new Area(0, 0, 1000) : revier.get(0);
        double lateral = (zugRng.nextDouble() - 0.5) * 3000;
        g.lx = c.x() - g.dx * 4500 - g.dz * lateral;
        g.lz = c.z() - g.dz * 4500 + g.dx * lateral;
        g.ly = 70 + 110 * zugRng.nextDouble();
        g.airTime = 0;
        int n = 15 + zugRng.nextInt(70);
        for (int i = 0; i < n; i++) {
            Bird b = new Bird(g);
            b.fx = (zugRng.nextDouble() - 0.5) * 0.8; b.fy = (zugRng.nextDouble() - 0.5) * 0.8;
            b.wingPhase = zugRng.nextDouble() * 6.28;
            b.air = true;
            g.birds.add(b);
        }
        skeins.add(g);
        skeinsSeen++;
        calls++;
        if (onCall != null) onCall.call(g.lx, g.ly, g.lz, 0.6, s);
    }

    private void call(Group g, double strength) {
        calls++;
        if (onCall != null) onCall.call(g.cx, groundAt(g.cx, g.cz), g.cz, strength, g.species);
    }

    static double angleTo(double from, double to) {
        double d = (to - from) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    /** Einen Schwarm von Hand aufscheuchen (Bedienfeld): die Gruppe am Boden, die dem Ort am nächsten ist. */
    public Group flush(double x, double z) {
        Group best = null;
        double bd = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++)   // zuerst ein Schwarm, der wenden kann
            for (Group g : groups) if ((g.mode == Mode.FRESSEN || g.mode == Mode.RASTEN) && (pass == 1 || g.species.murmur())) {
                double d = Math.hypot(g.cx - x, g.cz - z);
                if (d < bd) { bd = d; best = g; }
            }
        if (best == null) return null;
        double[] spot = feedSpotNear(best, best.cx, best.cz, 600);
        if (spot == null) spot = roostSpot(best);
        alarms++;
        if (best.species.murmur()) wheel(best, spot); else takeOff(best, spot, false);
        return best;
    }

    // ------------------------------------------------------------------ Ausgabe

    /** Zahl der Vögel insgesamt. */
    public int birds() { int n = 0; for (Group g : groups) n += g.size(); return n; }

    /** Zahl der Vögel je Zustand (Reihenfolge {@link Mode}). */
    public int[] census() {
        int[] c = new int[Mode.values().length];
        for (Group g : groups) c[g.mode.ordinal()] += g.size();
        return c;
    }

    /** Flügelstellung wie {@link GullMesh#wing}: {Anhebung innen, außen, Schwung, Faltung}. */
    public static double[] wing(Bird b) {
        double s = Math.sin(b.wingPhase);
        double dih = 0.1 + 0.6 * b.flap * s, out = 0.3 * b.flap * Math.sin(b.wingPhase - 0.7) - 0.06, sweep = 0.05 + 0.1 * b.flap * Math.cos(b.wingPhase);
        double f = b.fold;
        return new double[]{dih * (1 - f) - 0.08 * f, out * (1 - f) + 0.04 * f, sweep * (1 - f) + 1.45 * f, 0.25 * f};
    }

    /** „Wattvögel 1 290 in 11 Gruppen: 820 fressen, 400 rasten, 70 fliegen · Alpenstrandläufer 900 …“ */
    public String describe() {
        int[] c = census();
        StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "Wattvögel %,d in %d Gruppen: ", birds(), groups.size()));
        boolean first = true;
        for (Mode m : Mode.values()) if (c[m.ordinal()] > 0) { b.append(first ? "" : ", ").append(String.format(Locale.GERMAN, "%,d %s", c[m.ordinal()], m.label)); first = false; }
        if (first) b.append("keine");
        int[] per = new int[WaderSpecies.ALL.size()];
        for (Group g : groups) per[WaderSpecies.ALL.indexOf(g.species)] += g.size();
        for (int i = 0; i < per.length; i++) if (per[i] > 0) b.append(String.format(Locale.GERMAN, " · %s %,d", WaderSpecies.ALL.get(i).label(), per[i]));
        return b.toString();
    }
}
