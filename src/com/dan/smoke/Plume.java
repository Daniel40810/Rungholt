package com.dan.smoke;

/**
 * Die Rauchfahne als Formel (Zeile für Zeile wie {@code gl/smoke_common.glsl}).
 * <p>
 * <b>Aufstieg</b> nach Briggs für eine abgeknickte Fahne: frei Δh = (3F / (2β²))<sup>1/3</sup> · u<sup>−1/3</sup> ·
 * a<sup>2/3</sup> (a Alter in s, β = 0,6, u Wind in Dachhöhe); in stabiler Luft endet er bei Δh<sub>e</sub> = 2,6 ·
 * (F / (u · N²))<sup>1/3</sup> mit der Stabilität N² = 2·10⁻⁵ + S · 1,2·10⁻³ s⁻² (S = 0 neutral, 1 kräftige
 * Abendinversion). Dazwischen weich: Δh = Δh<sub>e</sub> · tanh(Δh<sub>frei</sub> / Δh<sub>e</sub>). Ein Herdfeuer von
 * 10 kW steigt so am Abend bei 2 m/s Wind nur 9 m über das Dach und legt sich dann flach, am Tag gut 30 m.
 * <p>
 * <b>Breite:</b> σ² = (0,4 Δh)² + 2 K a + 0,09 m² waagerecht und senkrecht; K<sub>waag</sub> = 0,2 + 0,12 U + 0,6 S
 * (unter der Inversion breitet sich der Rauch seitlich aus), K<sub>senk</sub> = (0,2 + 0,1 U) · (1 − 0,85 S).
 * <p>
 * <b>Deckkraft</b> eines Balls: min(0,8; 2,6 / (1 + w σ<sub>waag</sub> σ<sub>senk</sub>)) mal Menge. Der Faktor w war
 * bis Phase 10 1 und seit Phase 11 0,25, weil man die Fahnen am Abend auf dem Statusbild von Phase 10 nicht sah. Seit
 * Phase 13 hängt w von der Stabilität ab ({@link #widthFactor}): am Abend 0,25, am Tag 0,9.
 * <p>
 * <b>Teilchen:</b> Jede Fahne besteht aus n Bällen im Altersabstand Δa; Ball i hat das Alter (i + φ) Δa mit
 * φ = frac(t / Δa) und die Nummer k = floor(t / Δa) − i, die er sein Leben lang behält. Aus k folgt sein Versatz
 * (drei Zufallszahlen) – nichts wird gespeichert, die Grafikkarte rechnet dasselbe.
 */
public final class Plume {
    private Plume() { }

    public static final double BETA = 0.6, G = 9.81;
    /** Ein Ball je Fahne: Nummer der Größen in {@link #particle}. */
    public static final int X = 0, Y = 1, Z = 2, RADIUS = 3, ALPHA = 4, AGE = 5;

    /** Auftriebsfluss (m⁴/s³) aus der Wärmeleistung (kW): g / (π · 1005 · 1,2 · 288) je W. */
    public static double buoyancyFlux(double kw) { return G / (Math.PI * 1005 * 1.2 * 288) * kw * 1000; }

    /** Stabilität N² (s⁻²) aus S 0..1. */
    public static double n2(double stability) { return 2e-5 + Math.max(0, Math.min(1, stability)) * 1.2e-3; }

    /** Wind in Dachhöhe aus dem Wind in 10 m (Siedlung, Rauigkeit 0,5 m), nie unter 0,5 m/s (Eigenbewegung). */
    public static double roofWind(double u10) { return Math.max(0.5, 0.7 * u10); }

    /**
     * Die Konstanten einer Fahne für die Lage und Zeit t jetzt ({@link #FLOATS} Zahlen, so gehen sie auch an die
     * Grafikkarte). Zeit als ganze Schritte und Bruchteil, damit die Grafikkarte auch nach Stunden genau rechnet:
     * <pre>
     *  0 x  1 y  2 z  3 Menge
     *  4 Schwärze  5 Zufall (Bits)  6 Lebensdauer (s)  7 Δa (s)
     *  8 Zahl der Bälle  9 Größe  10 φ = frac(t/Δa)  11 Δh_e (m)
     * 12 Faktor frei  13 u (m/s)  14 K waag  15 K senk
     * 16 floor(t/Δa) (Bits)  17 Wind x  18 Wind z  19 Breitenfaktor der Deckkraft (seit Haithabu Phase 13)
     * </pre>
     */
    public static final int FLOATS = 20;

