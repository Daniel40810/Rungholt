package com.dan.rain;

import java.util.Locale;
import java.util.Random;

/**
 * Regen über offenem Wasser: Tropfen um das Auge, Regenvorhänge in der Ferne, Ringe und Krönchen auf dem Wasser.
 * <p>
 * Der Kern rechnet mit echten Größen. Die Tropfengrößen folgen Marshall und Palmer (1948):
 * {@code N(D) = N0 · e^(−Λ D)} mit {@code N0 = 8000 m⁻³ mm⁻¹} und {@code Λ = 4,1 · R^(−0,21) mm⁻¹}. Die Fallgeschwindigkeit
 * folgt Atlas, Srivastava und Sekhon (1973): {@code v(D) = 9,65 − 10,3 · e^(−0,6 D)} m/s. Daraus folgen die
 * Regenmenge (zur Probe), die Zahl der Einschläge je m² und Sekunde und die Sichtweite im Regen.
 * <p>
 * Gezeichnet wird nur, was das Auge auflöst: Tropfen ab 0,5 mm in einem Kasten um die Kamera, der mit der Welt fest
 * verbunden ist. Jeder Tropfen ist eine Formel aus Nummer, Zeit und Wind ({@link Snapshot#drop}); dieselbe Formel steht
 * im Shader. Wo es regnet, sagt ein {@link Curtain} (meist die Wolken); der Kern legt daraus eine Karte von 16 km um das
 * Auge an, die Tropfen und Vorhänge lesen.
 * <p>
 * Nur Java 21. Der Kern kennt weder Wolken noch Meer noch die Szene.
 */
public final class Rain {

    /** Stufen: gezeichnete Tropfen, halbe Kastenbreite (m), Auflösung der Ringkarte, Schritte im Vorhang, Krönchen. */
    public enum Quality {
        LOW(6_000, 10, 128, 16, 10, 6), MEDIUM(16_000, 12, 256, 24, 14, 8), HIGH(32_000, 14, 256, 32, 18, 8), ULTRA(60_000, 16, 512, 48, 22, 10);
        public final int drops, rippleRes, curtainSteps, crownDrops;
        public final double box, crownRadius;
        Quality(int drops, double box, int rippleRes, int curtainSteps, double crownRadius, int crownDrops) {
            this.drops = drops; this.box = box; this.rippleRes = rippleRes; this.curtainSteps = curtainSteps;
            this.crownRadius = crownRadius; this.crownDrops = crownDrops;
        }
    }

    /** Wo es regnet: 0 (trocken) bis 1 (volle Rate) am Ort x, z. Muss schnell sein (rund 10 000 Fragen je halbe Sekunde). */
    @FunctionalInterface public interface Curtain { double density(double x, double z); }

    // ------------------------------------------------------------------ Eingänge (öffentlich, zur Laufzeit änderbar)

    /** Regen in mm/h unter dem dichtesten Vorhang. 0,1–0,5 Niesel, 1–2 leicht, 2–8 mäßig, 8–30 stark, darüber Wolkenbruch. */
    public volatile double rate = 2;
    /** Wind in m/s, woher (Grad von Nord), Böigkeit 0..1. */
    public volatile double windSpeed = 7, windFromDeg = 240, gust = 0.2;
    /** Wolkenuntergrenze in m: bis dahin reichen die Vorhänge. */
    public volatile double cloudBase = 800;
    /** Wasserspiegel in m (Krönchen und Ringe). */
    public volatile double waterLevel;
    public volatile Quality quality = Quality.MEDIUM;
    public volatile int seed = 1885;
    /** null: überall dieselbe Rate (Landregen). */
    public volatile Curtain curtain;
    /** Auge (Mitte des Tropfenkastens und der Karte). */
    public volatile double eyeX, eyeY = 10, eyeZ;
    /**
     * Unterstände: senkrechte Zylinder, in denen es nicht regnet (ein Raum hinter Glas, ein Dach), je vier Zahlen
     * x, z, Halbmesser, Oberkante; höchstens vier.
     */
    public volatile double[] shelter = new double[0];

