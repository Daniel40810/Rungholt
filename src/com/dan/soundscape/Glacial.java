package com.dan.soundscape;

/**
 * Seit Glacier Phase 12: Klänge des Eises, beim Auslösen einmal gerechnet wie {@link Calls} und {@link Crafts}. Alles
 * erzeugt und nach Gehör und Beschreibungen gebaut [G]: Wer am Perito Moreno steht, hört das Eis knacken wie Schüsse,
 * dann das tiefe Ächzen und Grollen, bevor ein Turm fällt, den dumpfen Schlag ins Wasser und das Rauschen, wenn die Welle
 * am Ufer aufläuft.
 * <ul>
 * <li><b>Knacken</b>: ein bis sechs scharfe Schläge (breitbandig, durch kurze Eigentöne des Eises zwischen 700 und
 * 3 300 Hz), darunter ein leiser dumpfer Stoß um 110 Hz; stark wie ein Schuss. 104 + 22 · Stärke dB in 1 m.</li>
 * <li><b>Ächzen</b>: ein tiefes, reibendes Stöhnen (Grundton 28 bis 70 Hz, gleitend), stoßweise durch Haften und Gleiten
 * (8 bis 18 Stöße je s), 2 bis 5 s. 96 + 12 · Stärke dB in 1 m.</li>
 * <li><b>Aufschlag</b> (tone = Volumen in m³): dumpfer Schlag, umso tiefer, je größer (45 → 18 Hz), Krachen des
 * Wassers, dann das zurückfallende Wasser über Sekunden. 112 + 9 · lg(V / 100) dB in 1 m, 100 bis 140.</li>
 * <li><b>Auflaufen</b> (Stärke = Auflaufhöhe in m): das Wasser rauscht das Ufer hinauf (400 bis 2 500 Hz), steht kurz und
 * läuft über Kies zurück (hell rieselnd). 82 + 14 · lg(h / 0,2) dB in 1 m.</li>
 * <li><b>Kondor</b>: das Rauschen der Luft durch die gespreizten Handschwingen (ein Wischen von 600 bis 3 000 Hz), selten
 * ein Zischen. 70 dB in 1 m.</li>
 * </ul>
 * Nur Java 21.
 */
