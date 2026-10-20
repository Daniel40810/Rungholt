package com.dan.muster;

/**
 * Das Muster-Package: eine leere Vorlage, die zeigt, wie ein Element-Package nach dem Paketvertrag aussieht
 * (Werkbuch, Abschnitt 4). Es tut fast nichts – ein ruhiges Rauschfeld, das mit dem Wind stärker wird –, aber es
 * hat alles, was jedes Package haben muss: Kern ohne Abhängigkeiten, Presets nach Beaufort, offene Felder, einen
 * {@code seed}, Qualitätsstufen und Abfragen ohne Grafikkarte. Neue Packages entstehen durch Kopieren dieses Ordners.
 * <p>
 * Der Kern kennt nur Java 21. Was ein Package von außen braucht, nimmt es als Zahl oder als eigene kleine
 * Schnittstelle an, nie als Klasse eines anderen Packages.
 */
public final class Muster {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7). */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Eigene kleine Schnittstelle für ein Ereignis nach außen (Paketvertrag Punkt 4). */
    @FunctionalInterface
    public interface Peak { void peak(double x, double z, double value); }

    /** Startwert des Zufalls: gleicher seed, gleiches Feld (Punkt 6). */
    public long seed = 1;
    /** Eingänge als offene Werte (Punkt 5): Wind in m/s und Richtung, aus der er kommt (Grad). */
    public double windSpeed = 5, windFromDeg = 240;
    /** Stärke des Felds 0..1 und Größe der Strukturen in m. */
    public double strength = 0.3, scale = 40;
    public Quality quality = Quality.HIGH;
    /** Wird gerufen, wenn {@link #scan} einen Wert über {@code peakAbove} findet; null = niemand hört zu. */
    public Peak onPeak;
    public double peakAbove = 0.9;

    // ------------------------------------------------------------------ Presets (Punkt 5)
    public static Muster calm() { return beaufort(1); }
    public static Muster breeze() { return beaufort(4); }
    public static Muster gale() { return beaufort(8); }
    public static Muster storm() { return beaufort(10); }

    /** Preset nach Beaufort 0..12: Wind in der Mitte der Stufe, Stärke wächst mit. */
    public static Muster beaufort(int bft) {
        Muster m = new Muster();
        bft = Math.max(0, Math.min(12, bft));
        m.windSpeed = BFT_MID[bft];
        m.strength = Math.min(1, 0.05 + bft / 12.0 * 0.95);
        return m;
    }

    /** Mitte jeder Beaufort-Stufe in m/s (WMO), eigene Kopie. */
    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    /** Wert des Felds am Ort (x, z) zur Zeit t, 0..1; zieht mit dem Wind. Keine Grafikkarte nötig. */
    public double valueAt(double x, double z, double t) {
        double a = Math.toRadians(windFromDeg + 180);
        double u = x - Math.sin(a) * windSpeed * t * 0.2, v = z + Math.cos(a) * windSpeed * t * 0.2;
        int oct = switch (quality) { case LOW -> 2; case MEDIUM -> 3; case HIGH -> 4; case ULTRA -> 5; };
        double sum = 0, amp = 0.5, f = 1 / scale;
        for (int i = 0; i < oct; i++) {
            sum += amp * noise(u * f, v * f, i);
            amp *= 0.5;
            f *= 2.03;
        }
        return Math.max(0, Math.min(1, 0.5 + (sum - 0.25) * strength * 2));
    }

    /** Sucht auf einem Raster die Stellen über {@code peakAbove} und meldet sie über {@link #onPeak}; gibt die Zahl zurück. */
    public int scan(double x0, double z0, double size, int n, double t) {
        int found = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double x = x0 + size * i / n, z = z0 + size * j / n, v = valueAt(x, z, t);
                if (v > peakAbove) {
                    found++;
                    if (onPeak != null) onPeak.peak(x, z, v);
                }
            }
        return found;
    }

    /** Wertrauschen 0..1 mit weicher Interpolation, nur aus seed und Gitter (reproduzierbar). */
    double noise(double x, double z, int octave) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, octave), b = hash(ix + 1, iz, octave), c = hash(ix, iz + 1, octave), d = hash(ix + 1, iz + 1, octave);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    double hash(long x, long z, int o) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L + o * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