    // ------------------------------------------------------------------ Konstanten

    /** Marshall-Palmer: N0 in m⁻³ mm⁻¹. */
    public static final double N0 = 8000;
    /** Kleinster gezeichneter und größter Tropfen in mm. */
    public static final double DMIN = 0.5, DMAX = 6;
    /** Karte der Vorhänge: Zellen je Seite und Kantenlänge in m. */
    public static final int MAP_N = 96;
    public static final double MAP_SIZE = 16_000;
    /** Die Fallzeit läuft im Kreis, damit die Grafikkarte mit float genau bleibt (ein unsichtbarer Sprung je 10 min). */
    public static final double FALL_PERIOD = 600;
    /** Kantenlänge der Ringkarte in m (kachelbar). */
    public static final double RIPPLE_TILE = 3;

    private double time, driftX, driftZ, mapTimer = 1e9, gustT;
    private double mapX0, mapZ0, windX, windZ;
    private float[] map = new float[0];
    private int mapVersion;
    private volatile Snapshot snapshot;

    public Rain() { snapshot = build(); }

    // ------------------------------------------------------------------ Voreinstellungen

    public static Rain calm() { return beaufort(1); }
    public static Rain breeze() { return beaufort(5); }
    public static Rain gale() { return beaufort(8); }
    public static Rain storm() { return beaufort(10); }

    /** Wind nach Beaufort (Mitte der Stufe) mit einer Regenmenge, wie sie dazu oft fällt [G]. */
    public static Rain beaufort(int bft) {
        int b = Math.max(0, Math.min(12, bft));
        Rain r = new Rain();
        r.windSpeed = WIND[b];
        r.rate = RATE[b];
        r.gust = 0.1 + 0.03 * b;
        return r;
    }

    /** Niesel, leicht, mäßig, stark, Wolkenbruch (mm/h), Wind unverändert. */
    public static Rain drizzle() { return of(0.3); }
    public static Rain light() { return of(1.5); }
    public static Rain moderate() { return of(5); }
    public static Rain heavy() { return of(15); }
    public static Rain cloudburst() { return of(60); }

    static Rain of(double rate) { Rain r = new Rain(); r.rate = rate; return r; }

    static final double[] WIND = {0.2, 0.9, 2.4, 4.4, 6.7, 9.3, 12.3, 15.5, 18.9, 22.6, 26.4, 30.5, 34.0};
    static final double[] RATE = {0.2, 0.3, 0.5, 0.8, 1.2, 2, 3, 5, 8, 12, 20, 30, 40};

    // ------------------------------------------------------------------ Physik

    /** Λ der Marshall-Palmer-Verteilung in mm⁻¹. */
    public static double lambda(double rate) { return 4.1 * Math.pow(Math.max(0.05, rate), -0.21); }

    /** Fallgeschwindigkeit in m/s eines Tropfens mit D mm (Atlas 1973), wenigstens 0,5 m/s. */
    public static double terminal(double d) { return Math.max(0.5, 9.65 - 10.3 * Math.exp(-0.6 * d)); }

    /** Tropfen je m³ mit Durchmesser über dmin (mm). */
    public static double numberDensity(double rate, double dmin) {
        if (rate <= 0) return 0;
        double l = lambda(rate);
        return N0 / l * Math.exp(-l * dmin);
    }

    /** Einschläge je m² und Sekunde von Tropfen über dmin (mm) auf eine waagerechte Fläche: ∫ N(D) v(D) dD. */
    public static double impacts(double rate, double dmin) {
        if (rate <= 0) return 0;
        double l = lambda(rate), sum = 0, dd = 0.01;
        for (double d = dmin + dd / 2; d < 8; d += dd) sum += N0 * Math.exp(-l * d) * terminal(d) * dd;
        return sum;
    }

