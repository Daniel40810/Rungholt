package com.dan.fog;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Seenebel: eine flache Nebelschicht über dem Wasser, die in Bänken über die See zieht, dazu Nebelschallsignale.
 * <p>
 * <b>Dichte:</b> Der Auslöschungskoeffizient im Kern einer Bank folgt aus der Sicht dort (Koschmieder mit 5 %
 * Kontrast: σ = 3/V). Die Bänke sind ein Rauschfeld in der Fläche (drei Oktaven Wertrauschen, leicht verwirbelt), das
 * mit dem Wind zieht; eine Schwelle aus einer Eichtabelle macht daraus den gewünschten Anteil bedeckter Fläche. Die
 * Obergrenze der Schicht schwankt von Ort zu Ort um ±25 % und läuft weich aus, darunter ziehen Schwaden (Rauschen,
 * das mit der Höhe versetzt ist). Über der Obergrenze ist klare Luft: Ein hoher Turm kann aus dem Nebel ragen.
 * <p>
 * <b>Nebelsignale:</b> {@link Signal} mit Kennung ({@code Horn Mo(R) 30s}). Ein Signal tönt, solange die Sicht an
 * seinem Ort unter {@link #signalBelowM} liegt (2 sm), und meldet jeden Ton über {@link #onHorn}.
 * <p>
 * Nur Java 21. Dasselbe Rauschen (gleicher Ganzzahl-Hash) rechnet der Shader in {@code gl/}; {@link #density} ist
 * die Vorlage, an der er geprüft wird.
 */
public final class Fog {

    /** Stufen: Schritte des Blicks durch den Nebel, Größe der Bänke-Karte auf der Grafikkarte. */
    public enum Quality {
        LOW(8, 256), MEDIUM(12, 384), HIGH(20, 512), ULTRA(32, 768);
        public final int steps, mapSize;
        Quality(int s, int m) { steps = s; mapSize = m; }
    }

    /** Ein Ton eines Nebelsignals beginnt: Ort, Stärke 0..1, Dauer in s, das Signal. */
    @FunctionalInterface
    public interface Horn { void horn(double x, double y, double z, double strength, double seconds, Signal signal); }

    // ------------------------------------------------------------------ Eingänge
    public volatile long seed = 1;
    /** Wie viel Nebel 0..1 (0 = keiner, 1 = so dicht wie {@link #visibilityM}). */
    public volatile double amount = 1;
    /** Sicht im Kern einer Bank in m bei amount = 1. */
    public volatile double visibilityM = 250;
    /** Anteil der Fläche unter Bänken 0..1 (1 = geschlossene Schicht). */
    public volatile double coverage = 0.6;
    /** Mittlere Obergrenze der Schicht über dem Wasser in m. */
    public volatile double top = 60;
    /**
     * Untergrenze der Schicht über dem Wasser in m (0 = der Nebel liegt auf dem Wasser). Hebt sich die Untergrenze, wird
     * der Nebel zur abgehobenen Schicht (Hochnebel), wie Strahlungsnebel am Morgen, wenn die Sonne den Boden wärmt.
     * Der Übergang darunter ist 2 · max(1,5 m, 0,3 · base) weich. Seit Haithabu Phase 3.
     */
    public volatile double base = 0;
    /** Weichheit der Obergrenze als Anteil der Höhe. */
    public volatile double softness = 0.35;
    /** Typische Größe der Bänke in m (Zellgröße der untersten Oktave). */
    public volatile double bankSize = 1800;
    /** Schwaden 0..1 (Rauschen in der Dichte). */
    public volatile double wisps = 0.35;
    /** Wind in m/s und woher (Grad von Nord); der Nebel zieht mit 80 % davon. */
    public volatile double windSpeed = 3, windFromDeg = 240;
    /** Wasserspiegel (m). */
    public volatile double waterLevel = 0;
    public volatile Quality quality = Quality.HIGH;
    /** Nebelsignale tönen unterhalb dieser Sicht (m), mit 20 % Spielraum nach oben. */
    public volatile double signalBelowM = 3704;
    /** Sicht der Luft ohne Nebel (m) für die Schwelle der Signale (Dunst). */
    public volatile double hazeVisibilityM = 30_000;
    public final List<Signal> signals = new CopyOnWriteArrayList<>();
    public volatile Horn onHorn;

    // ------------------------------------------------------------------ Zustand
    private double time, offX, offZ;
    private volatile Snapshot snapshot;

    /** Rauschen: Wertrauschen auf einem Gitter mit Periode 256 Zellen. */
    public static final int PERIOD = 256;

    public Fog() { snapshot = build(); }

    // ------------------------------------------------------------------ Presets
    public static Fog calm() { return beaufort(1); }
    public static Fog breeze() { return beaufort(4); }
    public static Fog gale() { return beaufort(8); }
    public static Fog storm() { return beaufort(10); }

    /**
     * Preset nach Beaufort: Seenebel braucht schwachen Wind. Bis Bft 3 eine Schicht mit Bänken (Sicht 200 m), bei
     * Bft 4 und 5 nur noch Fetzen, ab Bft 6 reißt der Wind ihn auf (kein Nebel).
     */
    public static Fog beaufort(int bft) {
        Fog f = new Fog();
        bft = Math.max(0, Math.min(12, bft));
        f.windSpeed = BFT_MID[bft];
        f.amount = bft <= 3 ? 1 : bft <= 5 ? 0.55 : bft == 6 ? 0.15 : 0;
        f.coverage = bft <= 3 ? 0.7 : 0.4;
        f.visibilityM = 200;
        f.top = bft <= 2 ? 50 : 35;
        f.snapshot = f.build();
        return f;
    }

    /** Dunst: dünn, überall, hoch (Sicht 3 km). */
    public static Fog haze() { Fog f = new Fog(); f.visibilityM = 3000; f.coverage = 1; f.top = 160; f.wisps = 0.15; f.snapshot = f.build(); return f; }
    /** Nebelbänke: dicht (250 m), knapp die Hälfte der Fläche. */
    public static Fog banks() { Fog f = new Fog(); f.visibilityM = 250; f.coverage = 0.45; f.top = 55; f.snapshot = f.build(); return f; }
    /** Dichter Nebel: geschlossen, 70 m Sicht, 90 m hoch. */
    public static Fog dense() { Fog f = new Fog(); f.visibilityM = 70; f.coverage = 1; f.top = 90; f.windSpeed = 2; f.snapshot = f.build(); return f; }
    /** Flacher Seenebel: 18 m hoch, ein Turm von 30 m ragt heraus. */
    public static Fog shallow() { Fog f = new Fog(); f.visibilityM = 120; f.coverage = 0.9; f.top = 18; f.softness = 0.3; f.snapshot = f.build(); return f; }

    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    // ------------------------------------------------------------------ Zeit

    /** Um dt Sekunden weiter: Bänke ziehen mit dem Wind, Nebelsignale tönen. */
    public void step(double dt) {
        if (dt < 0) dt = 0;
        double t0 = time;
        synchronized (this) {
            time += dt;
            double a = Math.toRadians(windFromDeg + 180), v = windSpeed * DRIFT;
            double per = PERIOD * Math.max(1, bankSize);
            offX = Signal.mod(offX - Math.sin(a) * v * dt, per);
            offZ = Signal.mod(offZ + Math.cos(a) * v * dt, per);
            snapshot = build();
        }
        for (Signal s : signals) {
            double vis = Math.min(visibilityAt(s.x, s.y, s.z), hazeVisibilityM);
            boolean on = s.active ? vis < signalBelowM * 1.2 : vis < signalBelowM;
            s.active = on;
            if (!on) continue;
            double[] st = s.startsBetween(t0, time);
            for (int i = 0; i < st.length; i += 2) {
                s.count++;
                s.lastStart = st[i];
                s.lastSeconds = st[i + 1];
                Horn h = onHorn;
                if (h != null) h.horn(s.x, s.y, s.z, s.strength, st[i + 1], s);
            }
        }
    }

    /** Anteil der Windgeschwindigkeit, mit dem der Nebel zieht. */
    public static final double DRIFT = 0.8;

    public double time() { return time; }

    /** Szenenzeit setzen (Sprünge der Uhr), ohne dass Signale nachtönen. */
    public synchronized void setTime(double t) { time = t; snapshot = build(); }

    // ------------------------------------------------------------------ Abfragen

    /** Unveränderliche Werte für Grafikkarte und Abfragen. */
    public record Snapshot(double sigma, double threshold, double top, double softness, double bankSize, double wisps, double offX, double offZ,
                           double waterLevel, int seed, Quality quality, double coverage, double time, double base) {
        /** Gibt es überhaupt Nebel? */
        public boolean any() { return sigma > 1e-7 && coverage > 1e-3; }
    }

    public Snapshot snapshot() { return snapshot; }

    private Snapshot build() {
        double sig = Math.max(0, amount) * SIGMA_PER_VIS / Math.max(1, visibilityM);
        return new Snapshot(sig, thresholdFor(coverage), Math.max(0.5, top), clamp(softness, 0.02, 1.5), Math.max(10, bankSize), clamp(wisps, 0, 1),
                offX, offZ, waterLevel, (int) (seed * 0x9E3779B1L), quality, clamp(coverage, 0, 1), time, Math.max(0, base));
    }

    /** Koschmieder mit 5 % Kontrast: σ · V = 3,0. */
    public static final double SIGMA_PER_VIS = 2.996;

    /** Auslöschungskoeffizient (1/m) am Ort. */
    public double density(double x, double y, double z) { return density(snapshot, x, y, z); }

    /** Streukoeffizient (1/m) am Ort; Nebeltröpfchen streuen fast alles (Albedo 1). */
    public double scatter(double x, double y, double z) { return density(snapshot, x, y, z); }

    /** Waagerechte Sicht am Ort in m (bis 100 km). */
    public double visibilityAt(double x, double y, double z) {
        double s = density(x, y, z);
        return s <= SIGMA_PER_VIS / 1e5 ? 1e5 : SIGMA_PER_VIS / s;
    }

    /** Durchlässigkeit des Nebels zwischen zwei Punkten (64 Schritte). */
    public double transmittance(double ax, double ay, double az, double bx, double by, double bz) {
        Snapshot s = snapshot;
        if (!s.any()) return 1;
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double tau = 0;
        int n = 64;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            tau += density(s, ax + dx * t, ay + dy * t, az + dz * t) * len / n;
            if (tau > 30) break;
        }
        return Math.exp(-tau);
    }

    /** Wie viel Nebel am Ort in der Fläche liegt (0..1, ohne Höhe). */
    public double bankAt(double x, double z) { return bank(snapshot, x, z)[0]; }

    /** Obergrenze der Schicht am Ort über dem Wasser (m). */
    public double topAt(double x, double z) { return bank(snapshot, x, z)[1]; }

    /** Gemessener Anteil der Fläche unter einer Bank (Mittel über ein Gitter von 24 km um den Ursprung). */
    public double measuredCoverage() {
        Snapshot s = snapshot;
        int n = 96, hit = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) if (bank(s, (i + 0.5) / n * 24_000 - 12_000, (j + 0.5) / n * 24_000 - 12_000)[0] > 0.5) hit++;
        return hit / (double) (n * n);
    }

    // ------------------------------------------------------------------ Feld (wie fog_common.glsl)

    /**
     * Dichte am Ort: σ · Bank(x, z) · Höhe(y) · Schwaden. Zeile für Zeile wie {@code fogDensity} im Shader.
     */
    public static double density(Snapshot s, double x, double y, double z) {
        if (!s.any()) return 0;
        double h = y - s.waterLevel;
        if (h < -2) return 0;
        double topMax = s.top * 1.25 * (1 + s.softness * 0.5);
        if (h > topMax) return 0;
        double[] b = bank(s, x, z);
        if (b[0] <= 0) return 0;
        double tl = b[1], soft = tl * s.softness;
        double hv = smooth(tl + soft * 0.5, tl - soft * 0.5, h);
        if (s.base > 0) hv *= smooth(s.base - 2 * Math.max(1.5, 0.3 * s.base), s.base, h);
        if (hv <= 0) return 0;
        double w = 1;
        if (s.wisps > 0) {
            double q = vnoise((x + s.offX * 1.15) / 70 + h / 23, (z + s.offZ * 1.15) / 70 - h / 31, s.seed + 5);
            w = 1 + s.wisps * (q - 0.5) * 1.6;
        }
        return s.sigma * b[0] * hv * Math.max(0, w);
    }

    /** {Bank 0..1, Obergrenze am Ort in m} */
    public static double[] bank(Snapshot s, double x, double z) {
        double qx = (x + s.offX) / s.bankSize, qz = (z + s.offZ) / s.bankSize;
        double wx = vnoise(qx + 13, qz, s.seed + 1) - 0.5, wz = vnoise(qx, qz + 29, s.seed + 2) - 0.5;
        double n = 0.55 * vnoise(qx + wx * 0.8, qz + wz * 0.8, s.seed)
                + 0.30 * vnoise(qx * 2 + 5, qz * 2 + 9, s.seed + 3)
                + 0.15 * vnoise(qx * 4 + 11, qz * 4 + 3, s.seed + 4);
        double bk = s.coverage >= 0.999 ? 1 : smooth(s.threshold - EDGE, s.threshold + EDGE, n);
        double tl = s.top * (0.75 + 0.5 * vnoise(qx * 3 + 7, qz * 3 + 1, s.seed + 6));
        return new double[]{bk, tl};
    }

    /** Halbe Breite des Übergangs am Rand einer Bank (im Rauschwert). */
    public static final double EDGE = 0.035;

    /** Wertrauschen 0..1, periodisch über {@link #PERIOD} Zellen, weich interpoliert. */
    public static double vnoise(double x, double z, int salt) {
        double fx0 = Math.floor(x), fz0 = Math.floor(z);
        int ix = (int) fx0, iz = (int) fz0;
        double fx = x - fx0, fz = z - fz0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash01(ix, iz, salt), b = hash01(ix + 1, iz, salt), c = hash01(ix, iz + 1, salt), d = hash01(ix + 1, iz + 1, salt);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    /** Ganzzahl-Hash wie im Shader (uint-Arithmetik), Ergebnis 0..1 mit 24 Bit. */
    public static double hash01(int x, int z, int salt) {
        int h = (x & (PERIOD - 1)) * 0x8da6b343 ^ (z & (PERIOD - 1)) * 0xd8163841 ^ salt * 0xcb1ab31f;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        h ^= h >>> 16;
        return (h >>> 8) * (1.0 / 16777216.0);
    }

    // ------------------------------------------------------------------ Eichung

    private static double[] quantiles;

    /**
     * Schwelle im Rauschwert, unter der der Anteil 1 − c der Fläche liegt. Die Verteilung des Rauschens wird einmal
     * an 65 536 Orten gemessen (seed-unabhängig genug) und als Tabelle mit 129 Stufen gehalten.
     */
    public static synchronized double thresholdFor(double c) {
        if (quantiles == null) {
            Snapshot s = new Snapshot(1, 0, 50, 0.3, 1, 0, 0, 0, 0, 12345, Quality.HIGH, 0.5, 0, 0);
            int n = 256;
            double[] v = new double[n * n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    double x = (i + 0.37) * 0.731, z = (j + 0.61) * 0.689;
                    double wx = vnoise(x + 13, z, s.seed + 1) - 0.5, wz = vnoise(x, z + 29, s.seed + 2) - 0.5;
                    v[i * n + j] = 0.55 * vnoise(x + wx * 0.8, z + wz * 0.8, s.seed) + 0.30 * vnoise(x * 2 + 5, z * 2 + 9, s.seed + 3) + 0.15 * vnoise(x * 4 + 11, z * 4 + 3, s.seed + 4);
                }
            Arrays.sort(v);
            quantiles = new double[129];
            for (int k = 0; k <= 128; k++) quantiles[k] = v[Math.min(v.length - 1, (int) Math.round(k / 128.0 * (v.length - 1)))];
        }
        double q = clamp(1 - c, 0, 1) * 128;
        int k = (int) Math.min(127, Math.floor(q));
        return quantiles[k] + (quantiles[k + 1] - quantiles[k]) * (q - k);
    }

    // ------------------------------------------------------------------ Hilfen

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    static double smooth(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** „Nebelbänke 45 %, Sicht 250 m, bis 55 m, zieht mit 2,4 m/s“ */
    public String describe() {
        Snapshot s = snapshot;
        if (!s.any()) return "kein Nebel";
        String kind = coverage >= 0.95 ? (visibilityM / Math.max(1e-3, amount) < 1000 ? "dichter Nebel" : "Dunst") : "Nebelbänke " + Math.round(coverage * 100) + " %";
        return String.format(Locale.GERMAN, "%s, Sicht %.0f m, bis %.0f m, zieht mit %.1f m/s", kind, visibilityM / Math.max(1e-3, amount), top, windSpeed * DRIFT);
    }
}