    /**
     * Seit Haithabu Phase 13: wie stark die Breite der Fahne die Deckkraft eines Balls senkt. In stabiler Abendluft 0,25
     * (die flache Fahne bleibt sichtbar, Befund Phase 10), am Tag in gut durchmischter Luft 0,9: dort verdünnt sich der
     * Rauch schnell, und die Fahnen standen auf den Mittagsbildern von Phase 12 wie weiße Wattebäusche über den Dächern.
     */
    public static double widthFactor(double stability) {
        double s = Math.max(0, Math.min(1, stability));
        return 0.9 + (0.25 - 0.9) * Math.sqrt(s);
    }

    public static float[] constants(Source s, double windX, double windZ, double u10, double stability, int count, double life, double size, double t) {
        double f = Math.max(1e-6, s.buoyancyFlux());
        double up = roofWind(u10);
        double dhe = 2.6 * Math.cbrt(f / (up * n2(stability)));
        double free = Math.cbrt(3 * f / (2 * BETA * BETA)) / Math.cbrt(up);
        double kh = 0.2 + 0.12 * u10 + 0.6 * stability, kv = (0.2 + 0.1 * u10) * (1 - 0.85 * stability);
        double da = life / count;
        double tt = t / da + (Integer.toUnsignedLong(s.seed()) % 997) * 0.37;    // jede Fahne mit eigener Phase
        double steps = Math.floor(tt);
        return new float[]{(float) s.x(), (float) s.y(), (float) s.z(), (float) s.amount(),
                (float) s.black(), Float.intBitsToFloat(s.seed()), (float) life, (float) da,
                count, (float) size, (float) (tt - steps), (float) dhe,
                (float) free, (float) up, (float) kh, (float) kv,
                Float.intBitsToFloat((int) (long) steps), (float) windX, (float) windZ, (float) widthFactor(stability)};
    }

    /** Aufstieg (m) im Alter a (s). */
    public static double rise(float[] c, double a) {
        double dhe = c[11], free = c[12] * Math.pow(Math.max(0, a), 2.0 / 3.0);
        return dhe * Math.tanh(free / dhe);
    }

    /** Waagerechte und senkrechte Breite σ (m) im Alter a. */
    public static double[] sigma(float[] c, double a) {
        double dh = rise(c, a);
        double sh = Math.sqrt(0.16 * dh * dh + 2 * c[14] * a + 0.09), sv = Math.sqrt(0.16 * dh * dh + 2 * c[15] * a + 0.09);
        return new double[]{sh, sv};
    }

    /** Mitte der Fahne im Alter a (m): Austritt + Wind · u · a · (1 + 0,012 Δh) + Aufstieg. */
    public static double[] center(float[] c, double a) {
        double dh = rise(c, a);
        double run = c[13] * a * (1 + 0.012 * dh);
        return new double[]{c[0] + c[17] * run, c[1] + dh, c[2] + c[18] * run};
    }

    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** Zufallszahl 0..1 aus Fahne, Nummer, Kanal (wie {@code smokeRnd}). */
    public static double rnd(int seed, int k, int ch) {
        return (hash(hash(k * 8 + ch) ^ seed) >>> 8) * (1.0 / 16777216.0);
    }

    /**
     * Ball i der Fahne zur Zeit der Konstanten: {x, y, z, Halbmesser, Deckkraft, Alter}; Deckkraft 0 heißt
     * unsichtbar (jenseits der Lebensdauer oder Fahne aus).
     */
    public static double[] particle(float[] c, int i) {
        double da = c[7], life = c[6];
        double ph = c[10];
        int k = Float.floatToRawIntBits(c[16]) - i;
        double a = (i + ph) * da;
        int seed = Float.floatToRawIntBits(c[5]);
        double r1 = rnd(seed, k, 0) + rnd(seed, k, 1) - 1, r2 = rnd(seed, k, 2) + rnd(seed, k, 3) - 1, r3 = rnd(seed, k, 4) + rnd(seed, k, 5) - 1;
        double[] cen = center(c, a);
        double[] sg = sigma(c, a);
        double x = cen[0] + r1 * sg[0] * 0.9, y = cen[1] + r2 * sg[1] * 0.7, z = cen[2] + r3 * sg[0] * 0.9;
        double radius = Math.min(25, (0.9 + 1.1 * sg[0]) * c[9] * (0.8 + 0.4 * rnd(seed, k, 6)));
        double fadeIn = smooth(0, 1.5 * da, a), fadeOut = 1 - smooth(0.6 * life, life, a);
        double alpha = c[3] * fadeIn * fadeOut * Math.min(0.8, 2.6 / (1 + c[19] * sg[0] * sg[1])) * (0.7 + 0.6 * rnd(seed, k, 7));
        if (i >= (int) c[8]) alpha = 0;
        return new double[]{x, y, z, radius, alpha, a};
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