    /** Regenmenge in mm/h, die aus der Verteilung folgt: 6π·10⁻⁴ ∫ N(D) D³ v(D) dD (zur Probe gegen {@link #rate}). */
    public static double rateFromDistribution(double rate) {
        if (rate <= 0) return 0;
        double l = lambda(rate), sum = 0, dd = 0.005;
        for (double d = dd / 2; d < 8; d += dd) sum += N0 * Math.exp(-l * d) * d * d * d * terminal(d) * dd;
        return 6 * Math.PI * 1e-4 * sum;
    }

    /** Auslöschung durch Regen je km [G]: 0,25 · R^0,63 (nach Messungen der Sichtweite im Regen). */
    public static double extinctionPerKm(double rate) { return rate <= 0 ? 0 : 0.25 * Math.pow(rate, 0.63); }

    /** Sichtweite in km im Regen allein (Kontrast 2 %: 3,912 / σ). */
    public static double visibilityKm(double rate) { return rate <= 0 ? Double.POSITIVE_INFINITY : 3.912 / extinctionPerKm(rate); }

    /**
     * Anteil der gezeichneten Tropfen, die bei dieser Rate leben: mehr Regen, mehr Streifen [G]. Bei 40 mm/h alle,
     * bei 1 mm/h knapp ein Sechstel, bei Niesel ein Zwölftel.
     */
    public static double activeFraction(double localRate) { return localRate <= 0 ? 0 : Math.min(1, Math.sqrt(localRate / 40)); }

    /** Deckkraft eines Streifens [G]: im Niesel kaum zu sehen, im Wolkenbruch deutlich. */
    public static double streakAlpha(double rate) { return 0.06 + 0.20 * Math.min(1, Math.sqrt(Math.max(0, rate) / 30)); }

    /** Sichtbare Ringe (Tropfen ab 1 mm) je m² und Sekunde bei der vollen Rate. */
    public double rippleRate() { return impacts(rate, 1.0); }

    // ------------------------------------------------------------------ Lauf

    public double time() { return time; }

    /** Wind als Vektor (wohin er weht) in m/s: {x, z}, ohne Böen. */
    public double[] windTo() {
        double a = Math.toRadians(windFromDeg + 180);
        return new double[]{Math.sin(a) * windSpeed, -Math.cos(a) * windSpeed};
    }

    /** Einen Schritt weiter (dt in s): Böen, Drift, alle halbe Sekunde die Karte der Vorhänge. */
    public synchronized void step(double dt) {
        if (dt < 0) dt = 0;
        time += dt;
        gustT += dt;
        double[] w = windTo();
        // Böen: zwei langsame Schwingungen, gegeneinander verstimmt [G]
        double g = 1 + gust * (0.6 * Math.sin(gustT * 0.53) + 0.4 * Math.sin(gustT * 1.37 + 1.1));
        // in Bodennähe bremst die Reibung den Wind, die Tropfen folgen ihm ganz
        windX = w[0] * 0.9 * g;
        windZ = w[1] * 0.9 * g;
        double h2 = 2 * quality.box;
        driftX = mod(driftX + windX * dt, h2);
        driftZ = mod(driftZ + windZ * dt, h2);
        mapTimer += dt;
        double cell = MAP_SIZE / MAP_N;
        double cx = Math.floor(eyeX / cell) * cell - MAP_SIZE / 2, cz = Math.floor(eyeZ / cell) * cell - MAP_SIZE / 2;
        if (mapTimer >= 0.5 || cx != mapX0 || cz != mapZ0 || map.length == 0) rebuildMap(cx, cz);
        snapshot = build();
    }

    private void rebuildMap(double x0, double z0) {
        mapTimer = 0;
        mapX0 = x0;
        mapZ0 = z0;
        Curtain c = curtain;
        if (c == null) {
            if (map.length != 0) mapVersion++;
            map = new float[0];
            return;
        }
        double cell = MAP_SIZE / MAP_N;
        float[] m = new float[MAP_N * MAP_N];
        for (int j = 0; j < MAP_N; j++)
            for (int i = 0; i < MAP_N; i++)
                m[j * MAP_N + i] = (float) Math.max(0, Math.min(1, c.density(x0 + (i + 0.5) * cell, z0 + (j + 0.5) * cell)));
        map = m;
        mapVersion++;
    }

