package com.dan.soundscape;

/**
 * Eine Werkstatt, die von selbst arbeitet (seit Haithabu Phase 14): Schmied, Bronzegießer, Zimmermann, Säger oder
 * Kammmacher an einem Ort. Solange {@link #active} ist, schlägt, hackt oder sägt sie in ihrem Takt: Reihen von
 * Schlägen oder Strichen, dazwischen Pausen (Eisen in die Esse, ein neues Stück, Luft holen). Die App sagt nur, wann
 * gearbeitet wird; Takt, Reihen und Pausen wählt das Package [G].
 * <pre>
 * Gewerk        Klang             Takt (s)     Reihe       Pause (s)
 * Schmied       Hammer, Amboss    0,55–0,75    5–14        4–14
 * Bronzegießer  Klopfen           0,22–0,32    8–20        5–18
 * Zimmermann    Beil, Dechsel     1,0–1,4      5–16        4–15
 * Säger         Säge              0,5–0,62     10–30       6–20
 * Kammmacher    feine Säge        0,30–0,36    8–24        5–20
 * </pre>
 */
public final class Workshop {
    /** Die Gewerke. */
    public enum Craft {
        SCHMIED(Soundscape.Kind.HAMMER, 0.55, 0.75, 5, 14, 4, 14),
        BRONZE(Soundscape.Kind.KLOPFEN, 0.22, 0.32, 8, 20, 5, 18),
        ZIMMERER(Soundscape.Kind.BEIL, 1.0, 1.4, 5, 16, 4, 15),
        SAEGE(Soundscape.Kind.SAEGE, 0.5, 0.62, 10, 30, 6, 20),
        KAMMMACHER(Soundscape.Kind.FEINSAEGE, 0.30, 0.36, 8, 24, 5, 20);
        public final Soundscape.Kind sound;
        final double beat0, beat1, pause0, pause1;
        final int run0, run1;
        Craft(Soundscape.Kind k, double b0, double b1, int r0, int r1, double p0, double p1) { sound = k; beat0 = b0; beat1 = b1; run0 = r0; run1 = r1; pause0 = p0; pause1 = p1; }
    }

    public final String name;
    public final Craft craft;
    public volatile double x, y, z;
    /** Wird gearbeitet? Lautstärke 0..1 (1 = wie gerechnet). */
    public volatile boolean active = true;
    public volatile double strength = 1;

    // Zustand (nur im Faden des Mischers)
    private Dsp.Rng rng;
    private double next = -1, beat;
    private int left, count;
    private final float[][] cache = new float[8][];
    private final double[] cacheDb = new double[8];
    private final String[] cacheName = new String[8];
    private long built = Long.MIN_VALUE;
    private double tone;
    long strikes;

    public Workshop(String name, Craft craft, double x, double y, double z) {
        this.name = name; this.craft = craft; this.x = x; this.y = y; this.z = z;
    }

    /** Schläge oder Striche seit dem Start. */
    public long strikes() { return strikes; }

    /** Plant die Schläge im Stück [now, now + dt) und reicht sie mit ihrem Versatz an den Mischer. */
    void schedule(Soundscape s, double now, double dt, int sr, long seed) {
        if (rng == null || built != seed + sr) {
            rng = new Dsp.Rng(seed * 92821 + name.hashCode());
            java.util.Arrays.fill(cache, null);
            tone = rng.uniform();
            built = seed + sr;
            next = now + 1 + rng.uniform() * craft.pause1 * 0.5;
            left = 0;
        }
        if (!active) { next = Math.max(next, now + 1 + rng.uniform() * 3); left = 0; return; }
        while (next < now + dt) {
            if (left <= 0) {
                left = craft.run0 + (int) (rng.uniform() * (craft.run1 - craft.run0 + 1));
                beat = craft.beat0 + (craft.beat1 - craft.beat0) * rng.uniform();
            }
            double lead = Math.max(0, next - now);
            int slot = (int) (rng.uniform() * cache.length);
            boolean push = (count & 1) == 0;
            if (craft.sound == Soundscape.Kind.SAEGE || craft.sound == Soundscape.Kind.FEINSAEGE) slot = (slot & ~1) | (push ? 1 : 0);
            if (cache[slot] == null) {
                Calls.Made m = Crafts.make(craft.sound, 1, craft.sound == Soundscape.Kind.HAMMER ? tone : slot & 1, rng.next(), sr);
                cache[slot] = m.data();
                cacheDb[slot] = m.level1m();
                cacheName[slot] = m.name();
            }
            double lv = cacheDb[slot] + 6 * Math.log10(Math.max(0.05, strength)) + (rng.uniform() - 0.5) * 3;
            Voice v = new Voice.Buffered(cacheName[slot] + " (" + name + ")", x, y, z, lv, cache[slot], false);
            v.lead = lead;
            s.enqueueVoice(v);
            strikes++;
            count++;
            left--;
            next += left > 0 ? beat * (0.96 + 0.08 * rng.uniform()) : craft.pause0 + (craft.pause1 - craft.pause0) * rng.uniform();
        }
    }
}
