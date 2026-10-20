package com.dan.figure;

import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Die Kleidung einer Figur als Farben je Fläche des Netzes ({@link FigureMesh}): Haut, Jacke, Hose, Knie, Unterschenkel,
 * Fuß, Mütze, Haar, Rucksack, Unterarm. Was nackt ist, trägt die Farbe der Haut (barfuß, kurze Hose, kurze Ärmel).
 * Mütze und Rucksack gibt es oder nicht ({@link #hat}, {@link #pack}). Farben linear, wie sie das Licht der App
 * erwartet. Werte nach Fotos von Wattwanderungen gerundet [G].
 */
public final class Outfit {
    public static final int SKIN = 0, JACKET = 1, TROUSERS = 2, KNEE = 3, SHIN = 4, FOOT = 5, HAT = 6, HAIR = 7, PACK = 8, FOREARM = 9, SLOTS = 10;
    /** Farbe je Fläche (linear, 0..1), {@link #SLOTS} mal drei Werte. */
    public final float[] rgb = new float[SLOTS * 3];
    public boolean hat, pack;
    /** Kurzer Name für die Prüfung und das HUD („barfuß, kurze Hose“). */
    public String label = "";
    /** Barfuß (für die Spuren im Watt: Zehen statt Profil). */
    public boolean barefoot;

    public Outfit set(int slot, double r, double g, double b) {
        rgb[slot * 3] = (float) r; rgb[slot * 3 + 1] = (float) g; rgb[slot * 3 + 2] = (float) b;
        return this;
    }

    public Outfit copy(int to, int from) { return set(to, rgb[from * 3], rgb[from * 3 + 1], rgb[from * 3 + 2]); }

    public double[] color(int slot) { return new double[]{rgb[slot * 3], rgb[slot * 3 + 1], rgb[slot * 3 + 2]}; }

    // ------------------------------------------------------------------ Farben [G]

    static final double[][] SKINS = {{0.52, 0.36, 0.27}, {0.45, 0.30, 0.22}, {0.58, 0.42, 0.33}, {0.30, 0.19, 0.13}, {0.15, 0.09, 0.06}};
    static final double[][] HAIRS = {{0.02, 0.015, 0.01}, {0.10, 0.06, 0.03}, {0.30, 0.20, 0.09}, {0.22, 0.10, 0.04}, {0.30, 0.29, 0.28}};
    /** Regenjacken und Windjacken: Rot, Gelb, Blau, Grün, Orange, Marine, Grau, Türkis. */
    static final double[][] JACKETS = {{0.42, 0.04, 0.035}, {0.60, 0.42, 0.02}, {0.03, 0.10, 0.35}, {0.05, 0.20, 0.06}, {0.62, 0.17, 0.02},
            {0.02, 0.03, 0.09}, {0.16, 0.17, 0.18}, {0.02, 0.25, 0.27}};
    /** Hemden und T-Shirts im Sommer: Weiß, Hellblau, Rosa, Gelb, Grün, Grau. */
    static final double[][] SHIRTS = {{0.70, 0.70, 0.68}, {0.30, 0.45, 0.62}, {0.65, 0.30, 0.35}, {0.70, 0.60, 0.20}, {0.15, 0.35, 0.15}, {0.35, 0.35, 0.36}};
    static final double[][] TROUSERS_C = {{0.04, 0.05, 0.08}, {0.10, 0.10, 0.10}, {0.20, 0.17, 0.11}, {0.05, 0.08, 0.16}, {0.18, 0.19, 0.20}};
    static final double[][] BOOTS = {{0.03, 0.06, 0.03}, {0.02, 0.02, 0.022}, {0.35, 0.05, 0.03}, {0.03, 0.07, 0.20}};

    static double[] pick(double[][] a, SplittableRandom r) { return a[r.nextInt(a.length)]; }

    private Outfit base(SplittableRandom r, boolean child) {
        double[] s = pick(SKINS, r), h = pick(HAIRS, r);
        if (child && h == HAIRS[4]) h = HAIRS[2];
        set(SKIN, s[0], s[1], s[2]);
        set(HAIR, h[0], h[1], h[2]);
        return this;
    }

    /**
     * Kleidung nach Monat und Rolle (Zufall aus r): Juni bis August meist barfuß in kurzer oder hochgekrempelter Hose
     * und Hemd, gern mit Rucksack; Frühjahr und Herbst Regenjacke, Gummistiefel oder Watt-Socken; Winter dicke Jacke,
     * Mütze, Stiefel. Der Wattführer trägt Gelb oder Rot und hat immer einen Rucksack.
     */
    public static Outfit forMonth(int month, SplittableRandom r, boolean guide, boolean child) {
        Outfit o = new Outfit().base(r, child);
        boolean summer = month >= 6 && month <= 8, mild = month == 5 || month == 9, winter = month <= 2 || month >= 11;
        double[] jacket = guide ? (r.nextBoolean() ? JACKETS[1] : JACKETS[0]) : pick(JACKETS, r);
        double[] trousers = pick(TROUSERS_C, r), boot = pick(BOOTS, r);
        if (summer || (mild && r.nextDouble() < 0.5)) {
            double[] shirt = r.nextDouble() < 0.6 ? pick(SHIRTS, r) : jacket;
            o.set(JACKET, shirt[0], shirt[1], shirt[2]);
            boolean shortSleeve = shirt != jacket && r.nextDouble() < 0.7;
            if (shortSleeve) o.copy(FOREARM, SKIN); else o.copy(FOREARM, JACKET);
            o.set(TROUSERS, trousers[0], trousers[1], trousers[2]);
            boolean shorts = r.nextDouble() < 0.55;
            o.barefoot = r.nextDouble() < (summer ? 0.8 : 0.5);
            if (shorts) { o.copy(KNEE, SKIN); o.copy(SHIN, SKIN); }
            else { o.copy(KNEE, TROUSERS); o.copy(SHIN, SKIN); }       // hochgekrempelt
            if (o.barefoot) o.copy(FOOT, SKIN); else { o.set(FOOT, 0.05, 0.05, 0.06); o.set(SHIN, 0.04, 0.04, 0.05); }   // Watt-Socken aus Neopren
            o.label = (o.barefoot ? "barfuß" : "Watt-Socken") + ", " + (shorts ? "kurze Hose" : "hochgekrempelt");
            o.hat = r.nextDouble() < 0.25;
            if (o.hat) o.set(HAT, 0.65, 0.62, 0.52);                 // Sonnenhut
        } else {
            o.set(JACKET, jacket[0], jacket[1], jacket[2]);
            o.copy(FOREARM, JACKET);
            o.set(TROUSERS, trousers[0], trousers[1], trousers[2]);
            o.copy(KNEE, TROUSERS);
            o.set(SHIN, boot[0], boot[1], boot[2]);                    // Gummistiefel bis unters Knie
            o.copy(FOOT, SHIN);
            o.label = "Regenjacke, Gummistiefel";
            o.hat = winter || r.nextDouble() < 0.3;
            if (o.hat) { double[] c = pick(JACKETS, r); o.set(HAT, c[0], c[1], c[2]); }
            if (winter) o.label = "Winterjacke, Mütze, Stiefel";
        }
        o.pack = guide || (!child && r.nextDouble() < 0.45);
        double[] p = pick(JACKETS, r);
        o.set(PACK, p[0] * 0.7, p[1] * 0.7, p[2] * 0.7);
        if (!o.hat) o.copy(HAT, HAIR);
        if (guide) o.label = "Wattführer: " + o.label;
        return o;
    }

    /** Wolle des 14. Jahrhunderts: naturbraun, grau, ungefärbt hell, Waid-Blau, Krapp-Rot, Reseda-Gelb [G]. */
    static final double[][] WOOL = {{0.16, 0.11, 0.07}, {0.22, 0.20, 0.17}, {0.45, 0.40, 0.31}, {0.06, 0.10, 0.20}, {0.32, 0.06, 0.04}, {0.42, 0.34, 0.10}, {0.10, 0.08, 0.06}};

    /**
     * Seit Rungholt Phase 11: Kleidung von 1362 (Zufall aus r): Kittel aus Wolle bis über das Knie (Ärmel lang), Beinlinge,
     * Lederschuhe, oft eine Gugel (Kapuze mit Schulterkragen) oder eine Leinenhaube; im Sommer barfuß bei der Arbeit.
     * Kinder in Hellem. Ohne Rucksack.
     */
    public static Outfit medieval(int month, SplittableRandom r, boolean child) {
        Outfit o = new Outfit().base(r, child);
        boolean summer = month >= 5 && month <= 9;
        double[] tunic = child ? WOOL[2] : pick(WOOL, r), hose = r.nextDouble() < 0.6 ? WOOL[0] : pick(WOOL, r);
        o.set(JACKET, tunic[0], tunic[1], tunic[2]).copy(FOREARM, JACKET);
        o.copy(TROUSERS, JACKET).copy(KNEE, JACKET);             // der Kittel reicht bis über das Knie
        o.set(SHIN, hose[0] * 0.9, hose[1] * 0.9, hose[2] * 0.9);
        o.barefoot = summer && r.nextDouble() < (child ? 0.8 : 0.35);
        if (o.barefoot) { o.copy(FOOT, SKIN); o.copy(SHIN, SKIN); }
        else o.set(FOOT, 0.09, 0.055, 0.03);                    // Bundschuh aus Leder
        o.hat = !child && r.nextDouble() < (summer ? 0.45 : 0.85);
        if (o.hat) {
            double[] h = r.nextDouble() < 0.4 ? new double[]{0.55, 0.52, 0.44} : pick(WOOL, r);   // Leinenhaube oder Gugel
            o.set(HAT, h[0], h[1], h[2]);
        } else o.copy(HAT, HAIR);
        o.pack = false;
        o.set(PACK, 0, 0, 0);
        o.label = (child ? "Kind, " : "") + "Kittel" + (o.hat ? ", Gugel" : "") + (o.barefoot ? ", barfuß" : ", Bundschuhe");
        return o;
    }

    /** Ölzeug und Westen der Grabung: Gelb, Orange (Warnweste), Marine, Oliv [G]. */
    static final double[][] FIELD = {{0.62, 0.45, 0.02}, {0.70, 0.22, 0.02}, {0.03, 0.05, 0.14}, {0.10, 0.12, 0.05}};

    /**
     * Seit Rungholt Phase 13: Archäologinnen und Archäologen im Watt [G]: Wathose oder Ölhose bis zur Brust in Oliv oder
     * Schwarz über Gummistiefeln, darüber Ölzeug, Warnweste oder Fleece, oft eine Mütze, manchmal ein Rucksack.
     */
    public static Outfit archaeologist(int month, SplittableRandom r) {
        Outfit o = new Outfit().base(r, false);
        boolean summer = month >= 6 && month <= 8;
        double[] top = pick(FIELD, r), wader = r.nextBoolean() ? new double[]{0.06, 0.08, 0.04} : new double[]{0.025, 0.025, 0.03};
        o.set(JACKET, top[0], top[1], top[2]);
        if (summer && r.nextDouble() < 0.5) o.copy(FOREARM, SKIN); else o.copy(FOREARM, JACKET);
        o.set(TROUSERS, wader[0], wader[1], wader[2]).copy(KNEE, TROUSERS).copy(SHIN, TROUSERS).copy(FOOT, TROUSERS);
        o.hat = !summer || r.nextDouble() < 0.5;
        if (o.hat) { double[] c = r.nextBoolean() ? FIELD[2] : new double[]{0.30, 0.30, 0.28}; o.set(HAT, c[0], c[1], c[2]); } else o.copy(HAT, HAIR);
        o.pack = r.nextDouble() < 0.3;
        o.set(PACK, 0.08, 0.08, 0.08);
        o.label = "Grabung: " + (top == FIELD[1] ? "Warnweste" : "Ölzeug") + ", Wathose";
        return o;
    }

    /** Ein Wanderer allein (wie in Phase 7): rote Regenjacke, dunkle Hose, grüne Gummistiefel, blaue Mütze. */
    public static Outfit walker() {
        Outfit o = new Outfit();
        o.set(SKIN, 0.50, 0.36, 0.28).set(HAIR, 0.10, 0.06, 0.03).set(JACKET, 0.42, 0.045, 0.035).copy(FOREARM, JACKET)
                .set(TROUSERS, 0.045, 0.05, 0.065).copy(KNEE, TROUSERS).set(SHIN, 0.035, 0.075, 0.04).copy(FOOT, SHIN).set(HAT, 0.06, 0.10, 0.20)
                .set(PACK, 0.1, 0.1, 0.1);
        o.hat = true;
        o.label = "Regenjacke, Gummistiefel";
        return o;
    }

    @Override public String toString() { return label + (hat ? ", Mütze" : "") + (pack ? ", Rucksack" : ""); }

    public String rgbString() { return String.format(Locale.ROOT, "%s", Arrays.toString(rgb)); }
}
