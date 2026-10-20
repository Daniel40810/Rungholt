package com.dan.reed;

import java.util.Arrays;

/**
 * Schilf, Gras und Binsen im Wind (Haithabu, Phase 2). Das Paket verteilt Halme nach einer Dichte, die der Benutzer
 * vorgibt, auf Zellen von 16 m und sagt für jeden Halm zu jeder Zeit, wie weit er sich im Wind biegt. Der Kern braucht
 * nur Java 21; {@code gl/} zeichnet die Halme mit JOGL als Instanzen, ohne Eckpuffer, und rechnet die Biegung mit
 * derselben Formel wie {@link #sway}.
 * <p>
 * Wind wirkt dreifach: eine stetige Neigung mit dem Quadrat der Windgeschwindigkeit, Böenwellen, die mit dem Wind über
 * die Fläche laufen (man sieht sie im Schilf als helle Bahnen, weil die Blätter ihre Unterseite zeigen), und ein
 * leichtes Zittern jedes Halms. Die Jahreszeit färbt (Sommer grün, Herbst gold bis braun, Winter strohgrau).
 * <p>
 * Paketvertrag: alle Felder öffentlich und zur Laufzeit änderbar, Presets nach Beaufort, {@code seed} macht jede
 * Verteilung reproduzierbar, {@link Quality} regelt Reichweite und Dichte.
 */
public final class Reed {
    /** Qualitätsstufen: Reichweite und Dichte der Halme. */
    public enum Quality {
        LOW(0.45, 0.4), MEDIUM(0.75, 0.7), HIGH(1.0, 1.0), ULTRA(1.35, 1.4);
        public final double range, density;
        Quality(double range, double density) { this.range = range; this.density = density; }
    }

    /** Arten mit Höhe (m), Streuung der Höhe, Halme je m² bei Dichte 1, Reichweite (m), Steifigkeit (1 = steif). */
    public enum Kind {
        /** Schilfrohr (Phragmites australis): bis 3 m, Rispe oben, im Herbst gold. */
        SCHILF(2.4, 0.5, 6.0, 260, 0.55),
        /** Binsen und Seggen am nassen Rand: knapp 1 m, steif, dunkel. */
        BINSE(0.9, 0.25, 5.0, 120, 0.8),
        /** Wiesengras als Büschel: 0,3 bis 0,6 m. */
        GRAS(0.30, 0.30, 12.0, 34, 0.35),
        /**
         * Seit Glacier (Phase 2): Horstgras der patagonischen Steppe (Coirón, Festuca gracillima): feste Horste von
         * 0,4 bis 0,7 m, weit gestreut, das ganze Jahr strohgelb mit grüner Basis.
         */
        STEPPE(0.50, 0.35, 3.0, 60, 0.55);
        public final double height, spread, perM2, range, stiffness;
        Kind(double height, double spread, double perM2, double range, double stiffness) {
            this.height = height;
            this.spread = spread;
            this.perM2 = perM2;
            this.range = range;
            this.stiffness = stiffness;
        }
    }

    /** Wo welche Art wächst: Dichte 0..1 am Ort (vom Benutzer, etwa aus Gelände und Uferlinie). */
    @FunctionalInterface
    public interface Density { double at(double x, double z, Kind kind); }

    /** Höhe des Bodens am Ort (eigene kleine Schnittstelle, Paketvertrag Punkt 4). */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Seitenlänge einer Zelle in m. */
    public static final double CELL = 16;
    /** Werte je Halm in {@link #cell}: x, y, z, Höhe, Richtung der Ruhelage (rad), Phase, Ton (0..1), Art. */
    public static final int STRIDE = 8;

    // ------------------------------------------------------------------ Eingänge
    public volatile long seed = 1;
    /** Wind in m/s und Richtung, aus der er kommt (Grad von Nord). */
    public volatile double windSpeed = 4.4, windFromDeg = 245;
    /** Böigkeit 0..1. */
    public volatile double gust = 0.3;
    /** Jahreszeit: 0 Sommer, 0,5 Spätsommer, 0,75 Mitte Oktober, 1 Winter (strohgrau). */
    public volatile double season = 0.75;
    /** Nässe 0..1 (Regen): Halme hängen etwas, dunkler. */
    public volatile double wetness;
    /** Schneehöhe in m (seit Haithabu Phase 12): die Halme stehen im Schnee, Gras unter einer Decke verschwindet. */
    public volatile double snowDepth;
    /** Reif 0..1 (seit Haithabu Phase 12): weißgraue Halme an kalten, klaren Morgen. */
    public volatile double frost;
    /** Faktor auf alle Dichten. */
    public volatile double density = 1;
    public volatile Quality quality = Quality.HIGH;