    /** Neu anfangen (Standbilder, Prüfungen): Zeit, Drift, Karte. */
    public synchronized void reset() {
        time = driftX = driftZ = gustT = 0;
        mapTimer = 1e9;
        map = new float[0];
        step(0);
    }

    static double mod(double v, double m) { return v - m * Math.floor(v / m); }

    private Snapshot build() {
        double box = quality.box;
        double[] sh = shelter;
        float[] shf = new float[Math.min(16, sh.length - sh.length % 4)];
        for (int i = 0; i < shf.length; i++) shf[i] = (float) sh[i];
        return new Snapshot(time, mod(time, FALL_PERIOD), driftX, driftZ, windX, windZ, Math.max(0, rate), lambda(rate), eyeX, eyeY, eyeZ,
                box, box * 0.75, mapX0, mapZ0, MAP_SIZE / MAP_N, map.length == 0 ? 0 : MAP_N, map, mapVersion, quality, seed, cloudBase, waterLevel, shf);
    }

    /** Der Zustand für Shader und Prüfungen (unveränderlich). */
    public Snapshot snapshot() { return snapshot; }

    /**
     * Unveränderlicher Zustand. {@code fall} ist die Zeit im Kreis von {@link #FALL_PERIOD}, {@code driftX/Z} der
     * Weg des Windes im Kreis der Kastenbreite. Die Karte ist leer (n = 0), wenn es überall gleich regnet.
     */
    public record Snapshot(double time, double fall, double driftX, double driftZ, double windX, double windZ, double rate, double lambda,
                           double eyeX, double eyeY, double eyeZ, double box, double boxY, double mapX0, double mapZ0, double mapCell, int mapN,
                           float[] map, int mapVersion, Quality quality, int seed, double cloudBase, double waterLevel, float[] shelter) {

        /** Liegt der Ort in einem Unterstand? (Werte als float, wie im Shader) */
        public boolean sheltered(double x, double y, double z) {
            for (int i = 0; i + 3 < shelter.length; i += 4) {
                double dx = x - shelter[i], dz = z - shelter[i + 1];
                if (dx * dx + dz * dz < (double) shelter[i + 2] * shelter[i + 2] && y < shelter[i + 3]) return true;
            }
            return false;
        }

        /** Vorhang 0..1 am Ort (bilinear zwischen den Zellmitten, wie im Shader). */
        public double curtainAt(double x, double z) {
            if (mapN == 0) return 1;
            double gx = (x - mapX0) / mapCell - 0.5, gz = (z - mapZ0) / mapCell - 0.5;
            double fx = Math.floor(gx), fz = Math.floor(gz);
            int ix = (int) fx, iz = (int) fz;
            double tx = gx - fx, tz = gz - fz;
            double a = at(ix, iz), b = at(ix + 1, iz), c = at(ix, iz + 1), d = at(ix + 1, iz + 1);
            return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
        }

        private double at(int i, int j) {
            i = Math.max(0, Math.min(mapN - 1, i));
            j = Math.max(0, Math.min(mapN - 1, j));
            return map[j * mapN + i];
        }

        /** Regen in mm/h am Ort. */
        public double rateAt(double x, double z) { return rate * curtainAt(x, z); }

        /**
         * Tropfen i: {x, y, z, D (mm), lebt (1/0), vx, vy, vz}. Zeile für Zeile wie {@code rainDrop} im Shader.
         * Größe aus der abgeschnittenen Marshall-Palmer-Verteilung (0,5 bis 6 mm), Ort im Kasten um das Auge, der mit
         * der Welt fest verbunden ist: Ein Tropfen, der den Kasten verlässt, kommt auf der anderen Seite wieder herein.
         */
        public double[] drop(int i) {
            double r1 = rnd(seed, i, 1), r3 = rnd(seed, i, 3), r4 = rnd(seed, i, 4), r5 = rnd(seed, i, 5), r6 = rnd(seed, i, 6);
            double d = DMIN - Math.log(1 - r1 * (1 - Math.exp(-lambda * (DMAX - DMIN)))) / lambda;
            double vt = terminal(d);
            double x = (r3 * 2 - 1) * box + driftX, z = (r5 * 2 - 1) * box + driftZ, y = (r4 * 2 - 1) * boxY - vt * fall;
            x = eyeX + wrap(x - eyeX, box);
            z = eyeZ + wrap(z - eyeZ, box);
            y = eyeY + wrap(y - eyeY, boxY);
            boolean live = r6 < activeFraction(rateAt(x, z)) && y < cloudBase && !sheltered(x, y, z);
            return new double[]{x, y, z, d, live ? 1 : 0, windX, -vt, windZ};
        }

        /** Streudichte 0..1 am Ort (für einen sichtbaren Lichtstrahl, Phase 9): Auslöschung / 3 km⁻¹. */
        public double scatter(double x, double y, double z) {
            if (y > cloudBase) return 0;
            return Math.min(1, extinctionPerKm(rateAt(x, z)) / 3);
        }
    }

