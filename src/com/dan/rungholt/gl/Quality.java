package com.dan.rungholt.gl;

import com.dan.cloud.Clouds;
import com.dan.fog.Fog;
import com.dan.lightning.Lightning;
import com.dan.ocean.Ocean;
import com.dan.rain.Rain;
import com.dan.tide.Tide;
import com.dan.reed.Reed;
import com.dan.seal.Seal;
import com.dan.flock.Waders;

import java.util.Locale;

/**
 * Qualitätsstufen der App (aus Glacier, entschlackt). Phase 1: Anteil der Bildgröße, in dem gerechnet wird, und das
 * Zeitbudget. Jede weitere Package-Phase hängt die Stufe ihres Packages an (Phase 2: ocean, reed, forest, ground,
 * Gelände; Phase 3: Himmel und Wetter; Phase 4–8: tide, mudflat, seal, flock; Phase 11–12: timber, wave, fracture).
 * Die Packages haben ihre eigene Kopie dieser Aufzählung (Paketvertrag Punkt 7).
 * Phase 3: Wolken, Regen, Nebel und Blitz wie in Glacier („Mittel“ regnet mit LOW, Befund Glacier Status_Phase3). Die
 * Bodendecke kleiner als in Phase 2 (Befund Status_Phase2: 8,3 ms auf dem Prozessor mit der Blumenwiese auf 24 m;
 * jetzt der Deich, 18 m auf „Mittel“). Phase 4: das Gitter der Überflutung ({@code com.dan.tide}) im eigenen Faden. Phase 6: das
 * Gelände am Auge auf 2 m (auf „Niedrig“ 8 m) für die Feinform des Watts. Phase 7: die Zahl der
 * Seehunde ({@code com.dan.seal}).
 * Ziel (F22 A): „Mittel“ hält 60 Bilder/s in Full HD auf der Radeon 680M, „Hoch“ mindestens 30.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, 1.8, 8, Ocean.Quality.LOW, Reed.Quality.LOW, 12, Clouds.Quality.FLAT, Rain.Quality.LOW, Fog.Quality.LOW, Lightning.Quality.LOW, Tide.Quality.LOW, Seal.Quality.LOW, Waders.Quality.LOW),
    MITTEL("Mittel", 0.85, 2.3, 2, Ocean.Quality.MEDIUM, Reed.Quality.MEDIUM, 18, Clouds.Quality.MEDIUM, Rain.Quality.LOW, Fog.Quality.MEDIUM, Lightning.Quality.MEDIUM, Tide.Quality.MEDIUM, Seal.Quality.MEDIUM, Waders.Quality.MEDIUM),
    HOCH("Hoch", 1.0, 2.8, 2, Ocean.Quality.HIGH, Reed.Quality.HIGH, 24, Clouds.Quality.HIGH, Rain.Quality.HIGH, Fog.Quality.HIGH, Lightning.Quality.HIGH, Tide.Quality.MEDIUM, Seal.Quality.HIGH, Waders.Quality.HIGH),
    ULTRA("Ultra", 1.0, 3.4, 2, Ocean.Quality.ULTRA, Reed.Quality.ULTRA, 30, Clouds.Quality.ULTRA, Rain.Quality.ULTRA, Fog.Quality.ULTRA, Lightning.Quality.ULTRA, Tide.Quality.HIGH, Seal.Quality.ULTRA, Waders.Quality.ULTRA);

    public final String label;
    /** Anteil der Bildgröße, in dem gerechnet wird. */
    public final double renderScale;
    /** Phase 2: Sichtweite jeder Geländestufe in ihren Knotengrößen (CDLOD). */
    public final double terrainRange;
    /** Phase 6: Abstand der Punkte am Auge (m): 2 m zeigt die Feinform des Watts (Rinnsale, Prielkanten), 8 m nur die Kanten. */
    public final int terrainFine;
    /** Phase 2: Stufe des Meeres und der Halme, Halbmesser der Bodendecke um das Auge (m). */
    public final Ocean.Quality ocean;
    public final Reed.Quality reed;
    public final double groundRadius;
    /** Phase 3: Stufen von Wolken, Regen, Nebel und Blitz. */
    public final Clouds.Quality clouds;
    public final Rain.Quality rain;
    public final Fog.Quality fog;
    public final Lightning.Quality lightning;
    /** Phase 4: Gitter der Überflutung (Niedrig 32 m, Mittel und Hoch 16 m, Ultra 8 m). */
    public final Tide.Quality tide;
    /** Phase 7: Zahl der Seehunde (Niedrig 21, Mittel 36, Hoch 51, Ultra 60 bei höchstens 60). */
    public final Seal.Quality seal;
    /** Phase 8: Zahl der Wattvögel (Niedrig gut ein Drittel, Hoch fast das Doppelte, Ultra das Dreifache von „Mittel“). */
    public final Waders.Quality birds;

    Quality(String label, double renderScale, double terrainRange, int terrainFine, Ocean.Quality ocean, Reed.Quality reed, double groundRadius,
            Clouds.Quality clouds, Rain.Quality rain, Fog.Quality fog, Lightning.Quality lightning, Tide.Quality tide, Seal.Quality seal, Waders.Quality birds) {
        this.birds = birds;
        this.tide = tide;
        this.seal = seal;
        this.clouds = clouds;
        this.rain = rain;
        this.fog = fog;
        this.lightning = lightning;
        this.label = label;
        this.renderScale = renderScale;
        this.terrainRange = terrainRange;
        this.terrainFine = terrainFine;
        this.ocean = ocean;
        this.reed = reed;
        this.groundRadius = groundRadius;
    }

    /**
     * Bildzeit, die die Stufe höchstens verbrauchen darf (ms): „Mittel“ 15,2 ms nach dem Budget im Werkbuch
     * (Abschnitt 10, 1,5 ms Reserve auf 16,7 ms), „Hoch“ das Doppelte, „Niedrig“ die Hälfte.
     */
    public double budgetMs() {
        return switch (this) { case NIEDRIG -> 7.6; case MITTEL -> 15.2; case HOCH -> 30.4; case ULTRA -> 50.0; };
    }

    /** Was die Stufe einstellt, als eine Zeile (Bedienfeld, README). */
    public String summary() {
        return String.format(Locale.GERMAN, "%s: %d %% der Auflösung, Budget %.1f ms, Gelände %.1f Knoten, am Auge %d m, Meer %d × %d², Halme %.0f %% Reichweite, Bodendecke %.0f m, Wolken %s, Regen %s, Nebel %s, Blitz %s, Tide auf %.0f m, Seehunde %s, Vögel %s, Häuser bis %.0f m, Rauch %s, Deichbruch %s",
                label, Math.round(renderScale * 100), budgetMs(), terrainRange, terrainFine, ocean.cascades, ocean.n, reed.range * 100, groundRadius, clouds, rain, fog, lightning, 8.0 * tide.coarse, seal, birds, timber().far, smoke(), fracture());
    }

    /**
     * Phase 17 (Qualitätsstufen durchgängig): die Stufen der Packages, die bis Phase 16 fest auf „Mittel“ standen,
     * folgen der App eins zu eins: das Watt ({@code mudflat}), die Häuser von 1362 ({@code timber}: Sichtweite der
     * Stufen und Grenze, ab der die Stadt gezeichnet wird), der Herdrauch ({@code smoke}: Ballen und Lebensdauer) und
     * der Deich, der bricht ({@code fracture}: Zellen des Kleikörpers und Iterationen), und die Gischt über seiner Krone ({@code spray}). Fest bleiben nur der Klang
     * (Abtastrate beim Start) und die Welle der Bresche (Gitter nach dem Ort, nicht nach der Stufe).
     */
    public com.dan.mudflat.Mudflat.Quality mudflat() { return com.dan.mudflat.Mudflat.Quality.values()[ordinal()]; }
    public com.dan.timber.Timber.Quality timber() { return com.dan.timber.Timber.Quality.values()[ordinal()]; }
    public com.dan.smoke.Smoke.Quality smoke() { return com.dan.smoke.Smoke.Quality.values()[ordinal()]; }
    public com.dan.fracture.Fracture.Quality fracture() { return com.dan.fracture.Fracture.Quality.values()[ordinal()]; }
    /** Phase 17: die Gischt über der Deichkrone in der Mandränke. */
    public com.dan.spray.Spray.Quality spray() { return com.dan.spray.Spray.Quality.values()[ordinal()]; }

    @Override public String toString() { return label; }
}
