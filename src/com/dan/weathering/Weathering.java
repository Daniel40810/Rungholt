package com.dan.weathering;

import java.util.Locale;

/**
 * Verwitterung für beliebige Oberflächen: nass, Rost, Algen, Salz und Moos. Alles entsteht aus Masken und Rauschen,
 * nichts wird als Textur geladen. Der Kern rechnet den Zustand (Wasserstand, nasse Zone, Regen, Spritzwasser) und
 * dieselben Masken, die der Shader {@code gl/weathering.glsl} zeichnet, Zeile für Zeile auf dem Prozessor. So lässt
 * sich die Grafikkarte prüfen, und Werkzeuge ohne Grafikkarte (Prüfungen, Standbilder) bekommen dieselben Werte.
 * <p>
 * Eine Oberfläche gibt dem Shader fünf Dinge: den Ort (für alles, was am Wasserstand hängt), die Normale, die
 * Flächenkoordinaten {@code st} in Metern (s waagerecht entlang der Fläche, t senkrecht), den Abstand unter der
 * nächsten Kante darüber (dort beginnen Rostläufer) und die Anfälligkeit für Rost (0 frische Farbe, 1 blanker Stahl).
 * <p>
 * Nur Java 21. Die Szene kennt das Paket nicht; Wasserstand, Seegang, Wind und Regen kommen als Zahlen herein.
 */
public final class Weathering {

    /** Stufen: Größe der Nässekarte. Die Masken selbst rechnen in jeder Stufe gleich. */
    public enum Quality {
        LOW(64, 32), MEDIUM(128, 64), HIGH(256, 128), ULTRA(256, 128);
        public final int mapW, mapH;
        Quality(int w, int h) { mapW = w; mapH = h; }
    }

    // ------------------------------------------------------------------ Eingänge (alle öffentlich, zur Laufzeit änderbar)

    /** Wasserstand in m (gleiche Höhe wie y der Szene). */
    public volatile double waterLevel;
    /** Mittleres Hoch- und Niedrigwasser in m: dazwischen wachsen die Algen. */
    public volatile double meanHighWater = 1.5, meanLowWater = -1.5;
    /** Signifikante Wellenhöhe in m: bestimmt Auflauf und Spritzzone. */
    public volatile double waveHeight = 1.0;
    /** Wind in m/s und woher in Grad von Nord (Regen kommt mit dem Wind, Wind trocknet). */
    public volatile double windSpeed = 7, windFromDeg = 240;
    /** Regen in mm/h. */
    public volatile double rainRate;
    /** Sonne 0..1 (Anteil direkten Lichts mal Sonnenhöhe), trocknet mit. */
    public volatile double sun = 0.5;
    /** Zustand der Fläche 0..1: wie viel Rost, Algen, Salz, Moos überhaupt da sind. */
    public volatile double rust = 0.6, algae = 0.8, salt = 0.4, moss = 0.1;
    /** Startwert des Rauschens: gleicher seed, gleiches Bild. */
    public volatile int seed = 1885;
    public volatile Quality quality = Quality.MEDIUM;
    /** Größe der Nässekarte in m (Umfang × Höhe der Fläche, die sie abdeckt) und ihre Unterkante in m. */
    public volatile double mapWidthM = 30, mapHeightM = 32, mapBottom = -2;

    // ------------------------------------------------------------------ Zustand

    private double wetLine = Double.NaN, rainWet, time;
    private float[] map = new float[0];
    private int mapW, mapH, version;
    private volatile Snapshot snap;

    public Weathering() { snap = build(); }

    // ------------------------------------------------------------------ Voreinstellungen

    /** Glatte See, trocken, gepflegte Farbe. */
    public static Weathering calm() { return beaufort(1); }
    /** Frische Brise (Bft 5). */
    public static Weathering breeze() { return beaufort(5); }
    /** Stürmischer Wind (Bft 8). */
    public static Weathering gale() { return beaufort(8); }
    /** Orkanartiger Sturm (Bft 11) mit Regen. */
    public static Weathering storm() { Weathering w = beaufort(11); w.rainRate = 6; return w; }