    // ------------------------------------------------------------------ Presets
    public static Reed calm() { return beaufort(1); }
    public static Reed breeze() { return beaufort(3); }
    public static Reed fresh() { return beaufort(5); }
    public static Reed gale() { return beaufort(8); }

    /** Preset nach Beaufort 0..12: Wind in der Mitte der Stufe, Böigkeit wächst mit. */
    public static Reed beaufort(int bft) {
        Reed r = new Reed();
        bft = Math.max(0, Math.min(12, bft));
        r.windSpeed = BFT_MID[bft];
        r.gust = Math.min(0.6, 0.15 + bft * 0.05);
        return r;
    }

    /** Mitte jeder Beaufort-Stufe in m/s (WMO), eigene Kopie. */
    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    // ------------------------------------------------------------------ Verteilung

    /** Reichweite einer Art mit der Qualitätsstufe (m). */
    public double range(Kind k) { return k.range * quality.range; }

    /**
     * Die Halme einer Art in Zelle (ci, cj), reproduzierbar aus seed, Zelle und Art. Rückgabe: STRIDE Werte je Halm.
     * Gleichmäßig gestreut (Zufall in einem Gitter), dann nach der Dichte ausgedünnt.
     */
    public float[] cell(int ci, int cj, Kind kind, Density d, Ground g) {
        double per = kind.perM2 * density * quality.density;
        int side = Math.max(1, (int) Math.round(CELL * Math.sqrt(per)));
        double step = CELL / side;
        float[] out = new float[side * side * STRIDE];
        int n = 0;
        long base = mix(seed, ci, cj, kind.ordinal());
        for (int b = 0; b < side; b++)
            for (int a = 0; a < side; a++) {
                long h = mix(base, a, b, 7);
                double x = ci * CELL + (a + u(h, 0)) * step, z = cj * CELL + (b + u(h, 1)) * step;
                double dens = d.at(x, z, kind);
                if (dens <= 0 || u(h, 2) >= dens) continue;
                double ht = kind.height * (1 + kind.spread * (u(h, 3) + u(h, 4) - 1)) * (0.75 + 0.25 * Math.min(1, dens * 1.4));
                int o = n * STRIDE;
                out[o] = (float) x;
                out[o + 1] = (float) g.height(x, z);
                out[o + 2] = (float) z;
                out[o + 3] = (float) ht;
                out[o + 4] = (float) (u(h, 5) * 2 * Math.PI);
                out[o + 5] = (float) (u(h, 6) * 2 * Math.PI);
                out[o + 6] = (float) u(h, 7);
                out[o + 7] = kind.ordinal();
                n++;
            }
        return Arrays.copyOf(out, n * STRIDE);
    }