    static double wrap(double v, double h) { return v - 2 * h * Math.floor((v + h) / (2 * h)); }

    // ------------------------------------------------------------------ Zufall (bitgleich mit dem Shader)

    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** Zufall 0..1 für Tropfen i, Wurf j. */
    public static double rnd(int seed, int i, int j) {
        return (hash(hash(i * 16 + j) ^ seed) >>> 8) * (1.0 / 16777216.0);
    }

    // ------------------------------------------------------------------ Ausgänge

    /** Regen in mm/h am Ort (für Verwitterung, Scheiben). */
    public double rateAt(double x, double z) { return snapshot.rateAt(x, z); }

    /** Streudichte 0..1 am Ort (ScatterField). */
    public double scatter(double x, double y, double z) { return snapshot.scatter(x, y, z); }

    /**
     * Wie viele Tropfen über dmin (mm) in dt Sekunden auf eine Fläche von area m² mit der Normalen n treffen, am Ort
     * x, z. Der Regen fällt schräg mit dem Wind (Richtung eines Tropfens von 2 mm); die Rückseite bleibt trocken.
     */
    public double expectedHits(double x, double z, double area, double nx, double ny, double nz, double dmin, double dt) {
        Snapshot s = snapshot;
        double r = s.rateAt(x, z);
        if (r <= 0) return 0;
        double vt = terminal(2);
        double fx = s.windX(), fy = -vt, fz = s.windZ(), fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        double facing = Math.max(0, -(nx * fx + ny * fy + nz * fz) / fl);
        // senkrecht zur Fallrichtung kommen um den Faktor |v| / vt mehr Tropfen an als auf die waagerechte Fläche
        return impacts(r, dmin) * fl / vt * facing * area * dt;
    }

    /** Ein Tropfen aus der Verteilung über dmin: {D in mm, Fallgeschwindigkeit in m/s}. */
    public double[] sampleDrop(Random rnd, double dmin) {
        double l = lambda(rate);
        double d = dmin - Math.log(1 - rnd.nextDouble() * (1 - Math.exp(-l * (DMAX - dmin)))) / l;
        return new double[]{d, terminal(d)};
    }

    /** Lautstärke 0..1 des Regens am Auge (für den Ton, Phase 14). */
    public double loudness() { return Math.min(1, Math.sqrt(snapshot.rateAt(eyeX, eyeZ) / 40)); }

    public String describe() {
        Snapshot s = snapshot;
        double here = s.rateAt(eyeX, eyeZ);
        String kind = here < 0.05 ? "trocken" : here < 0.5 ? "Niesel" : here < 2 ? "leichter Regen" : here < 8 ? "mäßiger Regen"
                : here < 30 ? "starker Regen" : "Wolkenbruch";
        return String.format(Locale.GERMAN, "%s · %.1f mm/h hier (bis %.1f) · Sicht im Regen %s · %,.0f Einschläge/m²s",
                kind, here, rate, here > 0.05 ? String.format(Locale.GERMAN, "%.1f km", visibilityKm(here)) : "frei", impacts(here, 0.5));
    }
}
