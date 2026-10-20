package com.dan.hearth;

import java.util.Locale;

/**
 * Ein Feuer, das brennt, wie es soll: Herdfeuer, offene Feuerstelle, Fackel, Schmiedeesse, Talglampe. Es hat eine
 * Wärmeleistung, eine Flammenhöhe daraus, ein flackerndes Licht (Lichtstrom und Farbe je Augenblick, aus Zufall und
 * Zeit, ohne Zustand) und Rauch. Anzünden und Ausgehen dauern ihre Zeit ({@link #step}).
 * <p>
 * Flammenhöhe nach Heskestad ohne das Durchmesserglied: L = 0,23 · Q<sup>2/5</sup> (Q in kW, L in m): Herdfeuer
 * 10 kW → 0,6 m, Fackel 3 kW → 0,4 m, ein Haus mit 8 MW → 8 m. Licht: Lichtstrom ≈ 150 · Q<sup>0,9</sup> lm
 * (Herdfeuer gut 1 000 lm wie ein Lagerfeuer), Farbe eines Holzfeuers um 1 800 K [G].
 */
public final class Fire {
    /** Arten: Wärmeleistung (kW), Flackern (Anteil), Rauch 0..1, Schwärze 0..1, sichtbare Flamme. */
    public enum Kind {
        HERD("Herdfeuer", 10, 0.14, 0.8, 0.08, false),
        FEUERSTELLE("Feuerstelle", 25, 0.16, 0.9, 0.1, true),
        FACKEL("Fackel", 3, 0.24, 0.35, 0.45, true),
        SCHMIEDE("Esse", 30, 0.10, 0.7, 0.35, true),
        LAMPE("Talglampe", 0.08, 0.10, 0.05, 0.6, true);
        public final String label;
        public final double heatKw, flicker, smoke, black;
        public final boolean visible;
        Kind(String label, double heatKw, double flicker, double smoke, double black, boolean visible) {
            this.label = label; this.heatKw = heatKw; this.flicker = flicker; this.smoke = smoke; this.black = black; this.visible = visible;
        }
    }

    /** Farbe eines Holzfeuers (≈ 1 800 K), linear, auf Leuchtdichte 1 genormt. */
    public static final double[] FLAME_RGB = norm(new double[]{1.0, 0.47, 0.10});
    /** Farbe der Glut (≈ 1 000 K, tiefrot), linear, auf Leuchtdichte 1 genormt (seit Haithabu Phase 11). */
    public static final double[] EMBER_RGB = norm(new double[]{1.0, 0.16, 0.012});
    /** Lichtstrom einer vollen Glut je Größe 1 (lm) und ihre Wärme (kW) [G: ein Glutbett glimmt schwach]. */
    public static final double EMBER_LUMEN = 30, EMBER_KW = 1.2;

    public final Kind kind;
    public final double x, y, z;
    public final int seed;
    /** Größe (1 = wie die Art), etwa für ein großes Feuer am Strand. */
    public double scale = 1;
    /** Soll es brennen? {@link #step} führt {@link #level} dorthin. */
    public boolean lit = true;
    /** Wie stark es gerade brennt 0..1 (Anzünden in rund 20 s, Ausgehen in rund 60 s). */
    public double level = 1;