    /** Wind und Seegang nach Beaufort 0..12 (Wellenhöhe auf offener See nach WMO, wahrscheinliche Höhe). */
    public static Weathering beaufort(int bft) {
        int b = Math.max(0, Math.min(12, bft));
        Weathering w = new Weathering();
        w.windSpeed = WIND[b];
        w.waveHeight = WAVE[b];
        return w;
    }

    static final double[] WIND = {0.2, 0.9, 2.4, 4.4, 6.7, 9.3, 12.3, 15.5, 18.9, 22.6, 26.4, 30.5, 34.0};
    static final double[] WAVE = {0.0, 0.1, 0.2, 0.6, 1.0, 2.0, 3.0, 4.0, 5.5, 7.0, 9.0, 11.5, 14.0};

    // ------------------------------------------------------------------ Lauf

    /** Wie hoch Wellen am Bauwerk auflaufen (m über dem Wasserstand). */
    public static double runup(double hs) { return 0.15 + 0.55 * Math.max(0, hs); }

    /** Bis wohin das Spritzwasser reicht (m über dem Wasserstand). */
    public static double sprayTop(double hs, double wind) { return runup(hs) + 1.1 * Math.max(0, hs) + 0.05 * Math.max(0, wind - 8); }

    /**
     * Trocknung in m/h: so schnell sinkt die nasse Zone hinter fallendem Wasser her. Wind und Sonne trocknen, Regen
     * hält nass. Eine Stahlwand im Wind trocknet in einer halben bis zwei Stunden.
     */
    public double dryingRate() {
        double r = 0.25 + 0.035 * windSpeed + 0.6 * clamp(sun, 0, 1);
        return rainRate > 0.1 ? 0 : r;
    }

    /** Einen Schritt weiter (dt in Szenensekunden). */
    public synchronized void step(double dt) {
        time += dt;
        double top = waterLevel + runup(waveHeight);
        if (Double.isNaN(wetLine) || top > wetLine) wetLine = top;
        else wetLine = Math.max(top, wetLine - dryingRate() / 3600 * dt);
        // Regen: 1 mm/h macht in rund 10 min nass, ohne Regen trocknet es in rund 30 min (mit Wind und Sonne schneller)
        if (rainRate > 0.05) rainWet = Math.min(1, rainWet + dt * Math.min(1, rainRate) / 600 + dt * Math.max(0, rainRate - 1) / 1200);
        else rainWet = Math.max(0, rainWet - dt * dryingRate() / 0.5 / 1800);
        // Nässekarte: trocknet wie der Regen
        ensureMap();
        double k = Math.exp(-dt * Math.max(0.05, dryingRate()) / 0.5 / 1200);
        boolean any = false;
        for (int i = 0; i < map.length; i++) {
            if (map[i] > 1e-4f) { map[i] *= (float) k; any = true; }
            else if (map[i] != 0) { map[i] = 0; any = true; }
        }
        if (any) version++;
        snap = build();
    }

