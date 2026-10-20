package com.dan.soundscape;

/**
 * Ein Feuer, das man hört (seit Haithabu Phase 14): Herd, Esse, Feuerstelle, Fackel oder ein brennendes Haus. Größe
 * 0..1 (0,1 Fackel, 0,25 Herd, 0,4 Esse, 1 brennendes Haus): Knistern und Knacken (kurze helle Klicks und ein paar
 * tiefere Risse), mit der Größe dazu das Fauchen und Brausen der Flammen (tiefes Rauschen, das flackert).
 * <p>
 * Pegel in 1 m [G]: Knistern 58 + 30 · Größe dB, Brausen 40 + 56 · Größe dB (ein Herd 52, ein brennendes Haus 96);
 * Klicks je s 3 + 45 · Größe². {@link #indoor}: das Feuer steht im selben Raum wie das Ohr (nur zu hören, wenn das
 * Ohr drinnen ist, dann ohne die Wand).
 */
public final class Blaze {
    public final String name;
    public volatile double x, y, z, size;
    public volatile boolean indoor;

    public Blaze(String name, double x, double y, double z, double size, boolean indoor) {
        this.name = name; this.x = x; this.y = y; this.z = z; this.size = size; this.indoor = indoor;
    }

    // Zustand (Faden des Mischers)
    final Soundscape.Pings pops = new Soundscape.Pings();
    private Dsp.Brown roar;
    private Dsp.Pink hiss;
    private final Dsp.Biquad roarLp = new Dsp.Biquad(), roarBp = new Dsp.Biquad(), hissHp = new Dsp.Biquad(), air = new Dsp.Biquad();
    private double flicker;
    private Dsp.Rng rng;
    private int builtSr;
    private final float[] scratchL = new float[Soundscape.BLOCK], scratchR = new float[Soundscape.BLOCK];

    /** Addiert in l, r; Verstärkung je Seite, Abstand (m), Grenze der Luft; gibt die Energie zurück. */
    double render(float[] l, float[] r, int n, int sr, long seed, float gl, float gr, double d, double airHz) {
        if (builtSr != sr) {
            rng = new Dsp.Rng(seed * 4099 + name.hashCode());
            roar = new Dsp.Brown(seed * 3 + name.hashCode());
            hiss = new Dsp.Pink(seed * 5 + name.hashCode());
            roarLp.lowpass(260, 0.7, sr);
            roarBp.bandpass(650, 0.8, sr);
            hissHp.highpass(2500, 0.7, sr);
            pops.clear();
            builtSr = sr;
        }
        air.lowpass(airHz, 0.6, sr);
        double s = Math.max(0, Math.min(1.2, size)), dt = n / (double) sr;
        if (s < 0.01) { pops.render(l, r, 0, sr); return 0; }
        double att = 20 * Math.log10(Math.max(1, d));
        flicker += -flicker * dt / 0.25 + 0.5 * Math.sqrt(2 * dt / 0.25) * rng.gauss();
        float aRoar = (float) (Dsp.amp(40 + 56 * s - att) * Math.exp(0.35 * flicker));
        float aHiss = (float) (Dsp.amp(34 + 30 * s - att) * 0.8);
        double popDb = 58 + 30 * s - att;
        // Klicks: hell und kurz, ab und zu ein tieferer Riss
        pops.spawnPan(3 + 45 * s * s, dt, 1500, 6500, 0.0006, 0.0025, Dsp.amp(popDb) * 2.2, 0.7071f, 0.7071f, rng, sr);
        if (rng.uniform() < dt * (0.3 + 2 * s)) pops.one(500 + 900 * rng.uniform(), 0.004 + 0.005 * rng.uniform(), Dsp.amp(popDb + 4) * 2, 0.7071f, 0.7071f, rng, sr);
        float[] tl = scratchL, tr = scratchR;
        java.util.Arrays.fill(tl, 0, n, 0);
        java.util.Arrays.fill(tr, 0, n, 0);
        double e = pops.render(tl, tr, n, sr) * 0.5;
        for (int i = 0; i < n; i++) {
            float b = roar.next();
            float y = (roarLp.process(b) * 1.3f + roarBp.process(b) * 0.5f) * aRoar + hissHp.process(hiss.next()) * aHiss;
            float pl = air.process((tl[i] + tr[i]) * 0.7071f);
            l[i] += (y + pl) * gl;
            r[i] += (y + pl) * gr;
            e += y * y;
        }
        return e;
    }
}
