package com.dan.seal;

import java.util.Locale;

/**
 * Seehunde als Körper und Verhalten (Werkbuch Rungholt, Abschnitt 4, F13 A). Kennt kein Watt: Bänke, Wasser und
 * Störer kommen von außen. Diese Klasse hält die Art (Presets, offene Werte) und die Regeln; {@link Colony} lässt eine
 * Gruppe nach ihnen leben, {@link SealBody} gibt ihr den Körper, {@code gl/SealGl} zeichnet sie.
 * <ul>
 * <li><b>Körper</b>: Kopf, Rumpf, Vorder- und Hinterflossen, eine biegsame Wirbelsäule ({@link SealBody}); Fell nass
 * dunkel und glänzend, trocken heller und matt, gefleckt nach dem Startwert jedes Tiers.</li>
 * <li><b>Haltungen</b>: liegen, Banane, robben, schwimmen, tauchen, Kopf heben; langsame Übergänge.</li>
 * <li><b>Verhalten</b>: mit fallendem Wasser auf die Bank, nahe am Wasser und mit Abstand zueinander, liegen und
 * wärmen, mit steigendem Wasser ins Wasser; vor Störern erst wachsam, dann Flucht ins Wasser (Fluchtdistanz);
 * Heuler im Juni und Juli bei der Mutter.</li>
 * <li><b>Ereignisse</b>: {@code onCall} für den Klang, Spuren für das Watt.</li>
 * </ul>
 * Zahlen mit [G] sind gesetzt, nicht gemessen.
 */
public final class Seal {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }
    public enum Pose { LIEGEN, BANANE, ROBBEN, SCHWIMMEN, TAUCHEN, KOPF_HEBEN }

    /** Stand des Packages: 1 = Hülle; 7 = Körper und Verhalten. */
    public static final int STAGE = 7;

    /** Ein Ruf: Ort (m), Stärke 0..1. */
    @FunctionalInterface public interface Call { void call(double x, double z, double strength); }

    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Art (Preset), Länge eines erwachsenen Tiers (m), Masse (kg) [G]. */
    public String species = "Seehund";
    public double length = 1.6, mass = 90;
    /** Länge eines Jungen (m) [G]. */
    public double pupLength = 0.9;
    /** Höchstzahl der Tiere (Werkbuch, Abschnitt 10), Fluchtdistanz (m) [G], Monate mit Jungen. */
    public int maxAnimals = 60;
    public double flightDistance = 300;
    public int pupFrom = 6, pupTo = 7;
    /** Wie viel Wasser (m) über der Bank die Tiere noch liegen lässt, bevor sie gehen. */
    public double leaveDepth = 0.15;
    /** Wachsam ab diesem Vielfachen der Fluchtdistanz (Köpfe hoch). */
    public double alertFactor = 1.4;
    /** Geschwindigkeiten (m/s) [G]: robben, robben auf der Flucht, schwimmen. */
    public double crawlSpeed = 0.5, fleeSpeed = 1.3, swimSpeed = 1.2;
    /** Abstand zum Nachbarn auf der Bank (Vielfaches der Länge) [G]. */
    public double spacing = 1.3;
    /** So weit vom Wasser liegen sie am liebsten (m) [G]: am Rand, um schnell wegzukommen. */
    public double nearWaterMin = 3, nearWaterMax = 16;
    /** Wie lange sie warten, bis die Bank trocken genug ist (min) [G]: die vorsichtigen länger. */
    public double haulDelayMin = 4, haulDelayMax = 35;
    /** Anteil, der bei Ebbe im Wasser bleibt (Fressen) am Tag und in der Nacht [G]. */
    public double stayInWaterDay = 0.15, stayInWaterNight = 0.45;
    /** Nach einer Flucht so lange im Wasser (min) [G]. */
    public double calmMinutes = 25;
    /** Fell trocknet auf der Bank in der Sonne in so vielen Minuten halb (im Schatten das Dreifache) [G]. */
    public double furHalfMinutes = 20;
    public Call onCall;

    // ------------------------------------------------------------------ Presets
    /** Der Seehund (Phoca vitulina) im Wattenmeer: Junge im Juni und Juli. */
    public static Seal harbourSeal() { return new Seal(); }

    /** Die Kegelrobbe (Halichoerus grypus), größer, Junge im Winter (Lighthouse, Helgoland). */
    public static Seal greySeal() {
        Seal s = new Seal();
        s.species = "Kegelrobbe"; s.length = 2.2; s.mass = 200; s.pupLength = 1.0; s.pupFrom = 11; s.pupTo = 1; s.flightDistance = 200;
        return s;
    }

    // ------------------------------------------------------------------ Regeln
    /** Ist der Monat (1..12) die Zeit der Jungen? Über den Jahreswechsel gezählt, wenn {@code pupFrom > pupTo}. */
    public boolean pupSeason(int month) { return pupFrom <= pupTo ? month >= pupFrom && month <= pupTo : month >= pupFrom || month <= pupTo; }

    /** Darf ein Tier auf der Bank liegen bleiben? Wasser über der Bank (m), Abstand zum nächsten Störer (m). */
    public boolean stays(double waterAboveBank, double nearestDisturber) { return waterAboveBank < leaveDepth && nearestDisturber > flightDistance; }

    /** Zahl der Tiere je Qualitätsstufe (höchstens {@link #maxAnimals}). */
    public int animalsFor(Quality q) {
        double f = switch (q) { case LOW -> 0.35; case MEDIUM -> 0.6; case HIGH -> 0.85; case ULTRA -> 1.0; };
        return (int) Math.round(maxAnimals * f);
    }

    /** „seal: Stufe 7 · Seehund 1,6 m · höchstens 60 · Flucht 300 m · MEDIUM“ */
    public String describe() {
        return String.format(Locale.GERMAN, "seal: %s · %s %.1f m · höchstens %d · Flucht %.0f m · %s",
                STAGE == 1 ? "Hülle (Phase 1)" : "Stufe " + STAGE, species, length, maxAnimals, flightDistance, quality);
    }
}