final class Glacial {
    private Glacial() { }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Was ein Klang ist: Name, Pegel in 1 m, Daten (wie {@link Calls.Made}). */
    static Calls.Made make(Soundscape.Kind k, double strength, double tone, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = clamp(strength, 0, 1);
        switch (k) {
            case KNACKEN -> {
                int clicks = 1 + (int) (r.uniform() * (2 + 4 * s));
                float[] o = new float[(int) ((0.35 + 0.15 * clicks) * sr)];
                double t = 0.005;
                for (int c = 0; c < clicks; c++) {
                    int at = (int) (t * sr);
                    double amp = c == 0 ? 1 : 0.35 + 0.5 * r.uniform();
                    // der Schlag: 1 bis 3 ms Rauschen, dann kurze Eigentöne des Eises
                    int imp = (int) ((0.001 + 0.002 * r.uniform()) * sr);
                    for (int i = 0; i < imp && at + i < o.length; i++) o[at + i] += (float) (r.white() * amp * 2.5 * (1 - i / (double) imp));
                    for (int m = 0; m < 4; m++) {
                        double f = 700 + 2600 * r.uniform(), tau = 0.008 + 0.03 * r.uniform();
                        ring(o, at, f, tau, amp * (0.6 - 0.1 * m), sr);
                    }
                    ring(o, at, 90 + 40 * r.uniform(), 0.05 + 0.04 * s, amp * 0.35, sr);   // der dumpfe Stoß im Körper
                    t += 0.03 + 0.12 * r.uniform();
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Knacken", 104 + 22 * s, o);
            }
            case AECHZEN -> {
                double len = 2 + 3 * r.uniform();
                float[] o = new float[(int) ((len + 0.3) * sr)];
                double f0 = 28 + 42 * r.uniform(), f1 = f0 * (0.75 + 0.5 * r.uniform()), slip = 8 + 10 * r.uniform();
                Dsp.Biquad body = new Dsp.Biquad().lowpass(420, 0.9, sr), grit = new Dsp.Biquad().bandpass(260 + 200 * r.uniform(), 1.2, sr);
                double ph = 0, sp = 0;
                for (int i = 0; i < o.length; i++) {
                    double tt = i / sr, u = Math.min(1, tt / len);
                    double f = f0 + (f1 - f0) * Calls.smooth(u) + 3 * Math.sin(Dsp.TAU * 0.7 * tt);
                    ph += f / sr;
                    ph -= Math.floor(ph);
                    sp += slip * (0.8 + 0.4 * Math.sin(Dsp.TAU * 0.31 * tt)) / sr;
                    double stick = Math.pow(0.5 + 0.5 * Math.cos(Dsp.TAU * sp), 6);   // Stöße: Haften und Gleiten
                    double env = Math.sin(Math.PI * Math.min(1, tt / (len + 0.2))) * (tt < 0.3 ? tt / 0.3 : 1);
                    double saw = 2 * ph - 1, tonal = saw * 0.7 + Math.sin(Dsp.TAU * ph) * 0.6;
                    float w = r.white();
                    o[i] = (float) ((body.process((float) tonal) * (0.4 + 0.9 * stick) + grit.process(w) * stick * 0.8) * env);
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Ächzen", 96 + 12 * s, o);
            }
            case AUFSCHLAG -> {
                double v = Math.max(1, tone), big = clamp(Math.log10(v / 10) / 4, 0, 1);   // 10 m³ … 100 000 m³
                double tail = 1.2 + 4 * big;
                float[] o = new float[(int) ((tail * 3 + 0.5) * sr)];
                double fHi = 45 - 15 * big, fLo = 18 + 8 * (1 - big), ph = 0;
                Dsp.Biquad crash = new Dsp.Biquad().bandpass(500 + 600 * r.uniform() * (1 - big), 0.6, sr), hiss = new Dsp.Biquad().highpass(1800, 0.7, sr),
                        rumble = new Dsp.Biquad().lowpass(220, 0.8, sr);
                for (int i = 0; i < o.length; i++) {
                    double t = i / sr;
                    double f = fLo + (fHi - fLo) * Math.exp(-t * 1.5);
                    ph += Dsp.TAU * f / sr;
                    float w = r.white();
                    double thump = Math.sin(ph) * (1 - Math.exp(-t * 40)) * Math.exp(-t / (0.35 + 0.8 * big)) * 2.0;
                    double cr = crash.process(w) * (1 - Math.exp(-t * 50)) * Math.exp(-t / (0.3 + 0.6 * big)) * 2.6;
                    double ru = rumble.process(w) * Math.exp(-t / (0.8 + 1.5 * big)) * 2.2;
                    double t2 = Math.max(0, t - 0.3 - 0.5 * big);
                    double back = hiss.process(w) * (1 - Math.exp(-t2 * 4)) * Math.exp(-t2 / tail) * 0.9;
                    o[i] = (float) (thump + cr + ru + back);
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Aufschlag", clamp(112 + 9 * Math.log10(v / 100), 100, 140), o);
            }
            case AUFLAUFEN -> {
                double h = Math.max(0.02, strength * 2), len = 1.2 + clamp(h, 0, 3);   // Stärke 0..1 = 0 bis 2 m
                float[] o = new float[(int) ((len + 3) * sr)];
                Dsp.Biquad rush = new Dsp.Biquad().bandpass(800 + 400 * r.uniform(), 0.8, sr), peb = new Dsp.Biquad().highpass(2600, 0.7, sr),
                        low = new Dsp.Biquad().lowpass(300, 0.7, sr);
                for (int i = 0; i < o.length; i++) {
                    double t = i / sr;
                    double up = Calls.smooth(t / 0.6) * Math.exp(-Math.max(0, t - len) / 0.6);
                    double back = t > len * 0.6 ? Math.exp(-(t - len * 0.6) / 1.4) * Math.min(1, (t - len * 0.6) / 0.3) : 0;
                    float w = r.white();
                    // Kies: einzelne Körner (spärliche Ticks) im Rücklauf
                    double grain = r.uniform() < 0.004 * back ? r.white() * 3 : 0;
                    o[i] = (float) (rush.process(w) * up * 1.6 + low.process(w) * up * 1.0 + (peb.process(w) * 0.25 + grain * 0.5) * back);
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Auflaufen", 82 + 14 * Math.log10(h / 0.2), o);
            }
            case KONDOR -> {
                boolean hissing = r.uniform() < 0.25;
                float[] o = new float[(int) (1.6 * sr)];
                Dsp.Biquad bp = new Dsp.Biquad();
                for (int i = 0; i < o.length; i++) {
                    double t = i / sr, u = t / 1.4;
                    if (i % 64 == 0) bp.bandpass(600 + 2400 * Math.sin(Math.PI * Math.min(1, u)), 0.9, sr);
                    double env = Math.sin(Math.PI * Math.min(1, u)) * (u < 1 ? 1 : 0);
                    o[i] = bp.process(r.white()) * (float) env;
                }
                if (hissing) {
                    Dsp.Biquad hb = new Dsp.Biquad().bandpass(450, 1.0, sr);
                    for (int i = 0; i < (int) (0.5 * sr); i++) o[(int) (0.8 * sr) + i - 0] += hb.process(r.white()) * (float) (0.8 * Math.sin(Math.PI * i / (0.5 * sr)));
                }
                Calls.normalize(o, sr);
                return new Calls.Made(hissing ? "Kondor (zischt)" : "Kondor", 70, o);
            }
            default -> throw new IllegalArgumentException("kein Klang des Eises: " + k);
        }
    }

    /** Ein abklingender Eigenton f mit Abklingzeit τ ab at. */
    static void ring(float[] o, int at, double f, double tau, double amp, double sr) {
        double rad = Math.exp(-1 / (tau * sr)), w = Dsp.TAU * Math.min(f, sr * 0.45) / sr;
        double c1 = 2 * rad * Math.cos(w), c2 = -rad * rad, y1 = amp * Math.sin(w), y2 = 0;
        int n = Math.min(o.length - at, (int) (tau * 8 * sr));
        for (int i = 0; i < n; i++) {
            double y = c1 * y1 + c2 * y2;
            y2 = y1;
            y1 = y;
            o[at + i] += (float) y;
        }
    }
}