    /** Alle Halme einer Art im Umkreis r um (cx, cz), Zelle für Zelle. */
    public float[] around(double cx, double cz, double r, Kind kind, Density d, Ground g) {
        int i0 = (int) Math.floor((cx - r) / CELL), i1 = (int) Math.floor((cx + r) / CELL);
        int j0 = (int) Math.floor((cz - r) / CELL), j1 = (int) Math.floor((cz + r) / CELL);
        float[] all = new float[0];
        int n = 0;
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                double nx = Math.max(i * CELL, Math.min(cx, (i + 1) * CELL)), nz = Math.max(j * CELL, Math.min(cz, (j + 1) * CELL));
                if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) > r * r) continue;
                float[] c = cell(i, j, kind, d, g);
                if (c.length == 0) continue;
                if (n + c.length > all.length) all = Arrays.copyOf(all, Math.max(all.length * 2, n + c.length));
                System.arraycopy(c, 0, all, n, c.length);
                n += c.length;
            }
        return Arrays.copyOf(all, n);
    }

    // ------------------------------------------------------------------ Wind

    /** Wohin der Wind weht (x Ost, z Süd). */
    public double[] windTo() {
        double a = Math.toRadians(windFromDeg + 180);
        return new double[]{Math.sin(a), -Math.cos(a)};
    }

    /** Stetige Neigung der Spitze als Anteil der Halmhöhe (0..0,7), wächst mit U², steife Arten weniger. */
    public static double lean(double wind, double stiffness) {
        return Math.min(0.7, 0.0045 * wind * wind * (1.15 - stiffness));
    }

    /**
     * Auslenkung der Spitze eines Halms in Windrichtung und quer dazu (Anteile der Halmhöhe) zur Zeit t; der Shader
     * in gl/ rechnet dieselbe Formel. x, z: Ort; phase: eigene Phase des Halms; stiffness der Art.
     */
    public double[] sway(double x, double z, double phase, double stiffness, double t) {
        double[] to = windTo();
        double along = x * to[0] + z * to[1];
        double u = windSpeed, base = lean(u, stiffness);
        // Böenwellen: Wellenlänge 18 m, laufen mit 0,7 · U über die Fläche
        double k = 2 * Math.PI / 18.0, c = 0.7 * Math.max(0.5, u);
        double wave = 0.5 + 0.5 * Math.sin(k * (along - c * t) + 2.0 * Math.sin(0.11 * (x - z)));
        double gustAmp = gust * base * 1.6 * wave;
        double flutter = (0.015 + 0.004 * u) * (1.1 - stiffness) * Math.sin(t * (2.2 + 0.25 * u) + phase);
        double side = 0.6 * flutter * Math.cos(t * 1.7 + phase * 1.3);
        double droop = wetness * 0.08;
        return new double[]{base + gustAmp + flutter + droop, side};
    }

    /** Farbe einer Art bei der Jahreszeit (linear, Albedo), ton 0..1 streut einzelne Halme. */
    public double[] color(Kind k, double ton) {
        double[] summer, autumn, winter;
        switch (k) {
            case SCHILF -> { summer = new double[]{0.10, 0.16, 0.06}; autumn = new double[]{0.30, 0.22, 0.10}; winter = new double[]{0.30, 0.27, 0.20}; }
            case BINSE -> { summer = new double[]{0.07, 0.12, 0.05}; autumn = new double[]{0.12, 0.13, 0.06}; winter = new double[]{0.16, 0.14, 0.10}; }
            case STEPPE -> { summer = new double[]{0.22, 0.19, 0.09}; autumn = new double[]{0.27, 0.22, 0.11}; winter = new double[]{0.24, 0.21, 0.13}; }
            default -> { summer = new double[]{0.06, 0.10, 0.03}; autumn = new double[]{0.10, 0.105, 0.045}; winter = new double[]{0.16, 0.15, 0.10}; }
        }
        double s = Math.max(0, Math.min(1, season));
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = s < 0.75 ? summer[i] + (autumn[i] - summer[i]) * smooth(0.25, 0.75, s) : autumn[i] + (winter[i] - autumn[i]) * smooth(0.75, 1.0, s);
            c[i] = v * (0.8 + 0.4 * ton) * (1 - 0.25 * wetness);
        }
        return c;
    }

    // ------------------------------------------------------------------ Helfer

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static long mix(long s, long a, long b, long c) {
        long h = s * 0x9E3779B97F4A7C15L ^ a * 0xC2B2AE3D27D4EB4FL ^ b * 0x165667B19E3779F9L ^ c * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        return h;
    }

    /** k-te Zufallszahl 0..1 aus h. */
    static double u(long h, int k) {
        long x = mix(h, k, 0x51ED27L, 3);
        return (x >>> 11) * 0x1.0p-53;
    }

    /** „Schilf 2,4 m · Bft 3 · Herbst“ */
    public String describe() {
        String s = season < 0.3 ? "Sommer" : season < 0.6 ? "Spätsommer" : season < 0.9 ? "Herbst" : "Winter";
        int bft = 0;
        while (bft < 12 && windSpeed >= (BFT_MID[bft] + BFT_MID[bft + 1]) / 2) bft++;
        return String.format(java.util.Locale.GERMAN, "Bft %d · %s · Neigung Schilf %.0f %%", bft, s, 100 * lean(windSpeed, Kind.SCHILF.stiffness));
    }
}
