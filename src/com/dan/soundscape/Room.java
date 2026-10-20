package com.dan.soundscape;

/**
 * Wo das Ohr steht: wie stark die Wand den Schall von draußen dämpft (dB), bis zu welcher Frequenz er durchkommt
 * (Hz), Nachhallzeit T60 (s) und Anteil des Halls. {@link Soundscape#inside} blendet von draußen (0) in den Raum (1).
 */
public record Room(String name, double wallDb, double wallHz, double t60, double mix) {
    /** Unter freiem Himmel: keine Wand, kaum Hall. */
    public static final Room OUTDOORS = new Room("draußen", 0, 20_000, 0.3, 0.0);
    /** Ein Steinturm mit dicken Wänden und kleinen Fenstern: dumpf und hallig (Treppenhaus, Dienstraum). */
    public static final Room STONE_TOWER = new Room("Steinturm", 28, 320, 2.2, 0.35);
    /** Ein Glashaus aus Eisensprossen und Scheiben (Laternenraum): wenig gedämpft, kurzer, heller Hall. */
    public static final Room GLASS_LANTERN = new Room("Glashaus", 14, 2200, 0.7, 0.18);
    /** Ein Steuerhaus aus Stahl mit Fenstern: gedämpft, trocken. */
    public static final Room WHEELHOUSE = new Room("Steuerhaus", 18, 1500, 0.4, 0.12);

    /**
     * Seit Haithabu Phase 14: ein Langhaus aus Flechtwerk mit Lehm unter Reet, Lehmboden, Türen und Rauchlöcher offen.
     * Wenig gedämpft, die Höhen fehlen, kurzer weicher Hall (Reet und Lehm schlucken) [G].
     */
    public static final Room LONGHOUSE = new Room("Langhaus", 13, 1100, 0.55, 0.15);

    /** Durchlass der Wand als Verstärkung bei ganz drinnen. */
    public double wallGain() { return Math.pow(10, -wallDb / 20); }
}