    /** Nass auf einen Schlag (Brecher, Gischt, ein Eimer Wasser): Fläche um (u, v) in 0..1, Radius in m, Menge 0..1. */
    public synchronized void wet(double u, double v, double radiusM, double amount) {
        ensureMap();
        double cx = (u - Math.floor(u)) * mapW, cy = clamp(v, 0, 1) * mapH;
        double rx = radiusM / mapWidthM * mapW, ry = radiusM / mapHeightM * mapH;
        int x0 = (int) Math.floor(cx - 2 * rx), x1 = (int) Math.ceil(cx + 2 * rx), y0 = Math.max(0, (int) Math.floor(cy - 2 * ry)),
                y1 = Math.min(mapH - 1, (int) Math.ceil(cy + 2 * ry));
        // Wasser läuft herunter: unterhalb des Treffers wird es länger nass als oberhalb
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double dx = (x + 0.5 - cx) / Math.max(rx, 0.5), dy = (y + 0.5 - cy) / Math.max(ry, 0.5);
                if (dy < 0) dy *= 0.45;
                double g = amount * Math.exp(-(dx * dx + dy * dy));
                int xi = Math.floorMod(x, mapW), i = y * mapW + xi;
                map[i] = (float) Math.min(1, map[i] + g);
            }
        version++;
        snap = build();
    }

    /** Stellt den Zustand sofort ein, als hätte das Wasser lange gestanden (Standbilder, Prüfungen). */
    public synchronized void settle() {
        wetLine = waterLevel + runup(waveHeight);
        rainWet = rainRate > 0.05 ? Math.min(1, rainRate / 1.0) : 0;
        snap = build();
    }

    /** Setzt die nasse Zone (z. B. auf das letzte Hochwasser, wenn das Wasser seither fällt). */
    public synchronized void setWetLine(double y) {
        wetLine = Math.max(y, waterLevel + runup(waveHeight));
        snap = build();
    }

    public double wetLine() { Snapshot s = snap; return s.wetLine; }

    private void ensureMap() {
        Quality q = quality;
        if (map.length != q.mapW * q.mapH) {
            mapW = q.mapW;
            mapH = q.mapH;
            map = new float[mapW * mapH];
            version++;
        }
    }

    private Snapshot build() {
        ensureMap();
        double hs = waveHeight;
        double rad = Math.toRadians(windFromDeg);
        return new Snapshot(waterLevel, meanHighWater, meanLowWater, runup(hs), sprayTop(hs, windSpeed),
                Double.isNaN(wetLine) ? waterLevel + runup(hs) : wetLine, rainWet, Math.sin(rad), -Math.cos(rad),
                clamp(rust, 0, 1), clamp(algae, 0, 1), clamp(salt, 0, 1), clamp(moss, 0, 1), seed,
                clamp(hs / 6, 0, 1), map.clone(), mapW, mapH, version, mapWidthM, mapHeightM, mapBottom);
    }

    /** Der letzte Stand, unveränderlich (für den Zeichenfaden). */
    public Snapshot snapshot() { return snap; }

    /**
     * Der Zustand für Shader und Masken. {@code rainFromX/Z}: waagerechte Richtung, aus der Regen kommt (Einheitsvektor
     * in x Ost, z Süd). {@code spray}: Stärke des Spritzwassers 0..1.
     */
    public record Snapshot(double level, double mhw, double mlw, double runup, double sprayTop, double wetLine, double rainWet,
                           double rainFromX, double rainFromZ, double rust, double algae, double salt, double moss, int seed,
                           double spray, float[] map, int mapW, int mapH, int mapVersion, double mapWidthM, double mapHeightM,
                           double mapBottom) {
        /** Nässe der Karte an (u, v), bilinear wie die Textur. */
        public double mapAt(double u, double v) {
            if (mapW == 0) return 0;
            double x = (u - Math.floor(u)) * mapW - 0.5, y = clamp(v, 0, 1) * mapH - 0.5;
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = x - x0, fy = y - y0;
            return lerp(lerp(m(x0, y0), m(x0 + 1, y0), fx), lerp(m(x0, y0 + 1), m(x0 + 1, y0 + 1), fx), fy);
        }

        private double m(int x, int y) { return map[Math.max(0, Math.min(mapH - 1, y)) * mapW + Math.floorMod(x, mapW)]; }

        public String describe() {
            return String.format(Locale.GERMAN, "Wasser %+.2f m · Auflauf %.1f m · nass bis %+.2f m · Regen %.0f %% · Rost %.0f %% · Algen %.0f %% · Salz %.0f %%",
                    level, runup, wetLine, rainWet * 100, rust * 100, algae * 100, salt * 100);
        }
    }

    public String describe() { return snap.describe(); }

    // ================================================================== Masken (Zeile für Zeile wie weathering.glsl)

    /** Ganzzahl-Hash (wie wthHash im Shader, 32 Bit ohne Vorzeichen). */
    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** Zufall 0..1 für eine Gitterzelle. */
    static double rand(int cx, int cy, int seed) {
        int h = hash(cx * 0x9E3779B1 ^ hash(cy + seed));
        return (h >>> 8) * (1.0 / 16777216.0);
    }

    /** Wertrauschen 0..1 (glatt). */
    public static double noise(double x, double y, int seed) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        int cx = (int) ix, cy = (int) iy;
        double a = rand(cx, cy, seed), b = rand(cx + 1, cy, seed), c = rand(cx, cy + 1, seed), d = rand(cx + 1, cy + 1, seed);
        return lerp(lerp(a, b, ux), lerp(c, d, ux), uy);
    }

    /** Vier Oktaven, 0..1. */
    public static double fbm(double x, double y, int seed) {
        double s = 0, a = 0.5, n = 0;
        for (int i = 0; i < 4; i++) {
            s += a * noise(x, y, seed + i * 101);
            n += a;
            // gedreht und verdoppelt: so fallen die Gitter der Oktaven nicht aufeinander (keine Kästchen)
            double nx = 1.6 * x - 1.2 * y + 17.1, ny = 1.2 * x + 1.6 * y + 3.7;
            x = nx;
            y = ny;
            a *= 0.5;
        }
        return s / n;
    }

    /** Algen: {Bedeckung 0..1, Anteil grüner Schleimalgen 0..1 (sonst braun mit Seepocken)}. */
    public static double[] algae(Snapshot s, double y, double ss, double tt) {
        double z = (y - s.mlw) / Math.max(0.2, s.mhw - s.mlw);
        double ragged = (fbm(ss * 0.9, tt * 2.5, s.seed + 11) - 0.5) * 0.35;
        double band = smoothstep(-0.45, -0.15, z) * (1 - smoothstep(0.55, 0.85, z + ragged));
        double spot = smoothstep(0.25, 0.6, fbm(ss * 1.7 + 13.1, tt * 1.7, s.seed + 23));
        double cov = s.algae * band * lerp(0.55, 1.0, spot);
        // grüner Schleim (Meersalat) in einem Streifen um das Niedrigwasser, darunter und darüber braun (Tang, Seepocken)
        double green = smoothstep(-0.3, -0.08, z) * (1 - smoothstep(0.05, 0.35, z + ragged * 0.5));
        return new double[]{cov, green};
    }

    /** Rost: Läufer unter Kanten (below = Abstand unter der Kante in m) und Flecken; sus = Anfälligkeit 0..1. */
    public static double rust(Snapshot s, double ss, double tt, double below, double sus) {
        double len = lerp(0.2, 1.6, noise(ss * 1.3, 7.7, s.seed + 31));
        double stripes = smoothstep(0.55, 0.85, noise(ss * 7.0, tt * 0.35, s.seed + 37));
        double streak = Math.exp(-Math.max(below, 0) / len) * stripes;
        double pn = fbm(ss * 2.2 + 31.0, tt * 1.6, s.seed + 41);
        double spot = smoothstep(0.70 - 0.14 * sus, 0.86 - 0.14 * sus, pn);
        return s.rust * sus * clamp(Math.max(streak * 0.85, spot), 0, 1);
    }

    /**
     * Salz: weiße Kruste über dem Auflauf, wo es wieder trocken ist (die Trockenheit kommt in {@link #surface} dazu);
     * auf waagerechten Flächen spült der Regen das meiste ab (ny = Normale nach oben).
     */
    public static double salt(Snapshot s, double y, double ny, double ss, double tt) {
        double base = s.level + s.runup;
        double zone = smoothstep(base - 0.2, base + 0.6, y) * (1 - smoothstep(s.level + s.sprayTop + 0.5, s.level + s.sprayTop + 3.0, y));
        return s.salt * zone * (1 - 0.75 * smoothstep(0.5, 0.9, ny)) * smoothstep(0.52, 0.82, fbm(ss * 4.0 + 5.0, tt * 4.0, s.seed + 53));
    }

    /** Moos auf Flächen, die nach oben zeigen, über dem Hochwasser. */
    public static double moss(Snapshot s, double ny, double y, double ss, double tt) {
        double up = smoothstep(0.35, 0.8, ny);
        double above = smoothstep(s.mhw + 0.5, s.mhw + 1.5, y);
        return s.moss * up * above * smoothstep(0.45, 0.7, fbm(ss * 3.1 + 17.0, tt * 3.1, s.seed + 59));
    }

    /**
     * Nässe 0..1: unter Wasser, im Auflauf, in der Zone, die das fallende Wasser noch nicht freigegeben hat, im
     * Spritzwasser (fleckig), durch Regen (die Wetterseite mehr) und aus der Nässekarte (mapVal).
     */
    public static double wetness(Snapshot s, double y, double nx, double ny, double nz, double ss, double tt, double mapVal) {
        if (y < s.level) return 1;
        double jitter = (noise(ss * 2.1, tt * 0.7, s.seed + 61) - 0.5) * 0.3;
        double top = s.level + s.runup;
        double surge = 1 - smoothstep(top - 0.15, top + 0.25, y + jitter);
        double line = 1 - smoothstep(s.wetLine - 0.2, s.wetLine + 0.2, y + jitter);
        double sprayZone = smoothstep(top - 0.3, top + 0.3, y) * (1 - smoothstep(s.level + s.sprayTop - 0.5, s.level + s.sprayTop + 0.5, y));
        double spray = s.spray * sprayZone * smoothstep(0.45, 0.8, noise(ss * 1.5, tt * 1.5 + 9.0, s.seed + 67));
        double face = Math.max(0, nx * s.rainFromX + nz * s.rainFromZ);
        double rain = s.rainWet * clamp(0.35 + 0.65 * face + 0.4 * Math.max(ny, 0), 0, 1);
        return clamp(Math.max(Math.max(surge, line), Math.max(Math.max(spray, rain), mapVal)), 0, 1);
    }

    /** Ergebnis für eine Fläche: Farbe (linear), Rauheit, Nässe und die einzelnen Masken. */
    public record Surface(double r, double g, double b, double rough, double wet, double rust, double algae, double salt, double moss) { }

    /**
     * Wie wthApply im Shader: legt Rost, Salz, Algen und Moos über die Grundfarbe und macht die Fläche nass (dunkler,
     * glatter). porosity 0..1: wie stark Nässe abdunkelt (Farbe wenig, Stein und Rost viel).
     */
    public static Surface surface(Snapshot s, double[] albedo, double rough, double porosity, double[] pos, double[] n, double ss, double tt,
                                  double below, double sus, double mapVal) {
        double wet = wetness(s, pos[1], n[0], n[1], n[2], ss, tt, mapVal);
        double ru = rust(s, ss, tt, below, sus);
        double[] al = algae(s, pos[1], ss, tt);
        double sa = salt(s, pos[1], n[1], ss, tt) * (1 - wet);
        double mo = moss(s, n[1], pos[1], ss, tt);
        double tone = noise(ss * 3.0, tt * 3.0, s.seed + 71);
        double[] c = albedo.clone();
        double[] rustCol = mix3(new double[]{0.22, 0.075, 0.03}, new double[]{0.09, 0.04, 0.02}, tone);
        c = mix3(c, rustCol, ru);
        c = mix3(c, new double[]{0.62, 0.61, 0.58}, sa * 0.8);
        double[] algCol = mix3(new double[]{0.05, 0.04, 0.025}, new double[]{0.05, 0.17, 0.025}, al[1]);
        c = mix3(c, algCol, al[0]);
        c = mix3(c, new double[]{0.10, 0.16, 0.05}, mo);
        double por = clamp(porosity + 0.5 * ru + 0.4 * al[0], 0, 1);
        double dark = 1 - wet * por * 0.55;
        double rgh = lerp(rough, Math.max(0.07, rough * 0.35), wet * 0.9);
        rgh = lerp(rgh, Math.max(rgh, 0.75), ru * 0.8 * (1 - wet));
        return new Surface(c[0] * dark, c[1] * dark, c[2] * dark, rgh, wet, ru, al[0], sa, mo);
    }

    // ------------------------------------------------------------------ Helfer

    static double[] mix3(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    public static double smoothstep(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
