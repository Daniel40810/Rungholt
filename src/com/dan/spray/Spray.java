package com.dan.spray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gischt und Brandung an festen Körpern. Der Kern tastet die Wellen rund um jeden Körper ab und erkennt, wann ein Kamm
 * gegen die Wand läuft. Dann wirft er einen Schwall Spritzwasser die Wand hinauf, dazu eine Wolke Sprühnebel, die mit
 * dem Wind treibt. Ab Bft 7 reißt der Wind Gischtfahnen von den Kämmen. Jeder Wurf ist ein {@link Parcel}, seine Tropfen
 * rechnet die Grafikkarte aus dem Bündel.
 * <p>
 * Ausgänge: Treffer an Flächen ({@link Hit}, macht nass), Wiedereintritt ins Meer ({@link Reentry}, Ringwellen),
 * Klangereignisse ({@link Sound}) und die Streudichte am Ort ({@link #scatter}, für einen sichtbaren Lichtstrahl).
 * <p>
 * Nur Java 21. Die Szene kennt das Paket nicht: Wellen kommen über {@link Sea}, Körper als {@link Body}.
 */
public final class Spray {

    /** Stufen: Tropfen je Bündel (Spritzwasser, Nebel, Fahne) und höchstens gleichzeitige Bündel. */
    public enum Quality {
        LOW(160, 10, 48, 16, 96), MEDIUM(400, 20, 96, 28, 160), HIGH(800, 32, 160, 40, 256), ULTRA(1500, 48, 256, 56, 384);
        public final int splash, mist, drift, wash, maxParcels;
        Quality(int s, int m, int d, int w, int p) { splash = s; mist = m; drift = d; wash = w; maxParcels = p; }
        public int perParcel(int kind) { return kind == Parcel.SPLASH ? splash : kind == Parcel.MIST ? mist : kind == Parcel.WASH ? wash : drift; }
    }

    /** Ein Tropfen- oder Nebeltreffer an einer Fläche (Ort, Wassermenge in l, Geschwindigkeit, Art). */
    @FunctionalInterface public interface Hit { void hit(double x, double y, double z, double mass, double speed, int kind); }
    /** Wasser fällt zurück ins Meer. */
    @FunctionalInterface public interface Reentry { void reentry(double x, double z, double strength); }
    /** Ein Brecher schlägt an (für den Ton). */
    @FunctionalInterface public interface Sound { void sound(double x, double y, double z, double strength); }

    // ------------------------------------------------------------------ Eingänge (öffentlich, zur Laufzeit änderbar)

    /** Wind in m/s und woher in Grad von Nord. */
    public volatile double windSpeed = 7, windFromDeg = 240;
    /** Signifikante Wellenhöhe in m (bestimmt, wie hoch Brecher aufsteigen). */
    public volatile double waveHeight = 1;
    /** Stärke 0..2 (1 = nach Tabelle). */
    public volatile double strength = 1;
    public volatile Quality quality = Quality.MEDIUM;
    public volatile int seed = 1885;
    /** Gischtfahnen entstehen in diesem Kreis (Mitte x, z und Halbmesser in m), meist um Turm oder Kamera. */
    public volatile double focusX, focusZ, focusRadius = 120;
    /** Abtastung der Wände: Punkte je 10 m Umfang und Takt in Hz (die Wellen ändern sich langsam). */
    public volatile int samplesPer10m = 10;
    public volatile double sampleRate = 20;
    public volatile Sea sea;
    public volatile Hit onHit = (x, y, z, m, s, k) -> { };
    public volatile Reentry onReentry = (x, z, s) -> { };
    public volatile Sound onSound = (x, y, z, s) -> { };

    private final List<Body> bodies = new CopyOnWriteArrayList<>();
    private final List<Parcel> parcels = new ArrayList<>();
    private double time, sampleTimer, driftTimer;
    private double[][] cooldown = new double[0][];
    private final Random rnd = new Random(1885);
    private int parcelSeed = 1;
    private long splashes, mists, drifts, washes, hits, reentries, impacts;
    private double lastApex;

    public Spray() { }

    // ------------------------------------------------------------------ Voreinstellungen

    public static Spray calm() { return beaufort(2); }
    public static Spray breeze() { return beaufort(5); }
    public static Spray gale() { return beaufort(8); }
    public static Spray storm() { return beaufort(10); }

    /** Wind und Seegang nach Beaufort 0..12 (Wind in der Mitte der Stufe, Wellenhöhe nach WMO). */
    public static Spray beaufort(int bft) {
        int b = Math.max(0, Math.min(12, bft));
        Spray s = new Spray();
        s.windSpeed = WIND[b];
        s.waveHeight = WAVE[b];
        return s;
    }

    static final double[] WIND = {0.2, 0.9, 2.4, 4.4, 6.7, 9.3, 12.3, 15.5, 18.9, 22.6, 26.4, 30.5, 34.0};
    static final double[] WAVE = {0.0, 0.1, 0.2, 0.6, 1.0, 2.0, 3.0, 4.0, 5.5, 7.0, 9.0, 11.5, 14.0};

    // ------------------------------------------------------------------ Körper

    public void addBody(Body b) { bodies.add(b); cooldown = new double[0][]; }
    public void clearBodies() { bodies.clear(); cooldown = new double[0][]; }
    public List<Body> bodies() { return new ArrayList<>(bodies); }

    // ------------------------------------------------------------------ Lauf

    public double time() { return time; }

    /** Wind als Vektor (wohin er weht) in m/s: {x, z}. */
    public double[] windTo() {
        double a = Math.toRadians(windFromDeg + 180);
        return new double[]{Math.sin(a) * windSpeed, -Math.cos(a) * windSpeed};
    }

    /**
     * Wie hoch der Schwall an einer senkrechten Wand aufsteigt (m über dem Kamm), aus der signifikanten Wellenhöhe,
     * der Kammhöhe am Ort (relativ zu H_s) und der Anlaufgeschwindigkeit des Wassers gegen die Wand. Geeicht so,
     * dass im Mittel Bft 5 rund 2 m, Bft 7 rund 6 m, Bft 9 den Dienstraum (rund 17 m über dem Wasser) und Bft 11 die
     * Galerie erreicht (Werkbuch, Beaufort-Tabelle) [G].
     */
    public static double jetHeight(double hs, double crest, double approach) {
        if (hs <= 0.05) return 0;
        double c = Math.max(0, Math.min(2, crest));
        double a = Math.max(0, Math.min(2.5, approach / (0.9 + 0.35 * hs)));
        return Math.min(45, 2.4 * Math.pow(hs, 1.35) * (0.55 + 0.45 * c) * (0.45 + 0.4 * a));
    }

    /** Luftreibung eines Schwalls (1/s): zusammenhängendes Wasser, kaum gebremst; einzelne Tropfen bis viermal mehr. */
    public static final double SPLASH_K = 0.3;

    /** Gipfelhöhe über dem Start bei senkrechtem Wurf mit v0 und linearer Reibung k: v0/k − g/k² · ln(1 + k v0/g). */
    public static double apex(double v0, double k) {
        return v0 / k - Parcel.G / (k * k) * Math.log(1 + k * v0 / Parcel.G);
    }

    /** Startgeschwindigkeit, mit der ein Wurf trotz Reibung k genau h m steigt (Bisektion auf {@link #apex}). */
    public static double launchSpeed(double h, double k) {
        if (h <= 0) return 0;
        double lo = 0, hi = Math.sqrt(2 * Parcel.G * h) * 4 + 5;
        for (int i = 0; i < 60; i++) {
            double m = (lo + hi) / 2;
            if (apex(m, k) < h) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    /** Einen Schritt weiter (dt in s). */
    public synchronized void step(double dt) {
        if (dt <= 0) return;
        time += dt;
        Sea s = sea;
        if (s != null && strength > 0) {
            sampleTimer += dt;
            double period = 1 / Math.max(1, sampleRate);
            if (sampleTimer >= period) {
                sampleTimer = Math.min(sampleTimer - period, period);
                sampleWalls(s);
            }
            emitDrift(s, dt);
        }
        // Ereignisse: zurück ins Meer, Nebel an Wänden; tote Bündel entfernen
        List<Parcel> keep = new ArrayList<>();
        for (Parcel p : parcels) {
            double a0 = p.age(time - dt), a1 = p.age(time);
            if (!Parcel.cloudy(p.kind())) {
                double y0 = p.center(time - dt)[1], y1 = p.center(time)[1];
                if (a0 > 0 && y0 >= p.waterY() && y1 < p.waterY()) {
                    double[] c = p.center(time);
                    reentries++;
                    onReentry.reentry(c[0], c[2], Math.min(1, p.mass() / 4));
                }
            } else if (Math.floor(a1 * 2) != Math.floor(a0 * 2)) {
                double[] c = p.center(time);
                for (Body b : bodies) {
                    double d = b.distance(c[0], c[1], c[2]);
                    double spread = p.spreadPos() * (1 + (p.kind() == Parcel.WASH ? 0.35 : 0.3) * a1);
                    if (!Double.isNaN(d) && d < spread) {
                        hits++;
                        double dx = c[0] - b.x(), dz = c[2] - b.z(), l = Math.max(1e-6, Math.hypot(dx, dz));
                        double r = b.radiusAt(c[1]);
                        onHit.hit(b.x() + dx / l * r, c[1], b.z() + dz / l * r, p.mass() * 0.05, 1, p.kind());
                    }
                }
            }
            if (p.alive(time)) keep.add(p);
        }
        parcels.clear();
        parcels.addAll(keep);
    }

    /** Abtasten aller Wände: wo läuft gerade ein Kamm gegen den Körper? */
    private void sampleWalls(Sea s) {
        double level = s.level(), hs = waveHeight;
        if (cooldown.length != bodies.size()) cooldown = new double[bodies.size()][];
        for (int bi = 0; bi < bodies.size(); bi++) {
            Body b = bodies.get(bi);
            // ein Körper ganz über oder unter dem Seegang braucht keine Abfrage des Meeres
            if (level + 1.2 * hs < b.y0() || level - 1.2 * hs > b.y1()) continue;
            double r = b.radiusAt(level);
            int n = Math.max(6, (int) Math.round(2 * Math.PI * r / 10 * samplesPer10m));
            if (cooldown[bi] == null || cooldown[bi].length != n) cooldown[bi] = new double[n];
            for (int i = 0; i < n; i++) {
                if (time < cooldown[bi][i]) continue;
                double az = 2 * Math.PI * (i + 0.5) / n, ox = Math.sin(az), oz = -Math.cos(az);
                double px = b.x() + ox * (r + 0.4), pz = b.z() + oz * (r + 0.4);
                double h = s.height(px, pz);
                if (h < b.y0() || h > b.y1()) continue;
                double[] v = s.velocity(px, pz);
                double approach = Math.max(0, -(v[0] * ox + v[2] * oz)) + Math.max(0, v[1]);
                double crest = (h - level) / Math.max(0.05, hs);
                if (crest < 0.15 || approach < 0.25 + 0.05 * hs) continue;
                double jet = jetHeight(hs, crest, approach) * strength * (0.75 + 0.5 * rnd.nextDouble());
                if (jet < 0.3) continue;
                splash(b, az, h, jet);
                // dieselbe Stelle schlägt erst mit dem nächsten Kamm wieder an
                double cd = 1.2 + 0.25 * hs + rnd.nextDouble();
                for (int k = -1; k <= 1; k++) cooldown[bi][Math.floorMod(i + k, n)] = Math.max(cooldown[bi][Math.floorMod(i + k, n)], time + cd);
            }
        }
    }

    /**
     * Ein Einschlag ohne Körper (Glacier, Phase 7): etwas mit dem Volumen v (m³) schlägt mit dem Tempo s (m/s) bei (x, z)
     * auf das Wasser in der Höhe h. Ringsum steigt ein Kranz aus Schwällen; ihre Höhe wächst mit der Wurzel aus der
     * Fallhöhe des Wassers, das verdrängt wird [G]: jet ≈ 0,35 · s · (v^⅓ / 4)^0,4, höchstens 60 m. Gibt die Höhe zurück.
     */
    public synchronized double impact(double x, double h, double z, double v, double s) {
        if (v <= 0 || s <= 0) return 0;
        double r = Math.max(0.5, 0.5 * Math.cbrt(v));
        double jet = Math.min(60, 0.35 * s * Math.pow(Math.max(0.25, Math.cbrt(v) / 4), 0.4));
        if (jet < 0.3) return 0;
        Body b = Body.cylinder(x, z, r, h - 2, h + 2);
        int n = (int) Math.max(3, Math.min(10, 3 + Math.cbrt(v)));
        double a0 = rnd.nextDouble() * Math.PI * 2;
        for (int k = 0; k < n; k++) splash(b, a0 + k * Math.PI * 2 / n, h, jet * (0.7 + 0.5 * rnd.nextDouble()));
        impacts++;
        return jet;
    }

    /**
     * Ein Schwall an der Wand von Körper b in Richtung az (Bogenmaß, von Nord im Uhrzeigersinn) auf Wasserhöhe h, der
     * jet m über den Kamm steigt. Auch von Hand („Brecher am Turm“).
     */
    public synchronized void splash(Body b, double az, double h, double jet) {
        double ox = Math.sin(az), oz = -Math.cos(az), tx = Math.cos(az), tz = Math.sin(az);
        double r = b.radiusAt(h);
        double vUp = launchSpeed(jet, SPLASH_K);
        double[] w = windTo();
        double hs = Math.max(0.3, waveHeight);
        double spread = Math.min(3.5, 0.6 + 0.25 * hs + 0.05 * jet);
        double life = 2 * Math.sqrt(2 * jet / Parcel.G) + 1.5;
        double mass = jet * spread;
        int seed = nextSeed();
        add(new Parcel(Parcel.SPLASH, b.x() + ox * (r + 0.15), h, b.z() + oz * (r + 0.15), time, ox * vUp * 0.18, vUp, oz * vUp * 0.18, life,
                SPLASH_K, spread, 0.22 * vUp + 0.3, 1, seed, 0.16 + 0.008 * jet, Math.min(h, sea != null ? sea.level() : h) - 0.3, mass, tx, tz, ox, oz, w[0], w[1], 0.25));
        splashes++;
        lastApex = jet;
        // Sprühnebel aus dem Schwall: steigt mit, treibt mit dem Wind und hängt lange in der Luft
        int nm = jet > 6 ? 2 : 1;
        for (int k = 0; k < nm; k++) {
            double y = h + jet * (0.35 + 0.35 * k);
            add(new Parcel(Parcel.MIST, b.x() + ox * (r + 0.8), y, b.z() + oz * (r + 0.8), time + 0.2 + 0.3 * k, ox * 1.2 + w[0] * 0.2, vUp * 0.15, oz * 1.2 + w[1] * 0.2,
                    6 + 4 * rnd.nextDouble() + 0.2 * jet, 0.9, Math.min(4, 0.8 + 0.12 * jet), 0.6 + 0.04 * jet, 0.03, nextSeed(), 1.2 + 0.06 * jet, -1e9,
                    mass * 0.3, tx, tz, ox, oz, w[0], w[1], 1.0));
            mists++;
        }
        // Weißwasser (F32): ab schwerer See zerschellt ein großer Schwall am Fuß zu einer dichten, hellen Walze, die
        // sich ausbreitet, mit dem Wind abzieht und in einigen Sekunden zerfällt
        if (waveHeight >= WASH_HS && jet >= WASH_JET) {
            int nw = 3 + (jet > 16 ? 2 : 0);
            double scale = Math.min(1.6, jet / 14);
            for (int k = 0; k < nw; k++) {
                double side = (k - (nw - 1) / 2.0) / Math.max(1, (nw - 1) / 2.0);
                double px = b.x() + ox * (r + 1.2) + tx * side * r * 0.8, pz = b.z() + oz * (r + 1.2) + tz * side * r * 0.8;
                double y = h + 0.6 + jet * (0.08 + 0.12 * rnd.nextDouble()) + 1.5 * (k % 2);
                add(new Parcel(Parcel.WASH, px, y, pz, time + 0.05 + 0.12 * k, ox * 2.5 + tx * side * 1.5 + w[0] * 0.25, 1.2 + 0.08 * jet, oz * 2.5 + tz * side * 1.5 + w[1] * 0.25,
                        2.8 + 0.08 * jet + rnd.nextDouble(), 0.7, 1.8 + 0.12 * jet, 0.9, 0.05, nextSeed(), (0.8 + 0.05 * jet) * (0.8 + 0.4 * rnd.nextDouble()), -1e9,
                        mass * 0.4 * scale, tx, tz, ox, oz, w[0], w[1], 0.5));
                washes++;
            }
        }
        // nass: die Wand vom Kamm bis fast zum Gipfel
        for (int k = 0; k < 4; k++) {
            double y = h + jet * 0.9 * k / 3.0;
            hits++;
            onHit.hit(b.x() + ox * b.radiusAt(y), y, b.z() + oz * b.radiusAt(y), mass * (1 - 0.2 * k), vUp, Parcel.SPLASH);
        }
        onSound.sound(b.x() + ox * r, h, b.z() + oz * r, Math.min(1, jet / 15));
    }

    /** Weißwasser ab dieser Wellenhöhe (m, Bft 9 nach WMO) und Schwallhöhe (m). */
    public static final double WASH_HS = 6.5, WASH_JET = 7;

    /** Gischtfahnen ab Bft 7: der Wind reißt Wasser von den Kämmen. */
    private void emitDrift(Sea s, double dt) {
        double wind = windSpeed;
        if (wind < 13.9) return;
        // Bündel je Sekunde im Fokuskreis: von 0 bei Bft 7 bis rund 40 bei Bft 12
        double rate = Math.pow((wind - 13.9) / 20, 1.3) * 40 * strength * Math.pow(focusRadius / 120, 2);
        driftTimer += dt * rate;
        double level = s.level(), hs = Math.max(0.5, waveHeight);
        double[] w = windTo();
        double wl = Math.max(1e-6, Math.hypot(w[0], w[1]));
        int tries = 0;
        while (driftTimer >= 1 && tries < 64) {
            tries++;
            double ang = rnd.nextDouble() * 2 * Math.PI, rr = focusRadius * Math.sqrt(rnd.nextDouble());
            double px = focusX + Math.cos(ang) * rr, pz = focusZ + Math.sin(ang) * rr;
            boolean inside = false;
            for (Body b : bodies) if (Math.hypot(px - b.x(), pz - b.z()) < b.radiusAt(level) + 1) inside = true;
            if (inside) continue;
            double h = s.height(px, pz);
            if (h - level < 0.3 * hs) continue;   // nur Kämme
            driftTimer -= 1;
            double up = 1.0 + 2.0 * rnd.nextDouble();
            add(new Parcel(Parcel.DRIFT, px, h, pz, time, w[0] * 0.45, up, w[1] * 0.45, 1.8 + rnd.nextDouble() * 1.4, 1.6,
                    1.2 + 0.15 * hs, 0.9, 1, nextSeed(), 0.11, h - 0.4, 0.5, -w[1] / wl, w[0] / wl, w[0] / wl, w[1] / wl, w[0], w[1], 0.6));
            drifts++;
            if (rnd.nextDouble() < 0.06) {
                add(new Parcel(Parcel.MIST, px, h + 1, pz, time + 0.3, w[0] * 0.5, 0.3, w[1] * 0.5, 5 + 3 * rnd.nextDouble(), 0.8, 2.0, 0.5, 0.02,
                        nextSeed(), 1.6, -1e9, 0.4, -w[1] / wl, w[0] / wl, w[0] / wl, w[1] / wl, w[0], w[1], 1.0));
                mists++;
            }
        }
        if (driftTimer > 1) driftTimer = 1;
    }

    private int nextSeed() { return Parcel.hash(seed * 7919 + parcelSeed++); }

    private void add(Parcel p) {
        parcels.add(p);
        int max = quality.maxParcels;
        // Zu viele: zuerst die ältesten Gischtfahnen, dann die ältesten überhaupt
        while (parcels.size() > max) {
            int idx = -1;
            for (int i = 0; i < parcels.size(); i++) if (parcels.get(i).kind() == Parcel.DRIFT) { idx = i; break; }
            parcels.remove(idx >= 0 ? idx : 0);
        }
    }

    /** Die lebenden Bündel (Kopie, für den Zeichenfaden). */
    public synchronized List<Parcel> parcels() { return Collections.unmodifiableList(new ArrayList<>(parcels)); }

    /** Alles zurücksetzen (Standbilder, Prüfungen). */
    public synchronized void clear() {
        parcels.clear();
        cooldown = new double[0][];
        time = 0;
        sampleTimer = driftTimer = 0;
        rnd.setSeed(seed);
        parcelSeed = 1;
        splashes = mists = drifts = washes = hits = reentries = 0;
    }

    /**
     * Streudichte am Ort (0 = klare Luft, 1 = dichter Sprühnebel): wie viel Licht das Wasser in der Luft dort streut,
     * für einen sichtbaren Strahl (Phase 9). Summe weicher Kugeln um die Bündel, Nebel zählt am meisten.
     */
    public synchronized double scatter(double px, double py, double pz) {
        double sum = 0;
        for (Parcel p : parcels) {
            double a = p.age(time);
            if (a < 0) continue;
            double[] c = p.center(time);
            double sig = p.spreadPos() * (1 + (p.kind() == Parcel.WASH ? 0.35 : 0.3) * a) + 0.5;
            double dx = px - c[0], dy = py - c[1], dz = pz - c[2];
            double d2 = (dx * dx + dy * dy + dz * dz) / (sig * sig);
            if (d2 > 9) continue;
            double w = p.kind() == Parcel.WASH ? 0.9 : p.kind() == Parcel.MIST ? 0.6 : p.kind() == Parcel.SPLASH ? 0.35 : 0.15;
            double fade = Math.max(0, 1 - a / (p.life() + p.burst()));
            sum += w * fade * Math.exp(-d2);
        }
        return Math.min(1, sum);
    }

    // ------------------------------------------------------------------ Anzeige

    public synchronized int count(int kind) {
        int n = 0;
        for (Parcel p : parcels) if (p.kind() == kind) n++;
        return n;
    }

    public synchronized int dropsAlive() {
        int n = 0;
        for (Parcel p : parcels) n += quality.perParcel(p.kind());
        return n;
    }

    /** Bisher: Schwälle, Nebel, Fahnen, Treffer, Wiedereintritte, Weißwasser. */
    public synchronized long[] totals() { return new long[]{splashes, mists, drifts, hits, reentries, washes}; }

    /** Einschläge ohne Körper seit dem Start ({@link #impact}). */
    public long impacts() { return impacts; }

    public double lastApex() { return lastApex; }

    public String describe() {
        int wash = count(Parcel.WASH);
        return String.format(Locale.GERMAN, "%d Schwälle, %d Nebel, %d Fahnen%s in der Luft · bis %,d Tropfen · letzter Schwall %.1f m",
                count(Parcel.SPLASH), count(Parcel.MIST), count(Parcel.DRIFT), wash > 0 ? ", " + wash + " Weißwasser" : "", dropsAlive(), lastApex);
    }
}
