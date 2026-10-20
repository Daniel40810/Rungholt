package com.dan.lightning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gewitter: erzeugt Blitze, lässt sie in der Zeit ablaufen und meldet Licht und Donner.
 * <p>
 * Ein Blitz wird beim Entstehen ganz gebaut: der Leitblitz als Zufallsweg in Stufen von einigen Dutzend Metern, mit
 * der Windung echter Kanäle (im Mittel rund 16° je Stufe), Ästen, die nach unten auslaufen, und dem letzten Sprung
 * zum Wasser oder zum nächsten hohen Punkt, sobald der Leitblitz ihm auf die Schlagweite nahe kommt (r = 10 · I^0,65
 * m mit I in kA, bei 30 kA rund 90 m). Darum schlägt es in einen Turm, wenn der Blitz in seiner Nähe herunterkommt.
 * Danach laufen die Hauptschläge ab: im Mittel drei bis vier, im Abstand von einigen Dutzend Millisekunden, der erste
 * mit rund 30 kA, die folgenden schwächer, manche mit Nachstrom. Wolkenblitze, Spinnenblitze und Wetterleuchten folgen
 * demselben Muster mit flackernden Pulsen statt Schlägen.
 * <p>
 * <b>Eingänge</b> sind offene Felder: Stärke des Gewitters ({@link #setLevel}), wo Gewitterzellen stehen
 * ({@link #cells}, sonst gleichmäßig um {@link #cx}, {@link #cz}), Wolkenuntergrenze, Wasserstand oder Höhe des Bodens,
 * hohe Punkte ({@link #attractors}), das Ohr ({@link #listenerX} …), die Lufttemperatur und was zwischen Kanal und
 * Auge in der Wolke steckt ({@link #occlusion}). <b>Ausgänge</b>: die Blitze selbst, Lichtpunkte für fremde Shader
 * ({@link Snapshot#lights}), die Beleuchtungsstärke an jedem Ort und der Donner mit Laufzeit über {@link #onThunder}.
 * <p>
 * Nur Java 21. Gleicher {@code seed}, gleiche Blitze.
 */
public final class Lightning {

    /** Qualitätsstufen (Paketvertrag Punkt 7): Länge der Stufen, Zahl der Äste und Stücke. */
    public enum Quality {
        LOW(70, 1, 700), MEDIUM(45, 2, 1600), HIGH(30, 3, 3000), ULTRA(20, 3, 6000);
        /** mittlere Stufe des Leitblitzes in m, tiefste Ordnung der Äste, höchstens Stücke je Blitz */
        public final double step;
        public final int maxOrder, maxSegments;
        Quality(double step, int maxOrder, int maxSegments) { this.step = step; this.maxOrder = maxOrder; this.maxSegments = maxSegments; }
    }

    /** Stärke des Gewitters (Wetterregie ab Phase 12, bis dahin das Bedienfeld). */
    public enum Level {
        /** kein Gewitter */
        OFF(0, 0),
        /** nur Wetterleuchten am Horizont, kein Donner zu hören */
        DISTANT(5, 0),
        /** ein Gewitter in der Nähe: einige Blitze je Minute */
        MODERATE(3, 0.22),
        /** kräftiges Gewitter */
        STRONG(8, 0.28),
        /** schweres Gewitter mit Blitz auf Blitz */
        SEVERE(20, 0.33);
        /** Blitze je Minute und Anteil der Erdblitze */
        public final double perMinute, cgFraction;
        Level(double perMinute, double cgFraction) { this.perMinute = perMinute; this.cgFraction = cgFraction; }
    }

    /** Wo Gewitterzellen stehen: Gewicht ≥ 0 am Ort (x, z). Die Blitze entstehen dort, wo es groß ist. */
    @FunctionalInterface
    public interface CellField { double weight(double x, double z); }

    /** Höhe des Bodens oder der Wasseroberfläche am Ort (m). */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Durchlässigkeit 0..1 zwischen einem Punkt des Kanals und dem Auge (Wolke dazwischen). */
    @FunctionalInterface
    public interface Occlusion { double transmittance(double px, double py, double pz, double ex, double ey, double ez); }

    /** Donner, sobald er das Ohr erreicht. */
    @FunctionalInterface
    public interface ThunderListener { void thunder(Thunder t); }

    /** Ein Hauptschlag (oder Puls eines Wolkenblitzes) im Augenblick, in dem er leuchtet. */
    @FunctionalInterface
    public interface StrokeListener { void stroke(Bolt b, int k, double x, double y, double z, double candela); }

    /** Ein hoher Punkt, der Blitze fängt: die Spitze eines Turms, Masts oder Blitzableiters. */
    public record Attractor(double x, double y, double z, String name) { }

    /**
     * Donner: Zeit, zu der er beginnt (Szenensekunden), wie lange er rollt, kürzester Abstand zum Kanal (m),
     * Schalldruckpegel am Ohr (dB), ob es ein naher Knall ist, Ort des nächsten Kanalpunkts und der Blitz.
     */
    public record Thunder(double time, double duration, double distance, double levelDb, boolean crack,
                          double x, double y, double z, Bolt bolt) { }

    /** Ein Lichtpunkt für fremde Shader: Ort, Lichtstärke in cd und welcher Teil davon frei sichtbar ist (0..1). */
    public record Light(double x, double y, double z, double candela, double visible) { }

    // ------------------------------------------------------------------ Eingänge

    /** Startwert des Zufalls (Punkt 6). */
    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Blitze je Minute im ganzen Gebiet und Anteil der Erdblitze; {@link #setLevel} setzt beide. */
    public double perMinute, cgFraction = 0.25;
    /** Anteil der Spinnenblitze unter den Wolkenblitzen. */
    public double spiderFraction = 0.18;
    /** Mitte und Halbmesser des Gebiets, in dem Blitze entstehen (m), wenn {@link #cells} null ist. */
    public double cx, cz, radius = 12_000;
    /** Zellen: wo Blitze entstehen (null = gleichmäßig im Gebiet). */
    public CellField cells;
    /** Wolkenuntergrenze (m); der Kanal darüber ist in der Wolke. */
    public double cloudBase = 1200;
    /** Höhe des Ladungsschwerpunkts über der Untergrenze, aus dem die Erdblitze kommen (m). */
    public double chargeAbove = 2500;
    /** Wasserstand oder Bodenhöhe (m), wenn {@link #ground} null ist. */
    public double groundLevel;
    public Ground ground;
    /** Hohe Punkte, die Blitze fangen. */
    public final List<Attractor> attractors = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Das Ohr (und Auge) für Donner und Sichtbarkeit. */
    public double listenerX, listenerY = 2, listenerZ;
    /** Lufttemperatur in °C (Schallgeschwindigkeit). */
    public double temperatureC = 15;
    /** Was zwischen Kanal und Auge in der Wolke steckt (null: nur die Untergrenze zählt). */
    public Occlusion occlusion;
    /** Geschwindigkeit des Leitblitzes in m/s (echt rund 2·10⁵; die Demo kann sie für Zeitlupe senken). */
    public double leaderSpeed = 2.0e5;
    /** Wetterleuchten entsteht weiter als dieser Abstand vom Ohr (m). */
    public double sheetDistance = 28_000;
    /** Lichtstärke je Meter Kanal bei einem Schlag von 30 kA, gesehen mit der Trägheit des Auges (cd/m). */
    public double candelaPerMetre = 5.0e3;
    /** Höchstens so viele Blitze gleichzeitig. */
    public int maxBolts = 6;
    public ThunderListener onThunder;
    public StrokeListener onStroke;

    // ------------------------------------------------------------------ Zustand

    private double time, nextAt = Double.NaN;
    private long counter;
    private final List<Bolt> bolts = new ArrayList<>();
    private final List<Thunder> pending = new ArrayList<>();
    private Thunder lastThunder;
    private int flashes, groundFlashes, attractorHits;
    private final Rng rng = new Rng(0);
    private long rngSeed = Long.MIN_VALUE;
    private Level level = Level.OFF;

    /** Farbe des Kanals als lineares RGB mit Leuchtdichte 1 (bläulich weiß, Linien von Stickstoff und Sauerstoff). */
    public static final double[] CHANNEL_RGB = {0.86, 0.95, 1.30};

    static {
        double l = 0.2126 * CHANNEL_RGB[0] + 0.7152 * CHANNEL_RGB[1] + 0.0722 * CHANNEL_RGB[2];
        for (int i = 0; i < 3; i++) CHANNEL_RGB[i] /= l;
    }

    // ------------------------------------------------------------------ Presets (Punkt 5)

    public static Lightning calm() { return beaufort(1); }
    public static Lightning breeze() { return beaufort(4); }
    public static Lightning gale() { return beaufort(8); }
    public static Lightning storm() { return beaufort(10); }

    /** Nach Beaufort: bis 7 kein Gewitter, 8 Wetterleuchten, 9 mäßig, 10 kräftig, ab 11 schwer. */
    public static Lightning beaufort(int bft) {
        Lightning l = new Lightning();
        l.setLevel(levelFor(bft));
        return l;
    }

    public static Level levelFor(int bft) {
        if (bft <= 7) return Level.OFF;
        if (bft == 8) return Level.DISTANT;
        if (bft == 9) return Level.MODERATE;
        if (bft == 10) return Level.STRONG;
        return Level.SEVERE;
    }

    /** Stärke setzen: Blitze je Minute und Anteil der Erdblitze. */
    public void setLevel(Level l) {
        level = l;
        perMinute = l.perMinute;
        cgFraction = l.cgFraction;
        nextAt = Double.NaN;
    }

    public Level level() { return level; }

    // ------------------------------------------------------------------ Ablauf

    /** Szenenzeit in s. */
    public double time() { return time; }

    /** Zeit setzen (Standbilder); laufende Blitze bleiben, wie sie sind. */
    public void setTime(double t) { time = t; nextAt = Double.NaN; }

    /** Weiterzählen: neue Blitze nach der Rate, Schläge melden, Donner ausliefern, Erloschenes entfernen. */
    public void step(double dt) {
        if (dt <= 0) return;
        double t1 = time, t2 = time + dt;
        time = t2;
        reseed();
        if (perMinute > 0) {
            if (Double.isNaN(nextAt)) nextAt = t1 + rng.exp() * 60 / perMinute;
            int guard = 0;
            while (nextAt <= t2 && guard++ < 20) {
                spawnRandom(nextAt);
                nextAt += rng.exp() * 60 / perMinute;
            }
        } else nextAt = Double.NaN;
        // Schläge, die in diesem Schritt leuchten
        if (onStroke != null) for (Bolt b : bolts)
            for (int k = 0; k < b.strokeTime.length; k++) {
                double ts = b.t0 + b.strokeTime[k];
                if (ts > t1 && ts <= t2) {
                    double[] c = centre(b);
                    onStroke.stroke(b, k, c[0], c[1], c[2], candelaPerMetre * Bolt.energy(b.strokeKA[k]) * b.mainLength);
                }
            }
        // Donner
        for (var it = pending.iterator(); it.hasNext(); ) {
            Thunder th = it.next();
            if (th.time() <= t2) {
                it.remove();
                lastThunder = th;
                if (onThunder != null) onThunder.thunder(th);
            }
        }
        bolts.removeIf(b -> t2 - b.t0 > b.duration + 0.05);
    }

    private void reseed() {
        if (rngSeed != seed) {
            rngSeed = seed;
            rng.set(seed * 0x9E3779B97F4A7C15L + 17);
        }
    }

    /** Ein Blitz an einem Zufallsort nach den Zellen (wie die Rate ihn bringen würde), jetzt. */
    public Bolt trigger() {
        reseed();
        return spawnRandom(time);
    }

    private Bolt spawnRandom(double t) {
        double[] p = pickPlace();
        double dist = Math.hypot(p[0] - listenerX, p[1] - listenerZ);
        Bolt.Kind kind;
        if (level == Level.DISTANT || dist > sheetDistance) kind = Bolt.Kind.SHEET;
        else if (rng.next() < cgFraction) kind = Bolt.Kind.CLOUD_GROUND;
        else kind = rng.next() < spiderFraction ? Bolt.Kind.SPIDER : Bolt.Kind.INTRA_CLOUD;
        return add(build(kind, p[0], p[1], t, -1));
    }

    private double[] pickPlace() {
        if (level == Level.DISTANT) {
            // am Horizont, 30 bis 70 km vom Ohr
            double a = rng.next() * 2 * Math.PI, r = Math.max(sheetDistance + 2000, 30_000 + rng.next() * 40_000);
            return new double[]{listenerX + Math.sin(a) * r, listenerZ - Math.cos(a) * r};
        }
        double[] best = null;
        double bw = -1;
        for (int i = 0; i < 24; i++) {
            double a = rng.next() * 2 * Math.PI, r = radius * Math.sqrt(rng.next());
            double x = cx + Math.sin(a) * r, z = cz - Math.cos(a) * r;
            double w = cells != null ? Math.max(0, cells.weight(x, z)) : 1;
            if (cells == null || rng.next() * 1.0 < w) return new double[]{x, z};
            if (w > bw) { bw = w; best = new double[]{x, z}; }
        }
        return best;
    }

    /** Ein Erdblitz, der bei (x, z) herunterkommt (er kann noch in einen nahen hohen Punkt springen), jetzt. */
    public Bolt strike(double x, double z) {
        reseed();
        return add(build(Bolt.Kind.CLOUD_GROUND, x, z, time, -1));
    }

    /** Ein Erdblitz in den hohen Punkt i (z. B. den Turm), jetzt. */
    public Bolt strikeAttractor(int i) {
        reseed();
        Attractor a = attractors.get(i);
        return add(build(Bolt.Kind.CLOUD_GROUND, a.x(), a.z(), time, i));
    }

    /** Ein Wolkenblitz der Art kind bei (x, z), jetzt. */
    public Bolt discharge(Bolt.Kind kind, double x, double z) {
        reseed();
        return add(build(kind, x, z, time, -1));
    }

    private Bolt add(Bolt b) {
        if (b == null) return null;
        while (bolts.size() >= maxBolts) bolts.remove(0);
        bolts.add(b);
        flashes++;
        if (b.kind == Bolt.Kind.CLOUD_GROUND) groundFlashes++;
        if (b.attractor >= 0) attractorHits++;
        Thunder th = thunderFor(b);
        if (th != null) pending.add(th);
        return b;
    }

    /** Von vorn: alle Blitze und Zähler vergessen; derselbe seed baut dann wieder dieselben Blitze. */
    public void restart() {
        clear();
        counter = 0;
        flashes = groundFlashes = attractorHits = 0;
        lastThunder = null;
        rngSeed = Long.MIN_VALUE;
    }

    /** Alle Blitze vergessen (Standbilder, Umschalten). */
    public void clear() {
        bolts.clear();
        pending.clear();
        nextAt = Double.NaN;
    }

    // ------------------------------------------------------------------ Bau eines Blitzes

    /** Schlagweite in m bei einem Strom von kA (Eriksson / IEEE). */
    public static double strikingDistance(double kA) { return 10 * Math.pow(Math.max(1, kA), 0.65); }

    private double groundAt(double x, double z) { return ground != null ? ground.height(x, z) : groundLevel; }

    /** Hilfsliste der Stücke beim Bauen. */
    private static final class Seg {
        float[][] s = new float[6][256];
        byte[] ord = new byte[256];
        float[] path = new float[256];
        int n;

        void add(double[] a, double[] b, int order, double p) {
            if (n == ord.length) {
                int m = n * 2;
                for (int k = 0; k < 6; k++) s[k] = java.util.Arrays.copyOf(s[k], m);
                ord = java.util.Arrays.copyOf(ord, m);
                path = java.util.Arrays.copyOf(path, m);
            }
            s[0][n] = (float) a[0]; s[1][n] = (float) a[1]; s[2][n] = (float) a[2];
            s[3][n] = (float) b[0]; s[4][n] = (float) b[1]; s[5][n] = (float) b[2];
            ord[n] = (byte) order;
            path[n] = (float) p;
            n++;
        }
    }

    private Bolt build(Bolt.Kind kind, double x, double z, double t, int forceAttractor) {
        Rng r = new Rng(seed * 0x2545F4914F6CDD1DL + (++counter) * 0x9E3779B97F4A7C15L);
        Quality q = quality;
        Seg seg = new Seg();
        double[] hit;
        int att = -1;
        double leaderPath;
        double[] st, ka, cc;
        double base = cloudBase;
        if (kind == Bolt.Kind.CLOUD_GROUND) {
            // Ströme der Schläge: logarithmisch normal, erster Median 30 kA, folgende 12 kA (Berger, CIGRE)
            int strokes = 1 + Math.min(11, r.poisson(2.6));
            st = new double[strokes];
            ka = new double[strokes];
            cc = new double[strokes];
            ka[0] = clamp(30 * Math.exp(0.7 * r.gauss()), 5, 200);
            for (int k = 1; k < strokes; k++) ka[k] = clamp(12 * Math.exp(0.55 * r.gauss()), 3, 80);
            double rs = strikingDistance(ka[0]);
            double gy = groundAt(x, z);
            double[] p = {x + r.gauss() * 350, base + chargeAbove * (0.6 + 0.6 * r.next()), z + r.gauss() * 350};
            double[] target = {x, gy, z};
            if (forceAttractor >= 0) {
                Attractor a = attractors.get(forceAttractor);
                target = new double[]{a.x(), a.y(), a.z()};
            }
            double[] d = norm(new double[]{target[0] - p[0], target[1] - p[1], target[2] - p[2]});
            double path = 0;
            int guard = 0;
            boolean shifted = false;
            int mainSteps = 0;
            double[] end = null;
            while (guard++ < 4000) {
                // Fangen: der nächste hohe Punkt in Schlagweite, sonst das Wasser
                double best = Double.MAX_VALUE;
                int bi = -1;
                for (int i = 0; i < attractors.size(); i++) {
                    Attractor a = attractors.get(i);
                    // Ein hoher Punkt schickt dem Leitblitz eine Fangentladung entgegen und fängt ihn im
                    // Anziehungsradius nach Eriksson (Ra = 0,84 · I^0,74 · h^0,6), sobald er ihm auf die
                    // Schlagweite nahe ist
                    double hTop = a.y() - groundAt(a.x(), a.z());
                    double ra = Math.max(rs * 0.5, 0.84 * Math.pow(ka[0], 0.74) * Math.pow(Math.max(1, hTop), 0.6));
                    double lat = Math.hypot(p[0] - a.x(), p[2] - a.z()), up = p[1] - a.y();
                    double dd = dist(p, a.x(), a.y(), a.z());
                    boolean caught = (lat < ra && up < rs * 1.2) || dd < rs;
                    if (caught && dd < best && (forceAttractor < 0 || forceAttractor == i)) { best = dd; bi = i; }
                }
                double hGround = p[1] - groundAt(p[0], p[2]);
                // Der obere Teil des Kanals ist frei gewandert; sobald die Spitze kurz über der Fanghöhe ist, wird er so
                // verschoben, dass sie über dem gewünschten Ort steht. Den Rest wandert sie wieder frei.
                if (!shifted && hGround < 1.3 * rs + 40 + q.step) {
                    shifted = true;
                    // wo die Spitze in ihrer jetzigen Richtung aufträfe, soll das Ziel sein
                    double run = hGround / Math.max(0.3, -d[1]);
                    double ox = target[0] - (p[0] + d[0] * run), oz = target[2] - (p[2] + d[2] * run);
                    for (int k = 0; k < seg.n; k++) {
                        seg.s[0][k] += (float) ox; seg.s[3][k] += (float) ox;
                        seg.s[2][k] += (float) oz; seg.s[5][k] += (float) oz;
                    }
                    p[0] = (float) ((float) p[0] + (float) ox);
                    p[2] = (float) ((float) p[2] + (float) oz);
                    continue;
                }
                if (bi >= 0) {
                    Attractor a = attractors.get(bi);
                    end = new double[]{a.x(), a.y(), a.z()};
                    att = bi;
                    break;
                }
                if (forceAttractor >= 0 && dist(p, target[0], target[1], target[2]) < rs) {
                    end = target;
                    att = forceAttractor;
                    break;
                }
                if (hGround < rs && forceAttractor < 0) {
                    end = new double[]{p[0] + r.gauss() * hGround * 0.15, groundAt(p[0], p[2]), p[2] + r.gauss() * hGround * 0.15};
                    end[1] = groundAt(end[0], end[2]);
                    break;
                }
                // Notbremse (nur der Hauptkanal zählt; die Äste haben ihr eigenes Maß, Status_Phase10.png zeigte
                // einen langen geraden Sprung, als die Äste das Maß verbraucht hatten)
                if (++mainSteps > 3000) {
                    end = forceAttractor >= 0 ? target : new double[]{p[0], groundAt(p[0], p[2]), p[2]};
                    att = forceAttractor;
                    break;
                }
                // Stufe: Windung um rund 16°, Zug zum Ziel, nach unten
                double s = q.step * (0.5 + r.next());
                double[] g = r.unit();
                double[] toT = norm(new double[]{target[0] - p[0], target[1] - p[1], target[2] - p[2]});
                double pull = forceAttractor >= 0 ? 0.22 + 0.33 * (1 - clamp(hGround / 1500, 0, 1)) : shifted ? 0.35 : 0.10;
                d = norm(new double[]{d[0] + 0.37 * g[0] + pull * toT[0], d[1] + 0.37 * g[1] + pull * toT[1] - (d[1] > -0.5 ? 0.2 : 0), d[2] + 0.37 * g[2] + pull * toT[2]});
                double[] np = {p[0] + d[0] * s, p[1] + d[1] * s, p[2] + d[2] * s};
                seg.add(p, np, 0, path);
                path += s;
                // Äste
                if (r.next() < 0.075 && q.maxOrder >= 1)
                    branch(seg, r, np, d, 1, path, (np[1] - groundAt(np[0], np[2])) * (0.15 + 0.5 * r.next()), q, rs, true);
                p = np;
            }
            // letzter Sprung: Leitblitz und Fangentladung treffen sich; auch dieses Stück ist gewunden
            double jump = dist(p, end[0], end[1], end[2]);
            int parts = Math.max(2, (int) Math.round(jump / (q.step * 0.6)));
            double[] from = p;
            for (int k = 1; k <= parts; k++) {
                double f = k / (double) parts, wob = k == parts ? 0 : jump * 0.06 * Math.sin(Math.PI * f);
                double[] to = {p[0] + (end[0] - p[0]) * f + r.gauss() * wob, p[1] + (end[1] - p[1]) * f + r.gauss() * wob * 0.5, p[2] + (end[2] - p[2]) * f + r.gauss() * wob};
                if (k == parts) to = end;
                seg.add(from, to, 0, path);
                path += dist(from, to[0], to[1], to[2]);
                from = to;
            }
            leaderPath = path;
            hit = end;
            double lt = leaderPath / leaderSpeed;
            st[0] = lt;
            for (int k = 1; k < strokes; k++) st[k] = st[k - 1] + clamp(0.045 * Math.exp(0.6 * r.gauss()), 0.008, 0.3);
            for (int k = 0; k < strokes; k++) {
                double pc = k == 0 ? 0.1 : 0.15;
                cc[k] = r.next() < pc ? 0.05 + r.next() * (k == 0 ? 0.1 : 0.25) : 0;
            }
        } else {
            // Wolkenblitze: waagerechte Äste in der Wolke (Spinnenblitz knapp unter der Untergrenze)
            double h;
            double span;
            switch (kind) {
                case SPIDER -> { h = base - 80 + r.next() * 260; span = 4000 + r.next() * 9000; }
                case SHEET -> { h = base + 1500 + r.next() * 4000; span = 3000 + r.next() * 5000; }
                default -> { h = base + 1000 + r.next() * 4000; span = 3000 + r.next() * 9000; }
            }
            double[] o = {x, h, z};
            double az = r.next() * Math.PI * 2;
            double[] d1 = {Math.sin(az), 0, -Math.cos(az)};
            double[] d2 = {-d1[0], 0, -d1[2]};
            Quality qq = kind == Bolt.Kind.SHEET ? Quality.LOW : q;
            walkFlat(seg, r, o, d1, 0, 0, span * (0.4 + 0.3 * r.next()), qq, kind, h);
            walkFlat(seg, r, o, d2, 0, 0, span * (0.3 + 0.3 * r.next()), qq, kind, h);
            leaderPath = 0;
            hit = o;
            int pulses = 3 + r.poisson(kind == Bolt.Kind.SPIDER ? 4 : 3);
            st = new double[pulses];
            ka = new double[pulses];
            cc = new double[pulses];
            double tt = 0.005;
            for (int k = 0; k < pulses; k++) {
                st[k] = tt;
                ka[k] = clamp((kind == Bolt.Kind.SPIDER ? 14 : 8) * Math.exp(0.5 * r.gauss()), 2, 40);
                cc[k] = r.next() < 0.25 ? 0.03 + 0.08 * r.next() : 0;
                tt += clamp(0.06 * Math.exp(0.6 * r.gauss()), 0.01, 0.25);
            }
        }
        Bolt b = new Bolt(kind, t, seg.s, seg.ord, seg.path, seg.n, hit, att, leaderPath, leaderSpeed, st, ka, cc);
        updateVisibility(b);
        return b;
    }

    /** Ein Ast: läuft schräg nach unten aus, ohne den Boden zu erreichen, und verzweigt sich selbst. */
    private void branch(Seg seg, Rng r, double[] start, double[] dir, int ord, double path, double budget, Quality q, double rs, boolean down) {
        if (ord > q.maxOrder || seg.n >= q.maxSegments * 0.8) return;
        double[] perp = norm(cross(dir, r.unit()));
        double ang = Math.toRadians(20 + 30 * r.next());
        double[] d = norm(new double[]{dir[0] * Math.cos(ang) + perp[0] * Math.sin(ang), dir[1] * Math.cos(ang) + perp[1] * Math.sin(ang) - 0.1,
                dir[2] * Math.cos(ang) + perp[2] * Math.sin(ang)});
        double[] p = start.clone();
        double len = 0, step = q.step * (ord == 1 ? 0.8 : 0.6);
        while (len < budget && seg.n < q.maxSegments * 0.8) {
            double s = step * (0.5 + r.next());
            double[] g = r.unit();
            d = norm(new double[]{d[0] + 0.32 * g[0], d[1] + 0.32 * g[1] - (down && d[1] > -0.25 ? 0.1 : 0), d[2] + 0.32 * g[2]});
            double[] np = {p[0] + d[0] * s, p[1] + d[1] * s, p[2] + d[2] * s};
            if (np[1] - groundAt(np[0], np[2]) < rs * 1.3) break;
            seg.add(p, np, ord, path + len);
            len += s;
            if (r.next() < 0.05 && ord < q.maxOrder)
                branch(seg, r, np, d, ord + 1, path + len, (budget - len) * (0.3 + 0.4 * r.next()), q, rs, down);
            p = np;
        }
    }

    /** Waagerechter Weg eines Wolkenblitzes um die Höhe h. */
    private void walkFlat(Seg seg, Rng r, double[] start, double[] dir, int ord, double path, double budget, Quality q, Bolt.Kind kind, double h) {
        if (ord > q.maxOrder || seg.n >= q.maxSegments) return;
        double[] p = start.clone(), d = dir.clone();
        double len = 0, step = q.step * (ord == 0 ? 1.4 : 1.1);
        double pb = kind == Bolt.Kind.SPIDER ? 0.07 : 0.05;
        while (len < budget && seg.n < q.maxSegments) {
            double s = step * (0.5 + r.next());
            double[] g = r.unit();
            d = norm(new double[]{d[0] + 0.30 * g[0], d[1] * 0.6 + 0.12 * g[1] + (h - p[1]) * 0.0004, d[2] + 0.30 * g[2]});
            double[] np = {p[0] + d[0] * s, p[1] + d[1] * s, p[2] + d[2] * s};
            seg.add(p, np, ord, path + len);
            len += s;
            if (r.next() < pb && ord < q.maxOrder) {
                double[] perp = {-d[2], 0, d[0]};
                double sg = r.next() < 0.5 ? -1 : 1, a = Math.toRadians(25 + 35 * r.next());
                double[] bd = norm(new double[]{d[0] * Math.cos(a) + sg * perp[0] * Math.sin(a), d[1], d[2] * Math.cos(a) + sg * perp[2] * Math.sin(a)});
                walkFlat(seg, r, np, bd, ord + 1, path + len, (budget - len) * (0.25 + 0.4 * r.next()), q, kind, h);
            }
            p = np;
        }
    }

    /**
     * Sichtbarkeit jedes Stücks vom Ohr aus neu rechnen (beim Entstehen; nach großen Sprüngen des Auges erneut).
     * Wetterleuchten bleibt ganz verborgen.
     */
    public void updateVisibility(Bolt b) {
        for (int i = 0; i < b.n; i++) {
            double mx = (b.ax[i] + b.bx[i]) * 0.5, my = (b.ay[i] + b.by[i]) * 0.5, mz = (b.az[i] + b.bz[i]) * 0.5;
            double v;
            if (b.kind == Bolt.Kind.SHEET) v = 0;
            else if (occlusion != null) v = occlusion.transmittance(mx, my, mz, listenerX, listenerY, listenerZ);
            else v = my <= cloudBase ? 1 : Math.exp(-(my - cloudBase) / 150);
            b.visible[i] = (float) clamp(v, 0, 1);
        }
    }

    /** Alle laufenden Blitze vom Ohr aus neu ansehen (nach einem Sprung der Kamera). */
    public void updateVisibility() { for (Bolt b : bolts) updateVisibility(b); }

    // ------------------------------------------------------------------ Donner

    /** Schallgeschwindigkeit bei der eingestellten Temperatur (m/s). */
    public double speedOfSound() { return 331.3 * Math.sqrt(1 + temperatureC / 273.15); }

    /**
     * Donner für einen Blitz: Er beginnt, wenn der Schall vom nächsten Punkt des Kanals ankommt, und rollt, bis der vom
     * fernsten da ist. Pegel: 122 dB in 100 m bei 30 kA, kugelförmig leiser, 1,2 dB je km Dämpfung in der Luft, ab 15
     * km zusätzlich 3 dB je km (die Schallstrahlen biegen sich über das Ohr). Unter 35 dB hört man nichts.
     */
    Thunder thunderFor(Bolt b) {
        if (b.kind == Bolt.Kind.SHEET) return null;
        double dmin = Double.MAX_VALUE, dmax = 0;
        int im = 0;
        for (int i = 0; i < b.n; i++) {
            if (b.order[i] > 1) continue;
            double d = dist(new double[]{b.bx[i], b.by[i], b.bz[i]}, listenerX, listenerY, listenerZ);
            if (d < dmin) { dmin = d; im = i; }
            dmax = Math.max(dmax, d);
        }
        if (dmin == Double.MAX_VALUE) return null;
        double kA = 0;
        for (double k : b.strokeKA) kA = Math.max(kA, k);
        double L = 122 + 8 * Math.log10(Math.max(1e-3, Bolt.energy(kA))) - 20 * Math.log10(Math.max(30, dmin) / 100) - 1.2e-3 * dmin
                - (dmin > 15_000 ? 3e-3 * (dmin - 15_000) : 0);
        if (b.kind != Bolt.Kind.CLOUD_GROUND) L -= 6;
        if (L < 35) return null;
        double c = speedOfSound();
        double first = b.strokeTime.length > 0 ? b.strokeTime[0] : 0, last = b.strokeTime.length > 0 ? b.strokeTime[b.strokeTime.length - 1] : 0;
        double start = b.t0 + first + dmin / c;
        double dur = (dmax - dmin) / c + (last - first) + 0.4;
        return new Thunder(start, dur, dmin, L, dmin < 800, b.bx[im], b.by[im], b.bz[im], b);
    }

    // ------------------------------------------------------------------ Abfragen

    /** Laufende Blitze (nicht verändern). */
    public List<Bolt> bolts() { return Collections.unmodifiableList(bolts); }

    /** Donner, der noch unterwegs ist. */
    public List<Thunder> pendingThunder() { return Collections.unmodifiableList(pending); }

    public Thunder lastThunder() { return lastThunder; }

    /** Blitze seit dem Start, davon Erdblitze, davon in hohe Punkte. */
    public int flashes() { return flashes; }
    public int groundFlashes() { return groundFlashes; }
    public int attractorHits() { return attractorHits; }

    /** Mitte des Hauptkanals (für Meldungen). */
    static double[] centre(Bolt b) {
        double x = 0, y = 0, z = 0, w = 0;
        for (int i = 0; i < b.n; i++) {
            if (b.order[i] != 0) continue;
            double l = b.length(i);
            x += (b.ax[i] + b.bx[i]) * 0.5 * l;
            y += (b.ay[i] + b.by[i]) * 0.5 * l;
            z += (b.az[i] + b.bz[i]) * 0.5 * l;
            w += l;
        }
        if (w <= 0) return new double[]{b.hitX, b.hitY, b.hitZ};
        return new double[]{x / w, y / w, z / w};
    }

    /** Zustand jetzt, zum Zeichnen und Abfragen. */
    public Snapshot snapshot() { return new Snapshot(time, List.copyOf(bolts), candelaPerMetre, cloudBase); }

    /**
     * Zustand zu einer Zeit: die laufenden Blitze und Abfragen für Licht. Die Lichtstärke eines Stücks ist
     * {@code candelaPerMetre · Helligkeit · Länge}, verteilt über seine Länge.
     */
    public record Snapshot(double time, List<Bolt> bolts, double candelaPerMetre, double cloudBase) {

        /** Lichtstärke des Stücks i des Blitzes b jetzt (cd, ohne Sichtbarkeit). */
        public double segmentCandela(Bolt b, int i) { return candelaPerMetre * b.segmentLevel(i, time - b.t0) * b.length(i); }

        /** Gesamte Lichtstärke aller Blitze jetzt (cd), wahlweise nur der sichtbare Teil. */
        public double totalCandela(boolean visibleOnly) {
            double s = 0;
            for (Bolt b : bolts) for (int i = 0; i < b.n; i++) s += segmentCandela(b, i) * (visibleOnly ? b.visible[i] : 1);
            return s;
        }

        /**
         * Lichtpunkte für fremde Shader: jeder Blitz als bis zu {@code perBolt} Punkte entlang seines Wegs (gewichtet
         * nach Licht), die hellsten zuerst, höchstens {@code max}. Der Teil, der in der Wolke steckt, beleuchtet die
         * Wolke von innen ({@link Light#visible} klein); fremde Shader nehmen für die Szene darunter
         * {@code candela · (visible + escape(y, Untergrenze) · (1 − visible))} und setzen den Punkt dann an die
         * Untergrenze.
         */
        public List<Light> lights(int perBolt, int max) {
            List<Light> out = new ArrayList<>();
            for (Bolt b : bolts) {
                double t = time - b.t0;
                if (t < 0) continue;
                double pmax = 1;
                for (int i = 0; i < b.n; i++) pmax = Math.max(pmax, b.path[i]);
                double[][] acc = new double[perBolt][5];
                for (int i = 0; i < b.n; i++) {
                    double c = candelaPerMetre * b.segmentLevel(i, t) * b.length(i);
                    if (c <= 0) continue;
                    int k;
                    if (b.kind == Bolt.Kind.CLOUD_GROUND) k = (int) Math.min(perBolt - 1, b.path[i] / pmax * perBolt);
                    else {
                        // Wolkenblitze breiten sich nach zwei Seiten aus: nach dem Azimut um die Mitte teilen
                        double a = Math.atan2(b.ax[i] - b.hitX, -(b.az[i] - b.hitZ));
                        k = (int) Math.min(perBolt - 1, (a + Math.PI) / (2 * Math.PI) * perBolt);
                    }
                    double mx = (b.ax[i] + b.bx[i]) * 0.5, my = (b.ay[i] + b.by[i]) * 0.5, mz = (b.az[i] + b.bz[i]) * 0.5;
                    acc[k][0] += mx * c;
                    acc[k][1] += my * c;
                    acc[k][2] += mz * c;
                    acc[k][3] += c;
                    acc[k][4] += c * b.visible[i];
                }
                for (double[] a : acc) if (a[3] > 0) out.add(new Light(a[0] / a[3], a[1] / a[3], a[2] / a[3], a[3], a[4] / a[3]));
            }
            out.sort((u, v) -> Double.compare(v.candela(), u.candela()));
            return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
        }

        /**
         * Beleuchtungsstärke am Ort (x, y, z) in lx (Luminanz) von allen Stücken, auf eine Fläche quer zum Licht,
         * mit der Sichtbarkeit jedes Stücks (das Licht aus der Wolke kommt gedämpft und gestreut, {@link #escape}) und der
         * Dämpfung der Luft (sigma 1/m). Ohne Schatten.
         */
        public double illuminanceAt(double x, double y, double z, double sigma) {
            double e = 0;
            for (Bolt b : bolts) {
                double t = time - b.t0;
                if (t < 0) continue;
                for (int i = 0; i < b.n; i++) {
                    double c = candelaPerMetre * b.segmentLevel(i, t) * b.length(i);
                    if (c <= 0) continue;
                    double mx = (b.ax[i] + b.bx[i]) * 0.5 - x, my = (b.ay[i] + b.by[i]) * 0.5 - y, mz = (b.az[i] + b.bz[i]) * 0.5 - z;
                    double d2 = mx * mx + my * my + mz * mz + 1, v = b.visible[i];
                    e += c * (v + escape((b.ay[i] + b.by[i]) * 0.5, cloudBase) * (1 - v)) / d2 * Math.exp(-sigma * Math.sqrt(d2));
                }
            }
            return e;
        }

        /**
         * Typische Leuchtdichte der vom Blitz beleuchteten Szene in cd/m², für eine Belichtung, die im Augenblick des
         * Blitzes nachgibt (wie Auge oder Kamera, die auf den Blitz belichten): die Summe der hellsten Stellen der
         * Wolkenunterseite (von unten angestrahlt mit Albedo 0,8, von innen durch die Wolke gestreut, über rund 800 m
         * verteilt) oder eine helle Fläche (Albedo 0,6) am Ort (ex, ey, ez), je nachdem, was größer ist. 0, wenn nichts
         * leuchtet.
         */
        public double flashLuminance(double ex, double ey, double ez, double base) {
            double peak = illuminanceAt(ex, ey, ez, 0) * 0.6 / Math.PI, cloud = 0;
            if (base > 0) for (Light l : lights(4, 8)) {
                double h = l.y() - base;
                double c = h < 0 ? 0.8 * l.candela() / (Math.PI * Math.max(150 * 150, h * h))
                        : 3 * l.candela() * Math.exp(-Math.max(0, h - 1000) / 1300) / (4 * Math.PI * 640_000);
                // eine helle Stelle in der Ferne füllt nur einen kleinen Teil des Blickfelds: sie zählt weniger
                double dx = l.x() - ex, dz = l.z() - ez, D = Math.sqrt(dx * dx + dz * dz + h * h);
                cloud += c * Math.min(1, 9e6 / (D * D));
            }
            return Math.max(peak, cloud);
        }

        /** Ob gerade etwas leuchtet (über einer kleinen Schwelle). */
        public boolean active() { return totalCandela(false) > 1; }
    }

    // ------------------------------------------------------------------ Hilfen

    /**
     * Anteil des Lichts aus einem Kanal in der Wolke, der unten aus der Untergrenze gestreut herauskommt: 35 % knapp
     * über der Untergrenze, mit jedem Kilometer tiefer in der Wolke weniger (Diffusionslänge 1,3 km).
     */
    public static double escape(double y, double cloudBase) {
        return y <= cloudBase ? 0.35 : 0.35 * Math.exp(-(y - cloudBase) / 1300);
    }

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    static double dist(double[] p, double x, double y, double z) {
        double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-12 ? new double[]{0, -1, 0} : new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Eigener Zufall (xorshift64*), damit gleiche seeds gleiche Blitze geben. */
    static final class Rng {
        private long s;

        Rng(long seed) { set(seed); }

        void set(long seed) { s = seed == 0 ? 0x9E3779B97F4A7C15L : seed; for (int i = 0; i < 4; i++) nextLong(); }

        long nextLong() {
            s ^= s >>> 12;
            s ^= s << 25;
            s ^= s >>> 27;
            return s * 0x2545F4914F6CDD1DL;
        }

        double next() { return (nextLong() >>> 11) * 0x1.0p-53; }

        double gauss() {
            double u = Math.max(1e-12, next()), v = next();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        }

        double exp() { return -Math.log(Math.max(1e-12, next())); }

        int poisson(double mean) {
            double l = Math.exp(-mean), p = 1;
            int k = 0;
            do { k++; p *= next(); } while (p > l && k < 50);
            return k - 1;
        }

        double[] unit() {
            double z = 2 * next() - 1, a = 2 * Math.PI * next(), r = Math.sqrt(1 - z * z);
            return new double[]{r * Math.cos(a), z, r * Math.sin(a)};
        }
    }
}