    public Fire(Kind kind, double x, double y, double z, int seed) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.seed = seed;
    }

    /**
     * Glut 0..1 (seit Haithabu Phase 11): wächst, solange es brennt (in rund 10 min), und glimmt nach dem Ausgehen
     * unter der Asche lange nach (Zeitkonstante 3 h); so bleibt in der Nacht ein rotes Glutbett, aus dem man am
     * Morgen das Feuer wieder anfacht [G].
     */
    public double glow;

    /** Anzünden und Ausgehen um dt weiter; die Glut wächst und verglimmt. */
    public void step(double dt) {
        if (dt <= 0) return;
        double target = lit ? 1 : 0, tau = lit ? 7 : 20;
        level += (target - level) * (1 - Math.exp(-dt / tau));
        if (!lit && level < 0.01) level = 0;
        if (level > 0.3) glow += (1 - glow) * (1 - Math.exp(-dt / 600));
        else glow *= Math.exp(-dt / 10_800);
        if (glow < 1e-3) glow = 0;
    }

    /** Wärmeleistung jetzt (kW). */
    public double heatKw() { return kind.heatKw * scale * level; }

    /** Flammenhöhe (m) nach Heskestad ohne Durchmesserglied. */
    public static double flameHeight(double kw) { return kw <= 0 ? 0 : 0.23 * Math.pow(kw, 0.4); }

    public double flameHeight() { return flameHeight(heatKw()); }

    /** Lichtstrom (lm) eines Feuers mit dieser Leistung, ohne Flackern. */
    public static double lumenOf(double kw) { return kw <= 0 ? 0 : 150 * Math.pow(kw, 0.9); }

    /**
     * Flackern zur Zeit t: um 1 schwankend, aus drei Wellen und einem Rauschen; Esse mit dem Takt des Blasebalgs.
     * Ohne Zustand, gleiche Zeit, gleicher Wert.
     */
    public double flicker(double t) {
        double p1 = rnd(seed, 1) * 6.283, p2 = rnd(seed, 2) * 6.283, p3 = rnd(seed, 3) * 6.283;
        double f = kind.flicker;
        double w = 0.5 * Math.sin(2 * Math.PI * 1.3 * t + p1) + 0.3 * Math.sin(2 * Math.PI * 3.7 * t + p2) + 0.2 * Math.sin(2 * Math.PI * 7.9 * t + p3);
        w += 0.6 * (valueNoise(t * 9 + seed * 0.37) - 0.5);
        double v = 1 + f * w;
        if (kind == Kind.SCHMIEDE) v *= 1 + 0.3 * Math.max(0, Math.sin(2 * Math.PI * 0.4 * t + p1));
        return Math.max(0.2, v);
    }

    /** Lichtstrom jetzt (lm), mit Flackern. */
    public double lumen(double t) { return lumenOf(heatKw()) * flicker(t); }

    /** Lichtstärke jetzt (cd, rundum gleich): RGB, linear; Flamme und Glut. */
    public double[] intensity(double t) {
        double cd = lumen(t) / (4 * Math.PI);
        double warm = kind == Kind.SCHMIEDE ? 1.15 : 1;
        double ce = emberLumen(t) / (4 * Math.PI);
        return new double[]{FLAME_RGB[0] * cd * warm + EMBER_RGB[0] * ce, FLAME_RGB[1] * cd + EMBER_RGB[1] * ce, FLAME_RGB[2] * cd / warm + EMBER_RGB[2] * ce};
    }

    /** Lichtstrom der Glut (lm): nur wo keine Flamme mehr brennt, langsam atmend. */
    public double emberLumen(double t) {
        if (glow <= 0) return 0;
        return EMBER_LUMEN * scale * glow * (1 - level) * (0.9 + 0.1 * Math.sin(2 * Math.PI * 0.2 * t + rnd(seed, 4) * 6.283));
    }

    /** Wärme der Glut (kW), wo keine Flamme mehr brennt. */
    public double emberKw() { return EMBER_KW * scale * glow * (1 - level); }

    /** Die Glut als Strahler (flach am Boden), oder null. */
    public Flame ember() {
        if (glow < 0.03 || kind == Kind.FACKEL || kind == Kind.LAMPE) return null;
        double w = (kind == Kind.SCHMIEDE ? 0.35 : 0.42) * Math.sqrt(scale);
        return new Flame(x, y, z, w, 0.05, glow * (1 - 0.6 * level), seed, Flame.EMBER);
    }

    /** Wo das Licht sitzt: über der Glut, ein Drittel der Flammenhöhe. */
    public double[] lightPosition() { return new double[]{x, y + Math.max(0.05, flameHeight() * 0.35), z}; }

    /** Die sichtbare Flamme als Strahler für {@code gl/HearthGl}, oder null (Herd im Haus, aus). */
    public Flame flame() {
        if (!kind.visible || level < 0.02) return null;
        double h = flameHeight(), w = kind == Kind.FACKEL ? 0.18 : kind == Kind.LAMPE ? 0.02 : kind == Kind.SCHMIEDE ? 0.5 : 0.45 * Math.sqrt(scale);
        int k = kind == Kind.SCHMIEDE ? Flame.FORGE : kind == Kind.FACKEL ? Flame.TORCH : Flame.CAMPFIRE;
        return new Flame(x, y, z, w, kind == Kind.LAMPE ? 0.03 : h, level, seed, k);
    }

    /** Rauch 0..1 und Schwärze 0..1. */
    public double smoke() { return kind.smoke * level; }
    public double blackness() { return kind.black; }

    /** „Feuerstelle · 25 kW · Flamme 0,9 m · 1 900 lm“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%s · %.0f kW · Flamme %.1f m · %,.0f lm", kind.label, heatKw(), flameHeight(), lumenOf(heatKw()));
    }

    // ------------------------------------------------------------------ Zufall

    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    static double rnd(int seed, int k) { return (hash(hash(k) ^ seed) >>> 8) * (1.0 / 16777216.0); }

    static double valueNoise(double t) {
        double i = Math.floor(t), f = t - i;
        double a = rnd(0x51ed, (int) (long) i), b = rnd(0x51ed, (int) (long) i + 1);
        double u = f * f * (3 - 2 * f);
        return a + (b - a) * u;
    }

    static double[] norm(double[] c) {
        double l = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
        return new double[]{c[0] / l, c[1] / l, c[2] / l};
    }
}
