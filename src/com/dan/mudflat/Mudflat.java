package com.dan.mudflat;

import java.util.Locale;

/**
 * Das Watt als Material und Oberfläche (Werkbuch Rungholt, Abschnitt 4, F11 A). Kennt keine Tide und keine App: Wasser,
 * Strömung, Licht und Wetter kommen von außen. Stand Phase 5: das Material – {@link WetField} hält Nässe, Sediment und
 * die Richtung der letzten Strömung je Zelle und trocknet nach dem Wetter; {@code gl/mudflat.glsl} macht daraus Farbe,
 * Rippel mit Wasser in den Tälern, Wasserfilm, Pfützen, Schill, Wattwurmhaufen und Algen; {@code gl/MudflatGl} lädt den
 * Zustand als Textur. Die Oberfläche mit Kacheln und Prielkanten folgt in Phase 6:
 * <ul>
 * <li><b>Material</b> (Phase 5): Nässe als Zustand je Texel, die mit dem Wasser auf 1 springt und mit Sonne, Wind,
 * Temperatur und Versickerung trocknet; Farbe und Glanz nach Nässe und Sediment (Sand, Mischwatt, Schlick); Rippel quer
 * zur letzten Strömung mit Wasser in den Tälern; Schill, Wattwurmhaufen, Algen, Spuren bis zur nächsten Flut;</li>
 * <li><b>Oberfläche</b> (Phase 6): Kacheln mit LOD über einem Grund, zwei Gitter (2 m um die Kamera, 16 m über
 * alles), Prielkanten mit Abbruch;</li>
 * <li><b>Spiegelung</b>: der Himmel im Wasserfilm nach Fresnel; bei flachem Blick wird das Watt zum Spiegel.</li>
 * </ul>
 */
public final class Mudflat {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }
    public enum Sediment { SAND, MISCHWATT, SCHLICK }

    /** Stand des Packages: 1 = Hülle; 5 = Material; 6 = Feinform mit Prielkanten und Rinnsalen ({@link Relief}). */
    public static final int STAGE = 6;

    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Feines und grobes Gitter: Zellgröße (m), Texel je Kante; das feine liegt um die Kamera. */
    public double fineCell = 2, coarseCell = 16;
    public int fineN = 1024;
    /** Rippel: kleinste und größte Wellenlänge (m) [G]. */
    public double rippleMin = 0.05, rippleMax = 0.20;
    /** Trocknung [G]: Stunden bis halb trocken im Schatten bei Windstille, Minuten in Sonne und frischem Wind. */
    public double halfDryShadeHours = 3, halfDrySunWindMinutes = 25;
    /** Wie stark Nässe den Sand dunkler macht (Anteil der Helligkeit, der bei voller Nässe fehlt). */
    public double wetDarkening = 0.45;

    // ------------------------------------------------------------------ Presets
    /** Das Nordfriesische Watt (Rungholt): Sand auf den Höhen, Schlick in den Buchten. */
    public static Mudflat wadden() { return new Mudflat(); }

    /** Ein Sandstrand (Lighthouse, Thunder): nur Sand, gröbere Rippel, trocknet schneller. */
    public static Mudflat beach() {
        Mudflat m = new Mudflat();
        m.rippleMin = 0.08; m.rippleMax = 0.30; m.halfDryShadeHours = 1.5; m.halfDrySunWindMinutes = 12;
        return m;
    }

    // ------------------------------------------------------------------ Formeln
    /** Spiegelanteil einer Wasserfläche nach Schlick (Brechzahl 1,33) für den Kosinus zwischen Blick und Normale. */
    public static double fresnel(double cosTheta) {
        double f0 = 0.02, c = Math.max(0, Math.min(1, cosTheta));
        return f0 + (1 - f0) * Math.pow(1 - c, 5);
    }

    /** Helligkeit eines Sediments trocken, 0..1 [G]. */
    public static double dryAlbedo(Sediment s) {
        return switch (s) { case SAND -> 0.36; case MISCHWATT -> 0.26; case SCHLICK -> 0.17; };
    }

    /** Helligkeit bei Nässe 0..1. */
    public double albedo(Sediment s, double wetness) { return dryAlbedo(s) * (1 - wetDarkening * Math.max(0, Math.min(1, wetness))); }

    /**
     * Nässe nach einer Trockenzeit (h): halbiert sich nach der Halbwertszeit, die zwischen Schatten/Windstille und
     * Sonne/Wind liegt (sun, wind je 0..1). Die Hülle rechnet nur diese Kurve; Phase 5 macht daraus den Zustand je Texel.
     */
    public double wetnessAfter(double hours, double sun, double wind) {
        double k = Math.max(0, Math.min(1, 0.6 * sun + 0.4 * wind));
        double half = halfDryShadeHours * (1 - k) + halfDrySunWindMinutes / 60.0 * k;
        return Math.pow(0.5, Math.max(0, hours) / half);
    }

    /** Wattwurmhaufen je m² auf Sand und Mischwatt [G] (Arenicola marina: zehn bis vierzig). */
    public double lugwormPerM2 = 20;

    /** „mudflat: Stufe 5 · 1 024² zu 2 m, Rest 16 m · MEDIUM“ */
    public String describe() {
        return String.format(Locale.GERMAN, "mudflat: %s · %,d² zu %.0f m, Rest %.0f m · Rippel %.0f–%.0f cm · %s",
                STAGE == 1 ? "Hülle (Phase 1)" : "Stufe " + STAGE + (STAGE >= 6 ? " (Material und Feinform)" : " (Material)"), fineN, fineCell, coarseCell, rippleMin * 100, rippleMax * 100, quality);
    }
}
